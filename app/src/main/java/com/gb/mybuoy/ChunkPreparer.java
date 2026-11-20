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

import java.util.concurrent.BlockingQueue;

/**
 * #87: builds the chunks that need no network -- a cached area, or an OFFLINE chunk (the coastline
 * from land_polygons, no OSM data) -- away from the supervisor loop.
 *
 * WHY: the supervisor did that work itself, and it is not small. Measured on the emulator for a
 * coastal area: reading and parsing the cached JSON ~1.3 s, dbChunk() 0.46 s, then
 * contextScaleObjects() -- the screen coordinates of every road, area and mark -- ~4.6 s. For those
 * ~7 s the supervisor did not look at the position at all, which is precisely what #87 must never
 * allow. The chunk now reaches the supervisor fully built, scaled for the surface, ready to be put
 * on screen in a few milliseconds.
 *
 * ONE REQUEST AT A TIME, THE LATEST ONE, like ChunkDownloader. The chunk is centred on the position
 * when the work STARTS, not when it was asked for.
 *
 * Lives with the process, like every background thread of the app.
 */
public class ChunkPreparer {

    private static final String TAG = "ChunkPreparer";

    /** What the supervisor asks for. */
    public static class Request {
        final int reloadOrigin;         // AppManager.RELOAD_*, for the "Chunk reloaded!" message
        final boolean publishOnArrival; // show it as soon as it comes, whatever it is
        final Chunk onScreen;           // excluded as a non-optimum candidate (see ChunkCache.best)

        public Request(int reloadOrigin, boolean publishOnArrival, Chunk onScreen) {
            this.reloadOrigin = reloadOrigin;
            this.publishOnArrival = publishOnArrival;
            this.onScreen = onScreen;
        }
    }

    /** A chunk ready to show: optimum, or a fallback flagged incomplete. Never null. */
    public static class Result {
        public final Request request;
        public final Chunk chunk;
        public final boolean optimum;
        public final long tookMs;       // shown on the debug panel: "cache 1700 ms"

        Result(Request request, Chunk chunk, boolean optimum, long tookMs) {
            this.request = request;
            this.chunk = chunk;
            this.optimum = optimum;
            this.tookMs = tookMs;
        }
    }

    private final BlockingQueue<Object> _toSupervisor;
    private final ChunkDownloader _downloader;     // to skip the file it is producing
    private final Object _monitor = new Object();

    private Request _pending = null;        // guarded by _monitor
    private boolean _inProgress = false;    // guarded by _monitor

    private Thread _thread = null;

    public ChunkPreparer(BlockingQueue<Object> toSupervisor, ChunkDownloader downloader) {
        _toSupervisor = toSupervisor;
        _downloader = downloader;
    }

    public synchronized void start() {
        if( (_thread != null) && _thread.isAlive() )
            return;

        _thread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "ChunkPreparer");

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

    public boolean isBusy() {
        synchronized( _monitor ) {
            return (_pending != null) || _inProgress;
        }
    }

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

            Result result = null;

            try {
                result = prepare(request);
            } catch( Throwable e ) {
                // Nothing may end this thread: the supervisor asks again on its next tick.
                GBLog.e(TAG, "preparation failed: " + e, e);
                MemLog.logCrash(e);
            } finally {
                synchronized( _monitor ) {
                    _inProgress = false;
                }
            }

            if( result != null )
                _toSupervisor.offer(result);
        }
    }

    private Result prepare(Request request) {

        AppManager am = AppManager.getInstance();
        Context context = am.getContext();

        double[] now = am.chunkRequestSnapshot();
        double lat = now[0];
        double lon = now[1];
        double speed = now[2];

        long started = android.os.SystemClock.uptimeMillis();

        String source = Chunk.usesOverpassApi() ? ChunkCache.SOURCE_OVERPASS : ChunkCache.SOURCE_OSM;

        long tBest = android.os.SystemClock.uptimeMillis();
        long tLoad = 0;
        long tDb = 0;

        ChunkCache.Candidate candidate = ChunkCache.best(context, source, lat, lon,
                Chunk.filterLevelForSpeed(speed), false, request.onScreen, _downloader.inFlightName());

        Chunk chunk = null;
        boolean optimum = false;

        long tBestEnd = android.os.SystemClock.uptimeMillis();

        if( candidate != null ) {
            try {
                // dbChunk() FIRST, as ChunkDownloader does: loadFromCache() sorts the areas, and the
                // EARTH_LAND added after that sort was drawn last, over every lake, field and forest.
                long t = android.os.SystemClock.uptimeMillis();
                candidate.chunk.dbChunk();
                tDb = android.os.SystemClock.uptimeMillis() - t;
                t = android.os.SystemClock.uptimeMillis();
                candidate.chunk.loadFromCache(candidate.file, candidate.level);
                tLoad = android.os.SystemClock.uptimeMillis() - t;

                chunk = candidate.chunk;
                optimum = candidate.optimum;

                chunk.setIncomplete(!optimum);
                chunk.setOrigin(optimum ? "cache" : "cache-incomplete");
            } catch( Exception e ) {
                GBLog.e(TAG, "cache entry unusable, offline instead: " + e);
                chunk = null;
            }
        }

        if( chunk == null ) {
            chunk = new Chunk(lat, lon, speed);

            try {
                long t = android.os.SystemClock.uptimeMillis();
                chunk.dbChunk();
                tDb = android.os.SystemClock.uptimeMillis() - t;
            } catch( Exception e ) {
                // An empty chunk around the fix is still better than a map frozen on an area left.
                GBLog.e(TAG, "offline chunk without coastline: " + e);
            }

            chunk.setIncomplete(true);
            chunk.setOrigin("offline");
        }

        // The screen coordinates of every object, computed HERE rather than at publication (see the
        // class comment). setGameSurface() does nothing more when the surface is already the one set.
        long tScale = android.os.SystemClock.uptimeMillis();
        chunk.setGameSurface(am.getGameSurface());
        tScale = android.os.SystemClock.uptimeMillis() - tScale;

        MemLog.logEvent(String.format(java.util.Locale.ROOT,
                "PREP %s best=%d load=%d db=%d scale=%d ms %s",
                chunk.getOrigin(), tBestEnd - tBest, tLoad, tDb, tScale, chunk.bboxString()));

        long tookMs = android.os.SystemClock.uptimeMillis() - started;

        GBLog.i(TAG, String.format(java.util.Locale.ROOT, "prepared %s in %d ms: %s",
                chunk.getOrigin(), tookMs, chunk.bboxString()));

        return new Result(request, chunk, optimum, tookMs);
    }
}
