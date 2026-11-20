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

/**
 * Thin leveled wrapper over {@link android.util.Log}. Adds an in-app minimum level
 * (flipped by the "Debug mode" parameter) WITHOUT hiding anything from the standard
 * Android tooling: every call still delegates to Log.d/i/w/e with the same tag and
 * level, so `adb shell setprop log.tag.&lt;TAG&gt; DEBUG` + logcat keep working exactly
 * as before. A message is emitted when the in-app level allows it OR when isLoggable()
 * (i.e. a setprop) allows it.
 */
public final class GBLog {

    // Default = Information: DEBUG/VERBOSE suppressed in-app unless Debug mode is on
    // (or a setprop lowers the tag level). WARN/ERROR always pass.
    private static volatile int _minLevel = Log.INFO;

    private GBLog() {}

    /** Wired to the "Debug mode" parameter: on => DEBUG (verbose), off => INFO. */
    public static void setDebug(boolean on) {
        _minLevel = on ? Log.DEBUG : Log.INFO;
    }

    public static boolean isDebug() {
        return _minLevel <= Log.DEBUG;
    }

    // Emit if the in-app level allows it, OR if an external setprop does (isLoggable).
    // isLoggable throws for tags > 23 chars on API <= 25 -> guarded (some tags are FQCN).
    private static boolean on(String tag, int level) {
        if (level >= _minLevel)
            return true;
        try {
            return Log.isLoggable(tag, level);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static void v(String tag, String msg)              { if (on(tag, Log.VERBOSE)) Log.v(tag, msg); }
    public static void v(String tag, String msg, Throwable t) { if (on(tag, Log.VERBOSE)) Log.v(tag, msg, t); }
    public static void d(String tag, String msg)              { if (on(tag, Log.DEBUG))   Log.d(tag, msg); }
    public static void d(String tag, String msg, Throwable t) { if (on(tag, Log.DEBUG))   Log.d(tag, msg, t); }
    public static void i(String tag, String msg)              { if (on(tag, Log.INFO))    Log.i(tag, msg); }
    public static void i(String tag, String msg, Throwable t) { if (on(tag, Log.INFO))    Log.i(tag, msg, t); }
    public static void w(String tag, String msg)              { if (on(tag, Log.WARN))    Log.w(tag, msg); }
    public static void w(String tag, String msg, Throwable t) { if (on(tag, Log.WARN))    Log.w(tag, msg, t); }
    public static void e(String tag, String msg)              { if (on(tag, Log.ERROR))   Log.e(tag, msg); }
    public static void e(String tag, String msg, Throwable t) { if (on(tag, Log.ERROR))   Log.e(tag, msg, t); }
}
