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

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Rect;
import android.graphics.Typeface;

import java.util.Iterator;
import java.util.List;

public class MapGPSPos extends GameObject {

    private static final String TAG = "MapGPSPos";

    private GameSurface gameSurface;
    private AppManager _am;
    private final Paint paintAccurency;

    private float accurencyRadius = 0f;

    private Bitmap[] allPicts = null;

    private int rowUsing = 0;
    private int colUsing = 1;

    private long lastUpdateNanoTime =-1;

    // Easter egg: rate of fire. The player shoots on his own, without a button: he is walking, and
    // he already has the map to look at.
    // The player's rate of fire, in milliseconds between two tomatoes, by level. He is given the
    // throwing at level 2; it quickens at 3 and again at 4, where the crowd and then the boss
    // make the old pace untenable.
    private static final long FIRE_PERIOD_MS = 1800;
    private static final long FIRE_PERIOD_L3_MS = 1400;
    private static final long FIRE_PERIOD_L4_MS = 1000;
    private long lastFireNs = -1;

    // Bullets leave from one side of the player, then the other. Straight from his own point they
    // would come out of the red heading line and be lost in it; a quarter of a sprite to the side
    // is enough to clear it without looking as if they were fired by somebody standing next to him.
    private static final double FIRE_SIDE_OFFSET = 26.0;
    private boolean fireOnRight = true;

    // Avatar heading (task #94), read once per frame in update() and used by draw().
    private double viewAngle = 0;
    private boolean viewHeadingActive = false;
    private long lastColNanoTime =-1;

    // Avatar courant : sa categorie (AvatarCatalog.CAT_*, -1 tant que rien n'est charge) et le numero
    // de serie des reglages vu au dernier chargement, pour recharger le sprite quand l'utilisateur en
    // choisit un autre dans la page Parameters.
    private int avatarCategory = -1;
    private int avatarSerial = -1;

    // Position dans AvatarCatalog.MOVE_SEQUENCE (l'animation de marche 1,2,3,2,1,2,3,2...).
    private int seqIndex = 0;

    // Marques de proximité non-principales : dessinées à 30% d'opacité (trait + textes)
    // pour laisser ressortir la principale, qui reste pleine (comme les autres avant).
    private static final int NON_MAIN_ALPHA = 77;   // 0,30 × 255

    // Body and reach, as fractions of the reference circle, which is the half-diagonal of the
    // sprite box. Equal: a damage zone shorter than the body would never reach anything, since
    // two solid things stop when their bodies meet.
    // The life bar under the marker: it shows the hits left in the CURRENT life, not the three
    // lives -- those are already in the ring of the top-left panel.
    private static final int LIFE_BAR_HEIGHT = 16;
    private static final int LIFE_BAR_GAP = 6;

    // Between the two bars: enough to tell them apart, little enough to read them as a pair.
    private static final int BAR_SPACING = 4;

    // Half the life bar: at the same height the two read as one gauge cut in two, and the eye has
    // to work out which is which.
    private static final int ENERGY_BAR_HEIGHT = LIFE_BAR_HEIGHT / 2;

    private static final Paint ENERGY_BAR_LEFT = new Paint();
    private static final Paint ENERGY_BAR_SPENT = new Paint();
    private static final Paint LIFE_BAR_LEFT = new Paint();
    private static final Paint LIFE_BAR_LOST = new Paint();
    private static final Paint LIFE_BAR_EDGE = new Paint();

    static {
        LIFE_BAR_LEFT.setColor(Color.parseColor("#00C853"));
        LIFE_BAR_LEFT.setStyle(Paint.Style.FILL);

        LIFE_BAR_LOST.setColor(Color.parseColor("#D50000"));
        LIFE_BAR_LOST.setStyle(Paint.Style.FILL);

        ENERGY_BAR_LEFT.setColor(Color.parseColor("#FFAB00"));
        ENERGY_BAR_LEFT.setStyle(Paint.Style.FILL);

        ENERGY_BAR_SPENT.setColor(Color.parseColor("#5D4E00"));
        ENERGY_BAR_SPENT.setStyle(Paint.Style.FILL);

        LIFE_BAR_EDGE.setColor(Color.BLACK);
        LIFE_BAR_EDGE.setStyle(Paint.Style.STROKE);
        LIFE_BAR_EDGE.setStrokeWidth(1.5f);
    }

    // Le souffle du coup encaisse.
    private static final float HIT_SOUND_VOLUME = 1.0f;

    // Le coup se sent aussi dans la main : court, et pas a pleine puissance.
    private static final long HIT_VIBRATE_MS = 45;
    private static final int HIT_VIBRATE_AMPLITUDE = 110;   // sur 255

    // What being hit looks like. Owned here, driven from onHit() -- see HitEffect.
    private final HitEffect hitEffect = new HitEffect();

    private static final double HIT_COEF = 0.5;
    private static final double DAMAGE_COEF = 0.5;

    public MapGPSPos(GameSurface gameSurface) {
        super();

        anchor = Anchor.BOTTOM_MIDDLE;

        collider.setDebugColor(Color.CYAN);   // the player, told apart from the creatures and the marks

        // The body is tighter than the picture it is measured from: the reference circle is the
        // half-diagonal of the sprite box, and a sprite is never painted edge to edge. Eight
        // tenths of it is what the game actually touches and is touched on -- the same figure as
        // the creatures, so that nobody meets anybody on a different footing.
        collider.setHitCoef(HIT_COEF);
        collider.setDamageCoef(DAMAGE_COEF);

        // The body as well as the picture it is measured from: the hit zone is what creatures
        // actually bump into, so it is the one worth checking against the sprite.
        collider.setDebugDrawMasks(GameCollider.DEBUG_DRAW_REF | GameCollider.DEBUG_DRAW_HIT);


        this.gameSurface= gameSurface;
        _am = AppManager.getInstance();

        this.paintAccurency = new Paint();
        this.paintAccurency.setAntiAlias(true);
        //this.paintAccurency.setColor(Color.parseColor("#2196F3"));
        this.paintAccurency.setColor(Color.BLUE);
        this.paintAccurency.setAlpha(32);
    }

    /**
     * Easter egg: what the player tests himself against.
     *
     * The hearts lying on the ground, and nothing else. He does not look for creatures -- they
     * walk into him, and each of them tests itself -- but a heart waits to be collected, so the
     * looking is his to do. His DAMAGE zone against their bodies, read through getHurts().
     */
    @Override
    protected java.util.List<GameObject> getColliderTargets() {
        return gameSurface.getHeartObjects();
    }

