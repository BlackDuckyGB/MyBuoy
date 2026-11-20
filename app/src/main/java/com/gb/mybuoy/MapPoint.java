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
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.util.ArrayList;
import java.util.List;

public class MapPoint extends GameObject {

    protected Chunk chunk;

    // Where the icon is drawn INSIDE its own bitmap, as a fraction of that bitmap's size.
    //
    // This is the other half of the anchor: the anchor says which corner of the picture the
    // position refers to, and this says how far the drawing sits from it -- the buoy in a 192x192
    // icon is not painted right on the bottom edge, so it is nudged down by 15 pixels. It depends
    // on nothing but the texture pack, which never changes once the object is built, so it is set
    // in the constructor, beside the anchor it corrects, and read from there by everything that
    // needs it: the drawing, the title, and the collider.
    protected float anchorCorrectionX = 0.0f;
    protected float anchorCorrectionY = 0.0f;

    protected GPSCoordonate coord;
    protected String title;
    // #98 : the made-up name drawn instead of title in Streamer mode. Set by the map's own marks
    // (MapMark, MapSeaMark) when the chunk is built; null = title is drawn as it is.
    protected String fakeTitle = null;
    private final boolean boldTitle;
    // Shared across ALL MapPoints: these two Paints are configured identically for every
    // instance (only paintText's size/typeface are set per-draw, on the single render
    // thread). One per instance meant ~2 Paints x 157k points = 328k objects / 42MB heap
    // + as many native paints -> was a top memory offender (leak-hunt, 2026-07).
    private static final Paint paintWarning = new Paint();
    private static final Paint paintText = new Paint();
    static {
        paintWarning.setAntiAlias(true);
        paintWarning.setColor(Color.RED);
        paintWarning.setAlpha(64);
        paintText.setAntiAlias(true);
        paintText.setColor(Color.BLACK);
    }


    private static Bitmap bitmapTextures = null;
    private static Bitmap[] allTextures = null;

    protected int rowUsing = 0;
    protected int colUsing = 0;
    // # de planche (personal marks multi-sheets) ; reste 0 pour tous les autres MapPoint
    // (avatars, proximity...) donc getCurrentBitmap est inchangé pour eux.
    protected int sheetUsing = 0;

    private long lastUpdateNanoTime =-1;
    private long lastWarningNanoTime =-1;

    private float warningRadius = 0;
    private boolean warningSignDirection = false;

    private List<MapRoad> roads = null;

    // Body and reach, as fractions of the reference circle, which is the half-diagonal of the
    // sprite box. Equal: a damage zone shorter than the body would never reach anything, since
    // two solid things stop when their bodies meet.
    private static final double HIT_COEF = 0.8;
    private static final double DAMAGE_COEF = 0.8;

    public MapPoint(Chunk chunk, double longitude, double latitude, String title, boolean boldTitle) {
        super();

        this.chunk = chunk;
        this.coord = new GPSCoordonate(longitude, latitude);
        if( (title != null) && title.equals("Null Island") )
            this.title = null;
        else
            this.title = title;
        this.boldTitle = boldTitle;

        this.roads = new ArrayList<MapRoad>();

        this.anchor = Anchor.MIDDLE_MIDDLE;

        // Orange and not yellow: the map's own roads are yellow, and the circle disappeared into
        // them. The three families have to stay tellable apart at a glance.
        collider.setDebugColor(Color.parseColor("#FF6D00"));   // the marks

        // The body is tighter than the picture it is measured from: the reference circle is the
        // half-diagonal of the sprite box, and a sprite is never painted edge to edge. Eight
        // tenths of it is what the game actually touches and is touched on -- the same figure as
        // the creatures, so that nobody meets anybody on a different footing.
        collider.setHitCoef(HIT_COEF);
        collider.setDamageCoef(DAMAGE_COEF);

        // The body as well as the picture it is measured from: the hit zone is what a creature
        // walks into, so it is the one worth checking against the icon.
        collider.setDebugDrawMasks(GameCollider.DEBUG_DRAW_REF | GameCollider.DEBUG_DRAW_HIT);


        this.colUsing = 0;
        this.rowUsing = 0;
        this.sheetUsing = 0;
    }

    public MapPoint(Chunk chunk, double longitude, double latitude, String title) {
        this(chunk, longitude, latitude, title, false);
    }

    public MapPoint(Chunk chunk, double longitude, double latitude) {
        this(chunk, longitude, latitude, null, false);
    }

    public void setChunk(Chunk chunk) {
        this.chunk = chunk;
    }

