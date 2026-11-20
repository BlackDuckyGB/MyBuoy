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

/**
 * Young-Hee, the doll that counts to three.
 *
 * The first boss, and nothing like the crowd that came before: a thousand hits to put down, where
 * a ghost takes five. She walks at twelve kilometres an hour -- slower than the ghost, faster than
 * anyone running for long -- and she sees a very long way, ten times her own body, across a field
 * of a hundred and twenty degrees either side. Two hundred and forty degrees in all: there is
 * almost nowhere to stand where she is not already looking at you.
 *
 * She appears alone, from level 4. No creepers, no dolls, no ghost beside her -- the level IS her.
 *
 * The sheet is static: every creature of this kind shares one decode of it.
 */
public class ChibiYoungHee extends ChibiBoss {

    private static AvatarCatalog.Sheet _sheet = null;

    private static final int MAX_HITS = 1000;
    private static final double SPEED_KMH = 12.0;       // km/h, ChibiCharacter.DEFAULT_SPEED_KMH = 8
    // Zero : la toucher fait GAGNER, elle ne blesse pas au contact. Ses degats passent par son
    // obus et par lui seul.
    private static final int CONTACT_DAMAGE = 0;
    private static final int SCORE_VALUE = 1000;

    // Body and reach, as fractions of her reference circle.
    private static final double HIT_COEF = 0.5;
    private static final double DAMAGE_COEF = 0.5;

    // How far she sees, as a multiple of her collider, and how wide: half the field either side.
    private static final double VIEW_COEF = 3.0;
    private static final double VIEW_ANGLE_DEG = 120.0;

    // From level 4 only. Read by the orchestrator, which is what decides who appears.
    static final int MIN_LEVEL = 4;

    /** Decodes the sheet of this kind of creature. Called once, before the first one is made. */
    public static void loadSheet(Resources res) {
        if (_sheet == null)
            _sheet = AvatarCatalog.loadSheet(res, R.drawable.av_walk_younghee);
    }

    public ChibiYoungHee(GameSurface gameSurface) {
        super(gameSurface, _sheet);

        // Tighter than the crowd: her picture is four times theirs and carries far more air
        // around her, so eight tenths of it would have her stopping a long way from anything.
        collider.setHitCoef(HIT_COEF);
        collider.setDamageCoef(DAMAGE_COEF);

        collider.setViewCoef(VIEW_COEF);
        collider.setViewAngleDeg(VIEW_ANGLE_DEG);
    }

    public int getMaxHits() {
        return MAX_HITS;
    }

    public double getSpeedKmh() {
        return SPEED_KMH;
    }

    public int getContactDamage() {
        return CONTACT_DAMAGE;
    }

    public int getScoreValue() {
        return SCORE_VALUE;
    }

    public int getMinLevel() {
        return MIN_LEVEL;
    }

    /**
     * She is a head above everything on the map.
     *
     * So nothing hides from her: neither a mark nor another creature stands between her and what
     * she is looking at. She remains solid all the same -- she sees over the bollard, she does not
     * walk through it.
     */
    @Override
    protected boolean isGiant() {
        return true;
    }

    // Which of her two cries comes next. She alternates rather than repeating one: the same
    // syllable a thousand times running is a tic, two taking turns is a character.
    private boolean cryOuie = false;

    /** Her own cry when a tomato gets through: the two halves of "you moved", in turn. */
    @Override
    protected int getHurtSoundRes() {

        cryOuie = !cryOuie;

        return cryOuie ? R.raw.eg_younghee_ouie : R.raw.eg_younghee_aie;
    }

    /** Very quietly: she takes a thousand hits, and a thousand full-volume cries is a headache. */
    @Override
    protected float getHurtSoundVolume() {
        return 0.45f;
    }

    // She says nothing on simply catching sight of the player: her voice is the game's, and the
    // game below decides when she speaks.
    @Override
    protected int getViewSoundRes() {
        return 0;
    }

    // --- the game of red light, green light ------------------------------------------------

    // Looking for the player: she walks, says nothing, and the game has not begun.
    private static final int PHASE_SEEKING = 0;

    // Her back to the player, counting. He may move.
    private static final int PHASE_GREEN = 1;

