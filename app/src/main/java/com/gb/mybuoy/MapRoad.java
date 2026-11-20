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
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.util.Log;

public class MapRoad extends GameObject implements MapGameObject {

    private static final String TAG = "MapRoad";

    @Override
    public String getMapObjectType() {
        String objType = "Road";

        if( this.area != null ) { // Area Mode
            objType = "RoadArea";
        }

        return objType;
    }

    @Override
    public int getMapObjectSortIndex() {

        int idx = typeRoad.ordinal();

        if( tunnel )
            idx += 100;

        if( bridge )
            idx -= 100;

        return idx;
    }

    public enum TypeDash {
        DASH1,
        DASH2,
        DASH_NONE,
    }

    public enum TypeRoad {
        MOTORWAY("motorway", "#db2c68", "#e891a1", 61, 51),

        TRUNK("trunk", "#d56c50", "#f8b19c", 61, 51),



        FOOTWAY("footway", "#74138C", "#CB59E8", 27, 21, TypeDash.DASH1),
        STEPS("steps", "#74138C", "#CB59E8", 27, 21, TypeDash.DASH2),
        CYCLEWAY("cycleway", "#1F88A7", "#67C7E2", 27, 21, TypeDash.DASH1),

        PRIMARY("primary", "#a87610", "#fbd5a4", 61, 51),
        PRIMARY_LINK("primary_link", "#a87610", "#fbd5a4", 61, 51),
        SECONDARY("secondary", "#818d1c", "#f6f9bf", 61, 51),
        SECONDARY_LINK("secondary_link", "#818d1c", "#f6f9bf", 61, 51),
        TERTIARY("tertiary", Color.DKGRAY, Color.WHITE, 61, 51),
        TERTIARY_LINK("tertiary_link", Color.DKGRAY, Color.WHITE, 61, 51),
        UNCLASSIFIED("unclassified", Color.DKGRAY, Color.WHITE, 51, 41),
        RESIDENTIAL("residential", Color.DKGRAY, Color.WHITE, 51, 41),

        LIVING_STREET("living_street", "#d0cecd", "#ecedec", 51, 41),

        MOTORWAY_LINK("motorway_link", "#db2c68", "#e891a1", 61, 51),
        TRUNK_LINK("trunk_link", "#d56c50", "#f8b19c", 61, 51),


        PEDESTRIAN("pedestrian", "#a6a6a7", "#dcdce8", 51, 41),
        SERVICE("service", Color.DKGRAY, Color.WHITE, 31, 21),
        PATH("path", "#990099", "#F206FF", 27, 21, TypeDash.DASH1),
        TRACK("track", "#B05F3C", "#D69E87", 27, 21, TypeDash.DASH1),

        corridor("corridor", Color.DKGRAY, Color.WHITE, 27, 21),
        elevator("elevator", Color.DKGRAY, Color.WHITE, 27, 21, TypeDash.DASH2),
        road("road", Color.DKGRAY, Color.WHITE, 27, 21),

        AREA_ROAD("area_road",Color.RED,Color.RED, 27, 21), // MapAeraRoad  - Color Set by MapAeraRoad Class


        UNKNOWN("unknown", Color.RED, Color.RED, 51, 41);

        private String name;
        Paint paintLayer0;
        Paint paintLayer1;

        private TypeRoad(String name, int color0, int color1, int StrokeWidth0, int StrokeWidth1, TypeDash dashed) {
            this.name = name;

            paintLayer0 = new Paint();
            paintLayer0.setAntiAlias(true);
            paintLayer0.setStyle(Paint.Style.STROKE);
            paintLayer0.setStrokeJoin(Paint.Join.ROUND);
            paintLayer0.setStrokeCap(Paint.Cap.ROUND);
            paintLayer0.setStrokeWidth(StrokeWidth0);
            paintLayer0.setColor(color0);

            paintLayer1 = new Paint();
            paintLayer1.setAntiAlias(true);
            paintLayer1.setStyle(Paint.Style.STROKE);
            paintLayer1.setStrokeJoin(Paint.Join.ROUND);
            paintLayer1.setStrokeCap(Paint.Cap.ROUND);
            paintLayer1.setStrokeWidth(StrokeWidth1);
            paintLayer1.setColor(color1);

            switch( dashed ){
                default:
                case DASH_NONE:
                    break;

                case DASH1:
                    paintLayer1.setStrokeCap(Paint.Cap.ROUND);
                    //paintLayer1.setPathEffect(new DashPathEffect(new float[]{3, 25}, 0));
                    paintLayer1.setPathEffect(new DashPathEffect(new float[]{1, 23}, 0));

                    //paintLayer1.setStrokeCap(Paint.Cap.BUTT);
                    //paintLayer1.setPathEffect(new DashPathEffect(new float[]{21, 5}, 1));
                    break;

                case DASH2:
                    paintLayer1.setStrokeCap(Paint.Cap.BUTT);
                    paintLayer1.setPathEffect(new DashPathEffect(new float[]{5, 5}, 0));
                    break;
            }


        }

