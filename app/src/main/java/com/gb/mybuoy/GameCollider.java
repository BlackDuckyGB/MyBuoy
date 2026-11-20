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
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/**
 * The collision shape of a {@link GameObject}: a circle that encloses the whole of its bitmap.
 *
 * It holds a reference to the object it belongs to and works out its centre and its radius ON
 * DEMAND, rather than caching them and refreshing them from the setters. That is deliberate:
 * subclasses assign {@code this.x} directly -- ChibiCharacter does it on every frame -- so a
 * cached collider would be wrong for everything that moves, and there would be no reliable place
 * to invalidate it. Derived, it cannot go stale. The reference back to the owner makes a simple
 * cycle, which the collector reclaims without help.
 *
 * The radius is the HALF-DIAGONAL of the sprite box, which is what it takes for a circle to cover
 * a rectangle entirely, plus an adjustable margin. It is expressed in area pixels, the units the
 * objects themselves live in.
 */
public class GameCollider {

    private final GameObject owner;

    // Extra room around the sprite box, in area pixels. Adjustable because the sprites do not fill
    // their box the same way: an icon padded with transparency wants a smaller circle than a
    // creature drawn edge to edge.
    private float margin = 0f;

    // Colour of the debug outline. Set by the owner so that the three families -- the player, the
    // creatures, the marks -- can be told apart at a glance on screen.
    private int debugColor = Color.MAGENTA;

    private final Paint stroke = new Paint();
    private final Paint fill = new Paint();

    // Reused rather than made afresh: this is walked through on every frame, for every object.
    private final RectF arc = new RectF();

    // Alpha of the debug fill. Enough to spot the disc over a busy map, light enough to still read
    // what is underneath -- the whole point being to check that the circle covers the sprite.
    private static final int FILL_ALPHA = 0x50;

    // The derived zones are drawn in the SAME colour as the reference circle -- the colour says
    // which family the object belongs to, and that does not change from one zone to the next --
    // and they are told apart by how faint they are. They are wider, so at the reference alpha
    // they would swamp everything under them.
    private static final int ZONE_FILL_ALPHA = 0x18;

    // What the reference radius is multiplied by to get each zone. One means "the collider
    // itself", which is what they all are until somebody says otherwise.
    //
    // The reference circle is the body: what the sprite takes up, and what cannot be walked
    // through. The three others hang off it -- how far this object can be HIT from, how far it
    // DAMAGES, and how far it SEES -- so that a kind of creature is described by three numbers
    // rather than by three shapes.
    private double hitCoef = 1.0;
    private double damageCoef = 1.0;
    private double viewCoef = 1.0;

    // The hit zone may be stated in METRES instead, which overrides the coefficient above.
    //
    // A coefficient describes a BODY: so much of the picture, which is the right unit for a
    // creature -- a big sprite is a big creature. It is the wrong one for something measured on
    // the ground, where two metres are two metres whatever the icon drawn above them happens to
    // be, and whatever the zoom turns a metre into on screen.
    //
    // Zero means "use the coefficient", which is what everything does until it says otherwise.
    private double hitRadiusM = 0;

    // How wide the view is, in degrees EITHER SIDE of the way the object faces: 45 means 45 to the
    // right and 45 to the left, so ninety degrees of sight, and 180 means it sees all round it.
    private double viewAngleDeg = 30.0;

    // Which of the four circles the debug drawing shows. The body alone by default: an object with
    // nothing to say about the rest draws what it has always drawn.
    public static final int DEBUG_DRAW_REF    = 1;
    public static final int DEBUG_DRAW_HIT    = 2;
    public static final int DEBUG_DRAW_DAMAGE = 4;
    public static final int DEBUG_DRAW_VIEW   = 8;

    private int debugDrawMasks = DEBUG_DRAW_REF;

    public GameCollider(GameObject owner) {
        this.owner = owner;

        stroke.setAntiAlias(true);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(3f);

        fill.setAntiAlias(true);
        fill.setStyle(Paint.Style.FILL);
    }

    public float getMargin() {
        return margin;
    }

    public void setMargin(float margin) {
        this.margin = margin;
    }

    public int getDebugColor() {
        return debugColor;
    }

    public void setDebugColor(int debugColor) {
        this.debugColor = debugColor;
    }

    /**
     * Centre of the circle, in area coordinates. The offsets are the drawing corrections some
     * objects apply to their bitmap -- a mark shifts its icon by a fraction of its size -- and
     * they have to be applied here too, or the circle would sit beside what it is meant to cover.
     */
    public double getCenterX(double offsetX) {
        return owner.getAnchorX() + (double) owner.getWidth() / 2 + offsetX;
    }