    public void setPosition() {

        //Chunk chunk = _am.getCurrentChunk();
        Chunk chunk = gameSurface.getMap();
        if( chunk != null ) {
            this.x = chunk.getX(_am.getLongitude(), gameSurface.getAreaWidth());
            this.y = chunk.getY(_am.getLatitude(), gameSurface.getAreaHeight());

            gameSurface.setCamera(this.x-gameSurface.getWidth()/2, this.y-gameSurface.getHeight()/2, 0);
        }
    }

    /**
     * The hits left in the current life, under the marker.
     *
     * Hidden on full life and on nothing else, which is the three hearts and the three hits of
     * the first one: a bar that is always full says nothing and takes up room. From the first hit
     * to the end of the game it stays, even over a fresh life where it reads full -- taking it
     * away just as a heart is lost, which is the moment the player was struck, read as a healing.
     */
    private void drawLifeBar(Canvas canvas)  {

        if( !_am.getIsGameMode() )
            return;

        double energie = _am.getEnergy();

        // The life is hidden at full life and at nothing else; the energy whenever a point is
        // missing, or whenever the life is there -- two bars above a player who has just been hit
        // say more than one.
        boolean vie = !_am.getPlayerFullLife();
        boolean energy = vie || (energie < AppManager.ENERGY_MAX);

        if( !vie && !energy )
            return;

        // Two fixed slots, the life above the energy, so that nothing moves when one of the two
        // appears. The slot the other one leaves stays empty rather than being taken.
        float x0 = gameSurface.getCanvasX(getAnchorX());
        float x1 = x0 + width;

        float yEnergy = gameSurface.getCanvasY(getAnchorY()) - LIFE_BAR_GAP - ENERGY_BAR_HEIGHT;
        float yLife = yEnergy - BAR_SPACING - LIFE_BAR_HEIGHT;

        if( vie ) {

            int total = AppManager.GAME_HITS_PER_LIFE;
            int reste = total - _am.getHitsInLife();

            if( reste < 0 )
                reste = 0;

            drawBar(canvas, x0, yLife, x1, LIFE_BAR_HEIGHT, (float) reste / total,
                    LIFE_BAR_LOST, LIFE_BAR_LEFT);
        }

        if( energy )
            drawBar(canvas, x0, yEnergy, x1, ENERGY_BAR_HEIGHT,
                    (float) (energie / AppManager.ENERGY_MAX),
                    ENERGY_BAR_SPENT, ENERGY_BAR_LEFT);
    }

    /** One bar: what is gone, what is left drawn over it, and the edge around the two. */
    private void drawBar(Canvas canvas, float x0, float y0, float x1, float height, float part,
                         Paint gone, Paint left) {

        float y1 = y0 + height;

        canvas.drawRect(x0, y0, x1, y1, gone);
        canvas.drawRect(x0, y0, x0 + (x1 - x0) * part, y1, left);
        canvas.drawRect(x0, y0, x1, y1, LIFE_BAR_EDGE);
    }

    /** Struck from that point, in area coordinates: flash, step back, sparks, a sound and a nudge. */
    public void onHit(double fromX, double fromY) {

        hitEffect.start(fromX, fromY, getAnchorOriginX(), getAnchorOriginY());

        gameSurface.playSound(R.raw.eg_hou, HIT_SOUND_VOLUME);

        vibrate();
    }

