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
import android.graphics.Path;


/**
 * The play area: the shape creatures are placed on, and the one they have to stay inside.
 *
 * It belongs to a creature the way its collider does, and there is one per creature: the radius
 * is worked out with ITS speed, so a Chucky at 11 km/h carries a wider one than a creeper at 8.
 * The shape is centred on the player all the same -- it is the ground the game is played on, not
 * something the creature drags along. Nothing collides with it: what it answers is whether its
 * owner is still IN THE GAME.
 *
 * Only ONE is ever drawn, the one the orchestrator is watching. Drawing them all would turn the
 * map into a pile of circles.
 *
 * Its radius is not a distance but a DELAY: it is drawn so that its owner, standing on its edge,
 * is on the player PLAY_TIME_S later (PLAY_TIME_LVL0_S outside a game). The radius therefore
 * depends on how fast the two close on each other, which is the creature's speed plus the
 * player's speed projected on the line joining them:
 *
 *     closing = v_creature + V_player . u
 *
 * At 50 km/h due north, a creature ahead closes at 58 km/h and the edge stands 967 m away; the same
 * creature behind closes at -42 km/h, which is to say it never arrives at all. Hence the floor: the
 * shape is never tighter than it would be with the player standing still, which is the creature's
 * own speed times the delay. The ceiling is not here but where a creature is placed: what reaches
 * past the chunk is cut by the chunk.
 *
 * Standing still it is a circle. Moving it is not: it stretches ahead, where a creature has further
 * to walk to arrive in the same time, and closes in behind. That shape is what the drawing shows.
 */
public class PlayArea {

    // The delay the whole shape is built from. One minute is the real figure.
    private static final double PLAY_TIME_NORMAL_S = 60.0;

    // TEMPORARY, while the game is being tuned: a fraction of the real delay, so that a whole
    // approach takes a few seconds instead of a minute and the shape can be watched without
    // waiting for it. Put PLAY_TIME_NORMAL_S back in both when the tuning is over.
    //
    // Two of them, because the two situations are not the same game: in play, the delay is what
    // the player is given to deal with what is coming. Outside, there is nothing to deal with --
    // one creeper roams the chunk and is there to be found -- and that one keeps the shorter delay
    // it has always had, so that finding the easter egg is unchanged.
    private static final double PLAY_TIME_S = 30.0;
    private static final double PLAY_TIME_LVL0_S = 15.0;

    // How far PAST the edge a creature has to be before it counts as out, in metres.
    //
    // A creature is put back tangent to the edge, so without this it is one rounding error away
    // from being out again on the very next frame -- and while the player moves, the edge itself
    // shifts under it from one frame to the next. The threshold turns a line into two states: it
    // is in the game until it is CLEARLY out. Five metres is about two seconds of walking, enough
    // to cover the wobble, short enough that nothing lingers where it should not.
    private static final double OUT_MARGIN_M = 5.0;

    // One point every five degrees. The curve is smooth and slow-changing, so this is plenty, and
    // it keeps the shape cheap enough to rebuild whenever the player moves.
    private static final int SAMPLE_COUNT = 72;

    // Violet. The four collider colours are taken -- cyan the player, magenta the creatures,
    // orange the marks, white the bullets -- and this is not one of them.
    private static final int COLOR = Color.parseColor("#7C4DFF");

    // Light enough to read the map through: the shape is large, and it lies under everything.
    private static final int FILL_ALPHA = 0x30;

    // In AREA coordinates, like everything else the game holds. The canvas is only ever moved to
    // match, never the shape.
    private final Path path = new Path();

    // Nothing to draw until the first update has built a shape, and nothing to draw again as soon
    // as one of the things it is built from goes missing.
    private boolean ready = false;

    private final Paint stroke = new Paint();
    private final Paint fill = new Paint();

    // The creature it belongs to, and the world it is measured in.
    private final ChibiCharacter owner;
    private final GameSurface gameSurface;

    // Was the owner inside, as of the last update? The whole answer this shape exists to give.
    private boolean ownerInside = true;

