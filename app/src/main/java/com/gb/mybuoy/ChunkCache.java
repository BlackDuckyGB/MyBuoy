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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/**
 * #86, step 1: on-disk cache of the raw Overpass (or OSM) answer of a chunk.
 *
 * WHAT IS STORED: the JSON answer itself, written while it streams in, so nothing is ever held in
 * memory -- a city chunk weighs tens of megabytes (see Chunk.dlChunkHttp). Only the Maintenance page
 * and ChunkCacheThread ever delete anything here; see writeDir() on why this is not the system's
 * cache folder.
 *
 * THE FILE NAME carries everything needed to find an entry again and to purge it, so no index file
 * has to be kept in step with the folder:
 *
 *     ovp_F1_47.512345_-2.876543_47.531234_-2.851234.json
 *     |   |  `- bounding box, south, west, north, east, in the "%.6f" form used all over Chunk
 *     |   `---- filter level (1..5): the very query changes with it, F5 asks for neither minor
 *     |         roads nor landuse, so an F5 answer must never be served to a walker
 *     `-------- source: the OSM v0.6 API and Overpass do not return the same document
 *
 * LAST USE is carried by the file's own lastModified date, which is rewritten on every read
 * (touch()). Android gives no reliable access time: java.io.File does not expose one, NIO has one
 * only from API 26 while the app goes down to 21, and the volumes are mounted relatime/noatime so
 * it would not be refreshed anyway. Rewriting it ourselves makes the date mean exactly "last time
 * this entry served", which is what the purges need.
 */
public class ChunkCache {

    private static final String TAG = "ChunkCache";

    // The source doubles as a QUERY VERSION: a cached answer is only valid for the query that
    // produced it. "ovp2" since the natural=* relation filter was narrowed (#86): the "ovp" files
    // written before carry the whole Bay of Biscay and must never be served again. Bump it
    // whenever buildChunkQl() changes what an answer contains.
    public static final String SOURCE_OVERPASS = "ovp2";
    public static final String SOURCE_OSM      = "osm";

    // Sources the current code writes. Anything else in the folder is a leftover from an older
    // query, and goes first when the cache has to shrink.
    public static boolean isCurrentSource(String name) {
        return (name != null)
                && (name.startsWith(SOURCE_OVERPASS + "_") || name.startsWith(SOURCE_OSM + "_"));
    }

    private static final String SUB_DIR   = "overpass";
    private static final String EXTENSION = ".json";
    private static final String PART_EXT  = ".part";

    // A chunk download is streamed, so its final size is unknown: this is the headroom asked of a
    // volume before writing to it, generous enough for a dense city answer.
    private static final long WRITE_HEADROOM_BYTES = 200L * 1024 * 1024;

    private ChunkCache() {
    }

    // ---- Where the cache lives -------------------------------------------------------------

    /**
     * The folder new entries are written to: Android/data/<package>/files/overpass on the volume the
     * user prefers, internal by default and the SD card when the developer setting asks for it
     * (AppManager.getPreferSdStorage). When the preferred volume is missing or short on space, the
     * other one is used instead rather than losing the entry.
     *
     * NOT getExternalCacheDir(), on purpose, however tempting the name: that folder is declared to
     * the system as disposable, and DeviceStorageMonitorService has installd wipe the WHOLE subtree
     * as soon as storage runs low -- measured on the emulator, the folder was gone. These files
     * exist precisely so that data is not downloaded again over mobile data and against the
     * Overpass fair-use, so nothing but the Maintenance page and the cleanup thread may remove them.
     */
    public static File writeDir(Context context) {
        File[] dirs = cacheRoots(context);
        if( dirs == null )
            return null;

        File chosen = AppManager.getInstance().preferredDir(dirs, WRITE_HEADROOM_BYTES);

        // preferredDir() hands back the preferred folder even when NO volume has the headroom, so
        // that callers fail on writing rather than on a null. The cache must not: measured on the
        // emulator, writing anyway filled the disk to ENOSPC. Below the headroom, nothing is
        // cached and the download goes on as it always did.
        if( (chosen == null) || (chosen.getUsableSpace() <= WRITE_HEADROOM_BYTES) ) {
            warnNoRoomOnce();
            return null;
        }

        _noRoomWarned = false;

        return ensureDir(new File(chosen, SUB_DIR));
    }