    public Chunk getChunk() {
        return chunk;
    }

    // #60 : libelle du point. Pour une mark radar c'est la vitesse limite (tag maxspeed), affichee
    // sous la distance par la fleche de bord d'ecran. Surcharge par PersonalMark (titre saisi).
    public String getTitle() {
        return title;
    }

    // #59 : identite stable d'un point d'un chunk a l'autre. 0 = pas d'identite (cas general :
    // les points de trace, les seamarks...). Surcharge par MapMark (id du node OSM) et par
    // PersonalMark (id en base), les deux seuls a devoir etre reconnus apres un reload de chunk.
    public long getId() {
        return 0;
    }

    public void addRoad(MapRoad r ) {
        if( !roads.contains(r)) {
            roads.add(r);
            r.addPoint(this);
        }
    }

    public List<MapRoad> getRoads() {
        return roads;
    }

    public double getLongitude() {
        return coord.longitude;
    }
    public double getLatitude() {
        return coord.latitude;
    }

    // Metres, null when unknown (map objects have none today, only personal marks).
    public Double getAltitude() {
        return coord.altitude;
    }

    private Bitmap getCurrentBitmap(Bitmap[] textures)  {
        Bitmap texture = null;

        if( this.allTextures != null )
            texture = textures[sheetUsing*rowCount*colCount + rowUsing*colCount + colUsing];

        return texture;
    }

    private Bitmap getCurrentBitmap()  {
        return getCurrentBitmap(this.allTextures);
    }


    public void contextScale() {
        GameSurface gameSurface = null;

        if( this.chunk != null ) {
            gameSurface = this.chunk.getGameSurface();
        }

        if( gameSurface != null ) {

            // Was already decoded once for all MapPoints (static field); now it is the very same
            // instance as every other object's, through SharedTextures.
            if( this.bitmapTextures == null )
                this.bitmapTextures = SharedTextures.point(gameSurface.getResources());

            this.setImage(this.bitmapTextures, 1, 1);

            if( this.allTextures == null ) {
                this.allTextures = new Bitmap[colCount * rowCount];

                for (int col = 0; col < this.colCount; col++) {
                    for (int row = 0; row < this.rowCount; row++) {
                        this.allTextures[row * this.colCount + col] = this.createSubImageAt(row, col);
                    }
                }
            }

            // FIX bug d'echelle : scaler avec le VZ du PROPRE chunk de l'objet, pas celui de map.
            // Au moment du swap de chunk (setGameSurface appele par AppManager avant que
            // GameSurface bascule map), gameSurface.getAreaWidth() renvoie encore le VZ de
            // l'ANCIEN chunk -> routes du nouveau chunk mal scalees (facteur VZ_ancien/VZ_nouveau)
            // -> GPS a cote des routes jusqu'au chunk suivant. Comme VZ = 5*areaTot, base*VZ/areaTot
            // = base*5 : position invariante, independante de map.
            int base = gameSurface.getWidth();
            if( gameSurface.getHeight() > base )
                base = gameSurface.getHeight();
            int scale = base * chunk.getVirtualZoom();

            x = chunk.getX(coord.longitude, scale);
            y = chunk.getY(coord.latitude, scale);
        }
    }

    private float lerp( float start, float end, float fraction) {
        float lerpVal;

        lerpVal = start + (end - start) * fraction;

        return lerpVal;
    }

    protected double getColliderOffsetX() {
        return width * anchorCorrectionX;
    }

    protected double getColliderOffsetY() {
        return height * anchorCorrectionY;
    }

    public void update()  {
        collider.update();
        // Current time in nanoseconds
        long now = System.nanoTime();

        // Never once did draw.
        if(lastUpdateNanoTime==-1) {
            lastUpdateNanoTime= now;
        }

        if(lastWarningNanoTime==-1) {
            lastWarningNanoTime= now;
        }

        // Change nanoseconds to milliseconds (1 nanosecond = 1000000 milliseconds).
        int deltaTime = (int) ((now - lastUpdateNanoTime)/ 1000000 );
        int deltaWarningTime = (int) ((now - lastWarningNanoTime)/ 1000000 );

        int warningCycle = 700;
        if( deltaWarningTime > warningCycle )
        {
            lastWarningNanoTime = now;
            deltaWarningTime %= warningCycle;
            warningSignDirection = !warningSignDirection;
        }

        if( warningSignDirection )
            warningRadius = lerp( width*2, (float)width/2, (float)deltaWarningTime / warningCycle);
        else
            warningRadius = lerp( width/2, (float)width*2, (float)deltaWarningTime / warningCycle);

        this.lastUpdateNanoTime = now;
    }

