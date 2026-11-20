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

import android.database.Cursor;
//import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.sqlite.database.sqlite.SQLiteDatabase;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



public class Chunk {

    // Chunk geometry is ADAPTIVE to speed (speed/filter matrix measured locally).
    // Faster -> bigger chunk so we reload less often (one request per >=25 s, to respect
    // Overpass's 2 slots/IP cool-down), WITHOUT changing the on-screen zoom. The three are
    // tied and were measured 2026-07-15..20:
    //  RELOAD   = areaKM/2 : where coordonateInChunk() triggers (the margin cancels out).
    //  DOWNLOAD = areaMarginKM : ground left ahead once reload triggers = budget to fetch.
    //  ZOOM     : getX/getY spread (areaKM+2*areaMarginKM) over max(w,h)*VZ, with
    //             VZ = (areaKM+2*areaMarginKM)*5 (getVirtualZoom). So px/km = max(w,h)*5,
    //             CONSTANT -> the visible 200 m never changes with speed; only the chunk
    //             extent (data + one-finger pan room) grows.
    // Set per-chunk at construction from the speed; there is no static config anymore.
    private double areaKM;
    private double areaMarginKM;
    private double speedKmh;   // speed at construction; drives geometry AND filter level

    // Speed (km/h) -> (areaKM, areaMarginKM). Step function straight from the matrix, as a table so
    // that a chunk read back from the cache can find its own step from the size of its bbox (#86).
    // Every step has a DIFFERENT total width (areaKM + 2 * areaMarginKM), which is what makes that
    // reverse lookup unambiguous.
    private static final double[][] GEOMETRY = {
            //  speed below   areaKM   marginKM
            {   80,            1.2,     0.4 },
            {  100,            1.4,     0.5 },
            {  110,            1.6,     0.5 },
            {  120,            1.8,     0.6 },
            {  140,            2.0,     0.6 },
            {  150,            2.2,     0.7 },
            {  160,            2.4,     0.8 },
            {  170,            2.6,     0.8 },
            {  190,            2.8,     0.9 },
            { Double.MAX_VALUE, 3.0,    1.0 },
    };

    // OSM v0.6 mode: small fixed chunks (original behaviour).
    private static final double OSM_AREA_KM   = 0.3;
    private static final double OSM_MARGIN_KM = 0.15;

    private void applyGeometryForSpeed(double kmh) {
        if( !useOverpassApi ) {
            areaKM = OSM_AREA_KM;
            areaMarginKM = OSM_MARGIN_KM;
            return;
        }

        // The last step also catches what no comparison accepts (a NaN speed), as the former
        // trailing "else" did.
        double[] step = GEOMETRY[GEOMETRY.length - 1];
        for( double[] candidate : GEOMETRY ) {
            if( kmh < candidate[0] ) {
                step = candidate;
                break;
            }
        }

        areaKM = step[1];
        areaMarginKM = step[2];
    }

    // #86 step 2: the step whose total width is closest to the one measured on a cached bbox. The
    // widths are at least 0.2 km apart, far more than any rounding of a "%.6f" bound.
    private void applyGeometryForWidth(double widthKm) {
        if( !useOverpassApi ) {
            areaKM = OSM_AREA_KM;
            areaMarginKM = OSM_MARGIN_KM;
            return;
        }

        double[] best = GEOMETRY[0];
        double bestGap = Double.MAX_VALUE;

        for( double[] candidate : GEOMETRY ) {
            double gap = Math.abs((candidate[1] + 2 * candidate[2]) - widthKm);
            if( gap < bestGap ) {
                bestGap = gap;
                best = candidate;
            }
        }

        areaKM = best[1];
        areaMarginKM = best[2];
    }

    // Virtual-zoom keeping 200 m on the long screen axis for THIS chunk's size.
    // = chunkWidth(km) * 1000 / 200 = (areaKM + 2*areaMarginKM) * 5. Used by GameSurface.
    public int getVirtualZoom() {
        double areaTot = areaKM + 2 * areaMarginKM;
        return (int) Math.round(areaTot * 5);
    }

    // OSM relations (multipolygons) -> MapArea. Fetchées+parsées UNIQUEMENT en F1, décidé
    // par relationsForLevel() (les deux chemins, OSM et Overpass). Off au-dessus de F1 :
    // certains multipolygons sont énormes (Natura 2000 = une forêt entière) et coûtent
    // FPS/mémoire. F1 en a besoin : un lac/forêt en multipolygon porte natural=water /
    // landuse=forest sur la RELATION, sa way extérieure n'a aucun tag -> invisible sinon
    // (ex. le "Grand Étang" de Trainou n'existe QUE comme relation). La requête Overpass
    // est filtrée aux tags landuse/natural qu'on dessine (écarte les multipolygons
    // building/boundary, 982/1357 à Paris). Coût F1 : Paris +9%, Trainou +15% (2026-07-21).
    // TODO #5 (offline) : idem via SQL sur land_polygons.

    // Task #39. false -> dlChunk_OsmApi() (the OSM editing API, off-charter for display
    // and 400 past 50000 nodes), true -> dlChunk_OverpassApi(). Flip to compare both at
    // the same spot. Overpass returns a strictly identical set of nodes/ways/tags.
    private static final boolean useOverpassApi = true;

    // Exposé à AppManager : le throttle/attente de slot fair-use ne s'applique qu'en Overpass.
    public static boolean usesOverpassApi() {
        return useOverpassApi;
    }

    // Overpass endpoint. #68 : ce n'est plus une constante, c'est un reglage lu dans AppManager
    // (Developer parameters), pour que l'application puisse tourner contre une autre instance que
    // celle de Golden-Block. Le defaut reste l'instance officielle FOSSGIS, qui est la seule
    // recommandee : les miroirs (kumi.systems, private.coffee) servent des donnees corrompues ou
    // perimees. Voir AppManager.OVERPASS_BASE_DEFAULT.

    // Identifies us to Overpass. Mandatory: their fair-use policy expects an app name
    // and a contact, and the OSM API policy blocks clients that fake someone else's.
    // #67 : elle a demenage dans AppManager, aupres des adresses de serveur, la recherche de lieu
    // s'identifiant de la meme facon. L'alias garde les appels ci-dessous inchanges.
    private static final String USER_AGENT = AppManager.USER_AGENT;

    // Server-side WORKING RAM, unrelated to the response size -- do not size it from the
    // payload. Left out, the default is 512 MiB and the server refuses the query (504) as
    // soon as it is loaded, since it only admits what fits in half the remaining
    // resources. Measured floor for a 1.3 km chunk in Paris (the worst case, which still
    // returns 11.4 MB / 73005 elements): 4 MiB -> 8 MiB leaves 2x.
    // The wide alarm query of step 2 is a different story and will need its own value:
    // Paris needs 64 MiB there, and even 32 MiB fails although the server asks for
    // "at least 32 MB" (that message is a floor to CONTINUE, not the total need).
    // Latency does not depend on this value (measured 8/16/32/64 MiB: no correlation).
    // Sized for the 4.0 km chunk (16 km2) worst case = Paris: 87 MB, 550599 elements.
    // maxsize is server WORKING RAM (not the payload size): 128 MiB holds it.
    // timeout bounds SERVER EXECUTION only (NOT the transfer): Paris 16 km2 needs ~13 s
    // just to print, so timeout:10 FAILS there ("Query timed out after 13 s") -> 30.
    // At sea (off Cordouan) both are irrelevant, the chunk is tiny.
    // WARNING: 87 MB + 550k elements per chunk in a dense city is heavy on 4G AND on the
    // heap (Gson builds one OSMElement per node) -> filtering is needed for cities.
    // En OCTETS, et non "128Mi". Le moteur de reference accepte le suffixe, les reimplementations
    // derriere une cle ne l'acceptent pas : overpass.ch refuse la requete entiere avec
    // "setting maxsize must be a non-negative integer, got 128Mi". Le nombre nu passe partout.
    private static final String OVERPASS_MAXSIZE = "134217728";   // 128 Mio
    private static final int OVERPASS_TIMEOUT_S = 30;

    double latitudeN;
    double latitudeS;
    double longitudeE;
    double longitudeW;


    private final List<MapArea> gameObjectsLands = new ArrayList<MapArea>();
    private final List<MapGameObject> gameObjectsAreas = new ArrayList<MapGameObject>();
    private final List<MapGameObject> gameObjectsRoads = new ArrayList<MapGameObject>();
    private final List<MapGameObject> gameObjectsMarks = new ArrayList<MapGameObject>();

    public final Map<String,ListMapPoint> gameObjectBetterPointsForText = new HashMap<String, ListMapPoint>();

    private GameSurface gameSurface = null;

    // #86 step 2 / #87: a chunk that is shown but not the one we would have asked for -- read from the
    // cache with a poorer filter, or too close to its own edge. It stays on screen, but the loading
    // loop treats it as if the position had left it, so a proper chunk is fetched and replaces it.
    // coordonateInChunk() does NOT look at this flag: the splash screen relies on it to know whether
    // the map under it covers the position, which an incomplete chunk does perfectly well.
    private volatile boolean incomplete = false;

    public boolean isIncomplete() {
        return incomplete;
    }

    public double getAreaMarginKM() {
        return areaMarginKM;
    }

    // #87: the band along the edge, as a fraction of the chunk's download margin, that is kept for
    // the forced switch to another chunk. Once the position enters it, the loading loop switches
    // without waiting for anything; and a cached area is never offered when the position already
    // sits in ITS band -- it would call for another switch at once (measured: the fix left such an
    // area two seconds after it was shown). A quarter of the margin is 100 m on foot and 250 m at
    // motorway speed.
    public static final double SWITCH_MARGIN_RATIO = 0.25;

    // True when the position is inside this chunk and outside its switch band.
    public boolean clearOfSwitchBand(double latitude, double longitude) {
        return insideWithMargin(latitude, longitude, SWITCH_MARGIN_RATIO * areaMarginKM);
    }

    // #87: where this chunk comes from, for the loading loop's traces and the test analysis:
    // "online" (downloaded), "cache" (read back, optimum), "cache-incomplete", "offline" (coastline
    // only, no OSM data). Nothing decides on it; isIncomplete() is what the loop acts upon.
    private volatile String origin = "online";

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    // "lonW,latS,lonE,latN", the order the download traces already use.
    public String bboxString() {
        return String.format(Locale.ROOT, "%.6f,%.6f,%.6f,%.6f", longitudeW, latitudeS, longitudeE, latitudeN);
    }

