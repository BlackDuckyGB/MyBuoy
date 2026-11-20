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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MapArea extends GameObject implements MapGameObject {


    @Override
    public String getMapObjectType(){
        String objType = "Area";

        if( this.road != null ) { // Area Mode
            objType = "AreaRoad";
        }

        return objType;
    }


    @Override
    public int getMapObjectSortIndex() {
        return typeArea.ordinal();
    }

    public enum TypeArea {
        ROAD_AREA("road_area",Color.RED,Color.RED), // MapRoadAera  - Color Set by MapRoadArea Class
        WATER("water", "#a9d2de", "#a9d2de"),
        BASIN("basin", "#a9d2de", "#a9d2de"),

        WASTEWATER_PLANT("wastewater_plant", "#ebdae8", "#ebdae8"),

        FARMYARD("farmyard", "#e5ceab", "#f4dcba"),
        INDUSTRIAL( "industrial", "#ebdae8", "#ebdae8"),

        RESIDENTIAL( "residential", "#e0dfde", "#e0dfde"),

        WOOD("wood","#add09e","#add09e"),
        FOREST("forest","#add09e","#add09e"),
        FARMLAND("farmland", "#edf0d4", "#edf0d4"),
        MEADOW("meadow", "#cdeaaf", "#cdeaaf"),
        GRASS( "grass", "#cdeaaf", "#cdeaaf"),
        SCRUB("scrub", "#c7d6ab", "#c7d6ab"),
        HEATH("heath", "#d6d89f", "#d6d89f"),


        PIER("pier", "#f2efe8", "#f2efe8"),
        BREAKWATER("breakwater", "#a9aaa9", "#a9aaa9"),



        BARE_ROCK("bare_rock", "#5e5e4f", "#5e5e4f"),
        SAND("sand", "#f5e8c6","#f5e8c6"),
        WETLAND("wetland", "#64adee","#64adee"),



        PLAT_NURSERY("plant_nursery", "#addfa2", "#addfa2"),
        ORCHARD("orchard", "#addfa2", "#addfa2"),
        CEMETERY( "cemetery", "#aacaaf", "#aacaaf"),
        VINEYARD("vineyard", "#addfa2", "#addfa2"),
        RETAIL("retail", "#fed5d1", "#fed5d1"),
        ALLOTMENTS("allotments", "#c9e0be", "#c9e0be"),


        // Beach + surface=sand (presenter avec des petits point en plus /
        // une beach peut etre recouverte d'eau
        BEACH("beach", "#fff0ba", "#fff0ba"),


        //BAY("bay", "#a9d2de", "#a9d2de"),
        //PENINSULA("peninsula", Color.MAGENTA, Color.BLUE),

        //COASTLINE("coastline", "#a9d2de", "#a9d2de"),
       // COASTLINE("coastline", Color.RED, Color.RED),


        EARTH_LAND("earth_land", "#f2efe8", "#f2efe8"), // For Land Polygones Database

        BARE_ROCK_TIDAL("bare_rock", "#5e5e4f", "#5e5e4f", true),
        SAND_TIDAL("sand", "#f5e8c6","#f5e8c6", true),
        WETLAND_TIDAL("wetland", "#64adee","#64adee", true),

        UNKNOWN("unknown", Color.RED, Color.RED);

        private String name;
        Paint paintLayer0;
        Paint paintLayer1;
        private boolean tidal;

        private TypeArea(String name, int color0, int color1, boolean tidal) {
            this.name = name;
            this.tidal = tidal;

            paintLayer0 = new Paint();
            paintLayer0.setAntiAlias(true);
            paintLayer0.setStyle(Paint.Style.FILL_AND_STROKE);
            paintLayer0.setStrokeWidth(10);
            paintLayer0.setColor(color0);

            paintLayer1 = new Paint();
            paintLayer1.setAntiAlias(true);
            paintLayer1.setStyle(Paint.Style.FILL);
            paintLayer1.setColor(color1);

            if( this.tidal ) {
                paintLayer0.setAlpha(50);
                paintLayer1.setAlpha(50);
            }
        }

        private TypeArea(String name, int color0, int color1) {
            this( name, color0, color1, false);
        }

        private TypeArea(String name, String color0, String color1)  {
            this( name, Color.parseColor(color0), Color.parseColor(color1), false);
        }

        private TypeArea(String name, String color0, String color1, boolean tidal)  {
            this( name, Color.parseColor(color0), Color.parseColor(color1), tidal);
        }

        public Paint getPaintLayer0() {
            return paintLayer0;
        }

        public Paint getPaintLayer1() {
            return paintLayer1;
        }

        public boolean getTidal() {
            return tidal;
        }

        @Override
        public String toString() {
            return name;
        }

        public static TypeArea getTypeArea( String key, boolean tidal ) {
            TypeArea typeArea = UNKNOWN;

            /*
            tidal -> cover bye the tidal range (recouvert par la marée)
            waterway=river
            natural=water
            natural=sand
            natural=mud
            natural=shingle
            natural=bare_rock
             */


            if( (key != null) && (key.length() > 0) ) {
                if( tidal )
                    GBLog.d( "TYPEAREA", String.format("TIDAL: %s", key));

                for (TypeArea typ : TypeArea.values()) {

                    if (typ.toString().equals(key)) {

                        if( typeArea == UNKNOWN)
                            typeArea = typ;
                        else if( typ.getTidal() == tidal )
                            typeArea = typ;

                        if( typeArea.getTidal() == tidal )
                            break;
                    }
                }
            }

            if( (typeArea == UNKNOWN) && (key != null) && (key.length() > 0) )
                GBLog.d( "TYPEAREA", String.format("Area type:%s ???", key));

            return typeArea;
        }
    }



    private final Chunk chunk;

    private final String title;
    private final TypeArea typeArea;

    private final MapAreaRoad road;

    private List<ListMapPoint> points = null;

    private Bitmap[] allTextures = null;

    private int rowUsing = 0;
    private int colUsing = 0;
    private long lastUpdateNanoTime =-1;

    protected Paint paintLayer0 = null;
    protected Paint paintLayer1 = null;

    // Path combiné caché : outer (NON_ZERO) moins les trous (Path.op DIFFERENCE), UNE fois,
    // en coords locales du chunk -> le pan n'est qu'un canvas.translate, zéro reconstruction
    // par frame. Invalidé dans contextScale().
    private Path combinedPath = null;

    // combinedPath cut into square tiles of the screen's long side (200 m), so that a frame hands
    // the canvas only the tiles the screen actually covers -- at most four -- instead of one path
    // spanning the whole 2 km scrollable area. Null entries are tiles with nothing in them (open
    // sea): they are never built nor drawn. A tile entirely inland comes out of the intersection
    // as a full square, which is what must be painted there.
    private Path[] tiles = null;
    private int tileCount = 0;   // tiles per side
    private int tileSize = 0;    // px

    // Rings already cut to the scrollable area, as flat x,y arrays. Kept so that the tiles are cut
    // from these (a handful of points) and not from the raw geometry (tens of thousands).
    private java.util.List<float[]> clippedOuter = null;
    private java.util.List<float[]> clippedInner = null;


    private double widthKm = 0;
    private double heightKm = 0;
    private double longitude = 0;
    private double latitude = 0;


    public MapArea(Chunk chunk, String title, TypeArea typeArea, boolean isRoad) {
        super();

        this.chunk = chunk;
        this.title = title;
        this.typeArea = typeArea;

        this.points = new ArrayList<ListMapPoint>();


        paintLayer0 = new Paint(this.typeArea.getPaintLayer0());
        paintLayer1 = new Paint(this.typeArea.getPaintLayer1());

        if( isRoad ) {
            this.road = new MapAreaRoad(chunk, this);
        } else {
            this.road = null;
        }
    }

    public TypeArea getTypeArea() {
        return typeArea;
    }

    public String getTitle() {
        return title;
    }

    public Paint getPaintLayer0() {
        return paintLayer0;
    }

    public Paint getPaintLayer1() {
        return paintLayer1;
    }

    public void addListPoints( ListMapPoint lstPts ) {

        if( road != null ) { // Road Mode
            for (MapPoint pt : lstPts) {
                road.addPoint(pt);
            }
        } else {

            if (!points.contains(lstPts)) {
                points.add(lstPts);
            }
        }
    }
    /*
    public void addPoint( MapPoint pt ) {
        if( !points.contains(pt) ) {
            points.add(pt);
        }
    }
*/

    public Bitmap getCurrentBitmap()  {
        Bitmap texture = null;

        if( allTextures != null )
            texture = allTextures[rowUsing*colCount+colUsing];

        return texture;
    }

    /**
     * Recycle this area's PER-INSTANCE textures (native memory). Only ever called on a
     * chunk that is no longer the render target (see GameSurface.update), so nothing is
     * drawing these bitmaps anymore. Static/shared atlases are never touched.
     */
    public void disposeTextures() {
        if( allTextures != null ) {
            for( Bitmap b : allTextures ) {
                // The shared texture belongs to no chunk: see MapRoad.disposeTextures().
                if( b != null && !b.isRecycled() && !SharedTextures.isShared(b) )
                    b.recycle();
            }
            allTextures = null;
        }
    }

    public void contextScale() {
        GameSurface gameSurface = this.chunk.getGameSurface();

        if( gameSurface != null ) {
            // Shared, decoded once for the whole app (SharedTextures).
            Bitmap texture = SharedTextures.point(gameSurface.getResources());
            this.setImage(texture, 1, 1);

            this.allTextures = new Bitmap[colCount * rowCount];

            for (int col = 0; col < this.colCount; col++) {
                for (int row = 0; row < this.rowCount; row++) {
                    this.allTextures[row * this.colCount + col] = this.createSubImageAt(row, col);
                }
            }
        }

        double minX = 0, maxX = 0, minY = 0, maxY = 0;
        boolean bFirst = true;

        combinedPath = null;   // l'échelle a pu changer -> reconstruire le Path combiné
        for (ListMapPoint lstPts : points) {
            lstPts.invalidatePath();   // #53 : idem MapRoad, le Path cache est en coordonnees de zone
        }

        for(List<MapPoint> lstPts:  points) {

            for (MapPoint pt : lstPts) {
                pt.contextScale();

                if (bFirst) {
                    minX = pt.getRealX();
                    minY = pt.getRealY();
                    maxX = pt.getRealX() + pt.getWidth();
                    maxY = pt.getRealY() + pt.getHeight();

                    bFirst = false;
                } else {
                    if (minX > pt.getRealX())
                        minX = pt.getRealX();
                    if (minY > pt.getRealY())
                        minY = pt.getRealY();
                    if (maxX < pt.getRealX() + pt.getWidth())
                        maxX = pt.getRealX() + pt.getWidth();
                    if (maxY < pt.getRealY() + pt.getHeight())
                        maxY = pt.getRealY() + pt.getHeight();
                }
            }
        }

        if( road != null) {
            road.contextScale();

            if( bFirst ) {
                minX = road.getRealX();
                minY = road.getRealY();
                maxX = road.getRealX()+road.getWidth();
                maxY = road.getRealY()+road.getHeight();

                bFirst = false;
            } else {

                if (minX > road.getRealX())
                    minX = road.getRealX();
                if (minY > road.getRealY())
                    minY = road.getRealY();
                if (maxX < road.getRealX() + road.getWidth())
                    maxX = road.getRealX() + road.getWidth();
                if (maxY < road.getRealY() + road.getHeight())
                    maxY = road.getRealY() + road.getHeight();
            }
        }

        this.x = minX;
        this.y = minY;
        this.width = (int)(maxX - minX);
        this.height = (int)(maxY - minY);

        // Everything the frame will need, built HERE -- contextScale() runs on the loading thread,
        // on a chunk nobody draws yet, so it may take as long as it needs. Leaving the path to be
        // built lazily by the first draw() put Path.op on the render thread, under the GameSurface
        // lock: seconds of freeze and "MyBuoy isn't responding" on a slow phone.
        getCombinedPath();
    }

    public void update()  {
        // Current time in nanoseconds
        long now = System.nanoTime();

        // Never once did draw.
        if(lastUpdateNanoTime==-1) {
            lastUpdateNanoTime= now;
        }

        // Change nanoseconds to milliseconds (1 nanosecond = 1000000 milliseconds).
        int deltaTime = (int) ((now - lastUpdateNanoTime)/ 1000000 );

        lastUpdateNanoTime = now;

        colUsing = 0;
        rowUsing = 0;

        if( false ) {
            for(List<MapPoint> lstPts:  points) {
                for (MapPoint pt : lstPts) {
                    pt.update();
                }
            }
        }
        else {
            if( road != null) {
                road.update();
            }
        }
    }

    // A ring too wide to be drawn was filtered out here for a while. REMOVED: the premise was
    // wrong. On the Mississippi delta a 1103896 x 1337168 px path did vanish, but a 898407 x
    // 710887 px coastline ring draws perfectly well, so extent alone does not tell a path the
    // canvas will paint from one it will drop -- and filtering on it wiped the coast off a
    // working spot. Whatever makes the delta polygon disappear (its 166643 points, its
    // self-intersecting outline, or its extent) is not identified; it is kept out by the node
    // ceiling of Chunk.dbBBox in the meantime, and the real fix is to cut the polygons when
    // the database is built (task #90).

    // ---- Polygon clipping, done by hand ---------------------------------------------------
    // Skia's Path.op(INTERSECT) cannot be used here. On the Ebrie lagoon (27 173 points over 51
    // self-intersecting rings) it never returns: two threads were caught spinning four minutes
    // inside AddIntersectTs, one of them the render thread, leaving the app on its splash screen.
    // Cutting the coordinates ourselves is linear in the number of points, always terminates, and
    // cannot fail. Sutherland-Hodgman against a rectangle, which is convex, is exactly that.

    private static final float[] NO_POINTS = new float[0];

    private static float[] ringToFloats(ListMapPoint ring) {
        int n = ring.size();
        float[] out = new float[n * 2];

        for( int i = 0; i < n; i++ ) {
            MapPoint pt = ring.get(i);
            out[i * 2] = pt.getX();
            out[i * 2 + 1] = pt.getY();
        }

        return out;
    }

    // edge: 0 = keep x >= v, 1 = keep x <= v, 2 = keep y >= v, 3 = keep y <= v
    private static boolean insideEdge(float x, float y, int edge, float v) {
        if( edge == 0 ) return x >= v;
        if( edge == 1 ) return x <= v;
        if( edge == 2 ) return y >= v;
        return y <= v;
    }

    private static float[] clipEdge(float[] in, int edge, float v) {
        int n = in.length / 2;

        if( n < 3 )
            return NO_POINTS;

        float[] out = new float[in.length * 2 + 8];
        int m = 0;

        float px = in[(n - 1) * 2];
        float py = in[(n - 1) * 2 + 1];
        boolean pIn = insideEdge(px, py, edge, v);

        for( int i = 0; i < n; i++ ) {
            float cx = in[i * 2];
            float cy = in[i * 2 + 1];
            boolean cIn = insideEdge(cx, cy, edge, v);

            if( cIn != pIn ) {
                float ix, iy;

                if( (edge == 0) || (edge == 1) ) {
                    float dx = cx - px;
                    float t = (dx != 0) ? ((v - px) / dx) : 0;
                    ix = v;
                    iy = py + t * (cy - py);
                }
                else {
                    float dy = cy - py;
                    float t = (dy != 0) ? ((v - py) / dy) : 0;
                    iy = v;
                    ix = px + t * (cx - px);
                }

                out[m++] = ix;
                out[m++] = iy;
            }

            if( cIn ) {
                out[m++] = cx;
                out[m++] = cy;
            }

            px = cx;
            py = cy;
            pIn = cIn;
        }

        if( m < 6 )
            return NO_POINTS;

        return java.util.Arrays.copyOf(out, m);
    }

    private static float[] clipPolygon(float[] in, float left, float top, float right, float bottom) {
        float[] out = clipEdge(in, 0, left);
        out = clipEdge(out, 1, right);
        out = clipEdge(out, 2, top);
        out = clipEdge(out, 3, bottom);

        return out;
    }

    private static void addRingToPath(Path path, float[] ring) {
        path.moveTo(ring[0], ring[1]);

        for( int i = 1; i < ring.length / 2; i++ )
            path.lineTo(ring[i * 2], ring[i * 2 + 1]);

        path.close();
    }

    // Outer rings filled NON_ZERO, inner rings (islets) subtracted. The subtraction still uses
    // Path.op, but only ever on rings already cut down to the window, never on the raw geometry.
    private static Path pathFromRings(java.util.List<float[]> outer, java.util.List<float[]> inner) {
        Path path = new Path();

        for( float[] ring : outer )
            addRingToPath(path, ring);

        if( !inner.isEmpty() ) {
            Path holes = new Path();

            for( float[] ring : inner )
                addRingToPath(holes, ring);

            path.op(holes, Path.Op.DIFFERENCE);
        }

        return path;
    }

    // Cuts the clipped rings into tileCount x tileCount squares of one screen side each, so that a
    // frame hands the canvas only the tiles the screen covers -- at most four.
    private void buildTiles(int area) {
        tiles = null;
        tileCount = 0;
        tileSize = 0;

        if( (area <= 0) || (clippedOuter == null) )
            return;

        int count = this.chunk.getVirtualZoom();   // area / VZ == 200 m by construction
        if( count <= 0 )
            return;

        int size = area / count;
        if( size <= 0 )
            return;

        long t0 = System.nanoTime();
        Path[] built = new Path[count * count];
        int kept = 0;

        java.util.List<float[]> outer = new java.util.ArrayList<float[]>();
        java.util.List<float[]> inner = new java.util.ArrayList<float[]>();

        for( int row = 0; row < count; row++ ) {
            for( int col = 0; col < count; col++ ) {
                float left = col * size;
                float top = row * size;
                float right = left + size;
                float bottom = top + size;

                outer.clear();
                inner.clear();

                for( float[] ring : clippedOuter ) {
                    float[] cut = clipPolygon(ring, left, top, right, bottom);
                    if( cut.length >= 6 )
                        outer.add(cut);
                }

                if( outer.isEmpty() )
                    continue;

                for( float[] ring : clippedInner ) {
                    float[] cut = clipPolygon(ring, left, top, right, bottom);
                    if( cut.length >= 6 )
                        inner.add(cut);
                }

                Path tile = pathFromRings(outer, inner);

                if( !tile.isEmpty() ) {
                    built[row * count + col] = tile;
                    kept++;
                }
            }
        }

        tiles = built;
        tileCount = count;
        tileSize = size;

        GBLog.d("PERF", String.format(java.util.Locale.US,
                "tiles %s: %d x %d of %d px, %d kept, %.1f ms",
                typeArea, count, count, size, kept, (System.nanoTime() - t0) / 1000000.0));
    }

    // Draws only the tiles the screen covers. Returns false when there is no tiling to use.
    private boolean drawTiles(Canvas canvas, GameSurface surface, Paint paint) {
        if( (tiles == null) || (tileCount <= 0) || (tileSize <= 0) )
            return false;

        int left = surface.getAreaX(0);
        int top = surface.getAreaY(0);
        int right = surface.getAreaX(surface.getWidth());
        int bottom = surface.getAreaY(surface.getHeight());

        int colFirst = left / tileSize;
        int colLast = right / tileSize;
        int rowFirst = top / tileSize;
        int rowLast = bottom / tileSize;

        if( colFirst < 0 ) colFirst = 0;
        if( rowFirst < 0 ) rowFirst = 0;
        if( colLast >= tileCount ) colLast = tileCount - 1;
        if( rowLast >= tileCount ) rowLast = tileCount - 1;

        canvas.save();
        canvas.translate(surface.getCanvasX(0), surface.getCanvasY(0));

        for( int row = rowFirst; row <= rowLast; row++ ) {
            for( int col = colFirst; col <= colLast; col++ ) {
                Path tile = tiles[row * tileCount + col];

                if( tile != null )
                    canvas.drawPath(tile, paint);
            }
        }

        canvas.restore();

        return true;
    }

    private Path getCombinedPath() {
        if( combinedPath == null ) {
            long t0 = System.nanoTime();

            GameSurface surface = this.chunk.getGameSurface();
            int area = (surface != null) ? surface.getAreaWidth() : 0;

            clippedOuter = new java.util.ArrayList<float[]>();
            clippedInner = new java.util.ArrayList<float[]>();

            int srcPoints = 0;
            int keptPoints = 0;

            for( ListMapPoint ring : points ) {
                float[] pts = ringToFloats(ring);
                srcPoints += pts.length / 2;

                if( area > 0 )
                    pts = clipPolygon(pts, 0, 0, area, area);

                if( pts.length < 6 )
                    continue;

                keptPoints += pts.length / 2;

                if( ring.isOuter() )
                    clippedOuter.add(pts);
                else
                    clippedInner.add(pts);
            }

            combinedPath = pathFromRings(clippedOuter, clippedInner);

            double clipMs = (System.nanoTime() - t0) / 1000000.0;

            buildTiles(area);

            android.graphics.RectF b = new android.graphics.RectF();
            combinedPath.computeBounds(b, true);
            GBLog.d("PERF", String.format(java.util.Locale.US,
                    "path %s: %d rings, %d src points -> %d kept, bounds %.0f,%.0f -> %.0f,%.0f"
                    + " (%.0f x %.0f px), clip %.1f ms",
                    typeArea, points.size(), srcPoints, keptPoints,
                    b.left, b.top, b.right, b.bottom, b.width(), b.height(), clipMs));
        }

        return combinedPath;
    }

    public void draw(Canvas canvas, int layer)  {
        GameSurface gameSurface = this.chunk.getGameSurface();

        if( gameSurface != null ) {
            Bitmap bitmap = this.getCurrentBitmap();
            //canvas.drawBitmap(bitmap, gameSurface.getCanvasX(x), gameSurface.getCanvasY(y), null);

            if( false ) {
                for(List<MapPoint> lstPts:  points) {
                    for (MapPoint pt : lstPts) {
                        if (pt.isVisible())
                            pt.draw(canvas, layer);
                    }
                }
            } else if( true ) {

                if( road != null) {
                    if( road.isVisible() )
                        road.draw(canvas, layer);
                }

                // Un seul Path caché (outer NON_ZERO moins trous via Path.op). Dessin direct sur le
                // canvas avec un translate (pan), comme l'ancienne branche allOuter, mais qui
                // gère AUSSI les trous -> plus de bitmap plein écran ni de PorterDuff CLEAR par
                // frame (c'était le coût FPS des multipolygons eau/forêt à trous).
                if( !points.isEmpty() ) {
                    Paint paint = null;
                    if( layer == 0 )
                        paint = paintLayer0;
                    else if( layer == 1 )
                        paint = paintLayer1;

                    // Layer 0 is FILL_AND_STROKE with a 10 px stroke, in the very colour of the
                    // fill: on land it only ever served to fatten each polygon by 5 px so that
                    // neighbours overlapped and no seam of water showed through. Now that every
                    // polygon of the chunk lives in a single path, the seam cannot happen and
                    // stroking the coastline -- the most expensive operation of the frame on a
                    // path of tens of thousands of points -- buys nothing.
                    if( (layer == 0) && (typeArea == TypeArea.EARTH_LAND) )
                        paint = null;

                    // NEVER build anything here: the path and its tiles are made by contextScale()
                    // on the loading thread, before the chunk is published. A null path at draw
                    // time is a bug in that chain, not a case to handle: say so instead of
                    // painting nothing and hiding it.
                    if( combinedPath == null )
                        GBLog.e("MAPAREA", "draw() with no path for " + typeArea
                                + ": contextScale() did not run before publication");

                    if( (paint != null) && (combinedPath != null) ) {
                        if( !drawTiles(canvas, gameSurface, paint) ) {
                            canvas.save();
                            canvas.translate(gameSurface.getCanvasX(0), gameSurface.getCanvasY(0));
                            canvas.drawPath(combinedPath, paint);
                            canvas.restore();
                        }
                    }
                }
            }
        }
    }

    public boolean isVisible() {
        GameSurface gameSurface = this.chunk.getGameSurface();
        boolean bVisible = false;

        if( gameSurface != null )
            bVisible = gameSurface.isVisible(this);

        return bVisible;
    }

    // PERF : rings held by this area, i.e. land polygons once they are gathered.
    public int ringCount() {
        return points.size();
    }

    // PERF : total points of every ring, i.e. the size of the path drawn each frame
    // and of the walk done by Chunk.coordonateInLand.
    public int pointCount() {
        int total = 0;

        for( ListMapPoint lstPts : points )
            total += lstPts.size();

        return total;
    }

    public boolean contains(MapPoint point) {
        boolean oddTransitions = false;
        boolean inArea = false;


        for(List<MapPoint> lstPts:  points) {
            int polySides = lstPts.size();

            for (int i = 0, j = polySides - 1; i < polySides; j = i++) {
                MapPoint ptI = lstPts.get(i);
                MapPoint ptJ = lstPts.get(j);

                if ((ptI.getLatitude() < point.getLatitude() && ptJ.getLatitude() >= point.getLatitude()) || (ptJ.getLatitude() < point.getLatitude() && ptI.getLatitude() >= point.getLatitude())) {
                    if (ptI.getLongitude() + (point.getLatitude() - ptI.getLatitude()) / (ptJ.getLatitude() - ptI.getLatitude()) * (ptJ.getLongitude() - ptI.getLongitude()) < point.getLongitude()) {
                        oddTransitions = !oddTransitions;
                    }
                }
            }

            inArea = oddTransitions;
            if( inArea )
                break;
        }

        return inArea;
    }
}
