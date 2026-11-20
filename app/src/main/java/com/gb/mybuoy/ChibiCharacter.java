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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * A creature of the hidden game: the thing that walks the map and has to be dealt with.
 *
 * Abstract, because a creature is not a skin: what a kind of creature IS -- how much it takes to
 * kill, how fast it walks, what it costs the player to touch it, what it is worth dead -- belongs
 * with its picture and nowhere else. A subclass declares those five figures and its own sheet,
 * held statically, one decode for every creature of that kind.
 */
public abstract class ChibiCharacter extends GameObject {
    private static final int ROW_TOP_TO_BOTTOM = 0;
    private static final int ROW_RIGHT_TO_LEFT = 1;
    private static final int ROW_LEFT_TO_RIGHT = 2;
    private static final int ROW_BOTTOM_TO_TOP = 3;

    // Row index of Image are being used.
    private int rowUsing = ROW_LEFT_TO_RIGHT;

    private int colUsing;

    // The sheet, already cut and scaled by AvatarCatalog: the same path the player's avatar
    // takes. It matters that the catalogue does the cutting -- it scales each frame SEPARATELY,
    // after cutting, where scaling the whole sheet lets the filtering drag the first row of a cell
    // into the last row of the one above it, which is the fringe of foreign pixels that was seen
    // on the avatars. It also stamps the screen density, so nothing enlarges them a second time.
    private Bitmap[] frames;

    // Where in the walk cycle this creature is: an index into AvatarCatalog.MOVE_SEQUENCE.
    private int seqIndex = 0;

    // Whether it covered any ground on the last update. Standing still, it shows the idle frame --
    // the fourth column on a 4-wide sheet, the second on a 3-wide one -- instead of walking on the
    // spot, which happens now that a creature stops dead against the player.
    private boolean walking = true;

    // Speeds are in KILOMETRES PER HOUR, the unit the whole application already speaks. A walk is
    // 5 km/h; 8 is the reference for a creature, chosen so that a player who runs -- 12 to 15 --
    // gets away, and a player who walks does not.
    public static final double DEFAULT_SPEED_KMH = 8.0;

    // Half-width of the sector a returning creature comes back in, around the direction opposite
    // the one it drifted out by. HALF the cone: 15 here is a cone of 30 degrees.
    //
    // Narrowed from 60 to 15 after watching it at speed: at the edge of a wide cone a creature was
    // being put back almost across the player's course, where nothing closes and the play area is
    // therefore at its tightest, and it fell out again within a second or two. The tighter cone
    // keeps the randomness without ever aiming it sideways.
    private static final float SECTOR_HALF_DEG = 15.0f;

    // Body and reach of a creature, as fractions of its reference circle. Equal, because damage
    // shorter than the body would never reach anything: two solid things stop when their bodies
    // meet. See the doc, "Solide, touché, blessé".
    // What being hit looks like, when it survives it. Owned here, started from takeDamage().
    private final HitEffect hitEffect = new HitEffect();

    private static final double HIT_COEF = 0.6;
    private static final double DAMAGE_COEF = 0.6;

    // The least a finger is given, as a RADIUS in DP. The hit circle of a small creature comes to
    // some 13 dp across on a fine screen, which no thumb lands on while the thing walks. A tomato
    // gets no such favour: it has to reach the hit circle itself.
    private static final float FINGER_MIN_DP = 20f;

    // Whether the player was in sight at the last update, and the stream the call is running on.
    // See callOnSight(): the call is made on the way in, and the stream is what allows cutting it.
    private boolean sawPlayer = false;

    /**
     * Does it have the player in sight right now?
     *
     * Asked by the surface: a finger on the map turns the heads of the creatures that are looking
     * for something to walk towards, and this one has already found it.
     */
    public boolean seesPlayer() {
        return sawPlayer;
    }
    private int viewStreamId = 0;
    private static final float VIEW_SOUND_VOLUME = 0.8f;

    // The last time each call was heard, SHARED by every creature -- the key is the sound, not the
    // creature. Ten of them spotting the player at once would otherwise shout over one another,
    // which was what the first try sounded like. The same voice therefore waits its turn.
    private static final long VIEW_SOUND_GAP_MS = 5000;
    private static final java.util.Map<Integer, Long> _lastCallNs =
            new java.util.HashMap<Integer, Long>();

    // TEMPORARY: see steerAround(). Put back to true to walk round obstacles again.
    private static final boolean AVOID_OBSTACLES = true;

    // Ce que rapporte un coup qui ne tue pas, et ce que vaut de l'avoir porte a la tomate plutot
    // qu'au doigt.
    private static final int HIT_POINTS = 1;
    private static final int BULLET_FACTOR = 5;

    // Marks "no sector at all", for a first spawn, which may come from anywhere.
    private static final double NO_SECTOR = Double.NaN;

    // How many times it has been hit so far. A creature dies when it has taken as many as it can.
    private int hitsTaken = 0;

    // The life bar drawn over a wounded creature. Without it a creature that takes three hits
    // looks like a creature the finger keeps missing: nothing moves, nothing answers, and the tap
    // seems broken. It appears only once the first hit has landed, and only on a kind that takes
    // more than one -- a creeper dies on the first, it would never be seen.
    //
    // Area pixels, like everything else the creatures live in.
    // Same bar as the player's, which is the reference: same height, same gap, same colours. A
    // creature's life and the player's are read side by side, and two bars drawn differently would
    // be two things to learn instead of one.
    private static final int LIFE_BAR_HEIGHT = 16;
    private static final int LIFE_BAR_GAP = 6;        // between the top of the sprite and the bar

    private static final Paint LIFE_BAR_LEFT = new Paint();
    private static final Paint LIFE_BAR_LOST = new Paint();
    private static final Paint LIFE_BAR_EDGE = new Paint();

    static {
        LIFE_BAR_LEFT.setStyle(Paint.Style.FILL);
        LIFE_BAR_LEFT.setColor(Color.parseColor("#00C853"));   // what is left

        LIFE_BAR_LOST.setStyle(Paint.Style.FILL);
        LIFE_BAR_LOST.setColor(Color.parseColor("#D50000"));   // what has been taken

        LIFE_BAR_EDGE.setStyle(Paint.Style.STROKE);
        LIFE_BAR_EDGE.setStrokeWidth(1.5f);
        LIFE_BAR_EDGE.setColor(Color.BLACK);
    }

    // Which way round an obstacle this creature goes: -1 or +1, drawn when it is made and never
    // changed afterwards. Never changed is the point -- see steerAround(). Drawn, and not the same
    // for everyone, so that two creatures meeting the same mark do not file round it in a queue.
    private final int preferredSide = (Math.random() < 0.5) ? -1 : 1;

    // Where it would go if nothing were in the way. Written by computeWantedDirection().
    private double wantedX = 0;
    private double wantedY = 0;
    private double wantedDistance = 0;

    // The last thing walked into. See steerAround(): a contact has to outlive the update it
    // happens on, or a creature following a wall loses it every other step.
    private double lastNormalX = 0;
    private double lastNormalY = 0;

    // How many updates since the last real contact, and how many it takes to call it over. In
    // UPDATES and not in milliseconds: what is being covered is a rhythm of frames -- contact one
    // update in two -- so a delay in milliseconds would stop covering it the day the frame rate
    // drops, which is the day it matters most.
    private static final int CONTACT_MEMORY_UPDATES = 3;
    private int updatesSinceContact = CONTACT_MEMORY_UPDATES + 1;

    // Following a wall, and how far the target was when that wall was first touched. The wall is
    // not let go of until the creature is nearer than that -- see steerAround().
    private boolean following = false;
    private double followHitDistance = 0;