    // Facing him, watching. Moving now is seen.
    private static final int PHASE_RED = 2;

    // Turning on the spot, between the two.
    private static final int PHASE_TURNING = 3;

    // Out of her level: found the player and stopped there, doing nothing with him. She waits to
    // be killed. Her game belongs to her level -- below it she has nothing to play, and a boss
    // left shooting at a player who has already lost is a boss that outlived its own game.
    private static final int PHASE_DORMANT = 4;

    // How long each phase lasts, in milliseconds, drawn afresh every time. Red is the longer of
    // the two: it is the one where standing still costs something to hold.
    private static final long GREEN_MIN_MS = 3000;
    private static final long GREEN_MAX_MS = 10000;
    private static final long RED_MIN_MS = 8000;
    private static final long RED_MAX_MS = 13000;

    // The grace at the start of a red light: she has just turned round, and the player needs the
    // time to come to a stop before being told he moved. The red light was lengthened by as much,
    // so that what is left to hold still is what it was.
    private static final long RED_GRACE_MS = 2000;

    // The half-turn, and the three pictures it goes through: facing, side on, back turned. The
    // sheet is drawn in perspective, so the sprite is never rotated -- a face tilted by forty-five
    // degrees is a face lying down, not a three-quarter view.
    private static final long TURN_MS = 2000;

    // One time in three she counts out loud instead of just calling the green light.
    private static final int COUNTING_ONE_IN = 3;

    private int phase = PHASE_SEEKING;
    private long phaseUntilNs = -1;

    // What the turn is for: the phase it leads to, and the two headings it goes between, in
    // degrees from north. The HEADING is turned and not the picture -- the picture is drawn from
    // the heading, and so is the field of view, so turning one without the other would have her
    // facing the player and watching the hedge.
    private int turnTo = PHASE_GREEN;
    private long turnStartNs = -1;
    private double headingFrom = 0;
    private double headingTo = 180;

    // Said once per red light, not once per frame -- and shot at once, for the same reason: the
    // point of a single shot is that it can be dodged.
    private boolean toldHeMoved = false;

    // Ce que paient un feu rouge tenu sans bouger, et la victoire au contact.
    private static final int RED_HELD_POINTS = 100;

    // De combien le gain flotte au-dessus du marqueur, quand il n'a pas de point d'impact.
    private static final double PLAYER_POPUP_UP = 40.0;
    private static final int WIN_POINTS = 500;

    // Where the shot leaves from, as a fraction of her reference radius above its centre: her
    // head, not her middle.
    private static final double MUZZLE_UP = 0.20;

    // She does not aim at the middle of him but half a body to one side, drawn at random. A shot
    // dead on the centre could only be survived by being somewhere else entirely; aimed off, it
    // can be stepped out of -- and which way is never known in advance.
    private static final double AIM_OFF = 0.8;

    // She says it before she does it: the shell leaves when "you moved" has finished speaking.
    // The wait is the length of the file itself, read from it -- re-record the sound and the shot
    // follows, with nothing to adjust here.

    // When the shell is due to leave, or -1 when none is owed. Cleared by the end of the red
    // light: she does not fire into a green one.
    private long shotDueNs = -1;

    // He was seen moving under the red light, but she could not say so yet because something else
    // was speaking. Latched, so that stopping in the meantime does not get him off: the sanction
    // is delayed, never cancelled.
    private boolean caughtMoving = false;

    // When the red light starts counting what it sees, which is a few seconds after it starts.
    private long watchFromNs = -1;

    /** Her game is all deadlines, and they wait with everything else. */
    @Override
    protected void timeJumped(long gapNs) {
        super.timeJumped(gapNs);

        phaseUntilNs = shifted(phaseUntilNs, gapNs);
        turnStartNs = shifted(turnStartNs, gapNs);
        shotDueNs = shifted(shotDueNs, gapNs);
        watchFromNs = shifted(watchFromNs, gapNs);
    }

    /**
     * She stands still for all of it, except while she is still looking for the player.
     *
     * The game is a game of NOT moving, and it would be a poor one if she walked about during it.
     * The one exception is the red light with the player drifting out of her field: she closes in
     * rather than lose him -- handled in the phase itself, which lifts this.
     */
    @Override
    protected boolean isHalted() {
        return (phase != PHASE_SEEKING) && !closingIn;
    }