    public double getCenterY(double offsetY) {
        return owner.getAnchorY() + (double) owner.getHeight() / 2 + offsetY;
    }

    /** Half-diagonal of the sprite box plus the margin: the smallest circle that covers it all. */
    public double getRadius() {
        return refRadius();
    }

    // The real work: the class measures itself through this one, never through its own public
    // door.
    private double refRadius() {
        double w = owner.getWidth();
        double h = owner.getHeight();

        return Math.sqrt(w * w + h * h) / 2.0 + margin;
    }

    /** How far this object can be hit from: the reference circle times its hit coefficient. */
    public double getHitRadius() {
        return hitRadius();
    }

    // The real work, like refRadius(): everything in here measures the hit zone through this one.
    private double hitRadius() {

        if( hitRadiusM > 0 ) {
            double pxPerM = owner.getPixelsPerMeter();

            if( pxPerM > 0 )
                return hitRadiusM * pxPerM;
        }

        return refRadius() * hitCoef;
    }

    public double getHitRadiusMeters() {
        return hitRadiusM;
    }

    /** States the hit zone in metres, whatever the sprite. Zero goes back to the coefficient. */
    public void setHitRadiusMeters(double hitRadiusM) {
        this.hitRadiusM = hitRadiusM;
    }

    /** How far it hurts whatever it touches. */
    public double getDamageRadius() {
        return refRadius() * damageCoef;
    }

    /** How far it sees. */
    public double getViewRadius() {
        return refRadius() * viewCoef;
    }

    public double getHitCoef() {
        return hitCoef;
    }

    public void setHitCoef(double hitCoef) {
        this.hitCoef = hitCoef;
    }

    public double getDamageCoef() {
        return damageCoef;
    }

    public void setDamageCoef(double damageCoef) {
        this.damageCoef = damageCoef;
    }

    public double getViewCoef() {
        return viewCoef;
    }

    public void setViewCoef(double viewCoef) {
        this.viewCoef = viewCoef;
    }

    public double getViewAngleDeg() {
        return viewAngleDeg;
    }

    /** Half the field of view, in degrees: 0 sees nothing, 180 sees all round. */
    public void setViewAngleDeg(double viewAngleDeg) {

        if( viewAngleDeg < 0 )
            viewAngleDeg = 0;

        if( viewAngleDeg > 180 )
            viewAngleDeg = 180;

        this.viewAngleDeg = viewAngleDeg;
    }

    public int getDebugDrawMasks() {
        return debugDrawMasks;
    }

    /** Which circles to draw, as a combination of the DEBUG_DRAW_* flags. */
    public void setDebugDrawMasks(int debugDrawMasks) {
        this.debugDrawMasks = debugDrawMasks;
    }

    // What the last update() found, or null when it found nothing. Null and not an empty list, on
    // purpose: the caller asks "did I touch something", and a null answer makes that a single
    // test.
    //
    // Two questions, two lists, because they are not the same question.
    //
    // TOUCHED is my HIT zone against theirs: two bodies meeting, which is what stops one walking
    // through the other. Symmetric -- if I touch you, you touch me.
    //
    // HURT is my DAMAGE zone against their HIT zone: what I harm. Asymmetric on purpose, since
    // what wounds and what is wounded are two different surfaces: the tip of a tomato is not the
    // tomato. Each object works out its own.
    private List<GameObject> hits = null;
    private List<GameObject> hurts = null;

    // SEEN is my VIEW zone against their HIT zone, narrowed to the slice I am facing. Same shape
    // as the one drawn, so what a creature reacts to is what can be checked on screen.
    private List<GameObject> seen = null;

    // How many were dropped from that list because something stood in front of them. Kept for the
    // debug trace, which is the only way to see a masking happen.
    private int hiddenCount = 0;