    // The facing being waited on, and how far the creature has walked since it first asked for it.
    // A new facing has to be earned while an obstacle is being dealt with -- see updateRow().
    private int pendingRow = -1;
    private double pendingRowPx = 0;
    private static final double ROW_CHANGE_PX = 3.0;

    private long lastSteerTraceNs = -1;


    private long lastUpdateNanoTime =-1;
    private long lastColNanoTime =-1;

    // Tracing while the game is being built: once a second, where this creature is, in the same
    // coordinates the rest of the application speaks, plus how far it has left to walk.
    private static final long TRACE_PERIOD_MS = 1000;
    private long lastTraceNs = -1;

    // Not placed yet. A creature cannot be placed the moment it is made: the surface is built
    // before it is laid out, so getWidth() is still 0, the world has no size and the scale falls
    // back to one pixel per metre. A creature placed then lands on the edge of the map, 1900 m
    // away instead of 200, walking nowhere. It waits for the first update that has a real world
    // to be placed in.
    private boolean placed = false;

    // The voice this one cries out with when it is hit, drawn when it is made and kept for as
    // long as it lives. Drawn per CREATURE and not per kind, and not per hit either: a creature
    // that answered in one voice and then another would sound like two creatures, and a whole map
    // answering in the same voice sounds like one.
    private final int hurtVoice;

    // Its own play area, the way GameObject gives it its own collider: the shape is measured with
    // THIS creature's speed, so a kind that walks faster carries a wider one. Only the one the
    // orchestrator is watching is ever drawn.
    protected final PlayArea playArea;

    protected GameSurface gameSurface;

    // --- what each kind of creature is --------------------------------------------------------

    /** How many hits it takes before it explodes. */
    public abstract int getMaxHits();

    /** Kilometres per hour. DEFAULT_SPEED_KMH, 8, is the reference. */
    public abstract double getSpeedKmh();

    /** Lives taken from the player on contact. */
    public abstract int getContactDamage();

    /** Points it is worth once killed. */
    public abstract int getScoreValue();

    /** The game level from which this creature appears at all. */
    public abstract int getMinLevel();

    // --- where it comes from --------------------------------------------------------------------

    /** Puts a new creature on the map, on the edge of the play area, anywhere around the player. */
    public void spawn() {
        place(NO_SECTOR);
    }

    /**
     * Puts a creature that has left the world back on the play area, OPPOSITE the side it left by.
     *
     * The old trick of the screen edge: what goes out on one side comes back on the other. Not at
     * a point, though, but somewhere in a sector facing away from where it was last seen. The
     * creature itself is kept -- same species, same hits taken, same everything -- it is only
     * moved: destroying it and making another would mean adding to and removing from the list the
     * render loop is walking at that very moment.
     */
    public void respawn() {
        placeOpposite();
    }

    // What respawn() does, and what update() calls when a creature walks out of the world. A class
    // never calls its own public methods: the private one is the real work, and respawn() is only
    // a door onto it.
    private void placeOpposite() {
        double sector = driftBearing() + 180.0;

        place(sector);
    }

    /**
     * The direction a creature is DRIFTING in, seen from the player, in degrees from north.
     *
     * This is the side it leaves the play area by, and the respawn sector is taken opposite it.
     * Not opposite where it stood: standing still the two are the same thing, but the moment the
     * player moves they part company. What carries a creature out of the play area is its motion
     * RELATIVE to him, which is its velocity minus his:
     *
     *     V_relative = V_creature - V_player
     *
     * Walking north at 8 km/h while the player drives east at 16, a creature drifts north-west in
     * his frame however it is aimed, and that is the side it goes out by; it comes back opposite,
     * which is in front of him. Going by where it merely happened to STAND would send it back
     * sideways, across the player's course -- exactly where the play area is at its narrowest,
     * since nothing is closing there -- and it would be dropped again a second later.
     */
    private double driftBearing() {
        AppManager am = AppManager.getInstance();

        // The creature's own velocity: its heading is its moving vector, its speed is its own.
        double cx = getSpeedKmh() * dirX;
        double cy = getSpeedKmh() * dirY;

        // The player's velocity, in the same axes: x east, y south, so north is -y.
        double playerKmh = am.getSpeedKm();
        double headingDeg = am.getDirectionAngle();

        double gx = playerKmh * Math.sin(Math.toRadians(headingDeg));
        double gy = -playerKmh * Math.cos(Math.toRadians(headingDeg));

        double rx = cx - gx;
        double ry = cy - gy;

        // Travelling exactly with the player: no drift, and nothing to be opposite of. Where it
        // stands is then the best that can be said about it.
        if( (Math.abs(rx) < 0.001) && (Math.abs(ry) < 0.001) )
            return bearingToPlayer();

        return Math.toDegrees(Math.atan2(rx, -ry));
    }

    // Once a second, where this creature is: the GPS coordinates the rest of the application
    // speaks, its position in the world, how far the player is and how big the play area is for
    // the bearing it stands at. Temporary, while the game is being built -- debug mode only, and
    // rate limited, because this runs in the render loop.
    private void trace() {
        AppManager am = AppManager.getInstance();

        if( !am.getDebugMode() )
            return;

        long now = System.nanoTime();

        if( (lastTraceNs > 0) && ((now - lastTraceNs) / 1000000 < TRACE_PERIOD_MS) )
            return;

        lastTraceNs = now;

        Chunk chunk = gameSurface.getMap();

        if( chunk == null )
            return;

        int areaW = gameSurface.getAreaWidth();
        int areaH = gameSurface.getAreaHeight();

        double cx = this.x + (double) width / 2;
        double cy = this.y + (double) height / 2;

        double latitude = chunk.getLatitude(cy, areaH);
        double longitude = chunk.getLongitude(cx, areaW);

        traceSeen();

        double distM = distanceToPlayerM();
        double bearingDeg = bearingToPlayer();

        GBLog.d(getClass().getSimpleName(),
                String.format(java.util.Locale.US,
                              "%.6f %.6f | area %.0f,%.0f of %dx%d | %.0f m at %.0f deg | drift %.0f deg | area radius %.0f m | %.2f px/m",
                              latitude, longitude, cx, cy, areaW, areaH,
                              distM, bearingDeg, driftBearing(), playArea.radiusM(bearingDeg),
                              gameSurface.pixelsPerMeter()));
    }

    /**
     * The life bar, over the head of a creature that has been hit and is still standing.
     *
     * Nothing to compute here: two rectangles and an outline, from figures the creature already
     * holds. It is NOT a debug drawing -- it is what tells the player that the last tap counted --
     * so it obeys neither the debug mode nor anything else: it shows whenever there is something
     * to show.
     */
    private void drawLifeBar(Canvas canvas) {

        int maxHits = getMaxHits();

        // Nothing to say: either this kind dies on the first hit, or it has not been touched yet.
        if( (maxHits <= 1) || (hitsTaken <= 0) )
            return;

        int left = maxHits - hitsTaken;

        if( left < 0 )
            left = 0;

        float x0 = gameSurface.getCanvasX(this.x);
        float y0 = gameSurface.getCanvasY(this.y) - LIFE_BAR_GAP - LIFE_BAR_HEIGHT;
        float x1 = x0 + width;
        float y1 = y0 + LIFE_BAR_HEIGHT;

        // The whole bar in the colour of what was taken, then what is left over it: two rectangles
        // instead of three, and no gap to get wrong between them.
        canvas.drawRect(x0, y0, x1, y1, LIFE_BAR_LOST);
        canvas.drawRect(x0, y0, x0 + width * ((float) left / maxHits), y1, LIFE_BAR_LEFT);
        canvas.drawRect(x0, y0, x1, y1, LIFE_BAR_EDGE);
    }