    public PlayArea(ChibiCharacter owner, GameSurface gameSurface) {
        this.owner = owner;
        this.gameSurface = gameSurface;

        stroke.setAntiAlias(true);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(3f);
        stroke.setColor(COLOR);

        fill.setAntiAlias(true);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor((COLOR & 0x00FFFFFF) | (FILL_ALPHA << 24));
    }

    /**
     * Is the owner still in the game, as of the last update?
     *
     * This is what sends a creature back: not being in its own play area. True until the first
     * update has anything to say, so that a creature just made is never thrown out before it has
     * been anywhere.
     */
    public boolean isOwnerInside() {
        return ownerInside;
    }

    /**
     * The radius of the play area, in metres, at a given bearing.
     *
     * The door for whoever has a single bearing to ask about -- a creature working out where to
     * place itself. The frame's own work goes through the private one, which is handed the
     * player's speed and heading instead of asking for them eighty times in a row.
     */
    public double radiusM(double bearingDeg) {
        AppManager am = AppManager.getInstance();

        return radius(owner.getSpeedKmh(), bearingDeg, am.getSpeedKm(), am.getDirectionAngle(),
                      delaySeconds(am));
    }

    /**
     * The delay the shape is built on, in seconds: the one this level uses, tightened by whatever
     * the hurry up has taken.
     *
     * Read once and handed down, like the player's speed: the shape asks for it at every one of
     * its seventy-two points, and reading the game state that often would mean taking the lock
     * seventy-two times for one number.
     */
    private double delaySeconds(AppManager am) {

        double base = am.getIsGameMode() ? PLAY_TIME_S : PLAY_TIME_LVL0_S;

        return base * gameSurface.getOrchestrator().getDelayCoef();
    }

    // The creature's OWN speed, not a reference one: a kind that walks faster needs more room to
    // take the same minute, and the same kind will need more again the day the level speeds it up.
    private static double radius(double creatureKmh, double bearingDeg,
                                 double playerKmh, double headingDeg, double delayS) {

        // The player's velocity projected on the line joining him to that bearing: positive when
        // he is heading towards it, negative when he is running away from it.
        double towardsKmh = playerKmh * Math.cos(Math.toRadians(bearingDeg - headingDeg));

        // Floored at the creature's own speed: the standstill radius, the tightest the area gets.
        double closingKmh = Math.max(creatureKmh, creatureKmh + towardsKmh);

        // The delay IS the shape, which is why the hurry up shaves it: that is what brings the
        // creatures in closer and leaves less time to see them coming.
        return (closingKmh / 3.6) * delayS;
    }

    /**
     * The frame's work: who is inside, and -- when there is someone to show it to -- the shape.
     *
     * Called from the game loop and NEVER from the drawing: the list is what the game is decided
     * on, and seventy-two points with a sine and a cosine each have no business being computed
     * while the canvas is being painted.
     *
     * The list is built whenever a game is on, debug panel or not, since creatures are sent back
     * by it. The shape is built only when it is also going to be drawn.
     */
    public void update() {

        ready = false;

        // No game, no play area: nothing tested, nothing computed. The owner stays "inside", so
        // that leaving the game never throws a creature anywhere.
        AppManager am = AppManager.getInstance();

        if( !am.getIsGameMode() ) {
            ownerInside = true;
            return;
        }

        MapGPSPos player = gameSurface.getGps();

        if( player == null )
            return;

        double pxPerM = gameSurface.pixelsPerMeter();

        if( pxPerM <= 0 )
            return;

        // Centred on the PLAYER: the shape belongs to the creature, the ground belongs to him.
        double px = player.getAnchorOriginX();
        double py = player.getAnchorOriginY();

        // The player OUT of the world, which happens when he walks off the chunk faster than the
        // next one can be prepared: then no creature can ever be in a play area centred on him,
        // since they are all held inside the chunk. Sending them back would put them against the
        // edge and take them from there again, frame after frame -- which is exactly what was seen
        // on the emulator the day two chunks failed to load. So nothing moves until the world has
        // caught up with him.
        if( (px < 0) || (py < 0)
         || (px > gameSurface.getAreaWidth()) || (py > gameSurface.getAreaHeight()) ) {
            ownerInside = true;
            return;
        }

        double playerKmh = am.getSpeedKm();
        double headingDeg = am.getDirectionAngle();

        double delayS = delaySeconds(am);

        ownerInside = intersects(owner, px, py, pxPerM, playerKmh, headingDeg, delayS);

        // The shape is only ever drawn, so it is only built for the creature the game is watching,
        // and only when there is someone to show it to. Everyone else has just answered the one
        // question that matters and is done for this frame.
        if( !am.getDebugMode() )
            return;

        GameOrchestrator orchestrator = gameSurface.getOrchestrator();

        if( orchestrator.getMainChibi() != owner )
            return;

        buildPath(px, py, pxPerM, playerKmh, headingDeg, delayS);

        ready = true;
    }

