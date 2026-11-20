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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/**
 * A heart lying on the map, waiting to be walked over.
 *
 * Dropped by a mark that has grown one -- see GameOrchestrator -- it flies out into the field
 * around it, breathes for the best part of a minute, and goes. Picked up, it gives back one
 * PORTION of heart: one hit of the three that make a life.
 *
 * A GameObject like everything else on this map, with a collider of its own: it is the PLAYER who
 * tests himself against it, his damage zone against its body, exactly as a tomato tests itself
 * against a creature. Nothing here looks for anybody -- a heart waits to be touched.
 *
 * Its image is scaled ONCE, when the class is first loaded, to the size it is drawn at. That is
 * what lets the collider be right without being told anything: a collider measures the box of the
 * sprite it belongs to, and the sprite is now the size it looks.
 *
 * It holds its place as a LATITUDE and a LONGITUDE and works its pixels out afresh on every
 * frame, rather than being born in area coordinates and keeping them. A chunk is swapped
 * underneath the game whenever the player travels far enough, and everything measured in that
 * chunk's pixels is wrong the moment it is replaced; a pair of coordinates on the Earth is not.
 *
 * It breathes faster as its time runs out, which is the only warning it gives. The breath is in
 * the DRAWING alone: a target that pulsed in and out of reach would be a cruel joke.
 *
 * Counted in milliseconds and not in frames, so that it lasts as long in low-resource mode too.
 */
public class GameHeart extends GameObject {

    // How big it is drawn, in DP: a thing to be seen from across the screen, not a map symbol.
    private static final float SIZE_DP = 38f;

    // How much of its box the body fills. The box is square and the heart is round-ish inside it,
    // so the half-diagonal that a collider measures by default reaches well past the drawing.
    private static final double HIT_COEF = 0.72;

    // How much it swells and shrinks as it breathes, and how long one breath takes at first.
    private static final double BREATH = 0.09;
    private static final long BREATH_MS = 1100;

    // The last seconds of its life, and the breath it takes then: short, and getting shorter.
    private static final long HURRY_MS = 10000;
    private static final long BREATH_FAST_MS = 260;

    // How long the flight takes, and how high it arcs as a fraction of the distance covered.
    private static final long FLY_MS = 750;
    private static final double ARC_PART = 0.45;

    // And how much bigger it is in mid-air at the top of its arc: a thing thrown towards you.
    private static final double FLY_SWELL = 0.55;

    // The image, decoded and scaled ONCE for every heart there will ever be. A Bitmap and nothing
    // else: holding a Context or a Resources here would outlive the screen that lent it.
    private static Bitmap _image = null;

    /** Decodes the shared image and scales it to the size it is drawn at. Called once. */
    public static void loadSheet(Resources res) {

        if( (_image != null) || (res == null) )
            return;

        Bitmap full = BitmapFactory.decodeResource(res, R.drawable.eg_heart);

        if( full == null )
            return;

        int side = Math.round(SIZE_DP * res.getDisplayMetrics().density);

        if( side < 1 )
            side = 1;

        Bitmap scaled = Bitmap.createScaledBitmap(full, side, side, true);

        // The big one has no further use: kept as well as the small one it would be the same
        // picture twice in memory, for the whole life of the process.
        if( scaled != full )
            full.recycle();

        _image = scaled;
    }

    private static final Paint PAINT = new Paint();

    static {
        PAINT.setAntiAlias(true);
        PAINT.setFilterBitmap(true);
    }

    private final GameSurface gameSurface;

    // Where it was thrown FROM -- the mark that grew it -- and where it is going. It flies the one
    // to the other before it settles: a heart that simply appeared thirty metres away said nothing
    // about where it came from, and the player had no reason to connect the two.
    private final double fromLatitude;
    private final double fromLongitude;

    private final double latitude;
    private final double longitude;

    private final long bornMs;
    private final long liveMs;

    private boolean flying = true;
    private boolean placed = false;

    private double breath = 1.0;

    // The breath is carried as a PHASE that is added to, never as the age divided by the period
    // of the moment. See update(): with a period that shrinks, the second is not a quickening at
    // all, it is a phase racing ahead faster than the frames can sample it.
    private double breathPhase = 0;
    private long breathAtMs = -1;

    // Reused rather than made afresh: this is walked through on every frame.
    private final Rect src = new Rect();
    private final RectF dst = new RectF();