    /** Its play area: drawn by the surface, which asks every creature and draws one. */
    public PlayArea getPlayArea() {
        return playArea;
    }

    // How far the player is, in metres, or -1 when there is nothing to measure from.
    private double distanceToPlayerM() {
        MapGPSPos player = gameSurface.getGps();

        if( player == null )
            return -1;

        double pxPerM = gameSurface.pixelsPerMeter();

        if( pxPerM <= 0 )
            return -1;

        double dx = (this.x + (double) width / 2) - player.getAnchorOriginX();
        double dy = (this.y + (double) height / 2) - player.getAnchorOriginY();

        return Math.sqrt(dx * dx + dy * dy) / pxPerM;
    }

    // Where this creature stands seen from the player, in degrees from north.
    private double bearingToPlayer() {
        MapGPSPos player = gameSurface.getGps();

        if( player == null )
            return 0;

        double dx = (this.x + (double) width / 2) - player.getAnchorOriginX();
        double dy = (this.y + (double) height / 2) - player.getAnchorOriginY();

        // Area y grows downwards, so north is -y: hence the minus.
        return Math.toDegrees(Math.atan2(dx, -dy));
    }

    /**
     * Places this creature on the edge of the play area, at a bearing drawn at random -- inside
     * the given sector when there is one -- and never outside the chunk.
     *
     * The bearing is drawn FIRST, because the radius depends on it: ahead of a moving player the
     * edge is far away, behind him it is down at the floor. The ray from the player along that
     * bearing is then cut by the world, so that a creature whose radius reaches past the chunk is
     * put on the edge of the chunk instead -- nearer than the minute asked for, but in the world.
     */
    private void place(double sectorCenterDeg) {
        MapGPSPos player = gameSurface.getGps();

        if( player == null )
            return;

        double px = player.getAnchorOriginX();
        double py = player.getAnchorOriginY();

        double halfW = (double) width / 2;
        double halfH = (double) height / 2;

        double bearingDeg;

        if( Double.isNaN(sectorCenterDeg) ) {
            bearingDeg = Math.random() * 360.0;
        } else {
            bearingDeg = sectorCenterDeg - SECTOR_HALF_DEG + Math.random() * SECTOR_HALF_DEG * 2;
        }

        double radiusPx = playArea.radiusM(bearingDeg) * gameSurface.pixelsPerMeter();

        double ux = Math.sin(Math.toRadians(bearingDeg));
        double uy = -Math.cos(Math.toRadians(bearingDeg));

        double cx = px + radiusPx * ux;
        double cy = py + radiusPx * uy;

        // Cut by the world: what is left, inside the chunk, of the ray going from the player to
        // that point. Liang-Barsky, the same routine the zone labels are clipped with. The box is
        // shrunk by half a sprite so that the creature stands whole inside the world.
        double[] inWorld = Common.clipSegment(px, py, cx, cy,
                                              halfW, halfH,
                                              gameSurface.getAreaWidth() - halfW,
                                              gameSurface.getAreaHeight() - halfH);

        if( inWorld != null ) {
            cx = inWorld[2];
            cy = inWorld[3];
        }

        // It comes in walking towards the player, the only direction worth starting on. Worked out
        // before the position is settled, because the creature is backed off ALONG it.
        double towardsX = px - cx;
        double towardsY = py - cy;

        double towardsLength = Math.sqrt(towardsX * towardsX + towardsY * towardsY);

        // Backed off by its own collider radius, so that it arrives with its body JUST outside the
        // area instead of already inside it. That is the mirror of the way it leaves, which is
        // when the whole of that same collider is out: touching counts as being in the game on
        // both sides, so it belongs from its first frame and is not sent away again on the spot.
        if( towardsLength > 0 ) {

            double back = collider.getRadius();

            cx -= back * towardsX / towardsLength;
            cy -= back * towardsY / towardsLength;
        }

        // The world has the last word: backing off can push a creature that was already against
        // the edge of the chunk out of it, and out of the chunk it would be sent back at once.
        // Nearer than intended, then, rather than nowhere.
        cx = Math.min(Math.max(cx, halfW), gameSurface.getAreaWidth() - halfW);
        cy = Math.min(Math.max(cy, halfH), gameSurface.getAreaHeight() - halfH);

        this.x = cx - halfW;
        this.y = cy - halfH;

        applyDirection((int) Math.round(towardsX), (int) Math.round(towardsY));

        placed = true;

        // Just arrived: ask for the camera. The orchestrator gives it or not, depending on how
        // long the one it is watching has had it.
        GameOrchestrator orchestrator = gameSurface.getOrchestrator();

        orchestrator.placed(this);
    }

    // --- life ---------------------------------------------------------------------------------

    /**
     * A tap at this point, in area coordinates.
     *
     * Returns true when the creature was touched, so that the caller knows the tap has been used
     * and must do nothing else with it. The test is the collider and not a rectangle around the
     * sprite: the collider is what the game calls the creature's body everywhere else, and a
     * tomato and a finger have no business disagreeing about where it is.
     *
     * Widened to what a finger needs, which a tomato is not. That is the one difference allowed
     * between them, and it is about the hand, not about the creature.
     */
    public boolean hit(double px, double py) {

        float density = gameSurface.getResources().getDisplayMetrics().density;

        if( !collider.contains(px, py, FINGER_MIN_DP * density) )
            return false;

        AppManager am = AppManager.getInstance();

        // A blow by hand costs a point of energy, and out of energy it costs the creature nothing:
        // it answers with its squeak, which is the one thing in this game that makes fun of the
        // player. The tap is used up all the same -- it must not go on to move the map.
        if( am.getIsGameMode() && !am.spendEnergy() ) {
            mock();
            return true;
        }

        if( takeDamage(1, px, py, false, true) )
            die();

        return true;
    }

    /**
     * The squeak it answers a powerless finger with, in place of the cry it would have given.
     *
     * The sound belongs to the creature and not to the hand: a kind that sniggers differently one
     * day has only this to override.
     */
    protected int getMockSoundRes() {
        return R.raw.eg_pouet;
    }

    private void mock() {

        // Whatever it was calling out, the squeak takes the stream: the answer has to land on the
        // tap that asked for it.
        stopCall();

        // One squeak at a time: hammering on a creature with nothing left in the hand must not
        // turn its mockery into a rattle.
        gameSurface.playSoundOnce(getMockSoundRes(), getHurtSoundVolume());
    }

    /**
     * Ce que ce coup rapporte.
     *
     * Une victime vaut ce que vaut son espece, un coup porte sans tuer vaut un point -- sinon un
     * boss a mille vies ne rapporterait rien pendant deux minutes. Dans les deux cas, l'avoir fait
     * a la tomate vaut CINQ FOIS plus qu'au doigt : viser une creature qui marche n'a rien a voir
     * avec tapoter dessus.
     *
     * La serie, elle, ne compte que les victimes, et elle multiplie l'ensemble.
     */
    private void score(boolean lethal, boolean byBullet, boolean streaks) {

        AppManager am = AppManager.getInstance();

        if( !am.getIsGameMode() )
            return;

        int value = (worth >= 0) ? worth : getScoreValue();

        // Worth nothing at all: not the kill, not the hit, and not the streak either.
        if( value <= 0 )
            return;

        int points = lethal ? value : Math.min(HIT_POINTS, value);

        if( byBullet )
            points *= BULLET_FACTOR;

        // La serie compte AVANT que le gain ne soit multiplie : la victime qui fait passer le
        // palier en profite elle-meme.
        if( lethal && streaks )
            am.addStreak();

        ScorePopup.score(gameSurface,
                         this.x + (double) width / 2,
                         this.y + (double) height / 2,
                         points);
    }