        private TypeRoad(String name, int color0, int color1, int StrokeWidth0, int StrokeWidth1) {
            this( name, color0, color1, StrokeWidth0, StrokeWidth1, TypeDash.DASH_NONE);
        }

        private TypeRoad(String name, String color0, String color1, int StrokeWidth0, int StrokeWidth1)  {
            this( name, Color.parseColor(color0), Color.parseColor(color1), StrokeWidth0, StrokeWidth1, TypeDash.DASH_NONE);
        }

        private TypeRoad(String name, String color0, String color1, int StrokeWidth0, int StrokeWidth1,  TypeDash dashed)  {
            this( name, Color.parseColor(color0), Color.parseColor(color1), StrokeWidth0, StrokeWidth1, dashed);
        }

        public Paint getPaintLayer0() {
            return paintLayer0;
        }

        public Paint getPaintLayer1() {
            return paintLayer1;
        }


        @Override
        public String toString() {
            return name;
        }

        public static MapRoad.TypeRoad getTypeRoad(String key ) {
            MapRoad.TypeRoad typeRoad = UNKNOWN;

            if( (key != null) && (key.length() > 0) ) {

                for (MapRoad.TypeRoad typ : MapRoad.TypeRoad.values()) {

                    if (typ.toString().equals(key)) {

                        if( typeRoad == UNKNOWN)
                            typeRoad = typ;

                        if( typeRoad != UNKNOWN )
                            break;
                    }
                }
            }

            if( (typeRoad == UNKNOWN) ) // && (key != null) && (key.length() > 0) )
                GBLog.d( "TYPEROAD", String.format("Road type:%s ???", key));

            return typeRoad;
        }



    }

    private static final double SEGMENT_MIN_SIZE = 0;

    private final Chunk chunk;
    private final AppManager _am;

    private final String title;
    private final String ref;
    // #98 : made-up name and ref drawn instead in Streamer mode, picked here and not in draw.
    private final String fakeTitle;
    private final String fakeRef;
    private final TypeRoad typeRoad;
    private final String texture;
    private final int maxSpeed;

    private final boolean tunnel;
    private final boolean bridge;
    //private final MapRoadArea area;
    private final MapArea area;
    private final ListMapPoint outerAreaPoints;

    private ListMapPoint points = null;
    //private List<MapRoadSegment> segments = null;

    private Bitmap[] allTextures = null;

    private int rowUsing = 0;
    private int colUsing = 0;
    private long lastUpdateNanoTime =-1;

    private double directionAngle = 0;

    protected Paint paintLayer0 = null;
    protected Paint paintLayer1 = null;
    protected Paint paintText = null;



    public MapRoad(Chunk chunk, String title, String ref, TypeRoad typeRoad, String texture, boolean isTunnel, boolean isBridge, boolean isClosed, boolean isArea, MapArea.TypeArea typeArea, int maxSpeed) {
        super();

        _am = AppManager.getInstance();

        this.chunk = chunk;
        this.title = title;
        this.ref = ref;
        this.fakeTitle = StreamerMode.fakeStreet(title);
        this.fakeRef = StreamerMode.fakeRef(ref);
        this.typeRoad = typeRoad;
        this.texture = texture;
        this.tunnel = isTunnel;
        this.bridge = isBridge;
        this.maxSpeed = maxSpeed;


        this.points = new ArrayListMapPoint(true);
        //this.segments = new ArrayList<MapRoadSegment>();

        paintLayer0 = new Paint(typeRoad.getPaintLayer0());
        paintLayer1 = new Paint(typeRoad.getPaintLayer1());

        paintText = new Paint();
        paintText.setAntiAlias(true);
        paintText.setColor(Color.BLACK);

        if( this.tunnel )
            paintLayer0.setPathEffect(new DashPathEffect(new float[]{3, 25}, 0));

        if( isArea ) {
            this.area = new MapRoadArea(chunk, this);
            this.outerAreaPoints = new ArrayListMapPoint(true);
            this.area.addListPoints(this.outerAreaPoints);
        } else if( isClosed && (typeArea != MapArea.TypeArea.UNKNOWN) ) {
            this.area = new MapArea(chunk, title, typeArea, false);
            this.outerAreaPoints = new ArrayListMapPoint(true);
            this.area.addListPoints(this.outerAreaPoints);
        } else {
            this.area = null;
            this.outerAreaPoints = null;
        }
    }

