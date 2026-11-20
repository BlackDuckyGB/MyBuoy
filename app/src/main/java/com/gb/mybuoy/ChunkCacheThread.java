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

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * #86, step 1: the one thread allowed to touch the chunk cache folder.
 *
 * WHY A THREAD OF ITS OWN: sweeping the folder and adding up file sizes takes as long as the disk
 * decides. Hung on one of AppManager's 1 Hz loops it would hold up chunk loading, and run from the
 * UI thread it would freeze the Maintenance page.
 *
 * HOW IT IS DRIVEN: a single shared command, guarded by one monitor.
 *
 *   - DefaultValue means "nothing asked for": the thread wakes every WAIT_MS and trims the cache
 *     down to the size limit, should it be over it.
 *   - The Maintenance page writes one of the four purge commands, but ONLY while the command sits
 *     at DefaultValue -- a purge already asked for is never overwritten, and the page is told so.
 *   - Writing the command notifies the monitor, which breaks the wait: nothing is ever delayed by
 *     five minutes. The size setting notifies it too, so a smaller limit applies at once.
 *   - Once the work is done, the thread puts the command back to DefaultValue UNDER THE LOCK, and
 *     only if it is still the one it ran: should the page have queued another purge while the
 *     limit-based cleanup was running -- which is allowed, the command was at DefaultValue -- that
 *     new one is left in place and served on the spot, without waiting.
 *
 * POSITION: nothing is purged before a REAL GPS fix is in. The position restored from the
 * preferences at startup can be hours and hundreds of kilometres old, and the distance criterion
 * would then throw away exactly the areas one is about to cross.
 */
public class ChunkCacheThread {

    private static final String TAG = "ChunkCacheThread";

    // The command, shared between the UI and this thread.
    public static final int CMD_DEFAULT     = 0;   // no request: cleanup by size limit
    public static final int CMD_PURGE_FAR   = 1;
    public static final int CMD_PURGE_MONTH = 2;
    public static final int CMD_PURGE_WEEK  = 3;
    public static final int CMD_PURGE_ALL   = 4;
    public static final int CMD_STATS       = 5;   // no work: only the count below, for the page

    private static final long WAIT_MS = 5 * 60 * 1000L;

    // Areas closer than this are the ones worth keeping: they are the last to go, and only once
    // everything farther away has already been dropped.
    public static final double NEAR_KM = 50.0;

    private static final int PURGE_MONTH_DAYS = 30;
    private static final int PURGE_WEEK_DAYS  = 7;

    private static final Object _monitor = new Object();

    private static int _command = CMD_DEFAULT;          // guarded by _monitor
    private static volatile boolean _running = false;
    private static Thread _thread = null;

    private ChunkCacheThread() {
    }

    /**
     * Started by AppManager.startAllThreadsOnce() alongside the other background threads, exactly
     * like GeoSearch: not on the first purge, which would make the shutdown impossible to control.
     */
    public static synchronized void start() {

        if( _running && (_thread != null) && _thread.isAlive() )
            return;

        _running = true;
        _thread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "ChunkCache");

        // Daemon: even if the clean shutdown below were ever missed, this thread would not hold the
        // process alive.
        _thread.setDaemon(true);
        _thread.start();

