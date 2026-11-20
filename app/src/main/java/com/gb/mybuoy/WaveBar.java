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
package com.gb.mybuoy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

/**
 * Easter egg: how far through the level the player is, as a bar.
 *
 * One slot per wave, filled from the left. A wave's own slot fills over the time the wave was
 * written for -- its patience -- so the bar moves on its own while the player fights, and reaches
 * the end of the slot at the exact moment the hurry up starts biting. From there it cannot go any
 * further: clearing the wave is the only thing that opens the next slot.
 *
 * Which is why the colour says what the bar cannot: GREEN while it is climbing, ORANGE at the
 * first notch of hurry up, RED at the second. A bar stuck at a slot boundary and turning red is
 * the game telling the player he is the one holding it up.
 *
 * It draws itself and asks nothing: everything it shows is handed to it by
 * {@link AppManager#bindWaveBar}, which the orchestrator feeds a few times a second. Nothing is
 * decided in onDraw -- not even the colour, which is settled the moment the state changes.
 */
public class WaveBar extends View {

    // Rounded just enough not to look like a bare rectangle, and a hairline between two waves so
    // that they can be counted.
    private static final float CORNER_DP = 2f;
    private static final float SEPARATOR_DP = 1f;

    // Its width is fixed, and that is the point: measured on the panel it would stretch and shrink
    // with the length of a mark's name, and a progress bar that changes length says nothing. Two
    // sizes, because the panel itself has two: narrow with no mark selected, wider when the
    // heading and the name have pushed it out.
    private static final float WIDTH_DP = 160f;
    private static final float WIDTH_WIDE_DP = 210f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint separatorPaint = new Paint();

    // Reused: a view that draws three rectangles has no business making one every frame.
    private final RectF rect = new RectF();

    private final int colourOk;
    private final int colourWarn;
    private final int colourBad;

    private final float corner;
    private final float separator;

    private final float width;
    private final float widthWide;

    // How far through the level, in waves, and out of how many.
    private float progress = 0f;
    private int count = 0;

    // Which of the two widths it takes: the wide one when a mark is selected.
    private boolean wide = false;

    public WaveBar(Context context, AttributeSet attrs) {
        super(context, attrs);

        float density = getResources().getDisplayMetrics().density;

        corner = CORNER_DP * density;
        separator = SEPARATOR_DP * density;

        width = WIDTH_DP * density;
        widthWide = WIDTH_WIDE_DP * density;

        colourOk = ContextCompat.getColor(context, R.color.game_bar_ok);
        colourWarn = ContextCompat.getColor(context, R.color.game_bar_warn);
        colourBad = ContextCompat.getColor(context, R.color.game_bar_bad);

        trackPaint.setColor(ContextCompat.getColor(context, R.color.game_bar_track));
        separatorPaint.setColor(ContextCompat.getColor(context, R.color.game_bar_separator));

        fillPaint.setColor(colourOk);
    }

    /**
     * Where the level is up to, and how much the player is holding it up.
     *
     * Stress zero is the bar climbing, one is the first notch of hurry up, two and above is a
     * player who has stopped making progress at all.
     */
    public void setWaveState(float progress, int count, int stress) {

        float shown = progress;

        if( shown < 0f )
            shown = 0f;

        if( shown > count )
            shown = count;

        int colour = (stress <= 0) ? colourOk : ((stress == 1) ? colourWarn : colourBad);

        if( (shown == this.progress) && (count == this.count) && (colour == fillPaint.getColor()) )
            return;

        this.progress = shown;
        this.count = count;

        fillPaint.setColor(colour);

        invalidate();
    }

    /** Whether the panel is in its wide shape, which is to say whether a mark is selected. */
    public void setWide(boolean wide) {

        if( wide == this.wide )
            return;

        this.wide = wide;

        requestLayout();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {

        super.onMeasure(widthSpec, heightSpec);

        // The height is whatever the layout asked for; the width is ours.
        setMeasuredDimension(Math.round(wide ? widthWide : width), getMeasuredHeight());
    }

    @Override
    protected void onDraw(Canvas canvas) {

        int w = getWidth();
        int h = getHeight();

        if( (w <= 0) || (h <= 0) || (count <= 0) )
            return;

        rect.set(0f, 0f, w, h);

        canvas.drawRoundRect(rect, corner, corner, trackPaint);

        float done = (progress / count) * w;

        if( done > 0f ) {
            rect.set(0f, 0f, done, h);
            canvas.drawRoundRect(rect, corner, corner, fillPaint);
        }

        // The waves, so that the bar is read as ten steps and not as a percentage.
        for( int i = 1; i < count; i++ ) {

            float x = (w * i) / (float) count;

            canvas.drawRect(x - (separator / 2f), 0f, x + (separator / 2f), h, separatorPaint);
        }
    }
}
