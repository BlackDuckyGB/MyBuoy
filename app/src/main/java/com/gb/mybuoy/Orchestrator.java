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

/**
 * The one that decides what happens: who appears, when, and who the game is watching.
 *
 * Everything about the RULES of the game belongs here rather than in the creatures themselves. A
 * creature knows how to walk, to be hit and to come back; it has no business knowing how many of
 * its kind the level calls for. That question is asked once per frame, in one place, by this.
 *
 * It also holds the MAIN creature, the one the play area is drawn for. There are as many play
 * areas as there are creatures -- each is measured with its owner's own speed, so a Chucky at
 * 11 km/h has a wider one than a creeper at 8 -- but drawing them all would turn the map into a
 * pile of circles. So the game watches one at a time, the way a spectator camera cuts to whoever
 * just did something, and stays on it long enough to be followed.
 *
 * Drawn nothing for now: draw() is there so that the day the game has something of its own to show
 * -- a wave counter, a boss timer -- it has a place to put it, and so that GameSurface calls it in
 * the right order from the first day.
 */
public class Orchestrator {

    // How long the camera stays on a creature before another may take it, in milliseconds. Short
    // enough that a new arrival is not ignored, long enough that three creatures appearing at once
    // do not make the shape flicker between them.
    private static final long MAIN_HOLD_MS = 5000;

    private final GameSurface gameSurface;

    // The creature the play area is drawn for, and when it took the camera.
    private ChibiCharacter mainChibi = null;
    private long mainSinceNs = -1;

    // Since when the level has been running, and which level that is. A level is a fresh start:
    // the clock below is what the hurrying is measured against.
    private int startedLevel = -1;
    private long levelStartedNs = -1;

    // When the next reinforcement is due. Emptying the map repopulates it at once, but a player
    // who holds out gets company anyway: the first wave after a MINUTE, the next a minute later,
    // and one every thirty seconds after that. The game stops waiting to be finished.
    private static final long HURRY_FIRST_MS = 60000;
    private static final long HURRY_SECOND_MS = 60000;
    private static final long HURRY_THEN_MS = 30000;

    // The next wave, as an AGE of the level and not as a moment: the ladder in populate() is read
    // on that same age, so the two cannot drift apart. Counting from "now plus thirty seconds"
    // slid a little further every time -- a wave is never served on the exact tick -- and after a
    // few of them the wave and the rung it was meant to serve no longer fell together.
    private long nextWaveMs = -1;
    private int waveCount = 0;

    // Game over: the field empties, but not at once and not all together.
    //
    // The announcement is left to finish speaking -- its length is read from the file, so
    // re-recording the "game over", or the doll's "you are dead", is all it takes -- then a second
    // of silence, and the creatures go off one by one. Blowing them all up on the same frame would
    // make one shapeless bang; spaced out, they can be counted.
    private static final long CLEAR_AFTER_SOUND_MS = 1000;
    private static final long CLEAR_STEP_MS = 220;

    private long nextBlastNs = -1;

    // --- finding the easter egg ----------------------------------------------------------------
    //
    // The game does not announce itself: it is found by blowing up creepers one straight after
    // another. Each APPEARANCE counts for one step -- three creepers coming out together is one
    // event, not three, so the ten steps are ten the player has to make -- and the whole count
    // falls back to nothing as soon as more than EG_WINDOW_MS goes by without one.
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

    public Orchestrator(GameSurface gameSurface) {
        this.gameSurface = gameSurface;
    }