    /** Blows up and leaves the map. */
    private void die() {
        // Its own voice first: nothing it was saying matters now.
        stopCall();

        explode();
        gameSurface.removeChibi(this);
    }

    /** The puff it leaves behind, at its centre. */
    private void explode() {
        gameSurface.addExplosion(Explosion.at(gameSurface,
                                              getX() + getWidth() / 2,
                                              getY() + getHeight() / 2));
    }

    /** Takes one or more hits, from nowhere in particular. */
    public boolean hit(int damage) {
        return takeDamage(damage, this.x + (double) width / 2, this.y + (double) height / 2,
                          false, true);
    }

    /**
     * The door a pole's shockwave knocks on.
     *
     * The same wound as a shot and the same explosion, but it pays like a blow by hand -- it was
     * not aimed -- and it feeds NO streak. A ring fifty metres across can mow down twenty
     * creatures at once, and a run of kills nobody's finger earned has no business multiplying
     * everything that comes after it.
     */
    public void takeBlast(int damage, double fromX, double fromY) {

        stopCall();

        if( takeDamage(damage, fromX, fromY, false, false) )
            die();
    }

    // The real work, again, so that nothing inside the class goes through a public door.
    private boolean takeDamage(int damage, double fromX, double fromY, boolean byBullet,
                               boolean streaks) {
        hitsTaken += damage;

        boolean lethal = hitsTaken >= getMaxHits();

        score(lethal, byBullet, streaks);

        // Whatever it was calling out, the cry takes the stream.
        stopCall();

        // A hit that does not kill says so. A hit that kills says nothing here: explode() has the
        // explosion, and the two sounds on top of each other would be a mess.
        //
        // The creature is asked for its cry only when one is going to be heard: a kind that takes
        // turns between two voices would otherwise skip one every time it is killed.
        if( !lethal ) {

            // Flash, recoil and sparks -- but only while there is a creature left to show them. A
            // lethal blow has the explosion, and the two on top of each other would be a mess.
            hitEffect.start(fromX, fromY, this.x + (double) width / 2, this.y + (double) height / 2);

            int own = getHurtSoundRes();

            // Once at a time: a cry that is still being heard is not started again.
            if( own == 0 )
                gameSurface.playSoundChibiHit(hurtVoice);
            else
                gameSurface.playSoundOnce(own, getHurtSoundVolume());
        }

        return lethal;
    }

    public boolean isDead() {
        return hitsTaken >= getMaxHits();
    }

    // --- what is lying on the ground -------------------------------------------------------------
    //
    // An item dropped by a mark does not only wound: a trap holds what it bites, a banana skin
    // puts it on the floor, a swarm of wasps takes half its pace away. Both are the same shape --
    // an instant at which it is over, and for the wasps a coefficient -- and both are read on
    // every step rather than being written into the creature's own speed, which is a constant of
    // its kind and has to stay one.

    private long heldUntilNs = -1;

    private long slowUntilNs = -1;
    private double slowCoef = 1.0;

    /** Held where it stands for that long. A second grip never cuts the first one short. */
    public void holdStill(long ms) {

        long until = System.nanoTime() + (ms * 1000000L);

        if( until > heldUntilNs )
            heldUntilNs = until;
    }

    /** Down to that fraction of its pace for that long. The latest sting sets the pace. */
    public void slowDown(double coef, long ms) {

        long until = System.nanoTime() + (ms * 1000000L);

        slowCoef = coef;

        if( until > slowUntilNs )
            slowUntilNs = until;
    }

    /**
     * Does it fly over whatever is lying on the ground?
     *
     * False for everything that walks. True for the ghost, which goes through things: a mine, a
     * trap and a banana skin are all floor, and the floor is not where a ghost is. It is no
     * protection against a swarm of wasps, which comes up to meet it.
     */
    public boolean isFlying() {
        return false;
    }

    // Still held? Asked in private, the public door not being ours to use.
    private boolean held() {
        return (heldUntilNs > 0) && (System.nanoTime() < heldUntilNs);
    }

    // What is left of its pace, from nothing to all of it.
    private double paceCoef() {

        if( (slowUntilNs > 0) && (System.nanoTime() < slowUntilNs) )
            return slowCoef;

        return 1.0;
    }

    // What killing this one pays, or -1 for whatever its species is worth.
    //
    // Set by the orchestrator when it sends the creature in, because it is not a property of the
    // creature but of the reason it is there: one the wave called for is worth its species, one
    // the hurry up added is worth a single point so that dawdling never becomes the best way to
    // score, and the one that was already roaming outside the game is worth nothing at all.
    private int worth = -1;

    /** @see #worth */
    public void setWorth(int points) {
        this.worth = points;
    }

    public int getHitsTaken() {
        return hitsTaken;
    }

    /**
     * A creature is born where it belongs: it has no position to be given, it takes one.
     *
     * It puts itself on the map, so that making a creature is enough to have it there. WHERE it
     * stands is settled later, on the first update: see the comment on `placed`.
     */
    protected ChibiCharacter(GameSurface gameSurface, AvatarCatalog.Sheet sheet) {
        super();

        this.gameSurface= gameSurface;
        this.playArea = new PlayArea(this, gameSurface);
        this.hurtVoice = (int) (Math.random() * gameSurface.getChibiHitVoiceCount());

        collider.setDebugColor(Color.MAGENTA);   // the creatures

        // A creature shows its body and how far it sees. The two other zones exist but are not
        // drawn: four circles on top of one another say nothing to anybody.
        // The body is tighter than the sprite box it is measured from: a creature drawn with air
        // around it should not stop as if it filled its whole picture.
        collider.setHitCoef(HIT_COEF);
        collider.setDamageCoef(DAMAGE_COEF);

        collider.setDebugDrawMasks(GameCollider.DEBUG_DRAW_REF
                                 | GameCollider.DEBUG_DRAW_HIT
                                 | GameCollider.DEBUG_DRAW_VIEW);

        if( sheet != null ) {
            this.frames = sheet.frames;
            this.setFrameGrid(AvatarCatalog.ROW_COUNT, sheet.columns, sheet.frameWidth, sheet.frameHeight);
        }

        gameSurface.addChibi(this);
    }

    /** The twelve frames of this sheet, cut on the first creature that asks and kept for the rest. */
    public Bitmap getCurrentMoveBitmap()  {
        int index = this.rowUsing * this.colCount + this.colUsing;

        if( (this.frames == null) || (index < 0) || (index >= this.frames.length) )
            return null;   // sheet not loaded: draw nothing rather than fall over

        return this.frames[index];
    }


    public void contextScale() {
    }

