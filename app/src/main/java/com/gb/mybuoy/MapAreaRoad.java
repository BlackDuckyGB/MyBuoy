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

public class MapAreaRoad extends MapRoad {

    private final MapArea area;

    public MapAreaRoad(Chunk chunk, MapArea area) {
        super( chunk, area.getTitle(), "", MapRoad.TypeRoad.AREA_ROAD, "", false, false, false, false, MapArea.TypeArea.UNKNOWN, 0);

        this.area = area;

        /*
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
        */

        paintLayer0.setColor(area.getPaintLayer0().getColor());
        //paintLayer0.setAlpha(area.getPaintLayer0().getAlpha()); // Already get with GetColor ?

        paintLayer1.setColor(area.getPaintLayer1().getColor());
        //paintLayer1.setAlpha(area.getPaintLayer1().getAlpha()); // Already get with GetColor ?

    }
}
