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

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class ArrayListMapPoint extends ArrayList<MapPoint> implements ListMapPoint {

    private static final String TAG = "ArrayListMapPoint";
    private final boolean outer;

    private Path path = null;
    private Path pathReverse = null;
    private double pathLen = 0;
    private boolean pathTrendNorth = false;
    private boolean pathTrendEast = false;

    public ArrayListMapPoint( boolean outerList ) {
        super();

        this.outer = outerList;
    }

    public Path getPath() {
        pointsToPath();
        return path;
    }
    public Path getPathReverse() {
        pointsToPath();
        return pathReverse;
    }

    // #53 : jette le Path cache (et les longueurs qui en derivent) pour qu'il soit reconstruit au
    // prochain acces, avec les coordonnees a jour. Appele par les contextScale() de MapRoad et
    // MapArea : sans cela, re-scaler les MapPoint ne changeait rien au trace des routes et des cotes,
    // qui restaient dessines a l'ancienne echelle.
    public void invalidatePath() {
        this.path = null;
        this.pathReverse = null;
        this.pathLen = 0;
    }

    public double getPathLen() {
        pointsToPath();
        return pathLen;
    }

    public boolean isPathTrendNorth() {
        pointsToPath();
        return pathTrendNorth;
    }

    public boolean isPathTrendEast() {
        pointsToPath();
        return pathTrendEast;
    }

    public GEOPoint getBetterHOffsetText(float txtWidth, boolean reversed, float centerX, float centerY, float margin) {
        pointsToPath();

        Path path;
        if( reversed )
            path = this.pathReverse;
        else
            path = this.path;

        return getBetterHOffsetText(txtWidth, path, centerX, centerY, margin, false);
    }


    public static GEOPoint getBetterHOffsetText(float txtWidth, Path path, float centerX, float centerY, float margin) {
        return getBetterHOffsetText( txtWidth, path, centerX, centerY, margin, false);
    }



    public static GEOPoint getBetterHOffsetText(float txtWidth, Path path, float centerX, float centerY, float margin, boolean isBestPrecisionStep) {

        int step = 5;
        int bestStep;
        float betterX = 0;
        float betterY = 0;
        int betterStep = 0;
        double betterDistCenter = 0;
        float betterDistance = 0;

        PathMeasure pathMeasure = new PathMeasure(path, false);
        float maxPathLen = pathMeasure.getLength() - 2*margin - txtWidth;

        bestStep = (int)maxPathLen;
        if( maxPathLen > bestStep )
            bestStep ++;

        if( txtWidth > 0) {
            int stepMax = (int) (maxPathLen / txtWidth);
            if (maxPathLen % txtWidth > 0)
                stepMax++;
            if (stepMax > 100)
                stepMax = 100;

            if (stepMax > step)
                step = stepMax;
        }

        //GBLog.d( TAG, String.format("stepMax: %d", stepMax));

        for( int j=0; j<(isBestPrecisionStep ? 2 : 1); j++ ) {
            int iStart = 0;
            int iEnd = step;
            int precisionStep = step;

            if( j > 0 ) { // isBestPrecisionStep

                iStart = (betterStep - 1) * bestStep / step;
                iEnd = (betterStep + 1) * bestStep / step;

                if( iStart < 0)
                    iStart = 0;
                else if ( iStart > bestStep )
                        iStart = bestStep;

                if( iEnd < 0)
                    iEnd = 0;
                else if ( iEnd > bestStep )
                    iEnd = bestStep;

                precisionStep = bestStep;
            }

            for (int i = iStart; i < iEnd; i++) {

                Matrix mxTransform = new Matrix();

                float distance = margin + maxPathLen * i / precisionStep;
                if (i == iEnd - 1)
                    distance = maxPathLen;
                pathMeasure.getMatrix(distance, mxTransform, PathMeasure.POSITION_MATRIX_FLAG);
                //mxTransform.postTranslate(x, y);
                float[] values = new float[9];
                mxTransform.getValues(values);
                float stepX = values[2];
                float stepY = values[5];

                // Calc dist from the center screen
                float sideX = centerX - stepX;
                float sideY = centerY - stepY;
                double stepDistCenter = Math.sqrt(sideX * sideX + sideY * sideY);

                //GBLog.d( TAG, String.format("stepX:%.1f stepY:%.1f", stepX, stepY));

                if (i == iStart) {
                    betterX = stepX;
                    betterY = stepY;
                    betterDistCenter = stepDistCenter;
                    betterStep = i;
                    betterDistance = distance;
                } else {
                    if (stepDistCenter < betterDistCenter) {
                        betterX = stepX;
                        betterY = stepY;
                        betterDistCenter = stepDistCenter;
                        betterStep = i;
                        betterDistance = distance;
                    }
                }
            }
        }

        //return ((float)pathLen-txtWidth) / 2;
        return new GEOPoint(betterX, betterY, 0, betterDistance);
    }

    private Path pointsToPath() {

        if( this.path == null ) {
            Path path = new Path();
            Path pathReverse = new Path();

            double path_len = 0;
            double path_len_N = 0;
            double path_len_S = 0;
            double path_len_E = 0;
            double path_len_W = 0;
            double ox = 0, oy = 0;

            boolean bFirst = true;


            //for (MapPoint pt : points) {
            for (int i = 0; i < this.size(); i++) {
                MapPoint pt = this.get(i);
                MapPoint ptReverse = this.get(this.size() - i - 1);

                if (bFirst) {
                    //path.moveTo(gameSurface.getCanvasX(pt.getX()), gameSurface.getCanvasY(pt.getY()));
                    //path.moveTo((float)(pt.getRealX()-x), (float)(pt.getRealY()-y));
                    path.moveTo(pt.getX(), pt.getY());
                    pathReverse.moveTo(ptReverse.getX(), ptReverse.getY());
                    ox = pt.getX();
                    oy = pt.getY();
                    bFirst = false;
                } else {
                    //path.lineTo(gameSurface.getCanvasX(pt.getX()), gameSurface.getCanvasY(pt.getY()));
                    //path.lineTo((float) (pt.getRealX() - x), (float) (pt.getRealY() - y));
                    path.lineTo(pt.getX(), pt.getY());
                    pathReverse.lineTo(ptReverse.getX(), ptReverse.getY());

                    double nx = pt.getX();
                    double ny = pt.getY();

                    double part_path_len = Math.sqrt((nx - ox) * (nx - ox) + (ny - oy) * (ny - oy));
                    path_len += part_path_len;

                    if (nx > ox)
                        path_len_E += part_path_len;
                    else
                        path_len_W += part_path_len;

                    if (ny > oy)
                        path_len_N += part_path_len;
                    else
                        path_len_S += part_path_len;

                    ox = nx;
                    oy = ny;
                }
            }
            //path.close();

            this.path = path;
            this.pathReverse = pathReverse;

            PathMeasure pathMeasure = new PathMeasure(path, false);
            this.pathLen = pathMeasure.getLength();
            this.pathTrendNorth = ( path_len_N >  path_len_S);
            this.pathTrendEast = ( path_len_E >  path_len_W);

            //Matrix mxTransform = new Matrix();
            //pathMeasure.getMatrix(pathMeasure.getLength(), mxTransform, PathMeasure.POSITION_MATRIX_FLAG );
            //float[] values = new float[pathMeasure.getLength()];
            //mxTransform.getValues( values );
        }

        return this.path;
    }

    public boolean isOuter() {
        return outer;
    }
    public boolean isInner() {
        return !outer;
    }
}
