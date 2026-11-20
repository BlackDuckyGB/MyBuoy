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

import androidx.databinding.BaseObservable;


public abstract class GameObject extends BaseObservable {

    public enum Anchor {
        TOP_LEFT,
        TOP_MIDDLE,
        TOP_RIGHT,
        MIDDLE_LEFT,
        MIDDLE_MIDDLE,
        MIDDLE_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_MIDDLE,
        BOTTOM_RIGHT
    }

    protected Bitmap image;

    protected int rowCount;
    protected int colCount;

    protected int WIDTH;
    protected int HEIGHT;

    protected int width;

    protected int height;
    protected double x;
    protected double y;

    // Which way this object faces and moves: a UNIT vector in area coordinates, x to the east and
    // y to the south, so that north is -y as everywhere else. North by default, for want of
    // anything better to point at.
    protected double dirX = 0;
    protected double dirY = -1;

    protected Anchor anchor = Anchor.TOP_LEFT;

    // #59 : volatile car le mute est pose par le thread UI (le tap) et relu par le thread de
    // chargement de chunk (report du mute sur les marks recreees).
    protected volatile boolean muted = false;

    // Collision shape. Built once and never refreshed: it reads the object's position and size
    // when asked, so nothing has to invalidate it -- see GameCollider.
    protected final GameCollider collider = new GameCollider(this);

    public GameObject(Bitmap image, int rowCount, int colCount, double x, double y)  {

        this.setImage(image,rowCount, colCount);

        this.x= x;
        this.y= y;
    }

    public GameObject()  {
        this( null, 0, 0, 0, 0);
    }

    protected Bitmap createSubImageAt(int row, int col)  {
        // createBitmap(bitmap, x, y, width, height).
        Bitmap subImage = null;

        if(     (image != null) &&
                (col >= 0) && (col < colCount) &&
                (row >= 0) && (row < rowCount) &&
                (width > 0) && (height > 0) ) {
            subImage = Bitmap.createBitmap(image, col * width, row * height, width, height);
        }

        return subImage;
    }

    public void setImage(Bitmap image, int rowCount, int colCount) {
        this.image = image;
        if( image != null )
        {
            if(rowCount < 1 )
                this.rowCount = 1;
            else
                this.rowCount= rowCount;

            if(colCount < 1 )
                this.colCount = 1;
            else
                this.colCount= colCount;

            this.WIDTH = image.getWidth();
            this.HEIGHT = image.getHeight();

        }
        else
        {
            this.rowCount= 1;
            this.colCount= 1;

            this.WIDTH = 0;
            this.HEIGHT = 0;
        }

        this.width = this.WIDTH/ this.colCount;
        this.height = this.HEIGHT/ this.rowCount;
    }

    // Variante pour les objets dont les frames sont deja decoupees ailleurs (cf. AvatarCatalog, qui
    // decoupe avant d'agrandir pour ne pas melanger les cellules voisines) : il n'y a plus de planche
    // a decouper ici, seulement la grille et la taille affichee d'une frame a enregistrer.
    protected void setFrameGrid(int rowCount, int colCount, int frameWidth, int frameHeight) {
        this.image = null;

        this.rowCount = (rowCount < 1) ? 1 : rowCount;
        this.colCount = (colCount < 1) ? 1 : colCount;

        this.width = frameWidth;
        this.height = frameHeight;

        this.WIDTH = frameWidth * this.colCount;
        this.HEIGHT = frameHeight * this.rowCount;
    }

    public int getX()  {
        return (int)this.x;
    }
    public double getRealX()  { return this.x; }
    public double getAnchorX()  {
        double x = this.x;

        switch( anchor ) {
            default:
            case TOP_LEFT:
            case MIDDLE_LEFT:
            case BOTTOM_LEFT:
                x -= 0;
                break;
            case TOP_MIDDLE:
            case MIDDLE_MIDDLE:
            case BOTTOM_MIDDLE:
                x -= (double)width / 2;
                break;
            case TOP_RIGHT:
            case MIDDLE_RIGHT:
            case BOTTOM_RIGHT:
                x -= (double)width;
                break;
        }

        return x;
    }
    public void setX(double x) { this.x = x; }

    public int getY()  {
        return (int)this.y;
    }
    public double getRealY()  {
        return this.y;
    }
    public double getAnchorY()  {
        double y = this.y;

        switch( anchor ) {
            default:
            case TOP_LEFT:
            case TOP_MIDDLE:
            case TOP_RIGHT:
                y -= 0;
                break;

            case MIDDLE_LEFT:
            case MIDDLE_MIDDLE:
            case MIDDLE_RIGHT:
                y -= (double)height / 2;
                break;

            case BOTTOM_LEFT:
            case BOTTOM_MIDDLE:
            case BOTTOM_RIGHT:
                y -= (double)height;
                break;
        }

        return y;
    }
    public void setY(double y) { this.y = y; }


    public double getAnchorTextX(float txtWidth)  {
        double x = getAnchorX();

        switch( anchor ) {
            default:
            case TOP_LEFT:
            case MIDDLE_LEFT:
            case BOTTOM_LEFT:
                x += 0; // Text at align Left
                break;
            case TOP_MIDDLE:
            case MIDDLE_MIDDLE:
            case BOTTOM_MIDDLE:
                x += ((double)width/2 - txtWidth/2); // Text Center
                break;
            case TOP_RIGHT:
            case MIDDLE_RIGHT:
            case BOTTOM_RIGHT:
                x += (double)(width - txtWidth);
                break;
        }

        return x;
    }