    /** The creature the game is watching: the only one whose play area is drawn. */
    public ChibiCharacter getMainChibi() {
        return mainChibi;
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
     * One frame of the game's own business: keeping the map populated, and keeping the camera on
     * someone who still exists.
     */
    public void update() {

        AppManager am = AppManager.getInstance();

        int level = am.getGameLevel();

        // The camera first: the creature it was on may have been blown up since the last frame,
        // and a dead creature must not keep it -- nothing would ever be drawn again.
        if( (mainChibi != null) && !gameSurface.hasChibi(mainChibi) ) {
            mainChibi = null;
            mainSinceNs = -1;
        }

        long now = System.nanoTime();

        // A new level restarts everything: the clock, and the hurrying that hangs off it.
        if( level != startedLevel ) {
            startedLevel = level;
            levelStartedNs = now;
            nextWaveMs = HURRY_FIRST_MS;
            waveCount = 0;
        }

        long levelAgeMs = (now - levelStartedNs) / 1000000;

        // Out of play: the field empties of what has no business on it, and the roaming creeper
        // is brought back up to strength -- it is by blowing one up that the easter egg is found,
        // so there has to be one. populate() tops up by headcount, so level zero can only ever
        // call for that one creeper, however often this is asked.
        if( level <= 0 ) {

            clearField(now);

            // The count dies of its own accord: let the window pass and it is back to nothing.
            if( (egExpiresNs > 0) && (now >= egExpiresNs) ) {
                egSteps = 0;
                egExpiresNs = -1;
            }

            // Emptied means refilled, exactly as in a game -- and one refill is one step.
            //
            // The step is counted FIRST, and the field is filled from what the step has just
            // become. The other way round, the field was filled from the step before, so the one
            // that starts the game left its five creepers standing in a level that calls for one.
            if( gameSurface.chibiCount() == 0 ) {

                egStep(now);

                // The step may have started the game. The field then belongs to that level, which
                // fills it itself on the next frame.
                if( am.getGameLevel() <= 0 )
                    populate(level, levelAgeMs);
            }

            return;
        }

        nextBlastNs = -1;

        trace(level, levelAgeMs);

        // Then the population. Emptied means repopulated on the next frame, whatever emptied it --
        // a tomato, a tap, a creature walking off the edge of the world. The rule is the level's,
        // not the creature's: a creature blown up does not decide what replaces it.
        if( gameSurface.chibiCount() == 0 ) {
            populate(level, levelAgeMs);
        }
        else if( levelAgeMs >= nextWaveMs ) {

            // Holding out is not a way of waiting it out: what the level calls for at this age is
            // brought back up to strength, whatever is already standing.
            populate(level, levelAgeMs);

            waveCount++;
            nextWaveMs += (waveCount == 1) ? HURRY_SECOND_MS : HURRY_THEN_MS;
        }

        // Nobody being watched, and creatures on the map: the camera falls on one of them at once
        // rather than waiting for the next arrival. Blowing up the creature it was on must not
        // leave the screen without a shape until something happens to appear.
        if( mainChibi == null ) {

            ChibiCharacter chibi = gameSurface.firstChibi();

            if( chibi != null ) {
                mainChibi = chibi;
                mainSinceNs = System.nanoTime();
            }
        }
    }

    /**
     * Who appears on an empty map, and how many, for a given level.
     *
     * The whole table of the game so far, in one place. Level 0 is not "no game" here: the easter
     * egg is FOUND by blowing up creepers, so there has to be one to blow up -- and a creeper is
     * the only kind that ever appears outside a game. Above the last line written here, the last
     * line is what applies, so raising the level never empties the map.
     */
    private void populate(int level, long levelAgeMs) {

        int creepers;
        int chuckys;
        int ghosts;
        int younghees;

        if( level >= ChibiYoungHee.MIN_LEVEL ) {

            // The boss arrives alone -- the level IS her -- but only for as long as the player is
            // quick about it. Hold out and the crowd comes back around her, thicker as it goes.
            younghees = 1;

            if( levelAgeMs >= 240000 ) {
                creepers = 2;
                chuckys = 2;
                ghosts = 1;
            } else if( levelAgeMs >= 180000 ) {
                creepers = 3;
                chuckys = 2;
                ghosts = 0;
            } else if( levelAgeMs >= 90000 ) {
                creepers = 3;
                chuckys = 1;
                ghosts = 0;
            } else if( levelAgeMs >= 60000 ) {
                creepers = 1;
                chuckys = 0;
                ghosts = 0;
            } else {
                creepers = 0;
                chuckys = 0;
                ghosts = 0;
            }

        } else if( level >= ChibiGhost.MIN_LEVEL ) {
            creepers = 5;
            chuckys = 3;
            ghosts = 1;
            younghees = 0;
        } else if( level >= ChibiChucky.MIN_LEVEL ) {
            creepers = 2;
            chuckys = 1;
            ghosts = 0;
            younghees = 0;
        } else {
            creepers = egCreepers();
            chuckys = 0;
            ghosts = 0;
            younghees = 0;
        }

        // What the level calls for is a HEADCOUNT, not a delivery: only what is missing is made.
        // That is what lets this be called over and over -- on an empty map, or on a crowded one
        // that the hurrying has come to reinforce -- without the map filling up with copies.
        for( int i = gameSurface.chibiCount(ChibiCreeper.class); i < creepers; i++ )
            new ChibiCreeper(gameSurface);

        for( int i = gameSurface.chibiCount(ChibiChucky.class); i < chuckys; i++ )
            new ChibiChucky(gameSurface);

        for( int i = gameSurface.chibiCount(ChibiGhost.class); i < ghosts; i++ )
            new ChibiGhost(gameSurface);

        for( int i = gameSurface.chibiCount(ChibiYoungHee.class); i < younghees; i++ )
            new ChibiYoungHee(gameSurface);
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
    private void egStep(long now) {

        egSteps++;
        egExpiresNs = now + (EG_WINDOW_MS * 1000000L);

        AppManager am = AppManager.getInstance();

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
     * The ones whose level of appearance is above zero. The creeper stays: it is by blowing one up
     * that the easter egg is found, and it has roamed the map outside the game since the first day.
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

        for( ChibiCharacter chibi : gameSurface.getChibis() ) {

            if( chibi.getMinLevel() <= 0 )
                continue;

            chibi.kill();

            // One per pass: the others follow, spaced out.
            nextBlastNs = now + CLEAR_STEP_MS * 1000000L;
            return;
        }
    }

    // Temporary, while the waves are being tuned: once a second, the level, its age, who is on
    // the map and when the next wave is due. Debug mode only.
    private static final long TRACE_PERIOD_MS = 1000;
    private long lastTraceNs = -1;

    private void trace(int level, long levelAgeMs) {

        AppManager am = AppManager.getInstance();

        if( !am.getDebugMode() )
            return;

        long now = System.nanoTime();

        if( (lastTraceNs > 0) && (((now - lastTraceNs) / 1000000) < TRACE_PERIOD_MS) )
            return;

        lastTraceNs = now;

        GBLog.d(getClass().getSimpleName(),
                String.format(java.util.Locale.US,
                              "level %d | age %d s | wave %d due at %d s | chibis %d (creeper %d, chucky %d, ghost %d, younghee %d)",
                              level, levelAgeMs / 1000, waveCount, nextWaveMs / 1000,
                              gameSurface.chibiCount(),
                              gameSurface.chibiCount(ChibiCreeper.class),
                              gameSurface.chibiCount(ChibiChucky.class),
                              gameSurface.chibiCount(ChibiGhost.class),
                              gameSurface.chibiCount(ChibiYoungHee.class)));
    }

    /** Nothing of its own to draw yet. Same signature as everything else that draws itself. */
    public void draw(Canvas canvas, int layer) {
    }
}