    /**
     * Tests this collider against what its owner cares about, and remembers what it overlaps.
     *
     * No argument, like GameObject.update(): the collider knows its owner, and the owner knows
     * what it is looking for.
     *
     * Two circles touch when the distance between their centres is smaller than the sum of their
     * radii. Compared SQUARED, because a square root per pair per frame buys nothing: the
     * comparison holds just as well without it.
     *
     * The owner itself is skipped, obviously, and so is anything with no size -- an object whose
     * bitmap has not been set yet would otherwise sit at the origin with a radius of zero and
     * collide with whatever passes over the corner of the map.
     */
    public void update() {

        hits = null;
        hurts = null;
        seen = null;
        hiddenCount = 0;

        List<GameObject> others = owner.getColliderTargets();

        if (others == null)
            return;

        // The same circle the drawing shows and a tap tests: with the owner's drawing correction
        // applied, or contacts would be looked for beside the sprite they are meant to be on.
        double cx = getCenterX(owner.getColliderOffsetX());
        double cy = getCenterY(owner.getColliderOffsetY());

        // The reference circle is a reference and nothing else: it is what the three zones are
        // measured from, and no test is made on it. Bodies meet on their HIT zones.
        double ref = refRadius();

        double myHit = hitRadius();
        double myDamage = ref * damageCoef;
        double myView = ref * viewCoef;

        // Where this object looks, and how wide. Taken once: it is the same for everything tested.
        double facingDeg = owner.getDirectionDeg();

        // Filled first, then sifted: what is hidden is taken back out below, and that cannot be
        // decided until everything in sight is known.
        for (GameObject other : others) {

            if ((other == null) || (other == owner) || (other.getWidth() <= 0))
                continue;

            GameCollider oc = other.getCollider();
            double dx = oc.getCenterX(other.getColliderOffsetX()) - cx;
            double dy = oc.getCenterY(other.getColliderOffsetY()) - cy;

            double square = dx * dx + dy * dy;

            // Their body, which is what both questions are asked against.
            double otherHit = oc.getHitRadius();

            double touch = myHit + otherHit;

            boolean touching = (square <= touch * touch);

            if (touching) {
                if (hits == null)
                    hits = new ArrayList<GameObject>();
                hits.add(other);
            }

            double harm = myDamage + otherHit;

            if (square <= harm * harm) {
                if (hurts == null)
                    hurts = new ArrayList<GameObject>();
                hurts.add(other);
            }

            double sight = myView + otherHit;

            // Touching is seeing, whichever way the two are looking: you do not walk into
            // somebody without noticing. It goes round the field of view, and round whatever
            // stands between -- nothing can, when two bodies are already against each other.
            if (touching || ((square <= sight * sight) && inSight(dx, dy, facingDeg, otherHit))) {
                if (seen == null)
                    seen = new ArrayList<GameObject>();
                seen.add(other);
            }
        }

        hideWhatIsBehind(cx, cy);
    }

    /**
     * Takes out of the seen list whatever a solid body stands entirely in front of.
     *
     * Entirely: a creature that sticks out from behind a mark is still seen, which is the same
     * rule as the one used at the edge of the field of view. Each candidate is compared with the
     * bodies that are NEARER than it, since only those can be in the way; what is hidden is what
     * lies wholly inside the shadow one of them casts.
     *
     * An object that is not solid sees through everything: the ghost walks through the world and
     * looks through it just the same, and it is the one property that says both.
     */
    private void hideWhatIsBehind(double cx, double cy) {

        if (seen == null)
            return;

        // Two kinds of eye are never blocked: what passes through the world -- the ghost -- and
        // what stands above it -- a giant. The first walks through the obstacle, the second looks
        // over it, and neither has anything hidden from it.
        if (!owner.isSolid() || owner.isGiant())
            return;

        List<GameObject> visible = null;

        for (GameObject candidate : seen) {

            // Against me: seen whatever stands where, and nothing to work out.
            if ((hits != null) && hits.contains(candidate)) {
                if (visible == null)
                    visible = new ArrayList<GameObject>();
                visible.add(candidate);
                continue;
            }

            double candidateX = candidate.getCollider().getCenterX(candidate.getColliderOffsetX());
            double candidateY = candidate.getCollider().getCenterY(candidate.getColliderOffsetY());

            double dx = candidateX - cx;
            double dy = candidateY - cy;

            double distance = Math.sqrt(dx * dx + dy * dy);
            double bearing = Math.toDegrees(Math.atan2(dx, -dy));
            double halfWidth = halfWidthDeg(distance, candidate.getCollider().getHitRadius());

            boolean hidden = false;

            for (GameObject blocker : seen) {

                if ((blocker == candidate) || !blocker.isSolid())
                    continue;

                double blockerX = blocker.getCollider().getCenterX(blocker.getColliderOffsetX());
                double blockerY = blocker.getCollider().getCenterY(blocker.getColliderOffsetY());

                double bx = blockerX - cx;
                double by = blockerY - cy;

                double blockerDistance = Math.sqrt(bx * bx + by * by);

                // Only what stands in front of it can hide it.
                if (blockerDistance >= distance)
                    continue;

                double blockerBearing = Math.toDegrees(Math.atan2(bx, -by));
                double blockerHalfWidth = halfWidthDeg(blockerDistance, blocker.getCollider().getHitRadius());

                double offDeg = ((bearing - blockerBearing + 540) % 360) - 180;

                // Wholly inside its shadow: the far edge of the one behind is still within the
                // edges of the one in front.
                if ((Math.abs(offDeg) + halfWidth) <= blockerHalfWidth) {
                    hidden = true;
                    hiddenCount++;
                    break;
                }
            }

            if (!hidden) {
                if (visible == null)
                    visible = new ArrayList<GameObject>();
                visible.add(candidate);
            }
        }

        seen = visible;
    }

