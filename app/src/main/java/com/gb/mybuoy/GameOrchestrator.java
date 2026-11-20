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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * The one that decides what happens: who appears, when, and who the game is watching.
 *
 * Everything about the RULES of the game belongs here rather than in the creatures themselves. A
 * creature knows how to walk, to be hit and to come back; it has no business knowing how many of
 * its kind the wave calls for. That question is asked once per frame, in one place, by this.
 *
 * WHAT a level is made of is not here either: it is written in {@link GameLevels}, ten waves to a
 * level, one line of text to a wave. This class plays what is written there and nothing else -- it
 * chooses no species, invents no headcount, and has not one line that knows a boss from a creeper.
 *
 * Three things, then:
 *
 *   - OUT OF PLAY, at level zero: the field is emptied of what has no business on it, one creeper
 *     is kept roaming, and the player who blows them up one after another finds the game. That
 *     creeper is worth NOTHING: the discovery is not a way of scoring.
 *   - IN PLAY: the wave is sent in, the front is kept full, and the one who dawdles gets the hurry
 *     up -- a notch every so often, as its own line says, each one widening the front, pulling one
 *     creature out of the wave's own reserve, and tightening the ground under the player's feet.
 *     Clearing the tenth wave moves up a level.
 *   - THE CAMERA: the creature whose play area is drawn. There are as many play areas as there are
 *     creatures, but drawing them all would turn the map into a pile of circles, so the game
 *     watches one at a time and stays on it long enough to be followed.
 *
 * Replaces Orchestrator, which is kept beside it for the time being and is no longer called.
 */
public class GameOrchestrator {

    // How long the camera stays on a creature before another may take it, in milliseconds. Short
    // enough that a new arrival is not ignored, long enough that three creatures appearing at once
    // do not make the shape flicker between them.
    private static final long MAIN_HOLD_MS = 5000;

    // Between two waves: the time for the last explosions to finish and for the counter to be seen
    // changing. Nothing is sent in during it.
    private static final long WAVE_BREATH_MS = 1500;

    // --- the hurry up --------------------------------------------------------------------------
    //
    // It does not punish slowness, it forbids waiting: without it the game is won by standing in a
    // corner and tapping whatever comes. It is reset by every WAVE and not by the level, since it
    // is the wave that is dragging on, and it does not choose anything -- it empties the reserve
    // the wave was written with, in order. How long between two notches is the WAVE's business
    // too, written beside its patience -- "@25/8" is twenty-five seconds of grace then a notch
    // every eight.


    // How wide the field grows has no ceiling here, and does not need one: it is the reserve that
    // says it, group by group, and nothing else should contradict what the table was written to
    // say. A player who holds out long enough for it to run away is a player who has been dead for
    // a while -- the creatures walk to him and nine hits are all it takes.
    //
    // The tightening of the ground does have one, and it is a game value and not a guard: ten
    // notches of 0.05 is exactly DELAY_COEF_MIN.
    //
    // The front has one too, but that one IS a guard, and it is there for a single case: a reserve
    // that ends on a group with a plus grows the field at every notch for ever, and nothing in the
    // table stops it. MEASURED on the A26, 08/10/2026, with a wave whose front climbed by ten
    // every two seconds: 25 fps held to about 180 creatures, the update crossing its 40 ms budget
    // at 185, and the cost climbing faster than the count -- 10 ms at 60, 22 at 140, 32 at 180,
    // 55 at 230, 90 at 320. Memory was never the limit, PSS stayed around 330 MB throughout. A
    // hundred costs about 16 ms there, which is 40 % of the budget: a phone twice as slow still
    // holds its frame. No wave written by hand comes anywhere near it.
    //
    // And a phone that has SAID it is slow gets a lower one: low resource mode already drops the
    // frame rate to fifteen, and it is the same admission -- this one cannot keep up. Sixty is
    // ten milliseconds of update on the A26, a quarter of what the slower frame allows.
    //
    // The announcements have none: they keep coming for as long as the player holds out, even once
    // nothing hardens any more. A game that stops shouting looks like one that has given up on him.
    private static final int STRESS_DELAY_MAX = 10;
    private static final int FRONT_MAX = 100;
    private static final int FRONT_MAX_LOW = 60;

    // What a notch does to the ground: the play area is a DELAY, so shaving it is what makes the
    // creatures appear closer and leaves less time to see them coming. It is the only lever that
    // actually hurries the player -- speeding a creature up widens its own area by as much and it
    // arrives no sooner.
    private static final double DELAY_COEF_STEP = 0.05;
    private static final double DELAY_COEF_MIN = 0.50;

    // What a reinforcement pays. A creeper sent by the hurry up is not worth the ten points of one
    // the wave called for: a wave that produces them on tap would make dawdling the best way to
    // score. One point, whatever the species -- and the multipliers still apply on top, so it is
    // the skill that pays and never the waiting.
    private static final int REINFORCEMENT_POINTS = 1;

    // What a pole pays with, besides points. Loud enough to be heard over a fight, which is
    // where it is going to happen.
    private static final float POLE_VOLUME = 0.8f;

    // What the roaming creeper of level zero pays: nothing at all. Not the kill, not the hit, not
    // the streak. Finding the easter egg is not a way of making a score.
    private static final int STRAY_POINTS = 0;

    private final GameSurface gameSurface;

    // The creature the play area is drawn for, and when it took the camera.
    private ChibiCharacter mainChibi = null;
    private long mainSinceNs = -1;

    // The fix BEFORE the one in hand, and when it arrived. A pole is touched when the segment
    // between those two fixes passes through it: that segment is the ground actually walked over,
    // once a second or so, and it is the only thing a contact is judged on.
    //
    // The raw fix and not the position the avatar is drawn on -- that one is carried forward
    // along the cap, and in a turn it projects along the chord, a good two metres off the ground
    // under one's feet.
    private double lastFixLat = 0;
    private double lastFixLon = 0;
    private long lastFixMs = -1;

    // The one before that, kept for the TURN: three points are what it takes to know that the
    // segment being tested is a chord and not a path. See GameLevels.poleTurnM.
    private double prevFixLat = 0;
    private double prevFixLon = 0;
    private boolean prevFixKnown = false;

    // How far the chord of the last segment strays from the corner it cut, in metres. Nil in a
    // straight line.
    private double turnStrayM = 0;

    // Pole points put aside towards the next portion of heart. Not the score: the REFERENCE part
    // of each pole's price, so that the same course mends the same amount at any speed.
    private int healPoints = 0;

    // And where the avatar was DRAWN on the frame before, in area pixels. That is the estimated
    // position -- the fix carried forward along the cap, anticipation included, which is what
    // AppManager.getLatitude() answers. A pole is taken on the raw fixes OR on this, whichever
    // reaches it first: the raw segment is the ground that was really walked, and this one is
    // what the player watches himself walk over, up to a second before the fix proving it lands.
    private double lastDrawnX = 0;
    private double lastDrawnY = 0;
    private boolean lastDrawnKnown = false;

    // The two paths of this frame, in area pixels, and how wide they count. Worked out once in
    // poles() and read by everything that can be walked over -- the poles through their own
    // collider, the hearts through reaches().
    private double segFixX0 = 0, segFixY0 = 0, segFixX1 = 0, segFixY1 = 0;
    private double segDrawX0 = 0, segDrawY0 = 0, segDrawX1 = 0, segDrawY1 = 0;
    private boolean segFixOn = false;
    private boolean segDrawOn = false;
    private double touchM = 0;

    // --- the level being played ----------------------------------------------------------------

    private int level = -1;
    private int waveNo = 0;

    private GameLevels.Wave wave = null;

