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

/**
 * The shell: the first row of the sheet, and the plainest thing that can be fired.
 *
 * Same figures as the tomato for now -- one point of damage, the same reach -- it is the picture
 * that differs. What it is really for is the boss: Young-Hee will fire these, and a tomato lobbed
 * by a doll would say the wrong thing entirely.
 */
public class BulletGun extends Bullet {

    private static final int SPRITE_ROW = 0;      // sheet order: shell, star, tomato, egg
    // 150 km/h is what the old figure of half an area pixel per millisecond worked out to at the
    // usual scale -- the flight is unchanged, it is only said in a unit that means something.
    private static final double SPEED_KMH = 100;
    private static final int DAMAGE = 2;          // two thirds of a life, in one shell
    private static final double RANGE_M = 500;    // metres
    private static final int MIN_LEVEL = 4;       // the boss levels, where she fires it

    public BulletGun(GameSurface gameSurface, double x, double y, double angleDeg) {
        super(gameSurface, x, y, angleDeg);
    }

    public int getSpriteRow() {
        return SPRITE_ROW;
    }

    public double getSpeedKmh() {
        return SPEED_KMH;
    }

    public int getDamage() {
        return DAMAGE;
    }

    public double getRangeM() {
        return RANGE_M;
    }

    public int getMinLevel() {
        return MIN_LEVEL;
    }

    /** Shot down by the doll, it is the doll that says so. */
    @Override
    protected int getGameOverSoundRes() {
        return R.raw.eg_younghee_you_are_dead;
    }

    /** A shell is fired, not thrown. */
    @Override
    protected int getFireSoundRes() {
        return R.raw.eg_gun;
    }

    /**
     * It flies HIGH, so nothing on the ground stops it.
     *
     * Not the marks, not the other creatures -- it passes over all of it. Two things can meet it:
     * the player, and another shot. A tomato thrown at the right moment takes it out of the air,
     * which is the second way of answering it besides stepping aside. What it will not do is mow
     * down Young-Hee's own crowd on its way.
     */
    @Override
    protected java.util.List<GameObject> getColliderTargets() {

        MapGPSPos player = gameSurface.getGps();

        if( player == null )
            return null;

        java.util.List<GameObject> targets = gameSurface.getBullets();
        targets.add(player);

        return targets;
    }
}
