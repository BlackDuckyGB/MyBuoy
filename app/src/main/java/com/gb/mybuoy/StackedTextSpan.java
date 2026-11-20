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

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

// Two small texts stacked in the place of one character: the first as a superscript, the second
// as a subscript, both left-aligned (e.g. the slope angle over the height difference after ▲).
// The replaced character itself is not drawn.
public class StackedTextSpan extends ReplacementSpan {

    private static final float SCALE = 0.55f;           // of the surrounding text size
    private static final float TOP_BASELINE = -0.42f;   // of the surrounding text size, from its baseline
    private static final float BOTTOM_BASELINE = 0.13f;

    private final String top;
    private final String bottom;

    public StackedTextSpan(String top, String bottom) {
        this.top = top;
        this.bottom = bottom;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        float size = paint.getTextSize();
        Paint small = new Paint(paint);
        small.setTextSize(size * SCALE);

        // Within the line's own ascent / descent: the line keeps its height.
        if (fm != null) {
            paint.getFontMetricsInt(fm);
        }

        return (int) Math.ceil(Math.max(small.measureText(top), small.measureText(bottom)));
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end,
                     float x, int lineTop, int y, int lineBottom, Paint paint) {
        float size = paint.getTextSize();
        Paint small = new Paint(paint);
        small.setTextSize(size * SCALE);

        canvas.drawText(top, x, y + size * TOP_BASELINE, small);
        canvas.drawText(bottom, x, y + size * BOTTOM_BASELINE, small);
    }
}
