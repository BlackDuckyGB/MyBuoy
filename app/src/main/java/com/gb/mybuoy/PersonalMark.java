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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.gb.mybuoy.sqlite.AppDB;
import com.gb.mybuoy.sqlite.PersonalMarkDB;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.core.content.ContextCompat;
import androidx.databinding.Bindable;
import androidx.databinding.BindingAdapter;
import androidx.databinding.Observable;

public class PersonalMark extends MapPoint implements MapGameObject {

    @Override
    public String getMapObjectType() {
        return "PersonalMark";
    }

    @Override
    public int getMapObjectSortIndex() {
        return getBitmapId();
    }

    private static final String TAG = "PersonalMark";

    private AppManager _am;

    private long id;

    private boolean shared;
    private Date sharedTime;

    // #7 : marque armee -> rond rouge pulsant sur la carte, et alarme a l'approche comme un radar.
    private boolean alarmed;

    // #7 : marque EN TRAIN de signaler, c'est-a-dire armee ET dans les criteres WarnDist/WarnETA.
    // Calcule par la boucle de proximite d'AppManager en meme temps que la distance, et non persiste :
    // il ne sert qu'a l'affichage (ordre des listes, presence forcee dans la liste de proximite). Le
    // declenchement du son, lui, est decide image par image dans update(), comme pour un radar.
    private volatile boolean alarming;

    private static final int ROWBITMAPCOUNT = 6;
    private static final int COLBITMAPCOUNT = 6;

    private double distance;

    // Height difference from the current position to the mark, shown in the marks list. Computed
    // with the distance, not persisted. Hysteresis around the threshold (a Global parameter): shown
    // from it, hidden again ELEVATION_HYSTERESIS_M below, so GPS altitude noise does not make it blink.
    private static final double ELEVATION_HYSTERESIS_M = 1.0;
    private boolean elevationShown = false;
    private String elevationAngleString = "";
    private String elevationDeltaString = "";
    // Same thing as numbers, for the top-left panel which draws them its own way.
    private boolean elevationUp = false;
    private long elevationDegrees = 0;
    private long elevationAbsMeters = 0;

    // Cap courant (secteur 0=N,1=NE,…,7=NO) de la position courante vers la mark. Calculé comme la
    // distance (au même endroit), non persisté.
    private int heading = 0;

    private PersonalMark cloneFrom;

    private boolean selected;

    // --- the pole game (easter egg) ------------------------------------------------------------
    //
    // A mark whose icon says so is a POLE, and walking into it scores. The orchestrator runs it:
    // it arms it when a level starts, counts it down, and collects it when the player goes
    // through. Nothing here decides anything -- the rules are in GameLevels, the clock is in
    // GameOrchestrator. A mark only holds what the drawing has to show and what its collider has
    // to measure itself with.
    //
    // Held here rather than in a table of the orchestrator's own because these objects LAST: the
    // marks are read from the database once, at startup (AppManager.dbMarksInfos), and the list
    // is never rebuilt afterwards.

    private int poleValue = 0;             // what it is worth right now; zero when out of play
    private long poleDueNs = -1;           // when it falls to what reaching it in time would pay
    private String poleText = null;        // the same, written, built when the number changes
    private long poleDeadUntilNs = -1;     // scored: worth nothing again before then
    private boolean poleWentAway = false;  // ... and not before it has been left behind, either
    private int poleScored = 0;            // how many times it has paid, which halves what it pays

    // Area pixels per metre, measured in update(). Not read from the surface when it is wanted:
    // the collider asks for it once per object per frame, and the measure is a projection through
    // the chunk.
    private double pixelsPerMeter = 0;

    // The figure drawn above a pole. Static like the sprite sheets: there is one of them on
    // screen at a time per pole, and they all look the same.
    private static final float POLE_TEXT_DP = 22f;

    // How far BELOW the top of the icon the figure sits. It used to be written clear above it,
    // where it read as a label hanging in the air rather than as the price of the thing under it.
    private static final float POLE_DROP_DP = 8f;

    private static final Paint POLE_TEXT = new Paint();
    private static final Paint POLE_SHADOW = new Paint();

    // --- the heart gauge (easter egg) ----------------------------------------------------------
    //
    // Certain places GROW hearts: a gauge fills on their own slow clock, and choosing such a mark
    // as the main one, once it is full, makes it drop what it has grown. The gauge is drawn above
    // the icon, in the pink of the life it gives back, so that a glance across the map says which
    // detour is worth making.
    //
    // Filled by the orchestrator, like everything else about this game: a mark holds, it does not
    // decide.
    private float heartFill = 0;

    // And the same again for the four places that grow a WEAPON rather than a heart. Olive where
    // the other is pink, and that is the whole of the difference: a mark is either one or the
    // other, never both, so the two gauges never share a mark and never share a line.
    private float itemFill = 0;

    private static final float GAUGE_HEIGHT_DP = 7f;
    private static final float GAUGE_WIDTH_PART = 0.78f;

    // How far BELOW the top of the icon the gauge sits. It was drawn clear above it, where it
    // floated between the mark and whatever was behind it and read as belonging to neither.
    private static final float GAUGE_DROP_DP = 10f;

    // The bed's outline, kept to clip the filling to it. Reused rather than made afresh: this
    // runs on every frame, for every mark that grows anything.
    private static final Path GAUGE_PATH = new Path();
    private static final RectF GAUGE_RECT = new RectF();

    private static final Paint GAUGE_BED = new Paint();
    private static final Paint GAUGE_PINK = new Paint();
    private static final Paint GAUGE_OLIVE = new Paint();
    private static final Paint GAUGE_EDGE = new Paint();

    static {
        GAUGE_BED.setAntiAlias(true);
        GAUGE_BED.setStyle(Paint.Style.FILL);
        GAUGE_BED.setColor(Color.argb(150, 20, 20, 20));

        // The pink of the life bar itself: what this gauge promises is that heart, not a colour
        // of its own.
        GAUGE_PINK.setAntiAlias(true);
        GAUGE_PINK.setStyle(Paint.Style.FILL);
        GAUGE_PINK.setColor(Color.rgb(0xff, 0x4d, 0x7e));

        GAUGE_OLIVE.setAntiAlias(true);
        GAUGE_OLIVE.setStyle(Paint.Style.FILL);
        GAUGE_OLIVE.setColor(Color.rgb(0x6b, 0x8e, 0x23));

        GAUGE_EDGE.setAntiAlias(true);
        GAUGE_EDGE.setStyle(Paint.Style.STROKE);
        GAUGE_EDGE.setColor(Color.argb(210, 255, 255, 255));
    }

    static {
        POLE_TEXT.setAntiAlias(true);
        POLE_TEXT.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        POLE_TEXT.setTextAlign(Paint.Align.CENTER);
        POLE_TEXT.setColor(Color.rgb(16, 16, 16));

        POLE_SHADOW.setAntiAlias(true);
        POLE_SHADOW.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        POLE_SHADOW.setTextAlign(Paint.Align.CENTER);
        // A light outline behind the dark figure, so that it holds over a dark map too.
        POLE_SHADOW.setColor(Color.argb(200, 255, 255, 255));
    }


    // The icon sheets are decoded ONCE, by loadBitmaps(), at startup, and never change afterwards.
    // No lock anywhere around them: every field below is volatile and assigned a fully built
    // object, bitmapsTextures last, so whoever sees it non-null sees all the others too.
    //
    // There used to be a static syncObject here, taken by every constructor (initBitmaps). The
    // proximity thread cloned marks under AppManager's write lock, so it held the write lock and
    // waited for syncObject, while the main thread held syncObject in getMarksAdapter() and waited
    // for the read lock: deadlock, ANR on 01/10/2026 with the mark editor open.
    private static volatile Bitmap bitmapsTextures = null;
    private static volatile Bitmap[] allBitmapsTextures = null;
    private static volatile List<BitmapDrawable> allBitmapsTexturesList = null;
    private static volatile List<BitmapDrawable> allSelectedBitmapsTexturesList = null;

    // Les 8 flèches de direction (secteur de 45°), indexées par `heading`.
    //  - arrowDrawablesList  : flèche nue (liste principale, avant Edit).
    //  - arrowBadge*         : flèche dans un rond façon bouton burger (liste de proximité). Normal =
    //                          rond bleu / flèche blanche ; sélectionné = rond blanc / flèche bleue.
    private static volatile List<Drawable> arrowDrawablesList = null;
    private static volatile List<Drawable> arrowBadgeList = null;
    private static volatile List<Drawable> arrowBadgeSelectedList = null;

    // Easter egg: the same badges in the pink of the life gauge, for a place whose hearts are
    // ripe. Same two states as the blue ones, selected and not.
    private static volatile List<Drawable> arrowBadgeHealList = null;
    private static volatile List<Drawable> arrowBadgeHealSelectedList = null;

