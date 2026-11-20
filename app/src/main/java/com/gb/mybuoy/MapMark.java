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
import android.util.Log;

public class MapMark extends MapPoint implements MapGameObject {


    @Override
    public String getMapObjectType() {
        return "Mark";
    }

    @Override
    public int getMapObjectSortIndex() {
        return typeMark.ordinal();
    }

    public enum TypeMark {

        // Texture Pack 0
        locality("locality",0,0, 0),
        speed_camera("speed_camera",0,1, 0, true),
        UNKNOWN("unknown",0, 0, 0);

        private String name;
        int texturesPack;
        int rowMark;
        int colMark;
        boolean warning;

        private TypeMark(String name, int texturesPack, int rowMark, int colMark, boolean warning) {
            this.name = name;
            this.texturesPack = texturesPack;
            this.rowMark = rowMark;
            this.colMark = colMark;
            this.warning = warning;
        }

        private TypeMark(String name, int texturesPack, int rowMark, int colMark) {
            this( name, texturesPack, rowMark, colMark, false);
        }

        public int getTexturesPack() {
            return texturesPack;
        }

        public int getRowMark() {
            return rowMark;
        }

        public int getColMark() {
            return colMark;
        }

        public boolean getWarning() {
            return warning;
        }

        @Override
        public String toString() {
            return name;
        }

        public static TypeMark getTypeMark( String key ) {
            TypeMark typeMark = UNKNOWN;

            if( (key != null) && (key.length() > 0) ) {

                for (TypeMark typ : TypeMark.values()) {

                    if (typ.toString().equals(key)) {

                        if( typeMark == UNKNOWN)
                            typeMark = typ;

                        if( typeMark != UNKNOWN )
                            break;
                    }
                }
            }

            if( (typeMark == UNKNOWN) ) // && (key != null) && (key.length() > 0) )
                GBLog.d( "TYPEMARK", String.format("Road type:%s ???", key));

            return typeMark;
        }
    }



    private AppManager _am;
    private final TypeMark typeMark;

    // #59 : id du node OSM. Une mark radar est RECREEE a chaque chargement de chunk, donc c'est la
    // seule chose qui permette de reconnaitre la meme mark d'un chunk au suivant (report du mute).
    private final long id;

    private static Bitmap[] bitmapsTextures = null;
    private static Bitmap[][] allBitmapsTextures = null;

    public MapMark(Chunk chunk, long id, double longitude, double latitude, String title, TypeMark typeMark, boolean boldTitle ) {
        super(chunk, longitude, latitude, title, boldTitle);
        this.fakeTitle = StreamerMode.fakePlace(this.title);   // #98

        this._am = AppManager.getInstance();
        this.id = id;
        this.typeMark = typeMark;


        switch (typeMark.getTexturesPack()) {
            default:
            case 0:
                // Anchor correction + 15Pixel at BOTTOM_MIDDLE for 192x192 picture
                this.anchor = Anchor.BOTTOM_MIDDLE;
                //this.anchor = Anchor.MIDDLE_MIDDLE;
                this.anchorCorrectionX = 0.0f;
                this.anchorCorrectionY = 15.0f / 192;
                break;
        }


        rowUsing = this.typeMark.getRowMark();
        colUsing = this.typeMark.getColMark();
    }

    public MapMark(Chunk chunk, long id, double longitude, double latitude, String title, TypeMark typeMark ) {
        this( chunk, id, longitude, latitude, title, typeMark, false);
    }


    @Override
    public long getId() {
        return id;
    }


    public TypeMark getTypeMark() {
        return typeMark;
    }


    @Override
    public void contextScale() {
        super.contextScale();

        GameSurface gameSurface = this.chunk.getGameSurface();

        if( gameSurface != null ) {
            if( this.bitmapsTextures == null ) {
                this.bitmapsTextures = new Bitmap[1];
                this.allBitmapsTextures = new Bitmap[1][];

                for( int i=0; i<1; i++) {

                    switch (i) {
                        case 0:
                            this.bitmapsTextures[i] = BitmapFactory.decodeResource(gameSurface.getResources(), R.drawable.marks_96x96);
                            this.setImage(this.bitmapsTextures[i], 6, 6);
                            break;
                        default:
                            break;
                    }

                    this.allBitmapsTextures[i] = new Bitmap[colCount * rowCount];

                    for (int col = 0; col < this.colCount; col++) {
                        for (int row = 0; row < this.rowCount; row++) {
                            this.allBitmapsTextures[i][row * this.colCount + col] = this.createSubImageAt(row, col);
                        }
                    }
                }
            }

            switch (typeMark.getTexturesPack()) {
                default:
                case 0:
                    this.setImage(this.bitmapsTextures[typeMark.getTexturesPack()], 6, 6);
                    break;
            }
        }
    }

    @Override
    public void update() {
        super.update();

        GameSurface gameSurface = this.chunk.getGameSurface();

        double distancekm = _am.getKmFrom(getLongitude(), getLatitude(), getAltitude() );
        // #81 : alarmes radar debrayables. Coupees, la mark ne rejoint plus les objets en alarme :
        // elle ne fait donc ni son, ni bande peripherique (#40), ni fleche de bord (#60). Son rond
        // rouge pulsant, lui, reste -- il dit ce QU'ELLE EST, pas qu'elle est en train d'alerter.
        if( this.typeMark.getWarning() ) {
            if( _am.getRadarAlarmOn() && _am.markShouldAlarm(distancekm) )   // #7 : distance <= WarnDist OU eta <= WarnETA
                gameSurface.addAlarmObject(this);
            else
                gameSurface.removeAlarmObject(this);
        }


    }

    @Override
    public void draw(Canvas canvas, int layer)  {
        GameSurface gameSurface = this.chunk.getGameSurface();

        if( this.typeMark.getWarning() )
            draw_warning(canvas, layer);

        draw(canvas, layer, this.allBitmapsTextures[this.typeMark.getTexturesPack()]);
        draw_title(canvas, layer);

    }
}
