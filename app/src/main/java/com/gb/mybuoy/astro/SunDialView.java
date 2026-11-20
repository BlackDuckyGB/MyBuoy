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
 * The Sun as a dial, facing the Moon's phase across the sky map.
 *
 * The crown of rays turns with the hour angle, which is the real sundial
 * quantity: the angle the Earth still has to turn to bring the Sun back to the
 * meridian. At solar noon the long ray points straight up; six hours later it
 * has swung a quarter turn, and over a day it goes round once. Scrubbing the
 * curve underneath therefore spins it, and the hour of the day is readable at a
 * glance without a single figure.
 *
 * The long ray is not decoration. A crown of evenly spaced rays looks exactly
 * the same every forty-five degrees, so a turning one would say nothing at all.
 * One ray reaching further than the others is what makes the angle legible.
 *
 * Below the horizon the whole thing goes grey. The rays keep turning, because
 * the Sun keeps moving whether or not anyone can see it.
 *
 * Nothing is computed here.
 */
package com.gb.mybuoy.astro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** A small sundial: the Sun's hour angle, and whether it is up. */
public final class SunDialView extends View
{
    private static final int COLOUR_UP = 0xFFFFA000;
    private static final int COLOUR_DOWN = 0xFFB0A89A;
    private static final int COLOUR_EDGE_UP = 0xFFE07B00;
    private static final int COLOUR_EDGE_DOWN = 0xFF938B7E;

    /** Short rays all round, plus one long one that carries the angle. */
    private static final int RAY_COUNT = 8;

    private final Paint _discPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _rayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean _known = false;
    private boolean _up = false;
    private double _hourAngleRadians = 0.0;

    public SunDialView(Context context)
    {
        super(context);
        _setUpPaints();
    }

    public SunDialView(Context context, AttributeSet attributes)
    {
        super(context, attributes);
        _setUpPaints();
    }

    /**
     * @param hourAngleRadians the Sun's local hour angle, zero on the meridian
     * @param up               whether the Sun is above the horizon
     */
    public void setSun(double hourAngleRadians, boolean up)
    {
        _hourAngleRadians = hourAngleRadians;
        _up = up;
        _known = true;

        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec)
    {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);

        int side = Math.min(width, (height > 0) ? height : width);

        setMeasuredDimension(side, side);
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);

        float side = Math.min(getWidth(), getHeight());

        if ((side <= 0.0f) || !_known) {
            return;
        }

        float centreX = getWidth() / 2.0f;
        float centreY = getHeight() / 2.0f;

        // The long ray reaches the edge, so the disc has to leave room for it.
        float outer = side / 2.0f;
        float disc = outer * 0.62f;

        _discPaint.setColor(_up ? COLOUR_UP : COLOUR_DOWN);
        _rayPaint.setColor(_up ? COLOUR_EDGE_UP : COLOUR_EDGE_DOWN);

        canvas.drawCircle(centreX, centreY, disc, _discPaint);

        int saved = canvas.save();

        // Clockwise with the hour angle, so the long ray is up on the meridian and
        // goes round once a day, the way a shadow does.
        canvas.rotate((float) Math.toDegrees(_hourAngleRadians), centreX, centreY);

        for (int ray = 0; ray < RAY_COUNT; ray++) {

            boolean marker = (ray == 0);

            // Measured from straight up, turning clockwise.
            double angle = ((2.0 * Math.PI) / RAY_COUNT) * ray;

            float sin = (float) Math.sin(angle);
            float cos = (float) Math.cos(angle);

            // The long ray has to be the longest, which it had stopped being: the short
            // ones reached 1.62 of the disc, that is past the edge the long one stops at,
            // so the crown read as eight equal rays and the hour was lost.
            float from = disc * 1.22f;
            float to = marker ? (outer * 0.99f) : (disc * 1.34f);

            _rayPaint.setStrokeWidth(_rayPaint.getStrokeWidth());

            canvas.drawLine(centreX + (sin * from), centreY - (cos * from),
                    centreX + (sin * to), centreY - (cos * to), _rayPaint);
        }

        canvas.restoreToCount(saved);
    }

    private void _setUpPaints()
    {
        float density = getResources().getDisplayMetrics().density;

        _discPaint.setStyle(Paint.Style.FILL);

        _rayPaint.setStyle(Paint.Style.STROKE);
        _rayPaint.setStrokeWidth(2.0f * density);
        _rayPaint.setStrokeCap(Paint.Cap.ROUND);
    }
}
