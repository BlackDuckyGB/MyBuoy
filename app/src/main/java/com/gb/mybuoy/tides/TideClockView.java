/*
 * MyBuoy — outdoor navigation & mark-tracking for boating, hiking, foraging and discovery walks.
 * Copyright (C) 2026 Golden-Block
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * For commercial licensing (use without AGPL obligations), contact:
 * licence@golden-block.fr
 */

/*
 * A tide clock: high water at the top, low water at the bottom, the hand somewhere on the
 * way between them.
 *
 * A printed tide clock assumes six hours and a quarter from one to the next, which only
 * holds where the tide is semidiurnal. Here the half cycle is taken from the two tides
 * actually surrounding the moment, so the hand is right wherever it is used; the hour
 * figures are only drawn when that half cycle is close enough to six hours for them to
 * mean anything, and left off entirely in diurnal and mixed regimes.
 */
package com.gb.mybuoy.tides;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/** Draws where the tide stands between its last turn and its next. */
public final class TideClockView extends View
{
    private static final int COLOUR_FACE = 0xFFE3F2FD;
    private static final int COLOUR_RIM = 0xFF90CAF9;
    private static final int COLOUR_RISING = 0xFF2196F3;
    private static final int COLOUR_FALLING = 0xFFEF6C00;
    private static final int COLOUR_TEXT = 0xFF424242;
    private static final int COLOUR_TICK = 0xFF90A4AE;

    /** Hour figures are only honest when the half cycle is near the usual six and a bit. */
    private static final double SEMIDIURNAL_MIN_HOURS = 4.5;
    private static final double SEMIDIURNAL_MAX_HOURS = 8.0;

    private final Paint _facePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _handPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _endPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path _hand = new Path();
    private final RectF _bounds = new RectF();

    private boolean _known = false;
    private boolean _rising = false;
    private double _fraction = 0.0;
    private double _halfCycleHours = 0.0;

    public TideClockView(Context context)
    {
        super(context);
        _setUpPaints();
    }

    public TideClockView(Context context, AttributeSet attributes)
    {
        super(context, attributes);
        _setUpPaints();
    }

    /**
     * Places the hand from the two tides surrounding the moment.
     *
     * @param previousSeconds when the tide last turned
     * @param previousWasHigh whether that turn was a high water
     * @param nextSeconds     when it turns next
     * @param nowSeconds      the moment to show
     */
    public void setPhase(long previousSeconds, boolean previousWasHigh,
                         long nextSeconds, long nowSeconds)
    {
        long span = nextSeconds - previousSeconds;

        if (span <= 0L)
        {
            _known = false;
            invalidate();
            return;
        }

        double elapsed = (double) (nowSeconds - previousSeconds) / span;

        _fraction = Math.min(1.0, Math.max(0.0, elapsed));
        _rising = !previousWasHigh;
        _halfCycleHours = span / 3600.0;
        _known = true;

        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec)
    {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);

        int side = Math.min(width, height > 0 ? height : width);

