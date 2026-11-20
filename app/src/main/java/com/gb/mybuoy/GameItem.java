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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/**
 * A weapon lying on the map, waiting for somebody to walk into it.
 *
 * Thrown by a mark that has grown one -- see GameOrchestrator -- it is flown out by an ItemBullet
 * and takes over where that lands. From then on it does nothing at all: it waits, it is walked
 * into, and it answers. A mine goes off, a trap holds, a swarm comes out, a skin takes somebody's
 * feet away.
 *
 * The opposite of a heart in every way that matters. A heart is taken BY the player and gives
 * something back; an item is never the player's business at all -- he walks over his own mine and
 * nothing happens, which is the only way a thing dropped at his feet could be a gift rather than
 * a punishment. It tests itself against the CREATURES, and against nothing else.
 *
 * The ghost is not among them for three of the four: it flies, and a mine, a trap and a banana
 * skin are floor. The wasps go up to meet it.
 *
 * It holds its place as a LATITUDE and a LONGITUDE and works its pixels out afresh on every
 * frame, for the reason a heart does: a chunk is swapped underneath the game whenever the player
 * travels far enough, and everything measured in that chunk's pixels is wrong the moment it is
 * replaced.
 *
 * Counted in milliseconds and not in frames, so that it lasts as long in low-resource mode too.
 */
public class GameItem extends GameObject {

    // How big a cell of the sheet is drawn, in DP. The four objects are NOT equalised to it: each
    // is trimmed to its own ink and scaled by the same factor, so the trap stays the widest of
    // them and the mine the squattest, which is how they were drawn.
    private static final float CELL_DP = 44f;

    // How much of its box the body fills. The pictures are trimmed to their ink, so the box IS
    // the object and the half-diagonal a collider measures by default reaches past the corners
    // of a shape that is nowhere square.
    private static final double HIT_COEF = 0.78;

    // The mine's ring: red, where a pole's is gold, and half as wide. Nobody should ever have to
    // wonder which of the two just went off.
    private static final int BLAST_COLOUR = Color.rgb(200, 40, 20);
    private static final long BLAST_SOUND_MS = 1300;

    private static final float OFF_VOLUME = 0.95f;

    // The four resting frames, cut and scaled ONCE for every item there will ever be. Bitmaps and
    // nothing else: holding a Context or a Resources here would outlive the screen that lent it.
    private static final int SHEET_ROWS = 4;
    private static final int SHEET_COLS = 4;
    private static final int REST_COL = 3;          // three frames of flight, then the rest frame

    private static Bitmap[] _rest = null;

    /**
     * How many pixels one cell of the sheet is drawn at, on this screen.
     *
     * Shared with ItemBullet, which scales its own copy of the sheet to the same figure: what
     * flies and what lands are one object, and seeing it change size on arrival would say so.
     */
    static float cellPx(Resources res) {
        return CELL_DP * res.getDisplayMetrics().density;
    }

    /** Cuts the four resting frames out of the shared sheet and scales them. Called once. */
    public static void loadSheet(Resources res) {

        if( (_rest != null) || (res == null) )
            return;

        Bitmap sheet = BitmapFactory.decodeResource(res, R.drawable.eg_items);

        if( sheet == null )
            return;

        int cw = sheet.getWidth() / SHEET_COLS;
        int ch = sheet.getHeight() / SHEET_ROWS;

        if( (cw < 1) || (ch < 1) ) {
            sheet.recycle();
            return;
        }

        // One factor for all four, so that their sizes stay in the proportion they were drawn in.
        double factor = cellPx(res) / cw;

        Bitmap[] rest = new Bitmap[SHEET_ROWS];

        for( int row = 0; row < SHEET_ROWS; row++ )
            rest[row] = restFrame(sheet, REST_COL * cw, row * ch, cw, ch, factor);

        // The sheet itself has no further use: every frame that matters has been taken out of it,
        // and kept as well it would be three quarters of a megabyte of flight frames nobody will
        // ever draw from here.
        sheet.recycle();

        _rest = rest;
    }