    public void draw(Canvas canvas, int layer, Bitmap[] textures)  {
        GameSurface gameSurface = null;

        if( this.chunk != null ) {
            gameSurface = this.chunk.getGameSurface();
        }

        if( gameSurface != null ) {

            switch( layer ) {
                case 0:
                    // Layer 0, so UNDER the icon: the collider is there to be checked against the
                    // sprite, not to hide it. It reads the drawing correction by itself, through
                    // getColliderOffsetX/Y, and sits on the icon rather than beside it.
                    collider.draw(canvas, layer);
                    break;
                case 1:
                    Bitmap bitmap = this.getCurrentBitmap(textures);
                    canvas.drawBitmap(bitmap, gameSurface.getCanvasX(getAnchorX()+(width*anchorCorrectionX)), gameSurface.getCanvasY(getAnchorY()+(height*anchorCorrectionY)), null);
                    break;
                default:
                    break;
            }
        }
    }

    public void draw_title(Canvas canvas, int layer)  {
        GameSurface gameSurface = null;

        if( this.chunk != null ) {
            gameSurface = this.chunk.getGameSurface();
        }

        if( gameSurface != null ) {

            switch( layer ) {
                case 1:
                    String shownTitle = (StreamerMode.enabled && (this.fakeTitle != null)) ? this.fakeTitle : this.title;
                    if( (shownTitle != null) && (shownTitle.length() > 0) ) {
                        //Path path = new Path();
                        //path.moveTo(getAnchorX()+(width*anchorCorrectionX), getAnchorY()+(height*anchorCorrectionY);

                        if( false ) {


                            TextPaint textPaint = new TextPaint();
                            textPaint.setAntiAlias(true);
                            textPaint.setTextSize(16 * gameSurface.getResources().getDisplayMetrics().density);
                            textPaint.setColor(Color.BLACK);
                            int width = (int) textPaint.measureText(this.title);

                            StaticLayout staticLayout = new StaticLayout(this.title, textPaint, (int) width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0, false);

                            canvas.save();
                            canvas.drawText(this.title, gameSurface.getCanvasX(getAnchorX() + (width * anchorCorrectionX) + width/2), gameSurface.getCanvasY(getAnchorY() + (height * anchorCorrectionY) + height/2), textPaint);
                            staticLayout.draw(canvas);
                            canvas.restore();
                        } else {

                            if( this.boldTitle ) {
                                this.paintText.setTextSize(32 * gameSurface.getResources().getDisplayMetrics().density);
                                this.paintText.setTypeface(Typeface.DEFAULT_BOLD);
                            } else {
                                this.paintText.setTextSize(16 * gameSurface.getResources().getDisplayMetrics().density);
                            }


                            Rect bounds = new Rect();
                            this.paintText.getTextBounds(shownTitle, 0, shownTitle.length(), bounds);
                            //float txtWidth =textPaint.measureText(this.title);
                            float txtWidth = bounds.width();
                            float txtHeight = bounds.height();

                            canvas.drawText(shownTitle, gameSurface.getCanvasX(getAnchorTextX(txtWidth) + (width * anchorCorrectionX) ), gameSurface.getCanvasY(getAnchorTextY(txtHeight) + (height * anchorCorrectionY)), this.paintText);

                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    public void draw_warning(Canvas canvas, int layer) {
        GameSurface gameSurface = null;

        if( this.chunk != null ) {
            gameSurface = this.chunk.getGameSurface();
        }

        if (gameSurface != null) {

            switch( layer ) {
                case 1:
                    //canvas.drawCircle(gameSurface.getCanvasX(getAnchorOriginX() + (width * anchorCorrectionX) ), gameSurface.getCanvasY(getAnchorOriginY() + (height * anchorCorrectionY)), warningRadius, this.paintWarning);
                    canvas.drawCircle(gameSurface.getCanvasX(getAnchorOriginX()), gameSurface.getCanvasY(getAnchorOriginY()), warningRadius, this.paintWarning);
                    break;
                default:
                    break;
            }

        }
    }

    public void draw(Canvas canvas, int layer)  {
        draw(canvas, layer, this.allTextures);
    }

    public boolean isVisible() {
        GameSurface gameSurface = null;

        if( this.chunk != null ) {
            gameSurface = this.chunk.getGameSurface();
        }

        boolean bVisible = false;

        if( gameSurface != null )
            bVisible = gameSurface.isVisible(this);

        return bVisible;
    }
}
