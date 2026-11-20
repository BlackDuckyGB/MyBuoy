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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class OSMElement {
    public String type;
    public Long id;
    public Date timestamp;
    public Long version;
    public Long changeset;
    public String user;
    public Long uid;

    // Type node
    public Double lat;
    public Double lon;


    public Double latN;
    public Double latS;
    public Double longE;
    public Double longW;
    public Double kmX;
    public Double kmY;

    // Type: way
    public List<Long> nodes;
    public List<OSMElement> elements = new ArrayList<OSMElement>();

    // Type: relation
    public List<OSMMember> members;

    public Map<String, String> tags;
}
