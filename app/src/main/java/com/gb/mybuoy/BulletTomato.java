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
 * The tomato of the first projectile sheet: soft, absurd, and the first thing the player is given.
 *
 * Its figures are the reference the other bullets will be tuned against. Five times the speed of a
 * chibi, which makes it plainly faster than what it chases without being impossible to follow with
 * the eye, and a range of three screens at the current zoom.
 */
public class BulletTomato extends Bullet {

    private static final int SPRITE_ROW = 2;      // sheet order: shell, star, tomato, egg
    // 150 km/h is what the old figure of half an area pixel per millisecond worked out to at the
    // usual scale -- the flight is unchanged, it is only said in a unit that means something.
    private static final double SPEED_KMH = 150;
    private static final int DAMAGE = 1;
    private static final double RANGE_M = 170;    // metres
    private static final int MIN_LEVEL = 2;       // the player is given it at level 2

    public BulletTomato(GameSurface gameSurface, double x, double y, double angleDeg) {
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
}