    public void update()  {

        // Set by the first placement of a creature that had none, and read at the end of the same
        // update: a creature placed just now must not be measured against a list older than it is.
        boolean justPlaced = false;

        // Placed on the first update that has a world to be placed in, and not before.
        //
        // Three things have to be there, and each was found missing in turn: the SURFACE must be
        // laid out, or the world has no size; the CHUNK must be loaded, or there is no projection
        // and the scale falls back to one pixel per metre; and the player must have a FIX, or the
        // circle is drawn around the corner of the map instead of around him. Place a creature
        // before all three and it lands on the edge of the world, 1900 m away, walking nowhere.
        if( !placed ) {

            if( gameSurface.getAreaWidth() <= 0 )
                return;

            if( gameSurface.getMap() == null )
                return;

            if( !AppManager.getInstance().getHasFix() )
                return;

            place(NO_SECTOR);
            justPlaced = true;
        }

        playArea.update();

        trace();

        // Current time in nanoseconds
        long now = System.nanoTime();

        // Never once did draw.
        if(lastUpdateNanoTime==-1) {
            lastUpdateNanoTime= now;
        }

        if(lastColNanoTime==-1) {
            lastColNanoTime= now;
        }


        // Change nanoseconds to milliseconds (1 nanosecond = 1000000 milliseconds).
        int deltaTime = (int) ((now - lastUpdateNanoTime)/ 1000000 );
        int deltaColTime = (int) ((now - lastColNanoTime)/ 1000000 );


        // The walk, on the same cycle as the player's avatar: 0,1,2,1 over and over, and the idle
        // frame when it is not covering ground. Going round the columns in order, as this used to,
        // walked through the idle frame of a 4-wide sheet on every lap -- a limp every fourth step.
        if( deltaColTime > AvatarCatalog.MOVE_STEP_MS ) {

            if( walking ) {

                int col = AvatarCatalog.MOVE_SEQUENCE[this.seqIndex];

                if( col >= this.colCount )
                    col = this.colCount - 1;

                this.colUsing = col;

                this.seqIndex++;

                if( this.seqIndex >= AvatarCatalog.MOVE_SEQUENCE.length )
                    this.seqIndex = 0;

            } else {
                this.colUsing = AvatarCatalog.idleColumn(this.colCount);
                this.seqIndex = 0;
            }

            lastColNanoTime = now;
        }

        lastUpdateNanoTime = now;

        // Distance moves
        // km/h to area pixels per millisecond: metres per second, then pixels per second, then
        // per millisecond. The scale is asked for every frame on purpose -- it changes with the
        // chunk and the zoom, and a creature that walks at 8 km/h must keep walking at 8 km/h.
        double pxPerMs = ((getSpeedKmh() * paceCoef()) / 3.6) * gameSurface.pixelsPerMeter() / 1000.0;
        double distance = pxPerMs * deltaTime;

        // Against the player: it stops there and turns to him, instead of pushing past and
        // walking on. A creature that has reached what it was after has nowhere left to go.
        MapGPSPos touchedPlayer = touchedPlayer();

        if( touchedPlayer != null ) {

            if( aimAt(touchedPlayer) )
                applyDirection(wantedX, wantedY);

            distance = 0;

            onPlayerTouched();
        }

        // A creature that has decided to stand its ground stands it, whatever it wants.
        if( isHalted() )
            distance = 0;

        // And one caught by something on the floor stands it whether it wants to or not.
        if( held() )
            distance = 0;

        // Standing still against the player, or anywhere else: the picture says so.
        walking = (distance > 0);

        // Calculate the new position of the game character.
        this.x = x + distance * dirX;
        this.y = y + distance * dirY;

        // Having moved, see what that walked into. The collider is refreshed HERE, after the
        // step and not before it, or the contacts found would be those of the previous frame.
        hitEffect.update();

        collider.update();

        traceContact();
        resolveContacts();

        callOnSight();

        think();

        // Out of the world: it comes back on the other side.
        //
        // It used to bounce off the edge like a ball, which made no sense for something that is
        // supposed to be walking at the player -- it left, turned round, and came back through the
        // same corner for ever. Now it is teleported onto the play area, in the sector opposite
        // the one it was last seen in, and starts walking again. The creature is kept, not
        // replaced: the render loop is walking the very list it would have to be removed from.
        if( (this.x < 0) ||
            (this.y < 0) ||
            (this.x > this.gameSurface.getAreaWidth() - width) ||
            (this.y > this.gameSurface.getAreaHeight() - height) ) {

            placeOpposite();
        }

        // Out of the play area: the same treatment.
        //
        // The play area holds the list of everything whose COLLIDER reaches into it, and a
        // creature that is not in that list is out of the game. The collider and not the anchor:
        // a creature is out when its whole body is out, and not the instant the point it stands
        // on crosses the line. In game only: at level 0 a lone creature is free to wander the
        // chunk.
        //
        // Not on the frame it was just placed on, though: that list was drawn up before it moved,
        // or before it existed at all, and it would be sent away again on the spot.
        AppManager am = AppManager.getInstance();

        if( am.getIsGameMode() && !justPlaced ) {

            if( !playArea.isOwnerInside() )
                placeOpposite();
        }

        // Which way it faces, held back while it is working its way round something.
        updateRow(distance);
    }

    public void draw(Canvas canvas, int layer)  {

        switch( layer ) {
            case 0:
                // Before the sprite, so the circle stays under it and hides nothing.
                collider.draw(canvas, layer);
                Bitmap bitmap = this.getCurrentMoveBitmap();

                if( bitmap != null )
                    canvas.drawBitmap(bitmap,
                                      gameSurface.getCanvasX(x + hitEffect.getOffsetX()),
                                      gameSurface.getCanvasY(y + hitEffect.getOffsetY()),
                                      hitEffect.isFlashing() ? HitEffect.getFlashPaint() : null);

                hitEffect.draw(canvas, gameSurface);
                drawLifeBar(canvas);
                break;
            default:
                break;
        }
    }

    /**
     * Pushes this creature out of whatever it has walked into, and turns it so that it goes round.
     *
     * Two halves, both of them the usual way of doing this without a physics engine.
     *
     * SEPARATION: the overlap is measured along the line joining the two centres and given back,
     * each object taking the share its own mobility is worth. Against a mark or the player, which
     * the game never moves, the creature takes all of it and ends up exactly side by side.
     *
     * SLIDING: the part of its heading that digs into the obstacle is taken out, which leaves the
     * part that runs along it. That is what makes it skirt a mark instead of pressing against it
     * for ever. Head on, where nothing is left of the heading, a side is picked -- the one it was
     * already leaning towards, so that it does not shake between the two.
     */
    private void resolveContacts() {

        java.util.List<GameObject> hits = collider.getHits();

        // Nothing in the way, or a ghost, which is in nobody's way and has nobody in its own:
        // either way there is nothing to push apart, and the creature simply goes where it wants.
        if( (hits == null) || !isSolid() ) {
            steerAround(0, 0, false);
            return;
        }

        double mine = getMobility();

        // Bodies meet on their HIT zones, not on the reference circle, which is only what those
        // zones are measured from.
        double myRadius = collider.getHitRadius();

        double myX = this.x + (double) width / 2;
        double myY = this.y + (double) height / 2;

        double sumNormalX = 0;
        double sumNormalY = 0;

        boolean blocked = false;

        for( GameObject other : hits ) {

            if( (other == null) || !other.isSolid() )
                continue;

            GameCollider otherCollider = other.getCollider();

            if( otherCollider == null )
                continue;

            double otherX = otherCollider.getCenterX(other.getColliderOffsetX());
            double otherY = otherCollider.getCenterY(other.getColliderOffsetY());

            double dx = myX - otherX;
            double dy = myY - otherY;

            double distance = Math.sqrt(dx * dx + dy * dy);
            double reach = myRadius + otherCollider.getHitRadius();

            double normalX;
            double normalY;

            if( distance > 0.001 ) {
                normalX = dx / distance;
                normalY = dy / distance;
            } else {
                // Exactly on top of one another -- a creature placed on a mark. There is no line
                // between the centres to push along, so it backs out the way it came in.
                normalX = -dirX;
                normalY = -dirY;
            }

            // TOUCHING is what the steering is about, and OVERLAPPING is what has to be given
            // back. They are two different questions, and answering only the second one is what
            // jammed a creature against a mark: pushed out, it stood exactly at the contact
            // distance, no longer overlapping, so the frame after that it was told the road was
            // clear and walked straight back in -- into the mark, out of the mark, into the mark,
            // going nowhere.
            sumNormalX += normalX;
            sumNormalY += normalY;

            blocked = true;

            if( distance >= reach )
                continue;

            double share = 1.0;
            double sum = mine + other.getMobility();

            if( sum > 0 )
                share = mine / sum;

            double overlap = reach - distance;

            myX += normalX * overlap * share;
            myY += normalY * overlap * share;
        }

        this.x = myX - (double) width / 2;
        this.y = myY - (double) height / 2;

        double sumLength = Math.sqrt(sumNormalX * sumNormalX + sumNormalY * sumNormalY);

        if( sumLength > 0 ) {
            sumNormalX /= sumLength;
            sumNormalY /= sumLength;
        } else {
            blocked = false;
        }

        steerAround(sumNormalX, sumNormalY, blocked);
    }

