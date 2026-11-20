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

/**
 * The ghost. Harder than the doll on both counts that matter: five hits to put down instead of
 * three, and fourteen kilometres an hour instead of eleven -- fast enough that walking away from
 * it is no longer a plan. It only turns up from level 3, and it is worth what it costs.
 *
 * Its play area is therefore the widest of the three: the radius is measured with the creature's
 * own speed, so a ghost is placed further out than a doll, which is placed further out than a
 * creeper -- each of them a minute away, which is the whole point.
 *
 * The sheet is static: every creature of this kind shares one decode of it.
 */
public class ChibiGhost extends ChibiCharacter {

    private static AvatarCatalog.Sheet _sheet = null;

    private static final int MAX_HITS = 5;
    private static final double SPEED_KMH = 14.0;       // km/h, ChibiCharacter.DEFAULT_SPEED_KMH = 8
    // How far it sees, as a multiple of its collider.
    private static final double VIEW_COEF = 3.0;
    // Half its field of view: it sees near, so wide.
    private static final double VIEW_ANGLE_DEG = 80.0;
    private static final int CONTACT_DAMAGE = 1;
    private static final int SCORE_VALUE = 100;
    // From level 3 only. Read by the orchestrator, which is what decides who appears.
    static final int MIN_LEVEL = 3;

    /** Decodes the sheet of this kind of creature. Called once, before the first one is made. */
    public static void loadSheet(Resources res) {
        if (_sheet == null)
            _sheet = AvatarCatalog.loadSheet(res, R.drawable.av_walk_ghost1);
    }

    public ChibiGhost(GameSurface gameSurface) {
        super(gameSurface, _sheet);

        collider.setViewCoef(VIEW_COEF);
        collider.setViewAngleDeg(VIEW_ANGLE_DEG);
    }

    public int getMaxHits() {
        return MAX_HITS;
    }

    /**
     * It flies, so nothing left on the floor can touch it.
     *
     * A mine, a bear trap and a banana skin all lie on the ground and it passes over all three.
     * The wasps are another matter: a swarm goes up to meet it.
     */
    @Override
    public boolean isFlying() {
        return true;
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

    /** What it says when it catches sight of the player. */
    @Override
    protected int getViewSoundRes() {
        return R.raw.eg_houuu;
    }

    /**
     * The one that walks through everything.
     *
     * It goes through the marks, through the player and through the other creatures, and they go
     * through it -- the single answer covers both sides of any contact it is part of. It stays a
     * target all the same: a ghost is not an obstacle, but it can still be shot at and killed.
     */
    @Override
    protected boolean isSolid() {
        return false;
    }
}
