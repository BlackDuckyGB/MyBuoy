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

public class GEOPoint {

    private final double x;
    private final double y;
    private final double z;
    private final double distance;

    public GEOPoint(double x, double y, double z, double distance) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.distance = distance;
    }

    public GEOPoint(double x, double y) {
        this( x, y, 0, 0);
    }

    public GEOPoint(float x, float y, float z, float distance) {
        this( (double)x, (double)y, (double)z, (double)distance);
    }

    public GEOPoint(float x, float y) {
        this( (double)x, (double)y, (double)0, (double)0);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public double getDistance() {
        return distance;
    }
}