    // What is left to send, in the order written. The entries of the wave itself come first and
    // the reinforcements are appended behind them, which is what pendingOwn counts: as long as it
    // is above zero, the next entry out of the list is one of the wave's own.
    private final List<Integer> pending = new ArrayList<Integer>();
    private int pendingOwn = 0;

    // How far into the reserve the hurry up has got. Past its end the last entry repeats, so a
    // wave never runs out of what it takes to finish the player.
    private int reserveAt = 0;

    // The ones it sent and that are still standing. Keeping the references is what lets a wave be
    // counted exactly: a creature leaves this list by dying, and the creeper that was already
    // roaming when the game started was never in it.
    private final List<ChibiCharacter> sent = new ArrayList<ChibiCharacter>();

    // How many stand at once, reserve included, and how many notches of hurry up have been taken.
    private int front = 0;
    private int notches = 0;

    // Of those, the ones the clock took: what the player is holding up, as opposed to what the
    // level handed him on a plate. It is this one the progress bar turns orange and red on -- a
    // replayed level starts with notches already taken and has no business looking like a failure.
    private int timedNotches = 0;

    // The notches a level STARTS at: zero for a level with a table of its own, more for one that
    // replays an earlier table.
    private int baseNotches = 0;

    private long waveStartedNs = -1;
    private long nextNotchMs = -1;

    // When the notch that has just been announced is to be felt: the end of the announcement, so
    // that the reinforcement arrives as its consequence and not alongside it. Below zero when
    // there is nothing waiting.
    private long biteDueNs = -1;

    // What the hurry up will say in this wave, one per notch: one of the two warnings for the
    // first, one of the two threats for the second.
    private final int[] hurrySays = new int[4];

    // Which of the two each pair is on. A toggle and not a draw: tossing a coin says the same
    // thing twice in a row often enough to be noticed, and a player who hears "too slow!" three
    // waves running thinks the game only knows one line. It survives the level, so the alternation
    // is one long one and not one that restarts.
    private int hurryTurn = 0;

    // Between two waves, and nothing sent in until it is past.
    private long breatheUntilNs = -1;

    // --- finding the easter egg ----------------------------------------------------------------
    //
    // The game does not announce itself: it is found by blowing up creepers one straight after
    // another. Each APPEARANCE counts for one step -- three creepers coming out together is one
    // event, not three, so the steps are all the player's to make -- and the whole count falls
    // back to nothing as soon as more than EG_WINDOW_MS goes by without one.
    //
    // The field answers as it climbs, which is the only hint there is: one creeper, then three,
    // then five, then the game.
    private static final long EG_WINDOW_MS = 5000;

    private static final int EG_STEPS_3 = 5;
    private static final int EG_STEPS_5 = 8;
    private static final int EG_STEPS_GAME = 9;

    // The way back in, for someone who has already found it. The long climb IS the discovery, and
    // a discovery is made once: having been there, three steps are enough to get back.
    private static final int EG_STEPS_SPEED_GAME = 3;

    private int egSteps = 0;
    private long egExpiresNs = -1;

    // Game over: the field empties, but not at once and not all together.
    //
    // The announcement is left to finish speaking -- its length is read from the file, so
    // re-recording the "game over", or the doll's "you are dead", is all it takes -- then a second
    // of silence, and the creatures go off one by one. Blowing them all up on the same frame would
    // make one shapeless bang; spaced out, they can be counted.
    private static final long CLEAR_AFTER_SOUND_MS = 1000;
    private static final long CLEAR_STEP_MS = 220;

    private long nextBlastNs = -1;

    public GameOrchestrator(GameSurface gameSurface) {
        this.gameSurface = gameSurface;
    }

    /** The creature the game is watching: the only one whose play area is drawn. */
    public ChibiCharacter getMainChibi() {
        return mainChibi;
    }

    /**
     * What the play area's delay is multiplied by, from 1 down to {@link #DELAY_COEF_MIN}.
     *
     * One outside a game, so that nothing of the hurry up survives it.
     */
    public double getDelayCoef() {
        return delayCoef();
    }

    /**
     * A creature has just been placed -- it has spawned, or it has come back from outside the play
     * area -- and asks for the camera.
     *
     * It gets it if nobody holds it, or if the one who holds it has had it for long enough. That
     * hold is the whole point: without it, a creature that is being dropped and replaced every
     * second would keep the camera to itself and the shape would jump about.
     */
    public void placed(ChibiCharacter chibi) {

        if( chibi == null )
            return;

        long now = System.nanoTime();

        if( (mainChibi != null) && (mainChibi != chibi) ) {

            long heldMs = (now - mainSinceNs) / 1000000;

            if( heldMs < MAIN_HOLD_MS )
                return;
        }

        mainChibi = chibi;
        mainSinceNs = now;
    }

    /**
     * One frame of the game's own business: the wave, the hurry up, and the camera.
     */
    public void update() {

        AppManager am = AppManager.getInstance();

        int playing = am.getGameLevel();

        // The camera first: the creature it was on may have been blown up since the last frame,
        // and a dead creature must not keep it -- nothing would ever be drawn again.
        if( (mainChibi != null) && !gameSurface.hasChibi(mainChibi) ) {
            mainChibi = null;
            mainSinceNs = -1;
        }

        long now = System.nanoTime();

        timeJump(now, am);

        // A new level is a fresh start: its first wave, and whatever stress it starts at.
        if( playing != level )
            startLevel(playing, now, am);

        if( playing <= 0 ) {
            outOfPlay(now, am);
            return;
        }

        trace();

        // The ones it sent and that have been blown up since leave the count.
        prune();

        // Nobody being watched, and creatures on the map: the camera falls on one of them at once
        // rather than waiting for the next arrival. Blowing up the creature it was on must not
        // leave the screen without a shape until something happens to appear.
        camera();

        progress(am, now);

        poles(am, now);

        hearts(am, now);

        items(am, now);

        // Le double appui du coin demande le niveau suivant. Il passe par la meme porte qu'une
        // fin de niveau -- meme musique, meme banniere, meme attente de la parole -- et la demande
        // est gardee tant qu'elle n'a pas ete servie. Elle vit dans AppManager et nulle part
        // ailleurs, ce qui est ce qui permet d'en refuser une seconde tant que celle-ci court.
        if( am.isLevelSkipAsked() && levelUp(am, false) ) {
            am.clearLevelSkip();
            return;
        }

        // Between two waves nothing is sent in, and the hurry up is not even asked: the wave that
        // was dragging on is over, and the next one has not started.
        if( breatheUntilNs > 0 ) {

            if( now < breatheUntilNs )
                return;

            breatheUntilNs = -1;

            loadWave(now);
        }

        // What a notch announced a moment ago, now that the announcement has had its say.
        if( (biteDueNs > 0) && (now >= biteDueNs) ) {
            biteDueNs = -1;
            bite();
        }

        hurryUp(now, am);

        fillFront();

        // Everything sent, and nothing left standing of what was sent: the wave is done. The
        // creeper that was already roaming when the game started is not in that count -- it was
        // never part of any wave.
        if( pending.isEmpty() && sent.isEmpty() )
            waveCleared(now, am);
    }

    /** Nothing of its own to draw yet. Same signature as everything else that draws itself. */
    public void draw(Canvas canvas, int layer) {
    }

    // --- playing a level -----------------------------------------------------------------------

