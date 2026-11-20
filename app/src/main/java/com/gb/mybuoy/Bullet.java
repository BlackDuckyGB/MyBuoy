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

/**
 * A projectile fired by the player or by a mark, in the hidden game.
 *
 * All bullet types share ONE bitmap, loaded once and held statically: the sheet holds four types,
 * one per row, four frames each. A bullet is created dozens of times a minute and dies seconds
 * later, so decoding its sheet per instance would be absurd -- and every instance would hold its
 * own copy of the same pixels. Subclasses therefore declare which ROW is theirs and nothing more
 * about the image.
 *
 * What a subclass does declare is what makes it a weapon rather than a picture: its speed, its
 * damage, its range, and the level from which it exists.
 */
public abstract class Bullet extends GameObject {

    // The sheet, shared by every bullet of every type. Loaded once, never released: it is a few
    // dozen kilobytes and the game may ask for it again at any moment.
    private static Bitmap _sheet = null;

    // Sheet layout: four projectile types, four frames each.
    private static final int SHEET_ROWS = 4;
    private static final int SHEET_COLS = 4;

    // Frames 0, 1 and 2 are the flight, played back and forth 0, 1, 2, 1, 0… Frame 3 is the
    // projectile at rest, which only lands on the map and is not part of the loop.
    private static final int FLIGHT_FRAMES = 3;
    private static final long FRAME_MS = 80;

    // Where the drawn object sits in its cell, as a fraction of it. Measured on the sheet, not
    // guessed: the four bodies are centred between y = 34 and y = 38 out of 96.
    //
    // Of THIS sheet. A second family of things that fly is drawn on a sheet of its own, with its
    // bodies sitting somewhere else in the square, so the figure travels with the bullet rather
    // than with the class -- and it is handed in at birth rather than asked of an overridable
    // method, which during a constructor would be asked of an object not yet built.
    private static final float PIVOT_X = 0.49f;
    private static final float PIVOT_Y = 0.375f;

    private final float pivotX;
    private final float pivotY;

    protected final GameSurface gameSurface;

    // Direction of travel, a unit vector in area coordinates. Set once at birth: a bullet does
    // not steer.

    // Kept for the drawing: the sheet draws every projectile pointing up, and the canvas is turned
    // by this much to aim it.
    private final double angleDeg;

    // The stream this bullet's own sound is playing on. Each bullet holds its own, so that the
    // shot of one is never cut by the impact of another.
    private int streamId = 0;

    private boolean firstUpdate = true;

    private double travelled = 0;          // area pixels covered so far
    private boolean finished = false;

    private int frame = 0;
    private boolean frameForward = true;   // the back-and-forth of the flight loop
    private long lastFrameNs = -1;
    private long lastMoveNs = -1;

    /** Decodes the shared sheet. Called once, from the surface that will draw the bullets. */
    public static void loadSheet(Resources res) {
        if (_sheet == null)
            _sheet = BitmapFactory.decodeResource(res, R.drawable.eg_bullets);
    }

    // Body and reach, as fractions of the reference circle, which is the half-diagonal of the
    // sprite box. Equal: a damage zone shorter than the body would never reach anything, since
    // two solid things stop when their bodies meet.
    private static final double HIT_COEF = 0.3;
    private static final double DAMAGE_COEF = 0.3;

    protected Bullet(GameSurface gameSurface, double x, double y, double angleDeg) {
        this(gameSurface, _sheet, x, y, angleDeg);
    }

    /**
     * The same, off a sheet of its own.
     *
     * Everything about a projectile -- the four frames, the back and forth, the turning towards
     * its heading -- is the same whatever is drawn on it, so a second family of things that fly
     * brings its own pixels and nothing else. The layout must match: four rows of four.
     */
    protected Bullet(GameSurface gameSurface, Bitmap sheet, double x, double y, double angleDeg) {
        this(gameSurface, sheet, x, y, angleDeg, PIVOT_X, PIVOT_Y);
    }