    // Said once, not on every chunk; said again if room came back and ran out a second time.
    private static volatile boolean _noRoomWarned = false;

    private static void warnNoRoomOnce() {
        if( _noRoomWarned )
            return;

        _noRoomWarned = true;
        GBLog.i(TAG, "cache paused: less than the headroom left on every volume");
        ViewsController.notifyWarn("Storage almost full: downloaded map data is no longer kept.");
    }

    /**
     * Every folder the cache may hold entries in, whatever the current preference -- the purges
     * have to sweep both volumes, and so does the reading side: an entry written before the
     * setting was flipped is still perfectly good.
     */
    public static File[] allDirs(Context context) {
        File[] roots = cacheRoots(context);
        if( roots == null )
            return new File[0];

        int count = 0;
        File[] dirs = new File[roots.length];

        for( File root : roots ) {
            if( root != null )
                dirs[count++] = new File(root, SUB_DIR);
        }

        File[] trimmed = new File[count];
        System.arraycopy(dirs, 0, trimmed, 0, count);

        return trimmed;
    }

    // getExternalFilesDirs returns the primary (emulated internal) volume first, then the secondary
    // ones, the SD card among them. A slot can be null when a volume is being ejected.
    private static File[] cacheRoots(Context context) {
        if( context == null )
            return null;

        File[] dirs = context.getExternalFilesDirs(null);
        if( (dirs == null) || (dirs.length == 0) ) {
            File single = context.getExternalFilesDir(null);
            return (single == null) ? null : new File[]{ single };
        }

        return dirs;
    }

    private static File ensureDir(File dir) {
        if( dir == null )
            return null;

        if( !dir.exists() && !dir.mkdirs() )
            return null;

        return dir.isDirectory() ? dir : null;
    }

    // ---- Naming ------------------------------------------------------------------------------

    // Same "%.6f" as everywhere else in Chunk (about 0.1 m), and Locale.ROOT so a French phone
    // writes a dot and not a comma -- a comma would make the name unreadable by parseName().
    public static String fileName(String source, int level,
                                  double latitudeS, double longitudeW,
                                  double latitudeN, double longitudeE) {
        return String.format(Locale.ROOT, "%s_F%d_%.6f_%.6f_%.6f_%.6f%s",
                source, level, latitudeS, longitudeW, latitudeN, longitudeE, EXTENSION);
    }

    public static File entryFile(Context context, String source, int level,
                                 double latitudeS, double longitudeW,
                                 double latitudeN, double longitudeE) {
        File dir = writeDir(context);
        if( dir == null )
            return null;

        return new File(dir, fileName(source, level, latitudeS, longitudeW, latitudeN, longitudeE));
    }

    /**
     * Bounding box read back from a file name, as { latS, lonW, latN, lonE }, or null when the name
     * is not one of ours. Used by the purges, which work from the folder alone.
     */
    public static double[] parseBbox(String name) {
        if( (name == null) || !name.endsWith(EXTENSION) )
            return null;

        String base = name.substring(0, name.length() - EXTENSION.length());
        String[] parts = base.split("_");

        // source, level, then the four numbers
        if( parts.length != 6 )
            return null;

        try {
            double[] bbox = new double[4];
            for( int i = 0; i < 4; i++ )
                bbox[i] = Double.parseDouble(parts[i + 2]);

            return bbox;
        } catch( NumberFormatException e ) {
            return null;
        }
    }