    private void startLevel(int playing, long now, AppManager am) {

        level = playing;

        // Whatever was still in the air belonged to the level that is over.
        pending.clear();
        pendingOwn = 0;
        sent.clear();

        wave = null;
        waveNo = 0;
        notches = 0;
        front = 0;
        breatheUntilNs = -1;

        lastFixMs = -1;
        prevFixKnown = false;
        turnStrayM = 0;
        lastDrawnKnown = false;

        if( playing <= 0 ) {
            closePoles(am);
            return;
        }

        armPoles(playing, now, am);

        // Back in play: the next return to level zero waits for its announcement again.
        nextBlastNs = -1;

        baseNotches = GameLevels.startStress(playing);

        waveNo = 1;
        am.setGameWave(waveNo);

        loadWave(now);
    }

    /** The wave the table describes, taken as this one's own and started from nothing. */
    private void loadWave(long now) {

        wave = GameLevels.wave(level, waveNo);

        pending.clear();

        for( int species : wave.queue )
            pending.add(species);

        // All of them are the wave's own, for now: the reinforcements are appended behind.
        pendingOwn = pending.size();

        reserveAt = 0;
        front = wave.front;
        notches = 0;

        waveStartedNs = now;
        nextNotchMs = wave.patienceMs;
        timedNotches = 0;

        // A notch announced by the wave that is over has nothing to do with the one starting.
        biteDueNs = -1;

        pickHurrySays();

        // A level that replays an earlier table starts with the hurry up already biting, and that
        // is the whole difference between the two. Silently: the player has not dawdled yet, there
        // is nothing to announce to him.
        for( int i = 0; i < baseNotches; i++ )
            notch(null);
    }

    /**
     * One notch of hurry up: a wider front, one more creature out of the reserve, and the ground
     * a little tighter.
     *
     * It chooses nothing at all. The reserve is what the wave was written with, in the order it
     * was written -- and if there is a boss in it, the boss comes out. What has no business
     * falling on the player has no business being written in his reserve.
     *
     * The AppManager is given when the notch is to be announced, and left null when it is not: the
     * notches a level starts at are silent.
     */
    private void notch(AppManager am) {

        // Every notch the clock takes says so, in the order the wave drew: the four lines go by,
        // then round again. Only the notches a level STARTS at are silent -- the player has not
        // dawdled yet, there is nothing to tell him.
        boolean says = (am != null) && (timedNotches >= 1);

        if( !says ) {
            bite();
            return;
        }

        int what = hurrySays[(timedNotches - 1) % hurrySays.length];

        am.announceHurry(what);

        // The announcement first, the crowd after -- the one that answers the other. Both at once
        // and the player sees creatures appear while something shouts at him, with no way of
        // telling that the shout is about them.
        biteDueNs = System.nanoTime() + (am.getAnnounceDurationMs(what) * 1000000L);
    }

    /** What a notch actually does, once it is allowed to happen. */
    private void bite() {

        notches++;

        int index = reserveNext();

        int[] group = wave.reserve[index];

        // The group says itself whether the field grows with it: written with a plus, its own size
        // goes to the front, which is what opens the room it is about to walk into.
        if( wave.widens[index] ) {

            front += group.length;

            int ceiling = AppManager.getInstance().getLowResourceMode() ? FRONT_MAX_LOW : FRONT_MAX;

            if( front > ceiling )
                front = ceiling;
        }

        // And it only sends what there is ROOM for, right now. The queue is counted first, since
        // what is already waiting has the first claim on a free place -- a notch that finds the
        // field full therefore sends nobody at all, and what does not fit is DROPPED rather than
        // queued.
        //
        // That is the whole point: the queue can never hold more than the front can show. A
        // reserve that piled up behind a full field would be a wave that gets longer every ten
        // seconds without ever looking any harder, and the player would be killing a backlog.
        //
        // Same count as fillFront(), and it has to be: the two would otherwise disagree on what
        // "standing" means, and the reserve would send what the front then refuses.
        int room = front - gameSurface.chibiCount() - pending.size();

        // In the order written, so "k2ck3c" sends its Chucky first: with two places free, that is
        // one Chucky and one creeper, not two of whatever came last.
        for( int i = 0; (i < group.length) && (i < room); i++ )
            pending.add(Integer.valueOf(group[i]));
    }

    // The four lines of this wave, in the order they will be said, one per notch and round again
    // after the fourth.
    //
    // The pairs are not interchangeable: a notch of rank 1 or 3 warns or teases, a notch of rank 2
    // or 4 threatens -- "run!" has no business being the first thing the player is told. Inside a
    // pair it is a toggle and not a draw, and the second half of the wave says the other half of
    // each pair, so the four lines are all heard without one ever following itself.
    private void pickHurrySays() {

        int turn = hurryTurn & 1;

        hurryTurn++;

        hurrySays[0] = AppManager.ANNOUNCE_HURRY + turn;
        hurrySays[1] = AppManager.ANNOUNCE_COMING + turn;
        hurrySays[2] = AppManager.ANNOUNCE_HURRY + (turn ^ 1);
        hurrySays[3] = AppManager.ANNOUNCE_COMING + (turn ^ 1);
    }

    /**
     * The index of the reserve's next group. Past its end, its last group for ever.
     *
     * An index and not the group itself, because the group and the plus that goes with it live in
     * two arrays read at the same place: handing back only one of the two was how the bound got
     * lost on the other.
     */
    private int reserveNext() {

        int index = reserveAt;

        if( index >= wave.reserve.length )
            index = wave.reserve.length - 1;

        reserveAt++;

        return index;
    }

    private double delayCoef() {

        if( (level <= 0) || (notches <= 0) )
            return 1.0;

        int steps = Math.min(notches, STRESS_DELAY_MAX);

        double coef = 1.0 - (steps * DELAY_COEF_STEP);

        return Math.max(coef, DELAY_COEF_MIN);
    }

    private void hurryUp(long now, AppManager am) {

        long ageMs = (now - waveStartedNs) / 1000000;

        // Counted on the AGE of the wave rather than as "now plus ten seconds": a notch is never
        // served on the exact tick, and the slack would pile up from one notch to the next until
        // the hurrying no longer fell where it was meant to.
        if( ageMs < nextNotchMs )
            return;

        // The notch is an ACTION, and the one that announces itself has to be heard: if something
        // else is speaking, the whole notch waits for the next frame -- the front, the reserve and
        // the announcement together. Thirty seconds of patience becoming thirty-five is of no
        // consequence; a "hurry up!" nobody hears is.
        if( !mayAnnounce(am) )
            return;

        nextNotchMs += wave.stepMs;
        timedNotches++;

        notch(am);
    }

    /**
     * Whether the floor is free for the notch that is about to be taken, announcement included.
     *
     * Refused, the whole notch waits for the next frame rather than speaking over somebody: a few
     * seconds of delay on the pressure cost nothing, a line nobody hears costs the line.
     */
    private boolean mayAnnounce(AppManager am) {

        int next = timedNotches + 1;

        int what = hurrySays[(next - 1) % hurrySays.length];

        // The floor for what the banner SAYS, and not for its time on screen: it goes on fading
        // after the voice has stopped, and that is nobody's business but the drawing's.
        return gameSurface.mayStartAnnouncedAction(gameSurface.announceFloorMs(what));
    }

    /**
     * The progress bar: how far through the level, in waves.
     *
     * A wave's own slot fills over the time the wave was written for -- its patience -- so the bar
     * moves on its own while the player fights, and reaches the end of the slot at the exact
     * moment the hurry up starts biting. It goes no further than that: clearing the wave is the
     * only thing that opens the next slot, which is what the colour is there to say.
     *
     * Pushed rather than asked for, and only a few times a second: it is a bar of six pixels, not
     * an animation.
     */
    private void progress(AppManager am, long now) {

        if( (lastProgressNs > 0) && (((now - lastProgressNs) / 1000000) < PROGRESS_PERIOD_MS) )
            return;

        lastProgressNs = now;

        double fraction = 0.0;

        // Between two waves the next one has not started: its slot stays empty and the bar sits on
        // the boundary the player has just earned.
        if( (breatheUntilNs <= 0) && (wave != null) && (wave.patienceMs > 0) ) {

            long ageMs = (now - waveStartedNs) / 1000000;

            fraction = Math.min(1.0, ageMs / (double) wave.patienceMs);
        }

        am.setWaveState((float) ((waveNo - 1) + fraction), GameLevels.waveCount(level),
                        timedNotches);
    }

