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
import android.graphics.Canvas;

public class Explosion extends GameObject {

    // The sheet, shared by every explosion there will ever be. It used to be decoded on each tap,
    // which is a file read and an allocation for a puff of smoke that lasts a quarter of a second.
    private static Bitmap _sheet = null;

    /** Decodes the shared sheet. Called once, before the first explosion. */
    public static void loadSheet(android.content.res.Resources res) {
        if (_sheet == null)
            _sheet = android.graphics.BitmapFactory.decodeResource(res, R.drawable.explosion);
    }

    /** Builds one at a point, on the shared sheet. */
    public static Explosion at(GameSurface surface, int x, int y) {
        return new Explosion(surface, _sheet, x, y);
    }

    private int rowIndex = 0 ;
    private int colIndex = -1 ;

    private boolean finish= false;
    private GameSurface gameSurface;

    public Explosion(GameSurface GameSurface, Bitmap image, int x, int y) {
        super(image, 5, 5, x, y);

        this.gameSurface= GameSurface;
    }

    public void contextScale() {
    }

    public void update()  {
        this.colIndex++;

        // Play sound explosion.wav.
        if(this.colIndex==0 && this.rowIndex==0) {
            this.gameSurface.playSoundExplosion();
        }

        if(this.colIndex >= this.colCount)  {
            this.colIndex =0;
            this.rowIndex++;

            if(this.rowIndex>= this.rowCount)  {
                this.finish= true;
            }
        }
    }

    public void draw(Canvas canvas, int layer)  {
        if(!finish)  {

            switch( layer ) {
                case 0:
                    Bitmap bitmap= this.createSubImageAt(rowIndex,colIndex);
                    canvas.drawBitmap(bitmap, gameSurface.getCanvasX(x-width/2), gameSurface.getCanvasY(y-height/2),null);
                    //canvas.drawBitmap(bitmap, gameSurface.getCanvasX(x), gameSurface.getCanvasY(y),null);
                   //canvas.drawBitmap(bitmap, gameSurface.getCanvasX(-width/2), gameSurface.getCanvasY(-height/2),null);
                    break;
                default:
                    break;
            }
        }
    }

    public boolean isVisible() {
        return gameSurface.isVisible(this);
    }

    public boolean isFinish() {
        return finish;
    }
}
