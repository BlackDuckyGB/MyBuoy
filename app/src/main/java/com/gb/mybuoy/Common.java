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

public class Common {

    public static int tryParseInt(String value) {
        if( value == null )
            return 0;

        try {
            return Integer.parseInt(value);
        } catch(NumberFormatException nfe) {
            // Log exception.
            return 0;
        }
    }

    /**
     * Clips the segment (x0,y0)-(x1,y1) against the rectangle [xmin,xmax]x[ymin,ymax], by the
     * Liang-Barsky algorithm. Returns {ax,ay,bx,by}, the VISIBLE part of the segment, or null when
     * it lies entirely outside.
     *
     * Used to place what must stay readable when what it labels runs off screen: the middle of the
     * visible part of an edge, rather than its geometric middle, which may itself be off screen.
     */
    /**
     * Does that segment pass within `radius` of that point?
     *
     * The question a moving player asks of everything standing still: a position arrives about
     * once a second, so what matters is the ground covered between two of them and not the place
     * one ended up. Squared throughout -- the root would buy nothing.
     */
    public static boolean segmentReaches(double px, double py,
                                         double x0, double y0, double x1, double y1,
                                         double radius) {

        double dx = x1 - x0;
        double dy = y1 - y0;

        double fx = x0 - px;
        double fy = y0 - py;

        double length = (dx * dx) + (dy * dy);

        // How far along the segment the closest point lies, kept between its two ends: a standing
        // player is a segment of no length, and that is its starting point.
        double along = (length <= 0) ? 0 : -((fx * dx) + (fy * dy)) / length;

        if( along < 0 )
            along = 0;
        else if( along > 1 )
            along = 1;

        double nearX = fx + (along * dx);
        double nearY = fy + (along * dy);

        return ((nearX * nearX) + (nearY * nearY)) <= (radius * radius);
    }

    public static double[] clipSegment(double x0, double y0, double x1, double y1,
                                            double xmin, double ymin, double xmax, double ymax) {
        double dx = x1 - x0, dy = y1 - y0;
        double[] p = { -dx, dx, -dy, dy };
        double[] q = { x0 - xmin, xmax - x0, y0 - ymin, ymax - y0 };
        double u0 = 0.0, u1 = 1.0;
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0.0) {
                if (q[i] < 0.0) return null;               // parallel to an edge and outside
            } else {
                double t = q[i] / p[i];
                if (p[i] < 0.0) { if (t > u1) return null; if (t > u0) u0 = t; }
                else            { if (t < u0) return null; if (t < u1) u1 = t; }
            }
        }
        return new double[] { x0 + u0 * dx, y0 + u0 * dy, x0 + u1 * dx, y0 + u1 * dy };
    }
}