    public void setIncomplete(boolean incomplete) {
        this.incomplete = incomplete;
    }

    // Lowest speed that yields each filter level (see filterLevelForSpeed), used to give a cached
    // chunk a speed consistent with the level of its file.
    private static final double[] LEVEL_SPEED = { 0, 0, 50, 80, 110, 130 };

    /**
     * #86 step 2: a chunk rebuilt around a cached answer. The bounds are those of the FILE, not
     * recomputed from the current position, and the geometry is derived from their width: the
     * on-screen zoom is computed from areaKM + 2 * areaMarginKM (getVirtualZoom), so a geometry taken
     * from the current speed would scale a small F1 area as if it were a large F3 one.
     */
    public Chunk(double latS, double lonW, double latN, double lonE, int level)
    {
        int clamped = Math.max(1, Math.min(level, LEVEL_SPEED.length - 1));
        this.speedKmh = LEVEL_SPEED[clamped];

        latitudeS = latS;
        longitudeW = lonW;
        latitudeN = latN;
        longitudeE = lonE;

        applyGeometryForWidth(latitudeGetKmFrom(latS, latN));
    }

    public Chunk(double latitude, double longitude, double speedKmh)
    {
        this.speedKmh = speedKmh;
        applyGeometryForSpeed(speedKmh);

        double halfKm = areaKM / 2 + areaMarginKM;
        latitudeN = latitudeAddKm(latitude, halfKm);
        latitudeS = latitudeAddKm(latitude, -halfKm);
        longitudeE = longitudeAddKm(longitude, latitude, halfKm);
        longitudeW = longitudeAddKm(longitude, latitude, -halfKm);


        //double kmY = latitudeGetKmFrom( latitudeN,  latitude);
        //double kmX = longitudeGetKmFrom( longitudeW,  latitudeN, longitude);

        //double kmX2 = longitudeGetKmFrom( longitudeW,  latitudeN, 2.095);
    }

    public void dbVersion() {

        try {
            AppManager _am = AppManager.getInstance();
            if( _am.isInitializedDB() ) {
                String query = "select sqlite_version() AS sqlite_version";
                SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(":memory:", null);
                Cursor cursor = db.rawQuery(query, null);

                String sqliteVersion = "";
                if (cursor.moveToNext()) {
                    sqliteVersion = cursor.getString(0);
                }

                db.close();
            }

        } catch (Exception e){
            GBLog.e( "CHUNK", "ERR"+e.toString());
        }
    }


    public Cursor dbBBox(SQLiteDatabase db, double longitudeW, double longitudeE, double latitudeS, double latitudeN) {
        Cursor c = null;

        String query;
        String[] args;


        // Node ceiling per polygon. This is a MEMORY and load-time guard, not a drawing one:
        // every node becomes a MapPoint, and one 166643-node polygon took the heap from 9 to
        // 101 MB (ceiling 192) and 2.8 s to load. Such a polygon is an upstream defect -- the
        // land of a whole delta shipped as a single piece instead of being cut up like the
        // rest of the database -- and it would not be drawn anyway: see the extent guard in
        // MapArea.getCombinedPath(). Raising this ceiling only makes sense together with
        // clipping the polygon to the chunk at load time.
        final int LIMIT_NODES_COUNT = 50000;

        // important USE CROSS JOIN TO FORCE TO USE SPACIAL INDEX
        query = String.format(
                "SELECT w.way_id, n.lon, n.lat " +
                "FROM " +

                "(SELECT w2.way_id AS way_id, count(w2.way_id) AS way_nodes_count " +
                "FROM ways w2 " +
                "CROSS JOIN way_nodes wn2 ON w2.way_id=wn2.way_id " +
                "WHERE ((max_lon>=?1 AND min_lon<=?2) AND (max_lat>=?3 AND min_lat<=?4)) " +
                "GROUP BY w2.way_id " +
                "HAVING count(w2.way_id) < %d ) w "+

                "CROSS JOIN way_nodes wn ON w.way_id=wn.way_id "+
                "INNER JOIN nodes n ON n.node_id=wn.node_id " +
                "ORDER BY w.way_id, wn.node_order", LIMIT_NODES_COUNT);

        // Important : args only done for selection parameters : not work with having parameters
        // Locale.ROOT -> To use "." decimal separator on French Device
        /*
        args = new String[]{
            String.format(Locale.ROOT,"%.4f", longitudeW-0.0001),
            String.format(Locale.ROOT,"%.4f", longitudeE+0.0001),
            String.format(Locale.ROOT,"%.4f", latitudeS-0.0001),
            String.format(Locale.ROOT,"%.4f", latitudeN+0.0001),
            //String.format("%d", LIMIT_NODES_COUNT),
        };
        */

        args = new String[]{
                String.format(Locale.ROOT,"%.15E", longitudeW-0.0001),
                String.format(Locale.ROOT,"%.15E", longitudeE+0.0001),
                String.format(Locale.ROOT,"%.15E", latitudeS-0.0001),
                String.format(Locale.ROOT,"%.15E", latitudeN+0.0001),
                //String.format("%d", LIMIT_NODES_COUNT),
        };
        return db.rawQuery(query, args);
    }

    public void dbChunk() {

        if( false )
            dbVersion();

        long tDbStart = System.nanoTime();   // PERF

        try {
            AppManager _am = AppManager.getInstance();
            if( _am.isInitializedDB() ) {

                String dbPath = null;

                for( MapInfo mi: _am.getMaps() )
                {
                    if( mi.getName().equalsIgnoreCase("land_polygons") ) {
                        if( mi.getDownloadStatus() == MapInfo.TypeStatus.INSTALLED ) {
                            String miDbPath = mi.getDbPath();
                            if (miDbPath != null && miDbPath.length() > 0) {
                                File file = new File(miDbPath);
                                if (file.exists()) {
                                    dbPath = file.getAbsolutePath();
                                    break;
                                }
                            }
                        }
                    }
                }

                SQLiteDatabase db;

                if (dbPath != null) {
                    db = SQLiteDatabase.openDatabase(dbPath, null,
                            SQLiteDatabase.OPEN_READONLY);

                    Cursor c = dbBBox(db, longitudeW, longitudeE, latitudeS, latitudeN);
                    long countAreas = 0;
                    long old_way_id = 0;
                    ListMapPoint lst = null;

                    // ONE MapArea holding every land polygon of the chunk, each as its own ring,
                    // instead of one MapArea per polygon. The polygons are deliberately cut into
                    // adjacent pieces in the database, and two abutting antialiased fills leave a
                    // hairline of background between them -- a thread of water where there is land.
                    // Gathered in a single path, their common borders become interior to one filled
                    // surface and are never rasterised as edges, so the seam cannot appear and the
                    // fattening stroke that used to hide it is no longer needed. Disjoint pieces
                    // (an islet, the two banks of a river) stay separate contours and are filled
                    // separately: a path holds as many contours as needed.
                    MapArea land = null;

                    if (c.moveToFirst()) {
                        do {
                            long way_id = c.getLong(0);
                            double node_lon = c.getDouble(1);
                            double node_lat = c.getDouble(2);

                            if (old_way_id != way_id) {
                                if (land == null) {
                                    land = new MapArea(this, "", MapArea.TypeArea.EARTH_LAND, false);

                                    this.gameObjectsAreas.add(land);
                                    this.gameObjectsLands.add(land);
                                }

                                lst = new ArrayListMapPoint(true);
                                land.addListPoints(lst);

                                countAreas++;
                            }

                            if (lst != null) {
                                lst.add(new MapPoint(this, node_lon, node_lat));
                            }

                            old_way_id = way_id;

                        } while (c.moveToNext());

                        GBLog.d("CHUNK", String.format("LAND: %d", countAreas));
                    }

                    db.close();

                    // PERF : what the DB actually handed over for this chunk.
                    GBLog.d("PERF", String.format(Locale.US,
                            "dbChunk: %d land polygons, %d points, %d ms",
                            perfLandCount(), perfLandPointCount(),
                            (System.nanoTime() - tDbStart) / 1000000));
                }
            }
        } catch (Exception e){
            // Rethrow so AppManager.myThread skips setting _currentChunk and retries the
            // chunk next tick. NB: "land_polygons not installed" is NOT an error here
            // (dbPath stays null, we just add no land) -> no throw, no retry loop.
            GBLog.e( "CHUNK", "ERR"+e.toString());
            throw new RuntimeException("dbChunk failed", e);
        }

    }

    /**
     * Entry point used by AppManager. Picks the implementation; see useOverpassApi.
     */
    public void dlChunk() {
        if( useOverpassApi )
            dlChunk_OverpassApi();
        else
            dlChunk_OsmApi();
    }

    /**
     * Fair-use helper: seconds to wait before an Overpass slot is free.
     *   0 -> a slot is free now, OR we are in OSM mode.
     *   N -> both of our 2 IP slots are cooling down; N is the soonest to free.
     * FAIL CLOSED: if /api/status cannot be read (timeout, network), this THROWS instead
     * of returning 0, so the caller does NOT fire the query blindly -- the thread retries
     * in 1s. A 429 carries NO Retry-After header, so /api/status ("... in N seconds.") is
     * the only source of the cool-down. Called by AppManager BEFORE dlChunk().
     */
    /**
     * Tries a server and says, in one line, what it answered.
     *
     * For the helper page, and for nothing else: it is the only way to tell a saturated public
     * instance from a wrong address without reading a log. It asks for /status, and if the server
     * does not serve one it falls back on the smallest real query there is -- counting the nodes
     * in a few square metres -- because an instance behind a key may well answer queries and not
     * status.
     *
     * Short timeouts on purpose: here somebody is waiting in front of the screen, which is not
     * the case when the game downloads a chunk.
     */
    /** What a try came back with: what to say, how long it took, and whether it worked. */
    public static final class OverpassTry {

        public final String said;
        public final long ms;
        public final boolean ok;

        OverpassTry(String said, long ms, boolean ok) {
            this.said = said;
            this.ms = ms;
            this.ok = ok;
        }
    }