    /**
     * Keeps as many standing as the front allows, taking them in the order written.
     *
     * The front counts WHAT IS ON THE MAP, whatever put it there: this wave, the one before it, a
     * level walked out of by the corner shortcut, or the creeper that was roaming before the game
     * started. It is a ceiling on the field, not a quota for the wave -- counting only what this
     * wave had sent piled one level's front on top of the last one's, and a jump to level four
     * arrived on a map holding every front since the first.
     */
    private void fillFront() {

        while( (gameSurface.chibiCount() < front) && !pending.isEmpty() ) {

            int species = pending.remove(0);

            boolean own = (pendingOwn > 0);

            if( own )
                pendingOwn--;

            ChibiCharacter chibi = GameLevels.spawn(species, gameSurface);

            // One the wave called for is worth its species; one the hurry up sent is worth a
            // point, so that dawdling is never the best way to score.
            if( !own )
                chibi.setWorth(REINFORCEMENT_POINTS);

            sent.add(chibi);
        }
    }

    private void prune() {

        Iterator<ChibiCharacter> it = sent.iterator();

        while( it.hasNext() ) {

            if( !gameSurface.hasChibi(it.next()) )
                it.remove();
        }
    }

    private void waveCleared(long now, AppManager am) {

        // The last one: the level is over, and that is an action that speaks.
        if( waveNo >= GameLevels.waveCount(level) ) {
            levelUp(am, true);
            return;
        }

        waveNo++;

        am.setGameWave(waveNo);

        breatheUntilNs = now + (WAVE_BREATH_MS * 1000000L);
    }

    /**
     * The level cleared: the tune, the banner that names the next one, and the next one itself.
     *
     * All three at ONCE, which is the whole point of making it one action: the banner, the wave
     * bar going back to the start and the level line changing are the same piece of news. Refused
     * the floor, nothing happens at all -- not even the level change -- and it is asked again on
     * the next frame. The field is empty at that moment, so waiting costs nothing but a breath.
     *
     * Which tune depends on where he is going: the way the tables fall, an even level is one that
     * ends on the boss, and it is announced by its own.
     */
    /**
     * Up a level.
     *
     * CLEARED says whether it was earned -- the last wave emptied -- or asked for, by the double
     * tap of the corner. Only an earned one mends the player: a shortcut that handed out a
     * portion of heart per tap would be a way of healing, not a way of looking ahead.
     */
    private boolean levelUp(AppManager am, boolean cleared) {

        int next = level + 1;

        int what = ((next % 2) == 0) ? AppManager.ANNOUNCE_LEVEL_UP_BOSS
                                     : AppManager.ANNOUNCE_LEVEL_UP;

        if( !gameSurface.mayStartAnnouncedAction(gameSurface.announceFloorMs(what)) )
            return false;

        // Built here, in the update, because it is a composition and not a file -- and AFTER the
        // floor is granted, not before. Prepared too early it replaced the banner of the
        // announcement still on screen, which then finished its second under the next number.
        gameSurface.prepareLevelBanner(next);

        am.announceLevelUp(what);
        am.setGameLevel(next);

        // A level survived gives a portion back, and one that ended on a boss gives a whole life:
        // the fight itself has to mend a little, or a long game is a slow death whatever the
        // player does.
        if( cleared )
            healed(am, GameLevels.hasBoss(level) ? AppManager.GAME_HITS_PER_LIFE
                                                 : GameLevels.HEAL_LEVEL_PORTIONS);

        return true;
    }

    private void camera() {

        if( mainChibi != null )
            return;

        ChibiCharacter chibi = gameSurface.firstChibi();

        if( chibi == null )
            return;

        mainChibi = chibi;
        mainSinceNs = System.nanoTime();
    }

    // --- out of play ---------------------------------------------------------------------------

    /**
     * Level zero: the field empties of what has no business on it, and the creeper that is there
     * to be found is kept up to strength.
     */
    private void outOfPlay(long now, AppManager am) {

        clearField(now);

        // The count dies of its own accord: let the window pass and it is back to nothing.
        if( (egExpiresNs > 0) && (now >= egExpiresNs) ) {
            egSteps = 0;
            egExpiresNs = -1;
        }

        // Emptied means refilled, exactly as in a game -- and one refill is one step.
        //
        // The step is counted FIRST, and the field is filled from what the step has just become.
        // The other way round, the field was filled from the step before, so the one that starts
        // the game left its five creepers standing in a level that calls for one.
        if( gameSurface.chibiCount() != 0 )
            return;

        egStep(now, am);

        // The step may have started the game. The field then belongs to that level, which fills it
        // itself on the next frame.
        if( am.getGameLevel() <= 0 )
            strays();
    }

    /**
     * The creepers that roam outside the game: one, then three, then five as the discovery is
     * closed in on.
     *
     * They are worth NOTHING, and they belong to no wave. When a game starts they stay where they
     * are, they do not count towards the wave to be cleared, and killing one pays nothing -- not
     * the kill, not the hit, not the streak. Finding the easter egg is not a way of scoring. They
     * keep their teeth though: one that touches the player hurts him like any other.
     */
    private void strays() {

        int want = egCreepers();

        for( int i = gameSurface.chibiCount(ChibiCreeper.class); i < want; i++ ) {

            ChibiCharacter chibi = new ChibiCreeper(gameSurface);

            chibi.setWorth(STRAY_POINTS);
        }
    }

    /** How many creepers the field holds while the easter egg is being closed in on. */
    private int egCreepers() {

        if( egSteps >= EG_STEPS_5 )
            return 5;

        if( egSteps >= EG_STEPS_3 )
            return 3;

        return 1;
    }

    /**
     * One appearance more, straight after the last: one step towards the discovery.
     *
     * The last one starts the game, and says so in the preferences. How many that takes depends on
     * whether the egg has been found before: the whole climb the first time, a short one ever
     * after. Found once is found for good.
     */
    private void egStep(long now, AppManager am) {

        egSteps++;
        egExpiresNs = now + (EG_WINDOW_MS * 1000000L);

        int needed = am.isEasterEggFound(AppManager.EG_GAME) ? EG_STEPS_SPEED_GAME : EG_STEPS_GAME;

        if( egSteps < needed )
            return;

        egSteps = 0;
        egExpiresNs = -1;

        am.setEasterEggFound(AppManager.EG_GAME);
        am.setGameLevel(1);
    }

