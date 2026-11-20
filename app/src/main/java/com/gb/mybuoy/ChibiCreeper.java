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
 * the creeper, the one that was already there before the game had a name. The plain one: it takes a single hit, it walks at the reference speed, and it is worth ten points.
 *
 * The sheet is static: every creature of this kind shares one decode of it.
 */
public class ChibiCreeper extends ChibiCharacter {

    private static AvatarCatalog.Sheet _sheet = null;

    private static final int MAX_HITS = 1;
    private static final double SPEED_KMH = 8.0;        // km/h, ChibiCharacter.DEFAULT_SPEED_KMH = 8
    // How far it sees, as a multiple of its collider.
    private static final double VIEW_COEF = 9.0;
    // Half its field of view, in degrees either side of where it looks: it sees far, so narrow.
    private static final double VIEW_ANGLE_DEG = 20.0;
    private static final int CONTACT_DAMAGE = 1;
    private static final int SCORE_VALUE = 10;
    static final int MIN_LEVEL = 0;   // il erre aussi hors du jeu : c'est en l'explosant qu'on trouve l'easter egg

    /** Decodes the sheet of this kind of creature. Called once, before the first one is made. */
    public static void loadSheet(Resources res) {
        if (_sheet == null)
            _sheet = AvatarCatalog.loadSheet(res, R.drawable.av_walk_creeper1);
    }

    public ChibiCreeper(GameSurface gameSurface) {
        super(gameSurface, _sheet);

        // It sees three times its own body around it.
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

    /** What it says when it catches sight of the player. */
    @Override
    protected int getViewSoundRes() {
        return R.raw.eg_brain;
    }
}