    private boolean closingIn = false;

    /**
     * She points herself for the whole of her game, and the steering keeps away.
     *
     * Except while she is still looking for the player, and except while she is closing in on him
     * under the red light -- in both she walks at something, and facing where she walks is right.
     */
    @Override
    protected boolean ownsFacing() {
        return (phase != PHASE_SEEKING) && !closingIn;
    }

    /**
     * Hit by a tomato, she fires back.
     *
     * At once, and whatever she was in the middle of: this one is not announced and not waited
     * for, unlike the shot she owes a player who moved under her red light. Throwing at her is
     * therefore a trade, not a free hit -- and with a thousand lives to take off her, that trade
     * is the fight.
     *
     * Below her level the trade is off: she takes the hit and gives nothing back.
     */
    @Override
    protected void onShot() {

        // Not below her level: there she only waits to be taken apart, and shooting back is the
        // one thing that would still make a game of it.
        if( AppManager.getInstance().getGameLevel() < MIN_LEVEL )
            return;

        MapGPSPos player = gameSurface.getGps();

        if( player != null )
            shootAt(player);
    }

    /**
     * May she start the action that opens on this line?
     *
     * The floor is asked for the SPEECH and for the speech only: everything that hangs off it --
     * the half turn, the two seconds of grace, the shell, her own death -- stops nobody else from
     * speaking and has no business in the reservation. Each of her actions opens on its line, so
     * the reservation is simply the line plus the silence.
     *
     * Refused, she STAYS IN HER PHASE and asks again on the next frame. She does not lose her
     * turn, she is late -- and late means nothing here: a green light already lasts between three
     * and ten seconds, a red one between eight and thirteen. A second of waiting is that random
     * length, slightly longer.
     *
     * See GameSurface.mayStartAnnouncedAction for the rule itself.
     */
    private boolean maySay(int resId) {

        long duree = gameSurface.soundDurationMs(resId) + GameSurface.ACTION_SILENCE_MS;

        return gameSurface.mayStartAnnouncedAction(duree);
    }

    /** Touching her is winning: everything she has left goes at once. */
    @Override
    protected void onPlayerTouched() {

        ScorePopup.score(gameSurface,
                         this.x + (double) width / 2,
                         this.y + (double) height / 2,
                         WIN_POINTS);

        // The one line that asks for nothing: being touched ends her, and the win cannot be put
        // off to the next frame because something else is speaking. It takes the floor instead, so
        // that nothing starts over it.
        gameSurface.takeAnnouncedAction(gameSurface.soundDurationMs(R.raw.eg_younghee_you_won)
                                        + GameSurface.ACTION_SILENCE_MS);

        gameSurface.playSound(R.raw.eg_younghee_you_won, 1.0f);

        kill();
    }