    /**
     * Blows up, one by one, the creatures that have no business outside a game.
     *
     * The ones whose level of appearance is above zero, and the CREEPERS IN EXCESS of what the
     * discovery calls for. A game that ended with twenty of them on the map used to leave the
     * twenty roaming, since a creeper belongs to level zero and nothing swept it; the field now
     * comes back down to the one, three or five that egCreepers() wants.
     *
     * The creeper itself cannot simply be given a MIN_LEVEL of one: this method would then blow up
     * the very one strays() has just put there for the discovery, over and over.
     */
    private void clearField(long now) {

        // First pass after the game ended: wait for the announcement to finish speaking, then a
        // second more.
        if( nextBlastNs < 0 ) {

            AppManager am = AppManager.getInstance();

            // Walked away from: nothing was said, so there is nothing to wait for.
            if( am.wasGameQuit() ) {
                nextBlastNs = now;
            }
            else {

                int res = am.getGameOverSoundRes();

                long parole = (res == 0) ? gameSurface.soundDurationMs(R.raw.eg_game_over)
                                         : gameSurface.soundDurationMs(res);

                nextBlastNs = now + (parole + CLEAR_AFTER_SOUND_MS) * 1000000L;
                return;
            }
        }

        if( now < nextBlastNs )
            return;

        int creepers = gameSurface.chibiCount(ChibiCreeper.class);
        int wanted = egCreepers();

        for( ChibiCharacter chibi : gameSurface.getChibis() ) {

            boolean outOfPlace = (chibi.getMinLevel() > 0);

            // The surplus creepers go too, one per pass like the rest, until the field holds
            // exactly what the discovery is asking for at this step.
            if( !outOfPlace && (chibi instanceof ChibiCreeper) && (creepers > wanted) ) {
                outOfPlace = true;
                creepers--;
            }

            if( !outOfPlace )
                continue;

            chibi.kill();

            // One per pass: the others follow, spaced out.
            nextBlastNs = now + CLEAR_STEP_MS * 1000000L;
            return;
        }
    }

    // --- the game's own clock ------------------------------------------------------------------

    // Frames come twenty-five a second. Anything longer than this was not a frame: it was the
    // game being away. A whole second leaves ordinary stutters alone.
    private static final long TIME_JUMP_MS = 1000;

    private long lastFrameNs = -1;

    // How long the last frame took, in milliseconds, and nil when the game had been away: the
    // gauges that fill with time are advanced by this, so they stop filling with the game.
    private long frameMs = 0;

    /**
     * The game's time stops with the game.
     *
     * The render thread dies with the surface -- switching to another application stops it --
     * while System.nanoTime() goes on counting. Coming back from half an hour away, every
     * deadline the game had set was half an hour overdue and they ALL fell due at once: the wave
     * took its hundred and twenty notches of hurrying one after the other, shouting for seven
     * minutes and emptying a reserve meant for half an hour onto the map; every pole had slid to
     * its floor price; and every creature crossed the map in a single step, on a delta of thirty
     * minutes.
     *
     * So the gap is measured and GIVEN BACK: every instant anything is counting towards moves on
     * by as much. Nothing is rushed, nothing is lost, and the player picks the game up where he
     * put it down. The same hand covers an ordinary stutter: two seconds of freeze must not cost
     * a notch either.
     */
    private void timeJump(long now, AppManager am) {

        long gap = (lastFrameNs > 0) ? (now - lastFrameNs) : 0;

        lastFrameNs = now;

        if( gap < (TIME_JUMP_MS * 1000000L) ) {
            frameMs = gap / 1000000;
            return;
        }

        frameMs = 0;

        GBLog.d(getClass().getSimpleName(), "time jumped by " + (gap / 1000000) + " ms");

        waveStartedNs = shifted(waveStartedNs, gap);
        breatheUntilNs = shifted(breatheUntilNs, gap);
        biteDueNs = shifted(biteDueNs, gap);
        nextBlastNs = shifted(nextBlastNs, gap);
        mainSinceNs = shifted(mainSinceNs, gap);
        egExpiresNs = shifted(egExpiresNs, gap);
        lastProgressNs = shifted(lastProgressNs, gap);
        lastTraceNs = shifted(lastTraceNs, gap);

        for( ChibiCharacter chibi : gameSurface.getChibis() )
            chibi.timeJumped(gap);

        List<PersonalMark> marks = am.getMarksEnumerable();

        for( int i = 0; i < marks.size(); i++ ) {

            PersonalMark mark = marks.get(i);

            if( (mark == null) || !mark.isGamePole() )
                continue;

            mark.setPoleDueNs(shifted(mark.getPoleDueNs(), gap));
            mark.setPoleDeadUntilNs(shifted(mark.getPoleDeadUntilNs(), gap));
        }
    }

    // An instant never set is not an instant to move.
    private static long shifted(long instantNs, long gapNs) {
        return (instantNs <= 0) ? instantNs : (instantNs + gapNs);
    }

    // --- tuning --------------------------------------------------------------------------------

    // How often the progress bar is handed its new value.
    private static final long PROGRESS_PERIOD_MS = 100;
    private long lastProgressNs = -1;

    // --- the poles -------------------------------------------------------------------------
    //
    // A game inside the game. The player plants marks himself -- three along a path make a slalom
    // -- and the ones whose icon says so are worth points to walk into. What they are worth is
    // the whole of the game: a pole is priced from how far off it stood when the level began, so
    // that reaching it at the pace of its level pays the same hundred wherever it was put, and
    // every second spent dawdling is taken off it.
    //
    // The numbers are all in GameLevels. What follows is only the clock and the contact.

    /**
     * Every pole takes its price and starts counting.
     *
     * At the start of a level, all of them at once, which is what makes a course a course: the
     * near one is cheap and the far one is dear, and the player sees both prices before choosing
     * where to run.
     */
    private void armPoles(int playing, long now, AppManager am) {

        List<PersonalMark> marks = am.getMarksEnumerable();

        for( int i = 0; i < marks.size(); i++ ) {

            PersonalMark mark = marks.get(i);

            if( (mark == null) || !mark.isGamePole() )
                continue;

            mark.setPoleScored(0);
            mark.setPoleDeadUntilNs(-1);
            mark.setPoleGoneAway(false);

            armPole(mark, playing, now, am);
        }
    }

    /**
     * One pole, priced from where the player stands now.
     *
     * Its whole countdown is held as a single instant: the moment it falls to what reaching it in
     * time is worth. Before that it is worth more, after it is worth less, and the value itself
     * never has to be stored.
     */
    private void armPole(PersonalMark mark, int playing, long now, AppManager am) {

        double meters = am.getKmFrom(mark.getLongitude(), mark.getLatitude()) * 1000.0;

        mark.getCollider().setHitRadiusMeters(GameLevels.poleTouchM(am.getAccuracy()));

        // Standing on it is not reaching it. A pole already at arm's length when the level
        // starts is out of play until it has been LEFT BEHIND, exactly like one just scored --
        // otherwise the game opens by paying a hundred points to somebody who has not moved.
        // Dead with no time left to serve: the distance is the whole of what it waits for.
        if( meters < GameLevels.POLE_AWAY_M ) {
            mark.setPoleValue(0);
            mark.setPoleDueNs(-1);
            mark.setPoleGoneAway(false);
            mark.setPoleDeadUntilNs(now);
            return;
        }

        // Too far to be in the game at all: it waits on the ring, showing nothing and counting
        // nothing, and comes in by itself the moment the player is near enough -- at which point
        // it is worth the ceiling, which is what the ring is.
        if( meters > GameLevels.poleEntryM(playing) ) {
            mark.setPoleValue(0);
            mark.setPoleDueNs(-1);
            mark.setPoleDeadUntilNs(-1);
            mark.setPoleGoneAway(false);
            return;
        }

        double speed = GameLevels.poleSpeedKmh(playing) / 3.6;

        long dueNs = now;

        if( speed > 0 )
            dueNs += (long) ((meters / speed) * 1000000000.0);

        mark.setPoleDueNs(dueNs);
        mark.setPoleDeadUntilNs(-1);

        mark.setPoleValue(poleWorth(mark, now));
    }

