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

public class MapSeaMark extends MapPoint implements MapGameObject {


    @Override
    public String getMapObjectType() {
        return "SeaMark";
    }

    @Override
    public int getMapObjectSortIndex() {
        return typeSeaArea.ordinal();
    }

    public enum TypeSeaMark {

        // Texture Pack 0
        buoyant_GENERIC("beacon_special_purpose:::buoyant|stake|pole|perch|post|pile|lattice:::",0,7, 1),
        tower_GENERIC("beacon_special_purpose:::tower:::",0,7, 3),

        can_GENERIC("buoy_special_purpose:::can|conical:::",0,7, 2),
        pillar_GENERIC("buoy_special_purpose:::pillar|spar:::",0,7, 0),
        barrel_GENERIC("buoy_special_purpose:::barrel:::",0,7, 4),
        spherical_GENERIC("buoy_special_purpose:::spherical:::",0,7, 5),
        super_buoy_GENERIC("buoy_special_purpose:::super-buoy:::",0,7, 7),

        mooring_GENERIC("mooring::::::",0,7, 5),
        buoy_installation_GENERIC("buoy_installation::::::",0,7, 5),
        cairn_GENERIC(":::cairn:::",0,7, 6),



        tower_isolated_danger("beacon_isolated_danger:::tower:::",0,5, 3),
        beacon_isolated_danger("beacon_isolated_danger:::*:::",0,5, 1),
        buoy_isolated_danger("buoy_isolated_danger:::*:::",0,5, 0),

        //tower_safe_water("beacon_safe_water:::tower:::",0,5, 6),
        beacon_safe_water("beacon_safe_water:::*:::",0,5, 5),
        buoy_safe_water("buoy_safe_water:::*:::",0,5, 4),

        //tower_special_purpose_safe_water("beacon_special_purpose::red;white:tower:::",0,4, 3),
        beacon_special_purpose_safe_water("beacon_special_purpose::red;white:*:::",0,5, 5),
        buoy_special_purpose_safe_water("buoy_special_purpose::red;white:*:::",0,5, 4),

        tower_special_purpose("beacon_special_purpose::yellow:tower:::",0,4, 3),
        beacon_special_purpose("beacon_special_purpose::yellow:*:::",0,4, 1),
        buoy_special_purpose("buoy_special_purpose::yellow:*:::",0,4, 0),


        tower_cardinal_north("beacon_cardinal:north::tower:::",0, 1, 4),
        tower_cardinal_east("beacon_cardinal:east::tower:::",0, 1, 5),
        tower_cardinal_south("beacon_cardinal:south::tower:::",0, 1, 6),
        tower_cardinal_west("beacon_cardinal:west::tower:::",0, 1, 7),

        beacon_cardinal_north("beacon_cardinal:north::*:::",0, 1, 0),
        beacon_cardinal_east("beacon_cardinal:east::*:::",0, 1, 1),
        beacon_cardinal_south("beacon_cardinal:south::*:::",0, 1, 2),
        beacon_cardinal_west("beacon_cardinal:west::*:::",0, 1, 3),

        buoy_cardinal_north("buoy_cardinal:north::*:::",0, 0, 0),
        buoy_cardinal_east("buoy_cardinal:east::*:::",0, 0, 1),
        buoy_cardinal_south("buoy_cardinal:south::*:::",0, 0, 2),
        buoy_cardinal_west("buoy_cardinal:west::*:::",0, 0, 3),

        tower_lateral_channel_right1("beacon_lateral:channel_right::tower:::",0, 2, 3),
        tower_lateral_channel_right2("beacon_lateral::green:tower:::",0, 2, 3),
        tower_lateral_channel_left1("beacon_lateral:channel_left::tower:::",0, 3, 3),
        tower_lateral_channel_left2("beacon_lateral::red:tower:::",0, 3, 3),

        beacon_lateral_channel_right1("beacon_lateral:channel_right::*:::",0, 2, 1),
        beacon_lateral_channel_right2("beacon_lateral::green:*:::",0, 2, 1),
        beacon_lateral_channel_left1("beacon_lateral:channel_left::*:::",0, 3, 1),
        beacon_lateral_channel_left2("beacon_lateral::red:*:::",0, 3, 1),

        buoy_lateral_channel_right1("buoy_lateral:channel_right::*:::",0, 2, 0),
        buoy_lateral_channel_right2("buoy_lateral::green:*:::",0, 2, 0),
        buoy_lateral_channel_left1("buoy_lateral:channel_left::*:::",0, 3, 0),
        buoy_lateral_channel_left2("buoy_lateral::red:*:::",0, 3, 0),




        // Texture Pack 1
        rock_submerged("rock::::::submerged",1, 1, 0),
        rock_awash("rock::::::awash",1, 1, 1),
        rock_covers("rock::::::covers",1, 1, 2),




        UNKNOWN("unknown::::::",0, 0, 0);

        private String name;
        int texturesPack;
        int rowMark;
        int colMark;