    public String getTitle() {
        return title;
    }

    public TypeRoad getTypeRoad() {
        return typeRoad;
    }

    public boolean isTunnel() {
        return tunnel;
    }
    public boolean isBridge() { return bridge; }

    public Paint getPaintLayer0() {
        return paintLayer0;
    }

    public Paint getPaintLayer1() {
        return paintLayer1;
    }

    public void addPoint( MapPoint pt ) {
        if( !points.contains(pt) ) {
            MapPoint lastPt = null;
            if( points.size() > 0 )
                lastPt = points.get(points.size()-1);

            points.add(pt);
            pt.addRoad(this);

            if( area != null ) { // Area Mode
                //area.addPoint(pt);
                outerAreaPoints.add(pt);
            }
            /*
            else { // Segment Mode

                if (lastPt != null) {

                    double segmentWidth = 0;
                    double segmentHeight = 0;

                    double minX = lastPt.getRealX();
                    double minY = lastPt.getRealY();
                    double maxX = minX;
                    double maxY = minY;

                    if (minX > pt.getRealX())
                        minX = pt.getRealX();
                    if (minY > pt.getRealY())
                        minY = pt.getRealY();
                    if (maxX < pt.getRealX() + pt.getWidth())
                        maxX = pt.getRealX() + pt.getWidth();
                    if (maxY < pt.getRealY() + pt.getHeight())
                        maxY = pt.getRealY() + pt.getHeight();

                    segmentWidth = maxX - minX;
                    segmentHeight = maxY - minY;

                    if ((segmentWidth >= SEGMENT_MIN_SIZE) && (segmentHeight >= SEGMENT_MIN_SIZE))
                        segments.add(new MapRoadSegment(chunk, this, lastPt, pt));
                }
            }
             */
        }
    }

    /*
    private void remakeSegmentsList() {

        if( area == null ) { // Segment Mode
            List<MapRoadSegment> newList = new ArrayList<MapRoadSegment>();
            MapPoint lastPt = null;

            for (MapPoint pt : points) {

                if (lastPt != null) {

                    int nbWay = pt.getRoads().size();

                    double segmentWidth = 0;
                    double segmentHeight = 0;

                    double minX = lastPt.getRealX();
                    double minY = lastPt.getRealY();
                    double maxX = minX;
                    double maxY = minY;

                    if (minX > pt.getRealX())
                        minX = pt.getRealX();
                    if (minY > pt.getRealY())
                        minY = pt.getRealY();
                    if (maxX < pt.getRealX())
                        maxX = pt.getRealX();
                    if (maxY < pt.getRealY())
                        maxY = pt.getRealY();

                    segmentWidth = maxX - minX;
                    segmentHeight = maxY - minY;

                    if (((segmentWidth >= SEGMENT_MIN_SIZE) && (segmentHeight >= SEGMENT_MIN_SIZE)) || (nbWay > 1)) {
                        newList.add(new MapRoadSegment(chunk, this, lastPt, pt));
                        lastPt = pt;
                    }
                } else {
                    lastPt = pt;
                }
            }

            this.segments = newList;
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
     * Recycle this road's PER-INSTANCE textures (native memory). Only ever called on a
     * chunk that is no longer the render target (see GameSurface.update), so nothing is
     * drawing these bitmaps anymore. Static/shared atlases are never touched.
     */
    public void disposeTextures() {
        if( allTextures != null ) {
            for( Bitmap b : allTextures ) {
                // In a 1x1 grid the "sub-image" IS the shared texture: recycling it would break
                // every other object in the app.
                if( b != null && !b.isRecycled() && !SharedTextures.isShared(b) )
                    b.recycle();
            }
            allTextures = null;
        }
    }

