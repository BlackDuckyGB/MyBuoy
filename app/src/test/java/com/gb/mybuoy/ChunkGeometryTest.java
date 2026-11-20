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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// #86 step 2: a chunk rebuilt from the bounds of a cached file must end up with the geometry of the
// chunk that downloaded it. The on-screen zoom is derived from that geometry (getVirtualZoom), so a
// mismatch would show a cached area at the wrong scale.
public class ChunkGeometryTest {

    private static final double LAT = 48.649;      // Saint-Malo
    private static final double LON = -2.025;

    private static final double[] SPEEDS = { 0, 30, 79, 80, 95, 105, 115, 130, 145, 155, 165, 180, 200 };

    @Test
    public void aCachedBboxFindsBackTheZoomOfItsDownload() {
        for( double speed : SPEEDS ) {
            Chunk downloaded = new Chunk(LAT, LON, speed);
            double[] bbox = bboxOf(downloaded);

            Chunk cached = new Chunk(bbox[0], bbox[1], bbox[2], bbox[3], Chunk.filterLevelForSpeed(speed));

            assertEquals("zoom at " + speed + " km/h",
                    downloaded.getVirtualZoom(), cached.getVirtualZoom());
        }
    }

    @Test
    public void theCentreOfACachedBboxIsInsideItsSafeZone() {
        for( double speed : SPEEDS ) {
            double[] bbox = bboxOf(new Chunk(LAT, LON, speed));
            Chunk cached = new Chunk(bbox[0], bbox[1], bbox[2], bbox[3], Chunk.filterLevelForSpeed(speed));

            assertTrue("centre at " + speed + " km/h", cached.coordonateInChunk(LAT, LON));
        }
    }

    // The bounds exactly as the cache writes them in the file name, "%.6f".
    private static double[] bboxOf(Chunk chunk) {
        String name = ChunkCache.fileName(ChunkCache.SOURCE_OVERPASS, 1,
                chunk.latitudeS, chunk.longitudeW, chunk.latitudeN, chunk.longitudeE);

        return ChunkCache.parseBbox(name);
    }
}