    /**
     * What a pole is worth at that instant.
     *
     * The reference hundred when its time is up, more while it still has some, less once it is
     * past -- and never more than the ceiling, however far off it stood, nor less than the floor,
     * however long one takes. Halved at every replay, so that circling the same three poles is a
     * way of scoring but not a way of scoring for ever.
     */
    private int poleWorth(PersonalMark mark, long now) {

        double left = (mark.getPoleDueNs() - now) / 1000000000.0;
        double worth = GameLevels.POLE_TARGET_POINTS + (left * GameLevels.POLE_DECAY_PER_S);

        if( worth > GameLevels.POLE_MAX_POINTS )
            worth = GameLevels.POLE_MAX_POINTS;

        for( int i = 0; i < mark.getPoleScored(); i++ )
            worth /= GameLevels.POLE_REPLAY_DIVIDER;

        // Down to the step below, never up: a pole pays what it showed, and showing more than it
        // pays would be a lie told at the only moment it matters.
        int paid = ((int) (worth / GameLevels.POLE_STEP_POINTS)) * GameLevels.POLE_STEP_POINTS;

        if( paid < GameLevels.POLE_MIN_POINTS )
            paid = GameLevels.POLE_MIN_POINTS;

        return paid;
    }

    /**
     * Out of the game: the marks go back to being marks.
     *
     * The poles lose their price and their body in metres, and the places that grow hearts or
     * weapons lose what they had grown -- a gauge is part of the game and has no business being
     * drawn over a map nobody is playing on. Whatever was lying on the ground goes with it.
     */
    private void closePoles(AppManager am) {

        gameSurface.clearHearts();
        gameSurface.clearItems();

        List<PersonalMark> marks = am.getMarksEnumerable();

        for( int i = 0; i < marks.size(); i++ ) {

            PersonalMark mark = marks.get(i);

            if( mark == null )
                continue;

            if( mark.isGameHeart() )
                mark.setHeartFill(0);

            if( mark.isGameItem() )
                mark.setItemFill(0);

            if( !mark.isGamePole() )
                continue;

            mark.setPoleValue(0);
            mark.setPoleDueNs(-1);
            mark.setPoleDeadUntilNs(-1);
            mark.setPoleGoneAway(false);
            mark.setPoleScored(0);
            mark.getCollider().setHitRadiusMeters(0);
        }
    }

    /** One step of the course: the prices come down, and what was walked through is collected. */
    private void poles(AppManager am, long now) {

        Chunk map = gameSurface.getMap();

        GPSFix fix = am.getLastFix();

        // A new fix, and the segment from the one before it: the ground walked over since. No new
        // fix means no new ground, so there is nothing to test -- the prices go on coming down
        // all the same.
        boolean walked = (fix != null) && (fix.getTimeMs() > 0)
                      && (lastFixMs > 0) && (fix.getTimeMs() != lastFixMs);

        double fromX = 0;
        double fromY = 0;
        double toX = 0;
        double toY = 0;

        if( walked && (map != null) ) {
            fromX = map.getX(lastFixLon, gameSurface.getAreaWidth());
            fromY = map.getY(lastFixLat, gameSurface.getAreaHeight());
            toX = map.getX(fix.getLongitude(), gameSurface.getAreaWidth());
            toY = map.getY(fix.getLatitude(), gameSurface.getAreaHeight());
        }

        segFixOn = walked && (map != null);
        segFixX0 = fromX;
        segFixY0 = fromY;
        segFixX1 = toX;
        segFixY1 = toY;

        if( (fix != null) && (fix.getTimeMs() > 0) && (fix.getTimeMs() != lastFixMs) ) {

            turnStrayM = walked ? strayOf(fix) : 0;

            if( lastFixMs > 0 ) {
                prevFixLat = lastFixLat;
                prevFixLon = lastFixLon;
                prevFixKnown = true;
            }

            lastFixLat = fix.getLatitude();
            lastFixLon = fix.getLongitude();
            lastFixMs = fix.getTimeMs();
        }

        double touch = GameLevels.poleTouchM(am.getAccuracy());

        // A turn widens it: what the chord cut off was walked just the same.
        if( turnStrayM > touch )
            touch = turnStrayM;

        touchM = touch;

        // Where the avatar is drawn now, and where it was on the frame before. Twenty-five of
        // these a second, so the two are never far enough apart to step over anything -- but it
        // is a segment all the same, and it costs nothing to be sure.
        MapGPSPos gps = gameSurface.getGps();

        double drawnX = (gps != null) ? gps.getAnchorOriginX() : 0;
        double drawnY = (gps != null) ? gps.getAnchorOriginY() : 0;

        boolean drawn = (gps != null) && lastDrawnKnown;

        double drawnFromX = drawn ? lastDrawnX : drawnX;
        double drawnFromY = drawn ? lastDrawnY : drawnY;

        if( gps != null ) {
            lastDrawnX = drawnX;
            lastDrawnY = drawnY;
            lastDrawnKnown = true;
        }

        segDrawOn = drawn;
        segDrawX0 = drawnFromX;
        segDrawY0 = drawnFromY;
        segDrawX1 = drawnX;
        segDrawY1 = drawnY;

        List<PersonalMark> marks = am.getMarksEnumerable();

        for( int i = 0; i < marks.size(); i++ ) {

            PersonalMark mark = marks.get(i);

            if( (mark == null) || !mark.isGamePole() )
                continue;

            if( mark.getPoleDeadUntilNs() > 0 ) {
                poleWaking(mark, now, am);
                continue;
            }

            // Waiting on the ring: it tries to come in, every frame, and does the moment the
            // player is close enough. Still nothing to collect on the frame it arrives -- it has
            // just been priced, and the player is a ring away from it.
            if( mark.getPoleDueNs() < 0 ) {
                armPole(mark, level, now, am);
                continue;
            }

            // Refreshed rather than settled at arming: the accuracy it follows is the accuracy of
            // the moment, and a fix that gets worse under a cliff must widen the pole while one
            // is walking at it, not at the next level.
            mark.getCollider().setHitRadiusMeters(touch);

            mark.setPoleValue(poleWorth(mark, now));

            // A mark of another chunk is not on this map, and its coordinates are not in these
            // pixels: it keeps its price and waits to be walked at.
            if( mark.getChunk() != map )
                continue;

            // Two ways in, and either will do: the ground walked between the last two fixes, or
            // the avatar's own path across the screen.
            boolean byFix = walked && mark.getCollider().crossedBy(fromX, fromY, toX, toY);

            boolean byDrawn = drawn && mark.getCollider().crossedBy(drawnFromX, drawnFromY,
                                                                    drawnX, drawnY);

            if( !byFix && !byDrawn )
                continue;

            polePaid(mark, now, am);
        }
    }

    /**
     * How far the last segment's chord strays from the corner that was really walked.
     *
     * Three fixes: the one before last, the last, and the one just in. The angle between the two
     * legs is the turn, and the chord of the second leg passes inside it. Worked out in metres on
     * the ground rather than in area pixels, because longitude is squeezed by the cosine of the
     * latitude and an angle measured on the map would not be the angle walked.
     */
    private double strayOf(GPSFix fix) {

        if( !prevFixKnown )
            return 0;

        double cos = Math.cos(Math.toRadians(lastFixLat));

        double inN = (lastFixLat - prevFixLat) * 111320.0;
        double inE = (lastFixLon - prevFixLon) * 111320.0 * cos;

        double outN = (fix.getLatitude() - lastFixLat) * 111320.0;
        double outE = (fix.getLongitude() - lastFixLon) * 111320.0 * cos;

        double chord = Math.hypot(outN, outE);

        // Standing still turns nothing: a leg of no length has no direction either.
        if( (chord <= 0) || (Math.hypot(inN, inE) <= 0) )
            return 0;

        double turn = Math.toDegrees(Math.atan2(outE, outN) - Math.atan2(inE, inN));

        turn = ((turn + 540) % 360) - 180;

        return GameLevels.poleTurnM(chord, Math.abs(turn));
    }