    /**
     * True when the point, in area coordinates, falls inside the HIT circle -- or inside `floor`
     * of the centre, whichever is the wider.
     *
     * The HIT circle and not the reference one: this answers a blow, and a blow is received by the
     * surface that receives, which is what HIT means everywhere else here. It used to measure on
     * the reference circle, which made a finger reach further than a tomato on the same body.
     *
     * The floor is for the finger, and for nothing else. The hit circle of a small sprite is a few
     * millimetres across -- a target no thumb lands on while the thing walks -- so the caller may
     * ask for a minimum. Zero asks for none.
     */
    public boolean contains(double px, double py, double floor) {
        double dx = px - getCenterX(owner.getColliderOffsetX());
        double dy = py - getCenterY(owner.getColliderOffsetY());
        double r = Math.max(getHitRadius(), floor);

        return (dx * dx + dy * dy) <= (r * r);
    }

    /**
     * Did that path cross the HIT circle?
     *
     * A moving point is given as the SEGMENT it travelled and not as the place it ended up. A
     * position arrives about once a second: at six kilometres an hour that is a metre and a half
     * apart, at fifty it is fourteen metres, and two of them land either side of a small circle
     * without either one ever falling inside it. The segment goes through what was walked
     * through, at any speed.
     *
     * Its one weakness is that it is a CHORD: go round a pole fast enough and the straight line
     * between two positions cuts the corner, passing closer than the walk actually did. At a
     * walking pace the chord is a metre and a half long and the question does not arise.
     *
     * Squared throughout, like the rest of this class: the root buys nothing.
     */
    public boolean crossedBy(double x0, double y0, double x1, double y1) {

        return Common.segmentReaches(getCenterX(owner.getColliderOffsetX()),
                                     getCenterY(owner.getColliderOffsetY()),
                                     x0, y0, x1, y1,
                                     hitRadius());
    }

    /** What the last update() overlapped, or null if it overlapped nothing. */
    public List<GameObject> getHits() {
        return hits;
    }

    /** What this object's damage zone reaches, or null when it reaches nothing. */
    public List<GameObject> getHurts() {
        return hurts;
    }

    /**
     * Is the WHOLE of that body in sight -- not a shoulder showing past a corner, all of it?
     *
     * getSeen() answers the loose question, which is the right one for noticing something. This is
     * the strict one, for whoever needs the thing to be entirely in the open: Young-Hee does not
     * start counting until the player stands completely inside her field.
     */
    public boolean isFullySeen(GameObject other) {

        if( (other == null) || !seesAtAll(other) )
            return false;

        GameCollider oc = other.getCollider();

        double cx = getCenterX(owner.getColliderOffsetX());
        double cy = getCenterY(owner.getColliderOffsetY());

        double dx = oc.getCenterX(other.getColliderOffsetX()) - cx;
        double dy = oc.getCenterY(other.getColliderOffsetY()) - cy;

        double distance = Math.sqrt(dx * dx + dy * dy);
        double otherHit = oc.getHitRadius();

        // The far side of it has to be within reach, not just its middle.
        if( (distance + otherHit) > (refRadius() * viewCoef) )
            return false;

        if( viewAngleDeg >= 180 )
            return true;

        double towardsDeg = Math.toDegrees(Math.atan2(dx, -dy));
        double offDeg = ((towardsDeg - owner.getDirectionDeg() + 540) % 360) - 180;

        // And both its edges within the slice.
        return (Math.abs(offDeg) + halfWidthDeg(distance, otherHit)) <= viewAngleDeg;
    }

    // Is it in the loose list at all? Which also answers "nothing is standing in front of it".
    private boolean seesAtAll(GameObject other) {

        if( seen == null )
            return false;

        return seen.contains(other);
    }

    /** What this object can see, or null when it sees nothing. */
    public List<GameObject> getSeen() {
        return seen;
    }

    /** How many bodies were hidden behind another at the last update. */
    public int getHiddenCount() {
        return hiddenCount;
    }