    // True when the object's collider reaches into the shape AT ALL. A creature is out of the game
    // only once its whole body is out, and not the instant its anchor crosses the line.
    private boolean intersects(GameObject object, double px, double py, double pxPerM,
                               double playerKmh, double headingDeg, double delayS) {

        GameCollider collider = object.getCollider();

        if( collider == null )
            return false;

        double cx = collider.getCenterX(object.getColliderOffsetX());
        double cy = collider.getCenterY(object.getColliderOffsetY());

        double dx = cx - px;
        double dy = cy - py;

        double distance = Math.sqrt(dx * dx + dy * dy);

        // Area y grows downwards, so north is -y: hence the minus, as everywhere else.
        double bearingDeg = Math.toDegrees(Math.atan2(dx, -dy));

        double speedKmh = (object instanceof ChibiCharacter)
                        ? ((ChibiCharacter) object).getSpeedKmh()
                        : owner.getSpeedKmh();

        double edge = (radius(speedKmh, bearingDeg, playerKmh, headingDeg, delayS)
                     + OUT_MARGIN_M) * pxPerM;

        return (distance - collider.getRadius()) <= edge;
    }

    // The outline, in area coordinates. The canvas is only ever moved to match, never the shape.
    private void buildPath(double px, double py, double pxPerM, double playerKmh,
                           double headingDeg, double delayS) {

        path.reset();

        for( int i = 0; i < SAMPLE_COUNT; i++ ) {

            double bearingDeg = i * 360.0 / SAMPLE_COUNT;
            double radiusPx = radius(owner.getSpeedKmh(), bearingDeg, playerKmh, headingDeg,
                                     delayS) * pxPerM;

            float x = (float) (px + radiusPx * Math.sin(Math.toRadians(bearingDeg)));
            float y = (float) (py - radiusPx * Math.cos(Math.toRadians(bearingDeg)));

            if( i == 0 )
                path.moveTo(x, y);
            else
                path.lineTo(x, y);
        }

        path.close();
    }

    /**
     * Same signature as everything else that draws itself.
     *
     * The shape is in area coordinates and the canvas is in screen coordinates, which differ by
     * the scroll and by nothing else: the canvas is moved by that much, the shape is painted, and
     * the canvas is put back.
     */
    public void draw(Canvas canvas, int layer) {

        // The cheap question first, exactly as the colliders ask it: this draws nothing unless a
        // game is being played with the debug panel open.
        AppManager am = AppManager.getInstance();

        if( !am.getDebugMode() || !am.getIsGameMode() )
            return;

        // One shape on screen, not one per creature: the one the game is watching.
        GameOrchestrator orchestrator = gameSurface.getOrchestrator();

        if( orchestrator.getMainChibi() != owner )
            return;

        if( !ready || (canvas == null) )
            return;

        canvas.save();
        canvas.translate(gameSurface.getCanvasX(0.0), gameSurface.getCanvasY(0.0));

        canvas.drawPath(path, fill);
        canvas.drawPath(path, stroke);

        canvas.restore();
    }
}
