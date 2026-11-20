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
 * The textures every map object shares, decoded ONCE for the whole app and never recycled.
 *
 * point001 is a 32x32 dot used as the texture of every road, road segment and area. It used to be
 * decoded per object and per rescale: a coastal chunk holds over a thousand roads, so switching to
 * a chunk decoded the same PNG a thousand times. Measured on the emulator, contextScaleObjects()
 * took 4.6 s for such a chunk, which is what made the loading loop blind for that long (#87).
 *
 * NEVER RECYCLED, on purpose: it belongs to no chunk. MapRoad and MapArea recycle their per-object
 * textures when a chunk stops being drawn, and a 1x1 grid makes those "sub-images" the very same
 * Bitmap (Bitmap.createBitmap returns the source when the subset covers it whole), so they ask
 * isShared() before recycling anything.
 */
public final class SharedTextures {

    private static volatile Bitmap _point = null;

    private SharedTextures() {
    }

    /** The shared dot texture. Decoded on first use; the same instance ever after. */
    public static Bitmap point(Resources res) {
        Bitmap point = _point;

        if( (point == null) || point.isRecycled() ) {
            synchronized( SharedTextures.class ) {
                point = _point;

                if( (point == null) || point.isRecycled() ) {
                    point = BitmapFactory.decodeResource(res, R.drawable.point001);
                    _point = point;
                }
            }
        }

        return point;
    }

    /** True for a bitmap that belongs to everyone: it must never be recycled. */
    public static boolean isShared(Bitmap bitmap) {
        return (bitmap != null) && (bitmap == _point);
    }
}
