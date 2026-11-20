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

import java.util.ArrayList;
import java.util.List;

/**
 * The ring a pole throws out when it is taken.
 *
 * A ring and not a disc: seventy metres across is eight hundred pixels, and a filled one would
 * hide the very thing it is there to show -- the creatures going down inside it. It leaves from
 * the foot of the pole, widens, and fades as it goes: the LAST frame is the widest, because that
 * is the one the eye keeps as "this is what it covered".
 *
 * Two rings rather than one, the second a breath behind and fainter. A single circle reads as a
 * circle; two read as a wave.
 *
 * It travels at a SPEED, so a wide ring lasts longer than a narrow one instead of simply going
 * faster -- which is what it would do on a fixed number of frames, and at seventy metres that was
 * a flash nobody had time to see. Under half a second it is given half a second anyway: even the
 * poorest pole deserves to be watched.
 *
 * And it strikes AS IT PASSES, not all at once when it is born: what stands near the pole goes
 * first and what stands at the edge goes much later, so the wave is seen to mow them down in
 * order instead of a row of explosions appearing together. Each creature is struck once -- the
 * front goes by, and it is behind it for good.
 *
 * Counted in MILLISECONDS and not in frames, so that it lasts as long in low-resource mode, where
 * the thread runs at fifteen frames a second instead of twenty-five, as it does anywhere else.
 * The sound is stretched to the same length, which drops its pitch for nothing: a wider ring
 * sounds deeper.
 *
 * Owned by the surface: whatever sets one off drops it there and forgets it.
 */
public class Shockwave {

    // The wait between the pole being taken and the ring leaving it. The coin has time to be
    // heard and understood, and the blast is then its own event rather than a layer over it.
    //
    // A pole's wait, and not every ring's: a mine has no coin to wait for, and the thing that
    // sets it off is standing on it. Whoever makes the ring says which it is.
    private static final long POLE_DELAY_MS = 400;

    // How fast the front travels, in metres a second. Not the speed of sound -- this is a cartoon
    // -- but fast enough to read as a blast rather than as a ripple.
    private static final double SPEED_M_PER_S = 70.0;

    // However small the ring, it never crosses it quicker than this.
    private static final long EXPAND_MIN_MS = 500;

    // And it stays, fading, for this much of its own crossing time once it has stopped widening.
    private static final double FADE_FRAC = 0.3;

    // The second ring, this far behind the first.
    private static final long TRAIL_MS = 70;

    // The length of eg_pole_blast as it sits in res/raw. The rate it is read at is this over the
    // length of the ring, which SoundPool will take between a half and twice.
    private static final long POLE_SOUND_MS = 900;

    private static final float BLAST_VOLUME = 0.9f;

    private static final float STROKE_DP = 3.5f;

    // The gold of the scores worth noticing: the ring and the figure that set it off are one
    // event, and they have no business being two colours.
    private static final int POLE_COLOUR = Color.rgb(176, 112, 0);

    // How solid the line is when the ring leaves, and when it has finished widening. It goes on
    // fading to nothing from there.
    private static final int ALPHA_NEW = 255;
    private static final int ALPHA_WIDE = 180;

    private static final Paint RING = new Paint();
    private static final Paint HALO = new Paint();

    static {
        RING.setAntiAlias(true);
        RING.setStyle(Paint.Style.STROKE);

        // A white line behind the gold one, as under the score figures: gold alone is lost on a
        // pale road, and the ring has to hold on anything.
        HALO.setAntiAlias(true);
        HALO.setStyle(Paint.Style.STROKE);
        HALO.setColor(Color.WHITE);
    }

    private final GameSurface gameSurface;

    private final double x;
    private final double y;

    // Where the front stops, in area pixels.
    private final double radius;

    private final int damage;
    private final float stroke;

    // What kind of ring this one is: its colour, its voice, and how long it holds back before it
    // leaves. Carried per ring rather than written into the class, since the poles and the mines
    // share everything about a blast except what it looks and sounds like.
    private final int colour;
    private final int soundRes;
    private final long delayMs;

    private final long bornMs;
    private final long expandMs;
    private final long liveMs;
    private final float rate;

    // Set on the frame the ring leaves, which is also when it is heard.
    private boolean gone = false;

    // How far the front has got, in area pixels.
    private double front = 0;

    // The ones it has already struck: the wave goes through a creature once.
    private final List<ChibiCharacter> struck = new ArrayList<ChibiCharacter>();