    public static OverpassTry tryOverpass(String base) {

        if( (base == null) || base.isEmpty() )
            return new OverpassTry("No address.", 0, false);

        long t0 = System.currentTimeMillis();

        String said = askOverpass(base + "/status", null);

        long took = System.currentTimeMillis() - t0;

        if( said == null )
            return new OverpassTry("Answered in " + tenths(took) + " s. Usable.", took, true);

        // No status page is not a refusal: an instance behind a key may well answer queries
        // without serving one. The smallest real query there is settles it.
        long t1 = System.currentTimeMillis();

        String query = askOverpass(base + "/interpreter",
                                   "[out:json][timeout:25];node(48.649,-2.026,48.650,-2.025);out count;");

        took = System.currentTimeMillis() - t1;

        if( query == null )
            return new OverpassTry("No status page, but queries answer in " + tenths(took)
                                   + " s. Usable.", took, true);

        return new OverpassTry(query, took, false);
    }

    private static String tenths(long ms) {
        return String.format(java.util.Locale.US, "%.1f", ms / 1000.0);
    }

    // Null when it answered properly, otherwise what went wrong, in words a user can act on.
    private static String askOverpass(String url, String query) {

        HttpClient client = null;

        try {
            client = new DefaultHttpClient();
            HttpParams hp = client.getParams();
            HttpConnectionParams.setConnectionTimeout(hp, 10000);
            HttpConnectionParams.setSoTimeout(hp, 25000);

            // EXACTEMENT comme dlChunk_OverpassApi : un GET, la requete dans ?data=. Envoyee en
            // POST, elle revenait en 400 d'une passerelle qui n'accepte que cette forme -- et un
            // test qui n'emprunte pas le chemin de l'application ne teste pas l'application.
            HttpGet get = new HttpGet((query == null) ? url : (url + "?data=" + urlEncode(query)));

            get.setHeader("User-Agent", USER_AGENT);

            HttpResponse response = client.execute(get);

            int code = response.getStatusLine().getStatusCode();

            if( code == 200 )
                return null;

            if( code == 404 )
                return "No page there (404).";

            if( (code == 429) || (code == 504) || (code == 502) )
                return "Server saturated (" + code + "). Try another one.";

            if( (code == 401) || (code == 403) )
                return "Refused (" + code + "). The key is missing or wrong.";

            return "Answered " + code + ".";

        } catch( java.net.SocketTimeoutException e ) {
            return "No answer within 25 s. Saturated, or the wrong address.";
        } catch( javax.net.ssl.SSLException e ) {
            // Almost always an expired certificate on their side, and there is nothing to be done
            // here about it: the name of the exception would tell the user nothing at all.
            return "Its security certificate is refused. The server has to fix it, not you.";
        } catch( java.net.UnknownHostException e ) {
            return "That address does not exist.";
        } catch( java.io.IOException e ) {
            return "Could not be reached. Check the address, or the network.";
        } catch( Exception e ) {
            return "Could not be reached: " + e.getClass().getSimpleName() + ".";
        } finally {
            if( client != null ) {
                try { client.getConnectionManager().shutdown(); } catch( Exception ignored ) { }
            }
        }
    }