    /**
     * Where this creature WANTS to go, before anything gets in its way. Written into wantedX and
     * wantedY as a unit vector; false when there is nothing to want, and the creature then carries
     * straight on.
     *
     * It goes by what it SEES, and nothing else -- the list its collider fills, which is already
     * narrowed to its field of view and to what nothing stands in front of. Three answers, in this
     * order: the player, then the nearest creature of its own kind, then nothing at all.
     *
     * Always the player, for now, and unconditionally. It is a method of its own because it is
     * about to become a real decision, taken from what the creature can SEE: the collider will
     * carry three coefficients of its radius -- a hit zone, a damage zone and a VIEW zone, around
     * 2.5 for a creeper -- and the answer will then be one of three:
     *
     *   1. THE PLAYER, when he stands inside the view zone;
     *   2. otherwise the nearest creature OF ITS OWN KIND inside the view zone, so that they
     *      gather into a band instead of each walking its own line;
     *   3. otherwise NOTHING: once clear of whatever it was going round, it simply carries on the
     *      way it was already going.
     *
     * Only the first is written today. The other two land here, and nowhere else.
     */
    private boolean computeWantedDirection() {

        java.util.List<GameObject> seen = collider.getSeen();

        if( seen == null )
            return false;

        // The player first, whatever else is about: he is what the game is played against, and a
        // creature that has him in sight has nothing better to do.
        GameObject target = null;

        for( GameObject object : seen ) {
            if( object instanceof MapGPSPos ) {
                target = object;
                break;
            }
        }

        // Failing him, one of its own kind -- the same class, not merely another creature -- so
        // that they gather into a band instead of each walking its own line. The nearest of them,
        // or they would all set off after whichever happened to be first in the list.
        if( target == null )
            target = nearestOfMyKind(seen);

        // Nothing worth walking at: it carries straight on.
        if( target == null )
            return false;

        return aimAt(target);
    }

    // The player, when he is one of the bodies this creature is touching, and null otherwise.
    private MapGPSPos touchedPlayer() {

        java.util.List<GameObject> hits = collider.getHits();

        if( hits == null )
            return null;

        for( GameObject object : hits ) {
            if( object instanceof MapGPSPos )
                return (MapGPSPos) object;
        }

        return null;
    }

    // The nearest creature of this very kind in that list, or null when there is none.
    private GameObject nearestOfMyKind(java.util.List<GameObject> seen) {

        GameObject nearest = null;
        double nearestSquare = 0;

        double myX = this.x + (double) width / 2;
        double myY = this.y + (double) height / 2;

        for( GameObject object : seen ) {

            if( object.getClass() != getClass() )
                continue;

            double dx = object.getX() - myX;
            double dy = object.getY() - myY;

            double square = dx * dx + dy * dy;

            if( (nearest == null) || (square < nearestSquare) ) {
                nearest = object;
                nearestSquare = square;
            }
        }

        return nearest;
    }

    // Points the wanted direction at that object, and says how far it is. False when the two are
    // on the very same spot, where no direction means anything.
    private boolean aimAt(GameObject target) {

        GameCollider targetCollider = target.getCollider();

        double dx = targetCollider.getCenterX(target.getColliderOffsetX()) - (this.x + (double) width / 2);
        double dy = targetCollider.getCenterY(target.getColliderOffsetY()) - (this.y + (double) height / 2);

        double length = Math.sqrt(dx * dx + dy * dy);

        if( length <= 0 )
            return false;

        wantedX = dx / length;
        wantedY = dy / length;
        wantedDistance = length;

        return true;
    }

    /**
     * Decides where to walk next, given what is in the way.
     *
     * Free of anything, it goes where it wants. Against something, it walks ALONG it rather than
     * into it -- and always round the same side, the one drawn when the creature was made, because
     * a side picked afresh on every frame is a side that flips.
     *
     * Two things make that work, and both were missing at first.
     *
     * A contact is REMEMBERED for a few updates after it is lost. Walking along the tangent of a
     * circle takes you off the circle -- by five hundredths of a pixel, but off it -- so contact is
     * lost on one update and found again on the next. Taken at face value, the creature then aims
     * at the player on one update and along the wall on the next, and the two cancel out: it shakes
     * on the spot and travels nowhere, which is exactly what the phone showed. The memory is
     * counted in UPDATES and not in milliseconds on purpose: what it compensates for is a rhythm of
     * frames, one in two, and a delay in milliseconds would stop covering it the day the frame rate
     * drops -- precisely when it would be needed most.
     *
     * And the wall is not let go of merely because the way ahead looks clear. That is the naive
     * rule, and it has no guarantee at all: let go too early, cross to the far wall, come back, and
     * go round for ever without finding the way past. The distance to the target is noted WHEN THE
     * WALL IS FIRST TOUCHED, and the wall is let go of only when the road is clear AND the creature
     * is nearer the target than it was then. Every release is therefore a gain, and the distance
     * can only fall from one contact to the next.
     */
    private void steerAround(double normalX, double normalY, boolean blocked) {

        // A creature that points itself is not pointed by anything else.
        if( ownsFacing() )
            return;

        if( !computeWantedDirection() )
            return;

        // TEMPORARY: going round obstacles is switched off while the seeing and the chasing are
        // being settled. It was turning a creature sideways the moment it reached the player --
        // the player being an obstacle like any other -- so it stood against him looking away.
        // The pushing apart stays: nothing walks through anything, it simply does not skirt.
        if( !AVOID_OBSTACLES ) {
            applyDirection(wantedX, wantedY);
            traceSteer("direct", normalX, normalY, 0);
            return;
        }

        if( blocked ) {
            lastNormalX = normalX;
            lastNormalY = normalY;
            updatesSinceContact = 0;
        } else {

            updatesSinceContact++;

            if( updatesSinceContact <= CONTACT_MEMORY_UPDATES ) {
                // Just off the wall, and only because walking along it takes you off it. Still the
                // wall as far as this creature is concerned.
                normalX = lastNormalX;
                normalY = lastNormalY;
                blocked = true;
            } else {
                // Really away from it now.
                following = false;
            }
        }

        String mode = "free";
        double into = 0;

        if( blocked ) {

            // Arriving against it: note how far the target was, which is what the release is
            // measured against.
            if( !following ) {
                following = true;
                followHitDistance = wantedDistance;
            }

            // The normal points from the obstacle towards the creature, so a negative dot product
            // is the creature wanting to walk into it. Anything else is a free road.
            into = wantedX * normalX + wantedY * normalY;

            boolean clear = (into > 0);
            boolean nearer = (wantedDistance < followHitDistance);

            if( clear && nearer ) {
                following = false;
                applyDirection(wantedX, wantedY);
                mode = "leave";
            } else {
                // Along the obstacle, on the side this creature always takes.
                applyDirection(-normalY * preferredSide, normalX * preferredSide);
                mode = clear ? "hold" : "follow";
            }

        } else {
            applyDirection(wantedX, wantedY);
        }

        traceSteer(mode, normalX, normalY, into);
    }

