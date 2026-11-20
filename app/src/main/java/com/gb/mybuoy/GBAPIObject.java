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

import com.google.gson.Gson;

import java.util.List;

public class GBAPIObject {

    public String Status;
    public String Msg;

    public List<MapInfo> Map;

    public GBAPIObject()  {
    }



    public static GBAPIObject jsonToGBAPIObject(String jsonString ) {

        GBAPIObject data = null;
        try {
            Gson gson = new Gson();
            data = gson.fromJson(jsonString, GBAPIObject.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return data;
    }
}