    /**
     * The whole of her game, one step per update.
     *
     * She looks for the player; when he stands ENTIRELY inside her field she stops, counts him in,
     * and turns her back. From then on it alternates: green while her back is turned, red while
     * she watches, with a turn of two seconds between the two and a few seconds drawn at random on
     * either side. Moving under the red light is seen and said -- it costs nothing yet; the shot
     * that will answer it comes later.
     */
    @Override
    protected void think() {

        long now = System.nanoTime();

        closingIn = false;

        MapGPSPos player = gameSurface.getGps();

        if( player == null )
            return;

        boolean wholeInSight = collider.isFullySeen(player);

        // Below her level there is no game to play: she looks for the player, and once she has him
        // she stops at that. Handled before the phases rather than inside each of them, because
        // it is not a move in the game -- it is the game not being on.
        if( AppManager.getInstance().getGameLevel() < MIN_LEVEL ) {
            dormant(player, wholeInSight);
            return;
        }

        // Her level, back again: she picks her game up from the beginning.
        if( phase == PHASE_DORMANT )
            phase = PHASE_SEEKING;

        switch( phase ) {

            case PHASE_SEEKING:

                // Found: the game begins where he stands. She counts him in and turns her back.
                if( wholeInSight && maySay(R.raw.eg_younghee_1_2_3_green_light) ) {
                    gameSurface.playSound(R.raw.eg_younghee_1_2_3_green_light, 1.0f);
                    beginTurn(PHASE_GREEN, headingTowards(player) + 180, now);
                }

                break;

            case PHASE_TURNING:

                long elapsed = (now - turnStartNs) / 1000000;

                if( elapsed >= TURN_MS ) {

                    faceTowards(headingTo);

                    // Turned round to find nobody there: he has left her field entirely while her
                    // back was turned. Nothing to play against, so she goes looking for him again
                    // and the game starts over when she finds him.
                    if( (turnTo == PHASE_RED) && !seesAtAll(player) ) {
                        phase = PHASE_SEEKING;
                        break;
                    }

                    enter(turnTo, now);
                    break;
                }

                // Half a turn spread over two seconds. The picture goes through the profile on its
                // own, since the row is read off the heading, and the field of view sweeps with
                // her -- which is what makes the turn mean something.
                faceTowards(headingFrom + (headingTo - headingFrom) * elapsed / (double) TURN_MS);

                break;

            case PHASE_GREEN:

                if( (now >= phaseUntilNs) && maySay(R.raw.eg_younghee_red_light) ) {
                    gameSurface.playSound(R.raw.eg_younghee_red_light, 1.0f);
                    beginTurn(PHASE_RED, headingTowards(player), now);
                }

                break;

            case PHASE_RED:

                // He has drifted out of her field: she closes in rather than lose him.
                if( !wholeInSight )
                    closingIn = true;

                // Moving under the red light is noticed, and said once -- but not in the first
                // seconds of it, which are his to come to a stop in.
                if( (now >= watchFromNs) && !toldHeMoved && !isStill() )
                    caughtMoving = true;

                if( caughtMoving && maySay(R.raw.eg_younghee_you_moved) ) {
                    gameSurface.playSound(R.raw.eg_younghee_you_moved, 1.0f);

                    // The shell at the END of the sentence, counted from the moment she actually
                    // starts saying it and not from the moment she caught him.
                    shotDueNs = now + gameSurface.soundDurationMs(R.raw.eg_younghee_you_moved) * 1000000L;

                    toldHeMoved = true;
                    caughtMoving = false;
                }

                // And the shell once she has finished saying it.
                if( (shotDueNs > 0) && (now >= shotDueNs) ) {
                    shootAt(player);
                    shotDueNs = -1;
                }

                // Her light does not end until she can announce the next one: nothing else here
                // moves on its own, so waiting for the floor only makes a phase that was already
                // of a random length a little longer. Nobody can see that.
                if( now >= phaseUntilNs ) {

                    // Un feu rouge tenu de bout en bout sans bouger : le seul endroit du jeu qui
                    // paie l'immobilite.
                    // Tenu de bout en bout : le gain parait au-dessus du joueur, puisque c'est lui
                    // qui n'a pas bouge.
                    if( !toldHeMoved )
                        ScorePopup.score(gameSurface,
                                         player.getAnchorOriginX(),
                                         player.getAnchorOriginY() - PLAYER_POPUP_UP,
                                         RED_HELD_POINTS);

                    int sound = (((int) (Math.random() * COUNTING_ONE_IN)) == 0)
                            ? R.raw.eg_younghee_1_2_3_green_light
                            : R.raw.eg_younghee_green_light;

                    if( !maySay(sound) )
                        break;

                    gameSurface.playSound(sound, 1.0f);
                    beginTurn(PHASE_GREEN, headingTowards(player) + 180, now);
                }

                break;

            default:
                break;
        }
    }

