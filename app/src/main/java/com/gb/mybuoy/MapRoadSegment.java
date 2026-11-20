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
import android.graphics.Paint;

public class MapRoadSegment extends GameObject {

    private final Chunk chunk;
    private final MapRoad road;
    private final MapPoint p1;
    private final MapPoint p2;

    private Bitmap[] allPoints;

    private int rowUsing = 0;
    private int colUsing = 0;

    private long lastUpdateNanoTime =-1;

    private Paint paintLayer0 = null;
    private Paint paintLayer1 = null;


    public MapRoadSegment(Chunk chunk, MapRoad road, MapPoint p1, MapPoint p2) {
        super();

        this.chunk = chunk;
        this.road = road;
        this.p1 = p1;
        this.p2 = p2;

        paintLayer0 = road.getPaintLayer0();
        paintLayer1 = road.getPaintLayer1();
    }

    public Bitmap getCurrentBitmap()  {
        Bitmap point = null;

        if( allPoints != null )
            point = allPoints[rowUsing*colCount+colUsing];

        return point;
    }


    public void contextScale() {
        GameSurface gameSurface = this.chunk.getGameSurface();

        if( gameSurface != null ) {
            // Shared, decoded once for the whole app (SharedTextures).
            Bitmap texture = SharedTextures.point(gameSurface.getResources());
            this.setImage(texture, 1, 1);

            this.allPoints = new Bitmap[colCount*rowCount];

            for(int col = 0; col< this.colCount; col++ ) {
                for(int row = 0; row< this.rowCount; row++ ) {
                    this.allPoints[row*this.colCount+col] = this.createSubImageAt(row, col);
                }
            }

            double minX = 0, maxX = 0, minY = 0, maxY = 0;

            minX = p1.getRealX();
            minY = p1.getRealY();
            maxX = p1.getRealX()+p1.getWidth();
            maxY = p1.getRealY()+p1.getHeight();

            if( minX > p2.getRealX() )
                minX = p2.getRealX();
            if( minY > p2.getRealY() )
                minY = p2.getRealY();
            if( maxX < p2.getRealX()+p2.getWidth() )
                maxX = p2.getRealX()+p2.getWidth();
            if(maxY < p2.getRealY()+p2.getHeight() )
                maxY = p2.getRealY()+p2.getHeight();

            this.x = minX;
            this.y = minY;
            this.width = (int)(maxX - minX);
            this.height = (int)(maxY - minY);
        }
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

        p1.update();
        p2.update();
    }

    public void draw(Canvas canvas, int layer)  {
        GameSurface gameSurface = this.chunk.getGameSurface();

        if( gameSurface != null ) {
            Bitmap bitmap = this.getCurrentBitmap();
            //canvas.drawBitmap(bitmap, gameSurface.getCanvasX(x), gameSurface.getCanvasY(y), null);

            if( false ) {
                if (p1.isVisible())
                    p1.draw(canvas, layer);

                if (p2.isVisible())
                    p2.draw(canvas, layer);
            } else {

                switch (layer) {
                    case 0:
                        canvas.drawLine(gameSurface.getCanvasX(p1.getX()),
                                gameSurface.getCanvasY(p1.getY()),
                                gameSurface.getCanvasX(p2.getX()),
                                gameSurface.getCanvasY(p2.getY()), paintLayer0);
                        break;
                    case 1:
                        canvas.drawLine(gameSurface.getCanvasX(p1.getX()),
                                gameSurface.getCanvasY(p1.getY()),
                                gameSurface.getCanvasX(p2.getX()),
                                gameSurface.getCanvasY(p2.getY()), paintLayer1);
                        break;
                    default:
                        break;
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
