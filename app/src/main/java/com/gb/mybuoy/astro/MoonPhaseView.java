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
 * The Moon as it actually looks right now.
 *
 * The shape and the turn both come from MoonDisc, which the small Moon on the sky
 * map uses as well: one Moon drawn in two places from one piece of code.
 *
 * The turn is what makes it a Moon and not a logo: the horns follow the lit edge
 * through the night, the way they do overhead.
 *
 * Nothing is computed here: the fraction and the tilt arrive already worked out.
 */
package com.gb.mybuoy.astro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Draws the Moon at a given phase and tilt. */
public final class MoonPhaseView extends View
{
    private static final int COLOUR_LIT = 0xFFF5F3E7;
    private static final int COLOUR_DARK = 0xFF2A3042;
    private static final int COLOUR_RIM = 0xFF8894B0;

    private final Paint _litPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path _lit = new Path();
    private final RectF _bounds = new RectF();

    private double _illuminatedFraction = 0.5;
    private double _tiltRadians = 0.0;
    private boolean _waxing = true;

    public MoonPhaseView(Context context)
    {
        super(context);
        _setUpPaints();
    }

    public MoonPhaseView(Context context, AttributeSet attributes)
    {
        super(context, attributes);
        _setUpPaints();
    }

    /**
     * @param illuminatedFraction zero at new Moon, one at full
     * @param waxing              true while the lit part is growing
     * @param tiltRadians         angle of the lit edge from straight up, turning east
     */
    public void setPhase(double illuminatedFraction, boolean waxing, double tiltRadians)
    {
        _illuminatedFraction = Math.min(1.0, Math.max(0.0, illuminatedFraction));
        _waxing = waxing;
        _tiltRadians = tiltRadians;

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

        if (side <= 0.0f) {
            return;
        }

        float centreX = getWidth() / 2.0f;
        float centreY = getHeight() / 2.0f;
        float radius = (side / 2.0f) - _rimPaint.getStrokeWidth();

        int saved = canvas.save();

        // Built upright, then turned as a whole. MoonDisc holds both.
        canvas.rotate(MoonDisc.rotationDegrees(_tiltRadians, _waxing), centreX, centreY);

        canvas.drawCircle(centreX, centreY, radius, _darkPaint);

        MoonDisc.litPath(_lit, _bounds, centreX, centreY, radius, _illuminatedFraction, _waxing);

        canvas.drawPath(_lit, _litPaint);

        canvas.restoreToCount(saved);

        canvas.drawCircle(centreX, centreY, radius, _rimPaint);
    }

    private void _setUpPaints()
    {
        float density = getResources().getDisplayMetrics().density;

        _litPaint.setColor(COLOUR_LIT);
        _litPaint.setStyle(Paint.Style.FILL);

        _darkPaint.setColor(COLOUR_DARK);
        _darkPaint.setStyle(Paint.Style.FILL);

        _rimPaint.setColor(COLOUR_RIM);
        _rimPaint.setStyle(Paint.Style.STROKE);
        _rimPaint.setStrokeWidth(1.4f * density);
    }
}