    /** Filter level read back from a file name ("F3" -> 3), or -1 when the name is not one of ours. */
    public static int parseLevel(String name) {
        if( (name == null) || !name.endsWith(EXTENSION) )
            return -1;

        String[] parts = name.substring(0, name.length() - EXTENSION.length()).split("_");
        if( (parts.length != 6) || (parts[1].length() < 2) || (parts[1].charAt(0) != 'F') )
            return -1;

        try {
            return Integer.parseInt(parts[1].substring(1));
        } catch( NumberFormatException e ) {
            return -1;
        }
    }

    // ---- Reading: which cached area to use (#86 step 2) -------------------------------------

    /**
     * One cached area that covers the position, with what makes it good or not.
     *
     *   OPTIMUM = its filter is at least as rich as the one wanted (a lower level number: F1 holds
     *   everything F3 holds) AND the position sits in the part of the area that does not call for a
     *   new chunk yet -- the very test the loading loop applies, coordonateInChunk().
     *
     * Anything else that covers the position is still USABLE: it is shown at once, flagged
     * incomplete, and a download is started to replace it.
     */
    public static class Candidate {
        public final File file;
        public final int level;
        public final Chunk chunk;           // bounds and geometry of the file, not yet loaded
        public final boolean optimum;
        public final double offCentre;      // 0 at the centre, 1 on the edge

        Candidate(File file, int level, Chunk chunk, boolean optimum, double offCentre) {
            this.file = file;
            this.level = level;
            this.chunk = chunk;
            this.optimum = optimum;
            this.offCentre = offCentre;
        }
    }

    /**
     * The best cached area for this position, or null when none covers it.
     *
     * Ranking: optimum ones first; then the one where the position is closest to the centre --
     * measured as a fraction of the half-width, so that areas of different sizes compare fairly;
     * then the richer filter.
     *
     * optimumOnly: set when the chunk on screen is already an incomplete one waiting for its
     * replacement. Serving another non-optimum area from the cache would then loop for ever on the
     * same file instead of downloading the proper one.
     */
    public static Candidate best(Context context, String source, double latitude, double longitude,
                                 int wantedLevel, boolean optimumOnly, Chunk onScreen, String excludeName) {

        Candidate best = null;
        String prefix = source + "_";

        long started = android.os.SystemClock.uptimeMillis();
        int scanned = 0;

        for( File dir : allDirs(context) ) {
            for( File file : entries(dir) ) {
                String name = file.getName();
                scanned++;

                if( !file.isFile() || !name.startsWith(prefix) )
                    continue;

                // #87: the answer the downloader is turning into a chunk right now. It is committed
                // to the cache BEFORE its objects are built, so without this the preparer read the
                // same file again while the downloaded chunk was on its way -- measured, every
                // download was parsed twice.
                if( name.equals(excludeName) )
                    continue;

                double[] bbox = parseBbox(name);
                int level = parseLevel(name);

                if( (bbox == null) || (level < 1) )
                    continue;

                double latS = bbox[0];
                double lonW = bbox[1];
                double latN = bbox[2];
                double lonE = bbox[3];

                if( (latitude < latS) || (latitude > latN) || (longitude < lonW) || (longitude > lonE) )
                    continue;

                Chunk chunk = new Chunk(latS, lonW, latN, lonE, level);

                boolean optimum = (level <= wantedLevel) && chunk.coordonateInChunk(latitude, longitude);

                if( optimumOnly && !optimum )
                    continue;

                // #87: the position must be clear of this area's switch band (Chunk.SWITCH_MARGIN_RATIO),
                // otherwise showing it would only call for another switch. An optimum always is: it
                // requires the position outside the whole download margin, a wider band.
                if( !chunk.clearOfSwitchBand(latitude, longitude) )
                    continue;

                // The area already on screen, which the position is leaving: serving it again as a
                // non-optimum only re-parses the same file before the download starts anyway.
                // Measured: 37 such useless reads on a 33 km drive.
                if( !optimum && sameArea(chunk, onScreen) )
                    continue;

                double halfLat = (latN - latS) / 2;
                double halfLon = (lonE - lonW) / 2;
                double offLat = (halfLat > 0) ? Math.abs(latitude - (latS + halfLat)) / halfLat : 1;
                double offLon = (halfLon > 0) ? Math.abs(longitude - (lonW + halfLon)) / halfLon : 1;

                Candidate candidate = new Candidate(file, level, chunk, optimum, Math.max(offLat, offLon));

                if( better(candidate, best) )
                    best = candidate;
            }
        }

        MemLog.logEvent("CACHE best: " + scanned + " files in "
                + (android.os.SystemClock.uptimeMillis() - started) + " ms");

        return best;
    }