        setMeasuredDimension(side, side);
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);

        float side = Math.min(getWidth(), getHeight());

        if (side <= 0.0f)
        {
            return;
        }

        float centreX = getWidth() / 2.0f;
        float centreY = getHeight() / 2.0f;
        float radius = side / 2.0f - _textPaint.getTextSize() - 2.0f;

        canvas.drawCircle(centreX, centreY, radius, _facePaint);
        canvas.drawCircle(centreX, centreY, radius, _rimPaint);

        _drawTicks(canvas, centreX, centreY, radius);
        _drawEnds(canvas, centreX, centreY, radius);

        if (!_known)
        {
            return;
        }

        // Clockwise from the top: the first half of the turn runs high to low down the right
        // side, the second half runs low back to high up the left.
        double degrees = _rising ? 180.0 + 180.0 * _fraction : 180.0 * _fraction;

        _drawHand(canvas, centreX, centreY, radius * 0.78f, degrees);

        _handPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(centreX, centreY, radius * 0.08f, _handPaint);
    }

    private void _drawTicks(Canvas canvas, float centreX, float centreY, float radius)
    {
        boolean labelled = _known
                && _halfCycleHours >= SEMIDIURNAL_MIN_HOURS
                && _halfCycleHours <= SEMIDIURNAL_MAX_HOURS;

        int hours = (int) Math.round(_halfCycleHours);

        // The two turns themselves, which the hours in between are counted from.
        _tickAt(canvas, centreX, centreY, radius, 0.0, null, true);
        _tickAt(canvas, centreX, centreY, radius, 180.0, null, true);

        // The figures count down to the next turn, not up from the last one: what is worth
        // reading off a tide clock is how long is left, on either side of the dial.
        for (int step = 1; step < Math.max(2, hours); step++)
        {
            double share = (double) step / Math.max(2, hours);

            _tickAt(canvas, centreX, centreY, radius, 180.0 * share,
                    labelled ? String.valueOf(hours - step) : null, false);

            _tickAt(canvas, centreX, centreY, radius, 360.0 - 180.0 * share,
                    labelled ? String.valueOf(step) : null, false);
        }
    }

    private void _tickAt(Canvas canvas, float centreX, float centreY, float radius,
                         double degrees, String label, boolean strong)
    {
        double radians = Math.toRadians(degrees - 90.0);

        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);

        float inner = strong ? 0.78f : 0.88f;

        _tickPaint.setStrokeWidth((strong ? 2.6f : 1.2f)
                * getResources().getDisplayMetrics().density);

        canvas.drawLine(centreX + cos * radius * inner, centreY + sin * radius * inner,
                centreX + cos * radius, centreY + sin * radius, _tickPaint);

        if (label == null)
        {
            return;
        }

        canvas.drawText(label,
                centreX + cos * (radius + _textPaint.getTextSize() * 0.75f),
                centreY + sin * (radius + _textPaint.getTextSize() * 0.75f)
                        + _textPaint.getTextSize() * 0.35f,
                _textPaint);
    }

    private void _drawEnds(Canvas canvas, float centreX, float centreY, float radius)
    {
        canvas.drawText("HW", centreX,
                centreY - radius - _textPaint.getTextSize() * 0.25f, _endPaint);

        canvas.drawText("LW", centreX,
                centreY + radius + _textPaint.getTextSize() * 1.1f, _endPaint);
    }

    private void _drawHand(Canvas canvas, float centreX, float centreY,
                           float length, double degrees)
    {
        double radians = Math.toRadians(degrees - 90.0);

        float tipX = centreX + (float) Math.cos(radians) * length;
        float tipY = centreY + (float) Math.sin(radians) * length;

        float backX = centreX - (float) Math.cos(radians) * length * 0.18f;
        float backY = centreY - (float) Math.sin(radians) * length * 0.18f;

        float sideX = (float) Math.cos(radians + Math.PI / 2.0) * length * 0.08f;
        float sideY = (float) Math.sin(radians + Math.PI / 2.0) * length * 0.08f;

        _hand.reset();
        _hand.moveTo(tipX, tipY);
        _hand.lineTo(backX + sideX, backY + sideY);
        _hand.lineTo(backX - sideX, backY - sideY);
        _hand.close();

        _handPaint.setColor(_rising ? COLOUR_RISING : COLOUR_FALLING);
        _handPaint.setStyle(Paint.Style.FILL);

        canvas.drawPath(_hand, _handPaint);
    }

    private void _setUpPaints()
    {
        float density = getResources().getDisplayMetrics().density;

        _facePaint.setColor(COLOUR_FACE);
        _facePaint.setStyle(Paint.Style.FILL);

        _rimPaint.setColor(COLOUR_RIM);
        _rimPaint.setStyle(Paint.Style.STROKE);
        _rimPaint.setStrokeWidth(1.6f * density);

        _tickPaint.setColor(COLOUR_TICK);
        _tickPaint.setStyle(Paint.Style.STROKE);
        _tickPaint.setStrokeWidth(1.2f * density);

        _textPaint.setColor(COLOUR_TEXT);
        _textPaint.setTextSize(9.0f * density);
        _textPaint.setTextAlign(Paint.Align.CENTER);

        _endPaint.setColor(COLOUR_TEXT);
        _endPaint.setTextSize(10.0f * density);
        _endPaint.setTextAlign(Paint.Align.CENTER);
        _endPaint.setFakeBoldText(true);

        _handPaint.setStyle(Paint.Style.FILL);
    }
}