    public GameHeart(GameSurface gameSurface,
                     double fromLongitude, double fromLatitude,
                     double longitude, double latitude) {

        super();

        this.gameSurface = gameSurface;

        this.fromLongitude = fromLongitude;
        this.fromLatitude = fromLatitude;

        this.longitude = longitude;
        this.latitude = latitude;

        // The image is already the size it is drawn at, so the box the collider measures is the
        // box one can see.
        setImage(_image, 1, 1);

        this.anchor = Anchor.MIDDLE_MIDDLE;

        collider.setDebugColor(Color.parseColor("#FF4D7E"));   // the hearts, in their own pink
        collider.setHitCoef(HIT_COEF);
        collider.setDamageCoef(HIT_COEF);
        collider.setDebugDrawMasks(GameCollider.DEBUG_DRAW_REF | GameCollider.DEBUG_DRAW_HIT);

        this.liveMs = GameLevels.HEART_LIFE_S * 1000L;
        this.bornMs = System.nanoTime() / 1000000;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    /** Settled on the ground and there to be taken. In the air it is nobody's yet. */
    public boolean isPlaced() {
        return placed && !flying;
    }

    @Override
    public void contextScale() {
    }

    /** One step. True when its time is up and it must be taken off the list. */
    @Override
    public void update() {

        long age = (System.nanoTime() / 1000000) - bornMs;

        flying = (age < FLY_MS);

        place();

        if( flying )
            return;

        long left = liveMs - (age - FLY_MS);

        // The breath quickens over the last seconds: nothing else says it is about to go.
        long period = BREATH_MS;

        if( left < HURRY_MS ) {
            double hurry = (left > 0) ? ((double) left / HURRY_MS) : 0;
            period = BREATH_FAST_MS + (long) ((BREATH_MS - BREATH_FAST_MS) * hurry);
        }

        // The phase is ADDED TO, a frame at a time, at the rate of the moment.
        //
        // Written as sin(2*PI*age/period) it was wrong in a way that showed: with the period
        // shrinking, the quotient does not merely speed up, it races -- the age is fifty seconds
        // by then and divides by an ever smaller number, so the phase was advancing by more than
        // two whole turns per frame. Sampled twenty-five times a second, that reads as a beat
        // that wanders, slows down and runs again, which is exactly what was seen. Accumulated
        // instead, the rate is never more than one turn per quarter of a second and the
        // quickening is the quickening it was meant to be.
        long step = (breathAtMs < 0) ? 0 : (age - breathAtMs);

        breathAtMs = age;

        breathPhase += (2 * Math.PI * step) / period;

        // Kept inside one turn: nothing here needs to remember how many it has made.
        if( breathPhase > (2 * Math.PI) )
            breathPhase -= (2 * Math.PI) * Math.floor(breathPhase / (2 * Math.PI));

        breath = 1.0 + (BREATH * Math.sin(breathPhase));
    }

    /** True once it has had its minute. */
    public boolean isFinished() {
        return ((System.nanoTime() / 1000000) - bornMs) >= (liveMs + FLY_MS);
    }

    // Its pixels in the chunk of the moment. A heart dropped in one chunk and walked at in the
    // next has to be where the ground is, not where the old pixels were.
    private void place() {

        Chunk chunk = gameSurface.getMap();

        if( chunk == null ) {
            placed = false;
            return;
        }

        this.x = chunk.getX(longitude, gameSurface.getAreaWidth());
        this.y = chunk.getY(latitude, gameSurface.getAreaHeight());

        placed = true;

        if( !flying )
            return;

        // In the air: along the line from the mark to where it lands, lifted by an arc and swollen
        // at the top of it. Worked out from the two ends every frame rather than stored, for the
        // same reason as the ground position -- the chunk under it may have been swapped.
        double x0 = chunk.getX(fromLongitude, gameSurface.getAreaWidth());
        double y0 = chunk.getY(fromLatitude, gameSurface.getAreaHeight());

        long age = (System.nanoTime() / 1000000) - bornMs;

        double done = (double) age / FLY_MS;

        if( done < 0 )
            done = 0;
        else if( done > 1 )
            done = 1;

        // Thrown: quick off the mark, slowing as it arrives.
        double along = 1.0 - ((1.0 - done) * (1.0 - done));

        double reach = Math.hypot(this.x - x0, this.y - y0);

        this.x = x0 + ((this.x - x0) * along);
        this.y = y0 + ((this.y - y0) * along) - (Math.sin(Math.PI * along) * reach * ARC_PART);

        breath = 1.0 + (FLY_SWELL * Math.sin(Math.PI * along));
    }

    @Override
    public void draw(Canvas canvas, int layer) {

        if( (canvas == null) || (_image == null) || !placed )
            return;

        if( layer == 0 )
            collider.draw(canvas, layer);

        if( layer != 1 )
            return;

        float half = (float) (width * breath) / 2f;

        float cx = gameSurface.getCanvasX(this.x);
        float cy = gameSurface.getCanvasY(this.y);

        src.set(0, 0, _image.getWidth(), _image.getHeight());
        dst.set(cx - half, cy - half, cx + half, cy + half);

        canvas.drawBitmap(_image, src, dst, PAINT);
    }

    @Override
    public boolean isVisible() {
        return gameSurface.isVisible(this);
    }
}
