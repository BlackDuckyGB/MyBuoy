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

import java.util.ArrayList;
import java.util.List;

/**
 * An item on its way from the mark that grew it to the ground near the player.
 *
 * A projectile in every mechanical sense -- it uses the flight of a Bullet unchanged, the three
 * frames back and forth, the sprite turned to its heading -- and nothing like one in what it is
 * for. A shot is spent on arrival; this is only beginning. Where it stops, a GameItem takes over
 * and waits there.
 *
 * It travels in a straight line and not in an arc, because it is shown turned to its own heading
 * and an arc would have it flying sideways to the way it points.
 *
 * It stops at the first thing it meets, which is the point of flying it at all rather than making
 * the item appear where it was going. A wall is a wall: a mark standing between the thrower and
 * the ground it was aimed at takes it, and the item lies at its foot. A creature standing there
 * takes it in the face -- the wound and the effect it would have had on the floor, no more and no
 * less -- and the item is one use down, which for three of the four means there is nothing left
 * to lie anywhere.
 *
 * Two things are kept out of its way: the mark that threw it, which it is born on top of and
 * would otherwise strike on its first frame, and the shots in flight, since a tomato has no
 * business bringing down a mine.
 */
public class ItemBullet extends Bullet {

    // Where the bodies sit in their cells on eg_items, measured on the sheet and not guessed: the
    // four of them are centred between 0.41 and 0.51 of the square, the trail hanging below.
    private static final float PIVOT_X = 0.50f;
    private static final float PIVOT_Y = 0.46f;

    // Silence. It is thrown, not fired, and what it has to say it says when it lands.
    private static final int NO_SOUND = -1;

    private static final float SET_VOLUME = 0.85f;

    /** Everything on this sheet is drawn from level one: a mark grows what a mark grows. */
    private static final int MIN_LEVEL = 1;

    // The sheet, shared by every item in flight. Scaled at load to the size the things are drawn
    // at on the ground, so that what flies and what lands are the same object and not two.
    private static Bitmap _sheet = null;

    /** Decodes the shared sheet and scales it to the size an item is drawn at. Called once. */
    public static void loadSheet(Resources res) {

        if( (_sheet != null) || (res == null) )
            return;

        Bitmap full = BitmapFactory.decodeResource(res, R.drawable.eg_items);

        if( full == null )
            return;

        int cell = Math.round(GameItem.cellPx(res));

        if( cell < 1 )
            cell = 1;

        int side = cell * 4;

        Bitmap scaled = Bitmap.createScaledBitmap(full, side, side, true);

        if( scaled != full )
            full.recycle();

        _sheet = scaled;
    }

    private final GameLevels.Item kind;

    // The mark that threw it: kept only to be left out of what it can hit.
    private final PersonalMark thrower;

    // Where it was aimed, and how far that is. Settled at birth: the landing place is worked out
    // from where the player stood when the mark let go, and following him afterwards would make
    // an item impossible to outrun.
    private final double landLongitude;
    private final double landLatitude;
    private final double rangeM;

    // Did it actually go off on somebody on the way? A mark stopping it is not a use spent.
    private boolean struckCreature = false;

    public ItemBullet(GameSurface gameSurface, GameLevels.Item kind, PersonalMark thrower,
                      double x, double y, double angleDeg, double rangeM,
                      double landLongitude, double landLatitude) {

        super(gameSurface, _sheet, x, y, angleDeg, PIVOT_X, PIVOT_Y);

        this.kind = kind;
        this.thrower = thrower;

        this.rangeM = rangeM;

        this.landLongitude = landLongitude;
        this.landLatitude = landLatitude;
    }

    @Override
    public int getSpriteRow() {
        return (kind == null) ? 0 : kind.row;
    }

    @Override
    public double getSpeedKmh() {
        return GameLevels.ITEM_FLY_KMH;
    }

    @Override
    public int getDamage() {
        return (kind == null) ? 0 : kind.damage;
    }

    @Override
    public double getRangeM() {
        return rangeM;
    }

    @Override
    public int getMinLevel() {
        return MIN_LEVEL;
    }

    @Override
    protected int getFireSoundRes() {
        return NO_SOUND;
    }

    @Override
    protected int getHitSoundRes() {
        return NO_SOUND;
    }

    /**
     * What it may run into: the creatures it is allowed to touch, and the marks -- except the one
     * that threw it.
     *
     * The shots in flight are left out, which the usual target list would have included.
     */
    @Override
    protected List<GameObject> getColliderTargets() {

        List<GameObject> targets = new ArrayList<GameObject>();

        List<ChibiCharacter> chibis = gameSurface.getChibis();

        for( int i = 0; i < chibis.size(); i++ ) {

            ChibiCharacter chibi = chibis.get(i);

            if( chibi == null )
                continue;

            // Over its head: a mine does not meet a ghost, in the air any more than on the floor.
            if( chibi.isFlying() && ((kind == null) || !kind.reachesFlyers) )
                continue;

            targets.add(chibi);
        }

        List<PersonalMark> marks = AppManager.getInstance().getMarksEnumerable();

        for( int i = 0; i < marks.size(); i++ ) {

            PersonalMark mark = marks.get(i);

            if( (mark == null) || (mark == thrower) )
                continue;

            targets.add(mark);
        }

        return targets;
    }

    /**
     * What it does to the one thing it ran into.
     *
     * A creature gets exactly what the ground would have given it. A mark gets nothing at all: it
     * is a wall, and the only thing a wall does is stop the thing.
     */
    @Override
    protected void strike(GameObject object) {

        if( !(object instanceof ChibiCharacter) )
            return;

        ChibiCharacter chibi = (ChibiCharacter) object;

        if( chibi.isDead() )
            return;

        struckCreature = true;

        GameItem.setOff(gameSurface, kind, chibi, this.x, this.y);
    }

    /**
     * It has stopped, and the thing it was carrying is laid down.
     *
     * Where it was aimed if it got there, where it stopped if something was in the way -- and in
     * that second case the position is read back off the chunk as a latitude and a longitude,
     * since that is the only form that survives the chunk being swapped underneath it.
     */
    @Override
    protected void onFinished(boolean byImpact) {

        if( kind == null )
            return;

        int left = kind.uses;

        double lon = landLongitude;
        double lat = landLatitude;

        if( byImpact ) {

            if( struckCreature )
                left--;

            Chunk chunk = gameSurface.getMap();

            if( chunk == null )
                return;

            lon = chunk.getLongitude(this.x, gameSurface.getAreaWidth());
            lat = chunk.getLatitude(this.y, gameSurface.getAreaHeight());
        }

        // Spent in mid-air: there is nothing left to lie anywhere, and the only thing that was
        // going to be heard has already been heard by whoever it went off on.
        if( left <= 0 )
            return;

        gameSurface.addItem(new GameItem(gameSurface, kind, lon, lat, left));

        gameSurface.playSoundOnce(kind.setSound, SET_VOLUME);
    }
}