    /** The same again, for a sheet whose bodies do not sit where this one's do. */
    protected Bullet(GameSurface gameSurface, Bitmap sheet, double x, double y, double angleDeg,
                     float pivotX, float pivotY) {
        super(sheet, SHEET_ROWS, SHEET_COLS, x, y);

        this.pivotX = pivotX;
        this.pivotY = pivotY;

        this.gameSurface = gameSurface;
        this.anchor = Anchor.MIDDLE_MIDDLE;   // a bullet hangs on nothing: it IS its own centre

        // The point asked for is where the BODY must be, not where its cell must be.
        //
        // The sprites sit high in their square -- the tomato is centred at 38 out of 96 -- because
        // the cutting tool bottom-aligns a body and its trail together. The drawn body is
        // therefore an eighth of a cell above the middle of it, and placing the cell on the muzzle
        // left the visible shot beside the line of fire. Worse, that gap turns with the shot, so
        // it fell on one side or the other depending on the angle: two shots aimed symmetrically
        // either side of a target were not symmetrical at all.
        this.x += width * (0.5 - pivotX);
        this.y += height * (0.5 - pivotY);

        // Same convention as everywhere else in the application: the angle is a heading in degrees
        // from north, and north is up, which is why the Y component is negative.
        this.angleDeg = angleDeg;

        // The direction is the one GameObject carries for everything that moves; a bullet simply
        // never changes it once it has left.
        applyDirection(Math.sin(Math.toRadians(angleDeg)), -Math.cos(Math.toRadians(angleDeg)));

        collider.setDebugColor(Color.WHITE);

        // The body is tighter than the picture it is measured from: the reference circle is the
        // half-diagonal of the sprite box, and a sprite is never painted edge to edge. Eight
        // tenths of it is what the game actually touches and is touched on -- the same figure as
        // the creatures, so that nobody meets anybody on a different footing.
        collider.setHitCoef(HIT_COEF);
        collider.setDamageCoef(DAMAGE_COEF);

        // A bullet is not walked into, it wounds: what is worth seeing on it is its damage
        // zone, the circle that decides whether it has touched.
        collider.setDebugDrawMasks(GameCollider.DEBUG_DRAW_REF | GameCollider.DEBUG_DRAW_DAMAGE);

    }

    // --- what each type of bullet is ------------------------------------------------------------

    // What a bullet is looking for, which is the only thing that makes it a weapon rather than a
    // drawing: the creatures, and the scenery it flies through.
    protected java.util.List<GameObject> getColliderTargets() {
        return gameSurface.getTargets();
    }

    // And the collider follows the body for the same reason: what it covers has to be what is
    // seen, or a shot would be judged to have missed while it was drawn right through the target.
    @Override
    protected double getColliderOffsetX() {
        return width * (pivotX - 0.5);
    }

    @Override
    protected double getColliderOffsetY() {
        return height * (pivotY - 0.5);
    }

    /** Row of the shared sheet: 0 = shell, 1 = throwing star, 2 = tomato, 3 = egg. */
    public abstract int getSpriteRow();

    /**
     * How fast it flies, in KILOMETRES PER HOUR -- the unit the whole application speaks, and the
     * one the creatures already use. It used to be area pixels per millisecond, which is not a
     * speed at all: the same figure meant a different thing at every zoom, since an area pixel is
     * worth more or fewer metres depending on the chunk.
     */
    public abstract double getSpeedKmh();

    /** Lives taken from whatever it hits. */
    public abstract int getDamage();

    /** How far it flies before giving up, in area pixels. */
    /** How far it flies before giving up, in METRES, for the same reason. */
    public abstract double getRangeM();

    /** The game level from which this bullet exists at all. */
    public abstract int getMinLevel();

    // --- life -----------------------------------------------------------------------------------

    public boolean isFinished() {
        return finished;
    }

    // --- its two sounds, and nobody else's -----------------------------------------------------

    /**
     * The sound this kind of shot makes when it leaves, or 0 to use the game's default bloop.
     * A shell is not a tomato: it is not thrown, it is fired.
     */
    protected int getFireSoundRes() {
        return 0;
    }

    /**
     * The sound that announces the end of the game when THIS shot is the one that finishes the
     * player, or 0 for the game's own. A doll that shoots you down should be the one to say so.
     */
    protected int getGameOverSoundRes() {
        return 0;
    }

    /**
     * What is heard when it arrives, or 0 for the game's own impact.
     *
     * NEGATIVE for silence, here as for the shot: a thing that is thrown to be left on the ground
     * announces itself when it lands, with its own voice, and the generic knock underneath it
     * would be heard as a mistake.
     */
    protected int getHitSoundRes() {
        return 0;
    }

    // What a shot brought down in flight is worth.
    private static final int SHOOTDOWN_POINTS = 50;

    /** How loudly that shot is heard, from 0 to 1. */
    protected float getFireSoundVolume() {
        return 1.0f;
    }

    private void playFire() {

        int res = getFireSoundRes();

        if( res < 0 )
            return;

        gameSurface.stopSound(streamId);

        streamId = (res == 0) ? gameSurface.playSoundFire()
                              : gameSurface.playSound(res, getFireSoundVolume());
    }

    private void playHit() {

        int res = getHitSoundRes();

        if( res < 0 )
            return;

        gameSurface.stopSound(streamId);

        streamId = (res == 0) ? gameSurface.playSoundHit()
                              : gameSurface.playSound(res, getFireSoundVolume());
    }

    public void contextScale() {
    }