    /**
     * Picks the row of the sheet the creature is drawn from -- which way it faces -- and holds off
     * the change while it is dealing with an obstacle.
     *
     * Against a wall the heading swings by a lot from one update to the next, between along the
     * wall and towards the target, and the sprite followed every swing: the creature flickered
     * between facing left and facing down without going anywhere. So while a contact is live, a
     * new facing has to be EARNED -- the creature must actually have walked a few pixels that way
     * before its picture turns. Measured in pixels covered rather than in time, so that it behaves
     * the same whatever the frame rate.
     *
     * Away from any obstacle nothing is held off: the picture turns at once, as it always did.
     */
    private void updateRow(double stepPx) {

        int candidate;

        if( dirX > 0 )  {
            if(dirY > 0 && Math.abs(dirX) < Math.abs(dirY)) {
                candidate = ROW_TOP_TO_BOTTOM;
            }else if(dirY < 0 && Math.abs(dirX) < Math.abs(dirY)) {
                candidate = ROW_BOTTOM_TO_TOP;
            }else  {
                candidate = ROW_LEFT_TO_RIGHT;
            }
        } else {
            if(dirY > 0 && Math.abs(dirX) < Math.abs(dirY)) {
                candidate = ROW_TOP_TO_BOTTOM;
            }else if(dirY < 0 && Math.abs(dirX) < Math.abs(dirY)) {
                candidate = ROW_BOTTOM_TO_TOP;
            }else  {
                candidate = ROW_RIGHT_TO_LEFT;
            }
        }

        if( candidate == this.rowUsing ) {
            pendingRow = -1;
            pendingRowPx = 0;
            return;
        }

        // Nothing to do with an obstacle: turn straight away, as before.
        if( (updatesSinceContact > CONTACT_MEMORY_UPDATES) && !following ) {
            this.rowUsing = candidate;
            pendingRow = -1;
            pendingRowPx = 0;
            return;
        }

        if( candidate != pendingRow ) {
            pendingRow = candidate;
            pendingRowPx = 0;
        }

        pendingRowPx += stepPx;

        if( pendingRowPx >= ROW_CHANGE_PX ) {
            this.rowUsing = candidate;
            pendingRow = -1;
            pendingRowPx = 0;
        }
    }

    // Temporary, while the seeing is being tuned: what this creature has in sight, and what it
    // reaches. Same rate as the rest of the steering trace.
    private void traceSeen() {

        java.util.List<GameObject> seen = collider.getSeen();
        java.util.List<GameObject> hurts = collider.getHurts();

        StringBuilder names = new StringBuilder();

        double pxPerM = gameSurface.pixelsPerMeter();

        double myX = this.x + (double) width / 2;
        double myY = this.y + (double) height / 2;

        if( seen != null ) {
            for( GameObject object : seen ) {

                GameCollider oc = object.getCollider();

                double dx = oc.getCenterX(object.getColliderOffsetX()) - myX;
                double dy = oc.getCenterY(object.getColliderOffsetY()) - myY;

                double distance = Math.sqrt(dx * dx + dy * dy);

                if( names.length() > 0 )
                    names.append(", ");

                names.append(String.format(java.util.Locale.US, "%s %.0fm@%.0f",
                                           object.getClass().getSimpleName(),
                                           (pxPerM > 0) ? distance / pxPerM : distance,
                                           Math.toDegrees(Math.atan2(dx, -dy))));
            }
        }

        GBLog.d(getClass().getSimpleName(),
                String.format(java.util.Locale.US,
                              "sees %d [%s] | hidden %d | hurts %d | view %.0f px at %.0f deg +/- %.0f | facing %.0f deg",
                              (seen == null) ? 0 : seen.size(),
                              names.toString(),
                              collider.getHiddenCount(),
                              (hurts == null) ? 0 : hurts.size(),
                              collider.getViewRadius(),
                              getDirectionDeg(),
                              collider.getViewAngleDeg(),
                              getDirectionDeg()));
    }

    // Temporary, while the contact code is being tuned: once a second, what the creature decided
    // and what it decided it from. Debug mode only, and rate limited, since this runs in the
    // render loop.
    private void traceSteer(String mode, double normalX, double normalY, double into) {

        AppManager am = AppManager.getInstance();

        if( !am.getDebugMode() )
            return;

        long now = System.nanoTime();

        if( (lastSteerTraceNs > 0) && (((now - lastSteerTraceNs) / 1000000) < TRACE_PERIOD_MS) )
            return;

        lastSteerTraceNs = now;

        java.util.List<GameObject> hits = collider.getHits();

        GBLog.d(getClass().getSimpleName(),
                String.format(java.util.Locale.US,
                              "steer %-6s side %+d | hits %d | lost %d | want %.0f deg at %.0f px (hit at %.0f) | normal %.0f deg | into %+.2f | going %.0f deg",
                              mode, preferredSide,
                              (hits == null) ? 0 : hits.size(),
                              updatesSinceContact,
                              Math.toDegrees(Math.atan2(wantedX, -wantedY)), wantedDistance, followHitDistance,
                              Math.toDegrees(Math.atan2(normalX, -normalY)),
                              into,
                              Math.toDegrees(Math.atan2(dirX, -dirY))));
    }

    /**
     * The sound this kind of creature makes when it catches sight of the player: a raw resource,
     * or zero for a kind that says nothing. Zero by default, so a new kind is silent until it is
     * given a voice.
     */
    protected int getViewSoundRes() {
        return 0;
    }

    /**
     * The sound it makes when it is wounded, or zero to use the game's pool of generic cries.
     *
     * A boss with a voice of its own should answer in it: the five "aie" and "ouie" of the crowd
     * would say nothing about who had just been hit.
     */
    protected int getHurtSoundRes() {
        return 0;
    }

    /**
     * How loudly that cry is played, from 0 to 1.
     *
     * Full by default. A creature that takes a thousand hits to put down cries a thousand times,
     * and at the volume of a one-off it turns the fight into a nuisance -- so it may ask to be
     * barely audible without the sound itself being changed.
     */
    protected float getHurtSoundVolume() {
        return 1.0f;
    }

    /**
     * Calls out when the player comes into sight, and shuts up when something else needs the
     * stream.
     *
     * Only on the way IN: the cry belongs to the moment it spots him, not to every frame it keeps
     * him in view. Walk out of its sight and back in, and it calls again.
     *
     * The stream is kept so that the voice can be cut short -- a creature that is hit swallows its
     * own call and cries instead. Each creature has its own, so three of them seeing the player
     * together make three voices at once, which is the whole point of a band.
     */
    private void callOnSight() {

        // In a game only: outside it, a creature that wanders the map says nothing at all.
        AppManager am = AppManager.getInstance();

        if( !am.getIsGameMode() ) {
            sawPlayer = false;
            return;
        }

        boolean sees = false;

        java.util.List<GameObject> seen = collider.getSeen();

        if( seen != null ) {
            for( GameObject object : seen ) {
                if( object instanceof MapGPSPos ) {
                    sees = true;
                    break;
                }
            }
        }

        if( sees && !sawPlayer )
            cryOnSight();

        sawPlayer = sees;
    }