    public double getAnchorTextY(float txtHeight)  {
        double y = getAnchorY();

        switch( anchor ) {
            default:
            case TOP_LEFT:
            case TOP_MIDDLE:
            case TOP_RIGHT:
                y -= (txtHeight + 5); // Text at Top
                break;

            case MIDDLE_LEFT:
            case MIDDLE_MIDDLE:
            case MIDDLE_RIGHT:
                y -= (txtHeight + 5); // Text at Top
                break;

            case BOTTOM_LEFT:
            case BOTTOM_MIDDLE:
            case BOTTOM_RIGHT:
                y += height + txtHeight + 5; // Text at Bottom
                break;
        }

        return y;
    }

    public double getAnchorOriginX()  {
        double x = getAnchorX();

        switch( anchor ) {
            default:
            case TOP_LEFT:
            case MIDDLE_LEFT:
            case BOTTOM_LEFT:
                x += 0; // align Left
                break;
            case TOP_MIDDLE:
            case MIDDLE_MIDDLE:
            case BOTTOM_MIDDLE:
                x += ((double)width/2); // Center
                break;
            case TOP_RIGHT:
            case MIDDLE_RIGHT:
            case BOTTOM_RIGHT:
                x += (double)(width);
                break;
        }

        return x;
    }

    public double getAnchorOriginY()  {
        double y = getAnchorY();

        switch( anchor ) {
            default:
            case TOP_LEFT:
            case TOP_MIDDLE:
            case TOP_RIGHT:
                y += 0; // at Top
                break;

            case MIDDLE_LEFT:
            case MIDDLE_MIDDLE:
            case MIDDLE_RIGHT:
                y += ((double)height/2); // at Middle
                break;

            case BOTTOM_LEFT:
            case BOTTOM_MIDDLE:
            case BOTTOM_RIGHT:
                y += height; // at Bottom
                break;
        }

        return y;
    }



    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public GameCollider getCollider() {
        return collider;
    }

    /**
     * What this object's collider tests itself against, or null when it tests nothing.
     *
     * Most objects never ask the question: a mark does not go looking for what it touches, it
     * waits to be touched. Only what moves under its own power overrides this.
     */
    protected java.util.List<GameObject> getColliderTargets() {
        return null;
    }

    /**
     * Area pixels per metre, for an object whose collider is stated in metres rather than in
     * sprites. Zero -- which is what everything says here -- means the question does not arise.
     *
     * Asked of the owner and not of the surface, because the figure is not free: it is a
     * projection through the chunk, and the collider is walked over once per pair per frame.
     * Whoever answers it is expected to have measured it once, in its own update.
     */
    protected double getPixelsPerMeter() {
        return 0;
    }

    /**
     * The heading, in degrees from north, clockwise -- the way this object faces and moves.
     *
     * Everything that moves on the map has one, a creature walking as much as a bullet flying, so
     * it belongs here rather than being invented again in each of them, under a different name and
     * in a different shape.
     */
    public double getDirectionDeg() {
        return directionDeg();
    }

    // The real work: the class reads its own heading through this one, never through its public
    // door.
    private double directionDeg() {
        return Math.toDegrees(Math.atan2(dirX, -dirY));
    }

    /** Points this object along a vector of any length. A vector of no length changes nothing. */
    public void setDirection(double x, double y) {
        applyDirection(x, y);
    }

    /** Points it at a heading, in degrees from north. */
    public void setDirectionDeg(double deg) {
        applyDirection(Math.sin(Math.toRadians(deg)), -Math.cos(Math.toRadians(deg)));
    }

    // Where a direction is really set, so that a subclass can aim itself without going through a
    // public member of its own.
    protected void applyDirection(double x, double y) {

        double length = Math.sqrt(x * x + y * y);

        if( length <= 0 )
            return;

        dirX = x / length;
        dirY = y / length;
    }

    /**
     * Does this object stop the others?
     *
     * True for everything on the map. A ghost answers false, and that single answer covers both
     * sides of every contact it is part of: it walks through what it meets, and what it meets
     * walks through it. It says nothing about being SHOT at -- a ghost is still a target, it is
     * only not an obstacle.
     */
    protected boolean isSolid() {
        return true;
    }

    /**
     * Does this object stand head and shoulders above the scenery?
     *
     * A giant looks OVER the marks and over the others: nothing hides anything from it. It is not
     * the same thing as being able to walk through them -- Young-Hee sees across the whole square
     * and still cannot step through a bollard -- which is why this is a second question and not
     * another use of {@link #isSolid()}.
     */
    protected boolean isGiant() {
        return false;
    }

    /**
     * How much of a contact this object takes on itself, from 0 to 1.
     *
     * Zero for whatever the game does not move: the scenery, and the player, who is driven by the
     * satellite and not by us. One for a creature, which walks on its own legs. When two objects
     * overlap, each is given the share of the separation its own mobility is worth against the
     * sum of the two -- so a creature pushed against a mark moves alone, and two creatures move
     * half as far each.
     */
    protected double getMobility() {
        return 0;
    }

    /**
     * Some objects draw their bitmap shifted from their own position -- a mark nudges its icon by
     * a fraction of its size. The collider follows that shift, or it would sit beside the very
     * thing it describes.
     */
    protected double getColliderOffsetX() {
        return 0;
    }

    protected double getColliderOffsetY() {
        return 0;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public abstract void contextScale();
    public abstract void update();
    public abstract void draw(Canvas canvas, int layer);
    public abstract boolean isVisible();

}
