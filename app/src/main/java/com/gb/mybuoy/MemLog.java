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

import android.os.Debug;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;

/**
 * Lightweight heap probe for leak-hunting. Logs to logcat (tag MEMLOG) and,
 * once {@link #init} has been called, appends every sample to a file on the
 * device so the growth curve survives a buffer rotation or a crash in the field.
 */
final class MemLog {
    static final String TAG = "MEMLOG";
    private static File logFile;
    // Auto heap-dump: fire ONCE per process when we get close to the ceiling, so a
    // field OOM is captured for offline dominator analysis (adb pull the .hprof).
    private static boolean hprofDumped = false;
    private static final int HPROF_TRIGGER_PCT = 88;

    private MemLog() {}

    /** Called once at startup with the app's files dir. Gated on Debug mode (#36):
     * off => no mem log file, no heap sampling, no auto hprof dump. Enabling Debug at
     * runtime takes full effect for MemLog on the next launch (logFile stays unset). */
    static void init(File dir) {
        if (!GBLog.isDebug()) return;
        if (dir != null) {
            logFile = new File(dir, "mybuoy_mem.log");
        }
    }

    static void log(String where) {
        if (!GBLog.isDebug()) return;
        Runtime rt   = Runtime.getRuntime();
        long maxKB   = rt.maxMemory()   / 1024;
        long usedKB  = (rt.totalMemory() - rt.freeMemory()) / 1024;
        long nativeKB = Debug.getNativeHeapAllocatedSize() / 1024;
        String msg = String.format(Locale.US,
                "%-20s javaUsed=%,dKB / max=%,dKB (%.0f%%)  native=%,dKB",
                where, usedKB, maxKB, 100.0 * usedKB / maxKB, nativeKB);
        Log.i(TAG, msg);
        writeFile(msg);

        if (!hprofDumped && maxKB > 0 && (usedKB * 100 / maxKB) >= HPROF_TRIGGER_PCT) {
            hprofDumped = true;
            dumpHeap();
        }
    }

    /** One-shot heap dump next to the mem log. Heavy (~heap-sized file, ~1-2s pause). */
    private static void dumpHeap() {
        if (logFile == null) return;
        try {
            File hprof = new File(logFile.getParentFile(), "mybuoy_heap.hprof");
            Debug.dumpHprofData(hprof.getAbsolutePath());
            writeFile("*** HPROF dumped -> " + hprof.getAbsolutePath() + " ***");
        } catch (Throwable t) {
            writeFile("*** HPROF dump FAILED: " + t + " ***");
        }
    }

    /**
     * Record a plain event, without the heap sample. Meant for things that must stay
     * explainable the day after a field test: download failures, empty results, HTTP
     * codes. Logcat rotates and is gone by then; this file is not.
     */
    static void logEvent(String msg) {
        if (!GBLog.isDebug()) return;
        Log.i(TAG, msg);
        writeFile(msg);
    }

    /** Record the fatal exception into the same file before the process dies. */
    static void logCrash(Throwable t) {
        if (!GBLog.isDebug()) return;
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        writeFile("*** CRASH ***\n" + sw);
    }

    private static synchronized void writeFile(String msg) {
        if (logFile == null) return;
        try (FileWriter w = new FileWriter(logFile, true)) {
            w.write(System.currentTimeMillis() + " " + msg + "\n");
        } catch (Exception ignore) {}
    }
}