    /** A pole already collected: what it takes for it to be worth something again. */
    private void poleWaking(PersonalMark mark, long now, AppManager am) {

        // Away first, and the clock means nothing until it has happened: standing on a pole for
        // thirty seconds must not re-arm it under the player's feet.
        if( !mark.hasPoleGoneAway() ) {

            double meters = am.getKmFrom(mark.getLongitude(), mark.getLatitude()) * 1000.0;

            if( meters < GameLevels.POLE_AWAY_M )
                return;

            mark.setPoleGoneAway(true);
        }

        if( now < mark.getPoleDeadUntilNs() )
            return;

        mark.setPoleDeadUntilNs(-1);
        mark.setPoleGoneAway(false);

        armPole(mark, level, now, am);
    }

    /** Walked through: it pays what it was worth, and goes quiet. */
    private void polePaid(PersonalMark mark, long now, AppManager am) {

        int points = mark.getPoleValue();

        ScorePopup.scorePlain(gameSurface, mark.getAnchorOriginX(), mark.getAnchorOriginY(), points);

        // Once at a time: two poles taken on the same frame ring once, not as a chord.
        gameSurface.playSoundOnce(R.raw.eg_pole, POLE_VOLUME);

        // And it throws out a ring, as wide as it was dear, a breath after the coin has rung.
        // See GameLevels.poleBlastM and Shockwave.
        gameSurface.addShockwave(new Shockwave(gameSurface,
                                               mark.getAnchorOriginX(),
                                               mark.getAnchorOriginY(),
                                               GameLevels.poleBlastM(points),
                                               GameLevels.POLE_BLAST_DAMAGE));

        mark.setPoleValue(0);
        mark.setPoleDueNs(-1);
        mark.setPoleScored(mark.getPoleScored() + 1);
        mark.setPoleDeadUntilNs(now + (GameLevels.POLE_DEAD_S * 1000000000L));
        mark.setPoleGoneAway(false);

        // And it buys time. The wave's patience starts again from here, which is what makes a
        // slalom worth running in the middle of a fight rather than a detour one pays for.
        //
        // The CLOCK only: the notches already taken stay taken -- what they let onto the map is
        // on the map, and the progress bar goes on saying how long this wave has been held up.
        if( wave != null ) {
            waveStartedNs = now;
            nextNotchMs = wave.patienceMs;
        }

        // And the finger gets its wind back whole. A slalom is a way of fighting, not a pause
        // taken from the fighting.
        am.refillEnergy();

        // Put aside towards a portion of heart -- the reference part of the price only, so that
        // the same three posts mend the same amount whether they are walked or ridden.
        healPoints += GameLevels.healValueOf(points);

        while( healPoints >= GameLevels.HEAL_POLE_POINTS ) {
            healPoints -= GameLevels.HEAL_POLE_POINTS;
            healed(am, 1);
        }

        poleCourseRepriced(mark, now, am);
    }

    /**
     * One pole taken, and the whole course is priced again.
     *
     * Every other pole is offered a fresh price -- what its distance from HERE is worth, so that
     * reaching it at the pace of the level pays the hundred -- and it keeps whichever of the two
     * prices is the better. A player already moving faster than the level asks for has made his
     * own price on the way, and a reset has no business taking it back off him.
     *
     * A price only ever falls with time, so the LATER deadline IS the better price: the two are
     * compared on that, and nothing has to be worked out twice.
     */
    private void poleCourseRepriced(PersonalMark touched, long now, AppManager am) {

        double speed = GameLevels.poleSpeedKmh(level) / 3.6;

        if( speed <= 0 )
            return;

        List<PersonalMark> marks = am.getMarksEnumerable();

        for( int i = 0; i < marks.size(); i++ ) {

            PersonalMark mark = marks.get(i);

            if( (mark == null) || (mark == touched) || !mark.isGamePole() )
                continue;

            // Out of play -- just scored, or still waiting to be left behind -- stays out of play.
            if( (mark.getPoleDeadUntilNs() > 0) || (mark.getPoleDueNs() < 0) )
                continue;

            double meters = am.getKmFrom(mark.getLongitude(), mark.getLatitude()) * 1000.0;

            long fresh = now + (long) ((meters / speed) * 1000000000.0);

            if( fresh > mark.getPoleDueNs() )
                mark.setPoleDueNs(fresh);

            mark.setPoleValue(poleWorth(mark, now));
        }
    }

    // --- the hearts ------------------------------------------------------------------------
    //
    // The other half of the game inside the game. Certain places grow hearts on their own slow
    // clock; taking such a place as the MAIN MARK once its gauge is full makes it drop them in
    // the field around it, and walking over one gives back a portion of heart.
    //
    // The numbers are all in GameLevels. What follows is the clock, the throw and the contact.

    // Where the hearts land. Not kept between games: nothing here is worth a seed.
    private final java.util.Random throwAt = new java.util.Random();

    /** One step of the garden: the gauges fill, the full ones are harvested, the ground is swept. */
    private void hearts(AppManager am, long now) {

        PersonalMark main = am.getShownMark();

        List<PersonalMark> marks = am.getMarksEnumerable();

        for( int i = 0; i < marks.size(); i++ ) {

            PersonalMark mark = marks.get(i);

            if( (mark == null) || !mark.isGameHeart() )
                continue;

            long full = GameLevels.growMs(mark.getHeartCount());

            if( full <= 0 )
                continue;

            // Out of reach: the gauge is PAUSED and keeps what it has grown. A church half a
            // kilometre off has no business ripening while nobody is anywhere near it, and
            // emptying it instead would punish a walk that merely went the other way first.
            if( tooFar(am, mark) )
                continue;

            long grown = (long) (mark.getHeartFill() * full);

            // Full and being steered to: it gives what it has grown, and starts again from
            // nothing. Choosing it does NOT hold its clock -- a place goes on growing whether one
            // is walking at it or not, and it was odd to watch a gauge freeze for having been
            // looked at.
            if( (grown >= full) && (mark == main) ) {
                drop(mark, am);
                continue;
            }

            if( grown >= full )
                continue;

            grown += frameMs;

            mark.setHeartFill((float) grown / full);
        }

        pickUp(am);
    }

    /**
     * A place harvested: it throws what it grew into the field around it.
     *
     * Thrown rather than left at its foot, because a heart on the mark itself would be taken by
     * the very walk that harvested it. They land anywhere within the throw, at a distance that
     * favours the far side -- a square root of the draw, which spreads them evenly over the
     * ground rather than crowding them in the middle.
     */
    private void drop(PersonalMark mark, AppManager am) {

        int count = mark.getHeartCount();

        mark.setHeartFill(0);

        double cos = Math.cos(Math.toRadians(mark.getLatitude()));

        if( Math.abs(cos) < 1e-6 )
            cos = 1e-6;

        for( int i = 0; i < count; i++ ) {

            double bearing = throwAt.nextDouble() * 2 * Math.PI;

            // Anywhere inside the throw, and never past it -- the square root of the draw spreads
            // them evenly over the ground instead of crowding them near the mark.
            double meters = GameLevels.HEART_THROW_M * Math.sqrt(throwAt.nextDouble());

            double lat = mark.getLatitude() + ((meters * Math.cos(bearing)) / 111320.0);
            double lon = mark.getLongitude() + ((meters * Math.sin(bearing)) / (111320.0 * cos));

            gameSurface.addHeart(new GameHeart(gameSurface,
                                               mark.getLongitude(), mark.getLatitude(),
                                               lon, lat));

            // One pop each, a tone higher every time: three hearts are heard as three.
            gameSurface.playSound(R.raw.eg_heart_pop, HEART_POP_VOLUME,
                                  1f + (HEART_POP_STEP * i));
        }

        GBLog.d(getClass().getSimpleName(), "mark dropped " + count + " heart(s)");
    }

