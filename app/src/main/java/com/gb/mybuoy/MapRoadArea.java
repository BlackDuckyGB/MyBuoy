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

import android.util.Log;

public class MapRoadArea extends MapArea {

    private final MapRoad road;


    public MapRoadArea(Chunk chunk, MapRoad road) {
        super(chunk, road.getTitle(), TypeArea.ROAD_AREA, false );

        this.road = road;

        int strokeWidth = 10;
        try {
            strokeWidth = Math.round((road.getPaintLayer0().getStrokeWidth() - road.getPaintLayer1().getStrokeWidth()) / 2);
        }
        catch (Exception e ) {
            GBLog.e( "CHUNCK", "ERR"+e.toString());
        }

        if( strokeWidth < 5 )
            strokeWidth = 5;

        paintLayer0.setStrokeWidth(strokeWidth);
        paintLayer0.setColor(road.getPaintLayer0().getColor());
        paintLayer0.setPathEffect(road.getPaintLayer0().getPathEffect());
        paintLayer0.setStrokeCap(road.getPaintLayer0().getStrokeCap());

        paintLayer1.setColor(road.getPaintLayer1().getColor());
        paintLayer1.setPathEffect(road.getPaintLayer1().getPathEffect());
        paintLayer1.setStrokeCap(road.getPaintLayer1().getStrokeCap());
    }
}