    /** A pole's ring: gold, its own voice, and the wait that lets the coin be heard. */
    public Shockwave(GameSurface gameSurface, double x, double y, double radiusM, int damage) {

        this(gameSurface, x, y, radiusM, damage,
             POLE_COLOUR, R.raw.eg_pole_blast, POLE_SOUND_MS, POLE_DELAY_MS);
    }

    /** At that spot, that wide on the ground, in that colour and that voice. */
    public Shockwave(GameSurface gameSurface, double x, double y, double radiusM, int damage,
                     int colour, int soundRes, long soundMs, long delayMs) {

        this.gameSurface = gameSurface;

        this.colour = colour;
        this.soundRes = soundRes;
        this.delayMs = delayMs;

        this.x = x;
        this.y = y;
        this.damage = damage;

        this.radius = radiusM * gameSurface.pixelsPerMeter();

        long crossing = (long) ((radiusM / SPEED_M_PER_S) * 1000.0);

        this.expandMs = (crossing < EXPAND_MIN_MS) ? EXPAND_MIN_MS : crossing;
        this.liveMs = expandMs + (long) (expandMs * FADE_FRAC);

        this.rate = (float) ((double) soundMs / liveMs);

        this.stroke = STROKE_DP * gameSurface.getResources().getDisplayMetrics().density;

        this.bornMs = System.nanoTime() / 1000000;
    }

    /** One step. True when it has had its life. */
    public boolean update() {

        long age = (System.nanoTime() / 1000000) - bornMs - delayMs;

        // Nothing to see yet: the pole is still ringing.
        if( age < 0 )
            return false;

        if( !gone ) {
            gone = true;
            gameSurface.playSoundOnce(soundRes, BLAST_VOLUME, rate);
        }

        front = radius * reached(age);

        strike();

        return (age >= liveMs);
    }

    // How much of the way the front has gone at that age: quick away and settling in, like the
    // figures that climb -- thrown out rather than drawn out.
    private double reached(long age) {

        double done = (double) age / expandMs;

        if( done > 1.0 )
            done = 1.0;

        return 1.0 - ((1.0 - done) * (1.0 - done));
    }

    // Everything the front has reached since the last frame, and has not had yet.
    private void strike() {

        List<ChibiCharacter> chibis = gameSurface.getChibis();

        for( int i = 0; i < chibis.size(); i++ ) {

            ChibiCharacter chibi = chibis.get(i);

            if( (chibi == null) || struck.contains(chibi) )
                continue;

            double dx = (chibi.getX() + ((double) chibi.getWidth() / 2)) - x;
            double dy = (chibi.getY() + ((double) chibi.getHeight() / 2)) - y;

            if( ((dx * dx) + (dy * dy)) > (front * front) )
                continue;

            struck.add(chibi);

            chibi.takeBlast(damage, x, y);
        }
    }

    public void draw(Canvas canvas) {

        if( (canvas == null) || !gone )
            return;

        long age = (System.nanoTime() / 1000000) - bornMs - delayMs;

        float cx = gameSurface.getCanvasX(x);
        float cy = gameSurface.getCanvasY(y);

        ring(canvas, cx, cy, front, age);

        // The one behind, fainter, and only once it has somewhere to be.
        if( age > TRAIL_MS )
            ring(canvas, cx, cy, radius * reached(age - TRAIL_MS), age + TRAIL_MS);
    }

    private void ring(Canvas canvas, float cx, float cy, double at, long age) {

        if( at <= 0 )
            return;

        int alpha;

        if( age <= expandMs ) {

            // Solid at first and barely less so by the time it is wide.
            double done = (double) age / expandMs;

            alpha = (int) (ALPHA_NEW - ((ALPHA_NEW - ALPHA_WIDE) * done));

        } else {

            double left = 1.0 - ((double) (age - expandMs) / (liveMs - expandMs));

            if( left <= 0 )
                return;

            alpha = (int) (ALPHA_WIDE * left);
        }

        HALO.setStrokeWidth(stroke * 2f);
        HALO.setAlpha((int) (alpha * 0.45));
        canvas.drawCircle(cx, cy, (float) at, HALO);

        // The colour is set here and not once at birth: the two Paints are shared by every ring
        // on the map, and a gold one and a red one may well be widening at the same moment.
        RING.setColor(colour);
        RING.setStrokeWidth(stroke);
        RING.setAlpha(alpha);
        canvas.drawCircle(cx, cy, (float) at, RING);
    }
}