    public int slotWaitSeconds() {
        if( !useOverpassApi )
            return 0;

        HttpClient client = null;
        InputStream in = null;
        try {
            client = new DefaultHttpClient();
            HttpParams hp = client.getParams();
            // /api/status itself can take 30 s+ when the public instance is loaded:
            // at 8 s we failed closed forever and never downloaded a chunk.
            HttpConnectionParams.setConnectionTimeout(hp, 15000);
            HttpConnectionParams.setSoTimeout(hp, 60000);

            // #68 : l'adresse est lue UNE fois, ici. La relire plus loin dans la meme operation
            // pourrait tomber de l'autre cote d'un changement de reglage.
            HttpGet get = new HttpGet(AppManager.getInstance().getOverpassStatusUrl());
            get.setHeader("User-Agent", USER_AGENT);
            HttpResponse response = client.execute(get);

            in = (InputStream) response.getEntity().getContent();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            StringBuilder str = new StringBuilder();
            String line;
            while( (line = reader.readLine()) != null )
                str.append(line).append("\n");
            String txt = str.toString();

            // TEMP (debug): dump the raw /api/status body so we can see the slot state.
            GBLog.d("CHUNCK", "STATUS <<<\n" + txt + ">>> STATUS");

            // "N slots available now." -> at least one slot free
            Matcher mAvail = Pattern.compile("(\\d+)\\s+slots?\\s+available\\s+now").matcher(txt);
            if( mAvail.find() && Integer.parseInt(mAvail.group(1)) >= 1 )
                return 0;

            // otherwise the soonest "in N seconds"
            Matcher mWait = Pattern.compile("in\\s+(\\d+)\\s+seconds?").matcher(txt);
            int min = -1;
            while( mWait.find() ) {
                int v = Integer.parseInt(mWait.group(1));
                if( min < 0 || v < min )
                    min = v;
            }
            return (min < 0) ? 0 : min;

        } catch (Exception e){
            // Fail CLOSED: if /api/status cannot be read, do NOT fire the Overpass query.
            // Rethrow -> AppManager's thread catches it, skips the download, retries in 1s.
            GBLog.e( "CHUNCK", "slotWaitSeconds KO: " + e);
            throw new RuntimeException("slotWaitSeconds failed", e);
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignore) {}
            }
            if (client != null) {
                try { client.getConnectionManager().shutdown(); } catch (Exception ignore) {}
            }
        }
    }

    /**
     * Historical implementation, kept for comparison (see useOverpassApi).
     * Uses the OSM EDITING API, which is off-charter for read-only display and
     * returns 400 past 50000 nodes -- i.e. it cannot load a chunk in a dense city
     * at the current areaMarginKM. That is what task #39 is about.
     */
    public void dlChunk_OsmApi() {
        int level = filterLevelForSpeed(speedKmh);
        // JSON : suffixe .json (Locale.ROOT -> separateur "." meme sur device FR).
        String url = String.format(Locale.ROOT,
                "https://www.openstreetmap.org/api/0.6/map.json?bbox=%.6f,%.6f,%.6f,%.6f",
                longitudeW, latitudeS, longitudeE, latitudeN);
        dlChunkHttp(url, level, ChunkCache.SOURCE_OSM);
    }

    /**
     * Task #39. Same chunk, same objects, but through Overpass -- the API OSM itself
     * points to for read-only use. Strict parity with dlChunk_OsmApi() was verified on
     * nodes, ways and tags (checked with a local comparison harness).
     *
     * Overpass FAIR-USE (public instance overpass-api.de) -- respect it or we get blocked:
     *   - Send an identifying User-Agent (or Referer) with app name + contact. An
     *     anonymous client can be blocked; faking another app's agent WILL get blocked.
     *   - Stay under ~10 000 requests/day AND under ~1 GB downloaded/day.
     *   - 2 concurrent slots per IP. A request waits up to 15 s in queue, then 429
     *     (quota) or 504 (query not admitted under load).
     *   - Declare [timeout:] and [maxsize:]; the server prioritises small requests.
     *   - COOL-DOWN: a slot is NOT freed the instant a query returns -- it stays held
     *     for a cool-down that GROWS with server load and is PROPORTIONAL to the query's
     *     execution time. So back-to-back heavy queries keep both slots busy and the
     *     next ones get 429/504. Space them out; keep queries cheap.
     *   - Heavy/steady use -> host your own instance rather than the public one.
     * (Source: dev.overpass-api.de/overpass-doc/en/preface/commons.html)
     */
    public void dlChunk_OverpassApi() {
        int level = filterLevelForSpeed(speedKmh);
        String ql = buildChunkQl(level);
        String url = AppManager.getInstance().getOverpassInterpreterUrl() + "?data=" + urlEncode(ql);
        dlChunkHttp(url, level, ChunkCache.SOURCE_OVERPASS);
    }

    // Tronc commun OSM + Overpass : requete HTTP -> status/trace -> parse streaming -> remark/null
    // -> construction des objets (nodes/ways/relations) -> tri. Seules l'URL et le niveau de
    // filtre relations varient entre les deux modes (etape 3 de la tache #39 : factorisation).
    private void dlChunkHttp(String url, int level, String cacheSource) {

        MemLog.log("DLChunk >>> start");
        GBLog.i("CHUNCK", String.format(Locale.ROOT,
                "DLChunk[%s] F%d @%.0fkm/h bbox=%.6f,%.6f,%.6f,%.6f",
                usesOverpassApi() ? "OVP" : "OSM", level, speedKmh,
                longitudeW, latitudeS, longitudeE, latitudeN));

        HttpClient client = null;
        InputStream in = null;

        // #86 step 1: the answer is written to the cache as it streams past. Nothing lands under
        // its final name before the body has been parsed and found sound -- see the commit below.
        File cacheTarget = null;
        File cachePart = null;
        OutputStream cacheOut = null;
        ChunkCache.SafeTee tee = null;
        boolean cacheCommitted = false;

        try {

            //latitudeN = -16.7743;
            //latitudeS = -16.8367;
            //longitudeE = -179.9341;
            //longitudeW = -180.0542;

            client = new DefaultHttpClient();
            // Bound the wait: a silent/unreachable OSM server now fails fast
            // instead of hanging this thread forever (was: no timeout at all).
            HttpParams httpParams = client.getParams();
            HttpConnectionParams.setConnectionTimeout(httpParams, 15000);
            // Must exceed OVERPASS_TIMEOUT_S: Overpass sends nothing while the query runs.
            HttpConnectionParams.setSoTimeout(httpParams, 60000);

            HttpGet getMethod = new HttpGet(url);
            // Both APIs require it: OSM blocks clients that fake another app's agent,
            // Overpass expects an identifiable app + contact.
            getMethod.setHeader("User-Agent", USER_AGENT);
            HttpResponse response = client.execute(getMethod);
            // Examine the response status
            GBLog.i("CHUNCK",response.getStatusLine().toString());

            int statusCode = response.getStatusLine().getStatusCode();

            HttpEntity entity = response.getEntity();
            long contentLen = entity.getContentLength();   // -1 -> reponse chunked
            String lenStr = (contentLen < 0) ? "chunked" : String.valueOf(contentLen);
            // Trace persistante a CHAQUE chunk (diagnostic terrain : status + taille declaree,
            // pour trancher indispo/timeout / reponse vide / 429/504 le lendemain).
            MemLog.logEvent(String.format(Locale.ROOT,
                    "DLChunk HTTP %d len=%s bbox=%.6f,%.6f,%.6f,%.6f",
                    statusCode, lenStr, longitudeW, latitudeS, longitudeE, latitudeN));

            in = (InputStream) entity.getContent();
            // Status FIRST: an error body (429/504) is HTML, not JSON -- don't parse it.
            // 400 = over 50000 nodes (OSM v0.6 only), 429 = our IP quota is spent,
            // 504 = Overpass refused to admit the query under load.
            if( statusCode != 200 ) {

                // Ce que le serveur DIT du refus. Overpass ecrit toujours la raison dans le corps
                // -- une requete trop grosse, un maxsize refuse, une cle epuisee -- et jeter le
                // seul code laissait chercher a l'aveugle. Les premiers caracteres suffisent, et
                // on ne lit jamais plus : un corps d'erreur peut etre une page entiere.
                String why = "";

                try {
                    char[] buffer = new char[600];
                    int read = new InputStreamReader(in).read(buffer);

                    if( read > 0 )
                        why = new String(buffer, 0, read).replaceAll("\\s+", " ").trim();

                } catch( Exception ignore ) {
                }

                GBLog.e("CHUNCK", "DLChunk refused " + statusCode + " <<< " + why + " >>>");

                // Echec serveur (400/429/504...) : on LEVE au lieu de committer un chunk vide en
                // silence -> remonte au catch d'AppManager (notifyError) + retry au prochain tick.
                throw new RuntimeException("server HTTP " + statusCode);
            }

            // Parse the JSON STREAMING, straight from the socket: we never build the whole
            // response as a String. A dense-city chunk is tens of MB, and str.toString()
            // would need a char[] twice that size -> the OutOfMemoryError we hit in Paris.
            GBLog.i("CHUNCK", String.format(Locale.US, "DLChunk status=%d streaming parse", statusCode));
            // Reclaim the previous chunk's now-dead OSMData graph before this ~70MB parse,
            // to maximise headroom (band-aid; the real fix is capping retained memory).
            Runtime.getRuntime().gc();
            MemLog.log("DLChunk before parse");

            // #86: the body is duplicated into the cache WHILE it is parsed, through a TeeInputStream.
            // Writing it first and parsing afterwards would mean holding, or re-reading, tens of
            // megabytes; teeing costs one buffer. A cache that cannot be opened is not an error: the
            // download goes on exactly as before, uncached.
            InputStream body = in;
            cacheTarget = ChunkCache.entryFile(AppManager.getInstance().getContext(), cacheSource, level,
                    latitudeS, longitudeW, latitudeN, longitudeE);
            if( cacheTarget != null ) {
                try {
                    cachePart = ChunkCache.openPart(cacheTarget);
                    cacheOut = ChunkCache.openStream(cachePart);
                    tee = new ChunkCache.SafeTee(in, cacheOut);
                    body = tee;
                } catch( Exception e ) {
                    GBLog.e("CHUNCK", "cache write disabled: " + e);
                    cachePart = null;
                    cacheOut = null;
                    body = in;
                }
            }

            OSMData data  = OSMData.jsonToOSMData(new InputStreamReader(body, java.nio.charset.StandardCharsets.UTF_8));
            MemLog.log("DLChunk after parse");

            // The parser stops on the closing brace and may leave bytes in the socket; the tee only
            // writes what is read THROUGH it, so the cached copy would be short of that tail. Read
            // it out before deciding to keep the file.
            if( cacheOut != null )
                drain(body);

            // Everything below would NPE on a null data. jsonToOSMData swallows its own
            // exceptions and hands back null, so this is the only place we can catch it.
            if( data == null ) {
                MemLog.logEvent(String.format(Locale.ROOT,
                        "DLChunk PARSE FAILED bbox=%.6f,%.6f,%.6f,%.6f",
                        longitudeW, latitudeS, longitudeE, latitudeN));
                throw new RuntimeException("no data (parse failed)");   // reponse illisible (ex. page d'erreur box 4G)
            }

            // Overpass answers a runtime failure (out of memory, timeout) with HTTP 200,
            // a remark, and NO elements. Untested, that reads as "empty area" forever.
            if( data.hasRemark() ) {
                MemLog.logEvent(String.format(Locale.ROOT,
                        "DLChunk OVERPASS REMARK bbox=%.6f,%.6f,%.6f,%.6f : %s",
                        longitudeW, latitudeS, longitudeE, latitudeN, data.remark));
                throw new RuntimeException("server busy (Overpass remark)");   // 200 mais vide/surcharge
            }

            // #86: the answer parsed and carries no remark -- only now is it worth keeping. Closing
            // the stream first flushes the last buffer, otherwise the committed file is truncated.
            if( cacheOut != null ) {
                try { cacheOut.close(); } catch( Exception ignore ) {}
                cacheOut = null;

                // A copy that failed half-way (disk full...) is incomplete: the finally below
                // throws it away rather than committing a truncated answer.
                if( (tee == null) || !tee.failed() )
                    cacheCommitted = ChunkCache.commit(cachePart, cacheTarget);
            }

            buildFromData(data, level);

        } catch (Exception e){
           // Rethrow so AppManager.myThread retries the chunk next tick instead of keeping an empty
           // one. The finally below still frees the connection first. Depuis #52 : les echecs HTTP
           // (non-200), parse null et remark Overpass LEVENT aussi (au lieu d'un return silencieux)
           // -> ils passent ici, sont signales via notifyError (motif dans le message) et retentent.
           GBLog.e( "CHUNCK", "DLChunk ERR (serveur muet / réseau / parse ?): " + e, e);
           throw new RuntimeException("dlChunk failed", e);
        } finally {
            // Always release the socket, the stream and Apache's connection pool, even when the
            // download failed: without this, every retry leaked a connection and its buffers.
            if (in != null) {
                try { in.close(); } catch (Exception ignore) {}
            }
            if (client != null) {
                try { client.getConnectionManager().shutdown(); } catch (Exception ignore) {}
            }

            // #86: anything that did not make it to the commit is a half-written body -- a server
            // error page, a cut connection, an Overpass remark. It is thrown away rather than left
            // under a name that would read as a valid entry.
            if (cacheOut != null) {
                try { cacheOut.close(); } catch (Exception ignore) {}
            }
            if (!cacheCommitted) {
                ChunkCache.discard(cachePart);
            }

            MemLog.log("DLChunk <<< end");
        }
    }

    // #86 step 2: turns a parsed answer into the chunk's map objects. Shared by the network path
    // (dlChunkHttp) and the cache path (loadFromCache), which only differ in where the JSON comes
    // from. The body was moved here unchanged.
    private void buildFromData(OSMData data, int level) {

        //int nbNodes = data.getNodes().size();
        //int nbWays = data.ways.size();
        //int nbRelations = data.relations.size();
        int count = 0;

        for(OSMElement node:  data.getNodes().values()) {
            if( node.tags != null ) {
                String seamarkType = node.tags.get("seamark:type");
                String place = node.tags.get("place");
                String highway = node.tags.get("highway");
                if (seamarkType != null) {
                    count += 1;

                    String seamarkName = "";
                    if (node.tags.containsKey("seamark:name"))
                        seamarkName = node.tags.get("seamark:name");

                    String keyCategory = String.format("seamark:%s:category", seamarkType);
                    String seamarkCategory = "";
                    if (node.tags.containsKey(keyCategory))
                        seamarkCategory = node.tags.get(keyCategory);

                    String keyColor = String.format("seamark:%s:colour", seamarkType);
                    String seamarkColor = "";
                    if (node.tags.containsKey(keyColor))
                        seamarkColor = node.tags.get(keyColor);

                    String keyShape = String.format("seamark:%s:shape", seamarkType);
                    String seamarkShape = "";
                    if (node.tags.containsKey(keyShape))
                        seamarkShape = node.tags.get(keyShape);


                    String keyTopColor = String.format("seamark:%s:topmark:colour", seamarkType);
                    String seamarkTopColor = "";
                    if (node.tags.containsKey(keyTopColor))
                        seamarkTopColor = node.tags.get(keyTopColor);

                    String keyTopShape = String.format("seamark:%s:topmark:shape", seamarkType);
                    String seamarkTopShape = "";
                    if (node.tags.containsKey(keyTopShape))
                        keyTopShape = node.tags.get(keyTopShape);

                    String keyWaterLevel = String.format("seamark:%s:water_level", seamarkType);
                    String seamarkWaterLevel = "";
                    if (node.tags.containsKey(keyWaterLevel))
                        seamarkWaterLevel = node.tags.get(keyWaterLevel);


                    GBLog.d("Chunk", String.format("SEAMARK %d %s:%s %s", count, seamarkType, seamarkCategory, seamarkName));

                    MapSeaMark.TypeSeaMark typeSeaMark = MapSeaMark.TypeSeaMark.getTypeSeaMark(seamarkType, seamarkCategory, seamarkColor, seamarkShape, seamarkTopColor, seamarkTopShape, seamarkWaterLevel );

                    if (typeSeaMark != MapSeaMark.TypeSeaMark.UNKNOWN) {

                        MapSeaMark seaMark = new MapSeaMark(this, node.lon, node.lat, seamarkName, typeSeaMark );
                        this.gameObjectsMarks.add(seaMark);
                    }
                } else if( (place != null) || (highway != null) ) {

                    String name = "";
                    if( node.tags.containsKey("name") )
                        name = node.tags.get("name");

                    String maxSpeed = "";
                    if( node.tags.containsKey("maxspeed") )
                        maxSpeed = node.tags.get("maxspeed");

                    MapMark.TypeMark typeMark = MapMark.TypeMark.getTypeMark(place);
                    if( typeMark == MapMark.TypeMark.UNKNOWN )
                        typeMark = MapMark.TypeMark.getTypeMark(highway);

                    if (typeMark != MapMark.TypeMark.UNKNOWN) {

                        long markId = (node.id != null) ? node.id : 0;   // #59 : identite stable de la mark

                        MapMark mark = null;
                        if( typeMark == MapMark.TypeMark.speed_camera )
                            mark = new MapMark(this, markId, node.lon, node.lat, maxSpeed, typeMark, true );
                        else
                            mark = new MapMark(this, markId, node.lon, node.lat, name, typeMark );
                        this.gameObjectsMarks.add(mark);
                    }

                }
            }
        }

        if( relationsForLevel(level) ) {   // F1 uniquement
            count = 0;
            for(OSMElement rel:  data.getRelations().values()) {
                if( (rel.tags != null) && rel.tags.containsKey("type")) {
                    count += 1;
                    String type = rel.tags.get("type");

                    String title = "";
                    if( rel.tags.containsKey("name") )
                        title = rel.tags.get("name");

                    String landuse = "";
                    if( rel.tags.containsKey("landuse") )
                        landuse = rel.tags.get("landuse");

                    String natural = "";
                    if( rel.tags.containsKey("natural") )
                        natural = rel.tags.get("natural");

                    boolean tidal = false;
                    if (rel.tags.containsKey("tidal")) {
                        tidal = rel.tags.get("tidal").equalsIgnoreCase("yes");
                    }

                    GBLog.d( "Chunk", String.format("REL %d %s %s %s %s", count, type, landuse, natural, title));

                    MapArea.TypeArea typeArea = MapArea.TypeArea.UNKNOWN;
                    switch( type ) {

                        case "multipolygon":
                            typeArea = MapArea.TypeArea.getTypeArea(landuse, tidal);
                            if( typeArea == MapArea.TypeArea.UNKNOWN )
                                typeArea = MapArea.TypeArea.getTypeArea(natural, tidal);
                            break;

                        default:
                            break;
                    }

                    if( typeArea != MapArea.TypeArea.UNKNOWN ) {

                        MapArea area = new MapArea(this, title, typeArea, false );

                        // Regrouper les member ways par role, puis COUDRE en anneaux complets
                        // (role vide -> exterieur, convention OSM). Sans couture, l'anneau
                        // eclate de la Seine faisait des triangles/trous.
                        List<List<OSMElement>> outerSegs = new ArrayList<List<OSMElement>>();
                        List<List<OSMElement>> innerSegs = new ArrayList<List<OSMElement>>();
                        for( OSMMember mbr : rel.members ) {
                            if( mbr.way != null && mbr.way.elements != null ) {
                                String role = (mbr.role != null) ? mbr.role : "";
                                if( role.equals("inner") )
                                    innerSegs.add(mbr.way.elements);
                                else
                                    outerSegs.add(mbr.way.elements);
                            }
                        }

                        for( List<OSMElement> ring : stitchRings(outerSegs) ) {
                            ArrayListMapPoint lst = new ArrayListMapPoint(true);
                            for( OSMElement pt : ring )
                                lst.add(new MapPoint(this, pt.lon, pt.lat));
                            area.addListPoints(lst);
                        }
                        for( List<OSMElement> ring : stitchRings(innerSegs) ) {
                            ArrayListMapPoint lst = new ArrayListMapPoint(false);
                            for( OSMElement pt : ring )
                                lst.add(new MapPoint(this, pt.lon, pt.lat));
                            area.addListPoints(lst);
                        }

                        this.gameObjectsAreas.add(area);
                    }
                }

            }
        }

        count = 0;
        for(OSMElement way:  data.getWays().values()) {

            if( way.tags != null) {
                String highway = way.tags.get("highway");
                String landuse = way.tags.get("landuse");
                String natural = way.tags.get("natural");
                String man_made = way.tags.get("man_made");

                boolean isClosed = false;
                if( way.elements.size() > 1 ) {
                    OSMElement fisrtPt = way.elements.get(0);
                    OSMElement lastPt = way.elements.get(way.elements.size() - 1);

                    isClosed = (fisrtPt == lastPt);
                }

                /*
                boolean isArea = isClosed;
                if( landuse != null ) // landuse implies area=yes
                    isArea = true;
                 */
                boolean isArea = false;
                if (way.tags.containsKey("area")) {
                    isArea = way.tags.get("area").equalsIgnoreCase("yes");
                }

                boolean isBuilding = false;
                if (way.tags.containsKey("building")) {
                    isBuilding = way.tags.get("building").equalsIgnoreCase("yes");
                }

                count += 1;

                if (highway != null) { // ROAD

                    String title = "";
                    if (way.tags.containsKey("name"))
                        title = way.tags.get("name");

                    String ref = "";
                    if (way.tags.containsKey("ref"))
                        ref = way.tags.get("ref");


                    String sMaxSpeed = "";
                    if (way.tags.containsKey("maxspeed"))
                        sMaxSpeed = way.tags.get("maxspeed");
                    int maxSpeed = Common.tryParseInt(sMaxSpeed);

                    String surfaceTexture = "";
                    if (way.tags.containsKey("surface"))
                        surfaceTexture = way.tags.get("surface");

                    boolean isTunnel = false;
                    if (way.tags.containsKey("tunnel")) {
                        isTunnel = way.tags.get("tunnel").equalsIgnoreCase("yes");
                    }

                    boolean isBridge = false;
                    if (way.tags.containsKey("bridge")) {
                        isBridge = way.tags.get("bridge").equalsIgnoreCase("yes");
                    }


                    GBLog.d("Chunk", String.format("WAY %d %s %s", count, highway, title));

                    MapRoad.TypeRoad typeRoad = MapRoad.TypeRoad.getTypeRoad(highway);
                    MapArea.TypeArea typeArea = MapArea.TypeArea.getTypeArea(landuse, false);
                    MapRoad road = new MapRoad(this, title, ref, typeRoad, surfaceTexture, isTunnel, isBridge, isClosed, isArea, typeArea, maxSpeed);

                    for (OSMElement pt : way.elements) {
                        road.addPoint(new MapPoint(this, pt.lon, pt.lat));
                    }
                    this.gameObjectsRoads.add(road);
                } else if ((landuse != null) || (natural != null) || (man_made != null)) { // AREA

                    if( !isArea ) {
                        isArea = isClosed;
                        if (landuse != null) // landuse implies area=yes
                            isArea = true;
                    }

                    boolean tidal = false;
                    if (way.tags.containsKey("tidal")) {
                        tidal = way.tags.get("tidal").equalsIgnoreCase("yes");
                    }

                    String title = "";
                    if (way.tags.containsKey("name"))
                        title = way.tags.get("name");

                    if (landuse != null)
                        GBLog.d("Chunk", String.format("WAY(%s LANDUSE) %d %s %s", (isArea ? "AREA" : "NOT AREA"), count, landuse, title));

                    if (natural != null)
                        GBLog.d("Chunk", String.format("WAY(%s NATURAL) %d %s %s", (isArea ? "AREA" : "NOT AREA"), count, natural, title));

                    if (man_made != null)
                        GBLog.d("Chunk", String.format("WAY(%s MAN_MADE) %d %s %s", (isArea ? "AREA" : "NOT AREA"), count, man_made, title));

                    MapArea.TypeArea typeArea = MapArea.TypeArea.getTypeArea(landuse, tidal);
                    if (typeArea == MapArea.TypeArea.UNKNOWN)
                        typeArea = MapArea.TypeArea.getTypeArea(natural, tidal);
                    if (typeArea == MapArea.TypeArea.UNKNOWN)
                        typeArea = MapArea.TypeArea.getTypeArea(man_made, tidal);

                    if (typeArea != MapArea.TypeArea.UNKNOWN) {

                            MapArea area = new MapArea(this, title, typeArea, !isArea);
                            ListMapPoint lst = new ArrayListMapPoint(true);
                            //area.addListPoints(lst);

                            for (OSMElement pt : way.elements) {
                                lst.add(new MapPoint(this, pt.lon, pt.lat));
                            }
                            area.addListPoints(lst); // Add List After add all point (for !isArea)

                        this.gameObjectsAreas.add(area);
                    }

                } else {
                    if( isBuilding )
                        GBLog.d("Chunk", String.format("WAY(BUILDING) %d IGNORED", count ));
                    else
                        GBLog.d("Chunk", String.format("WAY %d IGNORED ???", count ));
                }
            }
        }

        Comparator<MapGameObject> comp = new Comparator<MapGameObject>() {
            @Override
            public int compare(MapGameObject obj1, MapGameObject obj2) {
                String typeObj1 = obj1.getMapObjectType();
                String typeObj2 = obj2.getMapObjectType();

                if( typeObj1.equalsIgnoreCase(typeObj2) ) {
                    int idxObj1 = obj1.getMapObjectSortIndex();
                    int idxObj2 = obj2.getMapObjectSortIndex();

                    if( idxObj1 > idxObj2)
                        return -1;
                    else if( idxObj1 < idxObj2)
                        return 1;
                    else
                        return 0;
                }
                else if( typeObj1.equalsIgnoreCase("Area") )
                    return -1;
                else if( typeObj2.equalsIgnoreCase("Area") )
                    return 1;
                else if( typeObj1.equalsIgnoreCase("RoadArea") )
                    return -1;
                else if( typeObj2.equalsIgnoreCase("RoadArea") )
                    return 1;
                else if( typeObj1.equalsIgnoreCase("AreaRoad") )
                    return -1;
                else if( typeObj2.equalsIgnoreCase("AreaRoad") )
                    return 1;
                else if( typeObj1.equalsIgnoreCase("Road") )
                    return -1;
                else if( typeObj2.equalsIgnoreCase("Road") )
                    return 1;
                else if( typeObj1.equalsIgnoreCase("SeaMark") )
                    return -1;
                else if( typeObj2.equalsIgnoreCase("SeaMark") )
                    return 1;
                else if( typeObj1.equalsIgnoreCase("Mark") )
                    return -1;
                else if( typeObj2.equalsIgnoreCase("Mark") )
                    return 1;
                else
                    return( typeObj1.compareTo(typeObj2));
            }
        };

        Collections.sort(this.gameObjectsAreas, comp );
        Collections.sort(this.gameObjectsRoads, comp );
        Collections.sort(this.gameObjectsMarks, comp );
    }

    /**
     * #86 step 2: fills this chunk from a cached answer instead of the network. The same checks as
     * the download apply -- a file that no longer parses, or that carries an Overpass remark, is an
     * error, and the caller falls back on a download. On success the file's date is refreshed: it is
     * what the cleanup goes by to find the least recently used entries.
     */
    public void loadFromCache(File file, int level) {

        MemLog.log("CacheChunk >>> start");
        GBLog.i("CHUNCK", String.format(Locale.ROOT, "CacheChunk F%d %s", level, file.getName()));

        InputStream in = null;

        try {
            in = new java.io.BufferedInputStream(new java.io.FileInputStream(file));

            OSMData data = OSMData.jsonToOSMData(new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));

            if( data == null )
                throw new RuntimeException("cached file does not parse: " + file.getName());

            if( data.hasRemark() )
                throw new RuntimeException("cached file carries a remark: " + file.getName());

            buildFromData(data, level);

            ChunkCache.touch(file);

        } catch (Exception e) {
            GBLog.e("CHUNCK", "CacheChunk ERR: " + e, e);
            throw new RuntimeException("cache load failed", e);
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignore) {}
            }
            MemLog.log("CacheChunk <<< end");
        }
    }

    // #86: reads out whatever is left of a stream, without keeping it. Used to make the cached copy
    // complete when the JSON parser stopped on the closing brace with bytes still in flight.
    private static void drain(InputStream stream) {
        if( stream == null )
            return;

        byte[] buffer = new byte[8192];

        try {
            while( stream.read(buffer) > 0 ) {
                // nothing to do: the tee writes the bytes as they go past
            }
        } catch( Exception e ) {
            GBLog.d("CHUNCK", "drain stopped: " + e);
        }
    }

    // Highway values dropped from F3 up: paths/cycleways/sidewalks/steps -- not needed
    // above ~80 km/h and the biggest weight in a dense city (footway alone = 16 MB in Paris).
    private static final String HW_MINOR =
            "^(footway|cycleway|service|steps|pedestrian|path|track|bridleway|corridor|platform|construction|proposed|raceway|elevator)$";
    // Major axes kept in F5: motorways down to tertiary (+ links) and generic road.
    private static final String HW_MAJOR =
            "^(motorway|trunk|primary|secondary|tertiary)(_link)?$|^road$";

    /**
     * Speed -> filter level 1..5 (faster = less detail). Bands and per-level data measured
     * on Paris 16 km2 (worst case); measured locally.
     */
    static int filterLevelForSpeed(double kmh) {
        if( kmh < 50 )  return 1;   // F1 = F2 (full whitelist, keeps paths for walking)
        if( kmh < 80 )  return 2;   // F2 whitelist
        if( kmh < 110 ) return 3;   // F3 : F2 without paths/cycleways/sidewalks
        if( kmh < 130 ) return 4;   // F4 : F3 + nodes = radars/enforcement only (no crossings)
        return 5;                    // F5 : F4 + major axes only + no landuse
    }

    private String buildChunkQl() {
        int level = filterLevelForSpeed(speedKmh);
        return buildChunkQl(level);
    }

    /**
     * Overpass QL for the current chunk at a given filter level (1..5). All levels fetch
     * only what this class draws (drops buildings and unused POI = the dead weight that
     * OOMed Paris). Higher levels trim more so a bigger chunk (needed at speed) still fits:
     *   F1/F2 : highway (all subtypes) + waterway/landuse/natural/man_made ; nodes = geometry
     *           + seamark/place/highway. Keeps paths -- for walking.
     *   F3    : same but highway drops HW_MINOR (paths/cycleways/sidewalks/steps).
     *   F4    : F3 but node marks = only speed_camera + enforcement (drops ~19k crossing/
     *           signal nodes that cost 3.9 MB in Paris for 8 radars).
     *   F5    : F4 but highway = HW_MAJOR only, and NO landuse.
     * node(w.w) is NOT optional: createIndexes() resolves way.nodes against this payload
     * and empties a way whose nodes are missing ("BAD WAY: incomplete Data").
     * Overpass wants (S,W,N,E), the reverse of the v0.6 URL. Never 'out center'/'out skel'
     * (OSMElement has no center field -> Gson drops the coords).
     */

    // F1 (basse vitesse : marche/bateau) est le seul niveau qui tire les relations
    // multipolygon. Les grandes étendues eau/forêt (ex. le Grand Étang de Trainou)
    // n'existent QUE comme relations et sont invisibles sinon. Aux vitesses > F1 on les
    // laisse tomber (détail inutile, mémoire préservée). Politique en un seul endroit :
    // pilote à la fois la requête (buildChunkQl) et le parse (dlChunk_OverpassApi).
    // #86: the tag values the app knows how to draw, as an Overpass regex, built from
    // MapArea.TypeArea so that the query and the renderer can never drift apart: a value added to
    // the enum is fetched as soon as it is drawn. UNKNOWN is the renderer's "cannot draw", not a
    // tag value worth asking for.
    //
    // Built on first use, NOT when Chunk is loaded: TypeArea's constants call Android colour APIs,
    // and tying Chunk's own class initialisation to them made the whole class unusable wherever
    // those are unavailable (unit tests, for one).
    private static volatile String drawnAreaValues = null;

    static String drawnAreaValues() {
        String values = drawnAreaValues;

        if( values == null ) {
            values = buildDrawnAreaValues();
            drawnAreaValues = values;
        }

        return values;
    }

    private static String buildDrawnAreaValues() {
        java.util.TreeSet<String> values = new java.util.TreeSet<>();

        for( MapArea.TypeArea type : MapArea.TypeArea.values() ) {
            if( type != MapArea.TypeArea.UNKNOWN )
                values.add(type.toString());
        }

        StringBuilder regex = new StringBuilder("^(");
        for( String value : values ) {
            if( regex.length() > 2 )
                regex.append('|');
            regex.append(value);
        }

        return regex.append(")$").toString();
    }

    private boolean relationsForLevel(int level) {
        return level == 1;
    }

    // Coud les member ways d'un multipolygon en anneaux fermes complets. L'anneau exterieur
    // d'une grande relation (ex. la Seine) est eclate sur plusieurs member ways OUVERTS qu'il
    // faut raccorder bout-a-bout (extremites = meme node id) ; sinon chaque segment se referme
    // par une corde droite -> triangles/trous parasites. Une way deja fermee ressort telle quelle.
    private List<List<OSMElement>> stitchRings(List<List<OSMElement>> segments) {
        List<List<OSMElement>> remaining = new ArrayList<List<OSMElement>>();
        for (List<OSMElement> s : segments)
            if (s != null && s.size() >= 2)
                remaining.add(new ArrayList<OSMElement>(s));

        List<List<OSMElement>> rings = new ArrayList<List<OSMElement>>();
        while (!remaining.isEmpty()) {
            List<OSMElement> ring = remaining.remove(remaining.size() - 1);

            boolean grew = true;
            while (grew && !isClosedRing(ring)) {
                grew = false;
                for (int i = 0; i < remaining.size(); i++) {
                    List<OSMElement> seg = remaining.get(i);
                    OSMElement rFirst = ring.get(0);
                    OSMElement rLast  = ring.get(ring.size() - 1);
                    OSMElement sFirst = seg.get(0);
                    OSMElement sLast  = seg.get(seg.size() - 1);

                    if (sameNode(rLast, sFirst)) {              // ring.fin -> seg.debut
                        for (int k = 1; k < seg.size(); k++)
                            ring.add(seg.get(k));
                    } else if (sameNode(rLast, sLast)) {        // ring.fin -> seg.fin (seg inverse)
                        for (int k = seg.size() - 2; k >= 0; k--)
                            ring.add(seg.get(k));
                    } else if (sameNode(rFirst, sLast)) {       // seg.fin -> ring.debut (prepend)
                        for (int k = seg.size() - 2; k >= 0; k--)
                            ring.add(0, seg.get(k));
                    } else if (sameNode(rFirst, sFirst)) {      // seg.debut -> ring.debut (prepend inverse)
                        for (int k = 1; k < seg.size(); k++)
                            ring.add(0, seg.get(k));
                    } else {
                        continue;
                    }
                    remaining.remove(i);
                    grew = true;
                    break;
                }
            }
            rings.add(ring);   // ferme, ou best-effort si la donnee a un trou
        }
        return rings;
    }

    private boolean isClosedRing(List<OSMElement> ring) {
        return ring.size() >= 4 && sameNode(ring.get(0), ring.get(ring.size() - 1));
    }

    private boolean sameNode(OSMElement a, OSMElement b) {
        return a != null && b != null && a.id != null && b.id != null
            && a.id.longValue() == b.id.longValue();
    }

    private String buildChunkQl(int level) {
        String bbox = String.format(Locale.ROOT, "%.6f,%.6f,%.6f,%.6f",
                latitudeS, longitudeW, latitudeN, longitudeE);
        StringBuilder ql = new StringBuilder();

        ql.append(String.format(Locale.ROOT, "[out:json][timeout:%d][maxsize:%s];",
                OVERPASS_TIMEOUT_S, OVERPASS_MAXSIZE));

        // ---- ways ----
        ql.append("(");
        if( level <= 2 )
            ql.append("way[\"highway\"](").append(bbox).append(");");
        else if( level <= 4 )
            ql.append("way[\"highway\"][\"highway\"!~\"").append(HW_MINOR).append("\"](").append(bbox).append(");");
        else
            ql.append("way[\"highway\"][\"highway\"~\"").append(HW_MAJOR).append("\"](").append(bbox).append(");");
        ql.append("way[\"waterway\"](").append(bbox).append(");");
        if( level <= 4 )                                  // F5 drops landuse
            ql.append("way[\"landuse\"](").append(bbox).append(");");
        ql.append("way[\"natural\"](").append(bbox).append(");");
        ql.append("way[\"man_made\"](").append(bbox).append(");");
        ql.append(")->.w;");

        // ---- nodes : geometry of kept ways + mark nodes ----
        ql.append("(");
        ql.append("node(w.w);");
        ql.append("node[\"seamark:type\"](").append(bbox).append(");");
        ql.append("node[\"place\"](").append(bbox).append(");");
        if( level <= 3 )                                  // F1/F2/F3 : all highway nodes
            ql.append("node[\"highway\"](").append(bbox).append(");");
        else {                                            // F4/F5 : only radars + enforcement
            ql.append("node[\"highway\"=\"speed_camera\"](").append(bbox).append(");");
            ql.append("node[\"enforcement\"](").append(bbox).append(");");
        }
        ql.append(")->.n;");

        if( relationsForLevel(level) ) {
            // Only the multipolygons carrying a landuse/natural we draw (getTypeArea): this drops
            // the mass of building/boundary multipolygons (982/1357 in Paris). '.r >;' then fetches
            // the member ways and their nodes so the polygon (e.g. the Grand Etang) is complete.
            //
            // #86: "natural" is restricted to the values we actually DRAW. Accepting any value let
            // natural=bay (the Bay of Biscay, 3214 members) and natural=peninsula (a whole region, 1217
            // members) through, with every one of their members: 366 918 nodes and ~40 MB for a
            // 2 km coastal chunk, for two areas getTypeArea() maps to UNKNOWN and never draws.
            ql.append("(rel[\"type\"=\"multipolygon\"][\"natural\"~\"").append(drawnAreaValues())
              .append("\"](").append(bbox).append(");");
            ql.append("rel[\"type\"=\"multipolygon\"][\"landuse\"](").append(bbox).append(");)->.r;");
            ql.append("(.w; .n; .r; .r >;);");
        }
        else {
            ql.append("(.w; .n;);");
        }

        ql.append("out body;");

        return ql.toString();
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            GBLog.e("CHUNCK", "urlEncode KO", e);
            return s;
        }
    }


    /**
     * Free the per-instance road/area textures of this chunk. MUST only be called on a
     * chunk that is no longer displayed (done on the game thread in GameSurface.update,
     * once the render target has been swapped to the new chunk). Marks keep their static
     * shared atlases and are left untouched.
     */
    public void disposeTextures() {
        disposeTexturesOf(gameObjectsRoads);
        disposeTexturesOf(gameObjectsAreas);
    }

    private static void disposeTexturesOf(List<MapGameObject> objs) {
        for( MapGameObject o : objs ) {
            if( o instanceof MapRoad )
                ((MapRoad) o).disposeTextures();
            else if( o instanceof MapArea )
                ((MapArea) o).disposeTextures();
        }
    }

    public boolean coordonateInChunk(double latitude, double longitude)
    {
        return insideWithMargin(latitude, longitude, areaMarginKM);
    }

    /**
     * #87: true when the position, widened by marginKm in the four directions, still fits in the
     * bbox. coordonateInChunk() is this test with the full areaMarginKM -- where a download of the
     * next chunk is started; the loading loop also uses a fraction of it, closer to the edge, as the
     * point where it switches to another chunk at once.
     */
    public boolean insideWithMargin(double latitude, double longitude, double marginKm)
    {
        boolean bOK = true;

        //TODO: to be reviewed, latitude spans -90..+90 and longitude -180..+180

        double latN = latitudeAddKm(latitude, marginKm);
        double latS = latitudeAddKm(latitude, -marginKm);
        double longE = longitudeAddKm(longitude, latitude, marginKm);
        double longW = longitudeAddKm(longitude, latitude, -marginKm);

        if( latN > latitudeN )
            bOK = false;
        if( latS < latitudeS )
            bOK = false;
        if( longE > longitudeE )
            bOK = false;
        if( longW < longitudeW )
            bOK = false;

        return bOK;
    }

    // True si (lat,lon) est dans la bbox BRUTE du chunk (sans marge). Un reload normal
    // se déclenche en restant DANS le chunk (la marge est devant) ; une position HORS de
    // la bbox = téléport/saut (ex. redémarrage loin du dernier spot restauré). L'appelant
    // s'en sert pour sauter la temporisation anti-martèlement dans ce cas.
    public boolean containsRaw(double latitude, double longitude)
    {
        return latitude  >= latitudeS && latitude  <= latitudeN
            && longitude >= longitudeW && longitude <= longitudeE;
    }

    public boolean coordonateInLand(double latitude, double longitude)
    {
        // Current time in nanoseconds
        long startTime = System.nanoTime();

        MapPoint myPos = new MapPoint(this, longitude, latitude);
        boolean inLand = false;

        for (MapArea land : gameObjectsLands) {
            if (land.contains(myPos)) {
                inLand = true;
                break;
            }
        }

        long endTime = System.nanoTime();

        // PERF : sub-millisecond resolution and the size of the walk. Replaces a line that
        // rounded to the millisecond, which showed a 700 us scan as "0ms". Formatted only when
        // Debug mode lets it through: counting the points has a cost of its own.
        if( GBLog.isDebug() )
            GBLog.d( "PERF", String.format(Locale.US,
                    "coordonateInLand: %.2f ms over %d polygons / %d points -> %s",
                    (endTime - startTime) / 1000000.0,
                    gameObjectsLands.size(), perfLandPointCount(),
                    inLand ? "land" : "water") );

        return inLand;
    }

    public GameSurface getGameSurface() {
        return this.gameSurface;
    }

    public void setGameSurface(GameSurface gameSurface) {

        if( this.gameSurface != gameSurface ) {
            this.gameSurface = gameSurface;

            if( this.gameSurface != null ) {
                contextScaleObjects();
            }
        }
    }

    // #53 : les x,y des objets du chunk sont figes par contextScale() a partir de la taille utile de la
    // surface (max(largeur, hauteur)), il faut donc les recalculer chaque fois que cette taille change.
    // Ce recalcul n'etait jusqu'ici qu'un effet de bord du changement de lien chunk->surface ci-dessus :
    // un declencheur faux dans les deux sens, absent quand la fenetre est redimensionnee sur la meme
    // surface, et intempestif quand on cassait le lien au retour d'appli. Il est desormais appelable
    // seul, par le thread de rendu (GameSurface.update), qui est le seul a dessiner ces objets.
    public void contextScaleObjects() {

        for (MapGameObject go : gameObjectsAreas) {
            go.contextScale();
        }

        for (MapGameObject go : gameObjectsRoads) {
            go.contextScale();
        }

        for (MapGameObject go : gameObjectsMarks) {
            go.contextScale();
        }

        AppManager _am = AppManager.getInstance();
        for(PersonalMark go: _am.getMarksEnumerable()) {
            if( go.getChunk() == this ) {
                go.contextScale();
            }
        }

        for(PersonalMark go: _am.getProximityMarksEnumerable()) {
            if( go.getChunk() == this ) {
                go.contextScale();
            }
        }
    }


    // #59 : le "son coupe" d'une alarme vit sur l'instance de la mark (GameObject.muted), or les marks
    // du chunk sont RECREEES a chaque chargement : sans report, l'alarme repartait toute seule sur la
    // MEME mark des que le chunk se rechargeait. Appele par le thread de chargement, avant que le
    // nouveau chunk ne soit publie : le thread de rendu n'en paie rien.
    public void inheritMutedMarksFrom(Chunk oldChunk) {

        if( oldChunk == null )
            return;

        HashSet<String> mutedKeys = new HashSet<String>();

        for( MapGameObject go: oldChunk.gameObjectsMarks ) {

            if( !(go instanceof MapPoint) )
                continue;

            MapPoint mark = (MapPoint) go;
            if( !mark.isMuted() )
                continue;

            String key = mutedKey(go.getMapObjectType(), mark.getId());
            if( key != null )
                mutedKeys.add(key);
        }

        if( mutedKeys.isEmpty() )
            return;

        for( MapGameObject go: gameObjectsMarks ) {

            if( !(go instanceof MapPoint) )
                continue;

            MapPoint mark = (MapPoint) go;

            String key = mutedKey(go.getMapObjectType(), mark.getId());
            if( (key != null) && mutedKeys.contains(key) )
                mark.setMuted(true);
        }
    }

    // #59 : cle d'identite d'une mark, stable d'un chunk a l'autre. Le type est dans la cle car les
    // id de nodes OSM et les id en base des PersonalMark vivent dans deux espaces qui se recouvrent.
    // null = objet sans identite (getId() == 0), donc non reportable.
    private static String mutedKey(String type, long id) {

        if( id == 0 )
            return null;

        return type + "#" + id;
    }


    public void update() {
        for(MapGameObject go: gameObjectsAreas) {
            go.update();
        }

        for(MapGameObject go: gameObjectsRoads) {
            go.update();
        }

        for(MapGameObject go: gameObjectsMarks) {
            go.update();
        }

        AppManager _am = AppManager.getInstance();
        for(MapGameObject go: _am.getMarksEnumerable()) {
            go.update();
        }
    }

    public void draw(Canvas canvas)  {

        //getTile(0, 0);

        // PERF : timing the areas one by one is only worth its own cost while Debug mode is on.
        // The per-category totals below are always measured -- three timestamps a frame -- since
        // they feed the "Draw" line of the debug panel.
        boolean perfDetail = GBLog.isDebug();

        long tPerf0 = System.nanoTime();

        for( int layer = 0; layer<2; layer++) {
            for(MapGameObject go: gameObjectsAreas) {
                if( go.isVisible() ) {
                    if( perfDetail ) {
                        long t0 = System.nanoTime();
                        go.draw(canvas, layer);
                        perfArea(go, System.nanoTime() - t0);
                    } else {
                        go.draw(canvas, layer);
                    }
                }
            }
        }

        long tPerf1 = System.nanoTime();

        for( int layer = 0; layer<5; layer++) {
            for(MapGameObject go: gameObjectsRoads) {
                if( go.isVisible() )
                    go.draw(canvas, layer);
            }
        }

        long tPerf2 = System.nanoTime();

        for( int layer = 0; layer<2; layer++) {
            for(MapGameObject go: gameObjectsMarks) {
                if( go.isVisible() )
                    go.draw(canvas, layer);
            }
        }

        long tPerf3 = System.nanoTime();
        perfDraw(tPerf1 - tPerf0, tPerf2 - tPerf1, tPerf3 - tPerf2);

        AppManager _am = AppManager.getInstance();

        // #97 : already in draw order (north to south, then west to east, alarming marks last),
        // sorted off the render thread. The main selected mark is held back and painted last, on
        // top, but only if it is in the list: a removed mark is never painted again.
        List<PersonalMark> marks = _am.getMarksInDrawOrder();
        PersonalMark shown = _am.getShownMark();

        for( int layer = 0; layer<2; layer++) {

            /*
            Iterator<MapGameObject> iterator= _am.getMarks().iterator();
            while(iterator.hasNext()) {
                Explosion explosion = iterator.next();
            }
            */
            PersonalMark shownFound = null;

            for(PersonalMark go: marks) {
                if( go == shown ) {
                    shownFound = go;
                    continue;
                }

                if( go.isVisible() )
                    go.draw(canvas, layer);
            }

            if( (shownFound != null) && shownFound.isVisible() )
                shownFound.draw(canvas, layer);
        }

    }

    // ------------------------------------------------------------------ PERF
    // Where the per-frame recording time goes, land polygons told apart from the other areas.
    // Feeds the "Draw" and "Land" lines of the debug panel, and a log line when Debug mode is on.

    private long _perfLandNs = 0;
    private long _perfOtherAreaNs = 0;
    private long _perfAreasNs = 0;
    private long _perfRoadsNs = 0;
    private long _perfMarksNs = 0;
    private int  _perfLandCalls = 0;
    private int  _perfFrames = 0;
    private long _perfLastLogMs = 0;

    private void perfArea(MapGameObject go, long deltaNs) {
        if( (go instanceof MapArea) && (((MapArea) go).getTypeArea() == MapArea.TypeArea.EARTH_LAND) ) {
            _perfLandNs += deltaNs;
            _perfLandCalls++;
        } else {
            _perfOtherAreaNs += deltaNs;
        }
    }

    private void perfDraw(long areasNs, long roadsNs, long marksNs) {
        _perfAreasNs += areasNs;
        _perfRoadsNs += roadsNs;
        _perfMarksNs += marksNs;
        _perfFrames++;

        long nowMs = System.currentTimeMillis();

        if( _perfLastLogMs == 0 ) {
            _perfLastLogMs = nowMs;
            return;
        }

        if( (nowMs - _perfLastLogMs) < 1000 )
            return;

        double n = _perfFrames;
        double areas = _perfAreasNs / 1000000.0 / n;
        double roads = _perfRoadsNs / 1000000.0 / n;
        double marks = _perfMarksNs / 1000000.0 / n;

        // Debug panel, "Draw" and "Land" lines.
        AppManager _am = AppManager.getInstance();
        _am.setDrawTimes(areas, roads, marks);
        _am.setLandStats(perfLandCount(), perfLandPointCount());

        if( GBLog.isDebug() )
            GBLog.d("PERF", String.format(Locale.US,
                    "record: areas=%.1f (land=%.1f other=%.1f) roads=%.1f marks=%.1f ms/frame, land draws=%.0f/frame",
                    areas,
                    _perfLandNs  / 1000000.0 / n,
                    _perfOtherAreaNs / 1000000.0 / n,
                    roads, marks,
                    _perfLandCalls / n));

        _perfLandNs = 0;
        _perfOtherAreaNs = 0;
        _perfAreasNs = 0;
        _perfRoadsNs = 0;
        _perfMarksNs = 0;
        _perfLandCalls = 0;
        _perfFrames = 0;
        _perfLastLogMs = nowMs;
    }

    // Land polygons held by this chunk, and their total point count: the size of what the
    // renderer has to push every frame, and what coordonateInLand walks every second.
    public int perfLandPointCount() {
        int total = 0;

        for( MapArea land : gameObjectsLands )
            total += land.pointCount();

        return total;
    }

    public int perfLandCount() {
        int total = 0;

        for( MapArea land : gameObjectsLands )
            total += land.ringCount();

        return total;
    }

    public static double latitudeAddKm(double latitude, double km)
    {
        double r_earth = 6378.137;
        double pi = Math.PI;
        double new_latitude = latitude + (km / r_earth) * (180 / pi);
        return new_latitude;
    }

    public static double latitudeGetKmFrom(double latitude,double latitude0)
    {
        double r_earth = 6378.137;
        double pi = Math.PI;
        double km = ((latitude0 - latitude) / (180 / pi)) * r_earth;
        return km;

        //latitude0 = latitude + (km / r_earth) * (180 / pi);
        //latitude0 - latitude =  (km / r_earth) * (180 / pi);
        //(km / r_earth) = (latitude0 - latitude) / (180 / pi);
        //km = ((latitude0 - latitude) / (180 / pi)) * r_earth;
    }

    public static double longitudeAddKm(double longitude, double latitude, double km)
    {
        double r_earth = 6378.137;
        double pi = Math.PI;
        double new_longitude = longitude + (km / r_earth) * (180 / pi) / Math.cos(latitude * pi / 180);
        return new_longitude;
    }

    public static double longitudeGetKmFrom(double longitude, double latitude, double longitude0)
    {
        double r_earth = 6378.137;
        double pi = Math.PI;
        double km = ((longitude0 - longitude) * Math.cos(latitude * pi / 180) / (180 / pi)) * r_earth;
        return km;

        //longitude0 = longitude + (km / r_earth) * (180 / pi) / Math.cos(latitude * pi / 180);
        //longitude0 - longitude = (km / r_earth) * (180 / pi) / Math.cos(latitude * pi / 180);
        //(km / r_earth) = (longitude0 - longitude) * Math.cos(latitude * pi / 180) / (180 / pi)
        //km = ((longitude0 - longitude) * Math.cos(latitude * pi / 180) / (180 / pi)) * r_earth;
    }

    public static double getKmFrom(double longitude, double latitude, double longitude0, double latitude0) {
        double longKm = longitudeGetKmFrom( longitude,  latitude, longitude0);
        double latKm = latitudeGetKmFrom(latitude, latitude0);

        return Math.sqrt(longKm*longKm+latKm*latKm);
    }

    static public double initialBearing (double lat1, double long1, double lat2, double long2)
    {
        return (_bearing(lat1, long1, lat2, long2) + 360.0) % 360;
    }

    static public double finalBearing(double lat1, double long1, double lat2, double long2)
    {
        return (_bearing(lat2, long2, lat1, long1) + 180.0) % 360;
    }

    static private double _bearing(double lat1, double long1, double lat2, double long2)
    {
        double degToRad = Math.PI / 180.0;
        double phi1 = lat1 * degToRad;
        double phi2 = lat2 * degToRad;
        double lam1 = long1 * degToRad;
        double lam2 = long2 * degToRad;

        return Math.atan2(Math.sin(lam2-lam1)*Math.cos(phi2),
                Math.cos(phi1)*Math.sin(phi2) - Math.sin(phi1)*Math.cos(phi2)*Math.cos(lam2-lam1)
        ) * 180/Math.PI;
    }



    public static double measureKm(double lat1, double lon1, double lat2, double lon2) {
        double r_earth = 6378.137; // Radius of earth in KM
        double dLat = lat2 * Math.PI / 180 - lat1 * Math.PI / 180;
        double dLon = lon2 * Math.PI / 180 - lon1 * Math.PI / 180;
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double d = r_earth * c;
        return d;

        //assuming lower distances where the floor is plane,
        // you have also altitudes alt1 and alt2,
        // and dm is the distance in meters (the result of measure function above).
        // You can use the hypothenuse function of JS Math.hypot(x, y),
        // where x is dm and y is max(alt1, alt2) - min(alt1, alt2).
    }

    // Straight-line distance when both altitudes (metres) are known: the hypotenuse of the ground
    // distance and the height difference. Falls back to the ground distance otherwise.
    public static double measureKm(double lat1, double lon1, Double alt1, double lat2, double lon2, Double alt2) {
        double groundKm = measureKm(lat1, lon1, lat2, lon2);
        if (alt1 == null || alt2 == null) {
            return groundKm;
        }
        return Math.hypot(groundKm, (alt1 - alt2) / 1000.0);
    }

    public static double measureMiles(double lat1, double lon1, double lat2, double lon2) {
        double distanceInKm = measureKm( lat1,  lon1,  lat2,  lon2);
        return distanceInKm * 0.621371;
    }


    public double getX(double longitude, int scale)
    {
        double kmX = longitudeGetKmFrom( longitudeW,  latitudeN, longitude);
        //double X = (int)(kmX * scale / (areaKM+2*areaMarginKM));
        double X = (double)(kmX * scale / (areaKM+2*areaMarginKM));

        return X;
    }

    public double getY(double latitude, int scale)
    {
        double kmY = latitudeGetKmFrom( latitudeN,  latitude);
        //double Y = -1*(int)(kmY * scale / (areaKM+2*areaMarginKM));
        double Y = -1*(double)(kmY * scale / (areaKM+2*areaMarginKM));
        return Y;
    }

    public double getLatitude( double y,  int scale) {
        /*
            Y = -1*(kmY * scaleY / (areaKM+2*areaMarginKM)
            -1*Y*(areaKM+2*areaMarginKM)/scaleY = kmY
         */
        double kmY = -1*y*(areaKM+2*areaMarginKM)/scale;
        double latitude = latitudeAddKm(latitudeN, kmY);

        return latitude;
    }

    public double getLongitude(double x, int scale) {
        /*
            X = kmX * scaleX / (areaKM+2*areaMarginKM)
            X*(areaKM+2*areaMarginKM)/scaleX = kmX
         */
        double kmX = x*(areaKM+2*areaMarginKM)/scale;
        double longitude = longitudeAddKm(longitudeW,  latitudeN, kmX);

        return longitude;
    }

    public double getWidthKM() {
        double width = (areaKM+2*areaMarginKM);

        return width;
    }

    public double getHeightKM() {
        double height = (areaKM+2*areaMarginKM);

        return height;
    }

    public static double getTile(double x, double y) {
        double r_earth = 6378.137; // Radius of earth in KM
        double surface_earth = 4 * Math.PI * r_earth * r_earth; // Radius of earth in KM
        long l_surface_earth = (long)surface_earth;
        if( surface_earth > l_surface_earth )
            l_surface_earth += 1;

        double nb_tiles_5000m = surface_earth / 5;
        long l_nb_tiles_5000m = (long)nb_tiles_5000m;

        if( nb_tiles_5000m > l_nb_tiles_5000m )
            l_nb_tiles_5000m += 1;

        double nb_tiles_20m = surface_earth / 0.02;
        long l_nb_tiles_20m = (long)nb_tiles_20m;

        if( nb_tiles_20m > l_nb_tiles_20m )
            l_nb_tiles_20m += 1;

        double ret_val = l_nb_tiles_5000m + l_nb_tiles_20m + l_surface_earth;

        return ret_val;
    }
}