        GBLog.d(TAG, "cache thread started");
    }

    /**
     * Not called by the app any more: the thread lives with the process (see MainActivity.onDestroy).
     * Kept for a controlled shutdown, should one ever be needed.
     */
    public static synchronized void stop() {

        if( !_running )
            return;

        _running = false;

        synchronized( _monitor ) {
            _monitor.notifyAll();       // breaks the wait so the thread sees _running and leaves
        }

        _thread = null;

        GBLog.d(TAG, "cache thread stopped");
    }

    /**
     * Asks for one of the four purges. Returns false when a request is already in flight: the
     * command is then left untouched and the caller says so rather than silently dropping it.
     * The monitor is notified either way -- it costs nothing, and a wake-up with nothing to do is
     * harmless.
     */
    public static boolean request(int command) {

        synchronized( _monitor ) {
            if( _command != CMD_DEFAULT ) {
                _monitor.notifyAll();
                return false;
            }

            _command = command;
            _monitor.notifyAll();
        }

        return true;
    }

    /** Wakes the thread without asking for anything -- the size limit has just changed. */
    public static void wake() {
        synchronized( _monitor ) {
            _monitor.notifyAll();
        }
    }

    public static boolean isBusy() {
        synchronized( _monitor ) {
            return _command != CMD_DEFAULT;
        }
    }

    // ---- The loop -----------------------------------------------------------------------------

    private static void loop() {

        while( _running ) {
            int command;

            synchronized( _monitor ) {
                // The command is read BEFORE waiting, under the same monitor that the UI writes it
                // under: one posted while the thread was working is seen right here, so no wake-up
                // can ever be missed.
                if( (_command == CMD_DEFAULT) && _running ) {
                    try {
                        _monitor.wait(WAIT_MS);
                    } catch( InterruptedException e ) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                command = _command;
            }

            if( !_running )
                return;

            // The long part, run OUTSIDE the lock: the UI must never block on a folder sweep.
            // Nothing restarts this thread once the process is up, so no failure may end it: the
            // command is still put back below, and the next request is served normally.
            try {
                run(command);
            } catch( RuntimeException e ) {
                GBLog.e(TAG, "cache work failed, thread goes on: " + e, e);
                ViewsController.notifyError("Map data cleanup failed: " + e.getMessage());
            }

            synchronized( _monitor ) {
                // Still the command we ran: back to DefaultValue. Something else in the meantime:
                // leave it, the next turn serves it without waiting.
                if( _command == command )
                    _command = CMD_DEFAULT;
            }

            // Out of the lock, and HERE rather than on the UI thread: counting the files takes as
            // long as the disk decides (982 of them on the SD card, measured, when the page was
            // slow to open). Only done while the page is open.
            if( ViewsController.isMaintenancePageOpen() ) {
                try {
                    Context context = AppManager.getInstance().getContext();
                    if( context != null )
                        ViewsController.refreshMaintenancePage(ChunkCache.stats(context));
                } catch( RuntimeException e ) {
                    GBLog.e(TAG, "cache count failed, thread goes on: " + e, e);
                }
            }
        }
    }

    private static void run(int command) {

        Context context = AppManager.getInstance().getContext();
        if( context == null )
            return;

        switch( command ) {
            case CMD_PURGE_ALL:
                report(ChunkCache.purgeAll(context));
                break;

            case CMD_PURGE_MONTH:
                report(ChunkCache.purgeOlderThan(context, PURGE_MONTH_DAYS));
                break;

            case CMD_PURGE_WEEK:
                report(ChunkCache.purgeOlderThan(context, PURGE_WEEK_DAYS));
                break;

            case CMD_STATS:
                break;

            case CMD_PURGE_FAR:
                if( !AppManager.getInstance().getHasFix() ) {
                    ViewsController.notifyWarn("No GPS fix yet: cannot tell which areas are far away.");
                    break;
                }
                report(ChunkCache.purgeFartherThan(context, NEAR_KM,
                        AppManager.getInstance().getLatitude(), AppManager.getInstance().getLongitude()));
                break;

            default:
                trimToLimit(context);
                break;
        }
    }

    private static void report(int removed) {
        ViewsController.notifyInfo((removed == 0)
                ? "Nothing to clear."
                : String.format(Locale.US, "%d area%s cleared.", removed, (removed > 1) ? "s" : ""));
    }

    // ---- Cleanup by size limit -----------------------------------------------------------------

    /**
     * Brings the cache back under the limit the user picked. Farthest first, and among those the
     * least recently used first; the areas within NEAR_KM are only touched once everything else has
     * gone, and that case is worth a warning: the limit is then too small for where one lives.
     */
    private static void trimToLimit(Context context) {

        AppManager am = AppManager.getInstance();

        // No real fix, no cleanup: the restored position would send the distance criterion after
        // the very areas we are about to need.
        if( !am.getHasFix() )
            return;

        long limit = am.getCacheLimitBytes();
        double latitude = am.getLatitude();
        double longitude = am.getLongitude();

        List<Entry> entries = new ArrayList<>();
        long total = 0;

        for( File dir : ChunkCache.allDirs(context) ) {
            for( File file : ChunkCache.entries(dir) ) {
                if( !file.isFile() )
                    continue;

                Entry entry = new Entry(file, latitude, longitude);
                entries.add(entry);
                total += entry.bytes;
            }
        }

        if( total <= limit )
            return;

        Collections.sort(entries, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                // Far ones go first, then the oldest use.
                if( a.far != b.far )
                    return a.far ? -1 : 1;

                return Long.compare(a.usedMs, b.usedMs);
            }
        });

        int removed = 0;
        int removedNear = 0;

        for( Entry entry : entries ) {
            if( total <= limit )
                break;

            if( !entry.file.delete() )
                continue;

            total -= entry.bytes;
            removed++;

            if( !entry.far )
                removedNear++;
        }

        if( removed == 0 )
            return;

        GBLog.i(TAG, String.format(Locale.ROOT, "trim removed %d file(s), %d of them nearby",
                removed, removedNear));

        if( removedNear > 0 ) {
            ViewsController.notifyWarn(String.format(Locale.US,
                    "Map data cache full: %d area%s dropped, %d of them nearby. A bigger cache size would help.",
                    removed, (removed > 1) ? "s" : "", removedNear));
        } else {
            ViewsController.notifyInfo(String.format(Locale.US,
                    "Map data cache trimmed: %d distant area%s dropped.",
                    removed, (removed > 1) ? "s" : ""));
        }
    }

    // One cached file, with what the sort needs: how big, when it last served, and whether it lies
    // within NEAR_KM of where we are. An entry whose name cannot be read counts as far away -- it is
    // not one of ours, so it goes first.
    private static class Entry {
        final File file;
        final long bytes;
        final long usedMs;
        final boolean far;

        Entry(File file, double latitude, double longitude) {
            this.file = file;
            this.bytes = file.length();
            this.usedMs = file.lastModified();

            double[] bbox = ChunkCache.parseBbox(file.getName());

            // A name that cannot be read, or one written by an older query (see
            // ChunkCache.SOURCE_OVERPASS), is never served again: it goes with the far ones, first.
            if( (bbox == null) || !ChunkCache.isCurrentSource(file.getName()) ) {
                this.far = true;
            } else {
                double centreLat = (bbox[0] + bbox[2]) / 2;
                double centreLon = (bbox[1] + bbox[3]) / 2;
                this.far = Chunk.measureKm(latitude, longitude, centreLat, centreLon) > NEAR_KM;
            }
        }
    }
}
