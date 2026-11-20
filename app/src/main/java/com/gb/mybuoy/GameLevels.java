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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * THE GAME, ON ONE PAGE. Everything a level is made of is written here, and nowhere else.
 *
 * A level is TEN waves. A wave is an amount to kill, not a delay: it is over when everything it
 * had to send is dead. Clearing the tenth moves up a level. One line of text is one wave:
 *
 *     "5c 2k /3 @25/8 +c c k"
 *      |     |   |   |
 *      |     |   |   +-- the RESERVE: what the hurry up adds, ONE GROUP PER NOTCH, in order.
 *      |     |   |       A group may mix species, written without a space: "2c1k" is two
 *      |     |   |       creepers and a Chucky on the same notch. Runs out? The LAST GROUP
 *      |     |   |       repeats for ever -- "2g" at the end means two ghosts a notch until
 *      |     |   |       the player falls.
 *      |     |   |
 *      |     |   |       A group WITH a plus widens the front by its own size, which opens
 *      |     |   |       the room it then walks into; without one it only fills the room
 *      |     |   |       there already is. "+3c +1c2k 3c +5c g" is five notches, of which the
 *      |     |   |       first, second and fourth make the field wider. A group never queues
 *      |     |   |       behind a full field: what does not fit is dropped, in the order
 *      |     |   |       written. The first plus is also what OPENS the reserve, so a reserve
 *      |     |   |       whose first group must not widen anything opens on a plus of its
 *      |     |   |       own: "+ 3c +2k".
 *      |     |   +------ the PATIENCE, in seconds, and after the slash the time between the
 *      |     |           notches that follow: twenty-five seconds of grace, then a notch every
 *      |     |           eight. The step may be left out, and the notches then come every
 *      |     |           DEFAULT_STEP_S.
 *      |     +---------- the FRONT: how many stand on the map at once. The rest wait their turn,
 *      |                 and each death lets the next one in. The front, not the headcount, is
 *      |                 what makes a wave hard rather than merely long.
 *      +---------------- WHAT it sends in all, in the order written: 5 creepers and 2 Chuckys.
 *
 * Species letters: c creeper, k Chucky, g ghost, Y Young-Hee. A CAPITAL is a boss -- which is
 * the only thing a boss is, here: a wave whose roster happens to be "1Y".
 *
 * Two shorthands keep a level short:
 *
 *   - beyond the last line written, THE LAST LINE APPLIES. A level that is three lines long is
 *     still ten waves long.
 *   - a line may be PINNED to its rank with "10:", and the line above it fills the gap by
 *     repeating. That is how a boss closes a level without writing out the eight waves before it.
 *
 * Everything left out takes its default: a front of one, {@link #DEFAULT_PATIENCE_S} of patience,
 * and a reserve of one creature of the roster's first species, every notch.
 *
 * The table is read ONCE, when this class is first touched. A mistake in a line costs THAT LINE:
 * it is dropped and written to the log as an error, and the rest of the level plays. It used to
 * throw, which killed the application the moment a game started -- fine for a table one writes and
 * immediately plays, not for a version in somebody else's hands.
 */
public class GameLevels {

    private static final String TAG = "GameLevels";

    // How many waves a level is made of is the LEVEL's business: as many as it has lines, or as
    // far as its last pinned one reaches. Ten is only what the levels written so far happen to be,
    // and the progress bar draws whatever number it is handed.
    //
    // The cap is a guard against a typo: "100:" on a line would otherwise ask for a hundred waves.
    private static final int WAVES_MAX = 99;

    /** Seconds of patience a wave gets when it does not ask for its own. */
    public static final int DEFAULT_PATIENCE_S = 25;

    /** Seconds between two notches of hurry up, for a wave that does not ask for its own. */
    public static final int DEFAULT_STEP_S = 15;

    // The species, and the letter each one is written with.
    public static final int CREEPER  = 0;
    public static final int CHUCKY   = 1;
    public static final int GHOST    = 2;
    public static final int YOUNGHEE = 3;

    private static final String LETTERS = "ckgY";

    // --- the poles -----------------------------------------------------------------------------
    //
    // A game inside the game. A personal mark whose icon is one of these is a POLE: walk into it
    // and it pays. Three of them planted along a path make a slalom, and the whole of it is the
    // player's to lay out -- the game brings the clock, he brings the course.
    //
    // Written as the flat icon index a mark carries, sheet * 36 + row * 6 + col. The contact sheet
    // of all of them, each with its number, is in EasterEgg/MarkIcons_ids.png.
    private static final int[] POLE_ICONS = { 0, 2, 8, 9, 10, 25, 27, 28, 29, 41, 60, 61, 62,
                                              63, 67, 68, 69, 70, 72, 73, 74, 81, 85 };

    // How close one has to pass for a pole to count as touched, in metres. The circle is on the
    // pole, and since a contact is judged on the DISTANCE from that circle's centre to the ground
    // walked over, this one number is also the half-thickness of the path that counts.
    //
    // It follows the accuracy the fix reports, between these two: two metres is as tight as the
    // game ever gets, five is as wide as it ever opens, whatever the satellites claim. A mark was
    // PLACED with an error of its own, and one walks past it with another, so the real gap between
    // a pole and the path can be about twice the figure announced -- which is why the floor is not
    // the whole story and the cap is not mean.
    public static final double POLE_TOUCH_MIN_M = 2.0;
    public static final double POLE_TOUCH_MAX_M = 5.0;

    // And it opens further still on a TURN, by the amount the straight line between two fixes
    // cuts off the corner actually walked. A position arrives once a second: go round a pole
    // between two of them and the segment joining them passes inside the curve, by
    //
    //     (distance between the fixes / 2) * tan(angle of the turn / 4)
    //
    // -- a third of a metre at walking pace, but one and three quarters at thirty an hour, and
    // four on a half-turn. Dead straight the angle is nil and nothing is added.
    //
    // Its own ceiling, above the accuracy one: this is geometry that was really walked, not
    // uncertainty about where one is.
    public static final double POLE_TURN_MAX_M = 8.0;

    /** How far a chord strays from the corner it cuts, in metres. */
    public static double poleTurnM(double chordM, double turnDeg) {

        if( (chordM <= 0) || (turnDeg <= 0) )
            return 0;

        if( turnDeg > 180 )
            turnDeg = 180;

        double sag = (chordM / 2.0) * Math.tan(Math.toRadians(turnDeg / 4.0));

        return (sag > POLE_TURN_MAX_M) ? POLE_TURN_MAX_M : sag;
    }

    /** The radius a pole takes for an announced accuracy, in metres. */
    public static double poleTouchM(double accuracyM) {

        if( accuracyM < POLE_TOUCH_MIN_M )
            return POLE_TOUCH_MIN_M;

        if( accuracyM > POLE_TOUCH_MAX_M )
            return POLE_TOUCH_MAX_M;

        return accuracyM;
    }

    /** What a pole pays when it is reached at exactly the reference speed of its level. */
    public static final int POLE_TARGET_POINTS = 100;

    // No pole is ever worth more than this -- and it is not a price trimmed down to fit, it is a
    // RING on the ground. A pole comes into play when the player is close enough for it to be
    // worth this much, and not before: its countdown starts there, at exactly this price, and
    // falls from there. Beyond the ring it shows nothing and counts nothing.
    //
    // Trimming instead -- which is what this used to do -- made every distant pole pay the same:
    // at a pace well above the level's, the price had not come down enough by the time one
    // arrived, so four hundred metres and eight hundred were worth the very same five hundred.
    public static final int POLE_MAX_POINTS = 500;

    /** And however long one dawdles, none ever falls below this. */
    public static final int POLE_MIN_POINTS = 5;

    // A price comes down in steps of five and never one point at a time: a figure whose last
    // digit flickers twice a second is a figure nobody reads, and what it is worth is a decision
    // to make at a glance while walking.
    public static final int POLE_STEP_POINTS = 5;

    // What a pole loses every second, which is what turns a distance into a price: a hundred
    // points per thirty seconds. At the level-one pace of six kilometres an hour, thirty seconds
    // is fifty metres -- so a pole fifty metres off starts at two hundred, one at a hundred and
    // fifty metres starts at four hundred, and reaching either of them at that pace pays the same
    // hundred. Walk faster and the difference is yours; dawdle and it is gone.
    public static final double POLE_DECAY_PER_S = 100.0 / 30.0;

    // What a pole taken throws out: a ring that wounds whatever it washes over. Three is enough
    // for a creeper, enough for a Chucky, and half of a ghost -- which takes two poles.
    //
    // Its width is a floor PLUS what the pole paid, an eighth of a metre a point: twenty-five
    // metres for the poorest pole, eighty-seven for one worth five hundred. The speed that earns the points
    // is therefore the same speed that clears the ground, which is what makes a course playable
    // on the poles alone -- and it needs no guard against farming, since the halving at every
    // replay walks the ring back down to the floor.
    //
    // The floor is not a round number picked for the look of it: it has to cover the ground a
    // creature can reach the player from. Player and creature meet at about nine metres between
    // their centres, so twenty leaves room for the one standing behind the one on top of you.
    public static final int POLE_BLAST_DAMAGE = 3;
    public static final double POLE_BLAST_M_PER_POINT = 0.125;
    public static final double POLE_BLAST_MIN_M = 25.0;

    /** How wide the ring of a pole that paid that much goes, in metres. */
    public static double poleBlastM(int points) {

        if( points < 0 )
            points = 0;

        return POLE_BLAST_MIN_M + (points * POLE_BLAST_M_PER_POINT);
    }

    // --- the hearts ----------------------------------------------------------------------------
    //
    // The other half of the game inside the game. Certain marks GROW hearts: a gauge fills over
    // their own slow clock, and choosing such a mark as the main one, once it is full, makes it
    // drop what it has grown. The hearts land in the field around it and wait to be walked over.
    //
    // Which marks, and how many each gives, is a matter of what the place IS: a fish or a crab is
    // a snack, a restaurant or a shop is a meal, a church is a church. Written as the flat icon
    // index, the same as the poles -- see EasterEgg/MarkIcons_ids.png.
    private static final int[][] HEART_MARKS = {
            //  icon  hearts
            {   1,    1 },      // a fish
            {  11,    1 },      // a crab
            {  16,    2 },      // a restaurant
            {  17,    2 },      // a cafe
            {  36,    2 },      // a corner shop
            {  87,    2 },      // a market stall
            {  37,    2 },      // a surgery
            {  40,    2 },      // a hotel
            {  66,    2 },      // a mountain refuge
            {  18,    3 },      // a church
            {  19,    3 },      // a mission
            {  20,    3 },      // a mosque
            {  21,    3 },      // a pagoda
    };

    // What growing one heart costs in time. A mark that gives three therefore takes three times
    // as long to be worth visiting again, which is the whole of the balance: the generous places
    // are the slow ones.
    //
    // One minute for the first, half a minute for each after it: one, one and a half, two. Three
    // minutes a heart was measured in play and it was far too long -- a church was never once
    // worth coming back to inside a game -- and straight proportion made the generous places
    // three times the wait, which is a punishment rather than a balance. A quantity discount.
    public static final long HEART_GROW_MS = 60 * 1000L;
    public static final long HEART_GROW_MORE_MS = 30 * 1000L;

    /** How far from the mark a dropped heart may land, in metres. At the very most. */
    public static final double HEART_THROW_M = 30.0;

    /** How long a heart waits on the ground before it goes, in seconds. */
    public static final int HEART_LIFE_S = 50;

    /** How many hearts that icon grows, or zero when the place grows none. */
    public static int heartsOf(int bitmapId) {

        for( int[] row : HEART_MARKS ) {
            if( row[0] == bitmapId )
                return row[1];
        }

        return 0;
    }

    /** How long that mark takes to fill its gauge, in milliseconds. */
    public static long growMs(int hearts) {

        if( hearts <= 0 )
            return 0;

        return HEART_GROW_MS + ((hearts - 1) * HEART_GROW_MORE_MS);
    }

    /**
     * Does that level end on a boss?
     *
     * Asked of the table rather than of a flag: a boss here is nothing but a wave whose roster is
     * a capital, so the only honest way to know is to look at what the last wave sends.
     */
    public static boolean hasBoss(int level) {

        Wave last = wave(level, waveCount(level));

        if( (last == null) || (last.queue == null) )
            return false;

        for( int species : last.queue ) {
            if( species == YOUNGHEE )
                return true;
        }

        return false;
    }

    // --- being healed --------------------------------------------------------------------------

    // What a pole pays towards the player's own skin, as opposed to his score: the part of its
    // price that the REFERENCE pace earns, never the bonus for beating it. Without that cap a
    // snowboarder at thirty an hour, whose poles pay four hundred and twenty apiece, would heal
    // five times faster than a walker for the same three posts -- and the gauge only holds nine.
    //
    // Capped that way, a course of three poles gives one portion on the first lap and one on the
    // second, then dries up as the halving bites: about three a level, at any speed.
    public static final int HEAL_POLE_POINTS = 200;

    /** What a pole contributes towards the next portion. */
    public static int healValueOf(int points) {
        return (points > POLE_TARGET_POINTS) ? POLE_TARGET_POINTS : points;
    }

    /** What clearing a level gives back, in portions. */
    public static final int HEAL_LEVEL_PORTIONS = 1;

    /** How far one must get from a pole already scored before it counts again, in metres. */
    public static final double POLE_AWAY_M = 10.0;

    /** And how long it stays dead once scored, in seconds. */
    public static final int POLE_DEAD_S = 30;

    // What a pole pays the second time is half what it paid the first, and so on down to the
    // floor. Circling the same three poles is a way to score, but not a way to score for ever.
    public static final int POLE_REPLAY_DIVIDER = 2;

    /** Is that icon a pole? The index is the flat one, sheet * 36 + row * 6 + col. */
    public static boolean isPoleIcon(int bitmapId) {

        for( int icon : POLE_ICONS ) {
            if( icon == bitmapId )
                return true;
        }

        return false;
    }

    /**
     * How far off a pole comes into play, in metres: exactly the distance that is worth
     * {@link #POLE_MAX_POINTS} at the pace of that level.
     *
     * Two hundred metres at level one, and it widens with the level, since a level that expects a
     * faster pace covers more ground in the same budget of time.
     */
    public static double poleEntryM(int level) {

        double speed = poleSpeedKmh(level) / 3.6;

        return ((POLE_MAX_POINTS - POLE_TARGET_POINTS) / POLE_DECAY_PER_S) * speed;
    }

    /**
     * The pace a level expects, in kilometres an hour: reach a pole at that speed and it pays
     * {@link #POLE_TARGET_POINTS}.
     *
     * Six to begin with -- a brisk walk -- and a little more at every level, which is the only
     * thing that makes the poles of a high level worth playing for.
     */
    public static double poleSpeedKmh(int level) {

        if( level < 1 )
            return 0;

        return 6.0 + (1.5 * (level - 1));
    }

    // --- the items -----------------------------------------------------------------------------
    //
    // The third game inside the game. Four marks are armed places rather than generous ones: they
    // grow a weapon instead of a heart, and taking one as the MAIN MARK once its gauge is full
    // makes it throw what it grew out towards the player, where the creatures are. It lands, it
    // waits, and the first creature to walk into it is sorry.
    //
    // The four are the four rows of eg_items, and the mark IS the object: a mine is a mine, a bear
    // trap a bear trap, a nest a nest. Nothing here is one drawing wearing another's name.
    //
    // Unlike a heart, an item is thrown at the PLAYER's feet and not into the field around the
    // mark: it is a weapon, and a weapon left two hundred metres behind is a weapon wasted.

    /** What an item does to whoever sets it off, besides wounding it. */
    public static final int EFFECT_NONE = 0;
    public static final int EFFECT_BLAST = 1;    // it goes off, and the ring carries the wound
    public static final int EFFECT_HOLD = 2;     // held where it stands, for a while
    public static final int EFFECT_SLOW = 3;     // it walks on, slower

    /**
     * One kind of item: what grows it, what it looks like, what it costs in time, and what it does.
     *
     * A small class rather than a row of figures, which is what the poles and the hearts use. An
     * item is a dozen things at once and a table of a dozen columns is unreadable: nobody can read
     * {34, 0, 5, 45000, 70, 1, 1, 0} and see a mine in it.
     */
    public static final class Item {

        /** The flat icon of the mark that grows it: sheet * 36 + row * 6 + col. */
        public final int icon;

        /** Its row on eg_items. */
        public final int row;

        /** Lives taken from whatever sets it off. */
        public final int damage;

        /** What growing one costs in time, and how long it then waits on the ground. */
        public final long chargeMs;
        public final int lifeS;

        /** How many creatures it has in it before it is spent. */
        public final int uses;

        /** What it does beyond the wound, how long for, and how hard. */
        public final int effect;
        public final long effectMs;
        public final double effectCoef;

        /** Does it reach something that flies? Only the wasps do: the rest are on the floor. */
        public final boolean reachesFlyers;

        /** What it says as it lands and arms itself, and what it says when it goes off. */
        public final int setSound;
        public final int offSound;

        Item(int icon, int row, int damage, long chargeMs, int lifeS, int uses,
             int effect, long effectMs, double effectCoef, boolean reachesFlyers,
             int setSound, int offSound) {

            this.icon = icon;
            this.row = row;
            this.damage = damage;
            this.chargeMs = chargeMs;
            this.lifeS = lifeS;
            this.uses = uses;
            this.effect = effect;
            this.effectMs = effectMs;
            this.effectCoef = effectCoef;
            this.reachesFlyers = reachesFlyers;
            this.setSound = setSound;
            this.offSound = offSound;
        }
    }

    // The four of them, in the order of the sheet.
    //
    // The ladder is the whole balance: what hurts most takes longest to grow, and every one of
    // them charges faster than it rots, so a generous object can be laid twice over before the
    // first has gone. A creeper has one life, Chucky three, a ghost five -- which is why the mine
    // is the only thing on this list that takes a ghost down on its own, and the banana the only
    // one that cannot kill anything but a creeper.
    private static final Item[] ITEMS = {

            // the mine: slow to grow, and it does not wound by contact at all -- the ring does
            new Item(34, 0, 5, 45000, 70, 1, EFFECT_BLAST,    0, 1.0, false,
                     R.raw.eg_item_set_mine,   R.raw.eg_item_blast_mine),

            // the bear trap: it holds what it bites, which is worth more than the wound
            new Item(33, 1, 3, 30000, 50, 1, EFFECT_HOLD,  3000, 1.0, false,
                     R.raw.eg_item_set_trap,   R.raw.eg_item_snap_trap),

            // the wasps: the only thing here that reaches a ghost, and it has two in it
            new Item(35, 2, 2, 20000, 40, 2, EFFECT_SLOW,  5000, 0.5, true,
                     R.raw.eg_item_set_wasps,  R.raw.eg_item_swarm_wasps),

            // the banana: cheap, quick, and it puts somebody on the floor for two seconds
            new Item(30, 3, 1, 10000, 30, 1, EFFECT_HOLD,  2000, 1.0, false,
                     R.raw.eg_item_set_banana, R.raw.eg_item_slip_banana),
    };

    /** What that icon grows, or null when the place grows nothing. */
    public static Item itemOf(int bitmapId) {

        for( Item item : ITEMS ) {
            if( item.icon == bitmapId )
                return item;
        }

        return null;
    }

    /**
     * How near a mark has to be to play at all, in metres.
     *
     * The screen covers two hundred metres across, so at a hundred the mark is on it when it
     * throws and one can see where the thing came from. Beyond that it is not refused, it is
     * merely PAUSED: a gauge keeps what it has grown and goes on from there when one comes back.
     *
     * The same figure governs the hearts, for the same reason: a church half a kilometre off has
     * no business ripening while nobody is anywhere near it.
     */
    public static final double ITEM_ENTRY_M = 100.0;

    // Where a thrown item lands: near the player, since that is where the creatures are going,
    // but never under his own feet. In the direction of the mark, give or take, so that the throw
    // reads as coming FROM the mark.
    public static final double ITEM_DROP_MIN_M = 8.0;
    public static final double ITEM_DROP_MAX_M = 20.0;
    public static final double ITEM_DROP_SPREAD_DEG = 20.0;

    /** How fast a thrown item travels, in kilometres an hour: a heavy lob, not a shot. */
    public static final double ITEM_FLY_KMH = 120.0;

    /** How wide the mine goes off, in metres. Half the pole's ring, and it carries five. */
    public static final double MINE_BLAST_M = 30.0;

    // --- the levels ----------------------------------------------------------------------------
    //
    // Four tables, ten waves each, one species joining at every level: creepers alone, then
    // Chucky, then the ghost, then the boss closing the fourth. Everything above level four
    // replays the third or the fourth, harder -- see table() and startStress() at the bottom.
    //
    // Read the notation at the top of this file. In short: what it sends, /front, @patience/step,
    // and +reserve by groups, one group per notch, a plus meaning the front grows with it.

    private static final String[] LEVEL_1 = {
            "9c /3 @30 +c",             // one creeper: where do I tap, and how close does it get
            "15c /5 @30 +c",
            "30c /7 @60 +c",
            "30c /10 @60/20 +c +2c",         // dawdling brings them two at a time
            "40c /12 @75/20 +2c",
            "50c /15 @90/30 +3c",
    };

    private static final String[] LEVEL_2 = {
            "3ck 4c 3ck /4 @30 +c",          // Chucky joins: faster, and he does not wander
            "4c 3ck 3ck /4 @30 +c +k",
            "4ck 4ck 4ck /5 @30 +c k2c",
            "5ck 5ck 4c2k /6 @45 +c +kc",
            "5c2k 7c 5c2k /7 @45 +2c k3c",
            "6ck 6ck 6ck /7 @45 +2c +kc",
            "7ck 6c2k 6c2k /8 @45 +2c k2c",
            "7c2k 7c2k 7c2k /9 @75/30 +2c 2k",
            "7c2k 6c3k 6c3k /9 @75/30 +3c 2k3c",
            "8c2k 7c3k 7c3k /10 @90/30 +3c +2k 2k4c",
    };

     
    private static final String[] LEVEL_3 = {
            "3cg 4c 3ck /4 @30 +ck",          // the ghost joins: it walks through everything
            "4c 2ckg 3ck /4 @30 +ck +g",
            "4ck 3ckg 5cg /5 @30 +ck g2c",
            "5cg 5ck 4ckg /6 @45 +ck +kgc",
            "5c2k 6ck 5ckg /7 @45 +2ck g3c",
            "6cg 6cg 5c2k /7 @45 +2ck +gkc",
            "7ck 7cg 6cgk /8 @45 +2ck gk2c",
            "7ckg 7c2k 7c2g /9 @75/30 +2ck 2kg",
            "7cgk 7ckg 6c2gk /9 @75/30 +3ck 2kg3c",
            "8cgk 6c2g2k 8cgk /10 @90/30 +3ckg +2k 2kg4c",
    };

    private static final String[] LEVEL_4 = {
            "1Y /1 @90/15 +ck +2cg +3c2k +2g4c ckccgkccc", // and she closes the level, the crowd thickening round her
    };

    private static final String[][] LEVELS = {
            LEVEL_1,
            LEVEL_2,
            LEVEL_3,
            LEVEL_4,
    };



    // --- what a wave is ------------------------------------------------------------------------

    /** One wave, as the table describes it. Read-only: the orchestrator works on its own copy. */
    public static final class Wave {

        // What it sends, in the order written, one entry per creature.
        final int[] queue;

        // How many of them stand on the map at once.
        final int front;

        // How long the player has before the hurry up starts, in milliseconds.
        final long patienceMs;

        // And how long between the notches after that.
        final long stepMs;

        // What the hurry up adds, one GROUP per notch. The last group repeats for ever.
        final int[][] reserve;

        // And for each of them, whether it widens the front by its own size or only lengthens
        // the queue. Same indices as the groups above.
        final boolean[] widens;

        private Wave(int[] queue, int front, long patienceMs, long stepMs, int[][] reserve,
                     boolean[] widens) {
            this.queue = queue;
            this.front = front;
            this.patienceMs = patienceMs;
            this.stepMs = stepMs;
            this.reserve = reserve;
            this.widens = widens;
        }
    }

    // The whole table, parsed. [level - 1][wave - 1], ten waves per level whatever the number of
    // lines written.
    private static final Wave[][] WAVES = parseAll();

    // --- what the orchestrator asks ------------------------------------------------------------

    /**
     * The wave to play, for a level and a wave number counted from one.
     *
     * A level above the last one written replays an earlier table: the same waves, made harder by
     * the stress it starts at rather than by another table nobody has written.
     */
    public static Wave wave(int level, int waveNo) {

        int table = table(level);

        int index = waveNo - 1;

        if( index < 0 )
            index = 0;

        Wave[] waves = WAVES[table - 1];

        if( index >= waves.length )
            index = waves.length - 1;

        return waves[index];
    }

    /** How many waves that level is made of, which the progress bar draws as its slots. */
    public static int waveCount(int level) {
        return WAVES[table(level) - 1].length;
    }

    /**
     * How many notches of hurry up a level STARTS at.
     *
     * Zero for the levels that have a table of their own. Above those, one notch more every two
     * levels: a replayed level is its original with the stress already biting, which is the whole
     * difference between them and costs no second table.
     */
    public static int startStress(int level) {

        if( level <= LEVELS.length )
            return 0;

        return (level - LEVELS.length + 1) / 2;
    }

    /** One creature of that species, on that surface. It puts itself on the map. */
    public static ChibiCharacter spawn(int species, GameSurface gameSurface) {

        switch( species ) {

            case CHUCKY:
                return new ChibiChucky(gameSurface);

            case GHOST:
                return new ChibiGhost(gameSurface);

            case YOUNGHEE:
                return new ChibiYoungHee(gameSurface);

            default:
                return new ChibiCreeper(gameSurface);
        }
    }

    /**
     * Which written table a level plays.
     *
     * Above the last one written, the last TWO alternate. Two levels in a row are therefore never
     * the same shape, and -- this is the point -- there is nothing to edit the day a fifth table
     * is written: it simply becomes one of the two, and the fourth takes the other place.
     */
    private static int table(int level) {

        if( level < 1 )
            return 1;

        if( level <= LEVELS.length )
            return level;

        int last = LEVELS.length;
        int before = Math.max(1, last - 1);

        // 0, 1, 0, 1 ... from the first level that has no table of its own.
        int turn = (level - last - 1) % 2;

        return (turn == 0) ? before : last;
    }

    // --- reading the table ---------------------------------------------------------------------

    private static Wave[][] parseAll() {

        Wave[][] all = new Wave[LEVELS.length][];

        for( int i = 0; i < LEVELS.length; i++ )
            all[i] = parseLevel(i + 1, LEVELS[i]);

        return all;
    }

    /**
     * The ten waves of one level, out of however many lines were written for it.
     *
     * A pinned line is placed at its rank and the line before it fills the gap by repeating; what
     * is left after the last line is the last line again.
     */
    private static Wave[] parseLevel(int level, String[] lines) {

        List<Wave> waves = new ArrayList<Wave>();

        Wave last = null;

        for( String line : lines ) {

            try {
                last = readInto(waves, last, level, line);
            }
            catch( IllegalArgumentException e ) {

                // A line that cannot be read COSTS ITS OWN LINE and nothing more. It used to
                // throw from here, and since this class is loaded the moment a game starts, a
                // mistake in the table took the whole application down with it -- a "§" typed
                // where a "6" was meant (same key on a French Mac, without the shift) went out
                // in the 1.0.921 release. A table written by hand will have typos; they belong
                // in the log, loudly, not in a crash.
                GBLog.e(TAG, "wave dropped -- " + e.getMessage());
            }
        }

        if( waves.isEmpty() ) {

            // Every line of the level was unreadable. Rather than throw -- which is the thing we
            // have just stopped doing -- the level falls back to one creeper, which is enough to
            // play and impossible to miss in the log.
            GBLog.e(TAG, "level " + level + " has no readable wave at all, falling back to one creeper");

            waves.add(parseWave(level, "1c"));
        }

        // And that is the level's length: what was written, no padding to a figure nobody asked
        // for. A level of four lines is four waves, and the bar draws four slots.
        return waves.toArray(new Wave[waves.size()]);
    }

    /**
     * Reads one line into the level, and hands back what becomes the line "above" the next one.
     *
     * Separate from the loop so that the whole of a line's reading -- its text and its pin --
     * fails in one place, and is dropped in one place.
     */
    private static Wave readInto(List<Wave> waves, Wave last, int level, String line) {

        int pin = pinOf(line);

        Wave wave = parseWave(level, line);

        if( pin > 0 ) {

            if( (last == null) && (waves.size() < (pin - 1)) )
                throw new IllegalArgumentException(
                        where(level, line) + "pinned to wave " + pin
                        + " with nothing written before it to fill the gap");

            if( pin > WAVES_MAX )
                throw new IllegalArgumentException(
                        where(level, line) + "pinned to wave " + pin + ", and a level stops at "
                        + WAVES_MAX);

            // The line above fills the gap by repeating, which is what lets a level of ten
            // waves be written in three lines.
            while( waves.size() < (pin - 1) )
                waves.add(last);

            if( waves.size() > (pin - 1) )
                throw new IllegalArgumentException(
                        where(level, line) + "pinned to wave " + pin
                        + ", which is already taken");
        }

        if( waves.size() >= WAVES_MAX )
            throw new IllegalArgumentException(
                    where(level, line) + "a level stops at " + WAVES_MAX + " waves");

        waves.add(wave);

        return wave;
    }

    // The rank a line is pinned to, or zero. Written as a leading "10:".
    private static int pinOf(String line) {

        int colon = line.indexOf(':');

        if( colon <= 0 )
            return 0;

        try {
            return Integer.parseInt(line.substring(0, colon).trim());
        }
        catch( NumberFormatException e ) {
            return 0;
        }
    }

    private static Wave parseWave(int level, String line) {

        String body = line;

        int colon = pinOf(line) > 0 ? line.indexOf(':') : -1;

        if( colon >= 0 )
            body = line.substring(colon + 1);

        List<Integer> queue = new ArrayList<Integer>();
        List<int[]> reserve = new ArrayList<int[]>();
        List<Boolean> widens = new ArrayList<Boolean>();

        int front = -1;
        int patienceS = -1;
        int stepS = -1;

        boolean inReserve = false;

        for( String token : body.trim().split("\\s+") ) {

            if( token.isEmpty() )
                continue;

            // A plus opens the reserve, and on a group it also means "this one widens the
             // front". A plus on its own opens the reserve without saying anything about the
             // group that follows.
            boolean plus = token.startsWith("+");

            if( plus ) {
                inReserve = true;
                token = token.substring(1);

                if( token.isEmpty() )
                    continue;
            }

            if( token.startsWith("/") ) {
                front = number(level, line, token.substring(1));
                continue;
            }

            if( token.startsWith("@") ) {

                // "@25" or "@25/8": the patience, and after the slash the time between notches.
                String[] parts = token.substring(1).split("/", -1);

                if( parts.length > 2 )
                    throw new IllegalArgumentException(
                            where(level, line) + "\"" + token + "\" has more than a patience "
                            + "and a step");

                patienceS = number(level, line, parts[0]);

                if( parts.length > 1 )
                    stepS = number(level, line, parts[1]);

                continue;
            }

            // Anything else is a count and a species, possibly several in a row: "3c", "c" for
            // one, "2c1k" for two creepers and a Chucky.
            List<Integer> group = creatures(level, line, token);

            // In the roster a token is just a run of creatures in one long queue. In the reserve
            // it is a GROUP, and a group is what ONE notch sends.
            if( inReserve ) {
                reserve.add(toArray(group));
                widens.add(Boolean.valueOf(plus));
            }
            else {
                queue.addAll(group);
            }
        }

        if( queue.isEmpty() )
            throw new IllegalArgumentException(where(level, line) + "sends nobody");

        if( front <= 0 )
            front = 1;

        if( patienceS < 0 )
            patienceS = DEFAULT_PATIENCE_S;

        if( stepS <= 0 )
            stepS = DEFAULT_STEP_S;

        // No reserve written: the hurry up sends one more of whatever the wave opens with, and
        // makes room for it.
        if( reserve.isEmpty() ) {
            reserve.add(new int[] { queue.get(0).intValue() });
            widens.add(Boolean.TRUE);
        }

        boolean[] flags = new boolean[widens.size()];

        for( int i = 0; i < flags.length; i++ )
            flags[i] = widens.get(i).booleanValue();

        return new Wave(toArray(queue), front, patienceS * 1000L, stepS * 1000L,
                        reserve.toArray(new int[reserve.size()][]), flags);
    }

    /**
     * The creatures a token names: "3c" is three creepers, "2c1k" two creepers and a Chucky.
     *
     * The same reading serves the roster and the reserve, which do not store it the same way: the
     * roster pours them into one queue, the reserve keeps the token together as one notch's worth.
     */
    private static List<Integer> creatures(int level, String line, String token) {

        List<Integer> out = new ArrayList<Integer>();

        int at = 0;

        while( at < token.length() ) {

            int cut = at;

            while( (cut < token.length()) && Character.isDigit(token.charAt(cut)) )
                cut++;

            int count = (cut > at) ? number(level, line, token.substring(at, cut)) : 1;

            if( cut >= token.length() )
                throw new IllegalArgumentException(
                        where(level, line) + "\"" + token + "\" ends on a count with no species");

            int species = LETTERS.indexOf(token.charAt(cut));

            if( species < 0 )
                throw new IllegalArgumentException(
                        where(level, line) + "\"" + token.charAt(cut) + "\" is no species I know "
                        + "of; they are written " + LETTERS);

            for( int i = 0; i < count; i++ )
                out.add(Integer.valueOf(species));

            at = cut + 1;
        }

        if( out.isEmpty() )
            throw new IllegalArgumentException(
                    where(level, line) + "\"" + token + "\" names no creature");

        return out;
    }

    private static int number(int level, String line, String text) {

        try {
            return Integer.parseInt(text);
        }
        catch( NumberFormatException e ) {
            throw new IllegalArgumentException(
                    where(level, line) + "\"" + text + "\" is not a number");
        }
    }

    private static int[] toArray(List<Integer> list) {

        int[] array = new int[list.size()];

        for( int i = 0; i < array.length; i++ )
            array[i] = list.get(i);

        return array;
    }

    private static String where(int level, String line) {
        return String.format(Locale.US, "level %d, wave \"%s\": ", level, line);
    }

    private GameLevels() {
    }
}