    // Easter egg: and in the olive of the item gauge, for a place whose weapon is ready.
    private static volatile List<Drawable> arrowBadgeItemList = null;
    private static volatile List<Drawable> arrowBadgeItemSelectedList = null;
    // #7 : les memes en rouge, pour une marque dont l'alarme est activee. Le rouge remplace le bleu
    // et rien d'autre : le mode inverse de la selection reste le meme (rond plein / rond blanc).
    private static volatile List<Drawable> arrowAlarmList = null;
    private static volatile List<Drawable> arrowBadgeAlarmList = null;
    private static volatile List<Drawable> arrowBadgeAlarmSelectedList = null;
    private static final int[] ARROW_RES = {
        R.drawable.ic_mark_dir_n,  R.drawable.ic_mark_dir_ne, R.drawable.ic_mark_dir_e,  R.drawable.ic_mark_dir_se,
        R.drawable.ic_mark_dir_s,  R.drawable.ic_mark_dir_sw, R.drawable.ic_mark_dir_w,  R.drawable.ic_mark_dir_nw
    };
    private static final int[] ARROW_BADGE_RES = {
        R.drawable.ic_mark_badge_n,  R.drawable.ic_mark_badge_ne, R.drawable.ic_mark_badge_e,  R.drawable.ic_mark_badge_se,
        R.drawable.ic_mark_badge_s,  R.drawable.ic_mark_badge_sw, R.drawable.ic_mark_badge_w,  R.drawable.ic_mark_badge_nw
    };
    private static final int[] ARROW_BADGE_SEL_RES = {
        R.drawable.ic_mark_badge_n_sel,  R.drawable.ic_mark_badge_ne_sel, R.drawable.ic_mark_badge_e_sel,  R.drawable.ic_mark_badge_se_sel,
        R.drawable.ic_mark_badge_s_sel,  R.drawable.ic_mark_badge_sw_sel, R.drawable.ic_mark_badge_w_sel,  R.drawable.ic_mark_badge_nw_sel
    };

    // Easter egg: pink circle and white arrow, then the inverse when the mark is selected.
    private static final int[] ARROW_BADGE_HEAL_RES = {
        R.drawable.ic_mark_badge_n_heal,  R.drawable.ic_mark_badge_ne_heal, R.drawable.ic_mark_badge_e_heal,  R.drawable.ic_mark_badge_se_heal,
        R.drawable.ic_mark_badge_s_heal,  R.drawable.ic_mark_badge_sw_heal, R.drawable.ic_mark_badge_w_heal,  R.drawable.ic_mark_badge_nw_heal
    };
    private static final int[] ARROW_BADGE_HEAL_SEL_RES = {
        R.drawable.ic_mark_badge_n_heal_sel,  R.drawable.ic_mark_badge_ne_heal_sel, R.drawable.ic_mark_badge_e_heal_sel,  R.drawable.ic_mark_badge_se_heal_sel,
        R.drawable.ic_mark_badge_s_heal_sel,  R.drawable.ic_mark_badge_sw_heal_sel, R.drawable.ic_mark_badge_w_heal_sel,  R.drawable.ic_mark_badge_nw_heal_sel
    };

    // Easter egg: olive circle and white arrow, then the inverse when the mark is selected.
    private static final int[] ARROW_BADGE_ITEM_RES = {
        R.drawable.ic_mark_badge_n_item,  R.drawable.ic_mark_badge_ne_item, R.drawable.ic_mark_badge_e_item,  R.drawable.ic_mark_badge_se_item,
        R.drawable.ic_mark_badge_s_item,  R.drawable.ic_mark_badge_sw_item, R.drawable.ic_mark_badge_w_item,  R.drawable.ic_mark_badge_nw_item
    };
    private static final int[] ARROW_BADGE_ITEM_SEL_RES = {
        R.drawable.ic_mark_badge_n_item_sel,  R.drawable.ic_mark_badge_ne_item_sel, R.drawable.ic_mark_badge_e_item_sel,  R.drawable.ic_mark_badge_se_item_sel,
        R.drawable.ic_mark_badge_s_item_sel,  R.drawable.ic_mark_badge_sw_item_sel, R.drawable.ic_mark_badge_w_item_sel,  R.drawable.ic_mark_badge_nw_item_sel
    };

    private static final int[] ARROW_ALARM_RES = {
        R.drawable.ic_mark_dir_n_alarm,  R.drawable.ic_mark_dir_ne_alarm, R.drawable.ic_mark_dir_e_alarm,  R.drawable.ic_mark_dir_se_alarm,
        R.drawable.ic_mark_dir_s_alarm,  R.drawable.ic_mark_dir_sw_alarm, R.drawable.ic_mark_dir_w_alarm,  R.drawable.ic_mark_dir_nw_alarm
    };
    private static final int[] ARROW_BADGE_ALARM_RES = {
        R.drawable.ic_mark_badge_n_alarm,  R.drawable.ic_mark_badge_ne_alarm, R.drawable.ic_mark_badge_e_alarm,  R.drawable.ic_mark_badge_se_alarm,
        R.drawable.ic_mark_badge_s_alarm,  R.drawable.ic_mark_badge_sw_alarm, R.drawable.ic_mark_badge_w_alarm,  R.drawable.ic_mark_badge_nw_alarm
    };
    private static final int[] ARROW_BADGE_ALARM_SEL_RES = {
        R.drawable.ic_mark_badge_n_alarm_sel,  R.drawable.ic_mark_badge_ne_alarm_sel, R.drawable.ic_mark_badge_e_alarm_sel,  R.drawable.ic_mark_badge_se_alarm_sel,
        R.drawable.ic_mark_badge_s_alarm_sel,  R.drawable.ic_mark_badge_sw_alarm_sel, R.drawable.ic_mark_badge_w_alarm_sel,  R.drawable.ic_mark_badge_nw_alarm_sel
    };

    // The roller adapter and its order are only touched by data binding, on the main thread.
    private static volatile ArrayAdapter marksAdapter = null;
    private static volatile int sheetCount = 0;                  // number of pm_*.png sheets loaded
    private static volatile int[] rollerFlatByPosition = null;   // roller position -> flat index (sheet*36+row*6+col)
    // Derniere icone sauvegardee (creation OU edition) depuis le demarrage : le roller la propose en
    // premier et une nouvelle mark demarre dessus. Volontairement EN MEMOIRE, jamais persistee.
    private static volatile int lastUsedFlat = -1;


    public PersonalMark(Chunk chunk, long id, double longitude, double latitude, String title, int rowBitmap, int colBitmap, boolean shared, Date sharedTime, double distance, boolean selected, boolean alarmed) {
        super(chunk, longitude, latitude, title, false);

        this._am = AppManager.getInstance();

        this.id = id;

        this.shared = shared;
        this.sharedTime = sharedTime;

        this.rowUsing = rowBitmap;
        this.colUsing = colBitmap;
        // Anchor correction + 15Pixel at BOTTOM_MIDDLE for 192x192 picture
        this.anchor = Anchor.BOTTOM_MIDDLE;
        this.anchorCorrectionX = 0.0f;
        this.anchorCorrectionY = 15.0f / 192;

        this.distance = distance;

        this.cloneFrom = null;
        this.selected = selected;
        this.alarmed = alarmed;

        // No initBitmaps() here any more: a constructor takes no lock and calls nothing opaque.
        // The sheets are loaded once at startup (loadBitmaps, from AppManager.getInstance(context)).
    }


    public PersonalMark(Chunk chunk, double longitude, double latitude ) {
        this( chunk, 0, longitude, latitude, "", 0, 0, false,null, 0, true, false);

        applyLastUsedBitmap();
    }

    // #65 : creation avec un titre deja rempli (libelle recu d'une autre application).
    public PersonalMark(Chunk chunk, double longitude, double latitude, String title ) {
        this( chunk, 0, longitude, latitude, title, 0, 0, false, null, 0, true, false);

        applyLastUsedBitmap();
    }
    public PersonalMark( ) {
        this( null, 0, 0, 0, "", 0, 0, false, null, 0, false, false);
    }

    public PersonalMark( PersonalMark mark) {
        this(mark.getChunk(), mark.getId(), mark.getLongitude(), mark.getLatitude(), mark.getTitle(), mark.getRowBitmap(), mark.getColBitmap(), mark.isShared(), mark.getSharedTime(), mark.getDistance(), mark.isSelected(), mark.isAlarmed());

        this.cloneFrom = mark;
        this.coord.altitude = mark.coord.altitude;
        this.heading = mark.heading;
        this.elevationShown = mark.elevationShown;
        this.elevationAngleString = mark.elevationAngleString;
        this.elevationDeltaString = mark.elevationDeltaString;
        this.elevationUp = mark.elevationUp;
        this.elevationDegrees = mark.elevationDegrees;
        this.elevationAbsMeters = mark.elevationAbsMeters;
        this.alarming = mark.alarming;
        // La planche ne passe pas par le constructeur complet (row/col seulement) : sans cette ligne
        // un clone d'une mark de pm_002 s'affiche et se sauvegarde avec l'icone de pm_001.
        this.sheetUsing = mark.sheetUsing;
    }

    @Override
    @Bindable
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;