    // Same bounds, to the precision the file name keeps ("%.6f").
    private static boolean sameArea(Chunk a, Chunk b) {
        if( (a == null) || (b == null) )
            return false;

        final double eps = 0.000002;

        return (Math.abs(a.latitudeS - b.latitudeS) < eps)
                && (Math.abs(a.longitudeW - b.longitudeW) < eps)
                && (Math.abs(a.latitudeN - b.latitudeN) < eps)
                && (Math.abs(a.longitudeE - b.longitudeE) < eps);
    }

    private static boolean better(Candidate a, Candidate b) {
        if( b == null )
            return true;

        if( a.optimum != b.optimum )
            return a.optimum;

        if( a.offCentre != b.offCentre )
            return a.offCentre < b.offCentre;

        return a.level < b.level;
    }

    // ---- Writing -----------------------------------------------------------------------------

    /**
     * Opens the temporary file an answer is streamed into. It is deliberately NOT the final name:
     * a network cut, a 429 page or an Overpass "remark" would otherwise leave a truncated body
     * under a name that reads as valid, and it would be served for good. commit() renames it once
     * the caller has checked the answer really parsed.
     */
    public static File openPart(File target) {
        if( target == null )
            return null;

        File part = new File(target.getAbsolutePath() + PART_EXT);
        part.delete();

        return part;
    }

    public static OutputStream openStream(File part) throws IOException {
        return (part == null) ? null : new FileOutputStream(part);
    }

    /**
     * Copies what is read to the cache file, and NEVER lets a write failure reach the reader.
     *
     * Apache's TeeInputStream, used at first, throws the write's IOException from read(): a disk
     * filling up mid-download (ENOSPC, measured on the emulator) then made the JSON parser fail and
     * the WHOLE download was lost -- eight chunks in one test drive. A cache is a bonus; here a
     * failed write only closes the copy, flags it, and reading goes on untouched. The caller
     * checks failed() and throws the half-written file away instead of committing it.
     */
    public static class SafeTee extends java.io.FilterInputStream {
        private OutputStream copy;
        private boolean failed = false;

        public SafeTee(java.io.InputStream in, OutputStream copy) {
            super(in);
            this.copy = copy;
        }

        public boolean failed() {
            return failed;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();

            if( b >= 0 )
                write(new byte[]{ (byte) b }, 0, 1);

            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);

            if( n > 0 )
                write(buffer, offset, n);

            return n;
        }

        // Skipped bytes would never reach the copy: they are read through, so the file stays whole.
        @Override
        public long skip(long count) throws IOException {
            byte[] buffer = new byte[(int) Math.min(8192, Math.max(0, count))];
            long skipped = 0;

            while( skipped < count ) {
                int n = read(buffer, 0, (int) Math.min(buffer.length, count - skipped));
                if( n <= 0 )
                    break;
                skipped += n;
            }

            return skipped;
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        private void write(byte[] buffer, int offset, int length) {
            if( (copy == null) || failed )
                return;

            try {
                copy.write(buffer, offset, length);
            } catch( IOException e ) {
                failed = true;
                GBLog.e(TAG, "cache copy abandoned, download continues: " + e);

                try { copy.close(); } catch( IOException ignore ) {}
                copy = null;
            }
        }
    }