    /**
     * One resting frame, trimmed to its ink and scaled.
     *
     * Trimmed because the sheet bottom-aligns a body and the trail it had in flight, so a resting
     * object sits anywhere in its square with a third of it empty. Left untrimmed, the box a
     * collider measures would be the square and not the thing, and a creature would set off a
     * bear trap from a metre and a half away.
     */
    private static Bitmap restFrame(Bitmap sheet, int x0, int y0, int cw, int ch, double factor) {

        Bitmap cell = Bitmap.createBitmap(sheet, x0, y0, cw, ch);

        int[] pixels = new int[cw * ch];
        cell.getPixels(pixels, 0, cw, 0, 0, cw, ch);

        int left = cw;
        int top = ch;
        int right = -1;
        int bottom = -1;

        for( int y = 0; y < ch; y++ ) {
            for( int x = 0; x < cw; x++ ) {

                // Anything but the faintest edge of the anti-aliasing: a stray pixel at alpha 2
                // in a corner would undo the whole trim.
                if( (pixels[(y * cw) + x] >>> 24) < 8 )
                    continue;

                if( x < left )   left = x;
                if( x > right )  right = x;
                if( y < top )    top = y;
                if( y > bottom ) bottom = y;
            }
        }

        // An empty cell: nothing to trim to, and nothing to draw either.
        if( right < left ) {
            cell.recycle();
            return null;
        }

        int w = (right - left) + 1;
        int h = (bottom - top) + 1;

        Bitmap ink = Bitmap.createBitmap(cell, left, top, w, h);

        if( ink != cell )
            cell.recycle();

        int sw = (int) Math.round(w * factor);
        int sh = (int) Math.round(h * factor);

        if( sw < 1 ) sw = 1;
        if( sh < 1 ) sh = 1;

        Bitmap scaled = Bitmap.createScaledBitmap(ink, sw, sh, true);

        if( scaled != ink )
            ink.recycle();

        return scaled;
    }

    private static final Paint PAINT = new Paint();

    static {
        PAINT.setAntiAlias(true);
        PAINT.setFilterBitmap(true);
    }

    private final GameSurface gameSurface;

    private final GameLevels.Item kind;

    private final double latitude;
    private final double longitude;

    private final long bornMs;
    private final long liveMs;

    private int usesLeft;

    private boolean placed = false;

    // The ones it has already answered: an item goes off under a creature once, however long that
    // creature then stands on it. Without this a nest with two in it would spend both on the same
    // wasp-bitten Chucky, in two consecutive frames.
    private final List<ChibiCharacter> struck = new ArrayList<ChibiCharacter>();

    // Reused rather than made afresh: this is walked through on every frame.
    private final Rect src = new Rect();
    private final RectF dst = new RectF();