    // A short knock in the hand. Short on purpose: it says "that one was for you" without
    // competing with the alarms, which are the only other thing in this application allowed to
    // make the phone move.
    private void vibrate() {

        try {
            Vibrator vibrator;

            if( Build.VERSION.SDK_INT >= 31 ) {
                VibratorManager manager = (VibratorManager) gameSurface.getContext()
                        .getSystemService(Context.VIBRATOR_MANAGER_SERVICE);

                vibrator = (manager == null) ? null : manager.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) gameSurface.getContext().getSystemService(Context.VIBRATOR_SERVICE);
            }

            if( (vibrator == null) || !vibrator.hasVibrator() )
                return;

            if( Build.VERSION.SDK_INT >= 26 )
                vibrator.vibrate(VibrationEffect.createOneShot(HIT_VIBRATE_MS, HIT_VIBRATE_AMPLITUDE));
            else
                vibrator.vibrate(HIT_VIBRATE_MS);

        } catch( Exception ignored ) {
            // Un telephone sans vibreur, ou qui le refuse : le coup se voit deja a l'ecran.
        }
    }

    public Bitmap getCurrentBitmap()  {
        int index = rowUsing*this.colCount+colUsing;

        if( (allPicts == null) || (index < 0) || (index >= allPicts.length) )
            return null;   // sprite pas (encore) charge : on ne dessine pas d'avatar plutot que planter

        return allPicts[index];
    }

    double startPosX = 0;
    double startPosY = 0;
    double endPosX = 0;
    double endPosY = 0;

    // Continuous follow model (replaces the per-fix 250 ms snap that caused the 1 s teleport): the
    // displayed position is eased toward the (already future-predicted) target every frame, never
    // set abruptly, so motion stays smooth.
    private static final double FOLLOW_HALFLIFE_MS = 400.0; // ease half the remaining gap every 400 ms
    private boolean _followInit = false;

    @Override
    public void setX(double x) {
        double diff = x - this.x;
        this.x = x;
        startPosX += diff;
        endPosX += diff;
    }

    @Override
    public void setY(double y) {
        double diff = y - this.y;
        this.y = y;
        startPosY += diff;
        endPosY += diff;
    }

    public void contextScale() {
    }

    public double offsetX(double d, double angle) {
        double x;


        if( true ) {
            x = d * Math.sin(Math.toRadians(angle));
        } else {
            if (angle >= 0 && angle <= 90)
                x = d * Math.sin(Math.toRadians(angle));
            else if (angle > 90 && angle <= 180)
                x = d * Math.cos(Math.toRadians(angle - 90));
            else if (angle > 180 && angle <= 270)
                x = -1 * d * Math.sin(Math.toRadians(angle - 180));
            else
                x = -1 * d * Math.cos(Math.toRadians(angle - 270));
        }

        return x;
    }

    public double offsetY(double d, double angle) {
        double y;

        if( true ) {
            y = d * Math.cos(Math.toRadians(angle));
        } else {
            if (angle >= 0 && angle <= 90)
                y = d * Math.cos(Math.toRadians(angle));
            else if (angle > 90 && angle <= 180)
                y = -1 * d * Math.sin(Math.toRadians(angle - 90));
            else if (angle > 180 && angle <= 270)
                y = -1 * d * Math.cos(Math.toRadians(angle - 180));
            else
                y = d * Math.sin(Math.toRadians(angle - 270));
        }
        return y;
    }

    /**
     * Easter egg: fires one bullet along the current heading.
     *
     * The bullet starts at the player's own position -- getAnchorOrigin, the point on the map, and
     * not the corner of his sprite -- so that it leaves from where he actually is whatever avatar
     * he has chosen.
     */
    public void fire() {
        double angle = _am.getViewHeading();   // cap vue (task #94)

        // Perpendicular to the heading: the heading itself is (sin, -cos), so its perpendicular
        // is (cos, sin). The side alternates at every shot.
        double side = fireOnRight ? FIRE_SIDE_OFFSET : -FIRE_SIDE_OFFSET;
        fireOnRight = !fireOnRight;

        double px = getAnchorOriginX() + side * Math.cos(Math.toRadians(angle));
        double py = getAnchorOriginY() + side * Math.sin(Math.toRadians(angle));

        gameSurface.addBullet(new BulletTomato(gameSurface, px, py, angle));
    }

    // How long between two shots, at the level being played.
    private long firePeriodMs() {

        int level = _am.getGameLevel();

        if( level >= 4 )
            return FIRE_PERIOD_L4_MS;

        if( level >= 3 )
            return FIRE_PERIOD_L3_MS;

        return FIRE_PERIOD_MS;
    }

    // Fires at the level's own rate while a game is running and the level has earned it.
    private void fireIfDue(long now) {
        if( _am.getGameLevel() < 2 )
            return;

        if( lastFireNs == -1 ) {
            lastFireNs = now;
            return;
        }

        if( (now - lastFireNs) / 1000000 < firePeriodMs() )
            return;

        lastFireNs = now;
        fire();
    }

    public void update()  {
        collider.update();
        hitEffect.update();

        // Current time in nanoseconds
        long now = System.nanoTime();

        fireIfDue(now);           // easter egg: the player shoots by himself, at his own rate

        // Never once did draw.
        if(lastUpdateNanoTime==-1) {
            lastUpdateNanoTime= now;
        }

        if(lastColNanoTime==-1) {
            lastColNanoTime= now;
        }

        // Change nanoseconds to milliseconds (1 nanosecond = 1000000 milliseconds).
        int deltaTime = (int) ((now - lastUpdateNanoTime)/ 1000000 );
        int deltaColTime = (int) ((now - lastColNanoTime)/ 1000000 );

        double speed = _am.getSpeedKm();
        double angle = _am.getViewHeading();   // cap vue (task #94): sprite facing + sight line
        boolean active = _am.isViewHeadingActive();
        if( active != viewHeadingActive )
            GBLog.d(TAG, "avatar heading -> " + (active ? "cap vue" : "cap T") + " (" + Math.round(angle) + ")");
        viewAngle = angle;
        viewHeadingActive = active;
        float accurencyKm = (float)(_am.getAccuracy() / 1000);

        // Categorie d'avatar : decidee par la machine a etats TravelMode (mode etabli, ou etat
        // instantane tant qu'elle est indeterminee), et non plus recalculee ici a chaque frame. Le
        // sprite de chaque categorie est choisi par l'utilisateur dans la page Parameters.
        int category = _am.getAvatarCategory();
        int serial = _am.getAvatarSerial();

        if( (category != this.avatarCategory) || (serial != this.avatarSerial) || (allPicts == null) ) {
            // Change vehicule

            int resId = _am.getAvatarResId(category);

            // Format du sprite (3 ou 4 colonnes) et decoupe des frames : la planche est decoupee a sa
            // resolution brute PUIS chaque frame est agrandie, sinon le filtrage de l'agrandissement
            // ferait deborder la derniere ligne d'une cellule sur la premiere de la suivante.
            AvatarCatalog.Sheet sheet = AvatarCatalog.loadSheet(gameSurface.getResources(), resId);

            if( sheet != null ) {
                this.allPicts = sheet.frames;
                this.setFrameGrid(AvatarCatalog.ROW_COUNT, sheet.columns, sheet.frameWidth, sheet.frameHeight);
            }

            if (this.colUsing >= this.colCount) {
                this.colUsing = 0;
            }

            if (this.rowUsing >= this.rowCount) {
                this.rowUsing = 0;
            }

            this.avatarCategory = category;
            this.avatarSerial = serial;
        }

        if( deltaColTime > AvatarCatalog.MOVE_STEP_MS ) {

            if( speed > 0.4 ) { // small dead-zone: don't animate walking when effectively stopped
                // Va-et-vient sur les colonnes de marche : 1,2,3,2,1,2,3,2... (les deux formats).
                int col = AvatarCatalog.MOVE_SEQUENCE[this.seqIndex];
                if (col >= this.colCount) {
                    col = this.colCount - 1;
                }
                this.colUsing = col;

                this.seqIndex++;
                if (this.seqIndex >= AvatarCatalog.MOVE_SEQUENCE.length) {
                    this.seqIndex = 0;
                }
            }
            else {
                // A l'arret : colonne dediee (la 4e) en format 4 colonnes, colonne 2 en format 3
                // colonnes. On repart du debut de la sequence au prochain deplacement.
                this.colUsing = AvatarCatalog.idleColumn(this.colCount);
                this.seqIndex = 0;
            }

            lastColNanoTime = now;
        }

        lastUpdateNanoTime  = now;






        if( (angle >= 45) && (angle < 135) )
            this.rowUsing = 2;
        else if( (angle >= 135) && (angle < 225) )
            this.rowUsing = 0;
        else if( (angle >= 225) && (angle < 315) )
            this.rowUsing = 1;
        else
            this.rowUsing = 3;

        //angle = 150;
        //speed = 200;

        //Chunk chunk = _am.getCurrentChunk();
        Chunk chunk = gameSurface.getMap();
        if( chunk != null ) {

            // Target = current (predicted) GPS position in world pixels.
            double positionX = chunk.getX(_am.getLongitude(), gameSurface.getAreaWidth());
            double positionY = chunk.getY(_am.getLatitude(), gameSurface.getAreaHeight());

            accurencyRadius = (float)(gameSurface.getAreaWidth()*accurencyKm/chunk.getWidthKM());

            // The forward prediction now lives in AppManager: getLongitude()/getLatitude() already
            // return the anticipated (future) position, so positionX/positionY ARE the target. We
            // only ease toward it for visual smoothness (no per-fix teleport). No prediction here,
            // otherwise it would be applied twice.
            double targetX = positionX;
            double targetY = positionY;

            if( !_followInit ) {
                // Snap once on the very first fix, then always ease (no teleport afterwards).
                this.x = targetX;
                this.y = targetY;
                _followInit = true;
            } else {
                // Exponential ease toward the predicted target. Continuous by construction, so the
                // marker never jumps when a new fix lands -> no per-second teleportation.
                double beta = 1.0 - Math.pow(0.5, deltaTime / FOLLOW_HALFLIFE_MS);
                this.x += beta * (targetX - this.x);
                this.y += beta * (targetY - this.y);
            }

            // Keep start/end bookkeeping coherent (setX/setY shift them on coordinate changes).
            startPosX = this.x; startPosY = this.y;
            endPosX = targetX;  endPosY = targetY;

            gameSurface.setCamera(this.x-gameSurface.getWidth()/2, this.y-gameSurface.getHeight()/2, 0);
        }
    }

    public void draw(Canvas canvas, int layer)  {
        Chunk chunk = gameSurface.getMap();

        switch( layer ) {
            case 0:
                collider.draw(canvas, layer);

                // Accuracy circle: blue translucent disc, radius = GPS accuracy at the map scale.
                canvas.drawCircle(gameSurface.getCanvasX(getAnchorOriginX()), gameSurface.getCanvasY(getAnchorOriginY()), this.accurencyRadius, this.paintAccurency);

                // Character sprite: the animated marker (frame picked from heading/motion in update()).
                Bitmap bitmap = this.getCurrentBitmap();
                if( bitmap != null )
                    canvas.drawBitmap(bitmap,
                                      gameSurface.getCanvasX(getAnchorX() + hitEffect.getOffsetX()),
                                      gameSurface.getCanvasY(getAnchorY() + hitEffect.getOffsetY()),
                                      hitEffect.isFlashing() ? HitEffect.getFlashPaint() : null);

                hitEffect.draw(canvas, gameSurface);

                drawLifeBar(canvas);

                if( true ) {
                    double curX = gameSurface.getCanvasX(getAnchorOriginX());
                    double curY = gameSurface.getCanvasY(getAnchorOriginY());

                    double angle = viewAngle;   // read in update(), nothing computed in draw
                    double w = gameSurface.getAreaWidth();
                    double h = gameSurface.getAreaHeight();

                    double simuX = curX+offsetX(w, angle);
                    double simuY = curY-offsetY(h, angle);

                    // Direction-uncertainty band: a blue translucent half-line along the heading, drawn
                    // UNDER the red line, with ROUND ends so it fully covers the acc circle. Width =
                    // acc-circle diameter (2 * accurencyRadius) widened by getUncertWidthRatio() so
                    // every considered past point falls inside the band. Reuses the meters->pixels
                    // scale of the circle (follows the zoom).
                    Paint uncertPaint = new Paint();
                    uncertPaint.setAntiAlias(true);
                    uncertPaint.setStyle(Paint.Style.STROKE);
                    uncertPaint.setStrokeCap(Paint.Cap.ROUND);
                    uncertPaint.setColor(Color.BLUE);
                    uncertPaint.setAlpha(32); // same translucency as the accuracy circle
                    uncertPaint.setStrokeWidth(2f * accurencyRadius * (float)_am.getUncertWidthRatio());
                    canvas.drawLine((float)curX, (float)curY, (float)simuX, (float)simuY, uncertPaint);

                    // GPS trace (debug): each valid past GPS position as a red filled dot, 2 m
                    // diameter (map scale). Drawn before the red line so the heading stays on top.
                    // Toggled by the "Trace" F-parameter.
                    if( _am.isTraceOn() && chunk != null ) { // chunk peut etre null (pas encore de carte) -> pas de NPE
                        double pxPerM = gameSurface.getAreaWidth() / (chunk.getWidthKM() * 1000.0);
                        float dotR = (float)(1.0 * pxPerM); // 2 m diameter
                        Paint dotPaint = new Paint();
                        dotPaint.setAntiAlias(true);
                        dotPaint.setStyle(Paint.Style.FILL);
                        dotPaint.setColor(Color.RED);
                        int hc = _am.getHistoryCount();
                        for( int i = 0; i < hc; i++ ) {
                            double wx = chunk.getX(_am.getHistoryLon(i), gameSurface.getAreaWidth());
                            double wy = chunk.getY(_am.getHistoryLat(i), gameSurface.getAreaHeight());
                            canvas.drawCircle(gameSurface.getCanvasX(wx), gameSurface.getCanvasY(wy), dotR, dotPaint);
                        }
                    }

                    // Hardware heading: solid orange half-line along the chipset's course-over-ground
                    // (Location.getBearing), drawn UNDER the red computed heading so the gap shows when
                    // they diverge. Gated by the generic DbgOverlay toggle; skipped when stopped (the
                    // chipset bearing is meaningless at rest) or when no bearing is reported.
                    if( _am.isOverlayOn() && _am.hasHwBearing() && !_am.isStopped() ) {
                        double hwAngle = _am.getHwBearing();
                        double hwX = curX + offsetX(w, hwAngle);
                        double hwY = curY - offsetY(h, hwAngle);
                        Paint hwPaint = new Paint();
                        hwPaint.setAntiAlias(true);
                        hwPaint.setStyle(Paint.Style.STROKE);
                        hwPaint.setStrokeJoin(Paint.Join.ROUND);
                        hwPaint.setStrokeCap(Paint.Cap.ROUND);
                        hwPaint.setStrokeWidth(5);
                        hwPaint.setColor(Color.rgb(255, 165, 0)); // orange
                        canvas.drawLine((float)curX, (float)curY, (float)hwX, (float)hwY, hwPaint);
                    }

                    // Predicted cap direction (used by the always-on turn cone + the debug yellow line).
                    double predAngle = _am.getPredictedHeadingDeg();
                    double pX = curX + offsetX(w, predAngle);
                    double pY = curY - offsetY(h, predAngle);

                    // Turn cone (part of the uncertainty viz, always shown): fill the sector between the
                    // current cap (red) and the predicted cap, same blue translucent style as the
                    // uncertainty band, same length as the half-lines. Naturally invisible when going
                    // straight or stopped (predcap == cap).
                    // On the cap vue (task #94) the red line no longer follows the course: the cone
                    // is hidden, only the straight band remains.
                    if( !viewHeadingActive ) {
                        Paint conePaint = new Paint();
                        conePaint.setAntiAlias(true);
                        conePaint.setStyle(Paint.Style.FILL);
                        conePaint.setColor(Color.BLUE);
                        conePaint.setAlpha(32); // same as the uncertainty band
                        Path cone = new Path();
                        cone.moveTo((float)curX, (float)curY);
                        cone.lineTo((float)simuX, (float)simuY); // along the current cap
                        cone.lineTo((float)pX, (float)pY);       // along the predicted cap
                        cone.close();
                        canvas.drawPath(cone, conePaint);
                    }

                    // Predicted heading line (yellow, debug): the cap projected forward by the rotation
                    // rate (PredCap horizon). Gated by DbgOverlay; skipped when stopped.
                    if( _am.isOverlayOn() && !_am.isStopped() ) {
                        Paint predPaint = new Paint();
                        predPaint.setAntiAlias(true);
                        predPaint.setStyle(Paint.Style.STROKE);
                        predPaint.setStrokeJoin(Paint.Join.ROUND);
                        predPaint.setStrokeCap(Paint.Cap.ROUND);
                        predPaint.setStrokeWidth(5);
                        predPaint.setColor(Color.YELLOW);
                        canvas.drawLine((float)curX, (float)curY, (float)pX, (float)pY, predPaint);
                    }

                    // Heading: the red forward half-line showing the current (computed) course.
                    Paint paint = new Paint();
                    paint.setAntiAlias(true);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeJoin(Paint.Join.ROUND);
                    paint.setStrokeCap(Paint.Cap.ROUND);
                    paint.setStrokeWidth(5);
                    paint.setColor(Color.RED);
                    canvas.drawLine((float)curX, (float)curY, (float)simuX, (float)simuY, paint);
                }


                if( true ) {

                    Iterator<PersonalMark> iterator= this._am.getMarksEnumerable().iterator();
                    while(iterator.hasNext())  {
                        PersonalMark mark = iterator.next();

                        if(mark.isSelected()) {

                            // La marque principale (affichee en haut a gauche) est dessinee comme
                            // les autres (trait 5px + texte DEFAULT_BOLD) ; ce sont les non-principales
                            // qui sont estompees (NON_MAIN_ALPHA) sur le trait et les textes.
                            boolean isMain = (mark == this._am.getShownMark());

                            if( false ) {
                                double curX = gameSurface.getCanvasX(getAnchorOriginX());
                                double curY = gameSurface.getCanvasY(getAnchorOriginY());

                                double markX = gameSurface.getCanvasX(mark.getAnchorOriginX());
                                double markY = gameSurface.getCanvasY(mark.getAnchorOriginY());

                                Paint paint = new Paint();
                                paint.setAntiAlias(true);
                                paint.setStyle(Paint.Style.STROKE);
                                paint.setStrokeJoin(Paint.Join.ROUND);
                                paint.setStrokeCap(Paint.Cap.ROUND);
                                paint.setStrokeWidth(5);
                                paint.setColor(Color.parseColor("#2196f3"));

                                canvas.drawLine((float) curX, (float) curY, (float) markX, (float) markY, paint);
                            } else if( chunk != null) {
                                // Visible portion of the GPS->mark segment, clipped to the screen
                                // rectangle (area coords). If the segment never crosses the screen
                                // (mark AND GPS off-screen) -> nothing to draw for this mark.
                                double gx = getAnchorOriginX(), gy = getAnchorOriginY();
                                double mkx = mark.getAnchorOriginX(), mky = mark.getAnchorOriginY();
                                double xmin = Math.min(gameSurface.getAreaX((float) 0), gameSurface.getAreaX((float) gameSurface.getWidth()));
                                double xmax = Math.max(gameSurface.getAreaX((float) 0), gameSurface.getAreaX((float) gameSurface.getWidth()));
                                double ymin = Math.min(gameSurface.getAreaY((float) 0), gameSurface.getAreaY((float) gameSurface.getHeight()));
                                double ymax = Math.max(gameSurface.getAreaY((float) 0), gameSurface.getAreaY((float) gameSurface.getHeight()));
                                double[] vis = Common.clipSegment(gx, gy, mkx, mky, xmin, ymin, xmax, ymax);
                                if( vis == null )
                                    continue; // segment off-screen -> no line, no bullet, no text

                                double vgx = vis[0], vgy = vis[1]; // visible endpoint, GPS side
                                double vmx = vis[2], vmy = vis[3]; // visible endpoint, mark side (bullet)

                                Path path = new Path();
                                Path pathReverse = new Path();
                                path.moveTo((float)vgx, (float)vgy);
                                path.lineTo((float)vmx, (float)vmy);
                                pathReverse.moveTo((float)vmx, (float)vmy);
                                pathReverse.lineTo((float)vgx, (float)vgy);

                                // Bullet + text anchor at the mark-side end of the VISIBLE segment
                                // (the screen-edge exit when the mark is off-screen, else the mark).
                                float xTextSibling = (float)vmx;
                                float yTextSibling = (float)vmy;
                                float distanceTextSibling = 0;

                                boolean pathTrendEast = (mkx > gx);
                                boolean reversedPath = !pathTrendEast;

                                canvas.save();
                                canvas.translate(gameSurface.getCanvasX(0), gameSurface.getCanvasY(0));


                                Paint paint = new Paint();
                                paint.setAntiAlias(true);
                                paint.setStyle(Paint.Style.STROKE);
                                paint.setStrokeJoin(Paint.Join.ROUND);
                                paint.setStrokeCap(Paint.Cap.ROUND);
                                paint.setStrokeWidth(5);
                                //paint.setColor(Color.parseColor("#2196f3"));
                                paint.setColor(Color.BLUE);
                                if( !isMain )
                                    paint.setAlpha(NON_MAIN_ALPHA);   // apres setColor (qui remet alpha=255)

                                canvas.drawPath(path, paint);

                                String title = mark.getTitle();
                                String distance = mark.getDistanceString();


                                printMarkText( canvas, title, (reversedPath ? pathReverse : path),  reversedPath, true, xTextSibling, yTextSibling, distanceTextSibling, isMain);
                                printMarkText( canvas, distance, (reversedPath ? pathReverse : path),  reversedPath,false, xTextSibling, yTextSibling, distanceTextSibling, isMain );

                                canvas.restore();
                            }

                        }
                    }
                }

                // #45 : indicateur de position hors-champ pendant le pan. Quand la carte a
                // ete deplacee au doigt (isPanActive) ET que le POINT de position est sorti de
                // l'ecran, on montre ou il est : un dot rouge colle au bord dans sa direction,
                // un chevron pointant vers lui, et la distance du point jusqu'a ce dot.
                // S'inspire de l'indicateur de mark selectionnee (clipSegment + offset vers le centre).
                // On teste le point de position (getAnchorOrigin), PAS la boite du sprite
                // (isVisible) : sinon l'apparition serait decalee et asymetrique selon le bord
                // (ancre BOTTOM_MIDDLE) -> retard a gauche/haut. Ici : symetrique, des 0 m.
                if( chunk != null && gameSurface.isPanActive() ) {

                    // Rectangle ecran + centre, en coords area (monde).
                    double xmin = Math.min(gameSurface.getAreaX((float)0), gameSurface.getAreaX((float)gameSurface.getWidth()));
                    double xmax = Math.max(gameSurface.getAreaX((float)0), gameSurface.getAreaX((float)gameSurface.getWidth()));
                    double ymin = Math.min(gameSurface.getAreaY((float)0), gameSurface.getAreaY((float)gameSurface.getHeight()));
                    double ymax = Math.max(gameSurface.getAreaY((float)0), gameSurface.getAreaY((float)gameSurface.getHeight()));
                    double ccx = (xmin + xmax) / 2.0;   // centre ecran, coords area
                    double ccy = (ymin + ymax) / 2.0;

                    double px = getAnchorOriginX();      // position courante, coords area
                    double py = getAnchorOriginY();

                    // Le point de position est-il hors de l'ecran ? (test symetrique sur les 4 bords)
                    boolean offscreen = (px < xmin) || (px > xmax) || (py < ymin) || (py > ymax);

                    // Point ou le rayon centre->position sort du rectangle ecran = le dot.
                    double[] vis = offscreen ? Common.clipSegment(ccx, ccy, px, py, xmin, ymin, xmax, ymax) : null;
                    if( vis != null ) {
                        float density = gameSurface.getResources().getDisplayMetrics().density;

                        // vis[2],vis[3] = point de sortie cote position. Passage en coords ecran.
                        double dotAx = vis[2], dotAy = vis[3];
                        float dotX = gameSurface.getCanvasX(dotAx);
                        float dotY = gameSurface.getCanvasY(dotAy);

                        // Rentre legerement le dot du bord pour qu'il ne soit pas rogne.
                        float inset = 18f * density;
                        float cvCx = gameSurface.getWidth()  / 2f;
                        float cvCy = gameSurface.getHeight() / 2f;
                        double dirx = cvCx - dotX, diry = cvCy - dotY;   // vers le centre
                        double dl = Math.hypot(dirx, diry);
                        if( dl > 1e-3 ) { dirx /= dl; diry /= dl; }
                        float cx = (float)(dotX + dirx * inset);
                        float cy = (float)(dotY + diry * inset);

                        // Distance position -> dot (px -> m -> km), format selon le mode vitesse.
                        double pxPerM = gameSurface.getAreaWidth() / (chunk.getWidthKM() * 1000.0);
                        double distKm = Math.hypot(px - dotAx, py - dotAy) / pxPerM / 1000.0;
                        String distStr = PersonalMark.formatDistance(distKm, 3);

                        float dotR = 9f * density;

                        Paint dotFill = new Paint();
                        dotFill.setAntiAlias(true);
                        dotFill.setStyle(Paint.Style.FILL);
                        dotFill.setColor(Color.RED);
                        canvas.drawCircle(cx, cy, dotR, dotFill);

                        // Chevron pointant vers l'exterieur (vers la position).
                        double outx = -dirx, outy = -diry;         // vers la position
                        double perpx = -outy, perpy = outx;        // 90 deg
                        float tip = dotR + 11f * density;
                        float half = 8f * density;
                        float baseD = dotR + 1f * density;
                        Path chevron = new Path();
                        chevron.moveTo((float)(cx + outx*tip),                (float)(cy + outy*tip));
                        chevron.lineTo((float)(cx + outx*baseD + perpx*half), (float)(cy + outy*baseD + perpy*half));
                        chevron.lineTo((float)(cx + outx*baseD - perpx*half), (float)(cy + outy*baseD - perpy*half));
                        chevron.close();
                        canvas.drawPath(chevron, dotFill);

                        // Texte distance, decale vers le centre pour rester lisible et a l'ecran,
                        // avec halo blanc pour le contraste sur la carte.
                        Paint txtBg = new Paint();
                        txtBg.setAntiAlias(true);
                        txtBg.setTextAlign(Paint.Align.CENTER);
                        txtBg.setTextSize(16f * density);
                        txtBg.setTypeface(Typeface.DEFAULT_BOLD);
                        txtBg.setStyle(Paint.Style.STROKE);
                        txtBg.setStrokeWidth(5f);
                        txtBg.setColor(Color.WHITE);
                        Paint txt = new Paint(txtBg);
                        txt.setStyle(Paint.Style.FILL);
                        txt.setColor(Color.RED);
                        float labelGap = dotR + 34f * density;
                        float lx = (float)(cx + dirx * labelGap);
                        float ly = (float)(cy + diry * labelGap) + txt.getTextSize()/3f;
                        canvas.drawText(distStr, lx, ly, txtBg);
                        canvas.drawText(distStr, lx, ly, txt);
                    }
                }

                // #52 : 3 cibles de mesure pendant le PAN. Des qu'on est en mode deplacement
                // (isPanActive, meme condition que le bouton de recentrage #26), on affiche
                // trois reticules rouges disposes en DIAGONALE : le milieu au centre de l'ecran,
                // les deux autres en coins opposes. En paysage, le viseur droit reste AVANT la
                // liste de proximite (48 dp) pour ne pas la chevaucher. Sous chaque cible, la
                // distance de ce point-ecran a la position GPS courante. But : mesurer des
                // distances sans devoir sortir la position de l'ecran (contrairement a
                // l'indicateur hors-champ #45 ci-dessus, qui coexiste).
                if( chunk != null && gameSurface.isPanActive() ) {
                    float[][] pts = computeTargetPositions(gameSurface);   // positions partagees avec le hit-test du clic
                    if( pts != null ) {
                        float density = gameSurface.getResources().getDisplayMetrics().density;
                        double gpsX = getAnchorOriginX();               // position GPS, coords area
                        double gpsY = getAnchorOriginY();
                        double pxPerM = gameSurface.getAreaWidth() / (chunk.getWidthKM() * 1000.0);
                        List<List<double[]>> chains = gameSurface.getMeasureChains();
                        // Couleur par chaine : haut=orange, milieu=rouge, bas=jaune fonce.
                        final int[] chainColors = { 0xFFFF9800, 0xFFFF0000, 0xFFB8860B };
                        boolean single = _am.getSingleTarget();   // #52 : mode cible unique -> seul le milieu

                        for( int i = 0; i < pts.length; i++ ) {
                            if( single && i != 1 )                // en mode cible unique, on ne garde que le milieu
                                continue;
                            if( !gameSurface.isTargetShown(i) )   // #64 : viseur efface par le choix du 1er clic
                                continue;
                            float sx = pts[i][0], sy = pts[i][1];
                            boolean above = (i == 2);                    // viseur du bas : textes au-dessus
                            int color = chainColors[i];
                            double vAx = gameSurface.getAreaX(sx);       // viseur -> coords area
                            double vAy = gameSurface.getAreaY(sy);
                            List<double[]> chain = (chains != null && i < chains.size()) ? chains.get(i) : null;

                            // Segments deja valides (a chaque clic) : trait 50% + mesure si la place + boules.
                            if( chain != null ) {
                                for( int k = 1; k < chain.size(); k++ ) {
                                    double[] a = chain.get(k - 1), b = chain.get(k);
                                    drawMeasureLine(canvas, a[0], a[1], b[0], b[1], density, color);
                                    double segKm = Math.hypot(a[0] - b[0], a[1] - b[1]) / pxPerM / 1000.0;
                                    drawMeasureLabel(canvas, PersonalMark.formatDistance(segKm, 3), a[0], a[1], b[0], b[1], density, color);
                                }
                            }

                            drawReticle(canvas, sx, sy, density, color);

                            if( chain == null || chain.isEmpty() ) {
                                // Avant le 1er clic : une seule mesure GPS -> viseur (ETA ◆ dist).
                                double distKm = Math.hypot(vAx - gpsX, vAy - gpsY) / pxPerM / 1000.0;
                                drawTargetDistance(canvas, _am.formatEtaDist(distKm), sx, sy, density, above, color);
                            } else {
                                // Apres >=1 clic : segment en cours (ref -> viseur) + 2 mesures (non-cumulee
                                // pres du viseur, cumulee en italique), chacune en "ETA ◆ dist".
                                double[] ref = chain.get(chain.size() - 1);
                                drawMeasureLine(canvas, ref[0], ref[1], vAx, vAy, density, color);
                                double segKm = Math.hypot(ref[0] - vAx, ref[1] - vAy) / pxPerM / 1000.0;
                                double accumKm = 0;
                                for( int k = 1; k < chain.size(); k++ )
                                    accumKm += Math.hypot(chain.get(k-1)[0]-chain.get(k)[0], chain.get(k-1)[1]-chain.get(k)[1]) / pxPerM / 1000.0;
                                drawTargetPair(canvas, _am.formatEtaDist(segKm),
                                        _am.formatEtaDist(accumKm + segKm), sx, sy, density, above, color);
                            }

                            // Boules de jonction a chaque sommet valide (points cliques + depart GPS).
                            if( chain != null )
                                for( double[] v : chain )
                                    drawJunction(canvas, v[0], v[1], density, color);
                        }
                    }
                }
                break;
            default:
                break;
        }
    }


    // Liang-Barsky: clip segment (x0,y0)->(x1,y1) to the rect [xmin,xmax]x[ymin,ymax]. Returns the
    // visible sub-segment {vx0,vy0, vx1,vy1} (x0/GPS side first), or null if it never crosses the rect.
    private void printMarkText( Canvas canvas, String text, Path path, boolean reversedPath, boolean onTop, float xSibling, float ySibling, float distanceSibling, boolean main ) {

        if( text != null && text.length() > 0 ) {

            PathMeasure pathMeasure = new PathMeasure(path, false);
            float pathLen = pathMeasure.getLength();

            Paint paintText = new Paint();
            paintText.setAntiAlias(true);
            //paintText.setColor(Color.parseColor("#2196f3"));
            paintText.setColor(Color.BLUE);

            float fontSize = 14;
            boolean bOK = false;

            float txtWidth = 0;
            float txtHeight = 0;

            // SizeChibi = 32
            float margin = (32+3) * gameSurface.getResources().getDisplayMetrics().density;

            final float fontMinSize = 9;

            while( !bOK ) {

                paintText.setTextSize(fontSize * gameSurface.getResources().getDisplayMetrics().density);

                //if( fontSize >= 12 )
                paintText.setTypeface(Typeface.DEFAULT_BOLD);

                Rect bounds = new Rect();
                paintText.getTextBounds(text, 0, text.length(), bounds);
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

            if( (txtWidth < (pathLen - margin*2)) && (fontSize >= fontMinSize) ) {
                float hOffset;
                float vOffset;


                if( false ) {
                    if (reversedPath) {
                        hOffset = pathLen - txtWidth - margin; // Text Near GPS Pos
                        //hOffset = margin; // Text Near Mark

                    } else {
                        hOffset = margin; // Text Near GPS Pos
                        //hOffset = pathLen - txtWidth - margin; // Text Near Mark
                    }
                }
                else if( true ){

                    //GEOPoint gpt = ArrayListMapPoint.getBetterHOffsetText(txtWidth, path, xSibling, ySibling, margin, true );
                    GEOPoint gpt = ArrayListMapPoint.getBetterHOffsetText(txtWidth+margin, path, xSibling, ySibling, 0, true );
                    hOffset = (float)gpt.getDistance();

                    if (reversedPath) {
                        hOffset += margin;   // west: text BEGINS `margin` past the mark-side edge
                    }
                    // east: getBetterHOffsetText already returns the start so the text END sits
                    // `margin` before the mark-side edge -> no further shift (the -= was double-counting).
                } else {

                    if (reversedPath) {
                        hOffset = distanceSibling;

                    } else {
                        hOffset = distanceSibling;
                    }
                }


                if( onTop )
                    vOffset = -10;
                else
                    vOffset = txtHeight + 10;


                // La principale reste un simple DEFAULT_BOLD, comme les autres ; les
                // non-principales sont estompees (trait + textes) via NON_MAIN_ALPHA.
                if( !main )
                    paintText.setAlpha(NON_MAIN_ALPHA);

                //canvas.drawTextOnPath(text, path, hOffset+0, vOffset, paintText);
                canvas.drawTextOnPath(text, path, hOffset, vOffset, paintText);

                if( false ) // debug bullet at the screen-edge exit (off)
                    canvas.drawCircle( xSibling, ySibling, 20, paintText);
            }
        }
    }

    // #52 : reticule facon cible (anneau + croix + point central), dans la couleur de la chaine.
    private void drawReticle(Canvas canvas, float cx, float cy, float density, int color) {
        float r = 12f * density;
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f * density);
        p.setColor(color);
        canvas.drawCircle(cx, cy, r, p);
        float arm = r + 5f * density;                 // la croix depasse un peu l'anneau
        canvas.drawLine(cx - arm, cy, cx + arm, cy, p);
        canvas.drawLine(cx, cy - arm, cx, cy + arm, p);
        Paint fill = new Paint();
        fill.setAntiAlias(true);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        canvas.drawCircle(cx, cy, 2f * density, fill);
    }

    // #52 : boule de jonction (rond plein) a un sommet de chaine, en coords AREA, couleur de la chaine.
    private void drawJunction(Canvas canvas, double ax, double ay, float density, int color) {
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        canvas.drawCircle(gameSurface.getCanvasX(ax), gameSurface.getCanvasY(ay), 4f * density, p);
    }

    // #52 : distance de la cible, rouge avec halo blanc (comme l'indicateur hors-champ #45).
    // above = true -> label AU-DESSUS du reticule (viseur du bas) ; sinon en dessous.
    private void drawTargetDistance(Canvas canvas, String distStr, float cx, float cy, float density, boolean above, int color) {
        Paint txtBg = new Paint();
        txtBg.setAntiAlias(true);
        txtBg.setTextAlign(Paint.Align.CENTER);
        txtBg.setTextSize(16f * density);
        txtBg.setTypeface(Typeface.DEFAULT_BOLD);
        txtBg.setStyle(Paint.Style.STROKE);
        txtBg.setStrokeWidth(5f);
        txtBg.setColor(Color.WHITE);
        Paint txt = new Paint(txtBg);
        txt.setStyle(Paint.Style.FILL);
        txt.setColor(color);
        float ly = above ? (cy - 24f * density)                     // au-dessus du reticule
                         : (cy + 22f * density + txt.getTextSize()); // en dessous
        canvas.drawText(distStr, cx, ly, txtBg);
        canvas.drawText(distStr, cx, ly, txt);
    }

    // #52 : trait de mesure entre deux points AREA, dans la couleur pleine de la chaine.
    private void drawMeasureLine(Canvas canvas, double ax, double ay, double bx, double by, float density, int color) {
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(3f * density);
        p.setColor(color);
        canvas.drawLine(gameSurface.getCanvasX(ax), gameSurface.getCanvasY(ay),
                        gameSurface.getCanvasX(bx), gameSurface.getCanvasY(by), p);
    }

    // #52 : mesure ecrite le long d'un segment (modele des traits de mark : police adaptative, rien si
    // pas assez de place). Couleur pleine de la chaine. Points en coords AREA. Le segment est
    // CLIPPE au rectangle ecran : la mesure est centree sur la PARTIE VISIBLE, texte gauche->droite.
    private void drawMeasureLabel(Canvas canvas, String text, double ax, double ay, double bx, double by, float density, int color) {
        if( text == null || text.length() == 0 )
            return;
        // Rectangle ecran en coords area, puis clip du segment dessus.
        double xmin = Math.min(gameSurface.getAreaX((float)0), gameSurface.getAreaX((float)gameSurface.getWidth()));
        double xmax = Math.max(gameSurface.getAreaX((float)0), gameSurface.getAreaX((float)gameSurface.getWidth()));
        double ymin = Math.min(gameSurface.getAreaY((float)0), gameSurface.getAreaY((float)gameSurface.getHeight()));
        double ymax = Math.max(gameSurface.getAreaY((float)0), gameSurface.getAreaY((float)gameSurface.getHeight()));
        double[] vis = Common.clipSegment(ax, ay, bx, by, xmin, ymin, xmax, ymax);
        if( vis == null )
            return;   // segment hors ecran -> rien

        float x0 = gameSurface.getCanvasX(vis[0]), y0 = gameSurface.getCanvasY(vis[1]);
        float x1 = gameSurface.getCanvasX(vis[2]), y1 = gameSurface.getCanvasY(vis[3]);
        float segLen = (float) Math.hypot(x1 - x0, y1 - y0);
        float margin = 20f * density;

        Paint txt = new Paint();
        txt.setAntiAlias(true);
        txt.setTypeface(Typeface.DEFAULT_BOLD);
        txt.setColor(color);

        float fontSize = 16f * density;
        final float fontMin = 9f * density;
        Rect b = new Rect();
        while( true ) {
            txt.setTextSize(fontSize);
            txt.getTextBounds(text, 0, text.length(), b);
            if( b.width() < segLen - margin * 2 )
                break;
            if( fontSize <= fontMin )
                return;   // pas la place -> rien
            fontSize -= density;
            if( fontSize < fontMin )
                fontSize = fontMin;
        }
        Path path = new Path();
        if( x0 <= x1 ) { path.moveTo(x0, y0); path.lineTo(x1, y1); }
        else           { path.moveTo(x1, y1); path.lineTo(x0, y0); }
        float hOffset = (segLen - b.width()) / 2f;   // centre sur la partie visible
        float vOffset = -6f * density;               // legerement au-dessus du trait
        canvas.drawTextOnPath(text, path, hOffset, vOffset, txt);
    }

    // #52 : 2 mesures empilees sur un viseur. near = distance au point de ref courant (la plus pres du
    // viseur) ; far = distance totale cumulee jusqu'au GPS, en italique. above = textes au-dessus.
    private void drawTargetPair(Canvas canvas, String near, String far, float cx, float cy, float density, boolean above, int color) {
        float ts = 16f * density;
        float lineH = ts + 6f * density;
        float nearBase = above ? (cy - 24f * density) : (cy + 22f * density + ts);
        float farBase  = above ? (nearBase - lineH)   : (nearBase + lineH);
        drawHaloText(canvas, near, cx, nearBase, density, false, color);
        drawHaloText(canvas, far,  cx, farBase,  density, true,  color);
    }

    // #52 : texte (couleur de la chaine) + halo blanc, centre. italic -> mesure cumulee.
    private void drawHaloText(Canvas canvas, String s, float cx, float baseline, float density, boolean italic, int color) {
        Paint bg = new Paint();
        bg.setAntiAlias(true);
        bg.setTextAlign(Paint.Align.CENTER);
        bg.setTextSize(16f * density);
        bg.setTypeface(Typeface.DEFAULT_BOLD);
        if( italic )
            bg.setTextSkewX(-0.25f);
        bg.setStyle(Paint.Style.STROKE);
        bg.setStrokeWidth(5f);
        bg.setColor(Color.WHITE);
        Paint fg = new Paint(bg);
        fg.setStyle(Paint.Style.FILL);
        fg.setColor(color);
        canvas.drawText(s, cx, baseline, bg);
        canvas.drawText(s, cx, baseline, fg);
    }

    // #52 : positions ecran (px) des 3 cibles de mesure, ou null hors mode PAN / sans chunk.
    // Partage par le rendu (draw) et le hit-test du clic (GameSurface). Positions en dp depuis
    // les bords (sauf le milieu, au centre) -> stables quelle que soit la taille d'ecran.
    public static float[][] computeTargetPositions(GameSurface gs) {
        if( gs == null || gs.getMap() == null || !gs.isPanActive() )
            return null;

        float d = gs.getResources().getDisplayMetrics().density;
        float W = gs.getWidth();
        float H = gs.getHeight();
        boolean portrait = (H >= W);

        float[][] p = new float[3][2];
        if( portrait ) {
            // haut-droite : 120 dp du bord droit (garde la liste de proximite 48 dp), 80 dp du haut
            p[0][0] = W - 120f * d;   p[0][1] = 80f * d;
            // milieu : centre ecran
            p[1][0] = 0.50f * W;      p[1][1] = 0.50f * H;
            // bas-gauche : 70 dp du bord gauche, 180 dp du bas
            p[2][0] = 70f * d;        p[2][1] = H - 180f * d;
        } else {
            // droite-haut : 120 dp du bord droit (avant la liste), 80 dp du haut
            p[0][0] = W - 120f * d;   p[0][1] = 80f * d;
            // milieu : centre ecran
            p[1][0] = 0.50f * W;      p[1][1] = 0.50f * H;
            // gauche-bas : 125 dp du bord gauche, 120 dp du bas
            p[2][0] = 125f * d;       p[2][1] = H - 120f * d;
        }
        return p;
    }

    public boolean isVisible() {
        return gameSurface.isVisible(this);
    }
}
