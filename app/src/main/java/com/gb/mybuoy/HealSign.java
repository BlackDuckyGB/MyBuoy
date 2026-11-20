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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/**
 * The heart that leaps over the player when a portion comes back.
 *
 * Anything given has to be SEEN to be given: a gauge that fills in the corner of the screen while
 * the player is looking at his feet is a reward nobody notices. So the heart jumps where he is,
 * swells as it goes up, and is gone in under a second.
 *
 * It springs from where the player stood at that instant and climbs from there rather than
 * following him: a mark of the place it happened, like the figures that count a score.
 *
 * Counted in milliseconds, like everything else here that has to last the same whatever the frame
 * rate.
 */
public class HealSign {

    // Big, and in no hurry. It was half this size and half this long, and in the middle of a
    // fight it went by without being seen at all -- which makes it worth nothing: a reward that
    // is not noticed has not been given.
    private static final float SIZE_DP = 128f;

    /** How far it climbs over its life, in DP. */
    private static final float RISE_DP = 86f;

    /** And how long that takes. */
    private static final long LIFE_MS = 1800;

    // It comes out small and swells to its full size in the first part of the climb, which is
    // what makes it leap rather than slide.
    private static final double SCALE_FROM = 0.35;
    private static final double SWELL_PART = 0.30;

    private static Bitmap _image = null;

    /** Decodes the shared image. Called once, before the first one. */
    public static void loadSheet(Resources res) {
        if( _image == null )
            _image = BitmapFactory.decodeResource(res, R.drawable.eg_heal);
    }

    private static final Paint PAINT = new Paint();

    static {
        PAINT.setAntiAlias(true);
        PAINT.setFilterBitmap(true);
    }

    private final GameSurface gameSurface;

    private final double x;
    private final double y;

    private final float size;
    private final float rise;

    private final long bornMs;

    private double climbed = 0;
    private double scale = SCALE_FROM;
    private int alpha = 255;

    private final Rect src = new Rect();
    private final RectF dst = new RectF();

    public HealSign(GameSurface gameSurface, double x, double y) {

        this.gameSurface = gameSurface;

        this.x = x;
        this.y = y;

        float density = gameSurface.getResources().getDisplayMetrics().density;

        this.size = SIZE_DP * density;
        this.rise = RISE_DP * density;

        this.bornMs = System.nanoTime() / 1000000;
    }

    /** One step. True when it has had its life. */
    public boolean update() {

        long age = (System.nanoTime() / 1000000) - bornMs;

        double done = (double) age / LIFE_MS;

        if( done > 1.0 )
            done = 1.0;

        // Fast off the ground and settling at the top, like everything thrown.
        climbed = rise * (1.0 - ((1.0 - done) * (1.0 - done)));

        double swell = (done < SWELL_PART) ? (done / SWELL_PART) : 1.0;

        scale = SCALE_FROM + ((1.0 - SCALE_FROM) * swell);

        // Solid for the first half of its life, then gone: a sign that fades from the first frame
        // is a sign one never quite reads.
        // Solid for two thirds of its life, then gone: long enough to be read, and the fade is
        // still quick enough not to linger over the fight.
        alpha = (done < 0.66) ? 255 : (int) (255 * (1.0 - done) * 3);

        return (age >= LIFE_MS);
    }

    public void draw(Canvas canvas) {

        if( (canvas == null) || (_image == null) || (alpha <= 0) )
            return;

        float half = (float) (size * scale) / 2f;

        float cx = gameSurface.getCanvasX(x);
        float cy = gameSurface.getCanvasY(y - climbed);

        src.set(0, 0, _image.getWidth(), _image.getHeight());
        dst.set(cx - half, cy - half, cx + half, cy + half);

        PAINT.setAlpha(alpha);

        canvas.drawBitmap(_image, src, dst, PAINT);
    }
}