    public void update() {
        long now = System.nanoTime();

        if (lastMoveNs == -1) {
            lastMoveNs = now;
            lastFrameNs = now;
        }

        double deltaMs = (now - lastMoveNs) / 1000000.0;
        lastMoveNs = now;

        // km/h to area pixels per millisecond, asked for every frame on purpose: the scale
        // changes with the chunk and the zoom, and a shell fired at 150 km/h must keep flying at
        // 150 km/h whatever the map is doing.
        double pxPerMs = (getSpeedKmh() / 3.6) * gameSurface.pixelsPerMeter() / 1000.0;

        double step = pxPerMs * deltaMs;
        this.x += dirX * step;
        this.y += dirY * step;
        travelled += step;

        // A bullet dies in two ways, and neither of them is heard if it dies on this very first
        // update: born into something, it was never on screen long enough to be seen, and a sound
        // with nothing to look at is just a noise.
        boolean first = firstUpdate;
        firstUpdate = false;

        // It hits something. That is the point of it, and it is heard -- unless it is that first
        // update.
        collider.update();

        // What it WOUNDS, which is its damage zone against their body -- not its own body against
        // theirs. The two were the same question until the zones were told apart, and a shot asks
        // the first one: a tomato harms with its flesh, not with the square it is drawn in.
        java.util.List<GameObject> touched = collider.getHurts();

        if (touched != null) {

            finished = true;

            // And it actually costs something. The damage used to be declared and never read: a
            // bullet died on contact and left its target untouched.
            for (GameObject object : touched)
                strike(object);

            if (!first)
                playHit();

            onFinished(true);

            return;
        }

        // Or it simply runs out: out of range, or out of the world. Both have to be checked, since
        // a bullet fired at the edge would otherwise travel for ever outside the area. Silent
        // either way: a shot that misses has nothing to say.
        if (travelled >= (getRangeM() * gameSurface.pixelsPerMeter())
                || this.x < 0 || this.x > gameSurface.getAreaWidth()
                || this.y < 0 || this.y > gameSurface.getAreaHeight()) {
            finished = true;
            onFinished(false);
            return;
        }

        // The shot is heard here and not at birth, and only once everything that could have killed
        // the bullet has been ruled out: a bullet that is already finished must not announce
        // itself.
        if (first)
            playFire();

        if ((now - lastFrameNs) / 1000000 >= FRAME_MS) {
            lastFrameNs = now;

            // Back and forth between the three flight frames, never touching the rest frame.
            if (frameForward) {
                frame++;
                if (frame >= FLIGHT_FRAMES - 1)
                    frameForward = false;
            } else {
                frame--;
                if (frame <= 0)
                    frameForward = true;
            }
        }
    }

    /**
     * What this shot does to one thing it has touched.
     *
     * A wound and a score by default, which is what a shot is for. Apart for whatever flies in
     * order to be LEFT somewhere: it carries its own idea of what touching means, and this is the
     * one door it has to go through to say so.
     */
    protected void strike(GameObject object) {

        // One shot bringing down another: the thing is hard to do, so it pays.
        if (object instanceof Bullet)
            ScorePopup.score(gameSurface, this.x, this.y, SHOOTDOWN_POINTS);

        if (object instanceof ChibiCharacter)
            ((ChibiCharacter) object).takeShot(getDamage(), this.x, this.y);

        else if (object instanceof MapGPSPos) {
            ((MapGPSPos) object).onHit(this.x, this.y);
            AppManager.getInstance().takePlayerHits(getDamage(), getGameOverSoundRes());
        }
    }

    /**
     * Its flight is over, and why.
     *
     * Nothing for a shot, which simply stops existing. Everything for a thing that was thrown in
     * order to stay where it stopped: BY IMPACT it has already spent itself on whatever it met,
     * otherwise it has arrived where it was aimed.
     */
    protected void onFinished(boolean byImpact) {
    }

    public void draw(Canvas canvas, int layer) {
        if (finished || (layer != 0))
            return;

        collider.draw(canvas, layer);

        Bitmap bitmap = this.createSubImageAt(getSpriteRow(), frame);

        if (bitmap == null)
            return;

        float left = gameSurface.getCanvasX(getAnchorX());
        float top = gameSurface.getCanvasY(getAnchorY());

        // The sheet draws every projectile pointing north, so the canvas is turned by the heading
        // to aim it. Around the centre of the DRAWN OBJECT and not the centre of the cell: the
        // sprites sit high in their square -- the tomato is centred at y = 38 out of 96, the four
        // of them between 34 and 38 -- because the cutting tool bottom-aligns a body and its
        // trail together. Turning about the cell centre would make the object orbit by ten pixels.
        float turnX = left + width * this.pivotX;
        float turnY = top + height * this.pivotY;

        canvas.save();
        canvas.rotate((float) angleDeg, turnX, turnY);
        canvas.drawBitmap(bitmap, left, top, null);
        canvas.restore();
    }

    public boolean isVisible() {
        return gameSurface.isVisible(this);
    }
}