    /**
     * One step out of her level: she looks, she finds, she stops.
     *
     * No sound and no half-turn when she finds him -- those announce a game, and there is none.
     * And nothing owed either: a shell already promised is dropped, since the "you moved" it
     * answers belonged to a red light that will not come.
     *
     * Having lost sight of him altogether, she goes looking again, and so on for as long as the
     * level stays below hers. PHASE_DORMANT is not PHASE_SEEKING, which is all isHalted() and
     * ownsFacing() need to know to hold her still and leave her facing alone.
     */
    private void dormant(MapGPSPos player, boolean wholeInSight) {

        shotDueNs = -1;

        if( phase == PHASE_DORMANT ) {

            if( !seesAtAll(player) )
                phase = PHASE_SEEKING;

            return;
        }

        // Any phase of the game she was in the middle of is dropped: only the two above are hers
        // now, and looking for him is where that starts.
        if( phase != PHASE_SEEKING ) {
            phase = PHASE_SEEKING;
            return;
        }

        if( wholeInSight )
            phase = PHASE_DORMANT;
    }

    // Is any part of him in her field at all? The loose question, where isFullySeen asks the
    // strict one: she only needs him wholly in the open to START, not to keep playing.
    private boolean seesAtAll(GameObject target) {

        java.util.List<GameObject> seen = collider.getSeen();

        if( seen == null )
            return false;

        return seen.contains(target);
    }

    // One shell, from her head, at the middle of him. Once per red light: a single shot is a shot
    // that can be side-stepped, which is the game.
    private void shootAt(MapGPSPos player) {

        double headX = collider.getCenterX(getColliderOffsetX());
        double headY = collider.getCenterY(getColliderOffsetY()) - collider.getRadius() * MUZZLE_UP;

        GameCollider target = player.getCollider();

        double targetX = target.getCenterX(player.getColliderOffsetX());
        double targetY = target.getCenterY(player.getColliderOffsetY());

        double toX = targetX - headX;
        double toY = targetY - headY;

        double length = Math.sqrt(toX * toX + toY * toY);

        // Half a hit radius to one side or the other, across the line of fire.
        if( length > 0 ) {

            double side = (Math.random() < 0.5) ? -1 : 1;
            double off = target.getHitRadius() * AIM_OFF * side;

            targetX += (-toY / length) * off;
            targetY += (toX / length) * off;
        }

        double angleDeg = Math.toDegrees(Math.atan2(targetX - headX, -(targetY - headY)));

        // A bullet does not put itself on the map -- unlike a creature, which adds itself in its
        // constructor -- so it is handed over, or it flies nowhere and is never drawn.
        gameSurface.addBullet(new BulletGun(gameSurface, headX, headY, angleDeg));
    }

    // Is the player still? The phone itself at rest (MotionSensor): it answers in under a second,
    // where the GPS speed lags ~2.5 s and drifts at rest.
    private boolean isStill() {
        AppManager am = AppManager.getInstance();

        return am.isPhoneStill();
    }

    private void beginTurn(int to, double towardsDeg, long now) {

        phase = PHASE_TURNING;
        turnTo = to;
        turnStartNs = now;

        headingFrom = getDirectionDeg();

        // The shorter way round, so a half-turn is a half-turn and not three quarters of one.
        double delta = ((towardsDeg - headingFrom + 540) % 360) - 180;

        headingTo = headingFrom + delta;
    }

    // Points her at that heading, in degrees from north.
    private void faceTowards(double deg) {
        applyDirection(Math.sin(Math.toRadians(deg)), -Math.cos(Math.toRadians(deg)));
    }

    private void enter(int next, long now) {

        phase = next;

        if( next == PHASE_GREEN ) {
            phaseUntilNs = now + randomMs(GREEN_MIN_MS, GREEN_MAX_MS) * 1000000L;
            watchFromNs = now;
        } else {
            phaseUntilNs = now + randomMs(RED_MIN_MS, RED_MAX_MS) * 1000000L;
            watchFromNs = now + RED_GRACE_MS * 1000000L;
        }

        toldHeMoved = false;
        shotDueNs = -1;
    }

    private static long randomMs(long low, long high) {
        return low + (long) (Math.random() * (high - low));
    }

    // The heading that has her looking straight at that object, in degrees from north.
    private double headingTowards(GameObject target) {

        double dx = target.getCollider().getCenterX(target.getColliderOffsetX())
                  - (this.x + (double) width / 2);
        double dy = target.getCollider().getCenterY(target.getColliderOffsetY())
                  - (this.y + (double) height / 2);

        return Math.toDegrees(Math.atan2(dx, -dy));
    }
}