        notifyPropertyChanged(BR.id);
    }

    public double getLatitude() {
        return coord.latitude;
    }

    public void setLatitude(double latitude) {
        this.coord.setLatitude(latitude);

        notifyPropertyChanged(BR.latitude);
    }

    public double getLongitude() {
        return coord.longitude;
    }

    public void setLongitude(double longitude) {
        this.coord.setLongitude(longitude);

        notifyPropertyChanged(BR.longitude);
    }

    // Metres, null when unknown. Read with MapPoint.getAltitude().
    public void setAltitude(Double altitude) {
        this.coord.setAltitude(altitude);

        notifyPropertyChanged(BR.altitude);
    }

    // Editor field: whole metres, empty when unknown.
    @Bindable
    public String getAltitudeEditString() {
        if( coord.altitude == null ) {
            return "";
        }
        return String.format(Locale.US, "%d", Math.round(coord.altitude));
    }

    // Not re-notified, so the text is not rewritten under the user's fingers. Empty or unreadable
    // (a lone "-" while typing) = unknown.
    public void setAltitudeEditString(String s) {
        Double altitude = null;
        if( s != null ) {
            s = s.trim();
            if( !s.isEmpty() ) {
                try {
                    altitude = (double) Long.parseLong(s);
                } catch (NumberFormatException e) {
                    altitude = null;
                }
            }
        }
        coord.setAltitude(altitude);
    }

    @Bindable
    public String getLongitudeString() {
        GPSCoordonate c = StreamerMode.shift(coord);   // #98 : shifted copy in Streamer mode
        switch( _am.getCoordFmt() ) {
            case AppManager.COORDFMT_DD:
                return c.lonDD();
            case AppManager.COORDFMT_DMS:
                return c.lonDMS();
            default:
                return c.lonDM();
        }
    }

    @Bindable
    public String getLatitudeString() {
        GPSCoordonate c = StreamerMode.shift(coord);   // #98 : shifted copy in Streamer mode
        switch( _am.getCoordFmt() ) {
            case AppManager.COORDFMT_DD:
                return c.latDD();
            case AppManager.COORDFMT_DMS:
                return c.latDMS();
            default:
                return c.latDM();
        }
    }

    // ---- #17 : edition DMS (deg / min / sec + hemisphere) ; recalcule le decimal a chaque changement ----

    // ---- Edition DMS : delegue a coord (GPSCoordonate garde decimal/DMS/DM synchronises). Les binders
    // restent ici pour l'affichage (@Bindable) ; on notifie le decimal, pas le champ en cours de frappe. ----

    private static int parseIntSafe(String s) {
        if( s == null ) {
            return 0;
        }
        s = s.trim();
        if( s.isEmpty() ) {
            return 0;
        }
        try {
            return Integer.parseInt(s);
        } catch( NumberFormatException ex ) {
            return 0;
        }
    }

    private static double parseDoubleSafe(String s) {
        if( s == null ) {
            return 0.0;
        }
        s = s.trim().replace(',', '.');   // tolere la virgule (clavier FR)
        if( s.isEmpty() ) {
            return 0.0;
        }
        try {
            return Double.parseDouble(s);
        } catch( NumberFormatException ex ) {
            return 0.0;
        }
    }

    private static int clampInt(int v, int min, int max) {
        if( v < min ) {
            return min;
        }
        if( v > max ) {
            return max;
        }
        return v;
    }

    private static double clampDouble(double v, double min, double max) {
        if( v < min ) {
            return min;
        }
        if( v > max ) {
            return max;
        }
        return v;
    }

    // Apres edition d'une composante : on rafraichit le decimal affiche (pas le champ en cours de frappe).
    private void notifyLatChanged() {
        notifyPropertyChanged(BR.latitudeString);
    }

    private void notifyLonChanged() {
        notifyPropertyChanged(BR.longitudeString);
    }

    // #98 : Streamer mode -> the popup shows the (shifted) coordinates read-only, instead of the
    // input fields, which would show and edit the real ones.
    @Bindable
    public boolean getStreamerMode() {
        return StreamerMode.enabled;
    }

    // Format de saisie courant (DD / DM / DMS) -> pilote la visibilite des 3 includes du popup.
    @Bindable
    public int getCoordFmt() {
        return _am.getCoordFmt();
    }

    // --- Latitude DMS ---
    @Bindable
    public String getLatDegString() {
        return String.valueOf(coord.getLatDeg());
    }

    public void setLatDegString(String s) {
        coord.setLatDeg(clampInt(parseIntSafe(s), 0, 90));
        notifyLatChanged();
    }

    @Bindable
    public String getLatMinString() {
        return String.valueOf(coord.getLatMin());
    }

    public void setLatMinString(String s) {
        coord.setLatMin(clampInt(parseIntSafe(s), 0, 59));
        notifyLatChanged();
    }

    @Bindable
    public String getLatSecString() {
        return String.format(Locale.US, "%.2f", coord.getLatSec());
    }

    public void setLatSecString(String s) {
        coord.setLatSec(clampDouble(parseDoubleSafe(s), 0.0, 59.99));
        notifyLatChanged();
    }

    // --- Latitude DM (minutes decimales) ---
    @Bindable
    public String getLatMinDecString() {
        return String.format(Locale.US, "%.4f", coord.getLatMinDec());
    }

    public void setLatMinDecString(String s) {
        coord.setLatMinDec(clampDouble(parseDoubleSafe(s), 0.0, 59.9999));
        notifyLatChanged();
    }

    // --- Latitude DD (decimal signe editable) ---
    @Bindable
    public String getLatitudeEditString() {
        return String.format(Locale.US, "%.6f", coord.getLatitude());
    }

    public void setLatitudeEditString(String s) {
        coord.setLatitude(clampDouble(parseDoubleSafe(s), -90.0, 90.0));
        notifyLatChanged();
    }

    @Bindable
    public String getLatHemi() {
        if( coord.isLatNorth() ) {
            return "N";
        } else {
            return "S";
        }
    }

    public void toggleLatHemi() {
        coord.toggleLatHemi();
        notifyPropertyChanged(BR.latHemi);
        notifyLatChanged();
    }

    // --- Longitude DMS ---
    @Bindable
    public String getLonDegString() {
        return String.valueOf(coord.getLonDeg());
    }

    public void setLonDegString(String s) {
        coord.setLonDeg(clampInt(parseIntSafe(s), 0, 180));
        notifyLonChanged();
    }

    @Bindable
    public String getLonMinString() {
        return String.valueOf(coord.getLonMin());
    }

    public void setLonMinString(String s) {
        coord.setLonMin(clampInt(parseIntSafe(s), 0, 59));
        notifyLonChanged();
    }

    @Bindable
    public String getLonSecString() {
        return String.format(Locale.US, "%.2f", coord.getLonSec());
    }

    public void setLonSecString(String s) {
        coord.setLonSec(clampDouble(parseDoubleSafe(s), 0.0, 59.99));
        notifyLonChanged();
    }

    // --- Longitude DM (minutes decimales) ---
    @Bindable
    public String getLonMinDecString() {
        return String.format(Locale.US, "%.4f", coord.getLonMinDec());
    }

    public void setLonMinDecString(String s) {
        coord.setLonMinDec(clampDouble(parseDoubleSafe(s), 0.0, 59.9999));
        notifyLonChanged();
    }

    // --- Longitude DD (decimal signe editable) ---
    @Bindable
    public String getLongitudeEditString() {
        return String.format(Locale.US, "%.6f", coord.getLongitude());
    }

    public void setLongitudeEditString(String s) {
        coord.setLongitude(clampDouble(parseDoubleSafe(s), -180.0, 180.0));
        notifyLonChanged();
    }

    @Bindable
    public String getLonHemi() {
        if( coord.isLonEast() ) {
            return "E";
        } else {
            return "W";
        }
    }

    public void toggleLonHemi() {
        coord.toggleLonHemi();
        notifyPropertyChanged(BR.lonHemi);
        notifyLonChanged();
    }

    // #17 : bouton "position actuelle" -> remplace lat/lon par la position courante (meme source que
    // l'ajout de mark). coord.setLatitude/setLongitude re-synchronisent DMS/DM ; on rafraichit ensuite
    // TOUT l'affichage (c'est un appui bouton, pas une frappe en cours).
    public void setToCurrentPosition() {
        GPSFix fix = _am.getCurrentDropFix();
        coord.setLatitude(fix.getLatitude());
        coord.setLongitude(fix.getLongitude());
        coord.setAltitude(fix.getAltitude());

        notifyPropertyChanged(BR.latitudeString);
        notifyPropertyChanged(BR.longitudeString);
        notifyPropertyChanged(BR.latDegString);
        notifyPropertyChanged(BR.latMinString);
        notifyPropertyChanged(BR.latSecString);
        notifyPropertyChanged(BR.latMinDecString);
        notifyPropertyChanged(BR.latitudeEditString);
        notifyPropertyChanged(BR.latHemi);
        notifyPropertyChanged(BR.lonDegString);
        notifyPropertyChanged(BR.lonMinString);
        notifyPropertyChanged(BR.lonSecString);
        notifyPropertyChanged(BR.lonMinDecString);
        notifyPropertyChanged(BR.longitudeEditString);
        notifyPropertyChanged(BR.lonHemi);
        notifyPropertyChanged(BR.altitudeEditString);
    }

    @Override
    @Bindable
    public String getTitle() {
        return title;
    }

    @Bindable
    public void setTitle(String title) {
        this.title = title;

        notifyPropertyChanged(BR.title);
    }

    @Bindable
    public int getBitmapId() {
        return this.sheetUsing * (this.ROWBITMAPCOUNT * this.COLBITMAPCOUNT)
                + this.rowUsing * this.COLBITMAPCOUNT + this.colUsing;
    }
    @Bindable
    public void setBitmapId(int bitmapId) {
        int perSheet = this.ROWBITMAPCOUNT * this.COLBITMAPCOUNT;
        this.sheetUsing = bitmapId / perSheet;
        int rem = bitmapId % perSheet;
        this.rowUsing = rem / this.COLBITMAPCOUNT;
        this.colUsing = rem % this.COLBITMAPCOUNT;

        notifyPropertyChanged(BR.bitmapId);
        notifyPropertyChanged(BR.bitmap);
    }

    // #65 : partage de la position AFFICHEE, et non de la mark enregistree : le popup ouvert par "+"
    // tient deja les valeurs saisies, donc partager sa position courante se fait "+", Share, puis
    // Back sans enregistrer. Passe par ViewsController, qui detient l'activite.
    public void share() {
        ViewsController vc = ViewsController.getInstance();

        if( vc != null ) {
            vc.ShareMark(this);
        }
    }

    // #65 : icone recue d'un partage. L'identifiant a plat (sheet*36 + row*6 + col) n'a de sens que
    // pour un jeu de planches identique ; chez un destinataire qui en a moins, ou apres l'ajout d'une
    // planche, il tombe hors liste. On retombe alors sur la premiere icone plutot que sur une case
    // vide.
    public void setBitmapIdFromShare(int bitmapId) {
        List<BitmapDrawable> textures = allBitmapsTexturesList;
        int total = (textures == null) ? 0 : textures.size();

        setBitmapId(((bitmapId >= 0) && (bitmapId < total)) ? bitmapId : 0);
    }

    @Bindable
    public boolean isShared() {
        return shared;
    }

    @Bindable
    public void setShared(boolean shared) {
        this.shared = shared;

        notifyPropertyChanged(BR.shared);
    }

    @Bindable
    public boolean isAlarmed() {
        return alarmed;
    }

    @Bindable
    public boolean isAlarming() {
        return alarming;
    }

    public void setAlarming(boolean alarming) {
        if( this.alarming == alarming )
            return;   // appele a chaque tour de la boucle de proximite : on ne notifie qu'au changement

        this.alarming = alarming;

        notifyPropertyChanged(BR.alarming);
    }

    @Bindable
    public void setAlarmed(boolean alarmed) {

        // #81 : rallumer l'alarme d'une marque la REVEILLE. Sans cela, une marque dont on avait
        // coupe le son, puis desarmee, puis rearmee, restait muette a cause d'un mute qui datait
        // d'avant : on rallume une alarme et il ne se passe rien, ce qui est incomprehensible.
        if( alarmed && !this.alarmed )
            setMuted(false);

        this.alarmed = alarmed;

        notifyPropertyChanged(BR.alarmed);
        notifyPropertyChanged(BR.arrowBitmap);
        notifyPropertyChanged(BR.arrowBadge);
    }

    // #7 : le titre d'une marque armee vire au rouge, partout ou il s'affiche -- liste de proximite,
    // page des marques, page de recherche, panneau haut-gauche. Un seul rouge pour les quatre.
    public static final int TITLE_COLOR_ALARMED = 0xFFD32F2F;

    // #7 : pose ou retire ce rouge sans jamais decider de la couleur NORMALE. Celle-ci varie d'un
    // ecran a l'autre (theme pour les listes, noir impose pour le panneau haut-gauche) : on la
    // releve sur la vue au premier passage et on la garde en tag, pour la rendre telle quelle quand
    // la marque n'est plus armee. Le recyclage des lignes de liste ne gene pas : toutes les lignes
    // d'une meme liste partent de la meme couleur.
    @BindingAdapter("alarmedTitle")
    public static void bindAlarmedTitle(TextView view, boolean alarmed) {

        Object base = view.getTag(R.id.tag_base_text_color);

        if( base == null ) {
            base = Integer.valueOf(view.getCurrentTextColor());
            view.setTag(R.id.tag_base_text_color, base);
        }

        view.setTextColor(alarmed ? TITLE_COLOR_ALARMED : ((Integer) base).intValue());
    }

    public Date getSharedTime() {
        return sharedTime;
    }

    public void setSharedTime(Date sharedTime) {
        this.sharedTime = sharedTime;
    }

    public int getRowBitmap() {
        return rowUsing;
    }

    public void setRowBitmap(int rowBitmap) {
        this.rowUsing = rowBitmap;

        notifyPropertyChanged(BR.bitmapId);
        notifyPropertyChanged(BR.bitmap);
    }

    public int getColBitmap() {
        return colUsing;
    }

    public void setColBitmap(int colBitmap) {
        this.colUsing = colBitmap;

        notifyPropertyChanged(BR.bitmapId);
        notifyPropertyChanged(BR.bitmap);
    }

    public int getSheetBitmap() {
        return sheetUsing;
    }

    public void setSheetBitmap(int sheetBitmap) {
        this.sheetUsing = sheetBitmap;

        notifyPropertyChanged(BR.bitmapId);
        notifyPropertyChanged(BR.bitmap);
    }



    @Bindable
    public BitmapDrawable getBitmap() {
        BitmapDrawable bd = null;

        int idx = this.sheetUsing * (this.ROWBITMAPCOUNT * this.COLBITMAPCOUNT)
                + this.rowUsing * this.COLBITMAPCOUNT + this.colUsing;

        // Easter egg: a selected mark whose hearts are ripe takes the pink bed instead of the
        // blue one. The COLOUR changes, and nothing else: a mark that is not selected has no bed
        // at all, here as everywhere.
        if( selected && (heartFill >= 1f) ) {
            bd = bedBitmap(idx, HEAL_BED_COLOUR, healBitmaps);

            if( bd != null )
                return bd;
        }

        // And the same in olive for a place whose weapon is ready to be thrown.
        if( selected && (itemFill >= 1f) ) {
            bd = bedBitmap(idx, ITEM_BED_COLOUR, itemBitmaps);

            if( bd != null )
                return bd;
        }

        if( selected ) {
            bd = allSelectedBitmapsTexturesList.get(idx);
        } else {
            bd = allBitmapsTexturesList.get(idx);
        }

        return bd;
    }

    // The icon on a bed of its own colour, built on first use and kept. Only a handful of the
    // hundred and eight ever grow anything, so the whole sheet is not worth laying out again --
    // and the two families are kept in two caches, since one icon could never be both.
    private BitmapDrawable bedBitmap(int idx, int colour,
                                     java.util.Map<Integer, BitmapDrawable> cache) {

        BitmapDrawable bd = cache.get(Integer.valueOf(idx));

        if( bd != null )
            return bd;

        if( (allBitmapsTextures == null) || (idx < 0) || (idx >= allBitmapsTextures.length) )
            return null;

        Bitmap b = allBitmapsTextures[idx];

        if( b == null )
            return null;

        Bitmap bed = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bed);
        c.drawColor(colour);
        c.drawBitmap(b, 0, 0, null);

        bd = new BitmapDrawable(_am.getContext().getResources(), bed);

        cache.put(Integer.valueOf(idx), bd);

        return bd;
    }

    private static final int HEAL_BED_COLOUR = Color.rgb(0xff, 0x4d, 0x7e);
    private static final int ITEM_BED_COLOUR = Color.rgb(0x6b, 0x8e, 0x23);

    private static final java.util.Map<Integer, BitmapDrawable> healBitmaps =
            new java.util.concurrent.ConcurrentHashMap<Integer, BitmapDrawable>();

    private static final java.util.Map<Integer, BitmapDrawable> itemBitmaps =
            new java.util.concurrent.ConcurrentHashMap<Integer, BitmapDrawable>();

    // Flèche du secteur courant (position courante -> mark), sur le modèle de getBitmap().
    // Flèche nue (liste principale, avant Edit).
    @Bindable
    public Drawable getArrowBitmap() {
        List<Drawable> list = alarmed ? arrowAlarmList : arrowDrawablesList;
        return (list == null) ? null : list.get(heading);
    }

    // Flèche dans un rond (liste de proximité) ; version inversée quand la mark est sélectionnée.
    @Bindable
    public Drawable getArrowBadge() {
        List<Drawable> list;

        if( alarmed )
            list = selected ? arrowBadgeAlarmSelectedList : arrowBadgeAlarmList;
        else if( heartFill >= 1f )
            list = selected ? arrowBadgeHealSelectedList : arrowBadgeHealList;
        else if( itemFill >= 1f )
            list = selected ? arrowBadgeItemSelectedList : arrowBadgeItemList;
        else
            list = selected ? arrowBadgeSelectedList : arrowBadgeList;

        return (list == null) ? null : list.get(heading);
    }

    // Current 45 degree sector (0 = north), for whoever has to point the same way as the arrow
    // without being the arrow -- the heart of the life gauge, for one.
    public int getHeading() {
        return heading;
    }

    // #54 : ressource du badge rond bleu / flèche blanche pour un secteur de 45° (0 = nord), pour
    // les écrans qui ont besoin de la même flèche sans avoir de mark sous la main.
    public static int arrowBadgeRes(int sector) {
        return ARROW_BADGE_RES[(((sector % 8) + 8) % 8)];
    }

    // Badge toujours en rond bleu / flèche blanche (panneau de la marque sélectionnée, fond clair).
    public Drawable getArrowBadgeNormal() {
        List<Drawable> list = alarmed ? arrowBadgeAlarmList : arrowBadgeList;
        return (list == null) ? null : list.get(heading);
    }

    // Appelé depuis la boucle qui met à jour la distance. Snap au secteur de 45° et ne notifie que
    // si le secteur change (évite de rafraîchir le binding à chaque tick).
    public void setHeadingFromBearing(double bearingDeg) {
        int idx = ((int) Math.round(bearingDeg / 45.0)) % 8;
        if (idx < 0)
            idx += 8;

        if (idx != this.heading) {
            this.heading = idx;

            notifyPropertyChanged(BR.arrowBitmap);
            notifyPropertyChanged(BR.arrowBadge);
        }
    }

    @Bindable
    public String getDistanceString() {
        return getDistanceString(3);
    }

    public String getDistanceString(int decimals) {
        return formatDistance(this.distance, decimals);
    }

    // Single distance formatter (Locale.US). Unit follows the current speed-unit mode; `decimals` =
    // digits after the point for the big unit (small unit stays integer).
    //
    //   mode  | small                      | big
    //   ------+----------------------------+-----
    //   km/h  | m   (< 1 km)               | km
    //   mph   | small-unit parameter:      | mi
    //         |   m  (< 1 mi = 1609 m)     |
    //         |   yd (< 0.5 mi = 880 yd)   |
    //         |   ft (< 1000 ft = ~305 m)  |
    //   kn    | NM, >=2 decimals (< 1 NM)  | NM    (always NM -> matches the chart latitude scale)
    //
    // #66 : `decimals` est un MAXIMUM, borne ensuite par l'ordre de grandeur (fitDecimals) et
    // debarrasse de sa rangee de zeros quand le nombre est entier (trimIfWhole).
    public static String formatDistance(double km, int decimals) {
        switch (AppManager.getInstance().getSpeedUnit()) {
            case 1: { // miles / small unit chosen in the Global parameters
                double miles = km * 0.6213712;
                switch (AppManager.getInstance().getSmallUnit()) {
                    case AppManager.SMALLUNIT_M:
                        if (miles < 1.0)
                            return String.format(Locale.US, "%.0f m", km * 1000.0);
                        break;
                    case AppManager.SMALLUNIT_YD:
                        if (miles < 0.5)
                            return String.format(Locale.US, "%.0f yd", km * 1093.613298);
                        break;
                    default: {
                        double feet = km * 3280.839895;
                        if (feet < 1000.0)
                            return String.format(Locale.US, "%.0f ft", feet);
                        break;
                    }
                }
                return number(miles, decimals) + " mi";
            }
            case 2: { // nautical miles, always (matches the chart latitude scale). >=2 decimals under
                      // 1 NM so close ranges stay readable (0.35 NM instead of 0.0 NM).
                double nm = km * 0.5399568;
                int d = (nm < 1.0) ? Math.max(decimals, 2) : decimals;
                return number(nm, d) + " NM";
            }
            default: { // kilometres / metres
                if (km < 1.0)
                    return String.format(Locale.US, "%.0f m", km * 1000.0);
                return number(km, decimals) + " km";
            }
        }
    }

    // #66 : partie numerique d'une distance, decimales bornees par l'ordre de grandeur puis rangee
    // de zeros retiree si le nombre est entier.
    private static String number(double value, int decimals) {
        return trimIfWhole(value, fitDecimals(value, decimals));
    }

    // La partie entiere prend la place des decimales quand la distance grandit, et le texte deborde
    // des colonnes etroites (la ligne de distance de la liste de proximite fait la largeur de
    // l'icone, 48 dp). Le nombre de decimales est donc borne par l'ordre de grandeur de la valeur
    // AFFICHEE, jamais des km d'entree : sinon la bascule tomberait au mauvais moment en miles ou en
    // nautiques (15 km ne font que 9.3 mi, donc encore un seul chiffre).
    //   un chiffre        -> les decimales demandees   9.876 km
    //   deux ou trois     -> une seule                 123.5 km
    //   mille et au-dela  -> aucune                    1235 km
    private static int fitDecimals(double value, int decimals) {
        // Arrondi a la precision demandee AVANT de compter les chiffres : 9.9996 s'ecrit 10.000 et
        // compte donc pour deux chiffres.
        double scale = Math.pow(10.0, decimals);
        double v = Math.abs(Math.round(value * scale) / scale);

        if( v < 10.0 ) {
            return decimals;
        }
        if( v < 1000.0 ) {
            return Math.min(decimals, 1);
        }

        return 0;
    }

    // Un nombre entier n'a pas besoin de sa rangee de zeros, qui coute deux caracteres de large :
    // 45.000 -> 45. Les decimales d'un nombre non entier sont en revanche conservees telles quelles,
    // zeros compris : en kilometres elles se lisent comme des metres, 9.100 km valant 9 km et 100 m.
    private static String trimIfWhole(double value, int decimals) {
        String s = String.format(Locale.US, "%." + decimals + "f", value);

        int dot = s.indexOf('.');
        if( dot < 0 ) {
            return s;
        }

        for( int i = dot + 1; i < s.length(); i++ ) {
            if( s.charAt(i) != '0' ) {
                return s;
            }
        }

        return s.substring(0, dot);
    }

    public double getDistance() {
        return distance;
    }

    // currentAltitude null (unknown), no altitude on the mark or showFromM 0 (never): nothing shown.
    public void setElevationFrom(Double currentAltitude, double distanceKm, int showFromM) {
        Double markAltitude = coord.altitude;
        boolean shown = elevationShown;
        String angle = elevationAngleString;
        String delta = elevationDeltaString;

        if( markAltitude == null || currentAltitude == null || showFromM <= 0 ) {
            shown = false;
        } else {
            double dh = markAltitude - currentAltitude;
            double absDh = Math.abs(dh);

            if( absDh >= showFromM ) {
                shown = true;
            } else if( absDh < showFromM - ELEVATION_HYSTERESIS_M ) {
                shown = false;
            }

            if( shown ) {
                // Slope seen from here: angle above (or below) the horizontal, whatever the distance.
                long degrees = Math.round(Math.toDegrees(Math.atan2(absDh, distanceKm * 1000.0)));
                angle = String.format(Locale.US, "%s %d°", (dh > 0) ? "▲" : "▼", degrees);
                delta = String.format(Locale.US, "%+d m", Math.round(dh));
                elevationUp = (dh > 0);
                elevationDegrees = degrees;
                elevationAbsMeters = Math.round(absDh);
            }
        }

        if( shown != elevationShown ) {
            elevationShown = shown;
            notifyPropertyChanged(BR.elevationShown);
        }
        if( !angle.equals(elevationAngleString) ) {
            elevationAngleString = angle;
            notifyPropertyChanged(BR.elevationAngleString);
        }
        if( !delta.equals(elevationDeltaString) ) {
            elevationDeltaString = delta;
            notifyPropertyChanged(BR.elevationDeltaString);
        }
    }

    @Bindable
    public boolean isElevationShown() {
        return elevationShown;
    }

    @Bindable
    public String getElevationAngleString() {
        return elevationAngleString;
    }

    @Bindable
    public String getElevationDeltaString() {
        return elevationDeltaString;
    }

    public boolean isElevationUp() {
        return elevationUp;
    }

    public long getElevationDegrees() {
        return elevationDegrees;
    }

    public long getElevationAbsMeters() {
        return elevationAbsMeters;
    }

    public void setDistance(double distance) {
        this.distance = distance;

        notifyPropertyChanged(BR.distanceString);
    }

    @Bindable
    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        notifyPropertyChanged(BR.selected);
        notifyPropertyChanged(BR.bitmap);
        notifyPropertyChanged(BR.arrowBitmap);
        notifyPropertyChanged(BR.arrowBadge);
    }

    /**
     * #7 : l'ordre des pages Marques et Recherche, en un seul endroit pour les deux -- elles trient
     * des objets de types differents mais selon exactement la meme regle.
     *
     * Trois paliers : les marques qui SIGNALENT, puis les selectionnees, puis les autres. A
     * l'interieur du palier d'alarme c'est la DISTANCE qui commande, la selection ne departageant
     * qu'a egalite : entre deux marques qui sonnent, celle qui est sur vous compte plus que celle que
     * vous aviez cochee. Dans les deux autres paliers la selection a deja fait son office en formant
     * le palier, et la distance suffit. Le nom departage les ex aequo.
     */
    public static int compareForList(boolean alarming1, boolean selected1, double distance1, String title1,
                                     boolean alarming2, boolean selected2, double distance2, String title2) {

        if( alarming1 != alarming2 )
            return alarming1 ? -1 : 1;

        if( !alarming1 && (selected1 != selected2) )
            return selected1 ? -1 : 1;

        if( distance1 < distance2 )
            return -1;

        if( distance1 > distance2 )
            return 1;

        if( alarming1 && (selected1 != selected2) )
            return selected1 ? -1 : 1;   // a distance egale seulement

        String t1 = (title1 != null) ? title1 : "";
        String t2 = (title2 != null) ? title2 : "";

        return t1.compareTo(t2);
    }

    // #97 : the order the map paints the marks in, the last one painted ending up on top. North
    // first, so that a mark lower on the screen passes in front of the one above it, then west
    // first at equal height, so that two marks on the same line keep a stable order. Alarming
    // marks come after all the others. The main selected mark is not sorted here: Chunk.draw
    // paints it last, so that changing it shows at once.
    public static int compareForDraw(boolean alarming1, double latitude1, double longitude1,
                                     boolean alarming2, double latitude2, double longitude2) {

        if( alarming1 != alarming2 )
            return alarming1 ? 1 : -1;

        if( latitude1 > latitude2 )
            return -1;

        if( latitude1 < latitude2 )
            return 1;

        return Double.compare(longitude1, longitude2);
    }

    public PersonalMark getCloneFrom() {
        return cloneFrom;
    }

    public void setCloneFromWithUpdate(PersonalMark cloneFrom) {
        this.cloneFrom = cloneFrom;

        this.id = this.cloneFrom.getId();

        this.coord.setLongitude(this.cloneFrom.getLongitude());
        this.coord.setLatitude(this.cloneFrom.getLatitude());
        this.coord.setAltitude(this.cloneFrom.getAltitude());
        this.title = this.cloneFrom.getTitle();
        this.shared = this.cloneFrom.isShared();
        this.sharedTime = this.cloneFrom.getSharedTime();
        this.alarmed = this.cloneFrom.isAlarmed();
        this.alarming = this.cloneFrom.isAlarming();

        this.sheetUsing = this.cloneFrom.getSheetBitmap();
        this.rowUsing = this.cloneFrom.getRowBitmap();
        this.colUsing = this.cloneFrom.getColBitmap();

        this.distance = this.cloneFrom.getDistance();
        this.heading = this.cloneFrom.heading;
        this.selected = this.cloneFrom.isSelected();

        // Easter egg: a ripe gauge colours the row. Copied here, on the proximity thread's own
        // once-a-second refresh, rather than notified from the game thread -- the bindings are
        // the main thread's business, and this is the channel that already carries the rest.
        this.heartFill = this.cloneFrom.heartFill;
        this.itemFill = this.cloneFrom.itemFill;

        notifyAllChanged();
    }

    public void notifyAllChanged() {

        notifyPropertyChanged(BR.longitude);
        notifyPropertyChanged(BR.latitude);
        notifyPropertyChanged(BR.title);
        notifyPropertyChanged(BR.shared);
        notifyPropertyChanged(BR.alarmed);
        notifyPropertyChanged(BR.alarming);
        notifyPropertyChanged(BR.distanceString);
        notifyPropertyChanged(BR.selected);
        notifyPropertyChanged(BR.bitmapId);
        notifyPropertyChanged(BR.bitmap);
        notifyPropertyChanged(BR.arrowBitmap);
        notifyPropertyChanged(BR.arrowBadge);
    }

    /** Is this mark a pole of the hidden game? Its icon says so, and nothing else does. */
    public boolean isGamePole() {
        return gamePole();
    }

    // Asked from inside, where the public door is not ours to use.
    private boolean gamePole() {
        return GameLevels.isPoleIcon(flatBitmapIndex());
    }

    /** Does this mark grow hearts? Its icon says so, and nothing else does. */
    public boolean isGameHeart() {
        return GameLevels.heartsOf(flatBitmapIndex()) > 0;
    }

    /** How many it grows, or zero. */
    public int getHeartCount() {
        return GameLevels.heartsOf(flatBitmapIndex());
    }

    public float getHeartFill() {
        return heartFill;
    }

    /** How full its gauge is, from nothing to one. Set by the orchestrator, drawn here. */
    public void setHeartFill(float heartFill) {
        this.heartFill = heartFill;
    }

    /** Does this mark grow a weapon? Its icon says so, and nothing else does. */
    public boolean isGameItem() {
        return GameLevels.itemOf(flatBitmapIndex()) != null;
    }

    /** Which one, or null. */
    public GameLevels.Item getItemKind() {
        return GameLevels.itemOf(flatBitmapIndex());
    }

    public float getItemFill() {
        return itemFill;
    }

    /** The same gauge, in olive. Set by the orchestrator, drawn here. */
    public void setItemFill(float itemFill) {
        this.itemFill = itemFill;
    }

    public int getPoleValue() {
        return poleValue;
    }

    /** What it is worth, as the orchestrator counts it down. Zero takes the figure off the map. */
    public void setPoleValue(int poleValue) {

        if( this.poleValue == poleValue )
            return;

        this.poleValue = poleValue;

        // Written here and not in draw(): the number changes a few times a second, the drawing
        // runs twenty-five.
        this.poleText = (poleValue > 0) ? (poleValue + " pts") : null;
    }

    public long getPoleDueNs() {
        return poleDueNs;
    }

    /** The instant it stops being worth more than the reference: its whole countdown, in one. */
    public void setPoleDueNs(long poleDueNs) {
        this.poleDueNs = poleDueNs;
    }

    public long getPoleDeadUntilNs() {
        return poleDeadUntilNs;
    }

    public void setPoleDeadUntilNs(long poleDeadUntilNs) {
        this.poleDeadUntilNs = poleDeadUntilNs;
    }

    public boolean hasPoleGoneAway() {
        return poleWentAway;
    }

    public void setPoleGoneAway(boolean poleWentAway) {
        this.poleWentAway = poleWentAway;
    }

    public int getPoleScored() {
        return poleScored;
    }

    public void setPoleScored(int poleScored) {
        this.poleScored = poleScored;
    }

    /** Area pixels per metre, measured in update() for whoever needs metres -- the collider. */
    @Override
    protected double getPixelsPerMeter() {
        return pixelsPerMeter;
    }

    /**
     * A pole stands ON its point.
     *
     * Its circle is two metres across, and the icon above it is three or four: centred on the
     * middle of the picture, as every other mark's is, the circle would sit a good two metres
     * north of the thing the player is walking at. So a pole moves it down onto the little ring
     * at the foot of the icon, which is where the mark actually is.
     */
    @Override
    protected double getColliderOffsetY() {

        if( !gamePole() )
            return super.getColliderOffsetY();

        return (double) height / 2;
    }

    // Index a plat de l'icone d'une mark : sheet*36 + row*6 + col.
    private int flatBitmapIndex() {
        return this.sheetUsing * (ROWBITMAPCOUNT * COLBITMAPCOUNT)
                + this.rowUsing * COLBITMAPCOUNT + this.colUsing;
    }

    // Une mark CREEE demarre sur la derniere icone sauvegardee de la session. Tant que rien n'a ete
    // sauvegarde (lastUsedFlat < 0) elle garde l'icone par defaut, planche 0 ligne 0 colonne 0.
    private void applyLastUsedBitmap() {
        if( lastUsedFlat < 0 )
            return;

        int perSheet = ROWBITMAPCOUNT * COLBITMAPCOUNT;
        this.sheetUsing = lastUsedFlat / perSheet;
        int rem = lastUsedFlat % perSheet;
        this.rowUsing = rem / COLBITMAPCOUNT;
        this.colUsing = rem % COLBITMAPCOUNT;
    }

    // Enumere les planches pm_*.png de R.drawable, triees par nom (pm_001, pm_002, ...).
    private static int[] sheetResIds(Context context) {
        java.util.List<String> names = new java.util.ArrayList<String>();
        for (java.lang.reflect.Field f : R.drawable.class.getFields()) {
            if (f.getName().matches("pm_\\d+"))
                names.add(f.getName());
        }
        java.util.Collections.sort(names);
        int[] ids = new int[names.size()];
        for (int i = 0; i < names.size(); i++)
            ids[i] = context.getResources().getIdentifier(names.get(i), "drawable", context.getPackageName());
        return ids;
    }

    // No lock in here: data binding only calls it on the main thread and the sheets never change
    // once loaded. The marks come as a snapshot from getMarksEnumerable(), called while holding
    // nothing (it used to be called inside syncObject: deadlock, see the comment on the statics).
    @Bindable
    public ArrayAdapter getMarksAdapter() {

        // Reference STABLE : renvoyer un NOUVEL adapter a chaque appel fait boucler le
        // data-binding (re-set adapter -> re-layout -> re-bind...). On garde donc le meme objet
        // et on remplace son contenu, recalcule a chaque ouverture de l'editeur.
        List<BitmapDrawable> textures = allBitmapsTexturesList;
        if (textures == null)
            return marksAdapter;

        int total = textures.size();

        // usage_count par index a plat, calcule EN MEMOIRE depuis les marks chargees (aucune base).
        final int[] usage = new int[total];
        java.util.List<PersonalMark> marks = _am.getMarksEnumerable();
        if (marks != null) {
            for (PersonalMark m : marks) {
                int fi = m.getSheetBitmap() * (ROWBITMAPCOUNT * COLBITMAPCOUNT)
                        + m.getRowBitmap() * COLBITMAPCOUNT + m.getColBitmap();
                if (fi >= 0 && fi < total) usage[fi]++;
            }
        }

        // Ordre du roller : la derniere icone sauvegardee de la session d'abord, puis usage
        // decroissant, puis index a plat (= sheet, row, col) croissant.
        final int first = lastUsedFlat;
        Integer[] order = new Integer[total];
        for (int i = 0; i < total; i++) order[i] = i;
        java.util.Arrays.sort(order, new java.util.Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) {
                if (a == first) return -1;
                if (b == first) return 1;
                if (usage[a] != usage[b]) return usage[b] - usage[a];
                return a - b;
            }
        });

        int[] flat = new int[total];
        for (int p = 0; p < total; p++)
            flat[p] = order[p];

        // Ordre INCHANGE : on rend l'adaptateur tel quel, sans toucher a son contenu.
        //
        // Le remplacer quand meme (clear + addAll + notifyDataSetChanged) coute cher et, surtout,
        // le clear() vide l'adaptateur SOUS le Spinner qui l'affiche : celui-ci remet alors sa
        // selection a zero, la liaison bidirectionnelle de selectedItemPosition renvoie ce zero
        // dans setBitmapPosition(), qui notifie bitmap et bitmapPosition, ce qui relance le
        // binding, donc un nouvel appel ici, donc un nouveau clear... D'ou le clignotement de
        // l'icone a l'ouverture du popup, et surtout le risque que la mark reparte en base avec
        // l'icone 0 du roller au lieu de la sienne.
        //
        // Le contenu ne change en pratique qu'entre deux ouvertures (lastUsedFlat et les
        // compteurs d'usage ont bouge), jamais pendant que le popup est affiche.
        ArrayAdapter adapter = marksAdapter;
        if ((adapter != null) && java.util.Arrays.equals(rollerFlatByPosition, flat)) {
            return adapter;
        }

        rollerFlatByPosition = flat;
        java.util.List<BitmapDrawable> sorted = new ArrayList<BitmapDrawable>(total);
        for (int p = 0; p < total; p++) {
            sorted.add(textures.get(order[p]));
        }

        if (adapter == null) {
            adapter = new MyListAdapter<BitmapDrawable>(_am.getContext(), R.layout.layout_bitmap, BR.bitmap, sorted);
            marksAdapter = adapter;
        } else {
            // Meme adapter, contenu reordonne : un seul notifyDataSetChanged a la fin.
            adapter.setNotifyOnChange(false);
            adapter.clear();
            adapter.addAll(sorted);
            adapter.notifyDataSetChanged();
        }

        return adapter;
    }

    // Position dans le roller (trie par usage) <-> (sheet,row,col) de la mark, via rollerFlatByPosition.
    @Bindable
    public int getBitmapPosition() {
        int[] map = rollerFlatByPosition;
        if (map == null) return 0;
        int fi = this.sheetUsing * (ROWBITMAPCOUNT * COLBITMAPCOUNT) + this.rowUsing * COLBITMAPCOUNT + this.colUsing;
        for (int p = 0; p < map.length; p++)
            if (map[p] == fi) return p;
        return 0;
    }

    @Bindable
    public void setBitmapPosition(int pos) {
        int[] map = rollerFlatByPosition;
        if (map == null || pos < 0 || pos >= map.length) return;
        int fi = map[pos];
        int perSheet = ROWBITMAPCOUNT * COLBITMAPCOUNT;
        this.sheetUsing = fi / perSheet;
        int rem = fi % perSheet;
        this.rowUsing = rem / COLBITMAPCOUNT;
        this.colUsing = rem % COLBITMAPCOUNT;

        notifyPropertyChanged(BR.bitmapPosition);
        notifyPropertyChanged(BR.bitmap);
    }

    // Decodes the icon sheets and the arrows ONCE, for every mark (static cache). Called by
    // AppManager.getInstance(context) on the main thread at startup, before any mark is built, and
    // never again once loaded.
    //
    // No lock and no AppManager call: everything is built into locals, then each static is assigned
    // a finished object, bitmapsTextures LAST since it is the "already loaded" flag. The context is
    // passed in rather than asked to AppManager, whose getter takes its read lock.
    public static void loadBitmaps(Context context) {

        if ((bitmapsTextures != null) || (context == null))
            return;

        // Multi-planches : on enumere tous les pm_*.png (tries par nom) et on concatene
        // leurs 36 cases dans le cache static, indexees a plat par sheet*36 + row*6 + col.
        int[] sheets = sheetResIds(context);
        int count = sheets.length;
        int perSheet = ROWBITMAPCOUNT * COLBITMAPCOUNT;   // 36

        Bitmap firstSheet = null;
        Bitmap[] textures = new Bitmap[count * perSheet];
        List<BitmapDrawable> texturesList = new ArrayList<BitmapDrawable>();
        List<BitmapDrawable> selectedTexturesList = new ArrayList<BitmapDrawable>();

        for( int s = 0; s < count; s++ ) {
            Bitmap sheet = BitmapFactory.decodeResource(context.getResources(), sheets[s]);
            if( s == 0 )
                firstSheet = sheet;
            int cw = sheet.getWidth() / COLBITMAPCOUNT;
            int ch = sheet.getHeight() / ROWBITMAPCOUNT;
            for( int row = 0; row < ROWBITMAPCOUNT; row++ ) {
                for( int col = 0; col < COLBITMAPCOUNT; col++ ) {
                    Bitmap b = Bitmap.createBitmap(sheet, col * cw, row * ch, cw, ch);
                    textures[s * perSheet + row * COLBITMAPCOUNT + col] = b;
                    texturesList.add(new BitmapDrawable(context.getResources(), b));

                    Bitmap pathBitMap = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(pathBitMap);
                    c.drawColor(Color.parseColor("#2196f3") );
                    c.drawBitmap(b, 0, 0, null);
                    selectedTexturesList.add(new BitmapDrawable(context.getResources(), pathBitMap));
                }
            }
        }

        List<Drawable> arrows = new ArrayList<Drawable>();
        List<Drawable> badges = new ArrayList<Drawable>();
        List<Drawable> badgesSelected = new ArrayList<Drawable>();
        List<Drawable> badgesHeal = new ArrayList<Drawable>();
        List<Drawable> badgesHealSelected = new ArrayList<Drawable>();
        List<Drawable> badgesItem = new ArrayList<Drawable>();
        List<Drawable> badgesItemSelected = new ArrayList<Drawable>();
        List<Drawable> alarms = new ArrayList<Drawable>();
        List<Drawable> badgesAlarm = new ArrayList<Drawable>();
        List<Drawable> badgesAlarmSelected = new ArrayList<Drawable>();
        for (int i = 0; i < ARROW_RES.length; i++) {
            arrows.add(ContextCompat.getDrawable(context, ARROW_RES[i]));
            badges.add(ContextCompat.getDrawable(context, ARROW_BADGE_RES[i]));
            badgesSelected.add(ContextCompat.getDrawable(context, ARROW_BADGE_SEL_RES[i]));
            badgesHeal.add(ContextCompat.getDrawable(context, ARROW_BADGE_HEAL_RES[i]));
            badgesHealSelected.add(ContextCompat.getDrawable(context, ARROW_BADGE_HEAL_SEL_RES[i]));
            badgesItem.add(ContextCompat.getDrawable(context, ARROW_BADGE_ITEM_RES[i]));
            badgesItemSelected.add(ContextCompat.getDrawable(context, ARROW_BADGE_ITEM_SEL_RES[i]));
            alarms.add(ContextCompat.getDrawable(context, ARROW_ALARM_RES[i]));
            badgesAlarm.add(ContextCompat.getDrawable(context, ARROW_BADGE_ALARM_RES[i]));
            badgesAlarmSelected.add(ContextCompat.getDrawable(context, ARROW_BADGE_ALARM_SEL_RES[i]));
        }

        // marksAdapter construit dans getMarksAdapter() : il est trie par usage a chaque ouverture.

        sheetCount = count;
        allBitmapsTextures = textures;
        allBitmapsTexturesList = texturesList;
        allSelectedBitmapsTexturesList = selectedTexturesList;
        arrowDrawablesList = arrows;
        arrowBadgeList = badges;
        arrowBadgeSelectedList = badgesSelected;
        arrowBadgeHealList = badgesHeal;
        arrowBadgeHealSelectedList = badgesHealSelected;
        arrowBadgeItemList = badgesItem;
        arrowBadgeItemSelectedList = badgesItemSelected;
        arrowAlarmList = alarms;
        arrowBadgeAlarmList = badgesAlarm;
        arrowBadgeAlarmSelectedList = badgesAlarmSelected;
        bitmapsTextures = firstSheet;   // last: the "already loaded" flag
    }

    public void switchSelected() {
        this.selected = !this.selected;

        if( this.cloneFrom != null ) {
            this.cloneFrom.selected = this.selected;
        }

        notifyPropertyChanged(BR.selected);
        notifyPropertyChanged(BR.bitmap);
        notifyPropertyChanged(BR.arrowBitmap);
        notifyPropertyChanged(BR.arrowBadge);

        // Drive the top-left mark panel: a just-selected mark becomes the shown one; on deselection
        // the panel re-resolves to the nearest selected mark.
        AppManager.getInstance().onMarkSelectionToggled(this.cloneFrom != null ? this.cloneFrom : this);
    }


    public void edit() {
        // On edite la mark CANONIQUE : si cette instance est un clone (cas des marks de proximite),
        // on part de son cloneFrom (la vraie mark de _marks) pour que Save la mette a jour EN PLACE,
        // sans creer de doublon dans la liste.
        PersonalMark target;
        if( this.cloneFrom != null ) {
            target = this.cloneFrom;
        } else {
            target = this;
        }

        ViewsController vc = ViewsController.getInstance();
        vc.ShowPersonalMark( new PersonalMark( target ) );
    }

    // #17 : appui long sur une mark de la liste de droite -> ouvre l'editeur. onLongClick exige un
    // boolean ; on renvoie true pour consommer l'evenement (le clic court switchSelected ne part pas).
    public boolean editLongPress() {
        edit();
        return true;
    }

    public boolean delete() {
        boolean bDeleteOK = false;

        GBLog.d( TAG, "Deleted");


        if( _am.isInitializedDB() ) {
            AppDB appDB = _am.getAppDB();

            try {
                PersonalMarkDB miDB = new PersonalMarkDB(appDB);
                long retval = miDB.remove(this.id);

                if( retval > 0 ) {
                    bDeleteOK = true;
                }

            } catch ( Exception ex) {
                GBLog.e( TAG, String.format("E:%s", ex.toString()));
            }
        }

        if( bDeleteOK ) {
            if( this.cloneFrom != null ) {
                _am.removeMarkInList(this.cloneFrom);
            } else {
                _am.removeMarkInList(this);
            }
        }


        return bDeleteOK;
    }

    public boolean save() {
        boolean bSaveOK = false;

        GBLog.d( TAG, "Saved");

        // Cap strict pole / antimeridien : on borne le decimal au globe avant persistance.
        coord.clampToGlobe();


        if( _am.isInitializedDB() ) {
            AppDB appDB = _am.getAppDB();

            try {
                PersonalMarkDB miDB = new PersonalMarkDB(appDB);
                long key = miDB.replace(this);

                if( key > 0 ) {
                    this.id = key;
                    bSaveOK = true;
                }

            } catch ( Exception ex) {
                GBLog.e( TAG, String.format("E:%s", ex.toString()));
            }

        }

        if( bSaveOK ) {

            // Creation comme edition : cette icone passe en tete du roller a la prochaine ouverture
            // et sert de point de depart a la prochaine mark creee.
            lastUsedFlat = flatBitmapIndex();

            if( this.cloneFrom != null ) {

                this.cloneFrom.setId(this.id);

                this.cloneFrom.setLongitude(this.coord.longitude);
                this.cloneFrom.setLatitude(this.coord.latitude);
                this.cloneFrom.setAltitude(this.coord.altitude);
                this.cloneFrom.setTitle(this.title);
                this.cloneFrom.setShared(this.shared);
                this.cloneFrom.setSharedTime(this.sharedTime);
                this.cloneFrom.setAlarmed(this.alarmed);

                this.cloneFrom.setSheetBitmap(this.sheetUsing);
                this.cloneFrom.setRowBitmap(this.rowUsing);
                this.cloneFrom.setColBitmap(this.colUsing);

                this.cloneFrom.setDistance(this.distance);
                this.cloneFrom.setSelected(this.selected);

                _am.addMarkInList(this.cloneFrom);
            } else {
                _am.addMarkInList(this);
            }
        }


        return bSaveOK;
    }

    // setChunk + contextScale in one call: re-point onto `chunk` AND recompute x,y in its frame.
    // Used at the map swap (render thread) so a mark's position never lags a chunk behind the camera.
    // Cascades to the canonical mark for proximity clones; skips it if already on `chunk`.
    public void setChunkWithContextScale(Chunk chunk) {
        super.setChunk(chunk);
        super.contextScale();
        this.setImage(bitmapsTextures, ROWBITMAPCOUNT, COLBITMAPCOUNT);
        
        if( this.cloneFrom != null && this.cloneFrom.getChunk() != chunk)
            this.cloneFrom.setChunkWithContextScale(chunk);
    }

    @Override
    public void setChunk(Chunk chunk) {
        super.setChunk(chunk);
        if( this.cloneFrom != null && this.cloneFrom.getChunk() != chunk)
            this.cloneFrom.setChunk(chunk);
    }

    @Override
    public void contextScale() {
        super.contextScale();
        this.setImage(bitmapsTextures, ROWBITMAPCOUNT, COLBITMAPCOUNT);
    }

    @Override
    public void update() {
        super.update();

        // #7 : exactement le traitement d'un radar (MapMark.update) -- la mark entre dans la liste
        // des objets en alarme des qu'elle est armee ET dans les criteres, en sort sinon. Tout le
        // reste (son, bande peripherique #40, fleches de bord #60) en decoule sans code de plus.
        Chunk chunk = getChunk();
        GameSurface gameSurface = (chunk == null) ? null : chunk.getGameSurface();

        if( gameSurface == null )
            return;

        // For the collider, which is given in metres on a pole. Measured here, once, because it
        // is read once per object per frame and it costs a projection through the chunk.
        this.pixelsPerMeter = gameSurface.pixelsPerMeter();

        if( alarmed && _am.markShouldAlarm(_am.getKmFrom(getLongitude(), getLatitude(), coord.altitude)) )
            gameSurface.addAlarmObject(this);
        else
            gameSurface.removeAlarmObject(this);
    }

    @Override
    public void draw(Canvas canvas, int layer)  {
        // #7 : le rond rouge pulsant d'une mark armee, sous l'icone comme pour un radar. Il est la
        // des que la mark est armee, pas seulement quand l'alarme sonne : il dit "cette mark
        // surveille", au meme titre que le rond d'un radar signale un radar. Le rayon est anime par
        // MapPoint.update (onde triangulaire, demi-cycle 700 ms), commune a toutes les alarmes.
        if( alarmed )
            draw_warning(canvas, layer);

        draw(canvas, layer, this.allBitmapsTextures);
        draw_title(canvas, layer);

        draw_poleValue(canvas, layer);
        draw_heartGauge(canvas, layer);
        draw_itemGauge(canvas, layer);

        if( selected ) {

        }

    }

    /**
     * What the pole is worth, in figures, above its icon.
     *
     * A price falling where the thing stands, so that choosing which one to run at is a glance
     * and not a calculation. Read and not worked out: the number, and the very text of it, are
     * settled by the time this runs.
     */
    /**
     * The gauge of a place that grows hearts, above its icon.
     *
     * Read and not worked out: the fraction is settled by the time this runs. Full, it is drawn
     * with a bright edge all round -- the one thing that has to be legible from across a map is
     * the difference between "nearly" and "now".
     */
    private void draw_heartGauge(Canvas canvas, int layer) {

        if( (layer != 1) || (heartFill <= 0) )
            return;

        draw_gauge(canvas, heartFill, GAUGE_PINK);
    }

    /**
     * The gauge of a place that grows a weapon. The same bar, in olive.
     *
     * A mark is one or the other and never both, so the two can share the drawing without ever
     * sharing a frame: what changes here is the colour, and nothing in the rule above it.
     */
    private void draw_itemGauge(Canvas canvas, int layer) {

        if( (layer != 1) || (itemFill <= 0) )
            return;

        draw_gauge(canvas, itemFill, GAUGE_OLIVE);
    }

    private void draw_gauge(Canvas canvas, float fill, Paint paint) {

        if( this.chunk == null )
            return;

        GameSurface gameSurface = this.chunk.getGameSurface();

        if( gameSurface == null )
            return;

        float density = gameSurface.getResources().getDisplayMetrics().density;

        float w = width * GAUGE_WIDTH_PART;
        float h = GAUGE_HEIGHT_DP * density;

        float cx = gameSurface.getCanvasX(this.x);
        float cy = gameSurface.getCanvasY(this.y - height + (GAUGE_DROP_DP * density));

        float x0 = cx - (w / 2f);
        float y0 = cy - h;

        float round = h / 2f;

        GAUGE_RECT.set(x0, y0, x0 + w, y0 + h);

        canvas.drawRoundRect(GAUGE_RECT, round, round, GAUGE_BED);

        float full = w * ((fill > 1f) ? 1f : fill);

        // CLIPPED to the bed rather than drawn as a rounded rectangle of its own. Drawn on its
        // own, a filling narrower than the bed is tall has its corners squashed into a lens by
        // the drawing itself, and the first seconds of growth read as a straight little bar
        // sitting inside a rounded tray. Clipped, the filling takes the shape it is filling, and
        // the very first pixels are the round of the left cap.
        if( full > 0 ) {

            GAUGE_PATH.reset();
            GAUGE_PATH.addRoundRect(GAUGE_RECT, round, round, Path.Direction.CW);

            canvas.save();
            canvas.clipPath(GAUGE_PATH);
            canvas.drawRect(x0, y0, x0 + full, y0 + h, paint);
            canvas.restore();
        }

        if( fill >= 1f ) {
            GAUGE_EDGE.setStrokeWidth(density * 1.5f);
            canvas.drawRoundRect(GAUGE_RECT, round, round, GAUGE_EDGE);
        }
    }

    private void draw_poleValue(Canvas canvas, int layer) {

        if( (layer != 1) || (poleText == null) || (this.chunk == null) )
            return;

        GameSurface gameSurface = this.chunk.getGameSurface();

        if( gameSurface == null )
            return;

        float density = gameSurface.getResources().getDisplayMetrics().density;
        float size = POLE_TEXT_DP * density;

        POLE_TEXT.setTextSize(size);
        POLE_SHADOW.setTextSize(size);

        // Over the top of the icon, which itself hangs above the point: the title is written
        // under the icon, and the two have no business sharing a line.
        float cx = gameSurface.getCanvasX(this.x);
        float cy = gameSurface.getCanvasY(this.y - height + (POLE_DROP_DP * density));

        canvas.drawText(poleText, cx + density, cy + density, POLE_SHADOW);
        canvas.drawText(poleText, cx, cy, POLE_TEXT);
    }


}
