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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

/**
 * What being hit looks like: a white flash, a step back, and three sparks.
 *
 * One blow, read in a fifth of a second. The sprite goes white for a single frame -- the eye
 * catches a change of colour before it catches a change of place -- then the whole figure is
 * pushed a couple of pixels away from whatever struck it and walks back to where it stood over the
 * next few frames, while three sparks fly off the point of impact and fade.
 *
 * It belongs to whoever is hit and not to the thing that hits: the player owns one, and a creature
 * owns one just the same. The owner asks it three things while it draws itself -- am I flashing,
 * how far am I pushed, and would you draw your sparks -- and nothing else is shared.
 *
 * Everything is counted in UPDATES rather than in milliseconds. What is being described is a
 * number of drawn frames, and a frame is what an update is; a delay in milliseconds would stretch
 * or shrink the whole thing the day the frame rate moves.
 */
public class HitEffect {

    // The white frames. One was too quick to register at twenty-five images a second -- it was
    // over in forty milliseconds, which the eye files under "flicker" rather than "blow".
    private static final int FLASH_UPDATES = 3;

    // How far the figure is driven back, in area pixels, and how many updates it takes to come
    // home. Five sits in the middle of what still reads as a recoil rather than a slide.
    private static final double RECOIL_PX = 7.0;
    private static final int RETURN_UPDATES = 10;

    private static final int SPARK_COUNT = 3;
    private static final double SPARK_SPEED_PX = 2.8;     // per update
    private static final float SPARK_RADIUS = 6f;
    private static final int SPARK_UPDATES = 12;

    private static final Paint FLASH_PAINT = new Paint();
    private static final Paint SPARK_PAINT = new Paint();

    static {
        FLASH_PAINT.setAntiAlias(true);
        FLASH_PAINT.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP));

        SPARK_PAINT.setAntiAlias(true);
        SPARK_PAINT.setStyle(Paint.Style.FILL);
    }

    // Updates since the blow, or -1 when nothing is running.
    private int age = -1;

    // Where the figure is pushed towards, as a unit vector, and the sparks' own courses.
    private double awayX = 0;
    private double awayY = -1;

    private final double[] sparkX = new double[SPARK_COUNT];
    private final double[] sparkY = new double[SPARK_COUNT];
    private final double[] sparkDirX = new double[SPARK_COUNT];
    private final double[] sparkDirY = new double[SPARK_COUNT];

    /**
     * A blow, struck from that point, in area coordinates.
     *
     * Restarting one already in flight is the point: being hit twice in quick succession should
     * flash twice, not be swallowed by the first.
     */
    public void start(double fromX, double fromY, double atX, double atY) {

        double dx = atX - fromX;
        double dy = atY - fromY;

        double length = Math.sqrt(dx * dx + dy * dy);

        if( length > 0 ) {
            awayX = dx / length;
            awayY = dy / length;
        }

        // The sparks leave the point of impact, spread around the way the blow came from.
        for( int i = 0; i < SPARK_COUNT; i++ ) {

            double spread = Math.toRadians(-50 + Math.random() * 100);

            double cos = Math.cos(spread);
            double sin = Math.sin(spread);

            sparkDirX[i] = awayX * cos - awayY * sin;
            sparkDirY[i] = awayX * sin + awayY * cos;

            sparkX[i] = atX;
            sparkY[i] = atY;
        }

        age = 0;
    }

    /** One step. Does nothing when no blow is in flight. */
    public void update() {

        if( age < 0 )
            return;

        for( int i = 0; i < SPARK_COUNT; i++ ) {
            sparkX[i] += sparkDirX[i] * SPARK_SPEED_PX;
            sparkY[i] += sparkDirY[i] * SPARK_SPEED_PX;
        }

        age++;

        if( age > Math.max(RETURN_UPDATES, SPARK_UPDATES) )
            age = -1;
    }

    /** Is this the white frame? The owner draws its sprite through getFlashPaint() if so. */
    public boolean isFlashing() {
        return (age >= 0) && (age < FLASH_UPDATES);
    }

    public static Paint getFlashPaint() {
        return FLASH_PAINT;
    }

    /** How far the figure stands from where it belongs, in area pixels. */
    public double getOffsetX() {
        return awayX * recoil();
    }

    public double getOffsetY() {
        return awayY * recoil();
    }

    // Full on the frame of the blow, nothing once it has walked back. The return is eased rather
    // than linear -- quick at first, then settling -- which is what a knock looks like; a straight
    // line back reads as a slide.
    private double recoil() {

        if( (age < 0) || (age >= RETURN_UPDATES) )
            return 0;

        double left = 1.0 - (double) age / RETURN_UPDATES;

        return RECOIL_PX * left * left;
    }

    /** The sparks, in area coordinates: the owner hands over the surface to place them on screen. */
    public void draw(Canvas canvas, GameSurface surface) {

        if( (age < 0) || (age >= SPARK_UPDATES) || (canvas == null) || (surface == null) )
            return;

        double left = 1.0 - (double) age / SPARK_UPDATES;

        // They fade AND shrink: a spark that only fades looks like a lamp being turned down, one
        // that also gets smaller looks like something thrown off and spent.
        SPARK_PAINT.setColor(Color.argb((int) (255 * left), 255, 240, 120));

        float radius = (float) (SPARK_RADIUS * (0.4 + 0.6 * left));

        for( int i = 0; i < SPARK_COUNT; i++ )
            canvas.drawCircle(surface.getCanvasX(sparkX[i]), surface.getCanvasY(sparkY[i]),
                              radius, SPARK_PAINT);
    }
}