    public GameItem(GameSurface gameSurface, GameLevels.Item kind,
                    double longitude, double latitude, int usesLeft) {

        super();

        this.gameSurface = gameSurface;
        this.kind = kind;

        this.longitude = longitude;
        this.latitude = latitude;

        this.usesLeft = usesLeft;

        Bitmap image = ((_rest != null) && (kind != null)
                        && (kind.row >= 0) && (kind.row < _rest.length)) ? _rest[kind.row] : null;

        // The image is already the size it is drawn at and trimmed to its ink, so the box the
        // collider measures is the object one can see.
        setImage(image, 1, 1);

        this.anchor = Anchor.MIDDLE_MIDDLE;

        collider.setDebugColor(Color.parseColor("#6B8E23"));   // the items, in their own olive
        collider.setHitCoef(HIT_COEF);
        collider.setDamageCoef(HIT_COEF);
        collider.setDebugDrawMasks(GameCollider.DEBUG_DRAW_REF | GameCollider.DEBUG_DRAW_DAMAGE);

        this.liveMs = ((kind == null) ? 0 : kind.lifeS) * 1000L;
        this.bornMs = System.nanoTime() / 1000000;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    /** What it tests itself against: the creatures, and nobody else. Never the player. */
    @Override
    protected List<GameObject> getColliderTargets() {
        return new ArrayList<GameObject>(gameSurface.getChibis());
    }

    @Override
    public void contextScale() {
    }

    /** One step: where it is on this chunk, and who has just walked into it. */
    @Override
    public void update() {

        place();

        if( !placed || (kind == null) || (usesLeft <= 0) )
            return;

        collider.update();

        List<GameObject> against = collider.getHurts();

        if( against == null )
            return;

        for( int i = 0; i < against.size(); i++ ) {

            if( usesLeft <= 0 )
                return;

            GameObject object = against.get(i);

            if( !(object instanceof ChibiCharacter) )
                continue;

            ChibiCharacter chibi = (ChibiCharacter) object;

            // Already answered this one, already dead, or over its head: all three are nothing.
            if( struck.contains(chibi) || chibi.isDead() )
                continue;

            if( chibi.isFlying() && !kind.reachesFlyers )
                continue;

            struck.add(chibi);

            usesLeft--;

            setOff(gameSurface, kind, chibi, this.x, this.y);
        }
    }

    /**
     * It goes off, on that creature, at that spot.
     *
     * Static and shared with ItemBullet on purpose: an object caught on its way out does exactly
     * what it would have done on the ground, and writing that twice is how the two come to
     * disagree. The only thing the caller supplies is where it happened.
     *
     * The mine is apart, and deliberately so: it wounds NOBODY by contact, the ring does all of
     * it, which is what lets the same explosion take a ghost that could never have trodden on it.
     */
    static void setOff(GameSurface gameSurface, GameLevels.Item kind, ChibiCharacter chibi,
                       double x, double y) {

        if( (gameSurface == null) || (kind == null) || (chibi == null) )
            return;

        if( kind.effect == GameLevels.EFFECT_BLAST ) {

            gameSurface.addShockwave(new Shockwave(gameSurface, x, y,
                                                   GameLevels.MINE_BLAST_M, kind.damage,
                                                   BLAST_COLOUR, kind.offSound,
                                                   BLAST_SOUND_MS, 0));
            return;
        }

        // Not a shot and not a finger: it feeds no streak and pays like a blow nobody aimed,
        // which is exactly what a thing lying on the floor is.
        chibi.takeBlast(kind.damage, x, y);

        if( kind.effect == GameLevels.EFFECT_HOLD )
            chibi.holdStill(kind.effectMs);

        else if( kind.effect == GameLevels.EFFECT_SLOW )
            chibi.slowDown(kind.effectCoef, kind.effectMs);

        gameSurface.playSoundOnce(kind.offSound, OFF_VOLUME);
    }

    /** True when it is spent, or when its time on the ground is up. */
    public boolean isFinished() {

        if( usesLeft <= 0 )
            return true;

        return ((System.nanoTime() / 1000000) - bornMs) >= liveMs;
    }

    // Its pixels in the chunk of the moment. An item laid in one chunk and walked into in the
    // next has to be where the ground is, not where the old pixels were.
    private void place() {

        Chunk chunk = gameSurface.getMap();

        if( chunk == null ) {
            placed = false;
            return;
        }

        this.x = chunk.getX(longitude, gameSurface.getAreaWidth());
        this.y = chunk.getY(latitude, gameSurface.getAreaHeight());

        placed = true;
    }

    @Override
    public void draw(Canvas canvas, int layer) {

        if( (canvas == null) || !placed )
            return;

        if( layer == 0 )
            collider.draw(canvas, layer);

        if( layer != 1 )
            return;

        if( this.image == null )
            return;

        float halfW = (float) width / 2f;
        float halfH = (float) height / 2f;

        float cx = gameSurface.getCanvasX(this.x);
        float cy = gameSurface.getCanvasY(this.y);

        src.set(0, 0, this.image.getWidth(), this.image.getHeight());
        dst.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH);

        canvas.drawBitmap(this.image, src, dst, PAINT);
    }

    @Override
    public boolean isVisible() {
        return gameSurface.isVisible(this);
    }
}
