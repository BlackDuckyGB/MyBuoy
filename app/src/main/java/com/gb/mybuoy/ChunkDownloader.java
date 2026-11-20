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

import android.os.SystemClock;

import java.util.Locale;
import java.util.concurrent.BlockingQueue;

/**
 * #87: the thread that talks to the map data server, and nothing else.
 *
 * WHY: the loading loop used to download in line. A download sits through the Overpass fair-use
 * throttle, the wait for a free slot, then a connection that can take 15 s to fail and a body read
 * that can take 20 more -- and during all that time the loop could not notice that the position was
 * leaving the chunk on screen. At sea or in a dead zone the map froze on an area already left.
 * The loop (AppManager's supervisor) now never blocks on the network: it hands a request to this
 * thread and gets the result back through its queue, which also wakes it up at once.
 *
 * ONE REQUEST AT A TIME, THE LATEST ONE: a new request replaces the one still waiting, never the
 * one in progress. The chunk is centred on the position at the moment it LEAVES, after the fair-use
 * waits, not when it was asked for -- the position may have moved a lot in between.
 *
 * NO RETRY HERE: a failure is posted back like a success. The supervisor asks again on its next
 * turn if it still needs a chunk, which is the "retry as soon as possible" the loop always had,
 * the Overpass status check deciding the actual pace.
 *
 * Like every background thread of the app, it lives with the process and is never stopped.
 */
public class ChunkDownloader {

    private static final String TAG = "ChunkDownloader";

    // Minimum delay between two Overpass queries, whatever the speed. Counted from the last
    // SUCCESSFUL download, as it always was; a request after a jump skips it (see Request.bigJump).
    private static final long MIN_QUERY_INTERVAL_MS = 25000;

    /** What the supervisor asks for. */
    public static class Request {
        final int reloadOrigin;     // AppManager.RELOAD_*, RELOAD_NONE for an ordinary need
        final boolean bigJump;      // the position is outside the chunk on screen: no throttle

        public Request(int reloadOrigin, boolean bigJump) {
            this.reloadOrigin = reloadOrigin;
            this.bigJump = bigJump;
        }
    }

    /** What comes back through the supervisor's queue: a filled chunk, or the failure. */
    public static class Result {
        public final Request request;
        public final Chunk chunk;           // null on failure
        public final Throwable error;       // null on success

        Result(Request request, Chunk chunk, Throwable error) {
            this.request = request;
            this.chunk = chunk;
            this.error = error;
        }
    }

    private final BlockingQueue<Object> _toSupervisor;
    private final Object _monitor = new Object();

    private Request _pending = null;        // guarded by _monitor
    private boolean _inProgress = false;    // guarded by _monitor
    private long _lastQueryMs = 0;          // only touched by this thread

    private Thread _thread = null;

    // Cache file name of the answer being downloaded, until the supervisor has taken the result
    // (see ChunkPreparer, which must not read that file twice).
    private volatile String _inFlightName = null;

    public String inFlightName() {
        return _inFlightName;
    }

    /** Called by the supervisor once it has handled a result: that file is fair game again. */
    public void resultTaken() {
        _inFlightName = null;
    }

    public ChunkDownloader(BlockingQueue<Object> toSupervisor) {
        _toSupervisor = toSupervisor;
    }

    public synchronized void start() {
        if( (_thread != null) && _thread.isAlive() )
            return;

        _thread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "ChunkDownloader");