    /**
     * Cries out, unless something else is speaking.
     *
     * The action is "it has seen the player", and it is worth nothing once the moment has passed:
     * refused, it is dropped and not postponed. The flag is armed all the same -- a creature that
     * called out two seconds after spotting him would be lying about when it saw him.
     */
    private void cryOnSight() {

        int resId = getViewSoundRes();

        if( resId == 0 )
            return;

        long now = System.nanoTime();

        Long last = _lastCallNs.get(Integer.valueOf(resId));

        // Has this call been quiet long enough to be made again? Asked of the SOUND, so that two
        // creatures of the same kind take turns rather than speak together.
        if( (last != null) && (((now - last.longValue()) / 1000000) < VIEW_SOUND_GAP_MS) )
            return;

        long duration = gameSurface.soundDurationMs(resId) + GameSurface.ACTION_SILENCE_MS;

        if( !gameSurface.mayStartAnnouncedAction(duration) )
            return;

        _lastCallNs.put(Integer.valueOf(resId), Long.valueOf(now));

        viewStreamId = gameSurface.playSound(resId, VIEW_SOUND_VOLUME);
    }

    // Cuts the call short, whatever it was saying. Called when something louder happens to this
    // creature -- being hit, or going up in smoke.
    private void stopCall() {

        if( viewStreamId == 0 )
            return;

        gameSurface.stopSound(viewStreamId);
        viewStreamId = 0;
    }

    /**
     * Should this creature stay where it is, whatever it would otherwise want?
     *
     * False for the crowd, which walks at what it sees. A boss has phases where standing still IS
     * the behaviour.
     */
    protected boolean isHalted() {
        return false;
    }

    /**
     * Does this creature point itself, rather than being pointed by what it is walking at?
     *
     * False for the crowd, which always faces where it goes. True while a boss is running its own
     * business -- and then the steering keeps its hands off the direction, because the direction
     * is also what the eyes follow: turning the picture without turning the heading would leave a
     * creature looking one way and seeing another.
     */
    protected boolean ownsFacing() {
        return false;
    }

    /**
     * A creature's own mind, one step per update. Nothing by default: the crowd walks at what it
     * sees and needs no more than that.
     */
    protected void think() {
    }

    /**
     * Called on each update where this creature is against the player.
     *
     * The crowd pays its damage with its life: it goes off against him -- the explosion IS the
     * blow -- and is gone. A boss does not, which is why this can be overridden: Young-Hee is won
     * against by being touched, not lost against.
     */
    protected void onPlayerTouched() {

        AppManager am = AppManager.getInstance();

        // Temporary, with the trace above: this one fires on the very frame of the contact.
        if( am.getDebugMode() )
            GBLog.d(getClass().getSimpleName(),
                    String.format(java.util.Locale.US,
                                  "TOUCHED lvl %d game %b dmg %d boss %b",
                                  am.getGameLevel(), am.getIsGameMode(),
                                  getContactDamage(), isBoss()));

        if( !am.getIsGameMode() )
            return;

        MapGPSPos player = gameSurface.getGps();

        if( player != null )
            player.onHit(this.x + (double) width / 2, this.y + (double) height / 2);

        am.takePlayerHits(getContactDamage(), 0);

        die();
    }

    /**
     * Takes that many points off this creature, from something that is not a finger: a shot.
     *
     * The door a bullet knocks on. The work is the same as a tap's -- the same wound, the same
     * cry, the same explosion when there is nothing left -- only the way in differs.
     */
    public void takeShot(int damage, double fromX, double fromY) {

        stopCall();

        if( takeDamage(damage, fromX, fromY, true, true) ) {
            die();
            return;
        }

        // Still standing, and now it knows it was shot at.
        onShot();
    }

    /**
     * Called when a shot has wounded this creature without killing it. Nothing by default: the
     * crowd takes it and walks on.
     */
    protected void onShot() {
    }

    /** Takes this creature off the map at once, explosion and all. */
    public void kill() {
        die();
    }

    /** The four rows of a walk sheet, for whoever needs to point a creature by hand. */
    protected static final int ROW_FACING_DOWN  = ROW_TOP_TO_BOTTOM;
    protected static final int ROW_FACING_LEFT  = ROW_RIGHT_TO_LEFT;
    protected static final int ROW_FACING_RIGHT = ROW_LEFT_TO_RIGHT;
    protected static final int ROW_FACING_UP    = ROW_BOTTOM_TO_TOP;

    /**
     * Is this one a boss? False for the crowd, true below {@link ChibiBoss}.
     *
     * Asked rather than tested with instanceof, so that the question can be answered differently
     * one day -- a creature promoted for one level, say -- without hunting down every place that
     * asks it.
     */
    public boolean isBoss() {
        return false;
    }

    // --- temporary, while the contact at level 1 is being chased --------------------------------
    //
    // Once a second and per creature: the gap to the player, the distance the two HIT circles meet
    // at, both reference circles for comparison, and what the contact test actually answers. To be
    // taken out once the thing is understood.
    private static final long TRACE_CONTACT_MS = 1000;
    private long lastContactTraceNs = -1;

    private void traceContact() {

        AppManager am = AppManager.getInstance();

        if( !am.getDebugMode() )
            return;

        long now = System.nanoTime();

        if( (lastContactTraceNs > 0) && (((now - lastContactTraceNs) / 1000000) < TRACE_CONTACT_MS) )
            return;

        lastContactTraceNs = now;

        MapGPSPos player = gameSurface.getGps();

        if( player == null )
            return;

        GameCollider pc = player.getCollider();

        if( pc == null )
            return;

        double dx = pc.getCenterX(player.getColliderOffsetX())
                  - collider.getCenterX(getColliderOffsetX());
        double dy = pc.getCenterY(player.getColliderOffsetY())
                  - collider.getCenterY(getColliderOffsetY());

        double distance = Math.sqrt(dx * dx + dy * dy);

        java.util.List<GameObject> hits = collider.getHits();

        GBLog.d(getClass().getSimpleName(),
                String.format(java.util.Locale.US,
                              "contact? lvl %d game %b | dist %.1f | hit %.1f + %.1f = %.1f | ref %.1f + %.1f = %.1f | inHits %b halted %b solid %b dmg %d",
                              am.getGameLevel(), am.getIsGameMode(),
                              distance,
                              collider.getHitRadius(), pc.getHitRadius(),
                              collider.getHitRadius() + pc.getHitRadius(),
                              collider.getRadius(), pc.getRadius(),
                              collider.getRadius() + pc.getRadius(),
                              (hits != null) && hits.contains(player),
                              isHalted(), isSolid(), getContactDamage()));
    }

    /** Easter egg: a creature walks on its own legs, so it takes contacts on itself. */
    @Override
    protected double getMobility() {
        return 1.0;
    }

    /**
     * Easter egg: the game stood still -- the application was away, or one frame took far longer
     * than a frame. Every instant this creature counts from moves on by as much.
     *
     * Without it, the first frame back is a delta of however long the absence was: at eight
     * kilometres an hour and half an hour away, a creature crosses several maps in one step.
     */
    protected void timeJumped(long gapNs) {
        lastUpdateNanoTime = shifted(lastUpdateNanoTime, gapNs);
        lastColNanoTime = shifted(lastColNanoTime, gapNs);
    }

    // An instant never set is not an instant to move.
    protected static long shifted(long instantNs, long gapNs) {
        return (instantNs <= 0) ? instantNs : (instantNs + gapNs);
    }

    /** Easter egg: what this creature may not walk through. */
    @Override
    protected java.util.List<GameObject> getColliderTargets() {
        return gameSurface.getObstacles();
    }

    public boolean isVisible() {
        return placed && gameSurface.isVisible(this);
    }


}
