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
 * A boss: the creature a level is built around, and not one of the crowd.
 *
 * It exists as a class of its own rather than as a flag on a figure sheet, because what makes a
 * boss is not only how much life it carries. It arrives alone, it is what the level is about, and
 * whatever the game ends up doing differently for one -- its own music, a life bar across the top,
 * a reward on its death -- belongs here, written once, rather than in each of them.
 *
 * For now it says only what it is. {@link #isBoss()} answers true for every creature below this
 * point and false for every other, which is all the rest of the game needs to ask.
 */
public abstract class ChibiBoss extends ChibiCharacter {

    protected ChibiBoss(GameSurface gameSurface, AvatarCatalog.Sheet sheet) {
        super(gameSurface, sheet);
    }

    @Override
    public boolean isBoss() {
        return true;
    }

    /**
     * A boss is not shoved about.
     *
     * Mobility is the share of a contact an object takes on itself, and a boss takes none: when a
     * creature walks into one, the creature is the one that gives way, exactly as it does against
     * a mark. The boss carries on with what it was doing, which is the whole point of being one.
     */
    @Override
    protected double getMobility() {
        return 0;
    }
}
