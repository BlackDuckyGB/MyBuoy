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

/*
 * A place whose tides have been read, kept from one run to the next.
 *
 * The tide model covers the open sea and stops short of the coast, so a user standing
 * inland or deep inside a bay gets nothing at all. The last place that did answer is worth
 * remembering: it is almost always the harbour or the stretch of water the question was
 * really about, and offering it again saves searching for it a second time.
 */
package com.gb.mybuoy.tides;

import java.util.Locale;

/** Where the tide was last looked at, and under which name. */
public final class TidePlace
{
    private final double _latitude;
    private final double _longitude;

    /** The name it was found under, or null when it is simply where the user stood. */
    private final String _name;

    private final long _seenAtUnixSeconds;

    public TidePlace(double latitude, double longitude, String name, long seenAtUnixSeconds)
    {
        _latitude = latitude;
        _longitude = longitude;
        _name = name;
        _seenAtUnixSeconds = seenAtUnixSeconds;
    }

    public double latitude()
    {
        return _latitude;
    }

    public double longitude()
    {
        return _longitude;
    }

    public String name()
    {
        return _name;
    }

    public long seenAtUnixSeconds()
    {
        return _seenAtUnixSeconds;
    }

    /** What to put on a button: the name when there is one, the position otherwise. */
    public String label()
    {
        if( (_name != null) && (_name.trim().length() > 0) )
        {
            return _name.trim();
        }

        return String.format(Locale.UK, "%.4f%s %.4f%s",
                Math.abs(_latitude), _latitude >= 0.0 ? "N" : "S",
                Math.abs(_longitude), _longitude >= 0.0 ? "E" : "W");
    }
}
