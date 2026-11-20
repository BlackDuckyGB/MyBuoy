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
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The figures that float up when points are scored.
 *
 * A score that only moves in a corner of the screen is a score nobody reads: the number has to
 * appear where the thing happened, so that the gesture and its reward are the same event. Each one
 * rises a little, fades, and is gone in under a second.
 *
 * They are offset up and to the side of the point they belong to, rather than sitting on it: the
 * sparks of a blow are already there, and a number painted over them hides the very animation it
 * is celebrating.
 *
 * The surface owns the list -- one place that draws them, one place that ages them -- and anything
 * that scores drops one in. Counted in updates, like everything else in this game that moves.
 */
public class ScorePopup {

    // How long one lives, in updates.
    private static final int LIFE_UPDATES = 28;

    // Everything below is in DP and turned into pixels when the figure is made.
    //
    // It used to be written in pixels, which made the figures shrink as screens got finer: the
    // 46 px of the small one came to 16 dp on a 450 dpi phone, barely readable, where the same
    // number filled the screen on the reference 160 dpi. Stated in DP, a figure reads the same
    // size on any screen.

    // How far it climbs over its life.
    private static final float RISE_DP = 26f;

    // Clear of the impact: up and to the side, so the sparks stay visible under it.
    private static final float OFFSET_X_DP = 13f;
    private static final float OFFSET_Y_DP = -11f;

    // The figures grow with what they are worth: a creeper and a boss should not read the same.
    private static final float SIZE_SMALL_DP = 30f;
    private static final float SIZE_BIG_DP = 46f;
    private static final int BIG_FROM = 100;

    // The light outline behind the figure, offset by this much.
    private static final float SHADOW_DP = 1f;

    // Black on the map, which is pale: white was lost on the roads and the light areas.
    private static final int COLOUR_PLAIN = Color.rgb(16, 16, 16);
    private static final int COLOUR_RICH = Color.rgb(176, 112, 0);     // worth noticing
    private static final int COLOUR_FACTOR = Color.rgb(0, 120, 60);    // the multiplier line

    private static final Paint TEXT = new Paint();
    private static final Paint SHADOW = new Paint();

    static {
        TEXT.setAntiAlias(true);
        TEXT.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        TEXT.setTextAlign(Paint.Align.CENTER);

        SHADOW.setAntiAlias(true);
        SHADOW.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        SHADOW.setTextAlign(Paint.Align.CENTER);
        // A light outline behind the black figure, so that it holds on a dark background too.
        SHADOW.setColor(Color.argb(190, 255, 255, 255));
    }

    private final double x;
    private final double y;
    private final String amount;
    private final String factor;   // null when the gain was plain
    private final int colour;

    // In pixels, worked out once here from the screen's density. draw() runs every frame and has
    // no business doing this: it reads these and nothing else.
    private final float size;
    private final double rise;
    private final float shadow;

    private int age = 0;

    private ScorePopup(double x, double y, int points, int multiplier, float density) {

        this.x = x + (OFFSET_X_DP * density);
        this.y = y + (OFFSET_Y_DP * density);

        this.amount = "+" + points;
        this.factor = (multiplier > 1) ? ("x" + multiplier) : null;

        float sizeDp = (points >= BIG_FROM) ? SIZE_BIG_DP : SIZE_SMALL_DP;

        this.size = sizeDp * density;
        this.rise = RISE_DP * density;
        this.shadow = SHADOW_DP * density;

        this.colour = (points >= BIG_FROM) ? COLOUR_RICH : COLOUR_PLAIN;
    }

    /**
     * Scores those points at that spot, and says so on the map.
     *
     * The amount given is the one already multiplied -- what the player actually earns -- and the
     * multiplier is written underneath when there was one. Showing "+10 x5" and leaving the sum to
     * be done in the middle of a fight would tell nobody anything.
     */
    public static void score(GameSurface surface, double x, double y, int points) {
        add(surface, x, y, points, AppManager.getInstance().getStreakMultiplier());
    }

    /**
     * The same, on its own: what is scored is what is given, with no streak on top.
     *
     * For a gain that is already an amount rather than a unit price. A pole pays what its own
     * countdown says it is worth, and hundreds of points multiplied by a run of kills would
     * drown everything else in the game.
     */
    public static void scorePlain(GameSurface surface, double x, double y, int points) {
        add(surface, x, y, points, 1);
    }

    private static void add(GameSurface surface, double x, double y, int points, int multiplier) {

        if( surface == null )
            return;

        AppManager am = AppManager.getInstance();

        if( !am.getIsGameMode() )
            return;

        int total = points * multiplier;

        am.addScore(total);

        float density = surface.getResources().getDisplayMetrics().density;

        surface.addScorePopup(new ScorePopup(x, y, total, multiplier, density));
    }

    /** One step. True when it has had its life. */
    public boolean update() {
        age++;
        return (age >= LIFE_UPDATES);
    }

    public void draw(Canvas canvas, GameSurface surface) {

        if( (canvas == null) || (surface == null) )
            return;

        double left = 1.0 - (double) age / LIFE_UPDATES;

        // It climbs quickly at first and settles, which reads as something thrown up rather than
        // something lifted.
        double climbed = rise * (1.0 - left * left);

        float cx = surface.getCanvasX(x);
        float cy = surface.getCanvasY(y - climbed);

        // It stays solid for the first half of its life, then goes: a number that fades from the
        // first frame is a number one never quite reads.
        int alpha = (left > 0.5) ? 255 : (int) (255 * left * 2);

        TEXT.setTextSize(size);
        SHADOW.setTextSize(size);

        TEXT.setColor(colour);
        TEXT.setAlpha(alpha);
        SHADOW.setAlpha((int) (alpha * 0.66));

        canvas.drawText(amount, cx + shadow, cy + shadow, SHADOW);
        canvas.drawText(amount, cx, cy, TEXT);

        if( factor == null )
            return;

        TEXT.setTextSize(size * 0.62f);
        SHADOW.setTextSize(size * 0.62f);

        TEXT.setColor(COLOUR_FACTOR);
        TEXT.setAlpha(alpha);

        canvas.drawText(factor, cx + shadow, cy + size * 0.64f + shadow, SHADOW);
        canvas.drawText(factor, cx, cy + size * 0.64f, TEXT);
    }
}