    // Renames the temporary file over the final one. Only called once the answer has been parsed
    // and found sound.
    public static boolean commit(File part, File target) {
        if( (part == null) || (target == null) || !part.exists() )
            return false;

        target.delete();

        if( part.renameTo(target) ) {
            GBLog.i(TAG, String.format(Locale.ROOT, "cached %s (%d bytes)",
                    target.getName(), target.length()));
            return true;
        }

        part.delete();
        GBLog.e(TAG, "cache commit failed for " + target.getName());

        return false;
    }

    public static void discard(File part) {
        if( (part != null) && part.exists() )
            part.delete();
    }

    // ---- Housekeeping -------------------------------------------------------------------------

    // Marks an entry as used now. See the class comment: this date, and not the file system access
    // time, is what the purges go by.
    public static void touch(File file) {
        if( (file != null) && file.exists() )
            file.setLastModified(System.currentTimeMillis());
    }

    public static File[] entries(File dir) {
        if( (dir == null) || !dir.isDirectory() )
            return new File[0];

        File[] files = dir.listFiles();

        return (files == null) ? new File[0] : files;
    }

    // ---- What the Maintenance page shows and does ---------------------------------------------

    /** What the cache holds right now, both volumes taken together. */
    public static class Stats {
        public int  count;
        public long bytes;
        public long oldestUseMs;     // 0 when the cache is empty
    }

    public static Stats stats(Context context) {
        Stats stats = new Stats();

        long started = android.os.SystemClock.uptimeMillis();

        for( File dir : allDirs(context) ) {
            for( File file : entries(dir) ) {
                if( !file.isFile() )
                    continue;

                stats.count++;
                stats.bytes += file.length();

                long used = file.lastModified();
                if( (stats.oldestUseMs == 0) || ((used > 0) && (used < stats.oldestUseMs)) )
                    stats.oldestUseMs = used;
            }
        }

        MemLog.logEvent("CACHE stats: " + stats.count + " files in "
                + (android.os.SystemClock.uptimeMillis() - started) + " ms");

        return stats;
    }

    /** Empties the cache, both volumes. Returns how many files were removed. */
    public static int purgeAll(Context context) {
        return purge(context, null);
    }

    /**
     * Drops every entry that has not served for more than that many days. The date compared is the
     * last USE (see touch()), not the download date: a chunk one keeps coming back to survives,
     * however old its data.
     */
    public static int purgeOlderThan(Context context, int days) {
        final long limit = System.currentTimeMillis() - (days * 24L * 3600L * 1000L);

        return purge(context, new Filter() {
            @Override public boolean drop(File file) {
                long used = file.lastModified();
                return (used > 0) && (used < limit);
            }
        });
    }

    /**
     * Drops every entry whose area lies farther than that many kilometres from the given position,
     * measured between that position and the centre of the entry's bounding box -- which is read
     * back from the file name, so nothing else has to be stored. An entry whose name cannot be read
     * is left alone: only "purge everything" removes strangers.
     */
    public static int purgeFartherThan(Context context, double km,
                                       final double latitude, final double longitude) {
        final double limitKm = km;

        return purge(context, new Filter() {
            @Override public boolean drop(File file) {
                double[] bbox = parseBbox(file.getName());
                if( bbox == null )
                    return false;

                double centreLat = (bbox[0] + bbox[2]) / 2;
                double centreLon = (bbox[1] + bbox[3]) / 2;

                return Chunk.measureKm(latitude, longitude, centreLat, centreLon) > limitKm;
            }
        });
    }

    private interface Filter {
        boolean drop(File file);
    }

    // A null filter empties the folder. Half-written .part files always go: they belong to a
    // download that never finished.
    private static int purge(Context context, Filter filter) {
        int removed = 0;

        for( File dir : allDirs(context) ) {
            for( File file : entries(dir) ) {
                if( !file.isFile() )
                    continue;

                boolean drop = (filter == null)
                        || file.getName().endsWith(PART_EXT)
                        || filter.drop(file);

                if( drop && file.delete() )
                    removed++;
            }
        }

        GBLog.i(TAG, "purge removed " + removed + " file(s)");

        return removed;
    }
}