        private TypeSeaMark(String name, int texturesPack, int rowMark, int colMark) {
            this.name = name;
            this.texturesPack = texturesPack;
            this.rowMark = rowMark;
            this.colMark = colMark;
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

        @Override
        public String toString() {
            return name;
        }

        public static TypeSeaMark getTypeSeaMark( String seamarkType, String seamarkCategory, String seamarkColor, String seamarkShape,String seamarkTopColor,String seamarkTopShape, String seamarkWaterLevel ) {
            TypeSeaMark typeSeaMark = UNKNOWN;

            String key = null;
            if( (seamarkType != null) && (seamarkType.length() > 0) ) {

                if( seamarkCategory == null )
                    seamarkCategory = "";
                if( seamarkColor == null )
                    seamarkColor = "";
                if( seamarkShape == null )
                    seamarkShape = "";
                if( seamarkTopColor == null )
                    seamarkTopColor = "";
                if( seamarkTopShape == null )
                    seamarkTopShape = "";
                if( seamarkWaterLevel == null )
                    seamarkWaterLevel = "";

                key = String.format("%s:%s:%s:%s:%s:%s:%s", seamarkType, seamarkCategory, seamarkColor, seamarkShape, seamarkTopColor, seamarkTopShape, seamarkWaterLevel);
            }

            if( (key != null) && (key.length() > 0) ) {
                String[] keyAttrs = key.split(":");
                int betterSameAttrCount = 0;
                int betterSamePos = 99;

                for (TypeSeaMark typ : TypeSeaMark.values()) {
                    String[] typAttrs = typ.toString().split(":");
                    int sameAttrCount = 0;
                    int sameAttrFirstPos = 0;

                    for( int i=0; i < Math.min(keyAttrs.length, typAttrs.length); i++) {
                        String[] typAttrsSplitted = typAttrs[i].split("\\|");
                        for( int j=0; j<typAttrsSplitted.length; j++ ) {

                            if( typAttrsSplitted[j].equals("*") ) {
                                if (sameAttrCount <= 0)
                                    sameAttrFirstPos = i;

                                sameAttrCount++;

                                break;
                            } else if ((keyAttrs[i].length() > 0) && (keyAttrs[i].equals(typAttrsSplitted[j]))) {
                                if (sameAttrCount <= 0)
                                    sameAttrFirstPos = i;

                                sameAttrCount++;

                                break;
                            }
                        }
                    }

                    if( sameAttrCount > 0 )
                    {
                        if( typeSeaMark == null ) {
                            typeSeaMark = typ;
                            betterSameAttrCount = sameAttrCount;
                            betterSamePos = sameAttrFirstPos;
                        } else if( sameAttrFirstPos < betterSamePos ) {
                            typeSeaMark = typ;
                            betterSameAttrCount = sameAttrCount;
                            betterSamePos = sameAttrFirstPos;
                        } else if( sameAttrCount > betterSameAttrCount ) {
                            typeSeaMark = typ;
                            betterSameAttrCount = sameAttrCount;
                            betterSamePos = sameAttrFirstPos;
                        }
                    }
                    //else if( typeSeaMark != UNKNOWN )
                    //    break;
                }
            }

            if( (typeSeaMark == UNKNOWN) && (key != null) && (key.length() > 0) )
                GBLog.d( "TYPESEAMARK", String.format("SeaMark type: %s ???", key));

            return typeSeaMark;
        }
    }



    private final TypeSeaMark typeSeaArea;

    private static Bitmap[] bitmapsTextures = null;
    private static Bitmap[][] allBitmapsTextures = null;

    public MapSeaMark(Chunk chunk, double longitude, double latitude, String title, TypeSeaMark typeSeaArea) {
        super(chunk, longitude, latitude, title);
        this.fakeTitle = StreamerMode.fakeBuoy(this.title);   // #98

        this.typeSeaArea = typeSeaArea;


        switch (typeSeaArea.getTexturesPack()) {
            default:
            case 0:
                // Anchor correction + 15Pixel at BOTTOM_MIDDLE for 192x192 picture
                this.anchor = Anchor.BOTTOM_MIDDLE;
                this.anchorCorrectionX = 0.0f;
                this.anchorCorrectionY = 15.0f / 192;
                break;
            case 1:
                this.anchor = Anchor.MIDDLE_MIDDLE;
                this.anchorCorrectionX = 0.0f;
                this.anchorCorrectionY = 0.0f;
                break;
        }


        rowUsing = this.typeSeaArea.getRowMark();
        colUsing = this.typeSeaArea.getColMark();
    }

    public TypeSeaMark getTypeSeaMark() {
        return typeSeaArea;
    }


    @Override
    public void contextScale() {
        super.contextScale();

        GameSurface gameSurface = this.chunk.getGameSurface();

        if( gameSurface != null ) {
            if( this.bitmapsTextures == null ) {
                this.bitmapsTextures = new Bitmap[2];
                this.allBitmapsTextures = new Bitmap[2][];

                for( int i=0; i<2; i++) {

                    switch (i) {
                        case 0:
                            this.bitmapsTextures[i] = BitmapFactory.decodeResource(gameSurface.getResources(), R.drawable.seamarks_96x96);
                            this.setImage(this.bitmapsTextures[i], 9, 8);
                            break;
                        case 1:
                            this.bitmapsTextures[i] = BitmapFactory.decodeResource(gameSurface.getResources(), R.drawable.seamarks2_96x96);
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

            switch (typeSeaArea.getTexturesPack()) {
                default:
                case 0:
                    this.setImage(this.bitmapsTextures[typeSeaArea.getTexturesPack()], 9, 8);
                    break;
                case 1:
                    this.setImage(this.bitmapsTextures[typeSeaArea.getTexturesPack()], 6, 6);
                    break;
            }
        }
    }

    @Override
    public void draw(Canvas canvas, int layer)  {

        draw(canvas, layer, this.allBitmapsTextures[this.typeSeaArea.getTexturesPack()]);
        draw_title(canvas, layer);
    }
}