        // Daemon: it never holds the process alive.
        _thread.setDaemon(true);
        _thread.start();
    }

    /** Queues a request, replacing one that is still waiting. */
    public void submit(Request request) {
        synchronized( _monitor ) {
            _pending = request;
            _monitor.notifyAll();
        }
    }

    /** True while a request is waiting or being served. */
    public boolean isBusy() {
        synchronized( _monitor ) {
            return (_pending != null) || _inProgress;
        }
    }

    // ---- The loop ----------------------------------------------------------------------------

    private void loop() {

        while( true ) {
            Request request;

            synchronized( _monitor ) {
                while( _pending == null ) {
                    try {
                        _monitor.wait();
                    } catch( InterruptedException e ) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                request = _pending;
                _pending = null;
                _inProgress = true;
            }

            Result result;
            long started = SystemClock.uptimeMillis();

            try {
                result = new Result(request, download(request), null);
            } catch( InterruptedException e ) {
                Thread.currentThread().interrupt();
                return;
            } catch( OutOfMemoryError e ) {
                // An Error, not an Exception: without this the thread would die for good. The failed
                // allocation is rolled back; the supervisor asks again on its next turn.
                MemLog.logCrash(e);
                MemLog.logEvent("DL failed after " + (SystemClock.uptimeMillis() - started) + " ms: " + e);
                result = new Result(request, null, e);
            } catch( Exception e ) {
                MemLog.logEvent("DL failed after " + (SystemClock.uptimeMillis() - started) + " ms: " + e);
                result = new Result(request, null, e);
            } finally {
                synchronized( _monitor ) {
                    _inProgress = false;
                }
            }

            // offer() on an unbounded queue never fails; it also wakes the supervisor at once.
            _toSupervisor.offer(result);
        }
    }

    private Chunk download(Request request) throws Exception {

        AppManager am = AppManager.getInstance();

        long t0 = SystemClock.uptimeMillis();

        if( Chunk.usesOverpassApi() ) {
            // A jump (restart far from the restored spot, position outside the chunk on screen)
            // must not wait for the throttle.
            if( request.bigJump )
                _lastQueryMs = 0;

            long sinceLast = System.currentTimeMillis() - _lastQueryMs;
            if( sinceLast < MIN_QUERY_INTERVAL_MS ) {
                long wait = MIN_QUERY_INTERVAL_MS - sinceLast;
                GBLog.i(TAG, "throttle: " + (wait / 1000) + "s before next query");
                Thread.sleep(wait);
            }

            // Fair-use: don't query while our 2 IP slots are cooling down. slotWaitSeconds() throws
            // when the status cannot be read (fail closed): that failure goes back to the supervisor
            // like any other, which asks again on its next turn.
            double[] probe = am.chunkRequestSnapshot();
            Chunk statusProbe = new Chunk(probe[0], probe[1], probe[2]);
            int cool;
            while( (cool = statusProbe.slotWaitSeconds()) > 0 ) {
                GBLog.i(TAG, "Overpass cool-down " + cool + "s, wait");
                Thread.sleep((cool + 2) * 1000L);
            }
        }

        // Centred on where we are NOW: the waits above may have lasted long enough to matter.
        double[] now = am.chunkRequestSnapshot();
        Chunk chunk = new Chunk(now[0], now[1], now[2]);

        _inFlightName = ChunkCache.fileName(
                Chunk.usesOverpassApi() ? ChunkCache.SOURCE_OVERPASS : ChunkCache.SOURCE_OSM,
                Chunk.filterLevelForSpeed(now[2]),
                chunk.latitudeS, chunk.longitudeW, chunk.latitudeN, chunk.longitudeE);

        long tDb = SystemClock.uptimeMillis();
        chunk.dbChunk();
        long tDl = SystemClock.uptimeMillis();
        chunk.dlChunk();
        long tScale = SystemClock.uptimeMillis();

        _lastQueryMs = System.currentTimeMillis();   // after the download: its own duration does not count

        // Screen coordinates computed here, not by the supervisor (~4.6 s measured for a coastal
        // chunk, see ChunkPreparer).
        chunk.setGameSurface(am.getGameSurface());
        long tEnd = SystemClock.uptimeMillis();

        chunk.setOrigin("online");

        MemLog.logEvent(String.format(Locale.ROOT, "DL ok wait=%d db=%d net+parse=%d scale=%d ms %s",
                tDb - t0, tDl - tDb, tScale - tDl, tEnd - tScale, chunk.bboxString()));

        return chunk;
    }
}