    /**
     * Draws the circle, in debug mode AND only while a game is being played. Silent the rest of the
     * time, and free: colliders are a tuning aid for the game, and have nothing to say to someone
     * who is simply navigating with the debug panel open.
     */
    public void draw(Canvas canvas, int layer) {

        // The cheap question first, and it is almost always the last one asked: this draws nothing
        // unless a game is being played with the debug panel open. Every mark on the map owns a
        // collider and every one of them comes through here on every frame, so anything done
        // before this test is paid for by a player who will never see a single circle.
        AppManager am = AppManager.getInstance();

        if( !am.getDebugMode() || (am.getGameLevel() <= 0) )
            return;

        // The surface is not a parameter either: it is the one the application is drawing on, and
        // AppManager knows it. Same signature as GameObject.draw(), so nothing about a collider
        // looks different from anything else that draws itself.
        GameSurface surface = am.getGameSurface();

        if( (canvas == null) || (surface == null) )
            return;

        double offsetX = owner.getColliderOffsetX();
        double offsetY = owner.getColliderOffsetY();

        float cx = surface.getCanvasX(getCenterX(offsetX));
        float cy = surface.getCanvasY(getCenterY(offsetY));

        double ref = refRadius();

        // Widest first, so that the body stays readable on top of whatever surrounds it.
        drawView(canvas, cx, cy, ref * viewCoef);
        drawZone(canvas, cx, cy, ref * damageCoef, DEBUG_DRAW_DAMAGE, ZONE_FILL_ALPHA);
        drawZone(canvas, cx, cy, hitRadius(), DEBUG_DRAW_HIT, ZONE_FILL_ALPHA);
        drawZone(canvas, cx, cy, ref, DEBUG_DRAW_REF, FILL_ALPHA);
    }

    /**
     * Does any part of that body fall inside the slice this object is facing?
     *
     * Any part, and not just its middle: a creature standing half in the light is seen. So the
     * body's own angular width -- how wide it looks from here, which grows as it comes closer --
     * is added to the half-angle of the view. A half-angle of 180 sees all round and asks no
     * question at all.
     */
    private boolean inSight(double dx, double dy, double facingDeg, double otherHit) {

        if (viewAngleDeg >= 180)
            return true;

        if (viewAngleDeg <= 0)
            return false;

        double towardsDeg = Math.toDegrees(Math.atan2(dx, -dy));

        // Brought back into -180..180, so that looking north at something to the north-west is a
        // small angle and not a very large one.
        double offDeg = ((towardsDeg - facingDeg + 540) % 360) - 180;

        return Math.abs(offDeg) <= (viewAngleDeg + halfWidthDeg(Math.sqrt(dx * dx + dy * dy), otherHit));
    }

    // How wide a body of that radius looks from that distance, in degrees, counted from its middle
    // to its edge. Standing inside it, it fills everything.
    private static double halfWidthDeg(double distance, double radius) {

        if (distance <= radius)
            return 180;

        return Math.toDegrees(Math.asin(radius / distance));
    }

    // What the object can see: the slice of that circle it faces, or the whole of it when it
    // looks all round. Android measures its arcs from three o'clock and clockwise, while a heading
    // is measured from north, hence the quarter turn taken off.
    private void drawView(Canvas canvas, float cx, float cy, double radius) {

        if( (debugDrawMasks & DEBUG_DRAW_VIEW) == 0 )
            return;

        if( (radius <= 0) || (viewAngleDeg <= 0) )
            return;

        if( viewAngleDeg >= 180 ) {
            drawZone(canvas, cx, cy, radius, DEBUG_DRAW_VIEW, ZONE_FILL_ALPHA);
            return;
        }

        float r = (float) radius;

        arc.set(cx - r, cy - r, cx + r, cy + r);

        float start = (float) (owner.getDirectionDeg() - 90.0 - viewAngleDeg);
        float sweep = (float) (viewAngleDeg * 2);

        fill.setColor((debugColor & 0x00FFFFFF) | (ZONE_FILL_ALPHA << 24));
        canvas.drawArc(arc, start, sweep, true, fill);

        stroke.setColor(debugColor);
        canvas.drawArc(arc, start, sweep, true, stroke);
    }

    // One circle of the debug drawing, when it was asked for. Same colour throughout -- the colour
    // tells which family the object belongs to, not which zone this is -- and only the fill tells
    // them apart.
    private void drawZone(Canvas canvas, float cx, float cy, double radius, int mask, int alpha) {

        if( (debugDrawMasks & mask) == 0 )
            return;

        if( radius <= 0 )
            return;

        fill.setColor((debugColor & 0x00FFFFFF) | (alpha << 24));
        canvas.drawCircle(cx, cy, (float) radius, fill);

        stroke.setColor(debugColor);
        canvas.drawCircle(cx, cy, (float) radius, stroke);
    }
}
