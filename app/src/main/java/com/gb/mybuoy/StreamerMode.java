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

import java.util.Random;

/**
 * #98 : Streamer mode, for recording videos without giving away where we are.
 *
 * The displayed coordinates are shifted to a fake place 1000 to 5000 km away (drawn once per
 * launch), and the names written on the map are swapped for made-up ones. Nothing real is
 * changed: only what is shown. No lock anywhere: one volatile flag and constants.
 */
public final class StreamerMode {

    /** Set by AppManager from the persisted "Streamer mode" switch. */
    public static volatile boolean enabled = false;

    private static final Random RANDOM = new Random();

    // Drawn once per launch: the fake place is DIST_KM away from the real one, towards BEARING.
    private static final double DIST_KM = 1000.0 + RANDOM.nextDouble() * 4000.0;
    private static final double BEARING = RANDOM.nextDouble() * 2.0 * Math.PI;
    private static final double KM_PER_DEG = 111.2;

    // Mixed into the name hash, so a real name does not get the same fake one on every launch.
    private static final int SEED = RANDOM.nextInt();

    private static final String[] STREETS = {
            "Heaven Street", "Duck Lane", "Golden Avenue", "Feather Road", "Buoy Boulevard",
            "Pond Street", "Quack Alley", "Lighthouse Way", "Seagull Drive", "Mallard Street",
            "Driftwood Lane", "Anchor Road"
    };

    private static final String[] REFS = {
            "D356", "A45", "N90", "D12", "N177", "D704", "A81", "D28", "N33", "D519"
    };

    private static final String[] PLACES = {
            "Ducky Church", "Goldenport", "Featherville", "Quackton", "Mallard Bay",
            "Pondsby", "Duckwater", "Little Beak", "Saint-Plume", "Webfoot Hill"
    };

    private static final String[] BUOYS = {
            "Duck Rock", "Golden Buoy", "Feather Beacon", "Old Quack", "Mallard Shoal",
            "Beak Point", "Webfoot Reef", "Drake Light", "Puddle Mark", "Egg Rock"
    };

    private StreamerMode() {
    }

    /** The coordinate to show: itself, or a shifted copy while Streamer mode is on. */
    public static GPSCoordonate shift(GPSCoordonate c) {
        if( !enabled )
            return c;

        double lat = c.getLatitude() + DIST_KM * Math.cos(BEARING) / KM_PER_DEG;
        double cosLat = Math.max(0.1, Math.cos(Math.toRadians(c.getLatitude())));
        double lon = c.getLongitude() + DIST_KM * Math.sin(BEARING) / (KM_PER_DEG * cosLat);

        // Past a pole: fold back, and go round to the other side of the globe.
        if( lat > 90.0 ) {
            lat = 180.0 - lat;
            lon += 180.0;
        } else if( lat < -90.0 ) {
            lat = -180.0 - lat;
            lon += 180.0;
        }
        while( lon > 180.0 )
            lon -= 360.0;
        while( lon < -180.0 )
            lon += 360.0;

        return new GPSCoordonate(lon, lat);
    }

    // Fake names, picked when a chunk is built (never in draw). The same real name always gets
    // the same fake one during a launch, so a street keeps its name from one chunk to the next.
    public static String fakeStreet(String real) {
        return pick(real, STREETS);
    }

    public static String fakeRef(String real) {
        return pick(real, REFS);
    }

    public static String fakePlace(String real) {
        return pick(real, PLACES);
    }

    public static String fakeBuoy(String real) {
        return pick(real, BUOYS);
    }

    private static String pick(String real, String[] names) {
        if( (real == null) || (real.length() == 0) )
            return real;

        // A title without any letter says nothing of the place (a speed limit, a number): kept.
        boolean hasLetter = false;
        for( int i = 0; i < real.length(); i++ ) {
            if( Character.isLetter(real.charAt(i)) ) {
                hasLetter = true;
                break;
            }
        }
        if( !hasLetter )
            return real;

        int index = ((real.hashCode() ^ SEED) & 0x7fffffff) % names.length;
        return names[index];
    }
}
