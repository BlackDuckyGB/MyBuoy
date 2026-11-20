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
import java.util.List;
import java.util.Locale;

// #79 Speed test page: pure computations on a copy of the fix history, no Android dependency, no lock.
public final class SpeedTest {

    // One line of the table: FIX (the newest) against FIX-n. Public fields, read by data binding.
    public static final class Row {
        public String label;      // "FIX-n"
        public String dtText;     // "1000 ms"
        public String distText;   // "12.4 m"
        public String speedText;  // in the current unit
        public String inCircle;   // "3/3", or "–" for FIX-1 (no intermediate)
        public boolean allInside; // false when an intermediate lies outside the circle (shown red bold)
    }

    private SpeedTest() {
    }

    // Row n: FIX (index 0) against FIX-n (index n). The intermediates FIX-1..FIX-(n-1) count when they
    // lie inside the circle of diameter [FIX, FIX-n], i.e. (Thales) PA² + PB² <= AB².
    public static List<Row> compute(double[] lat, double[] lon, long[] timeMs, int fill, int unit) {
        List<Row> rows = new ArrayList<>();

        for (int n = 1; n < fill; n++) {
            double ab = distM(lat, lon, 0, n);
            long dt = timeMs[0] - timeMs[n];
            int inside = 0;

            for (int k = 1; k < n; k++) {
                double pa = distM(lat, lon, 0, k);
                double pb = distM(lat, lon, n, k);

                if (pa * pa + pb * pb <= ab * ab) {
                    inside++;
                }
            }

            Row r = new Row();
            r.label = "FIX-" + n;
            r.dtText = dt + " ms";
            r.distText = String.format(Locale.US, "%.1f m", ab);
            r.speedText = (dt > 0) ? formatSpeed(ab / 1000.0 / (dt / 3600000.0), unit) : "–";
            r.inCircle = (n == 1) ? "–" : inside + "/" + (n - 1);
            r.allInside = (inside == n - 1);
            rows.add(r);
        }
        return rows;
    }

    // Same units and coefficients as AppManager (0 km/h, 1 mph, 2 knots), one decimal, Locale.US.
    public static String formatSpeed(double kmh, int unit) {
        switch (unit) {
            case 1:  return String.format(Locale.US, "%.1f mph", kmh * 0.6213712);
            case 2:  return String.format(Locale.US, "%.1f kn", kmh * 0.5399565);
            default: return String.format(Locale.US, "%.1f km/h", kmh);
        }
    }

    // Horizontal distance in metres between fixes i and j.
    private static double distM(double[] lat, double[] lon, int i, int j) {
        return Chunk.measureKm(lat[i], lon[i], lat[j], lon[j]) * 1000.0;
    }
}