    // What a heart sounds like as it lands, and how much higher each one of a batch is read.
    private static final float HEART_POP_VOLUME = 0.8f;
    private static final float HEART_POP_STEP = 0.14f;

    /**
     * Everything the player is standing against.
     *
     * Read from his own collider rather than measured here: a heart is a body like any other on
     * this map, and what touches what is the collider's question. HURTS is his damage zone
     * against their hit zone, which is the pair this wants -- the same test a tomato makes on a
     * creature, and nothing invented for the occasion.
     */
    private void pickUp(AppManager am) {

        MapGPSPos gps = gameSurface.getGps();

        if( gps == null )
            return;

        List<GameObject> against = gps.getCollider().getHurts();

        if( against == null )
            return;

        for( int i = 0; i < against.size(); i++ ) {

            GameObject object = against.get(i);

            if( !(object instanceof GameHeart) )
                continue;

            GameHeart heart = (GameHeart) object;

            // In the air it is nobody's yet.
            if( !heart.isPlaced() )
                continue;

            gameSurface.removeHeart(heart);

            healed(am, 1);
        }
    }

    /**
     * Gives portions back, and says so.
     *
     * Says so ONLY when something was actually given: a player already whole walks over a heart
     * and nothing happens -- no leap, no sound -- which is how he learns that he did not need it.
     */
    private void healed(AppManager am, int portions) {

        if( am.healPlayer(portions) <= 0 )
            return;

        MapGPSPos gps = gameSurface.getGps();

        if( gps != null )
            gameSurface.addHealSign(new HealSign(gameSurface,
                                                 gps.getAnchorOriginX(),
                                                 gps.getAnchorOriginY()));

        gameSurface.playSoundOnce(R.raw.eg_heal, HEAL_VOLUME);
    }

    private static final float HEAL_VOLUME = 0.95f;

    // Out of play: a mark further than the entry distance neither grows anything nor gives it.
    private boolean tooFar(AppManager am, PersonalMark mark) {

        double meters = am.getKmFrom(mark.getLongitude(), mark.getLatitude()) * 1000.0;

        return meters > GameLevels.ITEM_ENTRY_M;
    }

    // --- the items ---------------------------------------------------------------------------
    //
    // The same clock as the hearts, over four other places and with the opposite purpose: what
    // grows here is a weapon, and taking such a place as the MAIN MARK once its gauge is full
    // makes it throw what it grew out towards the player.
    //
    // Thrown and not dropped. A heart is left in the field around the mark because the player is
    // going there anyway; a weapon has to be where the creatures are, which is where he stands.

    /** One step of the armoury: the gauges fill, and the full ones throw. */
    private void items(AppManager am, long now) {

        PersonalMark main = am.getShownMark();

        List<PersonalMark> marks = am.getMarksEnumerable();

        for( int i = 0; i < marks.size(); i++ ) {

            PersonalMark mark = marks.get(i);

            if( mark == null )
                continue;

            GameLevels.Item kind = mark.getItemKind();

            if( (kind == null) || (kind.chargeMs <= 0) )
                continue;

            if( tooFar(am, mark) )
                continue;

            long full = kind.chargeMs;
            long grown = (long) (mark.getItemFill() * full);

            // Ready and being steered to: it throws, and starts again from nothing. Choosing it
            // does not hold its clock, exactly as a place that grows hearts goes on growing them
            // whether one is walking at it or not.
            if( (grown >= full) && (mark == main) ) {
                throwItem(mark, kind, am);
                continue;
            }

            if( grown >= full )
                continue;

            grown += frameMs;

            mark.setItemFill((float) grown / full);
        }
    }

    /**
     * A place that has grown a weapon throws it.
     *
     * It lands near the PLAYER and not near the mark, in the direction the mark lies in, give or
     * take a few degrees. Near him because that is where the creatures are heading and a trap
     * laid anywhere else is a trap nothing will ever walk into; never AT him, because a mine
     * under one's own feet is a joke that stops being funny the first time.
     *
     * The flight itself belongs to the bullet: it is given where to start, which way to go and
     * how far, and whatever it runs into on the way is its business and not this one's.
     */
    private void throwItem(PersonalMark mark, GameLevels.Item kind, AppManager am) {

        mark.setItemFill(0);

        Chunk map = gameSurface.getMap();

        if( map == null )
            return;

        double playerLat = am.getLatitude();
        double playerLon = am.getLongitude();

        double cos = Math.cos(Math.toRadians(playerLat));

        if( Math.abs(cos) < 1e-6 )
            cos = 1e-6;

        // Which way the mark lies, from where he stands. In metres on the ground rather than in
        // degrees, or a longitude would weigh more than a latitude and the bearing would be wrong
        // everywhere but the equator.
        double east = (mark.getLongitude() - playerLon) * cos * 111320.0;
        double north = (mark.getLatitude() - playerLat) * 111320.0;

        double bearing = Math.atan2(east, north);

        bearing += Math.toRadians(((throwAt.nextDouble() * 2) - 1) * GameLevels.ITEM_DROP_SPREAD_DEG);

        double meters = GameLevels.ITEM_DROP_MIN_M
                        + (throwAt.nextDouble()
                           * (GameLevels.ITEM_DROP_MAX_M - GameLevels.ITEM_DROP_MIN_M));

        double landLat = playerLat + ((meters * Math.cos(bearing)) / 111320.0);
        double landLon = playerLon + ((meters * Math.sin(bearing)) / (111320.0 * cos));

        double x0 = map.getX(mark.getLongitude(), gameSurface.getAreaWidth());
        double y0 = map.getY(mark.getLatitude(), gameSurface.getAreaHeight());

        double x1 = map.getX(landLon, gameSurface.getAreaWidth());
        double y1 = map.getY(landLat, gameSurface.getAreaHeight());

        // A heading from north, which is what everything in this application means by an angle,
        // and north is up, which is why the Y component is the one that is negated.
        double angleDeg = Math.toDegrees(Math.atan2(x1 - x0, -(y1 - y0)));

        double pxPerM = gameSurface.pixelsPerMeter();

        double rangeM = (pxPerM > 0) ? (Math.hypot(x1 - x0, y1 - y0) / pxPerM) : 0;

        gameSurface.addBullet(new ItemBullet(gameSurface, kind, mark,
                                             x0, y0, angleDeg, rangeM, landLon, landLat));

        GBLog.d(getClass().getSimpleName(),
                String.format(Locale.US, "mark threw an item over %.0f m", rangeM));
    }

    // Temporary, while the waves are being tuned: once a second, where the level is and what is
    // coming. Debug mode only.
    private static final long TRACE_PERIOD_MS = 1000;
    private long lastTraceNs = -1;

    private void trace() {

        AppManager am = AppManager.getInstance();

        if( !am.getDebugMode() )
            return;

        long now = System.nanoTime();

        if( (lastTraceNs > 0) && (((now - lastTraceNs) / 1000000) < TRACE_PERIOD_MS) )
            return;

        lastTraceNs = now;

        long ageMs = (waveStartedNs > 0) ? ((now - waveStartedNs) / 1000000) : 0;

        GBLog.d(getClass().getSimpleName(),
                String.format(Locale.US,
                              "level %d | wave %d | age %d s | notch %d (next at %d s) | front %d"
                              + " | standing %d | to send %d (own %d) | delay x%.2f | chibis %d",
                              level, waveNo, ageMs / 1000, notches, nextNotchMs / 1000, front,
                              sent.size(), pending.size(), pendingOwn, delayCoef(),
                              gameSurface.chibiCount()));
    }
}