    public void contextScale() {
        GameSurface gameSurface = this.chunk.getGameSurface();

        if( gameSurface != null ) {
            // Shared, decoded once for the whole app (SharedTextures): this used to decode the
            // same 32x32 PNG for every road, on every rescale.
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

        // #53 : les coordonnees des MapPoint vont changer -> le Path cache doit etre reconstruit.
        points.invalidatePath();

        for(MapPoint pt:  points) {
            pt.contextScale();

            if( bFirst ) {
                minX = pt.getRealX();
                minY = pt.getRealY();
                maxX = pt.getRealX()+pt.getWidth();
                maxY = pt.getRealY()+pt.getHeight();

                bFirst = false;
            } else {
                if( minX > pt.getRealX() )
                    minX = pt.getRealX();
                if( minY > pt.getRealY() )
                    minY = pt.getRealY();
                if( maxX < pt.getRealX()+pt.getWidth() )
                    maxX = pt.getRealX()+pt.getWidth();
                if(maxY < pt.getRealY()+pt.getHeight() )
                    maxY = pt.getRealY()+pt.getHeight();
            }
        }

        if( area != null) {
            area.contextScale();

            if( bFirst ) {
                minX = area.getRealX();
                minY = area.getRealY();
                maxX = area.getRealX()+area.getWidth();
                maxY = area.getRealY()+area.getHeight();

                bFirst = false;
            } else {

                if (minX > area.getRealX())
                    minX = area.getRealX();
                if (minY > area.getRealY())
                    minY = area.getRealY();
                if (maxX < area.getRealX() + area.getWidth())
                    maxX = area.getRealX() + area.getWidth();
                if (maxY < area.getRealY() + area.getHeight())
                    maxY = area.getRealY() + area.getHeight();
            }
        }

        /*
        remakeSegmentsList();

        for(MapRoadSegment rs:  segments) {
            rs.contextScale();

            if( bFirst ) {
                minX = rs.getRealX();
                minY = rs.getRealY();
                maxX = rs.getRealX()+rs.getWidth();
                maxY = rs.getRealY()+rs.getHeight();

                bFirst = false;
            } else {
                if( minX > rs.getRealX() )
                    minX = rs.getRealX();
                if( minY > rs.getRealY() )
                    minY = rs.getRealY();
                if( maxX < rs.getRealX()+rs.getWidth() )
                    maxX = rs.getRealX()+rs.getWidth();
                if(maxY < rs.getRealY()+rs.getHeight() )
                    maxY = rs.getRealY()+rs.getHeight();
            }
        }
         */

        this.x = minX;
        this.y = minY;
        this.width = (int)(maxX - minX);
        this.height = (int)(maxY - minY);
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

        this.lastUpdateNanoTime = now;

        colUsing = 0;
        rowUsing = 0;

        directionAngle = _am.getDirectionAngle();

        if( false ) {
            for (MapPoint pt : points) {
                pt.update();
            }
        } else {

            if( area != null) {
                area.update();
            }

            /*
            for (MapRoadSegment rs : segments) {
                rs.update();
            }
             */
        }
    }

    public void draw(Canvas canvas, int layer)  {
        GameSurface gameSurface = this.chunk.getGameSurface();

        if( gameSurface != null ) {
            Bitmap bitmap = this.getCurrentBitmap();
            //canvas.drawBitmap(bitmap, gameSurface.getCanvasX(x), gameSurface.getCanvasY(y), null);

            if( false ) {
                for(MapPoint pt:  points) {
                    if( pt.isVisible() )
                        pt.draw(canvas, layer);
                }
            } else {

                if( area != null) {
                    if( area.isVisible() )
                        area.draw(canvas, layer);
                }

                if( false ) {
                    /*
                    for (MapRoadSegment rs : segments) {
                        if (rs.isVisible())
                            rs.draw(canvas, layer);
                    }
                     */
                } else if( (area == null) || (area.getTypeArea() != MapArea.TypeArea.ROAD_AREA) ){

                    Path path = points.getPath();
                    Path pathReverse = points.getPathReverse();
                    double pathLen = points.getPathLen();
                    boolean pathTrendNorth = points.isPathTrendNorth();
                    boolean pathTrendEast = points.isPathTrendEast();

                    boolean reversedPath = !pathTrendEast;


                    canvas.save();
                    canvas.translate(gameSurface.getCanvasX(0), gameSurface.getCanvasY(0));

                    switch (layer) {
                        case 0:
                            canvas.drawPath(path, paintLayer0);
                            break;
                        case 1:
                            if( bridge ) {
                                Paint paintBridge = new Paint(paintLayer0);
                                //paintBridge.setColor(Color.BLACK);
                                paintBridge.setStrokeCap(Paint.Cap.BUTT);
                                canvas.drawPath(path, paintBridge);
                            }
                            canvas.drawPath(path, paintLayer1);

                            break;
                        case 2:
                            if( bridge ) // When To bridge at side a low zoom to erase border inside the other bridge
                                canvas.drawPath(path, paintLayer1);

                            // BetterTextPos
                            if( this.ref != null && this.ref.length() > 0) {

                                ListMapPoint betterPointsForText = chunk.gameObjectBetterPointsForText.get(this.ref);

                                if( betterPointsForText == null ) {
                                    chunk.gameObjectBetterPointsForText.put(this.ref, this.points);
                                } else if( betterPointsForText != this.points ) {
                                    float xSibling = gameSurface.getAreaX((float)gameSurface.getWidth()/2)+100;
                                    float ySibling = gameSurface.getAreaY((float)gameSurface.getHeight()/2)-200;

                                    boolean betterPointsPathTrendEast = betterPointsForText.isPathTrendEast();
                                    boolean reversedBetterPointsPath = !betterPointsPathTrendEast;
                                    GEOPoint gpt = betterPointsForText.getBetterHOffsetText(200, reversedBetterPointsPath, xSibling, ySibling, 100);;
                                    double sideX = xSibling - gpt.getX();
                                    double sideY = ySibling - gpt.getY();
                                    double dist1 = Math.sqrt(sideX*sideX+sideY*sideY);

                                    gpt = points.getBetterHOffsetText(200, reversedPath, xSibling, ySibling, 100);
                                    sideX = xSibling - gpt.getX();
                                    sideY = ySibling - gpt.getY();
                                    double dist2 = Math.sqrt(sideX*sideX+sideY*sideY);

                                    if( dist2 < dist1) {
                                        chunk.gameObjectBetterPointsForText.put(this.ref, this.points);
                                    }
                                }
                            }


                        case 3:
                            String shownTitle = StreamerMode.enabled ? this.fakeTitle : this.title;
                            if( shownTitle != null && shownTitle.length() > 0) {

                                //paintText.setTextSize(12 * gameSurface.getResources().getDisplayMetrics().density);
                                float fontSize = (paintLayer0.getStrokeWidth()-2) / gameSurface.getResources().getDisplayMetrics().density;
                                //GBLog.d( TAG, String.format("fontSize: %.1f", fontSize));

                                if( fontSize >= 14 )
                                    fontSize = 14;

                                boolean bOK = false;

                                float txtWidth = 0;
                                float txtHeight = 0;
                                float margin = paintLayer0.getStrokeWidth()*1.2f;

                                final float fontMinSize = 9;

                                while( !bOK ) {

                                    paintText.setTextSize(fontSize * gameSurface.getResources().getDisplayMetrics().density);

                                    //if( fontSize >= 12 )
                                    paintText.setTypeface(Typeface.DEFAULT_BOLD);

                                    Rect bounds = new Rect();
                                    paintText.getTextBounds(shownTitle, 0, shownTitle.length(), bounds);
                                    //float txtWidth =textPaint.measureText(this.title);
                                    txtWidth = bounds.width();
                                    txtHeight = bounds.height();

                                    bOK = txtWidth < (pathLen - margin*2);
                                    if( !bOK ) {
                                        if( fontSize <= fontMinSize )
                                            break;
                                        fontSize -= 0.5f;
                                        if( fontSize <= fontMinSize )
                                            fontSize = fontMinSize;
                                    }
                                }



                                //float hOffset = ((float)pathLen-txtWidth) / 2;

                                //float vOffset = (paintLayer0.getStrokeWidth() - txtHeight) / 2;
                                //float vOffset = txtHeight/2 -5;
                                //float vOffset = txtHeight/2-3;
                                float vOffset = txtHeight/2 -5;


                                if( (txtWidth < (pathLen - margin*2)) && (fontSize >= fontMinSize) ) {
                                    if( pathTrendEast ) {
                                        GEOPoint gpt = points.getBetterHOffsetText(txtWidth, false, gameSurface.getAreaX((float)gameSurface.getWidth()/2), gameSurface.getAreaY((float)gameSurface.getHeight()/2), margin);
                                        float hOffset = (float)gpt.getDistance();
                                        canvas.drawTextOnPath(shownTitle, path, hOffset, vOffset, paintText);

                                    } else {
                                        GEOPoint gpt = points.getBetterHOffsetText(txtWidth, true, gameSurface.getAreaX((float)gameSurface.getWidth()/2), gameSurface.getAreaY((float)gameSurface.getHeight()/2), margin);
                                        float hOffset = (float)gpt.getDistance();
                                        canvas.drawTextOnPath(shownTitle, pathReverse, hOffset, vOffset, paintText);
                                    }
                                }

                            }

                        case 4:

                            if( this.ref != null && this.ref.length() > 0) {

                                ListMapPoint betterPointsForText = chunk.gameObjectBetterPointsForText.get(this.ref);

                                if( betterPointsForText == this.points ) {
                                    String shownRef = StreamerMode.enabled ? this.fakeRef : this.ref;
                                    float fontSize = 16;

                                    paintText.setTextSize(fontSize * gameSurface.getResources().getDisplayMetrics().density);
                                    paintText.setTypeface(Typeface.DEFAULT_BOLD);

                                    Rect bounds = new Rect();
                                    paintText.getTextBounds(shownRef, 0, shownRef.length(), bounds);
                                    //float txtWidth =textPaint.measureText(this.title);
                                    float txtWidth = bounds.width();
                                    float txtHeight = bounds.height();

                                    float xSibling;
                                    float ySibling;

                                    if( (directionAngle >= 0) && (directionAngle <180) )
                                        xSibling = gameSurface.getAreaX((float)gameSurface.getWidth()/2)-250;
                                    else
                                        xSibling = gameSurface.getAreaX((float)gameSurface.getWidth()/2)+250;

                                    if( (directionAngle >= 90) && (directionAngle <270) )
                                        ySibling = gameSurface.getAreaY((float)gameSurface.getHeight()/2)-250;
                                    else
                                        ySibling = gameSurface.getAreaY((float)gameSurface.getHeight()/2)+250;


                                    GEOPoint gpt = points.getBetterHOffsetText(150, reversedPath, xSibling, ySibling, 100);

                                    float xTxt = (float) gpt.getX() - txtWidth / 2;
                                    float yTxt = (float) gpt.getY() + txtHeight / 2;

                                    Paint paintRef = new Paint();
                                    paintRef.setAntiAlias(true);
                                    paintRef.setColor(Color.YELLOW);
                                    paintRef.setStyle(Paint.Style.FILL);
                                    RectF rf = new RectF(xTxt - 10,
                                            yTxt - txtHeight - 10,
                                            xTxt + txtWidth + 10,
                                            yTxt + 10);
                                    RectF rfBorder = new RectF(xTxt - 15,
                                            yTxt - txtHeight - 15,
                                            xTxt + txtWidth + 15,
                                            yTxt + 15);

                                    paintRef.setColor(Color.BLACK);
                                    canvas.drawRoundRect(rfBorder, 5, 5, paintRef);
                                    paintRef.setColor(Color.YELLOW);
                                    canvas.drawRoundRect(rf, 5, 5, paintRef);
                                    canvas.drawText(shownRef, xTxt, yTxt, paintText);

                                    if( false )
                                        canvas.drawCircle(xSibling, ySibling, 6, paintRef);
                                }
                            }

                            if( false ) { // Valid: Anchor text is bottom/right
                                canvas.drawText("top+left", gameSurface.getAreaX(0), gameSurface.getAreaY(0), paintText);
                                canvas.drawText("bot+left", gameSurface.getAreaX(0), gameSurface.getAreaY(gameSurface.getHeight()), paintText);
                                canvas.drawText("top+right", gameSurface.getAreaX(gameSurface.getWidth()), gameSurface.getAreaY(0), paintText);
                                canvas.drawText("bot+right", gameSurface.getAreaX(gameSurface.getWidth()), gameSurface.getAreaY(gameSurface.getHeight()), paintText);
                            }
                            break;
                        default:
                            break;
                    }

                    canvas.restore();
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
}
