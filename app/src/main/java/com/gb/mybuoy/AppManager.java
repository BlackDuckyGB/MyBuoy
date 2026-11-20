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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.RequiresApi;
import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import androidx.databinding.BindingAdapter;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.altitude.AltitudeConverter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.os.SystemClock;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.databinding.Observable;
import android.util.Log;
import android.util.TypedValue;
import android.hardware.GeomagneticField;

import com.gb.mybuoy.sqlite.AppDB;
import com.gb.mybuoy.tides.TidePlace;
import com.gb.mybuoy.sqlite.MapInfoDB;
import com.gb.mybuoy.sqlite.PersonalMarkDB;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

/*
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.installations.FirebaseInstallations;
import com.google.firebase.messaging.FirebaseMessaging;
 */


public class AppManager extends BaseObservable {

    private static final String TAG = "AppManager";

    public enum TypeZoom {
        ZOOM_20("20m", 0.02),
        ZOOM_50("50m", 0.05),
        ZOOM_100("100m", 0.1),
        ZOOM_300("300m", 0.3),
        ZOOM_500("500m", 0.5),
        ZOOM_1000("1km", 1.0),
        ZOOM_2000("2km", 2.0),
        ZOOM_5000("5km", 5.0);

        private String stringZoom;
        private double kmZoom;

        private TypeZoom(String toString, double km) {
            stringZoom = toString;
            kmZoom = km;
        }

        @Override
        public String toString() {
            return stringZoom;
        }

        public long tilesCount() {
            double r_earth = 6378.137; // Radius of earth in KM
            double surface_earth = 4 * Math.PI * r_earth * r_earth; // Radius of earth in KM
            long l_surface_earth = (long) surface_earth;
            if (surface_earth > l_surface_earth)
                l_surface_earth += 1;

            double nb_tiles = surface_earth / kmZoom;
            long l_nb_tiles = (long) nb_tiles;

            return l_nb_tiles;
        }

        public String tileFileName(double longitude, double latitude) {
            double r_earth = 6378.137; // Radius of earth in KM
            double circ_earth = Math.PI * r_earth * 2;

            double kmY = (1 * Chunk.latitudeGetKmFrom(latitude, 0) + circ_earth) % circ_earth;
            double kmX = (-1 * Chunk.longitudeGetKmFrom(longitude, latitude, 0) + circ_earth) % circ_earth;

            // 32x32 tiles by File
            final long tileFileCols = 32;
            final long tileFileRows = 32;

            // 64x64 tiles Files by Directory
            final long tileDirCols = 64;
            final long tileDirRows = 64;


            long tileCol = (long) ((kmX / kmZoom) % tileFileCols);
            long tileLig = (long) ((kmY / kmZoom) % tileFileRows);

            long tileColFile = (long) ((kmX / kmZoom) % (tileFileCols * tileDirCols));
            long tileLigFile = (long) ((kmY / kmZoom) % (tileFileRows * tileDirRows));
            long tileColDir = (long) ((kmX / kmZoom) / (tileFileCols * tileDirCols));
            long tileLigDir = (long) ((kmY / kmZoom) / (tileFileRows * tileDirRows));
            ;

            String name = String.format("%d_%d/%d_%d_%s", tileColDir, tileLigDir, tileColFile, tileLigFile, stringZoom);

            return name;
        }

        public long tileFileCol(double longitude, double latitude) {
            double r_earth = 6378.137; // Radius of earth in KM
            double circ_earth = Math.PI * r_earth * 2;

            double kmX = (-1 * Chunk.longitudeGetKmFrom(longitude, latitude, 0) + circ_earth) % circ_earth;

            // 32x32 tiles by File
            final long tileFileCols = 32;

            long tileCol = (long) ((kmX / kmZoom) % tileFileCols);

            return tileCol;
        }

        public long tileFileLig(double latitude) {
            double r_earth = 6378.137; // Radius of earth in KM
            double circ_earth = Math.PI * r_earth * 2;

            double kmY = (1 * Chunk.latitudeGetKmFrom(latitude, 0) + circ_earth) % circ_earth;

            // 32x32 tiles by File
            final long tileFileRows = 32;

            long tileLig = (long) ((kmY / kmZoom) % tileFileRows);

            return tileLig;
        }
    }

    // volatile: getInstance() reads it WITHOUT the lock once the instance exists (see there).
    private static volatile AppManager _instance = null;

    private Context _context = null;
    private static final ReadWriteLock _readWriteLock = new ReentrantReadWriteLock();
    private static final Lock _writeLock = _readWriteLock.writeLock();
    private static final Lock _readLock = _readWriteLock.readLock();


    // Historique GPS : un seul tableau de fixes (position lat/lon/alt + accuracy + temps). Remplace les
    // anciens tableaux paralleles _latitudes/_longitudes/_altitudes/_accuracies/_positionTimes.
    private GPSFix _fixes[] = null;

    // --- GPS-derived values (computed in setLocation from the raw history arrays above) ----------
    // Two speeds (user terminology): instantaneous (short baseline, reactive) and average (long
    // baseline, stable). _speedInKm/Mile/Knot expose the AVERAGE (the stable, displayed value).
    private double _instantSpeed = 0;   // km/h, short ~SHORT_WINDOW_MS baseline — reactive
    private double _averageSpeed = 0;   // km/h, long  ~LONG_WINDOW_MS baseline  — stable / displayed
    private double _instantSpeedV2 = 0; // km/h, #79 new instantaneous speed (Speed test page), not computed yet
    private String _motionStatus = "";  // detected motion events (Stopped/Steady/Accel/Decel/Turn)
    private int    _turnDir = 0;         // current turn direction: -1 left, +1 right, 0 none
    private int    _stillCount = 0;      // consecutive "still" fixes for the low-speed stop detector
    private double _uncertWidthRatio = 1.0; // direction-uncertainty band width / acc (>=1; 1 = acc circle)
    private double  _hwBearing = 0.0;    // course-over-ground reported by the GPS chipset (Location.getBearing)
    private boolean _hasHwBearing = false; // whether the last fix carried a hardware bearing
    private double  _deviceHeadingMag = 0.0;   // device heading (DeviceHeadingSensor), magnetic, [0,360)
    private boolean _hasDeviceHeading = false;
    private long    _deviceHeadingNotifyMs = 0;
    private static final long DEVICE_HEADING_NOTIFY_MS = 300;

    // What the phone itself feels (MotionSensor, task #94): still / steps / agitated.
    private int _sensorMotion = MotionSensor.STATE_UNKNOWN;
    private boolean _loggedStopped = true;    // last stopped state written to the log

    // Last GPS position, validated & timestamped: the BASE for the future-position prediction.
    private GPSFix _lastKnownCoordonate = new GPSFix();
    private long   _lastKnownWallMs = 0; // System.currentTimeMillis() of the last fix
    private boolean _hasFix = false;
    private boolean _fixStale = false;   // true quand le dernier fix a depasse FIX_STALE_MS (GPS perdu)
    private int     _fillCount = 0;      // filled raw-history slots (0..NB_SWAP_VALUES)

    // Calculate Values from the AppManager
    private double _speedInKm = 0;       // = _averageSpeed (km/h)
    private double _speedInMile = 0;
    private double _speedInKnot = 0;
    private double _directionAngle = 0;  // stabilized heading, degrees [0,360)

    // Calculate Values from the GPSManager (GPSTracker)
    private float _speedInKmGPSManager = 0;
    private float _speedInMileGPSManager = 0;
    private float _speedInKnotGPSManager = 0;

    private boolean _inLand = true;

    // Altitude correction (m) from WGS-84 to mean sea level at the current chunk: ele = wgs84 + it.
    // 0 below Android 14 (altitudes stay WGS-84, saved as ele_wgs84); null on Android >= 14 until the
    // chunk loading thread has computed it.
    private Double _altitudeCorrection = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ? null : 0.0;

    // Machine a etats du mode de deplacement (eau / lent / moyen / rapide), cadencee a 1 Hz par
    // myThreadLand. C'est elle, et non plus la vitesse instantanee, qui choisit le skin.
    private final TravelMode _travelMode = new TravelMode();

    private static final int NB_SWAP_VALUES = 30; // ~30 s of 1 Hz history (needed for low-speed heading)

    // --- Dual-baseline (speed/heading) + future-prediction tuning -------------------------------
    private static final long   SHORT_WINDOW_MS     = 2500;  // instantaneous baseline (reactive)
    private static final long   LONG_WINDOW_MS      = 5000;  // average baseline (stable, displayed)
    private static final long   LONG_WINDOW_MIN_MS  = 4000;  // long window collapses to this on a real change
    private static final double SPEED_DIVERGE_KMH   = 4.0;   // |instant-average| over this => real change
    private static final double SPEED_DIVERGE_FRAC  = 0.35;  // ...or this fraction of the average
    // Heading uses the SHORTEST of these baselines that has cleared the noise floor => responsive
    // (near-instant at vehicle speed, a few seconds on foot) yet noise-free.
    private static final long[] HEADING_WINDOWS_MS  = {2000, 3500, 5000, 7000, 9000};
    private static final double HEADING_DISP_ACC_FACTOR = 1.0; // need displacement >= this x accuracy (was 1.5)
    private static final double HEADING_MIN_DISP_M      = 3.0; // absolute floor in m (was 5.0)
    // STABLE heading = shortest baseline reaching this much travel (auto-adapts time to speed, so
    // angular precision is consistent at all speeds). At low speed this can span up to ~30 s.
    private static final long[] HEADING_STABLE_WINDOWS_MS = {3000, 5000, 8000, 12000, 18000, 25000, 30000};
    private static final double HEADING_STABLE_DISP_M     = 20.0; // target travel for a stable bearing
    // (II) Speed-adaptive turn-rate: omega from the change of heading over a window that grows when
    // slow (enough travel for a clean bearing) and shrinks when fast (responsive). Works at walking
    // speed and in tight circles, where the old cross-track-offset detector never fired.
    private static final double TURN_RATE_DISP_M   = 8.0;   // base displacement for a clean bearing
    private static final long   TURN_RATE_W_MIN_MS = 2000;
    private static final long   TURN_RATE_W_MAX_MS = 12000;
    private static final double TURN_RATE_MIN_DEGS = 1.5;   // |omega| below this => "not turning"
    // Fast stop: if we were moving faster than this and the last ~STOP_STILL_MS barely moved, drop
    // the speed to 0 immediately (detect a real stop in ~2 s instead of waiting for the long window).
    private static final double STOP_FROM_SPEED_KMH = 12.0;  // vehicle bonus fast-stop (~2 s)
    private static final long   STOP_STILL_MS       = 2000;
    // Low-speed stop detector (works at ANY speed): "still" for STOP_CONFIRM_FIXES consecutive fixes,
    // measured over STILL_WINDOW_MS, with a displacement floor of max(STOP_DISP_M, accuracy).
    private static final long   STILL_WINDOW_MS     = 9000;  // 3 km/h travels ~7.5 m > stop floor
    private static final double STOP_DISP_M         = 4.0;
    private static final int    STOP_CONFIRM_FIXES  = 3;
    private static final double SPEED_EPS_KMH       = 0.3;   // below this the speed snaps to 0
    private static final double TELEPORT_SPEED_KMH  = 400.0; // above => reject baseline (GPS jump)
    private static final double SPEED_SMOOTH_ALPHA  = 0.4;   // EMA on displayed avg speed (anti-flicker)
    // Age au-dela duquel le dernier fix n'est plus exploitable. La plus longue fenetre de calcul fait
    // 9 s, donc passe 5 s sans fix il n'y a de toute facon plus rien de frais. Cf. le watchdog dans
    // myThreadLand : minTime = 1 s est un PLANCHER, jamais une cadence garantie, et GPS_PROVIDER cesse
    // simplement d'emettre quand il perd les satellites (batiment), sans erreur ni notification.
    private static final long   FIX_STALE_MS       = 5000;
    // Motion-event detection (displayed for tuning; later used to drive reactivity/anticipation).
    private static final double ACCEL_EVENT_KMH     = 4.0;   // |instant-average| => accel/decel event
    // The predicted (displayed) position leads the last fix by this anticipation horizon, to cancel
    // the real-vs-display lag (~2-3 s). Adjustable at runtime via the F / + / - debug buttons.
    // _anticipationMs >= 0 = fixed horizon (ms). ANTICIP_AUTO (-1) = adapt to speed.
    private long _anticipationMs = 1500;          // default 1.5 s
    private static final long ANTICIP_AUTO = -1;
    private static final long ANTICIPATION_MIN_MS  = 1000;
    private static final long ANTICIPATION_MAX_MS  = 5000;
    private static final long ANTICIPATION_STEP_MS = 500;
    // "auto": shrink the anticipation as speed rises (less overshoot at speed, e.g. ~1 s at 70 km/h).
    private static final long   ANTICIP_AUTO_BASE_MS  = 2000;
    private static final double ANTICIP_AUTO_PER_KMH  = 14.0;

    // --- Heading prediction (predicted "cap") ---------------------------------------------------
    // Projects the heading forward by the rotation rate omega (deg/s, signed: + right / - left),
    // measured between the turn detector's old-cap reference and the current heading.
    // _headingAnticipMs >= 0 = fixed horizon (ms). HEADING_ANTICIP_AUTO (-1) = use the auto default.
    private long _headingAnticipMs = -1;                 // default: auto
    private static final long HEADING_ANTICIP_AUTO    = -1;
    private static final long HEADING_ANTICIP_AUTO_MS = 2000; // auto default (to be tuned on the road)
    private static final long HEADING_ANTICIP_MIN_MS  = 1000;
    private static final long HEADING_ANTICIP_MAX_MS  = 5000;
    private static final long HEADING_ANTICIP_STEP_MS = 500;
    private static final double TURN_RATE_SMOOTH_ALPHA = 0.4;  // EMA to denoise omega across fixes
    private static final double PREDCAP_MAX_TURN_DEG    = 90.0; // clamp on the predicted turn amount
    private double _turnRateDegS = 0.0;                  // smoothed rotation rate (signed, deg/s)

    // --- Average-speed window (speed reactivity, "SpdWin" F parameter) ---------------------------
    // Steady-state average speed is measured over this window. SPDWIN_AUTO uses LONG_WINDOW_MS;
    // a smaller window makes the speed react faster on big steps (at the cost of more wobble).
    private long _speedAvgMs = -1;                       // default: auto

    // --- Average-altitude window ("Altitude averaging" developer parameter) -----------------------
    // The current altitude used everywhere (height difference, slope, 3D distances, HERE, new marks)
    // is the mean of the fixes of this window, not the last fix: GPS altitude jumps by metres from one
    // fix to the next. ALTWIN_AUTO uses ALTWIN_DEFAULT_MS for now. Same steps as the speed window.
    private long _altAvgMs = ALTWIN_AUTO;
    private static final long ALTWIN_AUTO       = -1;
    private static final long ALTWIN_DEFAULT_MS = 5000;
    private Double _averageAltitude = null;              // m, null when no fix of the window has one

    // Vertical noise floor when the fix gives no vertical accuracy (Android < 8): the part of a
    // height change below it is not counted in the speed.
    private static final double VERT_NOISE_DEFAULT_M = 15.0;
    private static final long SPDWIN_AUTO    = -1;
    private static final long SPDWIN_MIN_MS  = 2000;
    private static final long SPDWIN_MAX_MS  = 12000;
    private static final long SPDWIN_STEP_MS = 1000;

    // --- Debug control: the "F" button cycles which parameter the +/- buttons act on -----------
    // Add a new tunable by giving it a PARAM_* index (and bumping PARAM_COUNT), a label getter with
    // the "*" active marker, and a case in debugPlus()/debugMinus().
    private int _debugParam = 0;
    private static final int PARAM_ZOOM    = 0;   // default
    private static final int PARAM_ANTICIP = 1;
    private static final int PARAM_PREDCAP = 2;   // predicted-cap horizon (ms / auto)
    private static final int PARAM_SPDWIN  = 3;   // average-speed window (ms / auto)
    private static final int PARAM_TRACE   = 4;   // GPS trace dots on/off
    private static final int PARAM_NPTS    = 5;   // nb of points used for the uncertainty width
    private static final int PARAM_OVERLAY = 6;   // generic debug-overlay on/off (HW cap line, ...)
    private static final int PARAM_DECL    = 7;   // manual magnetic-declination correction (deg)
    private static final int PARAM_PROXDIST = 8;  // right-list proximity radius (m)
    private static final int PARAM_ZONEWIDTH = 9; // #25 : target zone width at creation (m)
    private static final int PARAM_COORDFMT = 10; // coordinate display format (DD / DM / DMS)
    private static final int PARAM_WARNDIST = 11; // #7 : mark warning alarm trigger distance (m)
    private static final int PARAM_WARNETA  = 12; // #7 : mark warning alarm trigger ETA (s)
    private static final int PARAM_GAMELVL  = 13; // easter egg: hidden game level (0 = not playing)
    private static final int PARAM_COUNT   = 14;

    // --- Easter egg: the hidden game ------------------------------------------------------------
    // 0 = not playing, and the application behaves exactly as it always did -- not one extra pixel,
    // not one extra sound. Above 0 a game is running and the value is the level reached; there is
    // no maximum, GAME_LEVEL_MAX below is only how far the debug dial goes. Never persisted:
    // losing the last life goes back to 0, so a fresh launch always starts at 0 and the easter egg
    // has to be found again.
    private int _gameLevel = 0;
    public static final int GAME_LEVEL_MAX = 9;

    // Which of the level's ten waves is being fought. The orchestrator's to decide, this one's to
    // hold: it is the progress bar, and not the level line, that shows it.
    private int _gameWave = 0;

    // The progress bar, as the orchestrator hands it over a few times a second: how far through
    // the level in waves, out of how many, and how much the player is holding it up (0 climbing,
    // 1 the first notch of hurry up, 2 and above stuck).
    //
    // Volatile rather than under the lock, like the announcement above: three values written by
    // the game thread ten times a second, read by the drawing, with no invariant between them
    // that a lock would be protecting.
    private volatile float _waveProgress = 0f;
    private volatile int _waveCount = 0;
    private volatile int _waveStress = 0;

    // The finger's energy: five points, one back every three seconds, one point per creature
    // struck BY HAND. Which is the whole point of it: five blows and then the wait, so the game
    // cannot be won by drumming on the screen. A tomato costs nothing -- aiming is its own price.
    //
    // Two seconds a point, so ten for a bar from empty: the ceiling by hand is then 5 + t/2 --
    // ten creatures in the first ten seconds, twenty in thirty.
    //
    // Not a counter that something has to tick, but a value read off the clock: what is kept is
    // how much was left at the last spend, and when that was. Everything else is arithmetic, so
    // there is no timer to start, to stop, or to forget to stop.
    public static final int ENERGY_MAX = 5;
    private static final long ENERGY_RECOVER_MS = 2000;

    private double _energy = ENERGY_MAX;
    private long _energyAtMs = 0;

    // Score and lives. Both are reset by startGame(), so a new game never inherits the last one.
    // Losing the third life is game over -- back to level 0, out of the game -- and not a restart
    // at level 1: there has to be a way for the game to END on a loss.
    public static final int GAME_LIVES = 3;

    // Combien de coups il faut encaisser pour perdre une vie. Trois aujourd'hui, a tous les
    // niveaux : c'est un reglage qui devra varier avec le niveau, et il est isole ici pour que ce
    // jour-la il n'y ait qu'un endroit a toucher.
    public static final int GAME_HITS_PER_LIFE = 3;

    // Coups encaisses dans la vie en cours, et le son a jouer si c'est la derniere : la balle de
    // Young-Hee a le sien, le reste garde celui du jeu.
    // Ce que paie un palier franchi, par niveau atteint.
    private static final int LEVEL_UP_POINTS = 200;

    // De combien un gain sans point d'impact flotte au-dessus du marqueur.
    private static final double PLAYER_POPUP_UP = 40.0;

    // Creatures tuees d'affilee sans encaisser un coup, et les deux paliers du multiplicateur.
    private int _streak = 0;
    private static final int STREAK_X2 = 50;
    private static final int STREAK_X3 = 100;

    private int _hitsInLife = 0;
    private volatile int _gameOverSoundRes = 0;
    private int _score = 0;

    // The best score ever made, kept from one game to the next and from one launch to the next.
    private int _bestScore = 0;

    /**
     * The easter eggs that have been found, ONE BIT each, kept from one launch to the next.
     *
     * A mask and not a flag: the day there is a second one it takes the next bit, so two found
     * make 3 and three make 7. Nothing to migrate when that day comes.
     */
    public static final int EG_GAME = 1;

    private int _easterEggs = 0;
    private int _lives = GAME_LIVES;

    // The life gauge, drawn in the top-left panel: a heart whose tip points at the shown mark,
    // over a ring of three sectors. Eight hearts, one per 45 degree sector, 0 = north; sector 4 is
    // south, which is the heart the natural way up, and that is what a missing bearing shows.
    // Indexed like every other 45 degree sector in the application: 0 = north, then clockwise.
    // Same eight files, same naming and same viewport as ic_mark_badge_<n|ne|...>.xml -- the game
    // has to look like part of the app, not like something bolted onto it.
    private static final int[] HEART_RES = {
            R.drawable.eg_heart_n,  R.drawable.eg_heart_ne, R.drawable.eg_heart_e,  R.drawable.eg_heart_se,
            R.drawable.eg_heart_s,  R.drawable.eg_heart_sw, R.drawable.eg_heart_w,  R.drawable.eg_heart_nw };
    // South: the heart the natural way up, tip down. Shown when there is no bearing to point at.
    private static final int HEART_NO_BEARING = 4;
    // Indexed by lives LEFT: eg_life_0 is three spent sectors, eg_life_3 is three green ones.
    private static final int[] LIFE_RES = {
            R.drawable.eg_life_0, R.drawable.eg_life_1, R.drawable.eg_life_2, R.drawable.eg_life_3 };

    // The full-screen announcement in flight. Held here, drawn and sounded by GameSurface, which
    // reads it on every frame -- hence the volatile fields: written by the UI thread, read by the
    // render thread.
    public static final int ANNOUNCE_NONE = 0;
    public static final int ANNOUNCE_FIGHT = 1;
    public static final int ANNOUNCE_GAME_OVER = 2;

    // The four ways the hurry up says itself, in two PAIRS, and the pairs are not interchangeable:
    // the first notch warns or teases, the second threatens. Each notch draws one of its own two,
    // so the same wave never says the same thing twice and two waves in a row do not sound alike.
    // Consecutive on purpose: the orchestrator draws an index inside a pair.
    public static final int ANNOUNCE_HURRY = 3;         // hurry up!      \ first notch
    public static final int ANNOUNCE_TOO_SLOW = 4;      // too slow!      /
    public static final int ANNOUNCE_COMING = 5;        // they're coming! \ second notch
    public static final int ANNOUNCE_RUN = 6;           // run!            /

    public static final int ANNOUNCE_HURRY_COUNT = 4;

    // Clearing the last wave of a level. Two of them for one event, because they do not say it
    // with the same tune: the plain one for a level that ends on the crowd, the other for one that
    // ends on a boss -- which, the way the tables fall, is every even level.
    public static final int ANNOUNCE_LEVEL_UP = 7;
    public static final int ANNOUNCE_LEVEL_UP_BOSS = 8;
    // One duration per announcement, because they do not last the same: the banner has to stay up
    // for the WHOLE sound, so these are the length of the two .ogg files plus the fade-out that
    // GameSurface applies at the end. Change a sound, change the figure here.
    // How long each announcement stays on screen, by ANNOUNCE_*: the length of its voice plus the
    // fade out, since the banner has to hold as long as it is speaking.
    //
    // MEASURED IN THE FILES, by GameSurface, which is the only one that knows how to read a
    // duration -- and handed over here through setAnnounceDurationsMs(). Written out by hand, these
    // figures were a trap: re-recording a voice changed its length and nothing said so, the banner
    // simply left too early or stayed too long.
    //
    // What is written here is only what is used BEFORE the surface has had a chance to measure
    // anything, which is the few frames before the game thread starts.
    private volatile long[] _announceMs = { 0, 2500, 3220, 1880, 2210, 2500, 2270, 1400, 1400 };
    private volatile int _announce = ANNOUNCE_NONE;
    private volatile long _announceEndMs = 0;
    private volatile boolean _announceToPlay = false;   // sound not fired yet

    // After a game over the panel does not vanish at once: it stays in game dress for ten seconds,
    // and a tap on it during that window starts a new game. That window is the only way back in
    // without hunting three creepers again, and it is what makes losing feel like a turn rather
    // than an ejection.
    private static final long GAME_OVER_WINDOW_MS = 10000;
    private volatile long _gameOverUntilMs = 0;

    // Walked away from rather than lost: nothing is said and no second go is offered. The field is
    // still emptied, which the orchestrator reads off this.
    private volatile boolean _gameQuit = false;
    private Handler _gameOverHandler = null;

    // --- Debug mode (#36, Parameters page) ------------------------------------------------------
    // Master developer switch, persisted. OFF (default) = clean screen: the top-left debug overlay
    // and the F / - / + tuning buttons are hidden, GBLog stays at Information (DEBUG traces muted),
    // and MemLog is inert. ON = all of that back plus verbose logging.
    private boolean _debugMode = false;

    // #98 : Streamer mode (Developer parameters, persisted). ON = shown coordinates shifted far away
    // and map names swapped for fake ones, to record videos without giving away where we are.
    private boolean _streamerMode = false;

    // #52 : mode "cible unique" (Global parameters, persiste). Defaut OFF = 3 viseurs de mesure ; ON =
    // seule la cible du milieu est affichee/active (mark au point vise + segments de mesure).
    private boolean _singleTarget = false;

    // GPS trace (red debug dots) on/off.
    private boolean _traceOn = true;
    // Generic debug overlay on/off: gates extra debug graphics (currently the hardware-cap line).
    private boolean _overlayOn = false;
    // Number of most-recent valid points used for the uncertainty width. NPTS_AUTO (-1) = all valid.
    private int _uncertPoints = 3;
    private static final int NPTS_AUTO    = -1;
    private static final int NPTS_MIN     = 2;
    private static final int NPTS_MAX     = 30;
    private static final int NPTS_DEFAULT = 3;

    // --- Displayed speed unit (tap the big speed to cycle), persisted ---------------------------
    private int _speedUnit = 0;                 // 0 = km/h, 1 = mph, 2 = knots
    private static final int SPEED_UNIT_COUNT = 3;

    // --- Magnetic heading -----------------------------------------------------------------------
    // magnetic = true - (model declination + manual correction). Model from Android's GeomagneticField
    // (WMM); the manual "Decl" F-param fine-tunes it. Tap the heading readout to toggle true/magnetic.
    private double _declModelDeg = 0.0;         // declination from the geomagnetic model (East +)
    // #54 : la declinaison du modele (Android/WMM) peut etre coupee. Elle depend du LIEU et est
    // calculee seule ; la correction manuelle ci-dessous s'y AJOUTE, elle ne la remplace pas.
    private boolean _declAutoOn = true;         // defaut : declinaison automatique active
    private double _magDeclManual = 0.0;        // manual correction (F-param Decl), persisted
    private boolean _headingMagnetic = false;   // false = display true (T), true = magnetic (M)
    // volatile: written on the UI thread (selection, panel taps), read on the game thread by the
    // hearts and the items to know which mark is being steered to.
    private volatile PersonalMark _shownMark = null;  // selected mark shown in the top-left panel (#12/#13)
    // --- Surveillance zone (#25), transient -----------------------------------------------------
    private SurveillanceZone _zone = null;
    private int _zoneState = SurveillanceZone.NONE; // ACTIVE / EDIT quand une zone existe
    private static final double ZONE_MARGIN_CREATE_M = 30.0; // un peu d'air autour des points englobés
    // #25 : largeur cible a la creation (param F "ZoneW"). Si l'englobante est plus etroite, on elargit
    // vers cette cible, sans jamais depasser la longueur. Pas variable, persiste.
    private double _zoneTargetWidthM = 100.0;
    private static final double ZONEW_MIN = 20.0;
    private static final double ZONEW_MAX = 2000.0;
    // Echelle des largeurs proposees, partagee par la liste de la page Parameters et par les boutons
    // +/- du mode debug : les deux parcourent donc exactement les memes valeurs. Le pas s'ouvre avec
    // la largeur (5 m sous 100 m, 50 m jusqu'au kilometre, 500 m au-dela) pour rester court a regler.
    private static final double[] ZONEW_VALUES = {
            20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100,
            150, 200, 250, 300, 350, 400, 450, 500, 550, 600, 650, 700, 750, 800, 850, 900, 950, 1000,
            1500, 2000
    };

    // Format d'affichage des coordonnees (param F "CoordFmt"). 3 valeurs, defaut DM. Persiste.
    // Pour l'instant utilise nulle part (juste le param, selectionnable/reglable via F et +/-).
    private int _coordFmt = 1;   // defaut DM
    public static final int COORDFMT_DD = 0, COORDFMT_DM = 1, COORDFMT_DMS = 2, COORDFMT_COUNT = 3;
    private static final double DECL_MIN  = -25.0;
    private static final double DECL_MAX  =  25.0;
    private static final double DECL_STEP =   0.5;

    // --- #54 : compas et cartes de deviation ----------------------------------------------------
    // Un compas porte 8 corrections, une par point cardinal, qui compensent le MATERIEL (ferraille,
    // enceinte, radio...) la ou la declinaison compense le LIEU. La liste et le compas choisi sont
    // persistes. Le compas n'entre PAS encore dans le cap affiche en navigation : l'interpolation
    // entre deux points cardinaux reste a ecrire, seuls la creation et le reglage existent.
    private final List<Compass> _compasses = new ArrayList<>();
    private int _compassIndex = -1;                     // -1 = aucun compas choisi
    private static final String COMPASS_DEFAULT_NAME = "My Compass";

    // Etat de la page de configuration, transitoire : compas en cours d'edition et point cardinal en
    // cours de reglage. Les 8 cases de la rose sont creees une fois pour toutes, la page s'y attache.
    private int _adjustCompass = -1;
    private int _adjustSector = 0;
    private final List<CompassSector> _compassSectors = createCompassSectors();

    private static List<CompassSector> createCompassSectors() {
        List<CompassSector> sectors = new ArrayList<>(Compass.SECTOR_COUNT);

        for (int s = 0; s < Compass.SECTOR_COUNT; s++)
            sectors.add(new CompassSector(s));

        return sectors;
    }

    // --- #68 : adresses des serveurs interrogés en ligne -----------------------------------------
    // Elles etaient des constantes de compilation, elles deviennent des reglages : n'importe qui doit
    // pouvoir faire tourner MyBuoy contre SON serveur. Elles sont donc lues par les threads de fond
    // (chargement de chunk, liste des cartes) pendant que l'utilisateur peut les changer depuis la
    // page Parameters : les deux passent sous le verrou comme le reste de l'etat.
    //
    // Overpass : on ne garde que la BASE, et le code recolle "/interpreter" et "/status". L'ancienne
    // instance officielle FOSSGIS reste le defaut ; les miroirs (kumi.systems, private.coffee)
    // servent des donnees corrompues ou perimees, d'ou l'avertissement laisse dans Chunk.
    public static final String OVERPASS_BASE_DEFAULT = "https://overpass-api.de/api";
    // Cartes hors ligne : meme principe, on ne garde que la base et le code recolle le nom du
    // script. Sans "www", qui est la forme canonique du domaine ; l'adresse naturelle serait le
    // sous-domaine mybuoy.golden-block.fr, que l'hebergement gratuit actuel ne permet pas de creer.
    public static final String MAPS_BASE_DEFAULT = "https://golden-block.fr/mybuoy/api";
    // Nominatim (#67) : la recherche d'un lieu par son nom ou son adresse. Meme principe, on ne
    // garde que la BASE et le code recolle "/search". Le defaut est l'instance publique de la
    // fondation OSM, qui tourne sur une machine unique : sa politique d'usage impose un User-Agent
    // identifiable, une requete par seconde au maximum, et interdit l'autocompletion au fil de la
    // frappe. Qui en veut plus heberge la sienne et la designe ici.
    public static final String NOMINATIM_BASE_DEFAULT = "https://nominatim.openstreetmap.org";
    private String _overpassBaseUrl = OVERPASS_BASE_DEFAULT;
    private String _mapsBaseUrl = MAPS_BASE_DEFAULT;
    private String _nominatimBaseUrl = NOMINATIM_BASE_DEFAULT;

    // Nous identifie aupres des serveurs OSM. Obligatoire : les politiques d'usage d'Overpass et de
    // Nominatim reclament un nom d'application et un contact, un client anonyme peut etre bloque et
    // un client qui se fait passer pour un autre LE SERA. Elle etait privee dans Chunk ; la
    // recherche de lieu en a besoin aussi, elle remonte donc ici, aupres des adresses de serveur.
    public static final String USER_AGENT =
            "MyBuoy/" + BuildConfig.VERSION_NAME + " (mybuoy@golden-block.fr)";

    // --- Right-list proximity radius (m): marks nearer than this show in the right list; selected
    // marks always show regardless. Tunable via the "ProxDist" F-param.
    // #67 : rayon de la recherche "Around me". La page de recherche en fait une boite carree
    // centree sur la position courante, que Nominatim recoit en viewbox avec bounded=1 : dans un
    // cadre impose, un simple mot de categorie ("bakery") suffit, la ou une recherche sans cadre
    // exige un nom ou la tournure "bakeries in <ville>".
    private int _searchRadiusKm = 20;
    // Choices offered on the parameters page, in km, ascending.
    private static final int[] SEARCHRAD_VALUES_KM = {
            1, 2, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50,
            55, 60, 65, 70, 75, 80, 85, 90, 95, 100
    };

    // #67 : nombre de lieux demandes au serveur de recherche, et donc affiches -- tout ce qui
    // revient est montre. Le defaut est le maximum du serveur : demander moins n'a d'interet que
    // pour raccourcir la liste, jamais pour la qualite des reponses, puisque c'est le serveur qui
    // choisit ce qu'il coupe, selon SA notion d'importance et non la distance.
    private int _searchLimit = GeoSearch.SERVER_MAX_RESULTS;

    // #67 : rayon en metres sous lequel un resultat de recherche est considere comme DEJA marque.
    // Volontairement serre : une marque nee d'un resultat en porte les coordonnees exactes, a
    // l'arrondi de l'editeur pres, tandis qu'une marque posee a la main a quelques dizaines de
    // metres designe autre chose.
    private int _samePlaceM = 10;
    private static final int SAMEPLACE_MIN  = 1;
    private static final int SAMEPLACE_MAX  = 20;
    private static final int SAMEPLACE_STEP = 1;

    // #67 : faut-il AUSSI le meme titre pour considerer que c'est le meme lieu ? Desactive par
    // defaut : la position seule suffit. Active, il evite de confondre deux etablissements loges a
    // la meme adresse, mais une marque renommee apres coup n'est alors plus reconnue.
    private boolean _samePlaceTitle = false;
    private static final int SEARCHLIMIT_MIN  = 5;
    private static final int SEARCHLIMIT_MAX  = GeoSearch.SERVER_MAX_RESULTS;
    private static final int SEARCHLIMIT_STEP = 5;

    private int _proxMeters = 2000;             // default = the previous hard-coded 2 km

    // Marks list: height difference shown from this many metres, hidden again one metre below
    // (hysteresis). 0 = never shown.
    private int _elevMinMeters = ELEVMIN_DEFAULT;
    private static final int ELEVMIN_DEFAULT = 10;
    private static final int[] ELEVMIN_STEPS = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100 };
    private static final int PROXDIST_MIN  = 1000;
    private static final int PROXDIST_MAX  = 9000;
    private static final int PROXDIST_STEP = 500;

    // Small unit used under the mile when the speed unit is mph (metres / yards / feet). Default
    // feet = the historical behaviour. Persisted.
    private int _smallUnit = 2;
    public static final int SMALLUNIT_M = 0, SMALLUNIT_YD = 1, SMALLUNIT_FT = 2, SMALLUNIT_COUNT = 3;

    // --- Mark warning alarm trigger (#7): fire if distance <= WarnDist OR eta <= WarnETA, where
    // eta = distance / ground speed (ignored when stopped). Both conditions always active. F-params,
    // persisted. Replaces the old hard-coded 300 m in MapMark.
    private int _warnDistM = 300;               // default = the previous hard-coded 300 m
    private static final int WARNDIST_MIN  = 50;
    private static final int WARNDIST_MAX  = 2000;
    private static final int WARNDIST_STEP = 50;
    private int _warnEtaSec = 30;               // default 30 s
    private static final int WARNETA_MIN  = 5;
    private static final int WARNETA_MAX  = 120;
    private static final int WARNETA_STEP = 5;

    // Sprite d'avatar choisi par categorie (AvatarCatalog.CAT_*), sous forme de nom de ressource.
    // Vide = pas de choix enregistre, on prend le defaut de la categorie.
    private final String[] _avatarNames = new String[AvatarCatalog.CAT_COUNT];
    // Incremente a chaque changement d'avatar : le thread de rendu (MapGPSPos) le compare a la valeur
    // qu'il a vue pour savoir qu'il doit recharger son sprite. volatile parce qu'il est lu hors lock.
    private volatile int _avatarSerial = 0;

    // --- Persistence (SharedPreferences) --------------------------------------------------------
    private static final String PREFS = "mybuoy_prefs";
    private static final String KEY_SPEED_UNIT = "speed_unit";
    private static final String KEY_DEBUG_MODE = "debug_mode";
    private static final String KEY_STREAMER_MODE = "streamer_mode";
    private static final String KEY_SINGLE_TARGET = "single_target";
    private static final String KEY_DEBUG_PARAM = "debug_param";
    private static final String KEY_BEST_SCORE = "best_score";
    private static final String KEY_EASTER_EGGS = "easter_eggs";
    private static final String KEY_ANTICIP_MS = "anticipation_ms";
    private static final String KEY_HEADING_ANTICIP_MS = "heading_anticipation_ms";
    private static final String KEY_SPEED_AVG_MS = "speed_avg_ms";
    private static final String KEY_ALT_AVG_MS = "alt_avg_ms";
    private static final String KEY_LAST_LAT = "last_lat";
    private static final String KEY_LAST_LON = "last_lon";
    private static final String KEY_LAST_INLAND = "last_inland";
    private static final String KEY_TRACE_ON = "trace_on";
    private static final String KEY_UNCERT_POINTS = "uncert_points";
    private static final String KEY_OVERLAY_ON = "overlay_on";
    private static final String KEY_MAG_DECL = "mag_decl_manual";
    private static final String KEY_DECL_AUTO = "decl_auto_on";
    private static final String KEY_HEADING_MAG = "heading_magnetic";
    private static final String KEY_PROX_METERS = "prox_meters";
    private static final String KEY_ELEV_MIN_METERS = "elev_min_meters";
    private static final String KEY_ZONE_WIDTH = "zone_target_width";
    private static final String KEY_COORD_FMT = "coord_fmt";
    private static final String KEY_SMALL_UNIT = "small_unit";
    private static final String KEY_WARN_DIST = "warn_dist_m";
    private static final String KEY_WARN_ETA = "warn_eta_s";
    private static final String KEY_OVERPASS_URL = "overpass_base_url";

    // One key per server, kept apart from the address: going to look at another instance and
    // coming back must not mean typing it again. NEVER shipped with the application -- every key
    // here is the user's own, asked for on the helper page.
    private static final String KEY_OVERPASS_KEY_PREFIX = "overpass_key_";
    private static final String KEY_MAPS_URL = "maps_manifest_url";
    private static final String KEY_NOMINATIM_URL = "nominatim_base_url";
    private static final String KEY_SEARCH_RADIUS = "search_radius_km";
    private static final String KEY_SEARCH_LIMIT = "search_limit";
    private static final String KEY_SAME_PLACE = "same_place_m";
    private static final String KEY_SAME_PLACE_TITLE = "same_place_title";
    // #54 : la liste des compas, une entree par index. Le compteur sert a relire la liste et a
    // effacer les entrees devenues orphelines apres une suppression.
    private static final String KEY_COMPASS_COUNT    = "compass_count";
    private static final String KEY_COMPASS_SELECTED = "compass_selected";
    private static final String KEY_COMPASS_NAME     = "compass_%d_name";
    private static final String KEY_COMPASS_DEV      = "compass_%d_dev";
    // Sprite choisi pour chaque categorie d'avatar, indexe par AvatarCatalog.CAT_*. On enregistre le
    // NOM de la ressource et pas son identifiant : les identifiants changent a chaque compilation.
    private static final String[] KEY_AVATAR = {
            "avatar_sea", "avatar_walk", "avatar_ride", "avatar_drive"
    };
    // #81 : son choisi pour chacune des trois alarmes, et interrupteur des alarmes radar. Comme pour
    // les avatars on enregistre le NOM de la ressource, jamais son identifiant, qui change a chaque
    // compilation. Chaine vide = pas de son, l'alerte reste visuelle.
    private static final String KEY_SND_RADAR  = "alarm_snd_radar";
    private static final String KEY_SND_MARK   = "alarm_snd_mark";
    private static final String KEY_SND_ZONE   = "alarm_snd_zone";
    private static final String KEY_RADAR_ON   = "alarm_radar_on";

    // #85: low-resource mode. A single switch, turned on BY HAND by the user -- no automatic
    // detection of the amount of RAM or of the phone model.
    private static final String KEY_LOW_RES    = "low_resource_mode";

    // #86: which volume new files should go to. Off by default, which means the internal storage;
    // on, the SD card is tried first. It is only a PREFERENCE -- when the preferred volume is
    // missing or short on space, the other one is used rather than failing (see preferredDir).
    private static final String KEY_PREFER_SD  = "prefer_sd_storage";

    // #86: how much room the chunk cache may take. Three steps rather than a free number: what
    // matters is the order of magnitude, and a coastal chunk weighs a few tens of kilobytes where a
    // dense city one weighs tens of megabytes.
    private static final String KEY_CACHE_SIZE = "cache_size_step";

    // How far the tide page may look for data when the model has none where you are.
    // EOT20 draws its coastline well offshore -- it drops the nodes altimetry cannot measure
    // near land -- so a bay can sit twenty kilometres from the first node holding anything.
    private static final String KEY_TIDE_REACH = "tide_reach_step";

    private static final int TIDE_REACH_FIRST_KM = 10;
    private static final int TIDE_REACH_STEP_KM = 10;
    private static final int TIDE_REACH_COUNT = 20;          // 10 km to 200 km
    private static final int TIDE_REACH_DEFAULT_POS = 4;     // 50 km

    public static final int CACHE_SIZE_SMALL  = 0;
    public static final int CACHE_SIZE_MEDIUM = 1;
    public static final int CACHE_SIZE_LARGE  = 2;

    private static final long[] CACHE_SIZE_BYTES = {
            100L * 1024 * 1024,          // Small
            500L * 1024 * 1024,          // Medium
            2048L * 1024 * 1024          // Large
    };

    // Short labels on purpose: the value column of a settings row is narrow, and "Medium (500 MB)"
    // was clipped on both sides. The sizes are spelled out in the row's description instead.
    private static final String[] CACHE_SIZE_LABELS = { "Small", "Medium", "Large" };

    private static final String SND_RADAR_DEFAUT = "alm_collision_chute";
    private static final String SND_MARK_DEFAUT  = "alm_melody_boudin";
    private static final String SND_ZONE_DEFAUT  = "alm_zone_double";

    private String _sndRadar = SND_RADAR_DEFAUT;
    private String _sndMark  = SND_MARK_DEFAUT;
    private String _sndZone  = SND_ZONE_DEFAUT;
    private boolean _radarAlarmOn = true;
    private boolean _lowResourceMode = false;
    private boolean _preferSdStorage = false;
    private int _cacheSizeStep = CACHE_SIZE_MEDIUM;

    private int _tideReachStep = TIDE_REACH_DEFAULT_POS;

    private static final long POSITION_SAVE_INTERVAL_MS = 10_000L;
    private long _lastSaveMs = 0;
    // Caps the "elapsed since last fix" part added to the anticipation horizon.
    private static final long   PREDICTION_CLAMP_MS = 4000;


    private Chunk _currentChunk = null;

    private GPSTracker _gps = null;

    private double _fps = 0;

    // #87 follow-up: how late the UI thread is. A Runnable is posted every second on the main
    // looper, and what it measures is the delay before it actually runs: that is exactly the time
    // the interface was stuck. Android calls the app frozen ("not responding") past five seconds,
    // and the tester sees no log -- the panel keeps the running value and the worst since launch.
    private static final long UI_LAG_PERIOD_MS = 1000;

    // #87 follow-up: counters that make a runaway loop visible on the panel, with no cable. Two or
    // three publications at startup are normal; dozens in the same time mean something loops.
    private int _countPublished = 0;
    private int _countPrepared = 0;
    private int _countDownloads = 0;
    private int _countFailures = 0;
    private String _lastPrepString = "--";

    private String _uiLagString = "--";
    private long _uiLagMaxMs = 0;
    private long _uiLagDueMs = 0;
    private Handler _uiLagHandler = null;

    // Debug-panel performance readouts (see setFrameTimes / setDrawTimes / setLandStats).
    // "--" until the render thread has posted a first second of measurements.
    private String _frameString = "--";
    private String _drawString  = "--";
    private String _landString  = "--";

    private TypeZoom _zoom = TypeZoom.ZOOM_20;

    private boolean _initializedDB = false;
    private AppDB _appDB = null;


    private List<MapInfo> _maps = null;
    private boolean _mapsLoaded = false;
    // #89: the ORIGIN of a chunk reload request, no longer a bare boolean. The confirmation shown
    // at the end must only speak of reloads that were actually wanted -- the double-tap and the end
    // of a map download; the one the app asks itself at startup, while loading the map list, has to
    // stay silent.
    private static final int RELOAD_NONE     = 0;
    private static final int RELOAD_MANUAL   = 1;   // double-tap on the map
    private static final int RELOAD_MAP      = 2;   // end of a map download
    private static final int RELOAD_INTERNAL = 3;   // map list being loaded (startup)

    private int _chunkReloadOrigin = RELOAD_NONE;

    private ListObservable<PersonalMark> _marks = null;
    // #97 : bumped under the write lock at every add or remove, so that a draw list copied
    // before a removal can never be published after it.
    private long _marksVersion = 0;
    // #97 : the marks in draw order. A new list at each publication, never changed afterwards.
    private volatile List<PersonalMark> _drawMarks = Collections.<PersonalMark>emptyList();
    private ListObservable<PersonalMark> _proximityMarks = null;
    private boolean _marksLoaded = false;
    private boolean _marksUpdated = false;



    // ---- Chunk supervisor (#87) --------------------------------------------------------------
    //
    // GOAL: there is ALWAYS a chunk on screen whose bbox holds the GPS fix. The loop used to download
    // in line and could spend the whole fair-use wait, a 15 s connection timeout and a 20 s read
    // without noticing that the position was leaving the chunk: at sea or in a dead zone the map
    // froze on an area already left.
    //
    // So this loop does no heavy work at all. ChunkDownloader talks to the server; ChunkPreparer
    // builds what needs no network (a cached area, or an OFFLINE chunk: the coastline from
    // land_polygons). Both post fully built chunks to _loaderQueue. The loop waits on that queue for
    // at most SUPERVISOR_TICK_MS -- a result or a forced reload (WAKE) cuts the wait short -- then
    // looks at where the position stands in the chunk on screen:
    //
    //   - in the reload zone (coordonateInChunk() false): a download is asked, and the preparer
    //     builds a STANDBY chunk around the position -- an optimum cached area is shown as soon as it
    //     comes, anything else is kept aside for the switch below.
    //   - within Chunk.SWITCH_MARGIN_RATIO of areaMarginKM from the edge: switch NOW to the standby,
    //     without waiting for anything. It is flagged incomplete, so the download goes on, and the
    //     downloaded chunk replaces it when it comes.
    //   - an incomplete chunk on screen keeps asking for a download, never for another standby.

    private static final long SUPERVISOR_TICK_MS = 1000;
    private static final long SUPERVISOR_SLOW_MS = 300;

    // Results from ChunkDownloader, and WAKE tokens from the places that force a reload.
    private final LinkedBlockingQueue<Object> _loaderQueue = new LinkedBlockingQueue<Object>();

    // A failed download is asked again only DOWNLOAD_RETRY_TICKS ticks later, never on the wake-up
    // its failure causes. Measured on the first dead-zone drive: the failure is instant there (no
    // DNS), its result woke the supervisor at once, which asked again at once -- about a hundred
    // attempts a second, 156 656 in 23 minutes of dead zone. Ten ticks rather than one: each
    // failure shows a red footer, and one a second made it come back as soon as it was tapped away.
    // Only the RETRY waits: the supervisor still looks at the position every tick, so leaving the
    // chunk is caught and switched at once. A double-tap ignores the delay. Only touched by the
    // supervisor thread.
    private static final int DOWNLOAD_RETRY_TICKS = 10;
    private long _downloadRetryNotBeforeMs = 0;
    private static final Object WAKE = new Object();

    private final ChunkDownloader _downloader = new ChunkDownloader(_loaderQueue);
    private final ChunkPreparer _preparer = new ChunkPreparer(_loaderQueue, _downloader);

    // A non-optimum chunk prepared in advance, kept for the next forced switch. Only touched by the
    // supervisor thread.
    private Chunk _standby = null;

    // A chunk built off the supervisor carries per-road textures (native memory, ~one bitmap per
    // road, scaled by ChunkPreparer/ChunkDownloader). One that will never be shown releases them at
    // once. Safe: it was never handed to the renderer, so nothing draws it.
    private static void discard(Chunk chunk) {
        if (chunk != null)
            chunk.disposeTextures();
    }

    // Minimum spacing between two ANTICIPATION requests to the preparer (not the urgent ones). A
    // guard against any request loop like the one found on the drive; nothing should hit it.
    private static final long PREPARE_SPACING_MS = 2000;
    private long _prepareNotBeforeMs = 0;

    private void dropStandby() {
        if (_standby != null)
            traceChunk("standby-dropped", _standby);
        discard(_standby);
        _standby = null;
    }

    // Debug trace of the chunks in memory (OOM of 2026-10-09): every chunk that reaches the
    // supervisor, held WEAKLY, so each line can say how many are not collected yet.
    // Only touched by the supervisor thread.
    private final List<WeakReference<Chunk>> _tracedChunks = new ArrayList<WeakReference<Chunk>>();

    private void traceChunk(String event, Chunk chunk) {
        if( !GBLog.isDebug() )
            return;

        boolean known = (chunk == null);
        int alive = 0;
        Iterator<WeakReference<Chunk>> it = _tracedChunks.iterator();
        while( it.hasNext() ) {
            Chunk c = it.next().get();
            if( c == null ) {
                it.remove();
            } else {
                alive++;
                if( c == chunk )
                    known = true;
            }
        }
        if( !known ) {
            _tracedChunks.add(new WeakReference<Chunk>(chunk));
            alive++;
        }

        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);

        MemLog.logEvent(String.format(Locale.ROOT, "CHUNK %-18s %s %s heap=%dMB alive=%d",
                event,
                (chunk == null) ? "-" : chunk.getOrigin(),
                (chunk == null) ? "" : chunk.bboxString(),
                usedMB, alive));
    }

    // Where a download is centred: the predicted position and the speed, read together.
    // Package-private, for ChunkDownloader.
    double[] chunkRequestSnapshot() {
        _readLock.lock();
        try {
            GPSFix p = predictedCoordonateRaw();
            return new double[]{ p.getLatitude(), p.getLongitude(), _speedInKm };
        } finally {
            _readLock.unlock();
        }
    }

    // Wakes the supervisor now instead of at its next tick.
    private void wakeSupervisor() {
        _loaderQueue.offer(WAKE);
    }

    Thread myThread = new Thread() {
        @Override
        public void run() {
            // Wait for the map list (land_polygons among it) before the first chunk: otherwise
            // dbChunk() does not find land_polygons and reports "in the water" wrongly until the
            // next reload. Bounded to ~10 s so an online map is not held up if the map init drags
            // (unanswered permissions, for instance).
            for( int i = 0; i < 100; i++ ) {
                boolean loaded;
                _readLock.lock();
                try { loaded = _mapsLoaded; } finally { _readLock.unlock(); }
                if( loaded )
                    break;
                try { Thread.sleep(100); } catch(InterruptedException e){ return; }
            }

            while (true) {
                // try/catch INSIDE the loop: nothing that goes wrong with one chunk may end it.
                try {
                    // Debug traces of the loop's three steps: the last one printed before a stall
                    // tells which step it is stuck in.
                    GBLog.d("APPMAN", "SUPERVISOR poll (queued " + _loaderQueue.size() + ")");
                    Object message = _loaderQueue.poll(SUPERVISOR_TICK_MS, TimeUnit.MILLISECONDS);

                    long workStart = SystemClock.uptimeMillis();

                    if (message instanceof ChunkDownloader.Result) {
                        GBLog.d("APPMAN", "SUPERVISOR onChunkDownloaded");
                        onChunkDownloaded((ChunkDownloader.Result) message);
                    } else if (message instanceof ChunkPreparer.Result) {
                        GBLog.d("APPMAN", "SUPERVISOR onChunkPrepared");
                        onChunkPrepared((ChunkPreparer.Result) message);
                    }

                    GBLog.d("APPMAN", "SUPERVISOR superviseChunk"
                            + ((message == WAKE) ? " (woken)" : ((message == null) ? " (tick)" : "")));
                    superviseChunk();

                    // The whole point of #87 is that this loop never stalls: say so when it does.
                    long workMs = SystemClock.uptimeMillis() - workStart;
                    if (workMs > SUPERVISOR_SLOW_MS)
                        GBLog.w("APPMAN", "SUPERVISOR SLOW pass: " + workMs + " ms");

                } catch (InterruptedException e) {
                    GBLog.i("APPMAN", "myThread interrupted, exit");
                    return;
                } catch (OutOfMemoryError e) {
                    // An Error, not an Exception: the catch below would not see it and the thread
                    // would die for good (as it did in Paris, 16 km2). The failed allocation is
                    // rolled back and the chunk goes out of scope, so a GC can reclaim it.
                    GBLog.e("APPMAN", "chunk load OOM, skip & retry: " + e);
                    MemLog.logCrash(e);
                    ViewsController.notifyError("Chunk load: out of memory, retrying.");
                } catch (Exception e) {
                    GBLog.e("APPMAN", "chunk supervision failed, retry next tick: " + e, e);
                    ViewsController.notifyError("Chunk load failed: " + rootMsg(e));
                }
            }
        }
    };

    // One look at the position against the chunk on screen, and what to do about it. Nothing here
    // lasts more than a few milliseconds: the heavy work is in ChunkPreparer and ChunkDownloader.
    private void superviseChunk() {
        int reloadOrigin;
        Chunk current;
        double lat;
        double lon;

        _writeLock.lock();
        try {
            reloadOrigin = _chunkReloadOrigin;
            _chunkReloadOrigin = RELOAD_NONE;

            current = _currentChunk;
            GPSFix p = predictedCoordonateRaw();
            lat = p.getLatitude();
            lon = p.getLongitude();
        } finally {
            _writeLock.unlock();
        }

        // A reload from inside the app (map list at startup, a map just downloaded) rebuilds the
        // chunk on screen: land_polygons may have appeared under it.
        boolean rebuild = (reloadOrigin == RELOAD_INTERNAL) || (reloadOrigin == RELOAD_MAP);

        boolean inReloadZone = (current == null) || !current.coordonateInChunk(lat, lon);
        boolean nearEdge = (current == null) || !current.clearOfSwitchBand(lat, lon);

        // Judged on the chunk on screen BEFORE any switch below: a position outside it (a restart
        // far from the restored spot) is a jump, and a jump skips the fair-use throttle.
        boolean jumped = (current == null) || !current.containsRaw(lat, lon);

        // A standby the position has moved on from is no use any more.
        if ((_standby != null) && !_standby.clearOfSwitchBand(lat, lon))
            dropStandby();

        if (rebuild) {
            // The very area on screen may be taken again: that is the point.
            _preparer.submit(new ChunkPreparer.Request(reloadOrigin, true, null));

        } else if (nearEdge) {
            if (_standby != null) {
                publishChunk(_standby, current, lat, lon);
                current = _standby;
                _standby = null;            // now on screen: NOT disposed
            } else if (!_preparer.isBusy()) {
                // Nothing ready: build one and show it the moment it comes.
                _preparer.submit(new ChunkPreparer.Request(RELOAD_NONE, true, current));
            }

        } else if (inReloadZone && (_standby == null) && !_preparer.isBusy()
                && (SystemClock.uptimeMillis() >= _prepareNotBeforeMs)) {
            // Anticipation: the switch band is still some way off, prepare what it will need.
            _prepareNotBeforeMs = SystemClock.uptimeMillis() + PREPARE_SPACING_MS;
            _preparer.submit(new ChunkPreparer.Request(RELOAD_NONE, false, current));
        }

        boolean wantBetter = (current == null)
                || current.isIncomplete()
                || !current.coordonateInChunk(lat, lon);

        // A manual reload (double-tap) always downloads, cache or not, and replaces a request still
        // waiting. Otherwise one request at a time, only while a better chunk is wanted, not before
        // the retry delay after a failure, and not while the preparer is still looking: it may find
        // an optimum cached area, which makes the query useless -- at every startup where the area
        // is cached, measured. The switch itself never waits on this.
        boolean asked = false;

        if (reloadOrigin == RELOAD_MANUAL) {
            _downloader.submit(new ChunkDownloader.Request(RELOAD_MANUAL, jumped));
            asked = true;
        } else if (wantBetter && !_downloader.isBusy() && !_preparer.isBusy()
                && (SystemClock.uptimeMillis() >= _downloadRetryNotBeforeMs)) {
            _downloader.submit(new ChunkDownloader.Request(RELOAD_NONE, jumped));
            asked = true;
        }

        if (asked) {
            _writeLock.lock();
            try { _countDownloads++; } finally { _writeLock.unlock(); }
            notifyPropertyChanged(BR.chunkCountsString);
        }
    }

    // A chunk built by ChunkPreparer came back.
    private void onChunkPrepared(ChunkPreparer.Result result) {
        Chunk current;
        double lat;
        double lon;

        _readLock.lock();
        try {
            current = _currentChunk;
            GPSFix p = predictedCoordonateRaw();
            lat = p.getLatitude();
            lon = p.getLongitude();
        } finally {
            _readLock.unlock();
        }

        Chunk chunk = result.chunk;

        _writeLock.lock();
        try {
            _countPrepared++;
            _lastPrepString = String.format(Locale.US, "%s %d ms", chunk.getOrigin(), result.tookMs);
        } finally {
            _writeLock.unlock();
        }
        notifyPropertyChanged(BR.chunkCountsString);
        notifyPropertyChanged(BR.lastPrepString);

        // Built for a position we have since moved away from: it no longer holds the fix, or only in
        // its switch band while the chunk on screen still does better.
        if (!chunk.containsRaw(lat, lon)
                || (!chunk.clearOfSwitchBand(lat, lon) && (current != null) && current.containsRaw(lat, lon))) {
            GBLog.i("APPMAN", "prepared chunk dropped (position moved on): " + chunk.bboxString());
            traceChunk("prep-dropped", chunk);
            discard(chunk);
            return;
        }

        boolean currentComplete = (current != null) && !current.isIncomplete();
        boolean currentClear = (current != null) && current.clearOfSwitchBand(lat, lon);
        boolean inReloadZone = (current == null) || !current.coordonateInChunk(lat, lon);

        // Show it now, or not. An optimum area is shown unless a complete chunk that needs no
        // replacing yet is on screen. A lesser one is only shown when nothing sound is on screen:
        // no chunk, the position in the switch band, or a rebuild over an incomplete chunk. It never
        // replaces a complete chunk that still covers the position -- seen at startup, the internal
        // rebuild prepared a cached F2 area and put it over the complete F1 chunk just downloaded.
        boolean publish;
        if (result.optimum) {
            publish = !currentComplete || inReloadZone || result.request.publishOnArrival;
        } else {
            publish = (current == null)
                    || !currentClear
                    || (!currentComplete && result.request.publishOnArrival);
        }

        if (publish) {
            publishChunk(chunk, current, lat, lon);
            dropStandby();

            if (result.request.reloadOrigin == RELOAD_MAP)
                ViewsController.notifyInfo("Chunk reloaded!");
            return;
        }

        if (inReloadZone && !result.optimum) {
            // Kept for the switch. Dropping it here, as a first version did, made the supervisor ask
            // for another at once: two preparations a second, for nothing.
            if (_standby != chunk)
                dropStandby();
            _standby = chunk;
            GBLog.i("APPMAN", "standby ready (" + chunk.getOrigin() + "): " + chunk.bboxString());
            traceChunk("standby", chunk);
            return;
        }

        GBLog.i("APPMAN", "prepared chunk not needed: " + chunk.bboxString());
        traceChunk("prep-unneeded", chunk);
        discard(chunk);
    }

    // A download came back.
    private void onChunkDownloaded(ChunkDownloader.Result result) {

        _downloader.resultTaken();

        if (result.chunk == null) {
            // Nothing to do but say it: the supervisor asks again DOWNLOAD_RETRY_TICKS later if it
            // still needs a chunk (see _downloadRetryNotBeforeMs).
            _downloadRetryNotBeforeMs = SystemClock.uptimeMillis() + DOWNLOAD_RETRY_TICKS * SUPERVISOR_TICK_MS;

            _writeLock.lock();
            try { _countFailures++; } finally { _writeLock.unlock(); }
            notifyPropertyChanged(BR.chunkCountsString);
            GBLog.e("APPMAN", "chunk download failed: " + result.error);
            traceChunk("dl-failed " + rootMsg(result.error), null);
            ViewsController.notifyError("Chunk load failed: " + rootMsg(result.error));
            return;
        }

        Chunk current;
        double lat;
        double lon;

        _readLock.lock();
        try {
            current = _currentChunk;
            GPSFix p = predictedCoordonateRaw();
            lat = p.getLatitude();
            lon = p.getLongitude();
        } finally {
            _readLock.unlock();
        }

        Chunk chunk = result.chunk;
        boolean manual = (result.request.reloadOrigin == RELOAD_MANUAL);

        boolean covers = chunk.containsRaw(lat, lon);
        boolean useful = (current == null)
                || current.isIncomplete()
                || !current.coordonateInChunk(lat, lon)
                || manual;

        if (!covers || !useful) {
            // Not shown, and not lost either: it went into the cache while it downloaded.
            GBLog.i("APPMAN", "downloaded chunk not shown (" + (covers ? "not needed" : "position left it")
                    + "): " + chunk.bboxString());
            traceChunk("dl-unused", chunk);
            discard(chunk);
            return;
        }

        // It may have arrived late, the position already in its download margin: shown anyway,
        // since it covers the fix, but flagged so that the next one is fetched.
        if (!chunk.coordonateInChunk(lat, lon)) {
            chunk.setIncomplete(true);
            chunk.setOrigin("online-late");
        }

        publishChunk(chunk, current, lat, lon);

        // A standby prepared around an older position has nothing left to offer.
        if (!chunk.isIncomplete())
            dropStandby();

        if (manual)
            ViewsController.notifyInfo("Chunk reloaded!");
    }

    // Correction (m) to add to a WGS-84 altitude to get the mean sea level altitude at lat/lon, or
    // null on failure. Offline: AltitudeConverter reads a geoid map shipped with the system. A point at
    // WGS-84 altitude 0 has a sea level altitude of minus the geoid height, which is the correction
    // itself (about -50 m in France: the geoid lies above the ellipsoid).
    // Reads system assets: the docs ask for a worker thread, nothing enforces it at run time.
    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static Double altitudeCorrectionAt(Context context, double lat, double lon) {
        if (context == null) {
            return null;
        }
        try {
            Location location = new Location("");
            location.setLatitude(lat);
            location.setLongitude(lon);
            location.setAltitude(0.0);
            new AltitudeConverter().addMslAltitudeToLocation(context, location);
            if (location.hasMslAltitude()) {
                return location.getMslAltitudeMeters();
            }
        } catch (Exception e) {
            GBLog.e(TAG, "altitudeCorrectionAt: " + e);
        }
        return null;
    }

    // Puts a chunk on screen.
    private void publishChunk(Chunk newChunk, Chunk oldChunk, double lat, double lon) {

        // Shared marks (personal + proximity clones) are re-pointed AND re-scaled on the RENDER
        // thread, at the exact instant the map switches (GameSurface.update ->
        // switchAndReContextMarksToChunk(map)), so they stay in the same frame as the camera.
        if (oldChunk != null)
            newChunk.setGameSurface(oldChunk.getGameSurface());

        // #59: alarms already muted by hand on the same marks carry over, done here (loading side,
        // both chunks at hand) rather than at the switch, so it costs the render thread nothing.
        newChunk.inheritMutedMarksFrom(oldChunk);

        boolean inLand = newChunk.coordonateInLand(lat, lon);

        // Constant enough over a 2 km chunk (the geoid moves a few metres per hundreds of km).
        Double altitudeCorrection = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Context context;
            _readLock.lock();
            try {
                context = _context;
            } finally {
                _readLock.unlock();
            }
            altitudeCorrection = altitudeCorrectionAt(context, lat, lon);
        }

        _writeLock.lock();
        try {
            _currentChunk = newChunk;
            _inLand = inLand;
            if (altitudeCorrection != null) {
                _altitudeCorrection = altitudeCorrection;
            }
        } finally {
            _writeLock.unlock();
        }

        _writeLock.lock();
        try { _countPublished++; } finally { _writeLock.unlock(); }
        notifyPropertyChanged(BR.chunkCountsString);

        // One line per chunk put on screen: what it is and where, against the position -- this is
        // what the dead-zone test drive is analysed from.
        GBLog.i("APPMAN", String.format(Locale.ROOT, "PUBLISH %s bbox=%s pos=%.6f,%.6f",
                newChunk.getOrigin(), newChunk.bboxString(), lon, lat));
        traceChunk("publish", newChunk);

        notifyPropertyChanged(BR.currentChunk);
        notifyPropertyChanged(BR.whereIAmString);
    }

    // Message de la cause la plus profonde (les echecs de chunk sont ré-emballés en cascade :
    // "dlChunk failed" -> ... -> "server HTTP 429" / "no data" / UnknownHostException). Sert au footer.
    private static String rootMsg(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null)
            c = c.getCause();
        String m = c.getMessage();
        return (m != null && !m.isEmpty()) ? m : c.getClass().getSimpleName();
    }

    Thread myThreadLand = new Thread() {
        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void run() {
            AppManager _am = AppManager.getInstance();
            try {
                boolean bOK = false;

                while(!bOK) {
                    try {

                        _readLock.lock();
                        try {
                            //bOK = _initializedDB;
                            bOK = _mapsLoaded && _marksLoaded;
                        } finally {
                            _readLock.unlock();
                        }

                        if (!bOK)
                            Thread.sleep(100);

                    } catch(InterruptedException e){
                        e.printStackTrace();
                    }
                }

                while (true) {
                    Chunk currentChunk = null;
                    double latitude = 0;
                    double longitude = 0;
                    Double altitude = null;

                    sleep(1000);

                    _readLock.lock();
                    try {
                        currentChunk = _currentChunk;
                        GPSFix p = predictedCoordonateRaw();
                        latitude = p.getLatitude();
                        longitude = p.getLongitude();
                        altitude = p.getAltitude();
                    } finally {
                        _readLock.unlock();
                    }

                    if (currentChunk != null) {
                        boolean inLand = currentChunk.coordonateInLand(latitude, longitude);

                        _writeLock.lock();
                        try {
                            _inLand = inLand;
                        } finally {
                            _writeLock.unlock();
                        }

                        notifyPropertyChanged(BR.whereIAmString);
                    }

                    // GPS perdu. La vitesse n'est recalculee que dans setLocation(), appelee par le seul
                    // onLocationChanged : sans nouveau fix, la derniere valeur resterait affichee
                    // indefiniment (constate en entrant dans un batiment, vitesse figee a 1.9 km/h). Ce
                    // controle vit ICI, dans le thread 1 Hz, et non dans setLocation : ni le calcul de
                    // vitesse ni le detecteur d'arret ne sont touches.
                    boolean fixStale;
                    boolean staleChanged;

                    _writeLock.lock();
                    try {
                        fixStale     = _hasFix && ((System.currentTimeMillis() - _lastKnownWallMs) > FIX_STALE_MS);
                        staleChanged = (fixStale != _fixStale);
                        _fixStale    = fixStale;

                        if (fixStale) {
                            _instantSpeed = 0.0;
                            _averageSpeed = 0.0;
                            _speedInKm    = 0.0;
                            _speedInMile  = 0.0;
                            _speedInKnot  = 0.0;
                            _turnDir      = 0;
                            _turnRateDegS = 0.0;
                            _motionStatus = "No GPS";
                        }
                    } finally {
                        _writeLock.unlock();
                    }

                    if (staleChanged) {
                        GBLog.d("APPMAN", fixStale ? "no fix for " + (FIX_STALE_MS / 1000) + "s, speed forced to 0"
                                                   : "fix back");
                        notifyPropertyChanged(BR.speedString);
                        notifyPropertyChanged(BR.speedKmString);
                        notifyPropertyChanged(BR.speedKm);
                        notifyPropertyChanged(BR.motionStatus);
                    }

                    // Mode de deplacement (cf. TravelMode) : cadence ici a 1 Hz. Volontairement HORS du
                    // bloc ci-dessus, pour que les temporisations continuent de tourner meme sans chunk
                    // charge : le milieu retombe alors sur la derniere valeur connue de _inLand.
                    int     modeBefore;
                    int     modeAfter;
                    boolean modeInLand;
                    double  modeSpeed;
                    boolean modeStopped;

                    _writeLock.lock();
                    try {
                        modeBefore  = _travelMode.getMode();
                        modeInLand  = _inLand;
                        modeSpeed   = _speedInKm;
                        modeStopped = _averageSpeed < SPEED_EPS_KMH;
                        _travelMode.update(SystemClock.elapsedRealtime(), modeInLand, modeSpeed, modeStopped);
                        modeAfter   = _travelMode.getMode();
                    } finally {
                        _writeLock.unlock();
                    }

                    // Trace de mise au point, gouvernee par le parametre "Debug mode" : le libelle n'est
                    // formate qu'au moment ou GBLog.d le laisse passer, et jamais sous le verrou.
                    if (modeAfter != modeBefore)
                        GBLog.d("TRAVEL", String.format(Locale.US, "mode %s -> %s (%s %.1f km/h%s)",
                                TravelMode.name(modeBefore), TravelMode.name(modeAfter),
                                modeInLand ? "land" : "water", modeSpeed, modeStopped ? " stopped" : ""));

                    // Proximity list, in three steps so that NOTHING but copies runs under the lock.
                    // It used to do all of it under the write lock -- distances, the clones (whose
                    // constructor locked), the sorts, a dozen notifications per mark -- and that is
                    // how it deadlocked with the mark editor on 01/10/2026.
                    //
                    // 1. Under the lock: a snapshot of the marks and of the parameters, nothing else.
                    List<PersonalMark> marks;
                    double proxKm;
                    int elevMinM;
                    int warnDistM;
                    int warnEtaSec;
                    double spdKmh;
                    double directionAngle;
                    boolean stoppedNow;
                    long drawVersion;

                    _readLock.lock();
                    try {
                        marks = new ArrayList<PersonalMark>(_marks);
                        drawVersion = _marksVersion;

                        proxKm = _proxMeters / 1000.0;
                        elevMinM = _elevMinMeters;

                        // #7 : parametres du test d'alarme, releves une fois pour toute la boucle.
                        warnDistM = _warnDistM;
                        warnEtaSec = _warnEtaSec;
                        spdKmh = _speedInKm;
                        directionAngle = directionHeadingNoLock();   // cap vue when stopped
                        stoppedNow = (_averageSpeed < SPEED_EPS_KMH);
                    } finally {
                        _readLock.unlock();
                    }

                    // 2. No lock held: distances, arrows, alarm state, clones, sort.
                    List<PersonalMark> lm = new ArrayList<PersonalMark>();

                    for (PersonalMark mark : marks) {
                        // Straight line when both altitudes are known (shown, proximity radius,
                        // alarms); the slope angle needs the ground distance.
                        double groundKm = Chunk.measureKm(mark.getLatitude(), mark.getLongitude(), latitude, longitude);
                        double distance = Chunk.measureKm(mark.getLatitude(), mark.getLongitude(), mark.getAltitude(),
                                latitude, longitude, altitude);
                        mark.setDistance(distance);
                        mark.setElevationFrom(altitude, groundKm, elevMinM);

                        double cap = Chunk.initialBearing(latitude, longitude, mark.getLatitude(), mark.getLongitude());
                        // Course-up / heading-up : la flèche montre le cap RELATIF au cap actuel
                        // du bateau (haut de la flèche = droit devant). directionAngle releve
                        // ci-dessus sous le verrou.
                        //
                        // #54 : les DEUX termes sont en cap VRAI, et doivent le rester. Un gisement
                        // ne depend pas du nord choisi tant que ses deux termes partagent le meme,
                        // et la deviation, elle, ne s'annule pas dans la soustraction : la passer
                        // ici fausserait la fleche de plusieurs degres. Voir la regle detaillee
                        // au-dessus de magneticFromTrueNoLock().
                        mark.setHeadingFromBearing(cap - directionAngle); // position courante -> mark, relatif au cap

                        // #7 : marque EN TRAIN de signaler (alarme activee ET dans les criteres
                        // WarnDist/WarnETA), a distinguer d'une marque simplement armee.
                        boolean alarming = mark.isAlarmed()
                                && shouldAlarmWithoutLock(distance, warnDistM, warnEtaSec, spdKmh);
                        mark.setAlarming(alarming);

                        // Within the (tunable) radius OR selected -> always show selected marks.
                        // #7 : ... et une marque qui signale y est TOUJOURS, elle aussi. Le rayon
                        // de cette liste (ProxDist) n'a rien a voir avec les criteres d'alarme :
                        // sans cela une marque pourrait sonner sans figurer dans la liste.
                        if (distance < proxKm || mark.isSelected() || alarming) {
                            lm.add(new PersonalMark(mark)); // Clone The Mark
                        } else {
                            // #7 : sortie de la liste de proximite = l'alarme peut resonner la
                            // prochaine fois. C'est le seul point de remise a zero du mute, et il
                            // est sur precisement parce qu'une marque qui signale ne sort jamais
                            // de cette liste (condition ci-dessus) : on ne peut donc pas relancer
                            // le son sous le nez de l'utilisateur qui vient de le couper.
                            mark.setMuted(false);
                        }
                    }

                    Collections.sort(lm, new Comparator<PersonalMark>() {
                        @Override
                        public int compare(PersonalMark pm1, PersonalMark pm2) {
                            if (pm1.getDistance() > pm2.getDistance())
                                return 1;
                            else if (pm1.getDistance() < pm2.getDistance())
                                return -1;
                            else {
                                String t1 = "";
                                String t2 = "";

                                if (pm1.getTitle() != null)
                                    t1 = pm1.getTitle();

                                if (pm2.getTitle() != null)
                                    t2 = pm2.getTitle();

                                return t1.compareTo(t2);
                            }
                        }
                    });

                    // 3. Under the lock: reorder the live lists, QUIETLY (no observer is called), and
                    // only write down which proximity clones need refreshing. The refresh and every
                    // notification come after the unlock.
                    boolean addedOrDeleted = false;
                    List<PersonalMark> refreshed = new ArrayList<PersonalMark>();
                    List<PersonalMark> refreshedFrom = new ArrayList<PersonalMark>();

                    _writeLock.lock();
                    try {
                        //Collections.sort(_marks, new Comparator<PersonalMark>() {
                        // #7 : regle commune a cette page et a celle de la recherche, tenue dans
                        // PersonalMark.compareForList. La liste de proximite, elle, garde son tri par
                        // pure distance. The live list is sorted here rather than the snapshot: a mark
                        // may have been added or removed meanwhile.
                        _marks.sortQuietly(new Comparator<PersonalMark>() {
                            @Override
                            public int compare(PersonalMark pm1, PersonalMark pm2) {
                                return PersonalMark.compareForList(
                                        pm1.isAlarming(), pm1.isSelected(), pm1.getDistance(), pm1.getTitle(),
                                        pm2.isAlarming(), pm2.isSelected(), pm2.getDistance(), pm2.getTitle());
                            }
                        });

                        int maxIdx = Math.max(_proximityMarks.size(), lm.size() );
                        int i = 0;
                        //for( int i=0; i < maxIdx; i++) {
                        while( i < maxIdx ) {
                            if( i > _proximityMarks.size()-1 ) {
                                _proximityMarks.addQuietly( lm.get(i) );
                                addedOrDeleted = true;
                                maxIdx = Math.max(_proximityMarks.size(), lm.size() );
                                i++;
                            } else if( i > lm.size()-1 ) {
                                _proximityMarks.remove(i);
                                addedOrDeleted = true;
                                maxIdx = Math.max(_proximityMarks.size(), lm.size() );
                            } else {
                                refreshed.add(_proximityMarks.get(i));
                                refreshedFrom.add(lm.get(i).getCloneFrom());
                                i++;
                            }
                        }
                    } finally {
                        _writeLock.unlock();
                    }

                    // #97 : the alarm states are known now, the draw order can follow them.
                    sortAndPublishDrawMarks(marks, drawVersion);

                    _marks.notifyObservers();

                    for (int k = 0; k < refreshed.size(); k++) {
                        refreshed.get(k).setCloneFromWithUpdate(refreshedFrom.get(k));
                    }

                    if( addedOrDeleted ) {
                        _proximityMarks.notifyObservers();
                        notifyPropertyChanged(BR.proximityMarks);
                    }

                    // #94: when stopped the bottom-right heading follows the cap vue, which changes
                    // without any fix (and there is no fix at all with "No GPS"): refresh it here,
                    // at this loop's 1 s pace, like the arrows.
                    if( stoppedNow )
                        notifyPropertyChanged(BR.headingDisplayString);

                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

    // Corps du thread "liste des maps" : attend le chargement DB, puis enregistre les maps deja
    // presentes sur le stockage (scan local) et interroge le manifest serveur, chaque etape avec
    // retry. Extrait en Runnable pour pouvoir etre RELANCE (voir relaunchListMapThread / #29).
    private final Runnable _listMapWork = new Runnable() {
        @Override
        public void run() {
        boolean bOK = false;

            while(!bOK) {
                try {

                    _readLock.lock();
                    try {
                        //bOK = _initializedDB;
                        bOK = _mapsLoaded;
                    } finally {
                        _readLock.unlock();
                    }

                    if (!bOK)
                        Thread.sleep(100);

                } catch(InterruptedException e){
                    e.printStackTrace();
                }
            }

            // Register map DBs already present on storage (works without the dead server).
            bOK = false;
            while(!bOK) {
                try {

                    bOK = scanLocalMapsInfos();
                    if (!bOK)
                        Thread.sleep(5000);

                } catch(InterruptedException e){
                    e.printStackTrace();
                }
            }

            bOK = false;
            while(!bOK) {
                try {

                    bOK = downloadMapsInfos();
                    if (!bOK)
                        Thread.sleep(5000);

                } catch(InterruptedException e){
                    e.printStackTrace();
                }
            }
        }
    };

    private Thread _listMapThread = null;

    // (Re)lance le thread ci-dessus s'il n'est pas deja en cours. Appele au demarrage
    // (startAllThreadsOnce) et apres un delete de map (#29), pour que la map redevienne
    // telechargeable sans redemarrer l'app. Si un thread precedent tourne encore (retries), on le
    // laisse finir : il refera scan + query tout seul.
    private void relaunchListMapThread() {
        _writeLock.lock();
        try {
            if (_listMapThread != null && _listMapThread.isAlive())
                return;
            _listMapThread = new Thread(_listMapWork);
            _listMapThread.start();
        } finally {
            _writeLock.unlock();
        }
    }

    private AppManager() {
        // Constructor hidden because this is a singleton
        this._fixes = new GPSFix[NB_SWAP_VALUES];

        for (int i = 0; i < this.NB_SWAP_VALUES; i++) {
            this._fixes[i] = new GPSFix();   // tout a 0 (lat/lon/alt/accuracy/temps)
        }

        this._maps = new ArrayList<MapInfo>();
        //this._marks = new ArrayList<PersonalMark>();
        this._marks = new ArrayListObservable<PersonalMark>();
        this._marks.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(Observable sender, int propertyId) {

                if( propertyId == BR._all ) {
                    GBLog.d(getClass().getName(), "marks changed");
                    notifyPropertyChanged(BR.marks);
                }

            }
        });

        this._proximityMarks = new ArrayListObservable<PersonalMark>();
        this._proximityMarks.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(Observable sender, int propertyId) {

                if( propertyId == BR._all ) {
                    GBLog.d(getClass().getName(), "proximityMarks changed");

                    // that is make in antoher thread for global modifies
                    //notifyPropertyChanged(BR.proximityMarks);
                }

            }
        });

        // NB: threads are NOT started here anymore. They start in startAllThreadsOnce(),
        // called AFTER loadPersistedState() restores the last position -- otherwise the
        // chunk loader fires immediately at 0,0 (predictedCoordonateRaw before restore).

        /*
        _maps = Maps.getInstance();
        _maps.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(Observable sender, int propertyId) {

                if( propertyId == BR.maps ) {
                    GBLog.d(getClass().getName(), "maps changed");
                    notifyPropertyChanged(BR.maps);
                }

            }
        });
         */

    }

    public static AppManager getInstance(Context context) {
        return instanceFor(context);
    }

    // Lock-free once the instance exists. It is called everywhere, several times per frame from
    // draw(), and from the PersonalMark constructor, where no lock may be taken. It used to take
    // the exclusive write lock on every call, so each caller waited for every reader in the app.
    public static AppManager getInstance() {
        AppManager instance = _instance;

        if (instance != null)
            return instance;

        return instanceFor(null);
    }

    private static AppManager instanceFor(Context context) {
        // Return the instance

        AppManager instance;
        boolean contextChanged = false;
        _writeLock.lock();
        try {
            if (_instance == null) {
                _instance = new AppManager();
            }
            instance = _instance; // capture under the lock; don't touch the static field after unlock

            if (context != null) {
                contextChanged = instance.setContextWithoutLock(context);
            }
        } finally {
            // To unlock the acquired read thread
            _writeLock.unlock();
        }

        // When the context (re)appears, load the persisted settings outside the lock, passing the
        // context explicitly (so we never read _context without a lock). It locks + notifies itself.
        // Threads start only AFTER the position is restored, and only once.
        // The mark icon sheets are decoded here, once, before any mark exists: the PersonalMark
        // constructor no longer loads them (no lock, no opaque call in a constructor).
        if (contextChanged) {
            PersonalMark.loadBitmaps(context);
            instance.loadPersistedState(context);
            instance.startAllThreadsOnce();
        }

        return instance;
    }

    private boolean _threadsStarted = false;

    // Start the background threads exactly once, and only AFTER the last position has been
    // restored (loadPersistedState) so the chunk loader doesn't fire at 0,0 on launch.
    private void startAllThreadsOnce() {
        _writeLock.lock();
        try {
            if (_threadsStarted) return;
            _threadsStarted = true;
        } finally {
            _writeLock.unlock();
        }
        relaunchListMapThread();
        myThread.start();
        myThreadLand.start();
        // #86: the cache housekeeping thread starts with the others.
        ChunkCacheThread.start();
        // #87: the chunk downloader, which the supervisor loop (myThread) hands its requests to.
        _downloader.start();
        _preparer.start();
        startUiLagWatch();
        // #67 : le thread de recherche de lieu part avec les autres, et pas a la premiere recherche :
        // c'est ce qui rend son arret maitrisable (MainActivity.onDestroy, quand on quitte pour de bon).
        GeoSearch.start();
        // #94: the motion sensor's callback thread.
        MotionSensor.startThread();
    }

    // Caller MUST already hold _writeLock (getInstance() and setContext() do) — hence the
    // "WithoutLock" name: it only sets the field, never locks/notifies. Returns true when the context
    // actually changed, so the caller can (re)load the persisted state (passing the context in).
    public boolean setContextWithoutLock(Context context) {
        if( this._context != context ) {
            this._context = context;
            return true;
        }
        return false;
    }

    // --- Persisted settings (SharedPreferences) -------------------------------------------------

    // The context is always passed in (never read from _context here), so these need no lock to
    // reach the prefs — the caller hands over a context it already holds safely.
    // Chart datum of a tide grid cell, kept between runs.
    //
    // It is the only slow part of preparing a tide page: a walk of the whole 18.6 year nodal
    // cycle looking for the lowest level the tide ever reaches there. That level is a
    // property of the place and never changes, so it is worked out once and kept.
    //
    // The key is the 1/8 degree cell of the grid itself, which is the granularity the data
    // actually has: a finer key would only store interpolations of the same four numbers,
    // a coarser one would mix cells holding different data. The size of the grid file goes
    // into the key as well, so a new grid quietly retires the old values.
    private static final String KEY_TIDE_DATUM = "tide_datum_";

    private static final double TIDE_CELL_DEGREES = 0.125;

    public Double getTideDatum(Context context, String gridPath, double latitude, double longitude) {
        SharedPreferences p = prefs(context);
        if (p == null) return null;

        String key = tideDatumKey(gridPath, latitude, longitude);
        if (!p.contains(key)) return null;

        return (double) p.getFloat(key, 0f);
    }

    public void putTideDatum(Context context, String gridPath, double latitude, double longitude,
                             double datumMetres) {
        SharedPreferences p = prefs(context);
        if (p == null) return;

        p.edit().putFloat(tideDatumKey(gridPath, latitude, longitude), (float) datumMetres).apply();
    }

    private String tideDatumKey(String gridPath, double latitude, double longitude) {
        long size = (gridPath == null) ? 0L : new File(gridPath).length();

        long row = (long) Math.floor(latitude / TIDE_CELL_DEGREES);
        long column = (long) Math.floor(longitude / TIDE_CELL_DEGREES);

        return String.format(Locale.ROOT, "%s%d_%d_%d", KEY_TIDE_DATUM, size, row, column);
    }

    // The last place whose tides were actually shown.
    //
    // Separate from the datum cache above: that one answers "what is the chart datum of this
    // cell", and holds every cell ever visited; this one answers "which of them was the last
    // one looked at", which is a single value. Keeping it apart also means it survives a new
    // grid, where the datum keys are retired by the file size in their name.
    //
    // The position is kept as raw double bits rather than a float: a float latitude is only
    // good to a few metres, and these coordinates are handed straight back to the model.
    private static final String KEY_TIDE_LAST = "tide_last_";

    public TidePlace getTideLastPlace(Context context) {
        SharedPreferences p = prefs(context);
        if (p == null) return null;

        if (!p.contains(KEY_TIDE_LAST + "latitude")) return null;

        double latitude = Double.longBitsToDouble(p.getLong(KEY_TIDE_LAST + "latitude", 0L));
        double longitude = Double.longBitsToDouble(p.getLong(KEY_TIDE_LAST + "longitude", 0L));

        return new TidePlace(latitude, longitude,
                p.getString(KEY_TIDE_LAST + "name", null),
                p.getLong(KEY_TIDE_LAST + "seen", 0L));
    }

    /** @param name what the place was found under, or null when it is where the user stood. */
    public void putTideLastPlace(Context context, double latitude, double longitude, String name) {
        SharedPreferences p = prefs(context);
        if (p == null) return;

        p.edit()
                .putLong(KEY_TIDE_LAST + "latitude", Double.doubleToRawLongBits(latitude))
                .putLong(KEY_TIDE_LAST + "longitude", Double.doubleToRawLongBits(longitude))
                .putString(KEY_TIDE_LAST + "name", name)
                .putLong(KEY_TIDE_LAST + "seen", System.currentTimeMillis() / 1000L)
                .apply();
    }

    /*
     * The same thing for the sun and moon page, under its own keys.
     *
     * Deliberately NOT shared with the tides: a harbour worth checking the water at and a
     * hilltop worth checking the sunset from are rarely the same place, and one page
     * overwriting the other's memory would be a quiet nuisance. The holder is reused
     * because it holds nothing tidal; only the drawer differs.
     */
    private static final String KEY_SKY_LAST = "sky_last_";

    public TidePlace getSkyLastPlace(Context context) {
        SharedPreferences p = prefs(context);
        if (p == null) return null;

        if (!p.contains(KEY_SKY_LAST + "latitude")) return null;

        double latitude = Double.longBitsToDouble(p.getLong(KEY_SKY_LAST + "latitude", 0L));
        double longitude = Double.longBitsToDouble(p.getLong(KEY_SKY_LAST + "longitude", 0L));

        return new TidePlace(latitude, longitude,
                p.getString(KEY_SKY_LAST + "name", null),
                p.getLong(KEY_SKY_LAST + "seen", 0L));
    }

    /** @param name what the place was found under, or null when it is where the user stood. */
    public void putSkyLastPlace(Context context, double latitude, double longitude, String name) {
        SharedPreferences p = prefs(context);
        if (p == null) return;

        p.edit()
                .putLong(KEY_SKY_LAST + "latitude", Double.doubleToRawLongBits(latitude))
                .putLong(KEY_SKY_LAST + "longitude", Double.doubleToRawLongBits(longitude))
                .putString(KEY_SKY_LAST + "name", name)
                .putLong(KEY_SKY_LAST + "seen", System.currentTimeMillis() / 1000L)
                .apply();
    }

    /*
     * Which of the two the page opens on: the position of the moment, or the place that was
     * last chosen by hand.
     *
     * Kept apart from the place itself, which has to survive either way: the place is what
     * the picker offers as a shortcut, this is what the page does when nobody asks. A user
     * who has deliberately put the page on a harbour expects to find it there the next time
     * rather than back under his feet, and a user who has deliberately put it back on his
     * own position expects it to follow him again.
     *
     * Defaults to following the user, which is what the page did before it could be sent
     * anywhere else.
     */
    private static final String KEY_TIDE_FOLLOWS = "tide_follows_user";
    private static final String KEY_SKY_FOLLOWS = "sky_follows_user";

    public boolean getTideFollowsUser(Context context) {
        SharedPreferences p = prefs(context);
        return (p == null) || p.getBoolean(KEY_TIDE_FOLLOWS, true);
    }

    public void putTideFollowsUser(Context context, boolean follows) {
        SharedPreferences p = prefs(context);
        if (p == null) return;

        p.edit().putBoolean(KEY_TIDE_FOLLOWS, follows).apply();
    }

    public boolean getSkyFollowsUser(Context context) {
        SharedPreferences p = prefs(context);
        return (p == null) || p.getBoolean(KEY_SKY_FOLLOWS, true);
    }

    public void putSkyFollowsUser(Context context, boolean follows) {
        SharedPreferences p = prefs(context);
        if (p == null) return;

        p.edit().putBoolean(KEY_SKY_FOLLOWS, follows).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return (context == null) ? null : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void loadPersistedState(Context context) {
        SharedPreferences p = prefs(context);
        if (p == null) return;

        _writeLock.lock();
        try {
            _speedUnit = p.getInt(KEY_SPEED_UNIT, 0);
            _debugMode = p.getBoolean(KEY_DEBUG_MODE, false);
            _streamerMode = p.getBoolean(KEY_STREAMER_MODE, false);
            StreamerMode.enabled = _streamerMode;
            GBLog.setDebug(_debugMode);   // wire the log level to the persisted debug mode
            _singleTarget = p.getBoolean(KEY_SINGLE_TARGET, false);   // #52 : mode cible unique persiste
            _debugParam = p.getInt(KEY_DEBUG_PARAM, 0);
            _bestScore = p.getInt(KEY_BEST_SCORE, 0);
            _easterEggs = p.getInt(KEY_EASTER_EGGS, 0);
            _anticipationMs = p.getLong(KEY_ANTICIP_MS, 1500);
            _headingAnticipMs = p.getLong(KEY_HEADING_ANTICIP_MS, HEADING_ANTICIP_AUTO);
            _speedAvgMs = p.getLong(KEY_SPEED_AVG_MS, SPDWIN_AUTO);
            _altAvgMs = p.getLong(KEY_ALT_AVG_MS, ALTWIN_AUTO);
            _traceOn = p.getInt(KEY_TRACE_ON, 1) == 1;
            _uncertPoints = p.getInt(KEY_UNCERT_POINTS, NPTS_DEFAULT);
            _overlayOn = p.getInt(KEY_OVERLAY_ON, 0) == 1;
            _magDeclManual = p.getFloat(KEY_MAG_DECL, 0f);
            _declAutoOn = p.getInt(KEY_DECL_AUTO, 1) == 1;
            _headingMagnetic = p.getInt(KEY_HEADING_MAG, 0) == 1;
            _proxMeters = p.getInt(KEY_PROX_METERS, 2000);
            // Snap an older radius (100 m steps, up to 5000 m) onto the 500 m grid of the spinner.
            _proxMeters = ((_proxMeters + PROXDIST_STEP / 2) / PROXDIST_STEP) * PROXDIST_STEP;
            if (_proxMeters < PROXDIST_MIN) _proxMeters = PROXDIST_MIN;
            if (_proxMeters > PROXDIST_MAX) _proxMeters = PROXDIST_MAX;
            _elevMinMeters = p.getInt(KEY_ELEV_MIN_METERS, ELEVMIN_DEFAULT);
            _searchRadiusKm = p.getInt(KEY_SEARCH_RADIUS, 20);
            _searchLimit = p.getInt(KEY_SEARCH_LIMIT, GeoSearch.SERVER_MAX_RESULTS);
            _samePlaceM = p.getInt(KEY_SAME_PLACE, 10);
            _samePlaceTitle = p.getInt(KEY_SAME_PLACE_TITLE, 0) == 1;
            _zoneTargetWidthM = p.getFloat(KEY_ZONE_WIDTH, 100f);
            _coordFmt = p.getInt(KEY_COORD_FMT, COORDFMT_DM);
            _smallUnit = p.getInt(KEY_SMALL_UNIT, SMALLUNIT_FT);
            _warnDistM = p.getInt(KEY_WARN_DIST, 300);
            _warnEtaSec = p.getInt(KEY_WARN_ETA, 30);
            // #68 : une adresse persistee est repassee par le meme filtre que la saisie. Une
            // preference bricolee a la main hors de l'application ne doit pas pouvoir empecher le
            // demarrage : ce qui ne passe pas le filtre retombe sur le defaut.
            _overpassBaseUrl = normalizeOverpassBase(p.getString(KEY_OVERPASS_URL, OVERPASS_BASE_DEFAULT));
            if (_overpassBaseUrl == null) _overpassBaseUrl = OVERPASS_BASE_DEFAULT;
            _mapsBaseUrl = normalizeMapsBase(p.getString(KEY_MAPS_URL, MAPS_BASE_DEFAULT));
            if (_mapsBaseUrl == null) _mapsBaseUrl = MAPS_BASE_DEFAULT;
            _nominatimBaseUrl = normalizeNominatimBase(p.getString(KEY_NOMINATIM_URL, NOMINATIM_BASE_DEFAULT));
            if (_nominatimBaseUrl == null) _nominatimBaseUrl = NOMINATIM_BASE_DEFAULT;
            // #54 : les compas. Une entree illisible retombe sur des corrections nulles plutot que
            // de faire echouer le chargement du reste des reglages.
            _compasses.clear();
            int nbCompasses = p.getInt(KEY_COMPASS_COUNT, 0);
            for (int c = 0; c < nbCompasses; c++) {
                Compass compass = new Compass(p.getString(String.format(Locale.US, KEY_COMPASS_NAME, c), COMPASS_DEFAULT_NAME));
                compass.parseDeviations(p.getString(String.format(Locale.US, KEY_COMPASS_DEV, c), ""));
                _compasses.add(compass);
            }
            _compassIndex = p.getInt(KEY_COMPASS_SELECTED, -1);
            if ((_compassIndex < 0) || (_compassIndex >= _compasses.size()))
                _compassIndex = -1;
            for (int c = 0; c < _compasses.size(); c++)
                _compasses.get(c).setInUse(c == _compassIndex);
            for (int c = 0; c < AvatarCatalog.CAT_COUNT; c++)
                _avatarNames[c] = p.getString(KEY_AVATAR[c], "");
            _sndRadar = p.getString(KEY_SND_RADAR, SND_RADAR_DEFAUT);
            _sndMark  = p.getString(KEY_SND_MARK,  SND_MARK_DEFAUT);
            _sndZone  = p.getString(KEY_SND_ZONE,  SND_ZONE_DEFAUT);
            _radarAlarmOn = p.getBoolean(KEY_RADAR_ON, true);
            _lowResourceMode = p.getBoolean(KEY_LOW_RES, false);
            _preferSdStorage = p.getBoolean(KEY_PREFER_SD, false);
            _cacheSizeStep = p.getInt(KEY_CACHE_SIZE, CACHE_SIZE_MEDIUM);
            _tideReachStep = p.getInt(KEY_TIDE_REACH, TIDE_REACH_DEFAULT_POS);
            if( (_cacheSizeStep < 0) || (_cacheSizeStep >= CACHE_SIZE_BYTES.length) )
                _cacheSizeStep = CACHE_SIZE_MEDIUM;
            // Restore the last known position so the app doesn't open at 0,0. Speed/heading stay 0
            // until a real fix arrives, so the marker simply sits on the last spot.
            String slat = p.getString(KEY_LAST_LAT, null);
            String slon = p.getString(KEY_LAST_LON, null);
            if (slat != null && slon != null) {
                try {
                    double lat = Double.parseDouble(slat);
                    double lon = Double.parseDouble(slon);
                    _lastKnownCoordonate = new GPSFix(lon, lat);
                    _lastKnownWallMs = System.currentTimeMillis();
                    _hasFix = true;
                    _averageSpeed = 0; _instantSpeed = 0; _speedInKm = 0;
                    if (_fixes != null) {
                        _fixes[0].setLatitude(lat);
                        _fixes[0].setLongitude(lon);
                    }
                } catch (NumberFormatException ignored) { }
            }
            // Restore land/sea mode too, so we don't always open in boat mode. Default
            // false (boat): a first launch sits at 0,0, which is open sea.
            _inLand = p.getBoolean(KEY_LAST_INLAND, false);
        } finally {
            _writeLock.unlock();
        }

        notifyPropertyChanged(BR.latitudeString);
        notifyPropertyChanged(BR.longitudeString);
        notifyPropertyChanged(BR.speedKmString);
        notifyPropertyChanged(BR.anticipationString);
        notifyPropertyChanged(BR.headingPredictString);
        notifyPropertyChanged(BR.spdWinString);
        notifyPropertyChanged(BR.zoomLabel);
        notifyPropertyChanged(BR.anticipLabel);
        notifyPropertyChanged(BR.predCapLabel);
        notifyPropertyChanged(BR.spdWinLabel);
        notifyPropertyChanged(BR.traceString);
        notifyPropertyChanged(BR.traceLabel);
        notifyPropertyChanged(BR.nptsString);
        notifyPropertyChanged(BR.nptsLabel);
        notifyPropertyChanged(BR.overlayString);
        notifyPropertyChanged(BR.overlayLabel);
        notifyPropertyChanged(BR.declString);
        notifyPropertyChanged(BR.declLabel);
        notifyPropertyChanged(BR.declSpinnerPos);
        notifyPropertyChanged(BR.declAuto);
        notifyPropertyChanged(BR.declAutoString);
        notifyPropertyChanged(BR.declAutoModelString);
        notifyPropertyChanged(BR.compassLabel);
        notifyPropertyChanged(BR.compassSelected);
        notifyPropertyChanged(BR.proxString);
        notifyPropertyChanged(BR.proxLabel);
        notifyPropertyChanged(BR.zoneWString);
        notifyPropertyChanged(BR.zoneWLabel);
        notifyPropertyChanged(BR.coordFmtString);
        notifyPropertyChanged(BR.coordFmtLabel);
        notifyPropertyChanged(BR.warnDistString);
        notifyPropertyChanged(BR.warnDistLabel);
        notifyPropertyChanged(BR.warnEtaString);
        notifyPropertyChanged(BR.warnEtaLabel);
        notifyPropertyChanged(BR.traceOn);
        notifyPropertyChanged(BR.overlayOn);
        notifyPropertyChanged(BR.anticipPos);
        notifyPropertyChanged(BR.predCapPos);
        notifyPropertyChanged(BR.spdWinPos);
        notifyPropertyChanged(BR.altWinPos);
        notifyPropertyChanged(BR.nptsPos);
        notifyPropertyChanged(BR.proxPos);
        notifyPropertyChanged(BR.elevMinPos);
        notifyPropertyChanged(BR.zoneWPos);
        notifyPropertyChanged(BR.coordFmtPos);
        notifyPropertyChanged(BR.warnDistPos);
        notifyPropertyChanged(BR.warnEtaPos);
        notifyPropertyChanged(BR.overpassUrl);
        notifyPropertyChanged(BR.overpassUrlDefault);
        notifyPropertyChanged(BR.mapsUrl);
        notifyPropertyChanged(BR.mapsUrlDefault);
        notifyPropertyChanged(BR.nominatimUrl);
        notifyPropertyChanged(BR.nominatimUrlDefault);
        notifyPropertyChanged(BR.searchRadiusPos);
        notifyPropertyChanged(BR.searchRadiusString);
        notifyPropertyChanged(BR.searchLimitPos);
        notifyPropertyChanged(BR.samePlacePos);
        notifyPropertyChanged(BR.samePlaceTitle);
        notifyPropertyChanged(BR.headingDisplayString);
        notifyPropertyChanged(BR.debugMode);
        notifyPropertyChanged(BR.streamerMode);
        for (int c = 0; c < AvatarCatalog.CAT_COUNT; c++)
            notifyAvatarChanged(c);
    }

    private void persistBoolean(Context context, String key, boolean value) {
        SharedPreferences p = prefs(context);
        if (p != null) p.edit().putBoolean(key, value).apply();
    }

    private void persistInt(Context context, String key, int value) {
        SharedPreferences p = prefs(context);
        if (p != null) p.edit().putInt(key, value).apply();
    }

    private void persistLong(Context context, String key, long value) {
        SharedPreferences p = prefs(context);
        if (p != null) p.edit().putLong(key, value).apply();
    }

    private void persistFloat(Context context, String key, float value) {
        SharedPreferences p = prefs(context);
        if (p != null) p.edit().putFloat(key, value).apply();
    }

    private void persistString(Context context, String key, String value) {
        SharedPreferences p = prefs(context);
        if (p != null) p.edit().putString(key, value).apply();
    }

    private void clearPref(Context context, String key) {
        SharedPreferences p = prefs(context);
        if (p != null) p.edit().remove(key).apply();
    }

    // Persist the current position (called periodically and on pause) so a restart reopens here.
    public void saveStateNow() {
        Context context;
        double lat, lon;
        boolean inLand;
        _readLock.lock();
        try {
            context = _context;
            lat = _lastKnownCoordonate.getLatitude();
            lon = _lastKnownCoordonate.getLongitude();
            inLand = _inLand;
        } finally {
            _readLock.unlock();
        }
        SharedPreferences p = prefs(context);
        if (p == null) return;
        if (lat != 0.0 || lon != 0.0) {
            p.edit().putString(KEY_LAST_LAT, Double.toString(lat))
                    .putString(KEY_LAST_LON, Double.toString(lon))
                    .putBoolean(KEY_LAST_INLAND, inLand).apply();
        }
    }

    public void setContext(Context context) {
        boolean contextChanged;
        _writeLock.lock();
        try {
            contextChanged = setContextWithoutLock(context);
        } finally {
            _writeLock.unlock();
        }
        if (contextChanged) {
            loadPersistedState(context);
            startAllThreadsOnce();
        }
    }

    public Context getContext() {
        Context contex = null;

        _readLock.lock();
        try {
            contex = _context;
        } finally {
            _readLock.unlock();
        }

        return contex;
    }

    public AppDB getAppDB() {
        AppDB appDB;

        _readLock.lock();
        try {
            appDB = this._appDB;
        } finally {
            _readLock.unlock();
        }

        return appDB;
    }

    public boolean isInitializedDB() {
        boolean initializedDB;

        _readLock.lock();
        try {
            initializedDB = this._initializedDB;
        } finally {
            _readLock.unlock();
        }
        return initializedDB;
    }

    // Path of the installed world tide grid, or null when it is not installed. Single entry
    // point for the tide engine, the way Chunk resolves the land/sea database.
    public String getTidesGridPath() {
        return installedDataPath(MapInfo.NAME_TIDES);
    }

    // File of an installed manifest entry, found by its name. Returns null when the entry is
    // absent, not installed, or when its file has disappeared from under us.
    private String installedDataPath(String name) {

        List<MapInfo> maps;
        _readLock.lock();
        try {
            maps = this._maps;
        } finally {
            _readLock.unlock();
        }

        if (maps == null)
            return null;

        for (MapInfo mi : maps) {

            if (!name.equalsIgnoreCase(mi.getName()))
                continue;

            if (mi.getDownloadStatus() != MapInfo.TypeStatus.INSTALLED)
                continue;

            String path = mi.getDbPath();
            if (path == null || path.length() == 0)
                continue;

            File file = new File(path);
            if (file.exists())
                return file.getAbsolutePath();
        }

        return null;
    }

    // Property Maps
    @Bindable
    public List<MapInfo> getMaps() {
        List<MapInfo> maps;
        _readLock.lock();
        try {
            maps = this._maps;
        } finally {
            _readLock.unlock();
        }

        return maps;
    }

    // Deletes a map: its files on storage (the data file, plus .tmp and .info) when present,
    // then its row in the database, then refreshes the displayed list (BR.maps).
    public void deleteMap(MapInfo mi) {
        if (mi == null || mi.getName() == null || mi.getVersion() == null)
            return;
        if (!isInitializedDB())
            return;

        String name = MapInfo.safeName(mi.getName());
        long version = mi.getVersion();

        // 1. Delete the files on every storage location. The extension depends on the entry --
        // a database for the maps, a packed binary for the tide grid -- so every extension the
        // client knows is swept rather than guessing which one this entry used.
        List<File> dirs = getStorageLocations();
        if (dirs != null && name != null) {
            for (File dir : dirs) {
                for (String sub : MapInfo.SUBDIRS) {
                    for (String extension : MapInfo.INSTALL_EXTENSIONS) {
                        String base = dir.getAbsolutePath() + sub
                                + String.format(Locale.ROOT, "%s_%d%s", name, version, extension);
                        deleteFileIfExists(base);
                        deleteFileIfExists(base + ".tmp");
                        deleteFileIfExists(base + ".info");
                    }
                }
            }
        }

        // 2. Supprimer la ligne en base.
        _writeLock.lock();
        try {
            MapInfoDB miDB = new MapInfoDB(_appDB);
            miDB.delete(mi.getName(), version);
        } catch (Exception e) {
            GBLog.e("Maps", "deleteMap db err: " + e.toString());
        } finally {
            _writeLock.unlock();
        }

        // 3. Rafraîchir la liste (retrait immédiat).
        dbMapsInfos();

        // 4. #29 : relancer le thread "liste des maps" (scan local + query manifest, avec retries)
        // pour que la map redevienne téléchargeable sans redémarrer l'app.
        relaunchListMapThread();
    }

    private void deleteFileIfExists(String path) {
        try {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                boolean ok = f.delete();
                GBLog.d("Maps", String.format("deleteMap: %s -> %s", path, ok ? "deleted" : "FAILED"));
            }
        } catch (Exception e) {
            GBLog.e("Maps", "deleteMap del err: " + e.toString());
        }
    }

    // Property Maps
    @Bindable
    public List<PersonalMark> getMarks() {
        List<PersonalMark> marks;
        _readLock.lock();
        try {
            // The LIVE list, on purpose. The two list adapters are built on it once, at startup,
            // and then rely on seeing it grow: handed a copy, they stayed bound to an empty
            // snapshot taken before the marks were loaded, and both lists showed nothing for the
            // rest of the session while the map, which reads the live list every frame, kept
            // drawing everything.
            //
            // Whoever WALKS the list must call getMarksEnumerable() instead.
            marks = this._marks;
        } finally {
            _readLock.unlock();
        }

        return marks;
    }

    @Bindable
    public List<PersonalMark> getProximityMarks() {
        List<PersonalMark> marks;
        _readLock.lock();
        try {
            marks = this._proximityMarks;   // the live list, see getMarks()
        } finally {
            _readLock.unlock();
        }

        return marks;
    }

    /**
     * The marks as a SNAPSHOT, for whoever walks them.
     *
     * Writers -- import, proximity clones -- hold the write lock, but readers used to walk the
     * live list with no lock at all: the render thread caught ConcurrentModificationException
     * while iterating, nine times over a two-hour test drive at 50, 150 and 160 km/h, one lost
     * frame each. Copying under the lock is a plain array copy, short enough to sit inside it,
     * and hands the caller a list nobody can change underneath them.
     *
     * Use this one to iterate. Use getMarks() only where the LIVE list is the point, which today
     * means the two list adapters and nothing else.
     */
    public List<PersonalMark> getMarksEnumerable() {
        _readLock.lock();
        try {
            return new ArrayList<PersonalMark>(this._marks);
        } finally {
            _readLock.unlock();
        }
    }

    /** The proximity marks as a snapshot, for whoever walks them. See getMarksEnumerable(). */
    public List<PersonalMark> getProximityMarksEnumerable() {
        _readLock.lock();
        try {
            return new ArrayList<PersonalMark>(this._proximityMarks);
        } finally {
            _readLock.unlock();
        }
    }

    // Property ZOOM
    @Bindable
    public String getZoomString() {
        TypeZoom zoom;

        _readLock.lock();
        try {
            zoom = this._zoom;
        } finally {
            _readLock.unlock();
        }

        return zoom.toString();
    }

    public void zoomMore() {
        _writeLock.lock();
        try {
            TypeZoom zoom = this._zoom;

            int zoomMaxOrdinal = TypeZoom.values().length - 1;
            int zoomNextOrdinal = zoom.ordinal() + 1;
            if (zoomNextOrdinal > zoomMaxOrdinal)
                zoomNextOrdinal = zoomMaxOrdinal;

            zoom = TypeZoom.values()[zoomNextOrdinal];
            this._zoom = zoom;
        } finally {
            _writeLock.unlock();
        }
        notifyPropertyChanged(BR.zoomString);
        notifyPropertyChanged(BR.tileFileName);
        notifyPropertyChanged(BR.tileFilePosString);
    }

    public void zoomLess() {
        _writeLock.lock();
        try {
            TypeZoom zoom = this._zoom;

            int zoomMaxOrdinal = TypeZoom.values().length - 1;
            int zoomPrevOrdinal = zoom.ordinal() - 1;
            if (zoomPrevOrdinal < 0)
                zoomPrevOrdinal = 0;

            zoom = TypeZoom.values()[zoomPrevOrdinal];
            this._zoom = zoom;
        } finally {
            _writeLock.unlock();
        }
        notifyPropertyChanged(BR.zoomString);
        notifyPropertyChanged(BR.tileFileName);
        notifyPropertyChanged(BR.tileFilePosString);
    }


    // Property Diverse pour test
    @Bindable
    public String getTileFilePosString() {
        TypeZoom zoom;
        double longitude;
        double latitude;

        _readLock.lock();
        try {
            zoom = this._zoom;
            GPSFix p = predictedCoordonateRaw();
            longitude = p.getLongitude();
            latitude = p.getLatitude();
        } finally {
            _readLock.unlock();
        }

        // #98 : the tile name gives the real place away.
        if( StreamerMode.enabled )
            return "";

        return String.format("%d:%d", zoom.tileFileCol(longitude, latitude), zoom.tileFileLig(latitude));
    }

    @Bindable
    public String getTileFileName() {
        TypeZoom zoom;
        double longitude;
        double latitude;

        _readLock.lock();
        try {
            zoom = this._zoom;
            GPSFix p = predictedCoordonateRaw();
            longitude = p.getLongitude();
            latitude = p.getLatitude();
        } finally {
            _readLock.unlock();
        }

        // #98 : the tile name gives the real place away.
        if( StreamerMode.enabled )
            return "";

        return String.format("%s", zoom.tileFileName(longitude, latitude));
    }


    // Property InLand
    public boolean isInLand() {
        boolean inLand;

        _readLock.lock();
        try {
            inLand = this._inLand;
        } finally {
            _readLock.unlock();
        }

        return inLand;
    }

    // Categorie d'avatar a afficher : le mode etabli, ou l'etat instantane tant qu'on est indetermine.
    public int getAvatarCategory() {
        int category;

        _readLock.lock();
        try {
            category = _travelMode.category();
        } finally {
            _readLock.unlock();
        }

        return category;
    }

    @Bindable
    public String getWhereIAmString() {
        boolean inLand;

        _readLock.lock();
        try {
            inLand = this._inLand;
        } finally {
            _readLock.unlock();
        }

        return (inLand ? "in the land" : "in the water");
    }

    public void setInLand(boolean inLand) {

        _writeLock.lock();
        try {
            this._inLand = inLand;
        } finally {
            _writeLock.unlock();
        }

        notifyPropertyChanged(BR.whereIAmString);
    }

    // Version (Major.Minor.buildnumber). Major/Minor are set manually and the build number is
    // auto-incremented in app/build.gradle; VERSION_NAME is derived from them.
    @Bindable
    public String getVersionString() {
        return "v" + BuildConfig.VERSION_NAME;
    }

    // Property FPS
    @Bindable
    public double getFps() {
        double fps;

        _readLock.lock();
        try {
            fps = this._fps;
        } finally {
            _readLock.unlock();
        }

        return fps;
    }

    @Bindable
    public String getFpsString() {
        double fps;

        _readLock.lock();
        try {
            fps = this._fps;
        } finally {
            _readLock.unlock();
        }

        return String.format("%.1f", fps);
    }

    // Heap readout for the debug panel: used / max in MB (leak-hunt).
    @Bindable
    public String getMemString() {
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMB  = rt.maxMemory() / (1024 * 1024);
        return String.format(Locale.US, "%d/%d MB", usedMB, maxMB);
    }


    public void setFps(double fps) {

        _writeLock.lock();
        try {
            this._fps = fps;
        } finally {
            _writeLock.unlock();
        }
        notifyPropertyChanged(BR.fpsString);
        notifyPropertyChanged(BR.fps);
        notifyPropertyChanged(BR.memString);   // refresh on-screen heap 1x/s
    }

    // Performance readouts of the debug panel, fed once a second by the render thread and by the
    // chunk being drawn. They only ever show something while "Debug mode" is on -- the same switch
    // that opens the PERF log lines -- since that is when the panel is of any use.
    // Frame: time of one frame, split into update, display-list recording and rasterisation. With
    // a hardware canvas, draw() only RECORDS; the real painting happens when the frame is posted,
    // so the two are shown apart, otherwise the numbers blame the wrong step.
    // Draw: recording time by category, areas / roads / marks.
    // Land: land polygons held by the chunk, and their total number of points.
    @Bindable
    public String getFrameString() {
        String frame;

        _readLock.lock();
        try {
            frame = this._frameString;
        } finally {
            _readLock.unlock();
        }

        return frame;
    }

    @Bindable
    public String getDrawString() {
        String draw;

        _readLock.lock();
        try {
            draw = this._drawString;
        } finally {
            _readLock.unlock();
        }

        return draw;
    }

    @Bindable
    public String getLandString() {
        String land;

        _readLock.lock();
        try {
            land = this._landString;
        } finally {
            _readLock.unlock();
        }

        return land;
    }

    // hardware: the render loop got a GPU canvas (lockHardwareCanvas). On the software canvas every
    // pixel is painted by the CPU and the last figure -- the time to present the frame -- explodes,
    // so the panel must say which one is in use: a tester has no cable to read the log.
    public void setFrameTimes(double updateMs, double recordMs, double postMs, boolean hardware) {

        String frame = String.format(Locale.US, "%.1f/%.1f/%.1f ms %s",
                updateMs, recordMs, postMs, hardware ? "hw" : "SW!");

        _writeLock.lock();
        try {
            this._frameString = frame;
        } finally {
            _writeLock.unlock();
        }

        notifyPropertyChanged(BR.frameString);
    }

    @Bindable
    public String getChunkCountsString() {
        _readLock.lock();
        try {
            return String.format(Locale.US, "pub %d prep %d dl %d err %d",
                    _countPublished, _countPrepared, _countDownloads, _countFailures);
        } finally {
            _readLock.unlock();
        }
    }

    @Bindable
    public String getLastPrepString() {
        _readLock.lock();
        try { return _lastPrepString; } finally { _readLock.unlock(); }
    }

    @Bindable
    public String getUiLagString() {
        _readLock.lock();
        try { return _uiLagString; } finally { _readLock.unlock(); }
    }

    private final Runnable _uiLagTick = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            long late = Math.max(0, now - _uiLagDueMs);

            if( late > _uiLagMaxMs )
                _uiLagMaxMs = late;

            String text = String.format(Locale.US, "%.1f / max %.1f s", late / 1000.0, _uiLagMaxMs / 1000.0);

            _writeLock.lock();
            try { _uiLagString = text; } finally { _writeLock.unlock(); }

            notifyPropertyChanged(BR.uiLagString);

            _uiLagDueMs = now + UI_LAG_PERIOD_MS;
            _uiLagHandler.postAtTime(this, _uiLagDueMs);
        }
    };

    // Started with the background threads; runs on the main looper for the life of the process.
    private void startUiLagWatch() {
        if( _uiLagHandler != null )
            return;

        _uiLagHandler = new Handler(Looper.getMainLooper());
        _uiLagDueMs = SystemClock.uptimeMillis() + UI_LAG_PERIOD_MS;
        _uiLagHandler.postAtTime(_uiLagTick, _uiLagDueMs);
    }

    public void setDrawTimes(double areasMs, double roadsMs, double marksMs) {

        String draw = String.format(Locale.US, "%.1f/%.1f/%.1f ms", areasMs, roadsMs, marksMs);

        _writeLock.lock();
        try {
            this._drawString = draw;
        } finally {
            _writeLock.unlock();
        }

        notifyPropertyChanged(BR.drawString);
    }

    public void setLandStats(int polygons, int points) {

        String land = String.format(Locale.US, "%d poly / %d pts", polygons, points);

        _writeLock.lock();
        try {
            this._landString = land;
        } finally {
            _writeLock.unlock();
        }

        notifyPropertyChanged(BR.landString);
    }

    // Property latitude
    @Bindable
    public double getLatitude() {
        double latitude;

        _readLock.lock();
        try {
            GPSFix pcr = predictedCoordonateRaw();
            latitude = pcr.getLatitude();
        } finally {
            _readLock.unlock();
        }

        return latitude;
    }

    @Bindable
    public String getLatitudeString() {
        String s;
        GPSFix pcr;
        int fmt;

        _readLock.lock();
        try {
            pcr = predictedCoordonateRaw();
            fmt = _coordFmt;
        } finally {
            _readLock.unlock();
        }

        // #98 : shifted copy while Streamer mode is on (outside the lock: it builds an object).
        GPSCoordonate c = StreamerMode.shift(pcr);
        switch (fmt) {
            case COORDFMT_DD:
                s = c.latDD();
                break;
            case COORDFMT_DMS:
                s = c.latDMS();
                break;
            default:
                s = c.latDM();
                break;
        }

        return s;
    }


    // Property longitude
    @Bindable
    public double getLongitude() {
        double longitude;

        _readLock.lock();
        try {
            GPSFix pcr = predictedCoordonateRaw();
            longitude = pcr.getLongitude();
        } finally {
            _readLock.unlock();
        }

        return longitude;
    }

    @Bindable
    public String getLongitudeString() {
        String s;
        GPSFix pcr;
        int fmt;

        _readLock.lock();
        try {
            pcr = predictedCoordonateRaw();
            fmt = _coordFmt;
        } finally {
            _readLock.unlock();
        }

        // #98 : shifted copy while Streamer mode is on (outside the lock: it builds an object).
        GPSCoordonate c = StreamerMode.shift(pcr);
        switch (fmt) {
            case COORDFMT_DD:
                s = c.lonDD();
                break;
            case COORDFMT_DMS:
                s = c.lonDMS();
                break;
            default:
                s = c.lonDM();
                break;
        }

        return s;
    }


    @Bindable
    public long getPositionTime() {
        long positionTime;

        _readLock.lock();
        try {
            positionTime = this._fixes[0].getTimeMs();
        } finally {
            _readLock.unlock();
        }

        return positionTime;
    }

    @Bindable
    public double getDirectionAngle() {
        double angle;

        _readLock.lock();
        try {
            angle = _directionAngle;
        } finally {
            _readLock.unlock();
        }

        return angle;
    }

    // --- Anticipation horizon (future-position prediction), adjustable via the "T" button --------
    public long getAnticipationMs() {
        long v;
        _readLock.lock();
        try {
            v = _anticipationMs;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    public void setAnticipationMs(long ms) {
        if (ms != ANTICIP_AUTO) {
            if (ms < ANTICIPATION_MIN_MS) ms = ANTICIPATION_MIN_MS;
            if (ms > ANTICIPATION_MAX_MS) ms = ANTICIPATION_MAX_MS;
        }

        Context context;
        _writeLock.lock();
        try {
            _anticipationMs = ms;
            context = _context;
        } finally {
            _writeLock.unlock();
        }

        persistLong(context, KEY_ANTICIP_MS, ms);
        notifyPropertyChanged(BR.anticipationString);
        notifyPropertyChanged(BR.anticipPos);
    }

    // Effective anticipation horizon (ms): fixed value, or speed-adaptive when set to "auto".
    private long effectiveAnticipationMs() {
        if (_anticipationMs != ANTICIP_AUTO) return _anticipationMs;
        long ms = (long) (ANTICIP_AUTO_BASE_MS - _averageSpeed * ANTICIP_AUTO_PER_KMH);
        if (ms < ANTICIPATION_MIN_MS) ms = ANTICIPATION_MIN_MS;
        if (ms > ANTICIP_AUTO_BASE_MS) ms = ANTICIP_AUTO_BASE_MS;
        return ms;
    }

    // --- Predicted-cap horizon (heading prediction), adjustable via the "PredCap" F parameter ------
    public long getHeadingAnticipMs() {
        long v;
        _readLock.lock();
        try {
            v = _headingAnticipMs;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    public void setHeadingAnticipMs(long ms) {
        if (ms != HEADING_ANTICIP_AUTO) {
            if (ms < HEADING_ANTICIP_MIN_MS) ms = HEADING_ANTICIP_MIN_MS;
            if (ms > HEADING_ANTICIP_MAX_MS) ms = HEADING_ANTICIP_MAX_MS;
        }

        Context context;
        _writeLock.lock();
        try {
            _headingAnticipMs = ms;
            context = _context;
        } finally {
            _writeLock.unlock();
        }

        persistLong(context, KEY_HEADING_ANTICIP_MS, ms);
        notifyPropertyChanged(BR.headingPredictString);
        notifyPropertyChanged(BR.predCapPos);
    }

    // --- Average-speed window (SpdWin F parameter) ----------------------------------------------
    public long getSpeedAvgMs() {
        long v;
        _readLock.lock();
        try {
            v = _speedAvgMs;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    public void setSpeedAvgMs(long ms) {
        if (ms != SPDWIN_AUTO) {
            if (ms < SPDWIN_MIN_MS) ms = SPDWIN_MIN_MS;
            if (ms > SPDWIN_MAX_MS) ms = SPDWIN_MAX_MS;
        }

        Context context;
        _writeLock.lock();
        try {
            _speedAvgMs = ms;
            context = _context;
        } finally {
            _writeLock.unlock();
        }

        persistLong(context, KEY_SPEED_AVG_MS, ms);
        notifyPropertyChanged(BR.spdWinString);
        notifyPropertyChanged(BR.spdWinPos);
    }

    // Predicted heading (degrees, [0,360)) exposed to the view for the green predicted-cap line.
    public double getPredictedHeadingDeg() {
        double v;
        _readLock.lock();
        try {
            v = predictedHeadingDeg();
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    // --- Debug "F" button: cycle which parameter +/- act on; +/- apply to the active one ---------

    private int getDebugParam() {
        int p;
        _readLock.lock();
        try {
            p = _debugParam;
        } finally {
            _readLock.unlock();
        }
        return p;
    }

    // "F" button: select the next tunable parameter, wrapping back to the first.
    public void cycleDebugParam() {
        int p;
        Context context;
        _writeLock.lock();
        try {
            _debugParam = (_debugParam + 1) % PARAM_COUNT;
            p = _debugParam;
            context = _context;
        } finally {
            _writeLock.unlock();
        }
        persistInt(context, KEY_DEBUG_PARAM, p);
        // Refresh the "*" active markers in the left debug list.
        notifyPropertyChanged(BR.zoomLabel);
        notifyPropertyChanged(BR.anticipLabel);
        notifyPropertyChanged(BR.predCapLabel);
        notifyPropertyChanged(BR.spdWinLabel);
        notifyPropertyChanged(BR.traceLabel);
        notifyPropertyChanged(BR.nptsLabel);
        notifyPropertyChanged(BR.overlayLabel);
        notifyPropertyChanged(BR.declLabel);
        notifyPropertyChanged(BR.proxLabel);
        notifyPropertyChanged(BR.zoneWLabel);
        notifyPropertyChanged(BR.coordFmtLabel);
        notifyPropertyChanged(BR.warnDistLabel);
        notifyPropertyChanged(BR.warnEtaLabel);
        notifyPropertyChanged(BR.gameLevelLabel);
    }

    // Tap on the big speed (bottom-right): cycle km/h -> mph -> knots, persisted.
    public void cycleSpeedUnit() {
        int u;
        Context context;
        _writeLock.lock();
        try {
            _speedUnit = (_speedUnit + 1) % SPEED_UNIT_COUNT;
            u = _speedUnit;
            context = _context;
        } finally {
            _writeLock.unlock();
        }
        persistInt(context, KEY_SPEED_UNIT, u);
        notifyPropertyChanged(BR.speedKmString);
        notifyPropertyChanged(BR.markEtaString); // distances follow the unit
    }

    // Current speed-unit mode (0 = km/h, 1 = mph, 2 = knots) — also drives distance units.
    public int getSpeedUnit() {
        int u;
        _readLock.lock();
        try {
            u = _speedUnit;
        } finally {
            _readLock.unlock();
        }
        return u;
    }

    // "+" button: act on the active parameter.
    public void debugPlus() {
        switch (getDebugParam()) {
            case PARAM_ANTICIP: {
                // Sequence: auto -> 1.0 -> 1.5 -> ... -> 5.0 (clamped at 5.0).
                long cur = getAnticipationMs();
                if (cur == ANTICIP_AUTO) setAnticipationMs(ANTICIPATION_MIN_MS);
                else setAnticipationMs(cur + ANTICIPATION_STEP_MS);
                break;
            }
            case PARAM_PREDCAP: {
                // Sequence: auto -> 1.0 -> 1.5 -> ... -> 5.0 (clamped at 5.0).
                long cur = getHeadingAnticipMs();
                if (cur == HEADING_ANTICIP_AUTO) setHeadingAnticipMs(HEADING_ANTICIP_MIN_MS);
                else setHeadingAnticipMs(cur + HEADING_ANTICIP_STEP_MS);
                break;
            }
            case PARAM_SPDWIN: {
                // Sequence: auto -> 2 s -> 3 s -> ... -> 12 s (clamped).
                long cur = getSpeedAvgMs();
                if (cur == SPDWIN_AUTO) setSpeedAvgMs(SPDWIN_MIN_MS);
                else setSpeedAvgMs(cur + SPDWIN_STEP_MS);
                break;
            }
            case PARAM_TRACE:
                setTraceOn(true);
                break;
            case PARAM_NPTS: {
                // Sequence: auto -> 2 -> 3 -> ... -> 30 (clamped at 30).
                int cur = getUncertPoints();
                if (cur == NPTS_AUTO) setUncertPoints(NPTS_MIN);
                else setUncertPoints(cur + 1);
                break;
            }
            case PARAM_OVERLAY:
                setOverlayOn(true);
                break;
            case PARAM_DECL:
                setMagDecl(getMagDecl() + DECL_STEP);
                break;
            case PARAM_PROXDIST:
                setProxMeters(getProxMeters() + PROXDIST_STEP);
                break;
            case PARAM_ZONEWIDTH: {
                int i = zoneWIndex(getZoneTargetWidthM());
                if (i < ZONEW_VALUES.length - 1)
                    setZoneTargetWidthM(ZONEW_VALUES[i + 1]);
                break;
            }
            case PARAM_COORDFMT:
                setCoordFmt((getCoordFmt() + 1) % COORDFMT_COUNT);   // DD -> DM -> DMS -> DD
                break;
            case PARAM_WARNDIST:
                setWarnDistM(getWarnDistM() + WARNDIST_STEP);
                break;
            case PARAM_WARNETA:
                setWarnEtaSec(getWarnEtaSec() + WARNETA_STEP);
                break;
            case PARAM_GAMELVL:
                // Le plafond est celui de la MOLETTE, et de rien d'autre : le jeu, lui, n'a pas
                // de dernier niveau. Il etait dans setGameLevel(), ou il bloquait aussi les
                // montees du jeu lui-meme -- vider la derniere vague du niveau 9 ne faisait alors
                // plus rien et laissait la partie sur un terrain vide.
                if (getGameLevel() < GAME_LEVEL_MAX)
                    setGameLevel(getGameLevel() + 1);
                break;
            case PARAM_ZOOM:
            default:
                zoomLess(); // historical mapping: "+" = zoomLess
                break;
        }
    }

    // "-" button: act on the active parameter.
    public void debugMinus() {
        switch (getDebugParam()) {
            case PARAM_ANTICIP: {
                // Sequence: 5.0 -> ... -> 1.0 -> auto (stays at auto).
                long cur = getAnticipationMs();
                if (cur == ANTICIP_AUTO) { /* already at the bottom of the list */ }
                else if (cur <= ANTICIPATION_MIN_MS) setAnticipationMs(ANTICIP_AUTO);
                else setAnticipationMs(cur - ANTICIPATION_STEP_MS);
                break;
            }
            case PARAM_PREDCAP: {
                // Sequence: 5.0 -> ... -> 1.0 -> auto (stays at auto).
                long cur = getHeadingAnticipMs();
                if (cur == HEADING_ANTICIP_AUTO) { /* already at the bottom of the list */ }
                else if (cur <= HEADING_ANTICIP_MIN_MS) setHeadingAnticipMs(HEADING_ANTICIP_AUTO);
                else setHeadingAnticipMs(cur - HEADING_ANTICIP_STEP_MS);
                break;
            }
            case PARAM_SPDWIN: {
                // Sequence: 12 s -> ... -> 2 s -> auto (stays at auto).
                long cur = getSpeedAvgMs();
                if (cur == SPDWIN_AUTO) { /* already at the bottom of the list */ }
                else if (cur <= SPDWIN_MIN_MS) setSpeedAvgMs(SPDWIN_AUTO);
                else setSpeedAvgMs(cur - SPDWIN_STEP_MS);
                break;
            }
            case PARAM_TRACE:
                setTraceOn(false);
                break;
            case PARAM_NPTS: {
                // Sequence: 30 -> ... -> 2 -> auto (stays at auto).
                int cur = getUncertPoints();
                if (cur == NPTS_AUTO) { /* already at the bottom of the list */ }
                else if (cur <= NPTS_MIN) setUncertPoints(NPTS_AUTO);
                else setUncertPoints(cur - 1);
                break;
            }
            case PARAM_OVERLAY:
                setOverlayOn(false);
                break;
            case PARAM_DECL:
                setMagDecl(getMagDecl() - DECL_STEP);
                break;
            case PARAM_PROXDIST:
                setProxMeters(getProxMeters() - PROXDIST_STEP);
                break;
            case PARAM_ZONEWIDTH: {
                int i = zoneWIndex(getZoneTargetWidthM());
                if (i > 0)
                    setZoneTargetWidthM(ZONEW_VALUES[i - 1]);
                break;
            }
            case PARAM_COORDFMT:
                setCoordFmt((getCoordFmt() + COORDFMT_COUNT - 1) % COORDFMT_COUNT);   // DD <- DM <- DMS
                break;
            case PARAM_WARNDIST:
                setWarnDistM(getWarnDistM() - WARNDIST_STEP);
                break;
            case PARAM_GAMELVL:
                setGameLevel(getGameLevel() - 1);
                break;
            case PARAM_WARNETA:
                setWarnEtaSec(getWarnEtaSec() - WARNETA_STEP);
                break;
            case PARAM_ZOOM:
            default:
                zoomMore(); // historical mapping: "-" = zoomMore
                break;
        }
    }

    // Left-list labels with a leading "*" on the currently selected parameter.
    @Bindable
    public String getZoomLabel() {
        return (getDebugParam() == PARAM_ZOOM ? "*" : "") + "Zoom";
    }

    @Bindable
    public String getAnticipLabel() {
        return (getDebugParam() == PARAM_ANTICIP ? "*" : "") + "Predict";
    }

    @Bindable
    public String getTraceLabel() {
        return (getDebugParam() == PARAM_TRACE ? "*" : "") + "Trace";
    }

    @Bindable
    public String getNptsLabel() {
        return (getDebugParam() == PARAM_NPTS ? "*" : "") + "Npts";
    }

    @Bindable
    public String getOverlayLabel() {
        return (getDebugParam() == PARAM_OVERLAY ? "*" : "") + "DbgOverlay";
    }

    @Bindable
    public String getPredCapLabel() {
        return (getDebugParam() == PARAM_PREDCAP ? "*" : "") + "PredCap";
    }

    @Bindable
    public String getHeadingPredictString() {
        long v;
        _readLock.lock();
        try {
            v = _headingAnticipMs;
        } finally {
            _readLock.unlock();
        }
        return (v == HEADING_ANTICIP_AUTO) ? "auto" : String.format(Locale.US, "%.1f s", v / 1000.0);
    }

    @Bindable
    public String getSpdWinLabel() {
        return (getDebugParam() == PARAM_SPDWIN ? "*" : "") + "SpdWin";
    }

    @Bindable
    public String getSpdWinString() {
        long v;
        _readLock.lock();
        try {
            v = _speedAvgMs;
        } finally {
            _readLock.unlock();
        }
        return (v == SPDWIN_AUTO) ? "auto" : String.format(Locale.US, "%.0f s", v / 1000.0);
    }

    @Bindable
    public String getAnticipationString() {
        long v;
        _readLock.lock();
        try {
            v = _anticipationMs;
        } finally {
            _readLock.unlock();
        }
        return (v == ANTICIP_AUTO) ? "auto" : String.format(Locale.US, "%.1f s", v / 1000.0);
    }

    // --- Les memes horizons, en liste, pour la page Parameters ----------------------------------
    // Chaque liste s'ouvre par "auto" puis parcourt les memes crans que les boutons +/-, et affiche
    // la valeur exactement comme l'affichage debug la formate.
    public List<String> getAnticipValues() {
        List<String> values = new ArrayList<>();

        values.add("auto");
        for (long ms = ANTICIPATION_MIN_MS; ms <= ANTICIPATION_MAX_MS; ms += ANTICIPATION_STEP_MS)
            values.add(String.format(Locale.US, "%.1f s", ms / 1000.0));

        return values;
    }

    @Bindable
    public int getAnticipPos() {
        long v = getAnticipationMs();

        return (v == ANTICIP_AUTO) ? 0 : (int) ((v - ANTICIPATION_MIN_MS) / ANTICIPATION_STEP_MS) + 1;
    }

    public void setAnticipPos(int position) {
        long ms = (position == 0) ? ANTICIP_AUTO
                                  : ANTICIPATION_MIN_MS + ((position - 1) * ANTICIPATION_STEP_MS);

        if (ms == getAnticipationMs())
            return;

        setAnticipationMs(ms);
    }

    public List<String> getPredCapValues() {
        List<String> values = new ArrayList<>();

        values.add("auto");
        for (long ms = HEADING_ANTICIP_MIN_MS; ms <= HEADING_ANTICIP_MAX_MS; ms += HEADING_ANTICIP_STEP_MS)
            values.add(String.format(Locale.US, "%.1f s", ms / 1000.0));

        return values;
    }

    @Bindable
    public int getPredCapPos() {
        long v = getHeadingAnticipMs();

        return (v == HEADING_ANTICIP_AUTO) ? 0
                : (int) ((v - HEADING_ANTICIP_MIN_MS) / HEADING_ANTICIP_STEP_MS) + 1;
    }

    public void setPredCapPos(int position) {
        long ms = (position == 0) ? HEADING_ANTICIP_AUTO
                                  : HEADING_ANTICIP_MIN_MS + ((position - 1) * HEADING_ANTICIP_STEP_MS);

        if (ms == getHeadingAnticipMs())
            return;

        setHeadingAnticipMs(ms);
    }

    public List<String> getSpdWinValues() {
        List<String> values = new ArrayList<>();

        values.add("auto");
        for (long ms = SPDWIN_MIN_MS; ms <= SPDWIN_MAX_MS; ms += SPDWIN_STEP_MS)
            values.add(String.format(Locale.US, "%.0f s", ms / 1000.0));

        return values;
    }

    @Bindable
    public int getSpdWinPos() {
        long v = getSpeedAvgMs();

        return (v == SPDWIN_AUTO) ? 0 : (int) ((v - SPDWIN_MIN_MS) / SPDWIN_STEP_MS) + 1;
    }

    public void setSpdWinPos(int position) {
        long ms = (position == 0) ? SPDWIN_AUTO : SPDWIN_MIN_MS + ((position - 1) * SPDWIN_STEP_MS);

        if (ms == getSpeedAvgMs())
            return;

        setSpeedAvgMs(ms);
    }

    // --- Average-altitude window ("Altitude averaging") -------------------------------------------
    public long getAltAvgMs() {
        long v;
        _readLock.lock();
        try {
            v = _altAvgMs;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    public void setAltAvgMs(long ms) {
        if (ms != ALTWIN_AUTO) {
            if (ms < SPDWIN_MIN_MS) ms = SPDWIN_MIN_MS;
            if (ms > SPDWIN_MAX_MS) ms = SPDWIN_MAX_MS;
        }

        Context context;
        _writeLock.lock();
        try {
            _altAvgMs = ms;
            context = _context;
        } finally {
            _writeLock.unlock();
        }

        persistLong(context, KEY_ALT_AVG_MS, ms);
        notifyPropertyChanged(BR.altWinPos);
    }

    public List<String> getAltWinValues() {
        return getSpdWinValues();
    }

    @Bindable
    public int getAltWinPos() {
        long v = getAltAvgMs();

        return (v == ALTWIN_AUTO) ? 0 : (int) ((v - SPDWIN_MIN_MS) / SPDWIN_STEP_MS) + 1;
    }

    public void setAltWinPos(int position) {
        long ms = (position == 0) ? ALTWIN_AUTO : SPDWIN_MIN_MS + ((position - 1) * SPDWIN_STEP_MS);

        if (ms == getAltAvgMs())
            return;

        setAltAvgMs(ms);
    }

    // Mean altitude of the fixes of the last windowMs, null if none of them has one. Caller holds
    // _writeLock (plain arithmetic on the fix history).
    private Double averageAltitudeNoLock(long windowMs, int fill) {
        if (fill < 1) {
            return null;
        }
        long t0 = _fixes[0].getTimeMs();
        long windowUnits = windowMs * timeUnitsPerMs();
        double sum = 0.0;
        int n = 0;
        for (int i = 0; i < fill; i++) {
            long age = t0 - _fixes[i].getTimeMs();
            if (age < 0 || age > windowUnits) {
                break;
            }
            Double a = _fixes[i].getAltitude();
            if (a != null) {
                sum += a;
                n++;
            }
        }
        return (n > 0) ? (sum / n) : null;
    }

    // --- Debug mode (#36, Parameters page) ------------------------------------------------------
    @Bindable
    public boolean getDebugMode() {
        boolean v;
        _readLock.lock();
        try {
            v = _debugMode;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    public void setDebugMode(boolean on) {
        Context context;
        boolean changed;
        _writeLock.lock();
        try {
            changed = (_debugMode != on);
            _debugMode = on;
            context = _context;
        } finally {
            _writeLock.unlock();
        }
        GBLog.setDebug(on);   // always (idempotent) — keeps the log level in sync
        if (changed) {
            persistBoolean(context, KEY_DEBUG_MODE, on);
            notifyPropertyChanged(BR.debugMode);
        }
    }

    // --- Streamer mode (#98, Parameters page) ---------------------------------------------------
    @Bindable
    public boolean getStreamerMode() {
        boolean v;
        _readLock.lock();
        try {
            v = _streamerMode;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    public void setStreamerMode(boolean on) {
        Context context;
        boolean changed;
        _writeLock.lock();
        try {
            changed = (_streamerMode != on);
            _streamerMode = on;
            context = _context;
        } finally {
            _writeLock.unlock();
        }
        StreamerMode.enabled = on;
        if (changed) {
            persistBoolean(context, KEY_STREAMER_MODE, on);
            notifyPropertyChanged(BR.streamerMode);
            notifyPropertyChanged(BR.latitudeString);
            notifyPropertyChanged(BR.longitudeString);
            notifyPropertyChanged(BR.tileFileName);
            notifyPropertyChanged(BR.tileFilePosString);
            notifyPropertyChanged(BR.declAutoString);
            notifyPropertyChanged(BR.declAutoModelString);
        }
    }

    // --- Single target (#52, Global parameters) -------------------------------------------------
    @Bindable
    public boolean getSingleTarget() {
        boolean v;
        _readLock.lock();
        try {
            v = _singleTarget;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    public void setSingleTarget(boolean on) {
        Context context;
        boolean changed;
        _writeLock.lock();
        try {
            changed = (_singleTarget != on);
            _singleTarget = on;
            context = _context;
        } finally {
            _writeLock.unlock();
        }
        if (changed) {
            persistBoolean(context, KEY_SINGLE_TARGET, on);
            notifyPropertyChanged(BR.singleTarget);
        }
    }

    // --- Avatar GPS : un sprite configurable par categorie ---------------------------------------

    // Nom de ressource du sprite choisi pour une categorie, "" si l'utilisateur n'a rien choisi.
    public String getAvatarName(int category) {
        String name = null;

        _readLock.lock();
        try {
            if ((category >= 0) && (category < AvatarCatalog.CAT_COUNT))
                name = _avatarNames[category];
        } finally {
            _readLock.unlock();
        }

        return (name == null) ? "" : name;
    }

    public void setAvatarName(int category, String name) {
        if ((category < 0) || (category >= AvatarCatalog.CAT_COUNT))
            return;

        if (name == null)
            name = "";

        Context context;
        boolean changed;

        _writeLock.lock();
        try {
            String previous = (_avatarNames[category] == null) ? "" : _avatarNames[category];
            changed = !previous.equals(name);
            _avatarNames[category] = name;
            context = _context;
        } finally {
            _writeLock.unlock();
        }

        if (changed) {
            persistString(context, KEY_AVATAR[category], name);
            notifyAvatarChanged(category);
        }
    }

    // Ressource du sprite a afficher pour une categorie, replis compris (cf. AvatarCatalog.resolve).
    // Le pieton est resolu d'abord : il sert de repli aux categories encore vides.
    public int getAvatarResId(int category) {
        String walkName;
        String name = null;

        _readLock.lock();
        try {
            walkName = _avatarNames[AvatarCatalog.CAT_WALK];
            if ((category >= 0) && (category < AvatarCatalog.CAT_COUNT))
                name = _avatarNames[category];
        } finally {
            _readLock.unlock();
        }

        int walkResId = AvatarCatalog.resolve(AvatarCatalog.CAT_WALK, walkName, 0);

        if (category == AvatarCatalog.CAT_WALK)
            return walkResId;

        return AvatarCatalog.resolve(category, name, walkResId);
    }

    // Numero de serie des reglages d'avatar : change des qu'un sprite est reaffecte, ce qui indique
    // au thread de rendu (MapGPSPos) qu'il doit recharger le sien.
    public int getAvatarSerial() {
        return _avatarSerial;
    }

    // Previent la page Parameters et le rendu qu'un avatar a change. Le pieton servant de repli aux
    // categories vides, quand il change les lignes "middle" et "fast" changent avec lui.
    private void notifyAvatarChanged(int category) {
        _avatarSerial++;

        switch (category) {
            case AvatarCatalog.CAT_SEA:
                notifyPropertyChanged(BR.avatarSeaLabel);
                notifyPropertyChanged(BR.avatarSeaPreview);
                break;

            case AvatarCatalog.CAT_RIDE:
                notifyPropertyChanged(BR.avatarRideLabel);
                notifyPropertyChanged(BR.avatarRidePreview);
                break;

            case AvatarCatalog.CAT_DRIVE:
                notifyPropertyChanged(BR.avatarDriveLabel);
                notifyPropertyChanged(BR.avatarDrivePreview);
                break;

            case AvatarCatalog.CAT_WALK:
            default:
                notifyPropertyChanged(BR.avatarWalkLabel);
                notifyPropertyChanged(BR.avatarWalkPreview);
                notifyPropertyChanged(BR.avatarRideLabel);
                notifyPropertyChanged(BR.avatarRidePreview);
                notifyPropertyChanged(BR.avatarDriveLabel);
                notifyPropertyChanged(BR.avatarDrivePreview);
                break;
        }
    }

    private String avatarLabel(int category) {
        AvatarCatalog.Entry entry = AvatarCatalog.entryOf(getAvatarResId(category));

        return (entry == null) ? "" : entry.label;
    }

    private Drawable avatarPreview(int category) {
        Context context;
        boolean lowRes;

        _readLock.lock();
        try {
            context = _context;
            lowRes = _lowResourceMode;
        } finally {
            _readLock.unlock();
        }

        if (context == null)
            return null;

        // #85: in low-resource mode, a single cut-out image instead of the animation, which keeps
        // the idle pose and the walk cycle of ALL FOUR directions in memory.
        if (lowRes)
            return AvatarCatalog.stillFrame(context.getResources(), getAvatarResId(category));

        // Animation de demonstration (arret puis marche, les 4 directions) et non une image figee.
        return AvatarCatalog.demoAnimation(context.getResources(), getAvatarResId(category));
    }

    @Bindable
    public String getAvatarSeaLabel() {
        return avatarLabel(AvatarCatalog.CAT_SEA);
    }

    @Bindable
    public Drawable getAvatarSeaPreview() {
        return avatarPreview(AvatarCatalog.CAT_SEA);
    }

    @Bindable
    public String getAvatarWalkLabel() {
        return avatarLabel(AvatarCatalog.CAT_WALK);
    }

    @Bindable
    public Drawable getAvatarWalkPreview() {
        return avatarPreview(AvatarCatalog.CAT_WALK);
    }

    @Bindable
    public String getAvatarRideLabel() {
        return avatarLabel(AvatarCatalog.CAT_RIDE);
    }

    @Bindable
    public Drawable getAvatarRidePreview() {
        return avatarPreview(AvatarCatalog.CAT_RIDE);
    }

    @Bindable
    public String getAvatarDriveLabel() {
        return avatarLabel(AvatarCatalog.CAT_DRIVE);
    }

    @Bindable
    public Drawable getAvatarDrivePreview() {
        return avatarPreview(AvatarCatalog.CAT_DRIVE);
    }

    // --- GPS trace toggle (F-button parameter) --------------------------------------------------
    // @Bindable : le switch "GPS trail" de la page Parameters suit ce champ, que la valeur soit
    // changee depuis la page ou depuis les boutons +/- du mode debug.
    @Bindable
    public boolean isTraceOn() {
        boolean on;
        _readLock.lock();
        try {
            on = _traceOn;
        } finally {
            _readLock.unlock();
        }
        return on;
    }

    public void setTraceOn(boolean on) {
        Context context;
        _writeLock.lock();
        try {
            _traceOn = on;
            context = _context;
        } finally {
            _writeLock.unlock();
        }
        persistInt(context, KEY_TRACE_ON, on ? 1 : 0);
        notifyPropertyChanged(BR.traceString);
        notifyPropertyChanged(BR.traceOn);
    }

    @Bindable
    public String getTraceString() {
        boolean on;
        _readLock.lock();
        try {
            on = _traceOn;
        } finally {
            _readLock.unlock();
        }
        return on ? "on" : "off";
    }

    // --- Number of points used for the uncertainty width (F-button parameter) -------------------
    public int getUncertPoints() {
        int v;
        _readLock.lock();
        try {
            v = _uncertPoints;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    public void setUncertPoints(int n) {
        if (n != NPTS_AUTO) {
            if (n < NPTS_MIN) n = NPTS_MIN;
            if (n > NPTS_MAX) n = NPTS_MAX;
        }
        Context context;
        _writeLock.lock();
        try {
            _uncertPoints = n;
            context = _context;
        } finally {
            _writeLock.unlock();
        }
        persistInt(context, KEY_UNCERT_POINTS, n);
        notifyPropertyChanged(BR.nptsString);
        notifyPropertyChanged(BR.nptsPos);
    }

    // Effective number of points for the uncertainty width (auto -> all valid points).
    private int effectiveUncertPoints(int fillCount) {
        return (_uncertPoints == NPTS_AUTO) ? fillCount : Math.min(_uncertPoints, fillCount);
    }

    @Bindable
    public String getNptsString() {
        int v;
        _readLock.lock();
        try {
            v = _uncertPoints;
        } finally {
            _readLock.unlock();
        }
        return (v == NPTS_AUTO) ? "auto" : Integer.toString(v);
    }

    // --- Generic debug overlay toggle (F-button parameter) --------------------------------------
    @Bindable
    public boolean isOverlayOn() {
        boolean on;
        _readLock.lock();
        try {
            on = _overlayOn;
        } finally {
            _readLock.unlock();
        }
        return on;
    }

    public void setOverlayOn(boolean on) {
        Context context;
        _writeLock.lock();
        try {
            _overlayOn = on;
            context = _context;
        } finally {
            _writeLock.unlock();
        }
        persistInt(context, KEY_OVERLAY_ON, on ? 1 : 0);
        notifyPropertyChanged(BR.overlayString);
        notifyPropertyChanged(BR.overlayOn);
    }

    @Bindable
    public String getOverlayString() {
        boolean on;
        _readLock.lock();
        try {
            on = _overlayOn;
        } finally {
            _readLock.unlock();
        }
        return on ? "on" : "off";
    }

    // --- Les memes reglages, en liste, pour la page Parameters ---------------------------------
    // Trace et DbgOverlay sont des interrupteurs : la page se lie directement a isTraceOn() et
    // isOverlayOn(), rendus @Bindable ci-dessus. Npts a une liste, ouverte par "auto".
    public List<String> getNptsValues() {
        List<String> values = new ArrayList<>();

        values.add("auto");
        for (int n = NPTS_MIN; n <= NPTS_MAX; n++)
            values.add(Integer.toString(n));

        return values;
    }

    @Bindable
    public int getNptsPos() {
        int v = getUncertPoints();

        return (v == NPTS_AUTO) ? 0 : (v - NPTS_MIN + 1);
    }

    public void setNptsPos(int position) {
        int n = (position == 0) ? NPTS_AUTO : (NPTS_MIN + position - 1);

        if (n == getUncertPoints())
            return;

        setUncertPoints(n);
    }

    // True when the motion detector reports a stop (same condition as the "Stopped" status text).
    public boolean isStopped() {
        boolean stopped;
        _readLock.lock();
        try {
            stopped = _averageSpeed < SPEED_EPS_KMH;
        } finally {
            _readLock.unlock();
        }
        return stopped;
    }

    // Called by DeviceHeadingSensor (~every 100 ms). The binding refresh is throttled to 300 ms.
    public void setDeviceHeadingMagnetic(double azimuthMag) {
        long now = SystemClock.uptimeMillis();
        boolean notify;
        _writeLock.lock();
        try {
            _deviceHeadingMag = azimuthMag;
            _hasDeviceHeading = true;
            notify = (now - _deviceHeadingNotifyMs) >= DEVICE_HEADING_NOTIFY_MS;
            if (notify)
                _deviceHeadingNotifyMs = now;
        } finally {
            _writeLock.unlock();
        }
        if (notify)
            notifyPropertyChanged(BR.directionAngleString);
    }

    // Raw device heading (magnetic, filtered by DeviceHeadingSensor), NaN without a measure. Read by
    // MotionSensor as part of its still reference.
    public double getDeviceHeadingMagnetic() {
        double v;
        _readLock.lock();
        try {
            v = _hasDeviceHeading ? _deviceHeadingMag : Double.NaN;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    // Two headings (task #94):
    //  - cap direction: where we go. Category 1 = mark arrows, bottom-right heading (T and M).
    //    Cap vue when stopped, cap T otherwise, in game or not.
    //  - cap vue: where the avatar looks. Category 2 = avatar sprite, red sight line, bullets.
    //    Cap vue in game (level > 0) or when stopped, cap T otherwise.
    // Without a device-heading measure (no sensor, or none received yet) the cap vue is the cap T.
    // The motion computations (prediction, stabilisation, player velocity) always stay on cap T.

    // Device heading converted to true: inverse of magneticFromTrueNoLock() without compass deviation
    // (phone, not boat compass). Cap T if no measure. Caller must hold a lock. [0,360).
    private double viewHeadingTrueNoLock() {
        if (!_hasDeviceHeading)
            return _directionAngle;

        double v = (_deviceHeadingMag + modelDeclNoLock() - _magDeclManual) % 360.0;

        return (v < 0) ? (v + 360.0) : v;
    }

    // Category 1 heading. Caller must hold a lock.
    private double directionHeadingNoLock() {
        return (_averageSpeed < SPEED_EPS_KMH) ? viewHeadingTrueNoLock() : _directionAngle;
    }

    // Is category 2 on the cap vue? Caller must hold a lock.
    private boolean viewHeadingActiveNoLock() {
        return _hasDeviceHeading && ((_gameLevel > 0) || (_averageSpeed < SPEED_EPS_KMH));
    }

    // Category 2 heading (avatar sprite, red sight line, bullets).
    public double getViewHeading() {
        double v;
        _readLock.lock();
        try {
            v = viewHeadingActiveNoLock() ? viewHeadingTrueNoLock() : _directionAngle;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    // True when category 2 uses the cap vue: the blue turn cone is then hidden.
    public boolean isViewHeadingActive() {
        boolean v;
        _readLock.lock();
        try {
            v = viewHeadingActiveNoLock();
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    // The phone's own heading, in true degrees, or NaN without a measure. Read by the Sun
    // and Moon map for its compass needle: every bearing on that map is a true one. No
    // compass deviation applied (that card belongs to the boat compass, not to the phone),
    // and no fallback on the cap T: without the chip there is no needle to draw.
    public double getDeviceHeadingTrue() {
        double v;
        _readLock.lock();
        try {
            v = _hasDeviceHeading ? viewHeadingTrueNoLock() : Double.NaN;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    @Bindable
    public String getDirectionAngleString() {
        double angle, hw;
        double devTrue;
        boolean hasHw, hasDev;

        _readLock.lock();
        try {
            angle = _directionAngle;
            hw = _hwBearing;
            hasHw = _hasHwBearing;
            hasDev = _hasDeviceHeading;
            devTrue = viewHeadingTrueNoLock();
        } finally {
            _readLock.unlock();
        }

        double mag;
        _readLock.lock();
        try {
            mag = magneticHeadingNoLock();
        } finally {
            _readLock.unlock();
        }
        // "trueT (hardware) magneticM" — superscript T/M; hw is "--" if absent.
        String hwStr = hasHw ? fmt1(hw) : "--";
        String devStr = hasDev ? fmt1(devTrue) : "--";
        return fmt1(angle) + "ᵀ (" + hwStr + ") " + fmt1(mag) + "ᴹ " + devStr + "ᴰ";
    }

    // Up to 1 decimal, trailing ".0" dropped, dot separator (Locale.US) so speed and every displayed
    // cap stay homogeneous. e.g. 90.0 -> "90", 90.1 -> "90.1".
    private static String fmt1(double v) {
        double r = Math.round(v * 10.0) / 10.0;
        return (r == Math.rint(r)) ? String.format(Locale.US, "%.0f", r)
                                   : String.format(Locale.US, "%.1f", r);
    }

    // Integer degrees for the displayed caps (heading + mark), no decimals; 360 wraps to 0.
    private static String capDeg(double v) {
        return Long.toString(Math.round(v) % 360);
    }

    // #54 -- DANS QUEL REFERENTIEL CALCULER UN ANGLE ? La question revient sans arret, voici la
    // regle une fois pour toutes.
    //
    // Un CAP est une direction absolue comptee depuis un nord : il faut donc dire lequel, et c'est
    // la que cette fonction sert. Un GISEMENT est une DIFFERENCE de deux caps : il ne depend
    // d'aucun nord, A CONDITION que les deux termes soient exprimes dans le meme referentiel.
    //
    // La subtilite est que le passage du vrai au compas n'est PAS une simple rotation. La
    // declinaison, elle, en est une : elle s'annulerait dans une soustraction. Mais la deviation
    // depend du cap, donc elle ne s'annule pas : convertir les deux termes d'un gisement change le
    // resultat de dev(terme1) - dev(terme2), qui ne correspond a rien de physique.
    //
    // D'ou les deux choix de l'application, qui ne se contredisent pas :
    //
    //  - les fleches vers les marks (panneau haut-gauche, liste de proximite, ecran Personal marks)
    //    montrent un gisement course-up, "la mark par rapport a l'avant du bateau". Les deux termes
    //    y sont en CAP VRAI, tels que le GPS les fournit : le relevement de la mark et le cap du
    //    bateau. Les convertir tous les deux ajouterait l'ecart des deviations (mesure : 6 a 10
    //    degres sur une carte reelle), et n'en convertir qu'un ajouterait la correction entiere.
    //
    //  - la fleche de la page de configuration d'un compas compare le cap VISE, qui est une
    //    graduation de la carte de deviation, a la direction suivie. Or les graduations de cette
    //    carte SONT des caps compas, puisque c'est le cap compas qui indexe la carte. Les deux
    //    termes y sont donc en CAP COMPAS, ce qui impose d'y utiliser le cap M et non le cap vrai.
    //
    // Meme regle dans les deux cas -- les deux termes dans le meme referentiel -- appliquee a des
    // grandeurs qui ne sont pas de meme nature.
    //
    // cap vrai -> cap compas, avec les TROIS corrections dans l'ordre : la declinaison du
    // modele (si l'utilisateur l'a laissee active), la correction additionnelle manuelle, puis la
    // deviation du compas, interpolee entre les 8 points cardinaux. Toutes s'AJOUTENT au cap, comme
    // elles sont affichees. C'est la fonction destinee a etre branchee sur le cap de navigation ;
    // pour l'instant seule la page de configuration d'un compas s'en sert avec un compas non nul.
    //
    // Une subtilite : la carte de deviation s'indexe sur le CAP COMPAS, celui-la meme qu'on cherche,
    // donc l'equation est implicite et se resout par iterations. Un nombre fixe de tours ne suffit
    // pas : sur une carte a fort gradient (mesure : N +8, NE +15) trois tours laissaient encore 0,3
    // degre d'erreur. On itere donc jusqu'a ce que la valeur se stabilise, avec un plafond de sur
    // pour le cas d'une carte tres accidentee ou l'iteration oscillerait au lieu de converger --
    // le resultat reste alors borne, la deviation ne depassant jamais 25 degres.
    // Caller must hold a lock. [0,360).
    private static final double MAG_ITER_EPSILON = 0.01;   // degre
    private static final int MAG_ITER_MAX = 20;

    private double magneticFromTrueNoLock(double trueHeading, Compass compass) {
        double base = trueHeading - modelDeclNoLock() + _magDeclManual;
        double m = base;

        if (compass != null) {
            for (int i = 0; i < MAG_ITER_MAX; i++) {
                double previous = m;

                m = base + compass.deviationAt(m);

                if (Math.abs(m - previous) < MAG_ITER_EPSILON)
                    break;
            }
        }

        m %= 360.0;

        return (m < 0) ? (m + 360.0) : m;
    }

    // Cap magnetique affiche en navigation : les trois corrections, carte de deviation du compas
    // choisi comprise. Caller must hold a lock. [0,360).
    private double magneticHeadingNoLock() {
        return magneticFromTrueNoLock(_directionAngle, selectedCompassNoLock());
    }

    // Compas choisi dans les parametres, null si aucun. Sous verrou.
    private Compass selectedCompassNoLock() {
        if ((_compassIndex < 0) || (_compassIndex >= _compasses.size()))
            return null;

        return _compasses.get(_compassIndex);
    }

    // Bottom-right heading readout: true (T) by default, magnetic (M) after a tap. Superscript T/M.
    @Bindable
    public String getHeadingDisplayString() {
        double v;
        boolean mag;
        _readLock.lock();
        try {
            mag = _headingMagnetic;
            double heading = directionHeadingNoLock();   // cap vue when stopped
            v = mag ? magneticFromTrueNoLock(heading, selectedCompassNoLock()) : heading;
        } finally {
            _readLock.unlock();
        }
        return capDeg(v) + "°" + (mag ? "ᴹ" : "ᵀ");
    }

    // Tap on the heading readout: toggle true <-> magnetic, persisted (like the speed-unit tap).
    public void cycleHeadingMode() {
        Context context;
        boolean mag;
        _writeLock.lock();
        try {
            _headingMagnetic = !_headingMagnetic;
            mag = _headingMagnetic;
            context = _context;
        } finally {
            _writeLock.unlock();
        }
        persistInt(context, KEY_HEADING_MAG, mag ? 1 : 0);
        notifyPropertyChanged(BR.headingDisplayString);
        notifyPropertyChanged(BR.markCapString);
    }

    // #54 : declinaison du modele geomagnetique effectivement appliquee, 0 si l'utilisateur l'a
    // coupee. Caller must hold a lock.
    private double modelDeclNoLock() {
        return _declAutoOn ? _declModelDeg : 0.0;
    }

    // Interrupteur de la declinaison automatique. La couper fait afficher un cap magnetique corrige
    // de la seule correction manuelle : c'est le cap vrai tant que celle-ci vaut 0.
    @Bindable
    public boolean getDeclAuto() {
        _readLock.lock();
        try {
            return _declAutoOn;
        } finally {
            _readLock.unlock();
        }
    }

    public void setDeclAuto(boolean on) {
        Context context;
        boolean changed;

        _writeLock.lock();
        try {
            changed = (on != _declAutoOn);
            _declAutoOn = on;
            context = _context;
        } finally {
            _writeLock.unlock();
        }

        if (!changed)
            return;

        persistInt(context, KEY_DECL_AUTO, on ? 1 : 0);
        notifyPropertyChanged(BR.declAuto);
        notifyPropertyChanged(BR.declAutoString);
        notifyPropertyChanged(BR.declAutoModelString);
        notifyPropertyChanged(BR.directionAngleString);
        notifyPropertyChanged(BR.headingDisplayString);
        notifyPropertyChanged(BR.markCapString);
        notifyPropertyChanged(BR.adjustMagString);
        notifyPropertyChanged(BR.adjustArrowBadge);
        notifyPropertyChanged(BR.adjustHeadingTrue);
        notifyPropertyChanged(BR.adjustHeadingMagnetic);
    }

    // Ce que la declinaison du modele ajoute au cap, donc l'OPPOSE de la valeur du modele. "0.0°
    // (off)" quand l'interrupteur est coupe : la valeur existe toujours, mais elle n'est plus appliquee.
    @Bindable
    public String getDeclAutoString() {
        double v;
        boolean on;
        GPSCoordonate pos;

        _readLock.lock();
        try {
            v = _declModelDeg;
            on = _declAutoOn;
            pos = _lastKnownCoordonate;
        } finally {
            _readLock.unlock();
        }
        v = shownDeclDeg(v, pos);

        return on ? String.format(Locale.US, "%+.1f°", -v) : "0.0° (off)";
    }

    // La meme declinaison a la mode nautique : sa valeur absolue et son cote, "2.2° E" ou "1.5° W".
    // C'est ce que donnent les cartes, et c'est l'oppose de ce qui s'ajoute au cap.
    @Bindable
    public String getDeclAutoModelString() {
        double v;
        GPSCoordonate pos;

        _readLock.lock();
        try {
            v = _declModelDeg;
            pos = _lastKnownCoordonate;
        } finally {
            _readLock.unlock();
        }
        v = shownDeclDeg(v, pos);

        return String.format(Locale.US, "%.1f° %s", Math.abs(v), (v >= 0) ? "E" : "W");
    }

    // #98 : in Streamer mode the help text shows the declination of the fake place, not the real one
    // (it would hint at the region). The heading keeps using the real one. Called outside any lock.
    private static double shownDeclDeg(double realDeg, GPSCoordonate pos) {
        if( !StreamerMode.enabled )
            return realDeg;
        GPSCoordonate fake = StreamerMode.shift(pos);
        GeomagneticField geo = new GeomagneticField((float) fake.getLatitude(), (float) fake.getLongitude(),
                0.0f, System.currentTimeMillis());
        return geo.getDeclination();
    }

    // --- Manual magnetic-declination correction (Decl F parameter) ------------------------------
    public double getMagDecl() {
        double v;
        _readLock.lock();
        try {
            v = _magDeclManual;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    public void setMagDecl(double d) {
        if (d < DECL_MIN) d = DECL_MIN;
        if (d > DECL_MAX) d = DECL_MAX;
        Context context;
        _writeLock.lock();
        try {
            _magDeclManual = d;
            context = _context;
        } finally {
            _writeLock.unlock();
        }
        persistFloat(context, KEY_MAG_DECL, (float) d);
        notifyPropertyChanged(BR.declString);
        notifyPropertyChanged(BR.declSpinnerPos);
        notifyPropertyChanged(BR.directionAngleString); // magnetic part changed
        notifyPropertyChanged(BR.headingDisplayString);
        notifyPropertyChanged(BR.markCapString);
        notifyPropertyChanged(BR.adjustMagString);      // #54 : la declinaison entre dans le reglage
        notifyPropertyChanged(BR.adjustArrowBadge);
        notifyPropertyChanged(BR.adjustHeadingTrue);
        notifyPropertyChanged(BR.adjustHeadingMagnetic);
    }

    @Bindable
    public String getDeclLabel() {
        return (getDebugParam() == PARAM_DECL ? "*" : "") + "Decl";
    }

    @Bindable
    public String getDeclString() {
        double v;
        _readLock.lock();
        try {
            v = _magDeclManual;
        } finally {
            _readLock.unlock();
        }
        return String.format("%+.1f°", v);
    }

    // --- #54 : valeurs proposees pour le reglage Declination -------------------------------------
    // Memes bornes et meme pas que le parametre F "Decl" : -25.0 a +25.0 par 0,5, soit 101 entrees.
    public List<String> getDeclValues() {
        List<String> values = new ArrayList<>();

        for (double d = DECL_MIN; d <= DECL_MAX + (DECL_STEP / 2); d += DECL_STEP) {
            // pas de signe sur zero : un "+0.0" n'a pas de sens
            values.add((Math.abs(d) < (DECL_STEP / 2)) ? "0.0\u00b0"
                                                       : String.format(Locale.US, "%+.1f\u00b0", d));
        }

        return values;
    }

    // Position de la valeur courante dans la liste, sans passer par le getter lie (qui prend le
    // meme verrou).
    private int declSpinnerPosition() {
        double v;

        _readLock.lock();
        try {
            v = _magDeclManual;
        } finally {
            _readLock.unlock();
        }

        return (int) Math.round((v - DECL_MIN) / DECL_STEP);
    }

    // Position de la valeur courante dans cette liste : la liste s'ouvre donc deja deroulee dessus.
    @Bindable
    public int getDeclSpinnerPos() {
        return declSpinnerPosition();
    }

    // Retour de la liste deroulante. Le Spinner la rappelle aussi au premier affichage avec la
    // position deja en place : sans ce garde-fou on reecrirait la preference a chaque ouverture.
    public void setDeclSpinnerPos(int position) {
        double d = DECL_MIN + (position * DECL_STEP);
        double current;

        _readLock.lock();
        try {
            current = _magDeclManual;
        } finally {
            _readLock.unlock();
        }

        if (Math.abs(d - current) < (DECL_STEP / 2))
            return;

        setMagDecl(d);
    }

    // --- #54 : compas et cartes de deviation -----------------------------------------------------

    // Copie de la liste : l'adaptateur de la ListView la garde par reference et ne doit pas pouvoir
    // toucher a la notre.
    public List<Compass> getCompasses() {
        _readLock.lock();
        try {
            return new ArrayList<>(_compasses);
        } finally {
            _readLock.unlock();
        }
    }

    public int getSelectedCompassIndex() {
        _readLock.lock();
        try {
            return _compassIndex;
        } finally {
            _readLock.unlock();
        }
    }

    // Index d'un compas dans la liste, -1 s'il n'y est plus. Sert au bouton Edit des lignes, qui ne
    // connait que l'objet.
    public int indexOfCompass(Compass compass) {
        _readLock.lock();
        try {
            return _compasses.indexOf(compass);
        } finally {
            _readLock.unlock();
        }
    }

    // Choix du compas a utiliser. Hors bornes -> aucun compas (ligne "None" de la liste).
    public void setSelectedCompassIndex(int index) {
        Context context;
        boolean changed;

        _writeLock.lock();
        try {
            context = _context;

            if ((index < 0) || (index >= _compasses.size()))
                index = -1;

            changed = (index != _compassIndex);
            _compassIndex = index;

            for (int c = 0; c < _compasses.size(); c++)
                _compasses.get(c).setInUse(c == index);
        } finally {
            _writeLock.unlock();
        }

        if (changed) {
            persistCompasses(context);
            notifyPropertyChanged(BR.compassLabel);
            notifyPropertyChanged(BR.compassSelected);
            notifyHeadingChanged();
        }
    }

    // Vrai tant qu'un compas est choisi : la ligne "Compass" des parametres prend l'icone barree
    // quand ce n'est pas le cas, comme la ligne "None" de la liste.
    @Bindable
    public boolean getCompassSelected() {
        _readLock.lock();
        try {
            return (_compassIndex >= 0) && (_compassIndex < _compasses.size());
        } finally {
            _readLock.unlock();
        }
    }

    // Nom du compas choisi, pour la ligne "Compass" de la page Parameters.
    @Bindable
    public String getCompassLabel() {
        String name = null;

        _readLock.lock();
        try {
            if ((_compassIndex >= 0) && (_compassIndex < _compasses.size()))
                name = _compasses.get(_compassIndex).getName();
        } finally {
            _readLock.unlock();
        }

        return (name == null) ? "None" : name;
    }

    // Nom libre a partir d'une base : la base telle quelle si personne ne la porte, sinon suffixee
    // " 2", " 3"... L'appelant tient deja le verrou d'ecriture.
    private String uniqueCompassNameNoLock(String base) {
        String candidate = base;
        int suffix = 1;

        while (compassNameTakenNoLock(candidate)) {
            suffix++;
            candidate = base + " " + suffix;
        }

        return candidate;
    }

    private boolean compassNameTakenNoLock(String name) {
        for (Compass compass : _compasses) {
            if (compass.getName().equals(name))
                return true;
        }

        return false;
    }

    // Bouton New : cree un compas vierge et renvoie son index, pour ouvrir sa configuration dans la
    // foulee. Le compas cree n'est PAS choisi d'office.
    public int addCompass() {
        Context context;
        int index;

        _writeLock.lock();
        try {
            context = _context;
            _compasses.add(new Compass(uniqueCompassNameNoLock(COMPASS_DEFAULT_NAME)));
            index = _compasses.size() - 1;
        } finally {
            _writeLock.unlock();
        }

        persistCompasses(context);

        return index;
    }

    // Bouton Clone : copie complete du compas, carte de deviation comprise, sous un nom libre.
    public int cloneCompass(int index) {
        Context context;
        int clone;

        _writeLock.lock();
        try {
            context = _context;

            if ((index < 0) || (index >= _compasses.size())) {
                clone = -1;
            } else {
                Compass source = _compasses.get(index);
                _compasses.add(source.copy(uniqueCompassNameNoLock(source.getName())));
                clone = _compasses.size() - 1;
            }
        } finally {
            _writeLock.unlock();
        }

        if (clone >= 0)
            persistCompasses(context);

        return clone;
    }

    // Bouton Delete. Les index qui suivent glissent d'un cran : le compas choisi et le compas en
    // cours d'edition suivent le mouvement, et retombent a -1 si c'est eux qu'on vient d'effacer.
    public void removeCompass(int index) {
        Context context;
        boolean removed;

        _writeLock.lock();
        try {
            context = _context;
            removed = (index >= 0) && (index < _compasses.size());

            if (removed) {
                _compasses.remove(index);

                if (_compassIndex == index)
                    _compassIndex = -1;
                else if (_compassIndex > index)
                    _compassIndex--;

                if (_adjustCompass == index)
                    _adjustCompass = -1;
                else if (_adjustCompass > index)
                    _adjustCompass--;

                for (int c = 0; c < _compasses.size(); c++)
                    _compasses.get(c).setInUse(c == _compassIndex);
            }
        } finally {
            _writeLock.unlock();
        }

        if (removed) {
            persistCompasses(context);
            notifyPropertyChanged(BR.compassLabel);
            notifyPropertyChanged(BR.compassSelected);
            notifyHeadingChanged();
        }
    }

    // Reecriture complete de la liste dans les preferences : plus sur qu'un suivi des seules entrees
    // modifiees, et les compas se comptent sur les doigts d'une main. Les entrees au-dela de la
    // nouvelle taille sont effacees pour ne pas laisser trainer un compas supprime.
    private void persistCompasses(Context context) {
        SharedPreferences p = prefs(context);

        if (p == null)
            return;

        List<Compass> compasses;
        int selected;

        _readLock.lock();
        try {
            compasses = new ArrayList<>(_compasses);
            selected = _compassIndex;
        } finally {
            _readLock.unlock();
        }

        SharedPreferences.Editor editor = p.edit();
        int previousCount = p.getInt(KEY_COMPASS_COUNT, 0);

        editor.putInt(KEY_COMPASS_COUNT, compasses.size());
        editor.putInt(KEY_COMPASS_SELECTED, selected);

        for (int c = 0; c < compasses.size(); c++) {
            editor.putString(String.format(Locale.US, KEY_COMPASS_NAME, c), compasses.get(c).getName());
            editor.putString(String.format(Locale.US, KEY_COMPASS_DEV, c), compasses.get(c).serializeDeviations());
        }

        for (int c = compasses.size(); c < previousCount; c++) {
            editor.remove(String.format(Locale.US, KEY_COMPASS_NAME, c));
            editor.remove(String.format(Locale.US, KEY_COMPASS_DEV, c));
        }

        editor.apply();
    }

    // --- #54 : page de configuration d'un compas -------------------------------------------------

    // Les 8 cases de la rose. Liste fixe : la page s'y attache par data binding et chaque case se
    // redessine seule, on ne la remplace jamais.
    public List<CompassSector> getCompassSectors() {
        return _compassSectors;
    }

    // Ouverture de la page de configuration : on retient le compas edite et on repart du nord.
    public void beginCompassAdjust(int index) {
        _writeLock.lock();
        try {
            _adjustCompass = ((index >= 0) && (index < _compasses.size())) ? index : -1;
            _adjustSector = 0;
        } finally {
            _writeLock.unlock();
        }

        refreshCompassSectors();
        notifyPropertyChanged(BR.compassEditName);
        notifyPropertyChanged(BR.adjustTrueString);
        notifyPropertyChanged(BR.adjustMagString);
        notifyPropertyChanged(BR.adjustValueString);
        notifyPropertyChanged(BR.adjustInstructionString);
        notifyPropertyChanged(BR.adjustCurveData);
        notifyPropertyChanged(BR.adjustSector);
        notifyPropertyChanged(BR.adjustArrowBadge);
    }

    public void endCompassAdjust() {
        _writeLock.lock();
        try {
            _adjustCompass = -1;
        } finally {
            _writeLock.unlock();
        }
    }

    public int getAdjustCompassIndex() {
        _readLock.lock();
        try {
            return _adjustCompass;
        } finally {
            _readLock.unlock();
        }
    }

    // Un tap sur une case de la rose : c'est desormais elle, et elle seule, qui corrige le cap M.
    public void selectCompassSector(int sector) {
        if ((sector < 0) || (sector >= Compass.SECTOR_COUNT))
            return;

        _writeLock.lock();
        try {
            _adjustSector = sector;
        } finally {
            _writeLock.unlock();
        }

        refreshCompassSectors();
        notifyPropertyChanged(BR.adjustMagString);
        notifyPropertyChanged(BR.adjustValueString);
        notifyPropertyChanged(BR.adjustInstructionString);
        notifyPropertyChanged(BR.adjustSector);
        notifyPropertyChanged(BR.adjustArrowBadge);
        notifyPropertyChanged(BR.adjustHeadingTrue);
        notifyPropertyChanged(BR.adjustHeadingMagnetic);
    }

    // Boutons - / + : un pas sur la valeur du point cardinal en cours de reglage.
    public void adjustDeviation(int steps) {
        Context context;
        boolean changed = false;

        _writeLock.lock();
        try {
            context = _context;

            if ((_adjustCompass >= 0) && (_adjustCompass < _compasses.size())) {
                Compass compass = _compasses.get(_adjustCompass);
                double before = compass.getDeviation(_adjustSector);

                compass.setDeviation(_adjustSector, before + (steps * Compass.DEV_STEP));
                changed = (compass.getDeviation(_adjustSector) != before);
            }
        } finally {
            _writeLock.unlock();
        }

        if (changed) {
            persistCompasses(context);
            refreshCompassSectors();
            notifyPropertyChanged(BR.adjustMagString);
            notifyPropertyChanged(BR.adjustValueString);
            notifyPropertyChanged(BR.adjustCurveData);
            notifyPropertyChanged(BR.adjustArrowBadge);
            notifyPropertyChanged(BR.adjustHeadingTrue);
            notifyPropertyChanged(BR.adjustHeadingMagnetic);
            notifyHeadingChanged();   // la carte reglee peut etre celle qui sert en navigation
        }
    }

    // Recharge les 8 cases depuis le compas edite : leurs valeurs et celle qui est selectionnee.
    private void refreshCompassSectors() {
        double[] values = new double[Compass.SECTOR_COUNT];
        int selected;

        _readLock.lock();
        try {
            selected = _adjustSector;

            if ((_adjustCompass >= 0) && (_adjustCompass < _compasses.size())) {
                Compass compass = _compasses.get(_adjustCompass);

                for (int s = 0; s < Compass.SECTOR_COUNT; s++)
                    values[s] = compass.getDeviation(s);
            }
        } finally {
            _readLock.unlock();
        }

        for (int s = 0; s < Compass.SECTOR_COUNT; s++) {
            CompassSector sector = _compassSectors.get(s);

            sector.setValue(Compass.formatDeviation(values[s]));
            sector.setSelected(s == selected);
        }
    }

    // Nom du compas edite, modifiable depuis le titre de la page. Pas de controle d'unicite ici :
    // on se battrait avec l'utilisateur a chaque caractere tape. L'unicite ne vaut que pour les noms
    // proposes d'office, a la creation et au clonage.
    @Bindable
    public String getCompassEditName() {
        String name = "";

        _readLock.lock();
        try {
            if ((_adjustCompass >= 0) && (_adjustCompass < _compasses.size()))
                name = _compasses.get(_adjustCompass).getName();
        } finally {
            _readLock.unlock();
        }

        return name;
    }

    public void setCompassEditName(String name) {
        Context context;
        boolean changed = false;

        if (name == null)
            name = "";

        _writeLock.lock();
        try {
            context = _context;

            if ((_adjustCompass >= 0) && (_adjustCompass < _compasses.size())) {
                Compass compass = _compasses.get(_adjustCompass);

                if (!compass.getName().equals(name)) {
                    compass.setName(name);
                    changed = true;
                }
            }
        } finally {
            _writeLock.unlock();
        }

        if (changed) {
            persistCompasses(context);
            notifyPropertyChanged(BR.compassLabel);
        }
    }

    // Cap M de la page de configuration. Il passe par la MEME fonction que celle qui sera branchee
    // sur le cap de navigation, appliquee au compas en cours d'edition : les 8 valeurs de la carte y
    // entrent donc, interpolees, et pas seulement celle qu'on est en train de regler.
    // Sous verrou. [0,360).
    private double adjustMagneticNoLock() {
        return magneticFromTrueNoLock(_directionAngle, adjustedCompassNoLock());
    }

    // Le cap affiche depend maintenant du compas choisi et de sa carte : tout ce qui touche a l'un
    // ou a l'autre doit rafraichir les caps.
    private void notifyHeadingChanged() {
        notifyPropertyChanged(BR.directionAngleString);
        notifyPropertyChanged(BR.headingDisplayString);
        notifyPropertyChanged(BR.markCapString);
    }

    // Compas en cours d'edition, null si la page de configuration n'est pas ouverte. Sous verrou.
    private Compass adjustedCompassNoLock() {
        if ((_adjustCompass < 0) || (_adjustCompass >= _compasses.size()))
            return null;

        return _compasses.get(_adjustCompass);
    }

    // Les deux caps affiches au centre de la rose : le cap vrai en petit, le cap corrige en gros.
    @Bindable
    public String getAdjustTrueString() {
        double v;

        _readLock.lock();
        try {
            v = _directionAngle;
        } finally {
            _readLock.unlock();
        }

        return capDeg(v) + "\u00b0\u1d40";
    }

    @Bindable
    public String getAdjustMagString() {
        double v;

        _readLock.lock();
        try {
            v = adjustMagneticNoLock();
        } finally {
            _readLock.unlock();
        }

        return capDeg(v) + "\u00b0\u1d39";
    }

    // Les 8 valeurs du compas edite, serialisees : la chaine change des qu'une correction bouge, ce
    // qui suffit a la courbe pour se redessiner.
    @Bindable
    public String getAdjustCurveData() {
        String data = "";

        _readLock.lock();
        try {
            if ((_adjustCompass >= 0) && (_adjustCompass < _compasses.size()))
                data = _compasses.get(_adjustCompass).serializeDeviations();
        } finally {
            _readLock.unlock();
        }

        return data;
    }

    // Les deux reperes traces sur la courbe : le cap vrai et le cap compas. Leur ecart sur l'axe est
    // la correction totale appliquee au cap.
    @Bindable
    public double getAdjustHeadingTrue() {
        _readLock.lock();
        try {
            return _directionAngle;
        } finally {
            _readLock.unlock();
        }
    }

    @Bindable
    public double getAdjustHeadingMagnetic() {
        _readLock.lock();
        try {
            return adjustMagneticNoLock();
        } finally {
            _readLock.unlock();
        }
    }

    // Point cardinal en cours de reglage : la courbe met son point en evidence.
    @Bindable
    public int getAdjustSector() {
        _readLock.lock();
        try {
            return _adjustSector;
        } finally {
            _readLock.unlock();
        }
    }

    // Fleche de guidage du reglage : l'ECART entre le cap M affiche et le cap a prendre. Elle tourne
    // quand on tourne et pointe vers le HAUT quand le cap M indique le cap vise, c'est-a-dire au
    // moment precis ou la valeur est bonne. Meme pas de 45 degres que les badges des marks.
    //
    // Le cap M et non le cap vrai : le cap vise est une graduation de la carte de deviation, donc un
    // cap COMPAS, et les deux termes d'un gisement doivent partager le meme referentiel. Voir la
    // regle detaillee au-dessus de magneticFromTrueNoLock().
    @Bindable
    public Drawable getAdjustArrowBadge() {
        Context context;
        int sector;
        double magnetic;

        _readLock.lock();
        try {
            context = _context;
            sector = _adjustSector;
            magnetic = adjustMagneticNoLock();
        } finally {
            _readLock.unlock();
        }

        if (context == null)
            return null;

        int index = (int) Math.round((Compass.sectorHeading(sector) - magnetic) / 45.0);

        return ContextCompat.getDrawable(context, PersonalMark.arrowBadgeRes(index));
    }

    // Consigne affichee au-dessus des boutons : le cap a prendre et ce qu'on attend de l'utilisateur.
    @Bindable
    public String getAdjustInstructionString() {
        int sector;

        _readLock.lock();
        try {
            sector = _adjustSector;
        } finally {
            _readLock.unlock();
        }

        int heading = Compass.sectorHeading(sector);

        return "Steer " + heading + "\u00b0 on your own compass, then use - / + until the M reading shows "
                + heading + "\u00b0.";
    }

    // Rappel de ce qu'on regle, entre les deux gros boutons : "NE  +2.5\u00b0".
    @Bindable
    public String getAdjustValueString() {
        int sector;
        double deviation = 0.0;

        _readLock.lock();
        try {
            sector = _adjustSector;

            if ((_adjustCompass >= 0) && (_adjustCompass < _compasses.size()))
                deviation = _compasses.get(_adjustCompass).getDeviation(sector);
        } finally {
            _readLock.unlock();
        }

        return Compass.SECTORS[sector] + "   " + Compass.formatDeviation(deviation);
    }

    // --- Right-list proximity radius (ProxDist F parameter) --------------------------------------
    public int getProxMeters() {
        int v;
        _readLock.lock();
        try {
            v = _proxMeters;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    public void setProxMeters(int m) {
        if (m < PROXDIST_MIN) m = PROXDIST_MIN;
        if (m > PROXDIST_MAX) m = PROXDIST_MAX;
        Context context;
        _writeLock.lock();
        try {
            _proxMeters = m;
            context = _context;
        } finally {
            _writeLock.unlock();
        }
        persistInt(context, KEY_PROX_METERS, m);
        notifyPropertyChanged(BR.proxString);
        notifyPropertyChanged(BR.proxPos);
    }

    @Bindable
    public String getProxLabel() {
        return (getDebugParam() == PARAM_PROXDIST ? "*" : "") + "ProxDist";
    }

    @Bindable
    public String getProxString() {
        int v;
        _readLock.lock();
        try {
            v = _proxMeters;
        } finally {
            _readLock.unlock();
        }
        return v + " m";
    }

    public List<String> getProxValues() {
        List<String> values = new ArrayList<>();

        for (int m = PROXDIST_MIN; m <= PROXDIST_MAX; m += PROXDIST_STEP)
            values.add(m + " m");

        return values;
    }

    @Bindable
    public int getProxPos() {
        return (getProxMeters() - PROXDIST_MIN) / PROXDIST_STEP;
    }

    public void setProxPos(int position) {
        int m = PROXDIST_MIN + (position * PROXDIST_STEP);

        if (m == getProxMeters())
            return;

        setProxMeters(m);
    }

    // --- Marks list: height difference threshold -------------------------------------------------
    public void setElevMinMeters(int m) {
        Context context;
        _writeLock.lock();
        try {
            _elevMinMeters = m;
            context = _context;
        } finally {
            _writeLock.unlock();
        }
        persistInt(context, KEY_ELEV_MIN_METERS, m);
        notifyPropertyChanged(BR.elevMinPos);
    }

    public List<String> getElevMinValues() {
        List<String> values = new ArrayList<>();

        for (int m : ELEVMIN_STEPS) {
            if (m == 0)
                values.add("Never");
            else
                values.add(m + " m");
        }

        return values;
    }

    // A stored value off the list (none today) shows as the nearest step below it.
    @Bindable
    public int getElevMinPos() {
        int v;
        _readLock.lock();
        try {
            v = _elevMinMeters;
        } finally {
            _readLock.unlock();
        }

        int pos = 0;
        for (int i = 0; i < ELEVMIN_STEPS.length; i++) {
            if (ELEVMIN_STEPS[i] <= v)
                pos = i;
        }
        return pos;
    }

    public void setElevMinPos(int position) {
        if (position < 0 || position >= ELEVMIN_STEPS.length)
            return;

        if (position == getElevMinPos())
            return;

        setElevMinMeters(ELEVMIN_STEPS[position]);
    }

    // --- #67 : rayon de la recherche "Around me" ------------------------------------------------
    public int getSearchRadiusKm() {
        return readSearchRadiusKm();
    }

    public void setSearchRadiusKm(int km) {
        writeSearchRadiusKm(km);
    }

    @Bindable
    public String getSearchRadiusString() {
        return readSearchRadiusKm() + " km";
    }

    public List<String> getSearchRadiusValues() {
        List<String> values = new ArrayList<>();

        for (int km : SEARCHRAD_VALUES_KM)
            values.add(km + " km");

        return values;
    }

    @Bindable
    public int getSearchRadiusPos() {
        int km = readSearchRadiusKm();
        int pos = 0;

        // Last entry not above the stored value: an unknown value falls on its lower neighbour.
        for (int i = 0; i < SEARCHRAD_VALUES_KM.length; i++) {
            if (SEARCHRAD_VALUES_KM[i] <= km)
                pos = i;
        }
        return pos;
    }

    public void setSearchRadiusPos(int position) {
        if (position < 0 || position >= SEARCHRAD_VALUES_KM.length)
            return;

        int km = SEARCHRAD_VALUES_KM[position];

        if (km == readSearchRadiusKm())
            return;

        writeSearchRadiusKm(km);
    }

    private int readSearchRadiusKm() {
        int v;
        _readLock.lock();
        try {
            v = _searchRadiusKm;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    private void writeSearchRadiusKm(int km) {
        if (km < SEARCHRAD_VALUES_KM[0])
            km = SEARCHRAD_VALUES_KM[0];
        if (km > SEARCHRAD_VALUES_KM[SEARCHRAD_VALUES_KM.length - 1])
            km = SEARCHRAD_VALUES_KM[SEARCHRAD_VALUES_KM.length - 1];

        Context context;
        _writeLock.lock();
        try {
            _searchRadiusKm = km;
            context = _context;
        } finally {
            _writeLock.unlock();
        }

        persistInt(context, KEY_SEARCH_RADIUS, km);
        notifyPropertyChanged(BR.searchRadiusPos);
        notifyPropertyChanged(BR.searchRadiusString);
    }

    public int getSearchLimit() {
        int v;
        _readLock.lock();
        try {
            v = _searchLimit;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    public void setSearchLimit(int limit) {
        if (limit < SEARCHLIMIT_MIN) limit = SEARCHLIMIT_MIN;
        if (limit > SEARCHLIMIT_MAX) limit = SEARCHLIMIT_MAX;

        Context context;
        _writeLock.lock();
        try {
            _searchLimit = limit;
            context = _context;
        } finally {
            _writeLock.unlock();
        }

        persistInt(context, KEY_SEARCH_LIMIT, limit);
        notifyPropertyChanged(BR.searchLimitPos);
    }

    public List<String> getSearchLimitValues() {
        List<String> values = new ArrayList<>();

        for (int n = SEARCHLIMIT_MIN; n <= SEARCHLIMIT_MAX; n += SEARCHLIMIT_STEP)
            values.add(String.valueOf(n));

        return values;
    }

    @Bindable
    public int getSearchLimitPos() {
        return (getSearchLimit() - SEARCHLIMIT_MIN) / SEARCHLIMIT_STEP;
    }

    public void setSearchLimitPos(int position) {
        int n = SEARCHLIMIT_MIN + (position * SEARCHLIMIT_STEP);

        if (n == getSearchLimit())
            return;

        setSearchLimit(n);
    }

    public int getSamePlaceM() {
        int v;
        _readLock.lock();
        try {
            v = _samePlaceM;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    public void setSamePlaceM(int m) {
        if (m < SAMEPLACE_MIN) m = SAMEPLACE_MIN;
        if (m > SAMEPLACE_MAX) m = SAMEPLACE_MAX;

        Context context;
        _writeLock.lock();
        try {
            _samePlaceM = m;
            context = _context;
        } finally {
            _writeLock.unlock();
        }

        persistInt(context, KEY_SAME_PLACE, m);
        notifyPropertyChanged(BR.samePlacePos);
    }

    @Bindable
    public boolean isSamePlaceTitle() {
        boolean v;
        _readLock.lock();
        try {
            v = _samePlaceTitle;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    public void setSamePlaceTitle(boolean on) {
        Context context;
        _writeLock.lock();
        try {
            _samePlaceTitle = on;
            context = _context;
        } finally {
            _writeLock.unlock();
        }

        persistInt(context, KEY_SAME_PLACE_TITLE, on ? 1 : 0);
        notifyPropertyChanged(BR.samePlaceTitle);
    }

    public List<String> getSamePlaceValues() {
        List<String> values = new ArrayList<>();

        for (int m = SAMEPLACE_MIN; m <= SAMEPLACE_MAX; m += SAMEPLACE_STEP)
            values.add(m + " m");

        return values;
    }

    @Bindable
    public int getSamePlacePos() {
        return (getSamePlaceM() - SAMEPLACE_MIN) / SAMEPLACE_STEP;
    }

    public void setSamePlacePos(int position) {
        int m = SAMEPLACE_MIN + (position * SAMEPLACE_STEP);

        if (m == getSamePlaceM())
            return;

        setSamePlaceM(m);
    }

    /**
     * #67 : la marque deja posee sur ce lieu, ou null.
     *
     * La POSITION compte toujours : a moins de SamePlace metres, une marque nee d'un resultat de
     * recherche en portant les coordonnees exactes, a l'arrondi de l'editeur pres.
     *
     * Le TITRE ne compte que si le reglage SamePlaceTitle est actif. Actif, il evite de confondre
     * deux etablissements loges a la meme adresse, mais une marque renommee apres coup n'est plus
     * reconnue et la page reproposera de la creer. Inactif -- le defaut -- la position fait foi.
     *
     * La plus proche gagne s'il y en a plusieurs.
     */
    public PersonalMark findMarkAt(double latitude, double longitude, String title) {
        return findMarkAt(latitude, longitude, null, title);
    }

    // Same, the altitude counting too when the place and the mark both have one.
    public PersonalMark findMarkAt(double latitude, double longitude, Double altitude, String title) {

        boolean checkTitle = isSamePlaceTitle();
        String wanted = (title == null) ? "" : title.trim();

        if (checkTitle && (wanted.length() == 0))
            return null;

        double radiusKm = getSamePlaceM() / 1000.0;
        PersonalMark best = null;
        double bestKm = Double.MAX_VALUE;

        _readLock.lock();
        try {
            if (_marks == null)
                return null;

            for (PersonalMark mark : _marks) {

                if (checkTitle) {
                    String markTitle = mark.getTitle();

                    if ((markTitle == null) || !markTitle.trim().equalsIgnoreCase(wanted))
                        continue;
                }

                double km = Chunk.measureKm(latitude, longitude, altitude, mark.getLatitude(), mark.getLongitude(), mark.getAltitude());

                if ((km <= radiusKm) && (km < bestKm)) {
                    bestKm = km;
                    best = mark;
                }
            }
        } finally {
            _readLock.unlock();
        }

        return best;
    }

    /**
     * #67 : distance en kilometres entre la position courante et un point quelconque. La page de
     * recherche s'en sert pour classer les reponses BRUTES avant d'en garder les plus proches, sans
     * avoir a construire une marque pour chacune.
     */
    public double distanceKmTo(double latitude, double longitude) {

        double curLat, curLon;

        _readLock.lock();
        try {
            curLat = _lastKnownCoordonate.getLatitude();
            curLon = _lastKnownCoordonate.getLongitude();
        } finally {
            _readLock.unlock();
        }

        return Chunk.measureKm(latitude, longitude, curLat, curLon);
    }

    /**
     * #67 : la boite de recherche autour de la position courante, prete pour Nominatim, ou null
     * tant qu'aucune position n'est connue -- il n'y a alors rien a centrer, et Null Island n'est
     * pas une reponse. Position et rayon sont lus d'un seul tenant sous le verrou.
     */
    public String getSearchViewbox() {

        double latitude, longitude;
        int radiusKm;

        _readLock.lock();
        try {
            latitude = _lastKnownCoordonate.getLatitude();
            longitude = _lastKnownCoordonate.getLongitude();
            radiusKm = _searchRadiusKm;
        } finally {
            _readLock.unlock();
        }

        if ((latitude == 0.0) && (longitude == 0.0))
            return null;

        return GeoSearch.viewbox(latitude, longitude, radiusKm);
    }

    // --- Mark warning alarm trigger (WarnDist / WarnETA F parameters, #7) -------------------------
    public int getWarnDistM() {
        int v;
        _readLock.lock();
        try { v = _warnDistM; } finally { _readLock.unlock(); }
        return v;
    }

    public void setWarnDistM(int m) {
        if (m < WARNDIST_MIN) m = WARNDIST_MIN;
        if (m > WARNDIST_MAX) m = WARNDIST_MAX;
        Context context;
        _writeLock.lock();
        try { _warnDistM = m; context = _context; } finally { _writeLock.unlock(); }
        persistInt(context, KEY_WARN_DIST, m);
        notifyPropertyChanged(BR.warnDistString);
        notifyPropertyChanged(BR.warnDistPos);
    }

    public int getWarnEtaSec() {
        int v;
        _readLock.lock();
        try { v = _warnEtaSec; } finally { _readLock.unlock(); }
        return v;
    }

    public void setWarnEtaSec(int s) {
        if (s < WARNETA_MIN) s = WARNETA_MIN;
        if (s > WARNETA_MAX) s = WARNETA_MAX;
        Context context;
        _writeLock.lock();
        try { _warnEtaSec = s; context = _context; } finally { _writeLock.unlock(); }
        persistInt(context, KEY_WARN_ETA, s);
        notifyPropertyChanged(BR.warnEtaString);
        notifyPropertyChanged(BR.warnEtaPos);
    }

    @Bindable
    public String getWarnDistLabel() {
        return (getDebugParam() == PARAM_WARNDIST ? "*" : "") + "WarnDist";
    }

    @Bindable
    public String getWarnDistString() {
        int v;
        _readLock.lock();
        try { v = _warnDistM; } finally { _readLock.unlock(); }
        return v + " m";
    }

    @Bindable
    public String getWarnEtaLabel() {
        return (getDebugParam() == PARAM_WARNETA ? "*" : "") + "WarnETA";
    }

    @Bindable
    public String getWarnEtaString() {
        int v;
        _readLock.lock();
        try { v = _warnEtaSec; } finally { _readLock.unlock(); }
        return v + " s";
    }

    // --- Easter egg: level, the two switches, and the announcement ------------------------------

    public int getGameLevel() {
        int v;
        _readLock.lock();
        try { v = _gameLevel; } finally { _readLock.unlock(); }
        return v;
    }

    // Is the game on? Everything the easter egg adds hangs off this one question, asked from the
    // render thread on every frame -- one lock and a comparison, nothing more. It exists as a
    // method of its own rather than a "level > 0" written all over the place, because the answer
    // will not always be that: a pause, or the game-over window, would change it HERE and nowhere
    // else.
    public boolean getIsGameMode() {
        int v;
        _readLock.lock();
        try { v = _gameLevel; } finally { _readLock.unlock(); }
        return (v > 0);
    }

    // The single way in. Every transition goes through here, so the two switches cannot be missed:
    // leaving 0 starts a game, coming back to 0 ends it. Moving inside 1..9 is just a level change
    // and announces nothing.
    public void setGameLevel(int level) {
        if (level < 0)
            level = 0;

        int before;
        _writeLock.lock();
        try {
            if (level == _gameLevel)
                return;
            before = _gameLevel;
            _gameLevel = level;
        } finally {
            _writeLock.unlock();
        }

        // Outside the lock: an announcement must never be raised with the write lock held.
        if (before == 0)
            startGame();
        else if (level == 0)
            endGame();

        // Monter d'un cran paie, et paie d'autant plus haut qu'on monte. Redescendre ne rapporte
        // rien, et la fin de partie non plus.
        if ((level > before) && (level > 0) && (before > 0))
            scoreAtPlayer(LEVEL_UP_POINTS * level);

        notifyPropertyChanged(BR.gameLevelString);
        notifyGamePanel();
    }

    /**
     * Walks away from the game: level zero, and nothing said about it.
     *
     * The same one door as everywhere else -- setGameLevel(0) -- with a flag raised beforehand
     * that endGame() reads. Leaving on purpose is not losing: no announcement, no "play again"
     * window, and the creatures are blown up at once instead of after a sentence nobody said.
     * The score still counts, since the points were earned.
     */
    public void quitGame() {
        _gameQuit = true;
        setGameLevel(0);
    }

    /** Whether the game that just ended was walked away from. */
    public boolean wasGameQuit() {
        return _gameQuit;
    }

    public void startGame() {
        _gameQuit = false;

        _writeLock.lock();
        try {
            _score = 0;
            _lives = GAME_LIVES;
            _hitsInLife = 0;
            _streak = 0;
            _gameWave = 1;
            _energy = ENERGY_MAX;
            _energyAtMs = 0;
            _gameOverSoundRes = 0;
        } finally {
            _writeLock.unlock();
        }
        _gameOverUntilMs = 0;               // a new game closes the "play again" window
        announce(ANNOUNCE_FIGHT);
        notifyGamePanel();
    }

    /** The finger gets its wind back, all of it at once: what a pole pays besides points. */
    public void refillEnergy() {

        _writeLock.lock();
        try {
            _energy = ENERGY_MAX;
            _energyAtMs = 0;
        } finally {
            _writeLock.unlock();
        }
    }

    /**
     * Takes one point of energy for a blow by hand, or refuses it.
     *
     * False when there is not a whole point left, and the caller must then do no damage at all:
     * the creature answers with its squeak and walks on.
     */
    public boolean spendEnergy() {

        long nowMs = SystemClock.elapsedRealtime();

        boolean paid;

        _writeLock.lock();
        try {
            double left = energyAt(nowMs);

            paid = (left >= 1.0);

            _energy = paid ? (left - 1.0) : left;
            _energyAtMs = nowMs;
        } finally {
            _writeLock.unlock();
        }

        return paid;
    }

    /** How much energy the finger has, 0 to ENERGY_MAX, the point being refilled included. */
    public double getEnergy() {

        long nowMs = SystemClock.elapsedRealtime();

        double left;

        _readLock.lock();
        try {
            left = energyAt(nowMs);
        } finally {
            _readLock.unlock();
        }

        return left;
    }

    // What the energy has grown back to by then. Arithmetic only: it is called with a lock held.
    private double energyAt(long nowMs) {

        if (_energyAtMs <= 0)
            return _energy;

        double grown = _energy + ((nowMs - _energyAtMs) / (double) ENERGY_RECOVER_MS);

        return Math.min(ENERGY_MAX, grown);
    }

    /**
     * The wave being fought, counted from one.
     *
     * The orchestrator's to set -- which of the ten waves the player is on is a rule, not a state
     * the application decides -- and the panel's to show, beside the level.
     */
    public void setGameWave(int wave) {

        _writeLock.lock();
        try {
            if (wave == _gameWave)
                return;
            _gameWave = wave;
        } finally {
            _writeLock.unlock();
        }

        notifyPropertyChanged(BR.gameLevelLine);
    }

    /**
     * Where the level is up to, for the progress bar: how far in waves, out of how many, and how
     * much the player is holding it up.
     *
     * Pushed by the orchestrator a few times a second rather than asked for, so that nothing has
     * to poll the rules to draw a bar.
     */
    public void setWaveState(float progress, int count, int stress) {

        if ((progress == _waveProgress) && (count == _waveCount) && (stress == _waveStress))
            return;

        _waveProgress = progress;
        _waveCount = count;
        _waveStress = stress;

        notifyPropertyChanged(BR.waveProgress);
        notifyPropertyChanged(BR.waveCount);
        notifyPropertyChanged(BR.waveStress);
    }

    @Bindable
    public float getWaveProgress() {
        return _waveProgress;
    }

    @Bindable
    public int getWaveCount() {
        return _waveCount;
    }

    @Bindable
    public int getWaveStress() {
        return _waveStress;
    }

    /**
     * A level cleared, said out loud: the tune, and the banner that names the one starting.
     *
     * Which of the two is the orchestrator's business, since it is the one that knows what the
     * next level is made of.
     */
    public void announceLevelUp(int what) {

        if ((what != ANNOUNCE_LEVEL_UP) && (what != ANNOUNCE_LEVEL_UP_BOSS))
            return;

        announce(what);
    }

    /**
     * The hurry up, said out loud: the first notch, then the second, and nothing after -- at that
     * point the player has understood, and one announcement per notch would end up hiding the game.
     *
     * WHICH of the four it says is the orchestrator's business, since it is the one that knows what
     * it has already said in this wave.
     *
     * Image and voice are both in: res/drawable-nodpi/eg_hurry_up.png and friends for the banner,
     * res/raw/eg_hurry_up.ogg and friends for the voice, which GameSurface pairs up.
     */
    public void announceHurry(int what) {

        if ((what < ANNOUNCE_HURRY) || (what >= (ANNOUNCE_HURRY + ANNOUNCE_HURRY_COUNT)))
            return;

        announce(what);
    }

    // One life gone. The third one ends the game: setGameLevel(0) fires endGame() by itself, so
    // there is nothing else to do here -- and the two paths out of a game stay the same one.
    public void loseLife() {
        int left;
        _writeLock.lock();
        try {
            if (_lives > 0)
                _lives--;
            left = _lives;
        } finally {
            _writeLock.unlock();
        }

        notifyGamePanel();

        if (left <= 0)
            setGameLevel(0);
    }

    /**
     * Le joueur encaisse des degats.
     *
     * Trois coups font une vie, trois vies font la partie. Le son de fin est celui que la source
     * du coup a demande -- la balle de la poupee dit "you are dead" d'elle-meme -- et zero laisse
     * celui du jeu.
     */
    public void takePlayerHits(int hits, int gameOverSoundRes) {

        if (hits <= 0)
            return;

        int perdues = 0;

        _writeLock.lock();
        try {
            _hitsInLife += hits;

            while ((_hitsInLife >= GAME_HITS_PER_LIFE) && (_lives > 0)) {
                _hitsInLife -= GAME_HITS_PER_LIFE;
                perdues++;
            }
        } finally {
            _writeLock.unlock();
        }

        _gameOverSoundRes = gameOverSoundRes;

        // Un coup encaisse coute la serie, quelle qu'elle soit.
        breakStreak();

        notifyGamePanel();

        // Hors verrou : loseLife notifie et peut terminer la partie.
        for (int i = 0; i < perdues; i++)
            loseLife();
    }

    /**
     * Gives back that many PORTIONS of heart -- a portion being one hit, three to a life.
     *
     * The mirror of takePlayerHits: a portion first undoes a hit taken in the life being lived,
     * and once that life is whole it buys back a lost one, which comes back with a single portion
     * in it rather than whole. Nothing is given to a player who has everything.
     *
     * Returns how many portions were actually used, so that the caller can keep what it could not
     * give -- or say nothing at all when nothing was taken.
     */
    public int healPlayer(int portions) {

        if (portions <= 0)
            return 0;

        int used = 0;

        _writeLock.lock();
        try {
            while (portions > 0) {

                if (_hitsInLife > 0) {
                    _hitsInLife--;
                } else if (_lives < GAME_LIVES) {
                    _lives++;
                    _hitsInLife = GAME_HITS_PER_LIFE - 1;
                } else {
                    break;                      // whole: there is nothing left to mend
                }

                used++;
                portions--;
            }
        } finally {
            _writeLock.unlock();
        }

        if (used > 0)
            notifyGamePanel();

        return used;
    }

    /** Coups encaisses dans la vie en cours, pour la barre sous le marqueur. */
    public int getHitsInLife() {
        _readLock.lock();
        try { return _hitsInLife; } finally { _readLock.unlock(); }
    }

    /** Le son de fin de partie demande par le dernier coup, ou 0 pour celui du jeu. */
    public int getGameOverSoundRes() {
        return _gameOverSoundRes;
    }

    public int getLives() {
        _readLock.lock();
        try { return _lives; } finally { _readLock.unlock(); }
    }

    /**
     * Full life: every heart, and every hit of the current one still to be taken.
     *
     * The two are read under the SAME lock. Asked through the two getters one after the other, a
     * hit landing between the two reads would answer full life when it no longer is.
     */
    public boolean getPlayerFullLife() {
        _readLock.lock();
        try {
            return (_lives >= GAME_LIVES) && (_hitsInLife <= 0);
        } finally {
            _readLock.unlock();
        }
    }

    public int getScore() {
        _readLock.lock();
        try { return _score; } finally { _readLock.unlock(); }
    }

    public void addScore(int points) {
        _writeLock.lock();
        try { _score += points; } finally { _writeLock.unlock(); }
        notifyPropertyChanged(BR.gameScoreLine);
        notifyPropertyChanged(BR.gameScoreTextSize);
    }

    /**
     * Combien vaut un point marque en ce moment, par la serie en cours.
     *
     * La serie compte les creatures tuees sans encaisser un seul coup. Elle recompense le jeu
     * propre, et elle se perd entierement au premier coup recu : c'est ce qui la rend tenante.
     */
    public int getStreakMultiplier() {

        int streak;

        _readLock.lock();
        try { streak = _streak; } finally { _readLock.unlock(); }

        if (streak >= STREAK_X3)
            return 3;

        if (streak >= STREAK_X2)
            return 2;

        return 1;
    }

    /**
     * Marque des points qui n'ont pas de point d'impact -- un palier franchi, par exemple.
     *
     * Ils paraissent au-dessus du marqueur : c'est le seul endroit de la carte que le joueur
     * regarde a coup sur. Sans surface sous la main, les points sont tout de meme comptes.
     */
    private void scoreAtPlayer(int points) {

        GameSurface surface = getGameSurface();

        MapGPSPos player = (surface == null) ? null : surface.getGps();

        if( player == null ) {
            addScore(points * getStreakMultiplier());
            return;
        }

        ScorePopup.score(surface,
                         player.getAnchorOriginX(),
                         player.getAnchorOriginY() - PLAYER_POPUP_UP,
                         points);
    }

    /** Une victime de plus dans la serie. */
    public void addStreak() {
        _writeLock.lock();
        try { _streak++; } finally { _writeLock.unlock(); }
    }

    /** Un coup encaisse : la serie tombe. */
    public void breakStreak() {
        _writeLock.lock();
        try { _streak = 0; } finally { _writeLock.unlock(); }
    }

    // Game over, however it was reached -- the three lives spent, or the player walking away.
    // The lives are forced to zero here rather than at each caller: whatever ended the game, the
    // gauge has to show an empty ring while the window is open, and there is only one way out.
    public void endGame() {

        int score;
        boolean record;

        _writeLock.lock();
        try {
            _lives = 0;

            score = _score;
            record = (_score > _bestScore);

            if (record)
                _bestScore = _score;
        } finally {
            _writeLock.unlock();
        }

        // Outside the lock: writing to the preferences is an I/O.
        if (record)
            persistInt(_context, KEY_BEST_SCORE, score);

        // Walked away from: the panel goes at once, with nothing said and nothing offered.
        if (!_gameQuit) {
            _gameOverUntilMs = SystemClock.elapsedRealtime() + GAME_OVER_WINDOW_MS;
            announce(ANNOUNCE_GAME_OVER);
            scheduleGameOverEnd();
        }

        notifyGamePanel();
    }

    // The window closes on its own. Nothing else would refresh the panel at that moment: data
    // binding reacts to notifications, not to the clock, so the end of the countdown has to come
    // and say so.
    private void scheduleGameOverEnd() {
        if (_gameOverHandler == null)
            _gameOverHandler = new Handler(Looper.getMainLooper());

        _gameOverHandler.removeCallbacksAndMessages(null);
        _gameOverHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                notifyGamePanel();
            }
        }, GAME_OVER_WINDOW_MS + 50);
    }

    // True while the panel keeps its game dress after a loss, waiting for a tap.
    public boolean inGameOverWindow() {
        return SystemClock.elapsedRealtime() < _gameOverUntilMs;
    }

    // The game is on screen either because it is being played, or because it just ended and is
    // offering another go.
    private boolean gameShowing() {
        return (getGameLevel() > 0) || inGameOverWindow();
    }

    /**
     * The real length of each announcement, measured in its own file.
     *
     * Pushed by GameSurface once, when it has loaded the sounds. Anything it does not know about --
     * an announcement with no voice yet -- is left at its fallback rather than zeroed: a banner
     * with no sound still has to be read.
     */
    public void setAnnounceDurationsMs(long[] ms) {

        if (ms == null)
            return;

        long[] next = _announceMs.clone();

        for (int what = 0; (what < ms.length) && (what < next.length); what++) {
            if (ms[what] > 0)
                next[what] = ms[what];
        }

        _announceMs = next;
    }

    private void announce(int what) {
        _announce = what;
        _announceEndMs = SystemClock.elapsedRealtime() + announceMs(what);
        _announceToPlay = true;
    }

    // How long that one lasts, or zero for an announcement that does not exist.
    private long announceMs(int what) {

        long[] ms = _announceMs;

        return ((what > 0) && (what < ms.length)) ? ms[what] : 0;
    }

    // The announcement to draw, or ANNOUNCE_NONE once its time is up.
    public int getAnnounce() {
        int what = _announce;
        if (what != ANNOUNCE_NONE && SystemClock.elapsedRealtime() > _announceEndMs) {
            _announce = ANNOUNCE_NONE;
            what = ANNOUNCE_NONE;
        }
        return what;
    }

    // How much of the announcement is left, in milliseconds, so the drawing can fade it out.
    public long getAnnounceRemainingMs() {
        long left = _announceEndMs - SystemClock.elapsedRealtime();
        return (left < 0) ? 0 : left;
    }

    // True EXACTLY ONCE per announcement: on the frame that has to fire the sound.
    public boolean consumeAnnounceSound() {
        if (!_announceToPlay)
            return false;
        _announceToPlay = false;
        return true;
    }

    @Bindable
    public String getGameLevelLabel() {
        return (getDebugParam() == PARAM_GAMELVL ? "*" : "") + "GameLvl";
    }

    @Bindable
    public String getGameLevelString() {
        int v;
        _readLock.lock();
        try { v = _gameLevel; } finally { _readLock.unlock(); }
        return String.valueOf(v);
    }

    // #7 : les memes bornes et le meme pas que les boutons +/-, sous forme de liste, pour la ligne
    // "Warning distance" de la page Parameters. Le libelle est celui de l'affichage debug, au mot pres.
    public List<String> getWarnDistValues() {
        List<String> values = new ArrayList<>();

        for (int m = WARNDIST_MIN; m <= WARNDIST_MAX; m += WARNDIST_STEP)
            values.add(m + " m");

        return values;
    }

    @Bindable
    public int getWarnDistPos() {
        return (getWarnDistM() - WARNDIST_MIN) / WARNDIST_STEP;
    }

    // Retour de la liste. La liste rappelle aussi ce point d'entree au premier affichage, avec la
    // position deja en place : sans ce garde-fou on reecrirait la preference a chaque ouverture.
    public void setWarnDistPos(int position) {
        int m = WARNDIST_MIN + (position * WARNDIST_STEP);

        if (m == getWarnDistM())
            return;

        setWarnDistM(m);
    }

    public List<String> getWarnEtaValues() {
        List<String> values = new ArrayList<>();

        for (int sec = WARNETA_MIN; sec <= WARNETA_MAX; sec += WARNETA_STEP)
            values.add(sec + " s");

        return values;
    }

    @Bindable
    public int getWarnEtaPos() {
        return (getWarnEtaSec() - WARNETA_MIN) / WARNETA_STEP;
    }

    public void setWarnEtaPos(int position) {
        int sec = WARNETA_MIN + (position * WARNETA_STEP);

        if (sec == getWarnEtaSec())
            return;

        setWarnEtaSec(sec);
    }

    // #7 : une mark "warning" declenche l'alarme si sa distance <= WarnDist OU son eta <= WarnETA.
    // eta = distance / vitesse sol (km/h) ; ignoree a l'arret (v ~ 0) pour eviter les faux positifs.
    // --- #81 : sons des alarmes et interrupteur des alarmes radar --------------------------------

    public String getAlarmSound(int quoi) {          // 0 radar, 1 mark perso, 2 zone
        _readLock.lock();
        try {
            switch( quoi ) {
                case 1:  return _sndMark;
                case 2:  return _sndZone;
                default: return _sndRadar;
            }
        } finally {
            _readLock.unlock();
        }
    }

    public void setAlarmSound(int quoi, String nom) {
        Context context;
        String cle;

        if( nom == null )
            nom = AlarmSoundCatalog.SILENCE;

        _writeLock.lock();
        try {
            switch( quoi ) {
                case 1:  _sndMark = nom; cle = KEY_SND_MARK; break;
                case 2:  _sndZone = nom; cle = KEY_SND_ZONE; break;
                default: _sndRadar = nom; cle = KEY_SND_RADAR; break;
            }
            context = _context;
        } finally {
            _writeLock.unlock();
        }

        persistString(context, cle, nom);

        // #81 : une alarme qui sonne DEJA doit passer au nouveau son tout de suite. Les flux sont
        // lances une fois puis boucles : on les coupe, la boucle de rendu les relance au tour
        // suivant avec le son fraichement choisi.
        GameSurface gs = getGameSurface();

        if( gs != null )
            gs.resetAlarmStreams();

        notifyPropertyChanged(BR.alarmSoundRadarLabel);
        notifyPropertyChanged(BR.alarmSoundMarkLabel);
        notifyPropertyChanged(BR.alarmSoundZoneLabel);
    }

    @Bindable
    public String getAlarmSoundRadarLabel() {
        return AlarmSoundCatalog.label(getAlarmSound(0), "No sound");
    }

    @Bindable
    public String getAlarmSoundMarkLabel() {
        return AlarmSoundCatalog.label(getAlarmSound(1), "No sound");
    }

    @Bindable
    public String getAlarmSoundZoneLabel() {
        return AlarmSoundCatalog.label(getAlarmSound(2), "No sound");
    }

    // #81 : a false, une mark radar ne declenche plus RIEN -- ni son, ni bande peripherique, ni
    // fleche de bord. Elle garde son rond rouge pulsant, qui dit ce qu'elle est, pas qu'elle alerte.
    @Bindable
    public boolean getRadarAlarmOn() {
        _readLock.lock();
        try { return _radarAlarmOn; } finally { _readLock.unlock(); }
    }

    public void setRadarAlarmOn(boolean on) {
        Context context;
        _writeLock.lock();
        try { _radarAlarmOn = on; context = _context; } finally { _writeLock.unlock(); }

        persistBoolean(context, KEY_RADAR_ON, on);
        notifyPropertyChanged(BR.radarAlarmOn);
    }

    // #85: when true, the app drops what is merely decorative -- frozen avatar previews, silent
    // cosmetic sounds, lower frame rate -- and touches nothing that serves navigation: alarms,
    // position, heading and measurements are unchanged, and the avatar on the MAP keeps walking.
    @Bindable
    public boolean getLowResourceMode() {
        _readLock.lock();
        try { return _lowResourceMode; } finally { _readLock.unlock(); }
    }

    // #86: developer setting. Applies to everything the app writes and can grow -- the chunk cache
    // (ChunkCache), the offline map databases (MapInfo) and the memory log (MemLog).
    @Bindable
    public boolean getPreferSdStorage() {
        _readLock.lock();
        try { return _preferSdStorage; } finally { _readLock.unlock(); }
    }

    public void setPreferSdStorage(boolean on) {
        Context context;
        _writeLock.lock();
        try { _preferSdStorage = on; context = _context; } finally { _writeLock.unlock(); }

        persistBoolean(context, KEY_PREFER_SD, on);
        notifyPropertyChanged(BR.preferSdStorage);
        notifyPropertyChanged(BR.storageLocationsString);

        // The memory log is opened once at startup: point it at the newly preferred volume so the
        // setting takes effect without restarting the app.
        MemLog.init(preferredDir(externalFilesDirs(), 0));
    }

    // Same shape as the cache spinner: 10 km to 200 km in steps of ten.
    public List<String> getTideReachValues() {
        List<String> values = new ArrayList<>();

        for( int step = 0; step < TIDE_REACH_COUNT; step++ )
            values.add(String.format(Locale.ROOT, "%d km", TIDE_REACH_FIRST_KM + step * TIDE_REACH_STEP_KM));

        return values;
    }

    @Bindable
    public int getTideReachPos() {
        _readLock.lock();
        try { return _tideReachStep; } finally { _readLock.unlock(); }
    }

    public void setTideReachPos(int position) {
        Context context;

        if( (position < 0) || (position >= TIDE_REACH_COUNT) )
            return;

        _writeLock.lock();
        try {
            if( _tideReachStep == position )
                return;                       // the list calls back on first display: do not rewrite

            _tideReachStep = position;
            context = _context;
        } finally {
            _writeLock.unlock();
        }

        persistInt(context, KEY_TIDE_REACH, position);
        notifyPropertyChanged(BR.tideReachPos);
    }

    /** How far the tide page may reach for data, in kilometres. */
    public int getTideReachKm() {
        _readLock.lock();
        try { return TIDE_REACH_FIRST_KM + _tideReachStep * TIDE_REACH_STEP_KM; }
        finally { _readLock.unlock(); }
    }

    // #86: the spinner of the Parameters page, same shape as the other stepped settings.
    public List<String> getCacheSizeValues() {
        List<String> values = new ArrayList<>();
        Collections.addAll(values, CACHE_SIZE_LABELS);

        return values;
    }

    @Bindable
    public int getCacheSizePos() {
        _readLock.lock();
        try { return _cacheSizeStep; } finally { _readLock.unlock(); }
    }

    public void setCacheSizePos(int position) {
        Context context;

        if( (position < 0) || (position >= CACHE_SIZE_BYTES.length) )
            return;

        _writeLock.lock();
        try {
            if( _cacheSizeStep == position )
                return;                       // the list calls back on first display: do not rewrite

            _cacheSizeStep = position;
            context = _context;
        } finally {
            _writeLock.unlock();
        }

        persistInt(context, KEY_CACHE_SIZE, position);
        notifyPropertyChanged(BR.cacheSizePos);

        // A smaller cache must apply now, not in five minutes.
        ChunkCacheThread.wake();
    }

    /** Ceiling the cache thread trims down to. */
    public long getCacheLimitBytes() {
        _readLock.lock();
        try { return CACHE_SIZE_BYTES[_cacheSizeStep]; } finally { _readLock.unlock(); }
    }

    // #86: a REAL fix, as opposed to the position restored from the preferences at startup. The
    // cache cleanup waits for it: judging distances on a position hours old would drop the very
    // areas one is about to cross.
    public boolean getHasFix() {
        _readLock.lock();
        try { return _hasFix; } finally { _readLock.unlock(); }
    }

    /**
     * #86: picks a folder from a system-ordered list of per-volume folders -- index 0 is always the
     * primary (emulated internal) storage, the following ones are removable volumes, the SD card
     * among them. The preferred volume comes first, the others follow in their own order, and the
     * first one holding at least neededBytes wins. Nothing fits, or nothing is readable: the
     * preferred folder is returned anyway, so the caller fails on writing rather than on a null.
     */
    public File preferredDir(File[] dirs, long neededBytes) {
        if( (dirs == null) || (dirs.length == 0) )
            return null;

        boolean preferSd;
        _readLock.lock();
        try { preferSd = _preferSdStorage; } finally { _readLock.unlock(); }

        // Removable volumes first when the SD card is preferred, primary first otherwise.
        List<File> ordered = new ArrayList<>();
        if( preferSd ) {
            for( int i = 1; i < dirs.length; i++ ) {
                if( dirs[i] != null ) ordered.add(dirs[i]);
            }
            if( dirs[0] != null ) ordered.add(dirs[0]);
        } else {
            if( dirs[0] != null ) ordered.add(dirs[0]);
            for( int i = 1; i < dirs.length; i++ ) {
                if( dirs[i] != null ) ordered.add(dirs[i]);
            }
        }

        if( ordered.isEmpty() )
            return null;

        for( File dir : ordered ) {
            if( dir.getUsableSpace() > neededBytes )
                return dir;
        }

        return ordered.get(0);
    }

    // Per-volume private folders, in the order the system gives them. Null before the context is
    // known (unit tests, early startup).
    public File[] externalFilesDirs() {
        Context context;
        _readLock.lock();
        try { context = _context; } finally { _readLock.unlock(); }

        return (context == null) ? null : context.getExternalFilesDirs(null);
    }

    /**
     * #86: one line per storage volume, "name: free of total", shown under the SD setting so the
     * choice is made knowing what is left. The primary volume is the internal one by definition.
     */
    @Bindable
    public String getStorageLocationsString() {
        File[] dirs = externalFilesDirs();

        if( (dirs == null) || (dirs.length == 0) )
            return "No storage volume found.";

        StringBuilder text = new StringBuilder();

        for( int i = 0; i < dirs.length; i++ ) {
            if( dirs[i] == null )
                continue;

            if( text.length() > 0 )
                text.append("\n");

            text.append(String.format(Locale.US, "%s: %s free of %s",
                    (i == 0) ? "Internal" : "SD card",
                    readableSize(dirs[i].getUsableSpace()),
                    readableSize(dirs[i].getTotalSpace())));
        }

        return (text.length() == 0) ? "No storage volume found." : text.toString();
    }

    // Byte count in the shortest readable form. Locale.US on purpose: the app's numbers use a dot.
    public static String readableSize(long bytes) {
        if( bytes < 1024 )
            return bytes + " B";

        if( bytes < 1024L * 1024 )
            return String.format(Locale.US, "%.0f kB", bytes / 1024.0);

        if( bytes < 1024L * 1024 * 1024 )
            return String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024));

        return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public void setLowResourceMode(boolean on) {
        Context context;
        _writeLock.lock();
        try { _lowResourceMode = on; context = _context; } finally { _writeLock.unlock(); }

        persistBoolean(context, KEY_LOW_RES, on);
        notifyPropertyChanged(BR.lowResourceMode);

        // The Parameters previews are built on demand: they have to be asked for again to switch
        // from the animation to the still pose, and back. The map avatar is not concerned, so
        // _avatarSerial stays put.
        notifyPropertyChanged(BR.avatarSeaPreview);
        notifyPropertyChanged(BR.avatarWalkPreview);
        notifyPropertyChanged(BR.avatarRidePreview);
        notifyPropertyChanged(BR.avatarDrivePreview);
    }

    public boolean markShouldAlarm(double distanceKm) {
        int distM, etaMax;
        double spdKmh;
        _readLock.lock();
        try {
            distM = _warnDistM;
            etaMax = _warnEtaSec;
            spdKmh = _speedInKm;
        } finally {
            _readLock.unlock();
        }

        return shouldAlarmWithoutLock(distanceKm, distM, etaMax, spdKmh);
    }

    // #7 : le meme test, sans toucher au verrou, pour la boucle de proximite qui tient deja le write
    // lock et a donc ces trois valeurs sous la main.
    private static boolean shouldAlarmWithoutLock(double distanceKm, int distM, int etaMax, double spdKmh) {
        double dM = distanceKm * 1000.0;
        if (dM <= distM)
            return true;                                 // condition distance
        if (spdKmh > 0.1) {                              // condition eta (ignoree a l'arret)
            double etaSec = dM / (spdKmh / 3.6);
            if (etaSec <= etaMax)
                return true;
        }
        return false;
    }

    // --- Target zone width at creation (ZoneW F parameter, #25) ----------------------------------
    public double getZoneTargetWidthM() {
        double v;
        _readLock.lock();
        try { v = _zoneTargetWidthM; } finally { _readLock.unlock(); }
        return v;
    }

    public void setZoneTargetWidthM(double m) {
        if (m < ZONEW_MIN) m = ZONEW_MIN;
        if (m > ZONEW_MAX) m = ZONEW_MAX;
        Context context;
        _writeLock.lock();
        try { _zoneTargetWidthM = m; context = _context; } finally { _writeLock.unlock(); }
        persistFloat(context, KEY_ZONE_WIDTH, (float) m);
        notifyPropertyChanged(BR.zoneWString);
        notifyPropertyChanged(BR.zoneWPos);
    }

    @Bindable
    public String getZoneWLabel() {
        return (getDebugParam() == PARAM_ZONEWIDTH ? "*" : "") + "ZoneW";
    }

    @Bindable
    public String getZoneWString() {
        double v;
        _readLock.lock();
        try { v = _zoneTargetWidthM; } finally { _readLock.unlock(); }
        return String.format(Locale.US, "%.0f m", v);
    }

    // Index de la largeur courante dans ZONEW_VALUES. La valeur persistee peut ne pas tomber sur un
    // cran de l'echelle (reglages faits avant, ou echelle retouchee) : on retient le cran le plus
    // proche plutot que de refuser la valeur.
    private static int zoneWIndex(double m) {
        int best = 0;

        for (int i = 1; i < ZONEW_VALUES.length; i++) {
            if (Math.abs(ZONEW_VALUES[i] - m) < Math.abs(ZONEW_VALUES[best] - m))
                best = i;
        }

        return best;
    }

    public List<String> getZoneWValues() {
        List<String> values = new ArrayList<>();

        for (double m : ZONEW_VALUES)
            values.add(String.format(Locale.US, "%.0f m", m));

        return values;
    }

    @Bindable
    public int getZoneWPos() {
        return zoneWIndex(getZoneTargetWidthM());
    }

    public void setZoneWPos(int position) {
        if ((position < 0) || (position >= ZONEW_VALUES.length))
            return;

        if (position == zoneWIndex(getZoneTargetWidthM()))
            return;

        setZoneTargetWidthM(ZONEW_VALUES[position]);
    }

    // --- Format d'affichage des coordonnees (CoordFmt F parameter, DD / DM / DMS) -----------------
    public int getCoordFmt() {
        int v;
        _readLock.lock();
        try { v = _coordFmt; } finally { _readLock.unlock(); }
        return v;
    }

    public void setCoordFmt(int fmt) {
        if (fmt < 0) fmt = 0;
        if (fmt >= COORDFMT_COUNT) fmt = COORDFMT_COUNT - 1;
        Context context;
        _writeLock.lock();
        try { _coordFmt = fmt; context = _context; } finally { _writeLock.unlock(); }
        persistInt(context, KEY_COORD_FMT, fmt);
        notifyPropertyChanged(BR.coordFmtString);
        notifyPropertyChanged(BR.coordFmtPos);
        // Rafraichir l'affichage debug lat/lon (format change sans nouveau fix).
        notifyPropertyChanged(BR.latitudeString);
        notifyPropertyChanged(BR.longitudeString);
    }

    @Bindable
    public String getCoordFmtLabel() {
        return (getDebugParam() == PARAM_COORDFMT ? "*" : "") + "CoordFmt";
    }

    @Bindable
    public String getCoordFmtString() {
        switch (getCoordFmt()) {
            case COORDFMT_DD:  return "DD";
            case COORDFMT_DMS: return "DMS";
            default:           return "DM";
        }
    }

    public List<String> getCoordFmtValues() {
        List<String> values = new ArrayList<>();

        values.add("DD");
        values.add("DM");
        values.add("DMS");

        return values;
    }

    @Bindable
    public int getCoordFmtPos() {
        return getCoordFmt();
    }

    public void setCoordFmtPos(int position) {
        if (position == getCoordFmt())
            return;

        setCoordFmt(position);
    }

    // --- Small unit under the mile in mph mode (metres / yards / feet) --------------------------
    public int getSmallUnit() {
        return readSmallUnit();
    }

    public List<String> getSmallUnitValues() {
        List<String> values = new ArrayList<>();

        values.add("Metres");
        values.add("Yards");
        values.add("Feet");

        return values;
    }

    @Bindable
    public int getSmallUnitPos() {
        return readSmallUnit();
    }

    public void setSmallUnitPos(int position) {
        if (position == readSmallUnit())
            return;

        writeSmallUnit(position);
    }

    private int readSmallUnit() {
        int v;
        _readLock.lock();
        try {
            v = _smallUnit;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    private void writeSmallUnit(int unit) {
        if (unit < 0) unit = 0;
        if (unit >= SMALLUNIT_COUNT) unit = SMALLUNIT_COUNT - 1;
        Context context;
        _writeLock.lock();
        try {
            _smallUnit = unit;
            context = _context;
        } finally {
            _writeLock.unlock();
        }
        persistInt(context, KEY_SMALL_UNIT, unit);
        notifyPropertyChanged(BR.smallUnitPos);
        notifyPropertyChanged(BR.markEtaString); // distances follow the unit
    }

    // --- #68 : adresses des serveurs interroges en ligne (Developer parameters) ------------------

    // Filtre commun aux deux adresses : vide, mal formee, ou schema autre que http(s) -> null, la
    // saisie est refusee. Le trim traite le piege du copier-coller, ou un espace ou un saut de ligne
    // reste colle a l'adresse et ferait echouer toutes les requetes sans rien d'explicite a l'ecran.
    private static String normalizeHttpUrl(String url) {
        if (url == null)
            return null;

        String cleaned = url.trim();

        if (TextUtils.isEmpty(cleaned))
            return null;

        Uri uri = Uri.parse(cleaned);
        String scheme = uri.getScheme();

        if ((scheme == null)
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")))
            return null;

        if (TextUtils.isEmpty(uri.getHost()))
            return null;

        return cleaned;
    }

    // Overpass : on ne retient que la base, le code recollant "/interpreter" et "/status". Les
    // adresses publiees partout finissent par "/interpreter" et la coller telle quelle est le geste
    // naturel : on la raccourcit plutot que de la refuser. Le "/" final part aussi, sans quoi la
    // recolle produirait un double slash.
    private static final String OVERPASS_INTERPRETER_PATH = "/interpreter";
    private static final String MAPS_MANIFEST_PATH = "/map-checkupdate.php";
    private static final String NOMINATIM_SEARCH_PATH = "/search";

    // Retire le "/" final et, s'il est la, le dernier segment que le code recolle lui-meme. Les
    // adresses publiees designent l'endpoint COMPLET, et la coller telle quelle est le geste
    // naturel : on la raccourcit plutot que de la refuser.
    private static String normalizeBaseUrl(String url, String suffix) {
        String cleaned = normalizeHttpUrl(url);

        if (cleaned == null)
            return null;

        while (cleaned.endsWith("/"))
            cleaned = cleaned.substring(0, cleaned.length() - 1);

        if (cleaned.toLowerCase(Locale.US).endsWith(suffix))
            cleaned = cleaned.substring(0, cleaned.length() - suffix.length());

        while (cleaned.endsWith("/"))
            cleaned = cleaned.substring(0, cleaned.length() - 1);

        return TextUtils.isEmpty(cleaned) ? null : cleaned;
    }

    private static String normalizeOverpassBase(String url) {
        return normalizeBaseUrl(url, OVERPASS_INTERPRETER_PATH);
    }

    private static String normalizeMapsBase(String url) {
        return normalizeBaseUrl(url, MAPS_MANIFEST_PATH);
    }

    private static String normalizeNominatimBase(String url) {
        return normalizeBaseUrl(url, NOMINATIM_SEARCH_PATH);
    }

    @Bindable
    public String getOverpassUrl() {
        String v;
        _readLock.lock();
        try {
            v = _overpassBaseUrl;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    // Les deux adresses reellement appelees. Chunk n'en lit qu'UNE par operation, rangee dans une
    // variable locale : deux lectures au cours d'une meme requete pourraient tomber de part et
    // d'autre d'un changement de reglage et adresser le status a un serveur et la requete a un autre.
    public String getOverpassInterpreterUrl() {
        return getOverpassUrl() + OVERPASS_INTERPRETER_PATH;
    }

    public String getOverpassStatusUrl() {
        return getOverpassUrl() + "/status";
    }

    @Bindable
    public boolean isOverpassUrlDefault() {
        return OVERPASS_BASE_DEFAULT.equals(getOverpassUrl());
    }

    // Retour du champ de saisie. Renvoie l'adresse REELLEMENT retenue, que le champ reaffiche : une
    // saisie refusee revient donc d'elle-meme a la valeur precedente, sous les yeux de l'utilisateur.
    public String setOverpassUrl(String url) {
        String cleaned = normalizeOverpassBase(url);

        if (cleaned == null)
            return getOverpassUrl();

        // Rien n'a change : ne RIEN ecrire. Le champ rend la main des qu'il perd le focus, donc un
        // simple passage sur la ligne appelle ce point d'entree ; sans ce garde-fou il figerait
        // l'adresse dans les preferences alors que l'utilisateur n'a rien touche.
        if (cleaned.equals(getOverpassUrl()))
            return cleaned;

        Context context;
        _writeLock.lock();
        try {
            _overpassBaseUrl = cleaned;
            context = _context;
        } finally {
            _writeLock.unlock();
        }

        // La preference n'enregistre qu'un ECART au defaut : revenue au defaut, elle est effacee.
        // L'installation suit alors le defaut de l'application, y compris s'il change plus tard.
        if (cleaned.equals(OVERPASS_BASE_DEFAULT))
            clearPref(context, KEY_OVERPASS_URL);
        else
            persistString(context, KEY_OVERPASS_URL, cleaned);

        notifyPropertyChanged(BR.overpassUrl);
        notifyPropertyChanged(BR.overpassUrlDefault);

        return cleaned;
    }

    // Le Reset EFFACE la preference au lieu d'y ecrire le defaut, et il l'efface meme si la valeur
    // courante est deja celle du defaut : une entree laissee la par une version anterieure figerait
    // sinon l'installation sur le defaut de l'epoque. Il ne passe donc pas par le setter, dont le
    // garde-fou d'egalite ne ferait rien dans ce cas.
    public void resetOverpassUrl() {
        Context context;
        _writeLock.lock();
        try {
            _overpassBaseUrl = OVERPASS_BASE_DEFAULT;
            context = _context;
        } finally {
            _writeLock.unlock();
        }

        clearPref(context, KEY_OVERPASS_URL);
        notifyPropertyChanged(BR.overpassUrl);
        notifyPropertyChanged(BR.overpassUrlDefault);
    }

    @Bindable
    public String getMapsUrl() {
        String v;
        _readLock.lock();
        try {
            v = _mapsBaseUrl;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    // L'adresse reellement appelee. Comme pour Overpass, elle n'est lue qu'une fois par operation.
    public String getMapsManifestUrl() {
        return getMapsUrl() + MAPS_MANIFEST_PATH;
    }

    @Bindable
    public boolean isMapsUrlDefault() {
        return MAPS_BASE_DEFAULT.equals(getMapsUrl());
    }

    public String setMapsUrl(String url) {
        String cleaned = normalizeMapsBase(url);

        if (cleaned == null)
            return getMapsUrl();

        if (cleaned.equals(getMapsUrl()))
            return cleaned;

        Context context;
        _writeLock.lock();
        try {
            _mapsBaseUrl = cleaned;
            context = _context;
        } finally {
            _writeLock.unlock();
        }

        if (cleaned.equals(MAPS_BASE_DEFAULT))
            clearPref(context, KEY_MAPS_URL);
        else
            persistString(context, KEY_MAPS_URL, cleaned);

        notifyPropertyChanged(BR.mapsUrl);
        notifyPropertyChanged(BR.mapsUrlDefault);

        return cleaned;
    }

    public void resetMapsUrl() {
        Context context;
        _writeLock.lock();
        try {
            _mapsBaseUrl = MAPS_BASE_DEFAULT;
            context = _context;
        } finally {
            _writeLock.unlock();
        }

        clearPref(context, KEY_MAPS_URL);
        notifyPropertyChanged(BR.mapsUrl);
        notifyPropertyChanged(BR.mapsUrlDefault);
    }

    @Bindable
    public String getNominatimUrl() {
        String v;
        _readLock.lock();
        try {
            v = _nominatimBaseUrl;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    // L'adresse reellement appelee. Comme les deux autres, elle n'est lue qu'UNE fois par operation,
    // et rangee dans une variable locale : deux lectures au cours d'une meme recherche pourraient
    // tomber de part et d'autre d'un changement de reglage.
    public String getNominatimSearchUrl() {
        return getNominatimUrl() + NOMINATIM_SEARCH_PATH;
    }

    @Bindable
    public boolean isNominatimUrlDefault() {
        return NOMINATIM_BASE_DEFAULT.equals(getNominatimUrl());
    }

    public String setNominatimUrl(String url) {
        String cleaned = normalizeNominatimBase(url);

        if (cleaned == null)
            return getNominatimUrl();

        if (cleaned.equals(getNominatimUrl()))
            return cleaned;

        Context context;
        _writeLock.lock();
        try {
            _nominatimBaseUrl = cleaned;
            context = _context;
        } finally {
            _writeLock.unlock();
        }

        if (cleaned.equals(NOMINATIM_BASE_DEFAULT))
            clearPref(context, KEY_NOMINATIM_URL);
        else
            persistString(context, KEY_NOMINATIM_URL, cleaned);

        notifyPropertyChanged(BR.nominatimUrl);
        notifyPropertyChanged(BR.nominatimUrlDefault);

        return cleaned;
    }

    public void resetNominatimUrl() {
        Context context;
        _writeLock.lock();
        try {
            _nominatimBaseUrl = NOMINATIM_BASE_DEFAULT;
            context = _context;
        } finally {
            _writeLock.unlock();
        }

        clearPref(context, KEY_NOMINATIM_URL);
        notifyPropertyChanged(BR.nominatimUrl);
        notifyPropertyChanged(BR.nominatimUrlDefault);
    }

    // --- Selected-mark panel (top-left): cap to take / ETA / name (#12, #13) ---------------------
    // Snapshot of the selected marks, sorted nearest-first from the current position.
    private List<PersonalMark> selectedMarksByDistance() {
        final List<PersonalMark> sel = new ArrayList<>();
        final double clat, clon;
        final Double calt;
        _readLock.lock();
        try {
            clat = _lastKnownCoordonate.getLatitude();
            clon = _lastKnownCoordonate.getLongitude();
            calt = _averageAltitude;
            if (_marks != null) {
                for (PersonalMark m : _marks) if (m != null && m.isSelected()) sel.add(m);
            }
        } finally {
            _readLock.unlock();
        }
        Collections.sort(sel, new Comparator<PersonalMark>() {
            public int compare(PersonalMark a, PersonalMark b) {
                return Double.compare(
                        Chunk.measureKm(clat, clon, calt, a.getLatitude(), a.getLongitude(), a.getAltitude()),
                        Chunk.measureKm(clat, clon, calt, b.getLatitude(), b.getLongitude(), b.getAltitude()));
            }
        });
        return sel;
    }

    // Which selected mark to show: keep _shownMark if still selected, else the nearest selected,
    // else null (no selection). Updates and returns _shownMark. (UI-thread access.)
    private PersonalMark resolveShownMark() {
        List<PersonalMark> sel = selectedMarksByDistance();
        if (_shownMark != null && sel.contains(_shownMark)) return _shownMark;
        _shownMark = sel.isEmpty() ? null : sel.get(0);
        return _shownMark;
    }

    // Read-only accessor for the map renderer: which selected mark is the one shown in the
    // top-left panel, so it can be emphasized (thicker line, bolder text) among the others.
    // May be briefly stale (re-resolved on selection events / panel taps) -> fine for a highlight.
    public PersonalMark getShownMark() {
        return _shownMark;
    }

    // Called by PersonalMark.switchSelected(): a just-selected mark becomes the shown one; on
    // deselection the shown mark is re-resolved (nearest selected).
    public void onMarkSelectionToggled(PersonalMark m) {
        if (m != null && m.isSelected()) _shownMark = m;
        else resolveShownMark();
        notifyMarkPanel();
    }

    // Tap on the panel. During the ten seconds that follow a game over it means "again", and
    // starts a new game at level 1 -- with its Fight!, since every entry goes through setGameLevel.
    // Outside that window it keeps its usual meaning, and cycles through the selected marks.
    /**
     * A long HOLD in the top-left corner: in or out of the game, whichever applies.
     *
     * In a game it walks away from it. Out of one it starts another, but only for someone who has
     * already found the easter egg -- it is not a way of finding it. Anything else is none of its
     * business, and false hands the gesture back to whatever else wants it.
     *
     * The corner and not the mark panel: that panel is not on screen when no mark is selected, so
     * there was nothing to hold. GBRootLayout, which sees every press, owns the timing.
     */
    // Jusqu'a quand l'appui du coin court, ou zero s'il n'y en a pas. Pose par la vue racine, lu
    // par le dessin : c'est tout ce qu'il faut pour un compte a rebours, qui n'a pas d'etat a lui.
    private volatile long _holdUntilMs = 0;

    /** L'appui du coin commence (duree en ms) ou s'arrete (zero). */
    public void setCornerHold(long dureeMs) {
        _holdUntilMs = (dureeMs > 0) ? (SystemClock.elapsedRealtime() + dureeMs) : 0;
    }

    /**
     * Ce qu'il reste de l'appui du coin, en millisecondes, ou zero s'il n'y a rien a montrer.
     *
     * Zero aussi quand l'appui ne lancerait rien : hors partie sans easter egg trouve il ne se
     * passera rien au bout, et en partie il ABANDONNE -- un abandon est silencieux par
     * construction, le compter a rebours en ferait une annonce.
     */
    public long getCornerHoldRemainingMs() {

        long until = _holdUntilMs;

        if (until <= 0)
            return 0;

        // Counted during a game as well as before one. It used to stop at level zero, so the hold
        // that ENDS a game showed nothing at all -- and that is the moment one most wants to know
        // the finger has been taken, since nothing else on screen answers it.
        if (!isEasterEggFound(EG_GAME))
            return 0;

        long left = until - SystemClock.elapsedRealtime();

        return (left > 0) ? left : 0;
    }

    // A level skip asked for by the double tap in the corner, and not served yet. The
    // orchestrator takes it when it can: the next level announces itself like a true end of
    // level, so it waits its turn if something else is speaking.
    private volatile boolean _levelSkip = false;

    /**
     * Double tap in the corner: the next level, music and banner included.
     *
     * False outside a game -- there is nothing to move up then, and the gesture follows its
     * normal course. False too for as long as an earlier request has not been served: chaining
     * double taps went up two levels at once, and the banner of the first was overwritten by the
     * number of the second while it was still on screen.
     */
    public boolean onCornerDoubleTap() {

        if (getGameLevel() <= 0)
            return false;

        // A main mark is being steered to: the corner then belongs to the marks, not to the
        // levels. Switching quickly between several selected marks taps twice in the same place,
        // and that read as a double tap and skipped a level. Changing level is not the normal way
        // to play, so it asks for a clear table: nothing selected at all.
        if (_shownMark != null)
            return false;

        if (_levelSkip)
            return false;

        _levelSkip = true;

        return true;
    }

    /** Is a skip request waiting to be served? */
    public boolean isLevelSkipAsked() {
        return _levelSkip;
    }

    /** Served: the request goes, and a new one can be taken. */
    public void clearLevelSkip() {
        _levelSkip = false;
    }

    public boolean onCornerHold() {

        if (getGameLevel() > 0) {
            quitGame();
            return true;
        }

        if (isEasterEggFound(EG_GAME)) {
            setGameLevel(1);
            return true;
        }

        return false;
    }

    public void onPanelTap() {
        if ((getGameLevel() == 0) && inGameOverWindow()) {
            setGameLevel(1);
            return;
        }

        cyclePanelMark();
    }

    // Tap on the panel: jump to the PREVIOUS selected mark by distance (toward the nearest);
    // from the nearest, wrap to the farthest.
    public void cyclePanelMark() {
        List<PersonalMark> sel = selectedMarksByDistance();
        if (sel.isEmpty()) {
            _shownMark = null;
        } else {
            int idx = (_shownMark == null) ? 0 : sel.indexOf(_shownMark);
            if (idx < 0) idx = 0;
            idx = (idx - 1 + sel.size()) % sel.size();
            _shownMark = sel.get(idx);
        }
        notifyMarkPanel();
    }

    private void notifyMarkPanel() {
        notifyPropertyChanged(BR.markPanelVisible);
        notifyPropertyChanged(BR.markCapString);
        notifyPropertyChanged(BR.markEtaString);
        notifyPropertyChanged(BR.markNameString);
        notifyPropertyChanged(BR.markAlarmed);
        notifyPropertyChanged(BR.markArrowBadge);
        notifyPropertyChanged(BR.zoneBtnVisible); // zone button appears/disappears with selection
        notifyGamePanel();                        // the heart points at the shown mark, and the
    }                                             // level/score lines depend on there being one

    // The panel now belongs to the game as well as to the selection: in a game it is shown even
    // with nothing selected, because that is where the life gauge lives. At level 0 nothing
    // changes for anyone who never found the easter egg.
    @Bindable
    public boolean getMarkPanelVisible() {
        return (resolveShownMark() != null) || gameShowing();
    }

    // Is there a mark to talk about? Gates the heading, the ETA and the name, which say nothing
    // without one.
    @Bindable
    public boolean getMarkShown() {
        return resolveShownMark() != null;
    }

    // The life gauge replaces the direction badge for as long as a game is running.
    @Bindable
    public boolean getGaugeVisible() {
        return gameShowing();
    }

    @Bindable
    public boolean getArrowBadgeVisible() {
        return !gameShowing() && (resolveShownMark() != null);
    }

    /**
     * Level and score BESIDE the gauge, which is where they go when no mark is selected.
     *
     * The slot beside the icon is free then, and two lines of 24dp fill its height exactly. Select
     * a mark and that slot is taken by the heading, in 48sp: the game moves underneath instead,
     * which is the next property.
     */
    @Bindable
    public boolean getGameInfoVisible() {
        return gameShowing() && (resolveShownMark() == null);
    }

    /**
     * Level and score UNDER the mark's name, which is where they go when one is selected.
     *
     * The exact complement of the property above -- the game shows them in one place or in the
     * other, never in both and never nowhere. Under the name the whole width of the panel is
     * free, so the two go side by side on a single line rather than stacked.
     */
    @Bindable
    public boolean getGameInfoUnderNameVisible() {
        return gameShowing() && (resolveShownMark() != null);
    }

    /** The progress bar is there whenever the game is: above the panel, whatever is in it. */
    @Bindable
    public boolean getGameBarVisible() {
        return gameShowing();
    }

    // Short on purpose: these two lines sit beside a 48dp icon, and they have to fit in its height
    // without shrinking to nothing.
    /**
     * How long an announcement stays on screen, which is how long the action that raises it speaks.
     *
     * Asked by the orchestrator, which has to reserve the floor for that long before it starts the
     * action -- and which has no business knowing which file is behind which announcement.
     */
    public long getAnnounceDurationMs(int what) {
        return announceMs(what);
    }

    @Bindable
    public String getGameLevelLine() {
        return "Lvl " + getGameLevel();
    }

    /**
     * The panel's score line.
     *
     * In a game, the score alone. Once the game is over -- the game over window, the only moment
     * this panel is seen at level zero -- the best score comes beside it, on the same line, to say
     * what there is left to beat.
     */
    @Bindable
    public String getGameScoreLine() {
        return scoreLine();
    }

    /**
     * The text size of that line, so that it holds on ONE line.
     *
     * The panel is laid out on its contents: nothing clips the line, it pushes the panel past the
     * edge of the screen instead. And the line is of no fixed length -- "8 pts" against
     * "999 999 999 pts / max 999 999 999". So the size is read off the length: full size up to a
     * reference of REF_CHARS characters, then narrowed in proportion, down to a floor below which
     * the thing would no longer be read at all.
     *
     * In PIXELS, and that is not a choice: bound to android:textSize, this goes through
     * TextViewBindingAdapter, which hands it to setTextSize as COMPLEX_UNIT_PX. The constants are
     * written in SP, the way the layout states its other sizes, and converted here.
     *
     * Android has an autosizing TextView for this, but its own documentation warns against it on
     * a wrap_content width, which is what this panel is built on.
     */
    @Bindable
    public float getGameScoreTextSize() {

        int length = scoreLine().length();

        float sp = SCORE_SIZE_SP;

        if (length > SCORE_REF_CHARS) {
            sp = (SCORE_SIZE_SP * SCORE_REF_CHARS) / (float) length;
            sp = Math.max(sp, SCORE_SIZE_MIN_SP);
        }

        if (_context == null)
            return sp;

        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                                         _context.getResources().getDisplayMetrics());
    }

    // The score line at full size, how long it may be at that size, and how small it is allowed
    // to become, in SP. The reference is the width the panel was drawn for: "25 345 pts" and a
    // little room, which is what a game shows while it is being played.
    private static final float SCORE_SIZE_SP = 19f;
    private static final float SCORE_SIZE_MIN_SP = 11f;
    private static final int SCORE_REF_CHARS = 14;

    // The line itself. Two public faces read it -- the text and its size -- and neither may call
    // the other.
    private String scoreLine() {

        int score;
        int best;
        int level;

        _readLock.lock();
        try {
            score = _score;
            best = _bestScore;
            level = _gameLevel;
        } finally {
            _readLock.unlock();
        }

        if ((level > 0) || (best <= 0))
            return grouped(score) + " pts";

        return grouped(score) + " pts \u25c7 max " + grouped(best);
    }

    // A number in groups of three digits, parted by a narrow no-break space: "8 434 343" is read
    // at a glance where "8434343" has to be counted.
    private static String grouped(int value) {

        String digits = Integer.toString(Math.abs(value));
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < digits.length(); i++) {

            if ((i > 0) && (((digits.length() - i) % 3) == 0))
                out.append('\u202f');

            out.append(digits.charAt(i));
        }

        return (value < 0) ? ("-" + out) : out.toString();
    }

    /** The easter eggs found so far, as a mask. */
    public int getEasterEggs() {
        _readLock.lock();
        try { return _easterEggs; } finally { _readLock.unlock(); }
    }

    /** Whether that one has been found. */
    public boolean isEasterEggFound(int bit) {
        _readLock.lock();
        try { return (_easterEggs & bit) == bit; } finally { _readLock.unlock(); }
    }

    /**
     * Marks that one as found, for good.
     *
     * Nothing is written when it was already known: this is called from the thread that found it,
     * which has no business touching the disc on every frame.
     */
    public void setEasterEggFound(int bit) {

        int mask;

        _writeLock.lock();
        try {
            if ((_easterEggs & bit) == bit)
                return;

            _easterEggs |= bit;
            mask = _easterEggs;
        } finally {
            _writeLock.unlock();
        }

        // Outside the lock: writing to the preferences is an I/O.
        persistInt(_context, KEY_EASTER_EGGS, mask);
    }

    /** The best score ever made. */
    public int getBestScore() {
        _readLock.lock();
        try { return _bestScore; } finally { _readLock.unlock(); }
    }

    // The heart, pointing where the arrow would: at the shown mark. With nothing selected there is
    // no bearing to show, so it sits the natural way up -- tip down, the south sector.
    @Bindable
    public Drawable getGaugeHeart() {
        Context context;
        _readLock.lock();
        try { context = _context; } finally { _readLock.unlock(); }

        if (context == null)
            return null;

        PersonalMark m = resolveShownMark();
        int sector = (m == null) ? HEART_NO_BEARING : m.getHeading();

        return ContextCompat.getDrawable(context, HEART_RES[((sector % 8) + 8) % 8]);
    }

    @Bindable
    public Drawable getGaugeRing() {
        Context context;
        int left;
        _readLock.lock();
        try { context = _context; left = _lives; } finally { _readLock.unlock(); }

        if (context == null)
            return null;

        if (left < 0) left = 0;
        if (left > GAME_LIVES) left = GAME_LIVES;

        return ContextCompat.getDrawable(context, LIFE_RES[left]);
    }

    // Everything the game shows in the top-left panel, refreshed in one go.
    private void notifyGamePanel() {
        notifyPropertyChanged(BR.markPanelVisible);
        notifyPropertyChanged(BR.markShown);
        notifyPropertyChanged(BR.gaugeVisible);
        notifyPropertyChanged(BR.arrowBadgeVisible);
        notifyPropertyChanged(BR.gaugeHeart);
        notifyPropertyChanged(BR.gaugeRing);
        notifyPropertyChanged(BR.gameBarVisible);
        notifyPropertyChanged(BR.gameInfoVisible);
        notifyPropertyChanged(BR.gameInfoUnderNameVisible);
        notifyPropertyChanged(BR.gameLevelLine);
        notifyPropertyChanged(BR.gameScoreLine);
        notifyPropertyChanged(BR.gameScoreTextSize);
    }

    @Bindable
    public String getMarkCapString() {
        PersonalMark m = resolveShownMark();
        if (m == null) return "";
        double clat, clon;
        boolean mag;
        _readLock.lock();
        try {
            clat = _lastKnownCoordonate.getLatitude(); clon = _lastKnownCoordonate.getLongitude();
            mag = _headingMagnetic;
        } finally {
            _readLock.unlock();
        }
        double cap = Chunk.finalBearing(clat, clon, m.getLatitude(), m.getLongitude());
        if (mag) {
            // meme conversion que le cap du bateau, carte de deviation comprise
            _readLock.lock();
            try {
                cap = magneticFromTrueNoLock(cap, selectedCompassNoLock());
            } finally {
                _readLock.unlock();
            }
        }
        return capDeg(cap) + "°" + (mag ? "ᴹ" : "ᵀ");
    }

    @Bindable
    public CharSequence getMarkEtaString() {
        PersonalMark m = resolveShownMark();
        if (m == null) return "";
        double clat, clon, spd;
        Double calt;
        _readLock.lock();
        try {
            clat = _lastKnownCoordonate.getLatitude(); clon = _lastKnownCoordonate.getLongitude(); spd = _averageSpeed;
            calt = _averageAltitude;
        } finally {
            _readLock.unlock();
        }
        double distKm = Chunk.measureKm(clat, clon, calt, m.getLatitude(), m.getLongitude(), m.getAltitude());
        String distStr = PersonalMark.formatDistance(distKm, 1); // same formatter, 1 decimal
        String eta;
        if (spd < SPEED_EPS_KMH) {
            eta = "--:--";
        } else {
            long totalSec = (long) (distKm / spd * 3600.0);
            long h = totalSec / 3600, mn = (totalSec % 3600) / 60, s = totalSec % 60;
            eta = (h > 0) ? String.format(Locale.US, "%d:%02d:%02d", h, mn, s)  // H:MM:SS (>=1h)
                          : String.format(Locale.US, "%d:%02d", mn, s);         // MM:SS
        }
        // Height difference and slope, as in the marks list (same values, same threshold), when
        // shown: the arrow, then the angle over the height difference, unsigned (the arrow says which way).
        if (m.isElevationShown()) {
            SpannableStringBuilder sb = new SpannableStringBuilder(eta + " ◆ " + distStr + " ");
            sb.append(m.isElevationUp() ? "▲" : "▼");
            int at = sb.length();
            sb.append("x");
            sb.setSpan(new StackedTextSpan(m.getElevationDegrees() + "°", m.getElevationAbsMeters() + " m"),
                    at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return sb;
        }
        return eta + " ◆ " + distStr;
    }

    // #52 : "ETA ◆ distance" pour une distance donnee (km), memes regles que le panneau main-mark
    // (vitesse = _averageSpeed ; "--:--" si vitesse < seuil ; distance formatee comme la main mark).
    public String formatEtaDist(double distKm) {
        double spd;
        _readLock.lock();
        try {
            spd = _averageSpeed;
        } finally {
            _readLock.unlock();
        }
        String distStr = PersonalMark.formatDistance(distKm, 1);
        String eta;
        if (spd < SPEED_EPS_KMH) {
            eta = "--:--";
        } else {
            long totalSec = (long) (distKm / spd * 3600.0);
            long h = totalSec / 3600, mn = (totalSec % 3600) / 60, s = totalSec % 60;
            eta = (h > 0) ? String.format(Locale.US, "%d:%02d:%02d", h, mn, s)
                          : String.format(Locale.US, "%d:%02d", mn, s);
        }
        return eta + " ◆ " + distStr;
    }

    @Bindable
    public String getMarkNameString() {
        PersonalMark m = resolveShownMark();
        return (m == null) ? "" : m.getTitle();
    }

    // #7 : la marque du panneau est-elle armee ? Son nom passe alors au rouge, comme dans les listes.
    @Bindable
    public boolean getMarkAlarmed() {
        PersonalMark m = resolveShownMark();
        return (m != null) && m.isAlarmed();
    }

    // Badge de direction (rond bleu / flèche blanche) de la marque affichée, pour le panneau haut-gauche.
    @Bindable
    public Drawable getMarkArrowBadge() {
        PersonalMark m = resolveShownMark();
        return (m == null) ? null : m.getArrowBadgeNormal();
    }

    // --- Surveillance zone (#25) ----------------------------------------------------------------
    public SurveillanceZone getZone() {
        _readLock.lock();
        try { return _zone; } finally { _readLock.unlock(); }
    }

    public boolean hasZone() {
        _readLock.lock();
        try { return _zone != null; } finally { _readLock.unlock(); }
    }

    // No zone, or the current position is inside it (+margin).
    public boolean isInZone(double marginM) {
        SurveillanceZone z; double lat, lon;
        _readLock.lock();
        try { z = _zone; lat = _lastKnownCoordonate.getLatitude(); lon = _lastKnownCoordonate.getLongitude(); }
        finally { _readLock.unlock(); }
        return z == null || z.contains(lat, lon, marginM);
    }

    // Build a zone enclosing the current position + all selected marks, oriented along the bearing
    // position -> shown mark. false if there's no fix or no shown mark.
    public boolean createZoneFromSelection() {
        List<PersonalMark> sel = selectedMarksByDistance();
        double posLat, posLon; PersonalMark shown; boolean has;
        _readLock.lock();
        try { posLat = _lastKnownCoordonate.getLatitude(); posLon = _lastKnownCoordonate.getLongitude(); shown = _shownMark; has = _hasFix; }
        finally { _readLock.unlock(); }
        if (!has || shown == null) return false;

        double angle = Chunk.finalBearing(posLat, posLon, shown.getLatitude(), shown.getLongitude());
        double th = Math.toRadians(angle), cosLat = Math.cos(Math.toRadians(posLat));

        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{posLat, posLon});
        for (PersonalMark m : sel) pts.add(new double[]{m.getLatitude(), m.getLongitude()});

        double minU = 1e18, maxU = -1e18, minV = 1e18, maxV = -1e18;
        for (double[] p : pts) {
            double dN = (p[0]-posLat)*111320.0, dE = (p[1]-posLon)*111320.0*cosLat;
            double u = dE*Math.sin(th)+dN*Math.cos(th), v = dE*Math.cos(th)-dN*Math.sin(th);
            if (u<minU) minU=u; if (u>maxU) maxU=u; if (v<minV) minV=v; if (v>maxV) maxV=v;
        }
        double halfLen = (maxU-minU)/2 + ZONE_MARGIN_CREATE_M;
        // L'englobante contient tous les points, position comprise : c'est un PLANCHER, jamais a
        // rogner.
        double encloseHalfW = (maxV-minV)/2 + ZONE_MARGIN_CREATE_M;
        double halfWidth = encloseHalfW;
        // #25 : largeur cible (param F "ZoneW"). Si l'englobante est plus etroite que la cible, on
        // elargit vers la cible, mais sans depasser la longueur.
        // #77 : ce plafond ne vaut QUE pour cet elargissement. Il etait applique a la largeur finale,
        // donc il rabotait aussi l'englobante : des que le cap vers la marque affichee orientait le
        // rectangle en travers de l'etalement des marques, la largeur necessaire depassait la
        // longueur, le plafond la coupait, et la POSITION COURANTE tombait dehors -- l'alarme de
        // sortie partait a la seconde ou la zone apparaissait. Le rectangle peut donc etre plus large
        // que long, ce qui n'a rien de choquant.
        double targetHalfW = getZoneTargetWidthM() / 2.0;
        if (halfWidth < targetHalfW)
            halfWidth = Math.min(targetHalfW, Math.max(halfLen, encloseHalfW));
        double cU = (minU+maxU)/2, cV = (minV+maxV)/2;
        double cE = cU*Math.sin(th)+cV*Math.cos(th), cN = cU*Math.cos(th)-cV*Math.sin(th);
        double cLat = posLat + cN/111320.0, cLon = posLon + cE/(111320.0*cosLat);

        _writeLock.lock();
        try { _zone = new SurveillanceZone(cLat, cLon, angle, halfLen, halfWidth); }
        finally { _writeLock.unlock(); }
        GBLog.d("ZONE", String.format(Locale.US, "created c=%.6f,%.6f angle=%.1f L/2=%.0fm l/2=%.0fm npts=%d",
                cLat, cLon, angle, halfLen, halfWidth, pts.size()));
        return true;
    }

    public int getZoneState() {
        _readLock.lock();
        try { return _zone == null ? SurveillanceZone.NONE : _zoneState; }
        finally { _readLock.unlock(); }
    }

    private void setZoneState(int s) {
        _writeLock.lock(); try { _zoneState = s; } finally { _writeLock.unlock(); }
    }

    // Zone button tap: NONE -> create(ACTIVE) -> edit(EDIT) -> validate(ACTIVE).
    public void zoneCycle() {
        int st = getZoneState();
        if (st == SurveillanceZone.NONE) {
            if (createZoneFromSelection()) setZoneState(SurveillanceZone.ACTIVE);
        } else {
            setZoneState(st == SurveillanceZone.ACTIVE ? SurveillanceZone.EDIT : SurveillanceZone.ACTIVE);
        }
        notifyPropertyChanged(BR.zoneBtnIconRes);
    }

    // Long-press: delete the zone.
    public boolean zoneDelete() {
        _writeLock.lock();
        try { _zone = null; _zoneState = SurveillanceZone.NONE; } finally { _writeLock.unlock(); }
        notifyPropertyChanged(BR.zoneBtnIconRes);
        notifyPropertyChanged(BR.zoneBtnVisible);
        return true;
    }

    /**
     * The zone button follows the SELECTION, and not the panel.
     *
     * The two were the same thing until the game moved into the panel: it is now also shown while
     * a game is running, and the button came up with it -- a button to lay a surveillance zone, in
     * the middle of a game, with no mark selected.
     */
    @Bindable
    public boolean getZoneBtnVisible() { return (resolveShownMark() != null) || hasZone(); }

    // #26 : bouton de recentrage manuel, visible seulement quand le pan a ete
    // deplace (tempo de recentrage auto armee). Etat pousse par GameSurface.
    private boolean _recenterBtnVisible = false;

    @Bindable
    public boolean getRecenterBtnVisible() {
        return _recenterBtnVisible;
    }

    // Pousse par GameSurface quand l'etat du pan change (arme/expire).
    public void setRecenterBtnVisible(boolean visible) {
        if( _recenterBtnVisible != visible ) {
            _recenterBtnVisible = visible;
            notifyPropertyChanged(BR.recenterBtnVisible);
        }
    }

    // #52 : mode "retour a la normale EN PAUSE" (balai). S'active des qu'1 segment de mesure est cree ;
    // gele le recentrage auto ; seule sortie = clic sur le bouton balai (recenterOnPosition). Pousse par
    // GameSurface. Change aussi l'image du bouton (balai vs viseur+chiffre).
    private boolean _measurePaused = false;
    // Chiffre du compte a rebours affiche sur le bouton ("5".."1", ou "" en pause/hors pan). Pousse par GameSurface.
    private String _recenterCountdown = "";

    public boolean isMeasurePaused() {
        return _measurePaused;
    }

    public void setMeasurePaused(boolean paused) {
        if( _measurePaused != paused ) {
            _measurePaused = paused;
            notifyPropertyChanged(BR.recenterBtnIconRes);
        }
    }

    public void setRecenterCountdown(String s) {
        if( s == null ) s = "";
        if( !_recenterCountdown.equals(s) ) {
            _recenterCountdown = s;
            notifyPropertyChanged(BR.recenterCountdown);
        }
    }

    @Bindable
    public String getRecenterCountdown() {
        return _recenterCountdown;
    }

    // Image du bouton : balai en pause, sinon le viseur de recentrage (le chiffre est un overlay).
    @Bindable
    public int getRecenterBtnIconRes() {
        return _measurePaused ? R.drawable.btn_broom : R.drawable.btn_recenter;
    }

    // #26 : recentrage manuel sur la position courante. Leve la tempo de suspension du recentrage auto
    // (la camera revient sur la position) et masque le bouton.
    // #75 : en mode pause (balai), le meme bouton n'a plus le meme office : il efface les mesures et
    // RESTE en pan, tempo remise au maximum. Le chunk affiche n'est pas perdu, seul le dessin l'est.
    // #81 : la surface de rendu du chunk courant, ou null. Elle porte le moteur sonore, dont
    // l'ecoute d'un son dans l'ecran de choix a besoin.
    public GameSurface getGameSurface() {
        Chunk c = getCurrentChunk();
        return (c != null) ? c.getGameSurface() : null;
    }

    public void recenterOnPosition() {
        Chunk c = getCurrentChunk();
        GameSurface gs = (c != null) ? c.getGameSurface() : null;

        if( _measurePaused ) {
            if( gs != null ) {
                gs.clearMeasureAndKeepPan();
                gs.playSoundBroom();   // #75
            }
            setMeasurePaused(false);   // apres : le gel maintient la tempo au max pendant la remise a zero
            return;                    // bouton toujours visible, redevenu viseur + compte a rebours "5"
        }

        if( gs != null ) {
            gs.recenterOnPosition();
            gs.playSoundRecenter();    // #75
        }
        setRecenterBtnVisible(false);
    }

    // #52 : sons d'ouverture/fermeture du popup de mark (joues via le SoundPool de GameSurface).
    public void playPopupOpenSound() {
        Chunk c = getCurrentChunk();
        if( c != null && c.getGameSurface() != null )
            c.getGameSurface().playSoundPopupOpen();
    }

    public void playPopupCloseSound() {
        Chunk c = getCurrentChunk();
        if( c != null && c.getGameSurface() != null )
            c.getGameSurface().playSoundPopupClose();
    }

    @Bindable
    public int getZoneBtnIconRes() {
        // Icon shows the action of the NEXT tap.
        switch (getZoneState()) {
            case SurveillanceZone.ACTIVE: return R.drawable.ic_zone_edit;
            case SurveillanceZone.EDIT:   return R.drawable.ic_zone_move; // bouton 3 (arrows-move)
            default:                      return R.drawable.ic_zone_create;
        }
    }

    // DataBinding: allow android:src="@{intDrawableRes}" on ImageView/ImageButton.
    /**
     * #68 : retour d'un champ de saisie de la page Parameters. Renvoie l'adresse que l'application a
     * retenue, laquelle peut differer de ce qui a ete tape (nettoyage) ou etre l'ancienne (refus).
     */
    public interface OnTextCommitted {
        String onTextCommitted(String text);
    }

    // DataBinding de layout_param_row_text. Le texte n'est repose sur le champ que s'il differe,
    // sinon le curseur sauterait a chaque reconstruction du binding. La saisie n'est validee qu'au
    // "Done" du clavier et a la perte du focus, jamais a la frappe : une adresse est incomplete,
    // donc invalide, tant qu'on est en train de l'ecrire.
    @BindingAdapter({"entryText", "onTextCommitted"})
    public static void bindTextEntry(final EditText view, String text, final OnTextCommitted listener) {
        if ((text != null) && !text.equals(view.getText().toString()))
            view.setText(text);

        view.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId != EditorInfo.IME_ACTION_DONE)
                    return false;

                commitTextEntry(view, listener);

                return true;
            }
        });

        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus)
                    commitTextEntry(view, listener);
            }
        });
    }

    private static void commitTextEntry(EditText view, OnTextCommitted listener) {
        if (listener == null)
            return;

        String kept = listener.onTextCommitted(view.getText().toString());

        if ((kept != null) && !kept.equals(view.getText().toString()))
            view.setText(kept);
    }

    /** Easter egg: the level's progress bar, which reads nothing by itself. */
    @BindingAdapter({"waveProgress", "waveCount", "waveStress"})
    public static void bindWaveBar(WaveBar view, float progress, int count, int stress) {
        view.setWaveState(progress, count, stress);
    }

    /** Its width, which follows the panel's two shapes and not the panel itself. */
    @BindingAdapter("waveWide")
    public static void bindWaveBarWide(WaveBar view, boolean wide) {
        view.setWide(wide);
    }

    @BindingAdapter("android:src")
    public static void bindImageSrc(ImageView view, int resId) {
        if (resId != 0) view.setImageResource(resId);
    }

    // DataBinding: android:src="@{drawable}". Un AnimationDrawable ne demarre pas tout seul quand on
    // le pose sur une ImageView, et il ne peut demarrer qu'une fois la vue attachee -> post().
    @BindingAdapter("android:src")
    public static void bindImageSrc(ImageView view, Drawable drawable) {
        view.setImageDrawable(drawable);

        if (drawable instanceof AnimationDrawable) {
            final AnimationDrawable animation = (AnimationDrawable) drawable;
            animation.stop();
            view.post(new Runnable() {
                @Override
                public void run() {
                    animation.start();
                }
            });
        }
    }

    // Hardware course-over-ground from the GPS chipset (Location.getBearing), for the heading overlay.
    public double getHwBearing() {
        double v;
        _readLock.lock();
        try {
            v = _hwBearing;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    public boolean hasHwBearing() {
        boolean v;
        _readLock.lock();
        try {
            v = _hasHwBearing;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    @Bindable
    public double getSpeedKm() {
        double speedInKm;

        _readLock.lock();
        try {
            speedInKm = _speedInKm;
        } finally {
            _readLock.unlock();
        }

        return speedInKm;
    }

    @Bindable
    public String getSpeedString() {
        double speedInKm;
        double speedInMile;
        double speedInKnot;
        double speedInKmGPS;
        double speedInMileGPS;

        _readLock.lock();
        try {
            speedInKm = _speedInKm;
            speedInMile = _speedInMile;
            speedInKnot = _speedInKnot;

            speedInKmGPS = _speedInKmGPSManager;
            speedInMileGPS = _speedInMileGPSManager;
        } finally {
            _readLock.unlock();
        }

        //return String.format( "%.1fkm/h - %.1fmph - %.1fknots (%.1f - %.1f)", speedInKm, speedInMile, speedInKnot, speedInKmGPS, speedInMileGPS);
        return String.format("%.1fkm/h - %.1fmph - %.1fknots", speedInKm, speedInMile, speedInKnot);
    }

    // #79 Speed test page: the four speeds in km/h (average, short baseline, hardware, instant V2).
    public double[] getSpeedTestSpeeds() {
        double[] v = new double[4];

        _readLock.lock();
        try {
            v[0] = _averageSpeed;
            v[1] = _instantSpeed;
            v[2] = _speedInKmGPSManager;
            v[3] = _instantSpeedV2;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    // #79 Speed test page: copy of the fix history (index 0 = newest), accuracy in m, times in ms. The arrays are
    // allocated by the caller (getSpeedTestHistorySize() long); only plain copies under the lock.
    public int getSpeedTestFixes(double[] lat, double[] lon, double[] acc, long[] timeMs) {
        int fill;

        _readLock.lock();
        try {
            fill = Math.min(_fillCount, lat.length);
            for (int i = 0; i < fill; i++) {
                lat[i] = _fixes[i].getLatitude();
                lon[i] = _fixes[i].getLongitude();
                acc[i] = _fixes[i].getAccuracy();
                timeMs[i] = _fixes[i].getTimeMs();
            }
        } finally {
            _readLock.unlock();
        }

        long unit = timeUnitsPerMs();
        for (int i = 0; i < fill; i++) {
            timeMs[i] /= unit;
        }
        return fill;
    }

    public static int getSpeedTestHistorySize() {
        return NB_SWAP_VALUES;
    }

    @Bindable
    public String getSpeedKmString() {
        double value;
        String unit;

        _readLock.lock();
        try {
            switch (_speedUnit) {
                case 1:  value = _speedInMile; unit = "mph";  break;
                case 2:  value = _speedInKnot; unit = "kn";   break;
                default: value = _speedInKm;   unit = "km/h"; break;
            }
        } finally {
            _readLock.unlock();
        }
        // Below 20 show one decimal — but only when it isn't 0 (so 5.0 stays "5", 0 stays "0").
        // Point as decimal separator (Locale.US). 20 and above stay integer. (20, not 10, because
        // in knots the numbers are smaller so the decimal stays useful higher up.)
        // Below 20 show one decimal (dropped when .0); 20+ stays integer. Dot separator (Locale.US),
        // homogeneous with the displayed caps (fmt1).
        String num = (value < 20.0) ? fmt1(value) : String.format(Locale.US, "%.0f", value);
        return num + " " + unit;
    }


    // Property altitude
    @Bindable
    public Double getAltitude() {
        Double altitude;

        _readLock.lock();
        try {
            altitude = this._fixes[0].getAltitude();
        } finally {
            _readLock.unlock();
        }

        return altitude;
    }

    // Formatage d'une valeur en metres : metres entiers en dessous de 1000, sinon km a 1 decimale
    // (separateur US). Utilise pour l'altitude et l'accuracy du debug.
    private static String formatMetersOrKm(double m) {
        if (m < 1000.0) {
            return String.format(Locale.US, "%.0f m", m);
        }
        return String.format(Locale.US, "%.1f km", m / 1000.0);
    }

    @Bindable
    public String getAltitudeString() {
        Double altitude;
        Double average;

        _readLock.lock();
        try {
            altitude = this._fixes[0].getAltitude();
            average = _averageAltitude;
        } finally {
            _readLock.unlock();
        }

        // Last fix, then the averaged one actually used: "126 m (avg 124 m)".
        String last = (altitude == null) ? "--" : formatMetersOrKm(altitude);
        String avg = (average == null) ? "--" : formatMetersOrKm(average);
        return last + " (avg " + avg + ")";
    }


    /**
     * The last RAW fix: the position the satellites gave, with nothing projected onto it.
     *
     * getLatitude()/getLongitude() answer with the PREDICTED position -- the fix carried forward
     * along the cap for as long as it has been since it arrived -- which is what the avatar is
     * drawn on. Whoever needs the ground actually walked over, and not where the player is
     * reckoned to be by now, asks for this one.
     *
     * A copy, and taken in one go under the lock: two separate getters could hand back the
     * latitude of one fix and the longitude of the next.
     */
    public GPSFix getLastFix() {

        GPSFix fix;

        _readLock.lock();
        try {
            GPSFix cur = this._fixes[0];

            fix = new GPSFix(cur.getLongitude(), cur.getLatitude(), cur.getAltitude(),
                             cur.getAccuracy(), cur.getTimeMs());
        } finally {
            _readLock.unlock();
        }

        return fix;
    }

    /** The key held for that server, or an empty string. */
    public String getOverpassKey(String serverId) {

        if ((serverId == null) || (_context == null))
            return "";

        SharedPreferences p = prefs(_context);

        return (p == null) ? "" : p.getString(KEY_OVERPASS_KEY_PREFIX + serverId, "");
    }

    /** Remembers it, or forgets it when given nothing. */
    public void setOverpassKey(String serverId, String key) {

        if ((serverId == null) || (_context == null))
            return;

        SharedPreferences p = prefs(_context);

        if (p == null)
            return;

        if ((key == null) || (key.trim().length() == 0))
            p.edit().remove(KEY_OVERPASS_KEY_PREFIX + serverId).apply();
        else
            p.edit().putString(KEY_OVERPASS_KEY_PREFIX + serverId, key.trim()).apply();
    }

    // Property accuracy
    @Bindable
    public double getAccuracy() {
        double accuracy;

        _readLock.lock();
        try {
            accuracy = this._fixes[0].getAccuracy();
        } finally {
            _readLock.unlock();
        }

        return accuracy;
    }

    @Bindable
    public String getAccuracyString() {
        double accuracy;

        _readLock.lock();
        try {
            accuracy = this._fixes[0].getAccuracy();
        } finally {
            _readLock.unlock();
        }

        return formatMetersOrKm(accuracy);
    }


    // ---- Dual-baseline helpers (speed/heading from the raw history) + future prediction --------

    private static final class Baseline {
        double speedKmh; double bearingDeg; double dispM; double dtMs; boolean valid;
    }

    // _positionTimes are nanos on API>=17 (getElapsedRealtimeNanos), millis otherwise (getTime).
    private static long timeUnitsPerMs() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) ? 1_000_000L : 1L;
    }

    // Index of the oldest sample at least targetAgeMs old (vs sample[0]); falls back to the oldest
    // available when the history isn't that deep yet. Returns -1 if fewer than 2 samples exist.
    private int indexForAge(long targetAgeMs, int fillCount) {
        if (fillCount < 2) return -1;
        long t0 = _fixes[0].getTimeMs();
        long targetUnits = targetAgeMs * timeUnitsPerMs();
        int best = -1;
        for (int i = 1; i < fillCount; i++) {
            long age = t0 - _fixes[i].getTimeMs();
            if (age < 0) continue;          // clock anomaly / out-of-order guard
            best = i;
            if (age >= targetUnits) break;  // first sample old enough
        }
        return best;
    }

    // Straight-line speed/bearing/displacement between sample[idx] (older) and sample[0] (newest).
    // Straight line (not path-sum) so per-fix jitter doesn't inflate the speed.
    private Baseline computeBaseline(int idx) {
        Baseline b = new Baseline();
        if (idx < 1) return b; // valid stays false
        double dispKm = Chunk.measureKm(_fixes[idx].getLatitude(), _fixes[idx].getLongitude(), _fixes[0].getLatitude(), _fixes[0].getLongitude());

        // Height change counted too (a free fall is not a stop), but only beyond the vertical noise
        // of the fix: GPS altitude jumps by metres while standing still. The bearing stays 2D.
        Double alt0 = _fixes[0].getAltitude();
        Double altIdx = _fixes[idx].getAltitude();
        if (alt0 != null && altIdx != null) {
            Double vAcc = _fixes[0].getVerticalAccuracy();
            double noiseM = (vAcc != null) ? vAcc : VERT_NOISE_DEFAULT_M;
            double climbM = Math.max(0.0, Math.abs(alt0 - altIdx) - noiseM);
            dispKm = Math.hypot(dispKm, climbM / 1000.0);
        }

        b.dispM = dispKm * 1000.0;
        b.dtMs = (_fixes[0].getTimeMs() - _fixes[idx].getTimeMs()) / (double) timeUnitsPerMs();
        if (b.dtMs <= 0) return b; // valid stays false
        b.speedKmh = dispKm / (b.dtMs / 3600000.0);
        b.bearingDeg = Chunk.finalBearing(_fixes[idx].getLatitude(), _fixes[idx].getLongitude(), _fixes[0].getLatitude(), _fixes[0].getLongitude());
        b.valid = true;
        return b;
    }

    // Signed perpendicular (cross-track) distance in metres of point P from the line through anchor
    // A with bearing thetaDeg. Positive = to the RIGHT of the travel direction, negative = LEFT.
    private static double crossTrackM(double aLat, double aLon, double thetaDeg, double pLat, double pLon) {
        double dN = (pLat - aLat) * 111320.0;
        double dE = (pLon - aLon) * 111320.0 * Math.cos(Math.toRadians(aLat));
        double th = Math.toRadians(thetaDeg);
        return dE * Math.cos(th) - dN * Math.sin(th);
    }

    // Straight-line bearing from the older sample[idxOld] to the newer sample[idxNew] (idxOld>idxNew).
    private double bearingBetween(int idxOld, int idxNew) {
        return Chunk.finalBearing(_fixes[idxOld].getLatitude(), _fixes[idxOld].getLongitude(),
                                  _fixes[idxNew].getLatitude(), _fixes[idxNew].getLongitude());
    }

    // Shortest signed difference a - b, normalized to [-180, 180] (+ = clockwise / right).
    private static double signedAngleDiff(double a, double b) {
        double d = ((a - b) % 360.0 + 540.0) % 360.0 - 180.0;
        return d;
    }

    // Arc/chord ratio for a turn: a straight-line baseline measures the chord, but the real path is
    // the arc. ratio = halfAngle / sin(halfAngle), halfAngle = |omega| * window / 2 (radians).
    // 1.0 when going straight; clamped so a near-half-circle window can't blow the factor up.
    private static double arcFactor(double turnRateDegS, double windowMs) {
        double half = Math.abs(Math.toRadians(turnRateDegS) * (windowMs / 1000.0) / 2.0);
        if (half < 1e-3) return 1.0;
        if (half > 1.3) half = 1.3;  // ~149 deg swept: cap so sin() stays well away from 0
        return half / Math.sin(half);
    }

    @Bindable
    public String getMotionStatus() {
        String s;
        boolean still;
        _readLock.lock();
        try {
            s = _motionStatus;
            still = (_gameLevel > 0) && (_sensorMotion == MotionSensor.STATE_STILL);
        } finally {
            _readLock.unlock();
        }
        // Task #94: in game, "+ Still" when the phone itself does not move, whatever the GPS says
        // (what Young-Hee checks). Not shown in normal mode.
        if (still)
            s = s.isEmpty() ? "Still" : (s + " + Still");
        return s;
    }

    // Task #94: the phone itself does not move (MotionSensor), whatever the GPS says.
    public boolean isPhoneStill() {
        boolean v;
        _readLock.lock();
        try {
            v = (_sensorMotion == MotionSensor.STATE_STILL);
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    // Called by MotionSensor (its own thread) on every state change: the phone is being moved, or
    // it is not. The speed is never touched by the sensor -- the GPS owns it.
    public void setMotionSensorState(int state) {

        _writeLock.lock();
        try {
            _sensorMotion = state;
        } finally {
            _writeLock.unlock();
        }

        notifyPropertyChanged(BR.motionStatus);
    }

    // Direction-uncertainty band width relative to the acc circle (>= 1). MapGPSPos draws the blue
    // band with stroke width = 2 * accurencyRadius * this ratio.
    public double getUncertWidthRatio() {
        double v;
        _readLock.lock();
        try {
            v = _uncertWidthRatio;
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    // Raw GPS history (debug overlay): number of valid points and per-index lat/lon (index 0 = newest).
    public int getHistoryCount() {
        int n;
        _readLock.lock();
        try {
            n = _fillCount;
        } finally {
            _readLock.unlock();
        }
        return n;
    }

    public double getHistoryLat(int i) {
        double v = 0;
        _readLock.lock();
        try {
            if (i >= 0 && i < _fillCount && _fixes != null) v = _fixes[i].getLatitude();
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    public double getHistoryLon(int i) {
        double v = 0;
        _readLock.lock();
        try {
            if (i >= 0 && i < _fillCount && _fixes != null) v = _fixes[i].getLongitude();
        } finally {
            _readLock.unlock();
        }
        return v;
    }

    // Future-prediction horizon in seconds = (elapsed since last fix, clamped) + anticipation.
    private double predictionHorizonS() {
        long dt = System.currentTimeMillis() - _lastKnownWallMs;
        if (dt < 0) dt = 0;
        if (dt > PREDICTION_CLAMP_MS) dt = PREDICTION_CLAMP_MS;
        return (dt + effectiveAnticipationMs()) / 1000.0;
    }

    // Predicted (future) position = last known fix projected forward along the stabilized heading
    // by the average speed over predictionHorizonS(). This is what getLatitude()/getLongitude()
    // expose, so the whole app uses the anticipated position (cancels the real-vs-display lag).
    // Position PREDITE (projetee vers l'avant) sous forme de GPSFix : lat/lon predits + altitude /
    // accuracy / temps du fix courant. Centralise la projection (avant : dupliquee dans predictedLat/LonRaw).
    // Suppose le _readLock deja detenu par l'appelant.
    private GPSFix predictedCoordonateRaw() {
        double lat;
        double lon;
        // At rest (same condition as isStopped()/the "Stopped" status) return the raw fix: no forward
        // projection, otherwise a mark dropped while stopped would land several metres ahead. Also
        // covers auto mode, whose horizon grows with the idle time since the last fix.
        if (!_hasFix || _averageSpeed < SPEED_EPS_KMH) {
            lat = _lastKnownCoordonate.getLatitude();
            lon = _lastKnownCoordonate.getLongitude();
        } else {
            // Project along the CHORD bearing over the horizon (cap + omega*horizon/2): the straight-line
            // direction that best approximates the curved path during a turn. Off a turn omega~0 so it is
            // just the current cap. (Measured better than projecting along the full predicted cap.)
            double h = predictionHorizonS();
            double dir = _directionAngle + _turnRateDegS * (h / 2.0);
            double distKm = _averageSpeed / 3600.0 * h;
            double northKm = distKm * Math.cos(Math.toRadians(dir));
            double eastKm = distKm * Math.sin(Math.toRadians(dir));
            double cosLat = Math.cos(Math.toRadians(_lastKnownCoordonate.getLatitude()));
            if (Math.abs(cosLat) < 1e-6) cosLat = 1e-6;
            lat = _lastKnownCoordonate.getLatitude() + northKm / 111.32;
            lon = _lastKnownCoordonate.getLongitude() + eastKm / (111.32 * cosLat);
        }
        // The averaged altitude, not the last fix's: see _altAvgMs.
        GPSFix cur = _fixes[0];
        return new GPSFix(lon, lat, _averageAltitude, cur.getAccuracy(), cur.getTimeMs());
    }

    // Predicted heading = current cap rotated forward by the smoothed rotation rate over the horizon
    // (clamped). At rest there is no meaningful rotation, so it returns the current cap unchanged.
    private double predictedHeadingDeg() {
        if (!_hasFix || _averageSpeed < SPEED_EPS_KMH) return _directionAngle;
        double turn = _turnRateDegS * (effectiveHeadingAnticipMs() / 1000.0);
        if (turn >  PREDCAP_MAX_TURN_DEG) turn =  PREDCAP_MAX_TURN_DEG;
        if (turn < -PREDCAP_MAX_TURN_DEG) turn = -PREDCAP_MAX_TURN_DEG;
        double h = (_directionAngle + turn) % 360.0;
        if (h < 0) h += 360.0;
        return h;
    }

    private long effectiveHeadingAnticipMs() {
        return (_headingAnticipMs == HEADING_ANTICIP_AUTO) ? HEADING_ANTICIP_AUTO_MS : _headingAnticipMs;
    }

    // Effective average-speed window (ms): the long window by default, or the tunable value.
    private long effectiveSpeedAvgMs() {
        return (_speedAvgMs == SPDWIN_AUTO) ? LONG_WINDOW_MS : _speedAvgMs;
    }

    public void setLocation(Location location) {

        // Raw altitude above the WGS-84 ellipsoid, read before the lock; null when the fix has none.
        Double rawAltitude = location.hasAltitude() ? location.getAltitude() : null;
        Double verticalAccuracy = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
            verticalAccuracy = (double) location.getVerticalAccuracyMeters();
        }

        // Copied under the lock, for the declination computed once it is released.
        double geoLat;
        double geoLon;
        Double geoAltitude;
        long geoWallMs;
        // Copied under the lock, for the position saved once it is released (null = no save due).
        Context saveContext = null;
        double saveLat = 0.0;
        double saveLon = 0.0;
        boolean saveInLand = false;

        _writeLock.lock();
        try {
            // Horodatage du fix : nanos elapsed-realtime sur API>=17, sinon millis (converti via timeUnitsPerMs).
            long fixTime;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                fixTime = location.getElapsedRealtimeNanos();
            else
                fixTime = location.getTime();

            // Decalage des references (slot0 -> slot1 -> ...), puis nouveau fix en slot 0. Reproduit
            // exactement l'ancien decalage de valeurs (le plus vieux fix tombe).
            for (int i = this.NB_SWAP_VALUES; i > 1; i--) {
                this._fixes[i - 1] = this._fixes[i - 2];
            }
            // Mean sea level = WGS-84 + correction. Unknown correction (Android >= 14, no chunk yet)
            // means an unknown altitude, never a WGS-84 one passed off as sea level.
            Double altitude = null;
            if (rawAltitude != null && _altitudeCorrection != null) {
                altitude = rawAltitude + _altitudeCorrection;
            }
            this._fixes[0] = new GPSFix(location.getLongitude(), location.getLatitude(),
                    altitude, location.getAccuracy(), fixTime);
            this._fixes[0].setVerticalAccuracy(verticalAccuracy);

            //GBLog.d( "AppManager", String.format("Last lat:%f", this._fixes[0].getLatitude()));

            // getSpeed() est en METRES PAR SECONDE (doc Android), pas en mph : x3.6 donne des km/h.
            // L'ancien x1.609344 traitait des m/s comme des mph, la vitesse materielle etait donc
            // fausse d'un facteur 2.24. Comme pour _speedInKm/Mile/Knot plus bas, le km/h est
            // l'unite de base et les deux autres en derivent avec les memes coefficients.
            _speedInKmGPSManager = location.getSpeed() * 3.6f;
            _speedInMileGPSManager = _speedInKmGPSManager * 0.6213712f;
            _speedInKnotGPSManager = _speedInKmGPSManager * 0.5399565f;

            // Hardware course-over-ground, straight from the chipset (only present while moving).
            _hasHwBearing = location.hasBearing();
            _hwBearing = location.getBearing();

            // --- Speed & heading (multi-baseline) + last-known position -------------------------
            // Recomputed statelessly from the raw history (index 0 = newest). HEADING uses the
            // SHORTEST baseline that has already travelled far enough to clear GPS noise -> responsive
            // (near-instant at vehicle speed, a few seconds on foot) yet noise-free. SPEED keeps a
            // stable long-window average, made reactive by collapsing it on a short/long divergence.
            _fillCount = Math.min(_fillCount + 1, NB_SWAP_VALUES);
            int fill = _fillCount;

            _averageAltitude = averageAltitudeNoLock(
                    (_altAvgMs == ALTWIN_AUTO) ? ALTWIN_DEFAULT_MS : _altAvgMs, fill);

            double accuracyM = (location.getAccuracy() > 0.0) ? location.getAccuracy() : 20.0;
            double sigMinDispM    = Math.max(HEADING_MIN_DISP_M, HEADING_DISP_ACC_FACTOR * accuracyM);
            double stableMinDispM = Math.max(HEADING_STABLE_DISP_M, 4.0 * accuracyM);

            Baseline shortB = computeBaseline(indexForAge(SHORT_WINDOW_MS, fill));
            Baseline longB  = computeBaseline(indexForAge(LONG_WINDOW_MS, fill));

            // Shortest baseline whose displacement clears the noise floor: drives the "is moving"
            // decision and the instantaneous speed.
            Baseline sigB = null;
            for (int wi = 0; wi < HEADING_WINDOWS_MS.length; wi++) {
                Baseline b = computeBaseline(indexForAge(HEADING_WINDOWS_MS[wi], fill));
                if (b.valid && b.dispM >= sigMinDispM) { sigB = b; break; }
            }

            // Vehicle fast-stop (bonus): clearly moving fast and the last STOP_STILL_MS barely
            // moved -> stop in ~2 s.
            Baseline stillB = computeBaseline(indexForAge(STOP_STILL_MS, fill));
            boolean fastStop = (_averageSpeed > STOP_FROM_SPEED_KMH)
                    && stillB.valid && stillB.dispM < sigMinDispM;

            // Low-speed stop (works at ANY speed, incl. walking / boat): the displacement over
            // STILL_WINDOW_MS must stay below the noise floor for STOP_CONFIRM_FIXES consecutive
            // fixes. The "consecutive" rule rejects single noisy fixes; the window is long enough
            // that slow walking still clears the noise, so it isn't mistaken for a stop.
            Baseline stillSlowB = computeBaseline(indexForAge(STILL_WINDOW_MS, fill));
            double stopDispM = Math.max(STOP_DISP_M, accuracyM);
            if (stillSlowB.valid && stillSlowB.dispM < stopDispM) _stillCount++; else _stillCount = 0;
            boolean slowStop = (_stillCount >= STOP_CONFIRM_FIXES) && Math.abs(_turnRateDegS) <= TURN_RATE_MIN_DEGS;

            if (fastStop || slowStop) {
                // Stopped: drop immediately.
                _instantSpeed = 0.0;
                _averageSpeed = 0.0;
                _turnDir = 0;
                _turnRateDegS = 0.0;
            } else if (sigB == null) {
                // No movement signal this fix — at low speed that's usually just GPS noise, so EASE
                // toward 0 instead of snapping (a single noisy fix shouldn't flicker a walker to 0).
                _instantSpeed = 0.0;
                _averageSpeed *= (1.0 - SPEED_SMOOTH_ALPHA);
                if (_averageSpeed < SPEED_EPS_KMH) _averageSpeed = 0.0;
                _turnDir = 0;
                _turnRateDegS = 0.0;
            } else {
                // Instantaneous (reactive) speed.
                double instWinMs;
                if (shortB.valid && shortB.speedKmh <= TELEPORT_SPEED_KMH) {
                    _instantSpeed = shortB.speedKmh; instWinMs = shortB.dtMs;
                } else {
                    _instantSpeed = sigB.speedKmh;   instWinMs = sigB.dtMs;
                }
                // (B) Arc correction: a straight-line baseline measures the CHORD; in a turn the real
                // path is the longer ARC. Scale up using the rotation rate (previous fix's omega).
                _instantSpeed *= arcFactor(_turnRateDegS, instWinMs);

                // Average (stable) speed. Steady -> the (tunable) average window. On a STRONG
                // deceleration (short << long) collapse straight onto the SHORT window so it drops fast.
                double avg, avgWinMs;
                if (longB.valid) {
                    double diff = shortB.valid ? (shortB.speedKmh - longB.speedKmh) : 0.0;
                    Baseline avgB;
                    if (shortB.valid && diff < -SPEED_DIVERGE_KMH) {
                        avgB = shortB;                                                   // strong decel / stop
                    } else if (shortB.valid && Math.abs(diff) > Math.max(SPEED_DIVERGE_KMH, SPEED_DIVERGE_FRAC * longB.speedKmh)) {
                        avgB = computeBaseline(indexForAge(LONG_WINDOW_MIN_MS, fill));    // other change
                    } else {
                        avgB = computeBaseline(indexForAge(effectiveSpeedAvgMs(), fill)); // (C) steady: tunable window
                    }
                    avg = avgB.valid ? avgB.speedKmh : longB.speedKmh;
                    avgWinMs = avgB.valid ? avgB.dtMs : longB.dtMs;
                } else {
                    avg = sigB.speedKmh; avgWinMs = sigB.dtMs;
                }
                avg *= arcFactor(_turnRateDegS, avgWinMs);          // (B) arc correction
                if (avg > TELEPORT_SPEED_KMH) avg = _averageSpeed;  // reject GPS jump
                // Light EMA to damp the low-speed 0<->5 flicker (real vehicle stops are handled by
                // the fastStop branch above, so this doesn't slow them down).
                _averageSpeed += SPEED_SMOOTH_ALPHA * (avg - _averageSpeed);
                if (_averageSpeed < SPEED_EPS_KMH) _averageSpeed = 0.0;

                // --- Heading: stable line + CROSS-TRACK turn detection --------------------------
                // STABLE: shortest baseline that has travelled >= stableMinDispM (consistent angular
                // precision across speeds; spans up to ~30 s when walking slowly).
                Baseline stableHdg = null;
                for (int wi = 0; wi < HEADING_STABLE_WINDOWS_MS.length; wi++) {
                    Baseline b = computeBaseline(indexForAge(HEADING_STABLE_WINDOWS_MS[wi], fill));
                    if (b.valid && b.dispM >= stableMinDispM) { stableHdg = b; break; }
                }
                if (stableHdg == null) stableHdg = sigB; // not enough travel yet -> best available

                // --- (II) TURN: speed-adaptive rotation-rate estimate ---------------------------
                // Compare the bearing over the most recent window W to the bearing over the previous
                // W; (signed gap)/dt = omega. W grows at low speed (clean bearing), shrinks when fast.
                double omegaMeas = 0.0, spanRecentS = 0.0, headingRecent = _directionAngle;
                boolean headingRecentValid = false;
                {
                    double speedMps = Math.max(_averageSpeed / 3.6, 0.05);
                    double dispM = Math.max(TURN_RATE_DISP_M, 2.0 * accuracyM);
                    long wMs = (long) (dispM / speedMps * 1000.0);
                    if (wMs < TURN_RATE_W_MIN_MS) wMs = TURN_RATE_W_MIN_MS;
                    if (wMs > TURN_RATE_W_MAX_MS) wMs = TURN_RATE_W_MAX_MS;
                    int idxW  = indexForAge(wMs, fill);
                    int idx2W = indexForAge(2 * wMs, fill);
                    if (idxW >= 1) {
                        headingRecent = bearingBetween(idxW, 0);
                        spanRecentS = (_fixes[0].getTimeMs() - _fixes[idxW].getTimeMs()) / (double) timeUnitsPerMs() / 1000.0;
                        headingRecentValid = true;
                    }
                    if (idxW >= 1 && idx2W > idxW) {
                        double headingOld = bearingBetween(idx2W, idxW);
                        long midRecent = (_fixes[0].getTimeMs() + _fixes[idxW].getTimeMs()) / 2;
                        long midOld    = (_fixes[idxW].getTimeMs() + _fixes[idx2W].getTimeMs()) / 2;
                        double dtS = (midRecent - midOld) / (double) timeUnitsPerMs() / 1000.0;
                        if (dtS > 0.3) omegaMeas = signedAngleDiff(headingRecent, headingOld) / dtS;
                    }
                }
                _turnRateDegS += TURN_RATE_SMOOTH_ALPHA * (omegaMeas - _turnRateDegS);
                if (Math.abs(_turnRateDegS) < 0.05) _turnRateDegS = 0.0;
                int turnDir = (Math.abs(_turnRateDegS) > TURN_RATE_MIN_DEGS) ? (_turnRateDegS > 0 ? 1 : -1) : 0;
                _turnDir = turnDir;

                // Heading: straight -> the clean stable line; turning -> the responsive recent window,
                // recentred to NOW (correction A: the chord bearing trails by omega*span/2).
                if (turnDir != 0 && headingRecentValid) {
                    _directionAngle = (headingRecent + _turnRateDegS * (spanRecentS / 2.0)) % 360.0;
                    if (_directionAngle < 0) _directionAngle += 360.0;
                } else if (stableHdg.valid) {
                    _directionAngle = stableHdg.bearingDeg;
                }
            }

            // Motion events (displayed for tuning; later used to drive reactivity/anticipation).
            if (_averageSpeed < SPEED_EPS_KMH) {
                _motionStatus = "Stopped";
            } else {
                StringBuilder st = new StringBuilder();
                if (_turnDir < 0) {
                    st.append("Turn ←");         // left
                } else if (_turnDir > 0) {
                    st.append("Turn →");         // right
                }
                double dv = _instantSpeed - _averageSpeed;
                if (dv > ACCEL_EVENT_KMH) {
                    if (st.length() > 0) st.append(" + ");
                    st.append("Accel");
                } else if (dv < -ACCEL_EVENT_KMH) {
                    if (st.length() > 0) st.append(" + ");
                    st.append("Decel");
                }
                _motionStatus = (st.length() > 0) ? st.toString() : "Steady";
            }

            boolean stoppedLog = (_averageSpeed < SPEED_EPS_KMH);
            if (stoppedLog != _loggedStopped) {
                _loggedStopped = stoppedLog;
                GBLog.d("APPMAN", "STOPSTATE " + (stoppedLog ? "stopped" : "moving") + " (fix) avg=" + fmt1(_averageSpeed)
                        + " inst=" + fmt1(_instantSpeed) + " acc=" + fmt1(accuracyM) + " sensor=" + MotionSensor.name(_sensorMotion));
            }

            _speedInKm = _averageSpeed;
            _speedInMile = _speedInKm * 0.6213712;
            _speedInKnot = _speedInKm * 0.5399565;

            // Direction-uncertainty band width ratio (>= 1, relative to the acc circle): widen the
            // band so every considered past point sits inside it. For each point compute its
            // perpendicular (cross-track) distance to the heading line through the current position;
            // ratio = max(1, maxDistance / accuracy). 1 => band = acc circle; a point sticking out
            // widens it. Only the N most-recent points are used (Npts F-param; auto = all valid), so a
            // fast turn doesn't inflate the band with stale pre-turn points.
            int npts = effectiveUncertPoints(fill);
            double maxOffM = accuracyM; // floor at the accuracy (so the band always covers the circle)
            for (int i = 0; i < npts; i++) {
                double off = Math.abs(crossTrackM(_fixes[0].getLatitude(), _fixes[0].getLongitude(), _directionAngle,
                        _fixes[i].getLatitude(), _fixes[i].getLongitude()));
                if (off > maxOffM) maxOffM = off;
            }
            _uncertWidthRatio = maxOffM / accuracyM;

            // Last known (validated) position + timestamp: base for the future prediction exposed
            // through getLatitude()/getLongitude(). On reference le fix courant (porte lat/lon/alt/acc/temps).
            _lastKnownCoordonate = this._fixes[0];
            _lastKnownWallMs = System.currentTimeMillis();
            _hasFix = true;

            // Inputs of the magnetic declination, computed below once the lock is released.
            geoLat = _fixes[0].getLatitude();
            geoLon = _fixes[0].getLongitude();
            geoAltitude = _fixes[0].getAltitude();
            geoWallMs = _lastKnownWallMs;

            // Persist the position at most every POSITION_SAVE_INTERVAL_MS so a restart reopens here.
            if (_lastKnownWallMs - _lastSaveMs >= POSITION_SAVE_INTERVAL_MS) {
                _lastSaveMs = _lastKnownWallMs;
                saveContext = _context;
                saveLat = _lastKnownCoordonate.getLatitude();
                saveLon = _lastKnownCoordonate.getLongitude();
                saveInLand = _inLand;
            }
        } finally {
            _writeLock.unlock();
        }

        // Magnetic declination at the current position/time, from Android's geomagnetic model.
        // Outside the lock: GeomagneticField is an opaque constructor, only its result goes in.
        GeomagneticField geo = new GeomagneticField((float) geoLat, (float) geoLon,
                (geoAltitude != null) ? geoAltitude.floatValue() : 0.0f, geoWallMs);
        double declModelDeg = geo.getDeclination();
        _writeLock.lock();
        try {
            _declModelDeg = declModelDeg;
        } finally {
            _writeLock.unlock();
        }

        // Position save, outside the lock too: SharedPreferences is I/O.
        SharedPreferences sp = prefs(saveContext);
        if (sp != null) {
            sp.edit().putString(KEY_LAST_LAT, Double.toString(saveLat))
                     .putString(KEY_LAST_LON, Double.toString(saveLon))
                     .putBoolean(KEY_LAST_INLAND, saveInLand).apply();
        }


        notifyPropertyChanged(BR.latitudeString);
        notifyPropertyChanged(BR.latitude);
        notifyPropertyChanged(BR.longitudeString);
        notifyPropertyChanged(BR.longitude);
        notifyPropertyChanged(BR.altitudeString);
        notifyPropertyChanged(BR.altitude);
        notifyPropertyChanged(BR.accuracyString);
        notifyPropertyChanged(BR.accuracy);

        notifyPropertyChanged(BR.positionTime);

        notifyPropertyChanged(BR.speedString);
        notifyPropertyChanged(BR.speedKmString);
        notifyPropertyChanged(BR.speedKm);
        notifyPropertyChanged(BR.directionAngleString);
        notifyPropertyChanged(BR.directionAngle);
        notifyPropertyChanged(BR.headingDisplayString);
        notifyPropertyChanged(BR.adjustTrueString);     // #54 : caps de la page de reglage d'un compas
        notifyPropertyChanged(BR.adjustMagString);
        notifyPropertyChanged(BR.adjustArrowBadge);
        notifyPropertyChanged(BR.adjustHeadingTrue);
        notifyPropertyChanged(BR.adjustHeadingMagnetic);     // #54 : la fleche de guidage suit le cap
        notifyPropertyChanged(BR.declAutoString);       // #54 : la declinaison du modele suit la position
        notifyPropertyChanged(BR.declAutoModelString);
        notifyPropertyChanged(BR.markPanelVisible);
        notifyPropertyChanged(BR.markCapString);
        notifyPropertyChanged(BR.markEtaString);
        notifyPropertyChanged(BR.markNameString);
        notifyPropertyChanged(BR.markAlarmed);
        notifyPropertyChanged(BR.markArrowBadge);
        // Easter egg: the heart is the same arrow, so it goes stale in the same places.
        notifyPropertyChanged(BR.gaugeHeart);
        notifyPropertyChanged(BR.motionStatus);

        notifyPropertyChanged(BR.tileFileName);
        notifyPropertyChanged(BR.tileFilePosString);
    }



    // --- Waiting screen (splash) ----------------------------------------------------------------
    // It covers the screen as long as no map area is loaded, i.e. as long as currentChunk is null,
    // the only time it is. It stays at least SPLASH_MINI so a tip can be read even when the map
    // comes at once, and goes away after SPLASH_GARDE in any case: a map that never comes must not
    // lock the user behind the splash.

    // #86 test feedback: the map now comes from the cache almost instantly, and 2 s was too short
    // to read a tip. Every "next tip" tap starts this delay again (splashRestartMinimum).
    private static final long SPLASH_MINI  = 4000;

    // The safety net is only there so the user is never locked behind an opaque screen, not to cut
    // a slow load short. A first launch that opens the database, downloads an area and waits for a
    // GPS fix easily takes half a minute: firing then would lift the splash onto an empty sea,
    // which is exactly what we want to avoid.
    private static final long SPLASH_GARDE = 60000;

    private boolean _splashVisible = true;
    private long _splashDepart = 0;
    private Handler _splashHandler = null;

    // The removal checks, when it runs, that SPLASH_MINI has really elapsed since the LAST start,
    // and puts itself back otherwise. The start can move while the removal is already scheduled --
    // a "next tip" tap -- and it is scheduled from two threads (the render thread through
    // splashMapDrawn, the UI thread here): checking at run time makes the order of those posts
    // irrelevant, the splash can never go before the reader's 4 s.
    private final Runnable _splashRetrait = new Runnable() {
        @Override
        public void run() {
            long left;
            Handler h;

            _readLock.lock();
            try {
                left = SPLASH_MINI - (SystemClock.uptimeMillis() - _splashDepart);
                h = _splashHandler;
            } finally {
                _readLock.unlock();
            }

            if( (left > 0) && (h != null) ) {
                h.removeCallbacks(this);
                h.postDelayed(this, left);
                return;
            }

            splashHide();
        }
    };

    /**
     * #86 test feedback: a tap on "next tip" gives the reader a full SPLASH_MINI again, whether the
     * map is already drawn or not. Moving the start is enough: the scheduled removal re-checks it
     * when it runs.
     */
    public void splashRestartMinimum() {
        _writeLock.lock();
        try {
            if( _splashVisible )
                _splashDepart = SystemClock.uptimeMillis();
        } finally {
            _writeLock.unlock();
        }
    }

    @Bindable
    public boolean getSplashVisible() {
        _readLock.lock();
        try {
            return _splashVisible;
        } finally {
            _readLock.unlock();
        }
    }

    /** Appele une fois la vue principale en place : demarre le compte du temps minimum. */
    public void splashStart() {

        Handler h;

        _writeLock.lock();
        try {
            if( _splashHandler == null ) {
                _splashHandler = new Handler(Looper.getMainLooper());
            }
            _splashDepart = SystemClock.uptimeMillis();
            h = _splashHandler;
        } finally {
            _writeLock.unlock();
        }

        h.postDelayed(_splashRetrait, SPLASH_GARDE);
    }

    /**
     * Appele par le fil de rendu a chaque image tant que le splash tient. Le splash ne se retire
     * pas sur la seule existence d'un chunk : a cet instant le fil de rendu ne l'a pas encore
     * adopte et l'on verrait la mer vide. On attend que la zone soit REELLEMENT tracee, marques
     * remises en contexte comprises, et, s'il y a un point GPS, que la position soit dedans. Sans
     * point GPS on n'attend pas : en interieur il n'en viendra peut-etre jamais.
     *
     * Rend vrai quand le retrait est acquis, pour que l'appelant cesse d'appeler.
     */
    public boolean splashMapDrawn(Chunk dessine) {

        boolean visible;
        long depart;
        Handler h;
        boolean fix;
        double lat, lon;

        _readLock.lock();
        try {
            visible = _splashVisible;
            depart  = _splashDepart;
            h       = _splashHandler;
            fix     = _hasFix;
            lat     = _lastKnownCoordonate.getLatitude();
            lon     = _lastKnownCoordonate.getLongitude();
        } finally {
            _readLock.unlock();
        }

        if( !visible ) {
            return true;
        }

        if( dessine == null || h == null ) {
            return false;
        }

        if( fix && !dessine.coordonateInChunk(lat, lon) ) {
            return false;
        }

        long reste = SPLASH_MINI - (SystemClock.uptimeMillis() - depart);

        h.removeCallbacks(_splashRetrait);
        h.postDelayed(_splashRetrait, Math.max(0, reste));

        return true;
    }

    private void splashHide() {

        boolean change = false;

        _writeLock.lock();
        try {
            if( _splashVisible ) {
                _splashVisible = false;
                change = true;
            }
        } finally {
            _writeLock.unlock();
        }

        if( change ) {
            notifyPropertyChanged(BR.splashVisible);
            GBLog.d(TAG, "splash retire");
        }
    }


    // Property CurrentChunk
    @Bindable
    public Chunk getCurrentChunk() {
        Chunk currentChunk;

        _readLock.lock();
        try {
            currentChunk = this._currentChunk;
        } finally {
            _readLock.unlock();
        }

        return currentChunk;
    }

    public void setCurrentChunk(Chunk currentChunk) {

        _writeLock.lock();
        try {
            this._currentChunk = currentChunk;
        } finally {
            _writeLock.unlock();
        }

        notifyPropertyChanged(BR.currentChunk);
    }

    // Re-point AND re-scale every shared mark (personal + proximity clones) onto `newChunk`.
    // Called from the RENDER thread at the exact instant the map switches, so the marks' x,y land in
    // the same frame as the camera (no offset while a pan defers the swap). setChunkWithContextScale
    // also lets the previous chunk be GC'd (the mark stops pinning it). Marks already on `newChunk`
    // are skipped.
    //
    // Only the two lists are copied under the lock; the re-pointing and re-scaling run outside it.
    // setChunkWithContextScale used to run under the read lock and reach PersonalMark's static
    // lock: one leg of a three-thread deadlock (render, main, proximity loop).
    public void switchAndReContextMarksToChunk(Chunk newChunk) {
        List<PersonalMark> marks;
        List<PersonalMark> proximityMarks;

        _readLock.lock();
        try {
            marks = new ArrayList<PersonalMark>(_marks);
            proximityMarks = new ArrayList<PersonalMark>(_proximityMarks);
        } finally {
            _readLock.unlock();
        }

        for( PersonalMark pm: marks ) {
            if( pm.getChunk() != newChunk ) {
               pm.setChunkWithContextScale(newChunk);
            }

        }
        // Proximity marks are CLONES (new PersonalMark(mark)) that captured the
        // chunk at clone time and were NEVER re-pointed -> they pinned old chunks
        // alive (heap dump: 4 live Chunks, ~369 marks). Re-point them too so the
        // previous chunk becomes collectable.
        for( PersonalMark pm: proximityMarks ) {
            if( pm.getChunk() != newChunk ) {
               pm.setChunkWithContextScale(newChunk);
            }
        }
    }

    // Double-tap sur la carte (ou tout appelant) : force le rechargement du chunk au prochain tick
    // (comme apres un download de map). Footer info a la demande, puis "Chunk reloaded!" quand le
    // reload manuel aboutit reellement (voir myThread).
    public void requestChunkReload() {
        _writeLock.lock();
        try {
            _chunkReloadOrigin = RELOAD_MANUAL;
            // #89: the same gesture sends the travel mode back to undetermined and rearms its
            // timers. Under the write lock, like the loop's update: TravelMode is not thread-safe
            // on its own.
            _travelMode.reset();
        } finally {
            _writeLock.unlock();
        }
        wakeSupervisor();
        ViewsController.notifyInfo("Chunk reload requested…"); // footer bleu au moment de la demande
    }

    // Android >= 14: ele_wgs84 tags (marks saved under an older Android) become ele. Finds nothing
    // almost always, and then costs one query.
    private void migrateEleWgs84(Context context) {
        AppDB appDB;

        _readLock.lock();
        try {
            appDB = _appDB;
        } finally {
            _readLock.unlock();
        }

        if (appDB == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return;
        }

        try {
            PersonalMarkDB pmDB = new PersonalMarkDB(appDB);
            pmDB.migrateEleWgs84(context);
        } catch (Exception e) {
            GBLog.e(TAG, "migrateEleWgs84: " + e);
        }
    }

    private boolean dbMarksInfos() {
        boolean bOK = false;
        AppDB appDB = null;
        boolean initDBOK;

        try {
            _readLock.lock();
            try {
                initDBOK = _initializedDB;
                appDB = _appDB;
            } finally {
                _readLock.unlock();
            }

            if( initDBOK && appDB != null ) {
                // Quiet under the lock, observers called once it is released: a listener must
                // never run under the write lock.
                _writeLock.lock();
                try {
                    _marks.clearQuietly();
                    _proximityMarks.clearQuietly();
                    _marksVersion++;
                } finally {
                    _writeLock.unlock();
                }
                _marks.notifyObservers();
                _proximityMarks.notifyObservers();
                notifyPropertyChanged(BR.marks);

                PersonalMarkDB pmDB = new PersonalMarkDB(appDB);
                Cursor c = pmDB.selectAll();
                if (c.moveToFirst()) {
                    do {
                        PersonalMark pm = pmDB.cursorToObj(c);

                        _writeLock.lock();
                        try {
                            _marks.addQuietly(pm);
                            _marksVersion++;
                        } finally {
                            _writeLock.unlock();
                        }
                        //notifyPropertyChanged(BR.maps);
                    } while (c.moveToNext());
                    _marks.notifyObservers();
                    notifyPropertyChanged(BR.marks);

                    _writeLock.lock();
                    try {
                        //_marksLoaded = true;
                        _marksUpdated = true;
                    } finally {
                        _writeLock.unlock();
                    }
                }
                c.close();
                bOK = true;

                _writeLock.lock();
                try {
                    _marksLoaded = true;
                } finally {
                    _writeLock.unlock();
                }

                refreshDrawMarks();
            }

        } catch (Exception e){
            GBLog.e( TAG, "ERR"+e.toString());
        }

        return bOK;
    }

    private boolean dbMapsInfos() {
        boolean bOK = false;
        AppDB appDB = null;
        boolean initDBOK;

        try {
            _readLock.lock();
            try {
                initDBOK = _initializedDB;
                appDB = _appDB;
            } finally {
                _readLock.unlock();
            }

            if( initDBOK && appDB != null ) {
                _writeLock.lock();
                try {
                    _maps.clear();
                } finally {
                    _writeLock.unlock();
                }
                notifyPropertyChanged(BR.maps);

                MapInfoDB miDB = new MapInfoDB(appDB);
                Cursor c = miDB.selectAll();
                if (c.moveToFirst()) {
                    do {
                        MapInfo mi = miDB.cursorToObj(c);

                        mi.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
                            @Override
                            public void onPropertyChanged(Observable sender, int propertyId) {

                                if( propertyId == BR.finished ) {
                                    GBLog.d(getClass().getName(), "map changed");

                                    _writeLock.lock();
                                    try {
                                        _chunkReloadOrigin = RELOAD_MAP;
                                    } finally {
                                        _writeLock.unlock();
                                    }
                                    wakeSupervisor();

                                }

                            }
                        });

                        _writeLock.lock();
                        try {
                            _maps.add(mi);
                        } finally {
                            _writeLock.unlock();
                        }
                        //notifyPropertyChanged(BR.maps);
                    } while (c.moveToNext());
                    notifyPropertyChanged(BR.maps);

                    _writeLock.lock();
                    try {
                        //_mapsLoaded = true;
                        _chunkReloadOrigin = RELOAD_INTERNAL;
                    } finally {
                        _writeLock.unlock();
                    }
                    wakeSupervisor();
                }
                c.close();
                bOK = true;

                _writeLock.lock();
                try {
                    _mapsLoaded = true;
                } finally {
                    _writeLock.unlock();
                }
            }

        } catch (Exception e){
            GBLog.e( TAG, "ERR"+e.toString());
        }

        return bOK;
    }

    private boolean downloadMapsInfos() {
        boolean bOK = false;

        // #68 : l'adresse est un reglage, plus une constante. Lue UNE fois, ici, dans une variable
        // locale : l'utilisateur peut la changer pendant que ce thread travaille.
        String url = getMapsManifestUrl();

        try {

            HttpClient client = new DefaultHttpClient();
            HttpGet getMethod = new HttpGet(url);
            HttpResponse response = client.execute(getMethod);
            // Examine the response status
            GBLog.d("Maps", response.getStatusLine().toString());

            int statusCode = response.getStatusLine().getStatusCode();

            HttpEntity entity = response.getEntity();
            InputStream in = (InputStream) entity.getContent();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            StringBuilder str = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                str.append(line + "\n");
            }
            in.close();

            //GBLog.d("Maps", "response:"+str.toString());

            GBAPIObject data = GBAPIObject.jsonToGBAPIObject(str.toString());

            if( (data != null) && (data.Status.equals("OK")) ) {

                _writeLock.lock();
                try {
                    if(data.Map != null) {
                        for( MapInfo mi : data.Map ) {
                            MapInfoDB miDB = new MapInfoDB(_appDB);
                            miDB.replace(mi);
                        }
                    }
                } finally {
                    _writeLock.unlock();
                }
                bOK = dbMapsInfos();
            }

        } catch (Exception e){
            GBLog.e( "Maps", "ERR"+e.toString());
        }

        return bOK;
    }

    // Local equivalent of downloadMapsInfos(): instead of querying store.golden-block.fr for a
    // list of MapInfo, it discovers the map DBs already installed on storage (MapInfo.findInstalledMaps)
    // and registers the ones not already known. Makes the app self-sufficient without the (dead) server.
    private boolean scanLocalMapsInfos() {
        boolean bOK = false;

        try {
            java.util.List<MapInfo> found = MapInfo.findInstalledMaps();

            _writeLock.lock();
            try {
                MapInfoDB miDB = new MapInfoDB(_appDB);

                // Collect already-known (name|version) so we don't clobber server/cached rows.
                java.util.Set<String> known = new java.util.HashSet<>();
                Cursor c = miDB.selectAll();
                if (c.moveToFirst()) {
                    do {
                        MapInfo mi = miDB.cursorToObj(c);
                        known.add(mi.getName() + "|" + mi.getVersion());
                    } while (c.moveToNext());
                }
                c.close();

                for (MapInfo mi : found) {
                    if (known.contains(mi.getName() + "|" + mi.getVersion()))
                        continue; // already registered (e.g. by the server) -> keep its url
                    miDB.replace(mi);
                }
            } finally {
                _writeLock.unlock();
            }

            bOK = dbMapsInfos();

        } catch (Exception e){
            GBLog.e( "Maps", "ERR"+e.toString());
        }

        return bOK;
    }

    // #17 : position "a deposer" (meme source que addNewMark) pour le bouton "position actuelle" du popup.
    // A fresh GPSFix (lat, lon and altitude), never one of the shared fixes.
    public GPSFix getCurrentDropFix() {
        GPSFix pcr;
        _readLock.lock();
        try {
            pcr = predictedCoordonateRaw();
        } finally {
            _readLock.unlock();
        }
        return pcr;
    }

    public void addNewMark() {

        double lon;
        double lat;
        Double altitude;
        Chunk chunk;

        _readLock.lock();
        try {
            GPSFix pcr = predictedCoordonateRaw();
            lon = pcr.getLongitude();
            lat = pcr.getLatitude();
            altitude = pcr.getAltitude();
            chunk = _currentChunk;
        } finally {
            _readLock.unlock();
        }

        GBLog.d( TAG, String.format("addNewMark lon:%.1f lat:%.1f", lon, lat));

        PersonalMark mark = new PersonalMark( chunk, lon, lat );
        mark.setAltitude( altitude );

        ViewsController vc = ViewsController.getInstance();

        vc.ShowPersonalMark( mark );
    }

    // #52 : creation d'une mark a une position DONNEE (point vise par un viseur de mesure), et non
    // a la position courante. Meme popup d'edition que addNewMark / le bouton "+".
    // The aimed point's altitude is unknown: it is prefilled with the current fix's, usually close
    // and better than nothing for the user.
    public void addNewMarkAt( double lon, double lat ) {
        Double altitude;
        _readLock.lock();
        try {
            altitude = _averageAltitude;
        } finally {
            _readLock.unlock();
        }

        addNewMarkAt( lon, lat, "", -1, altitude );
    }

    // #65 : meme chose avec un titre pre-rempli (libelle recu d'une autre application).
    public void addNewMarkAt( double lon, double lat, String title ) {
        addNewMarkAt( lon, lat, title, -1 );
    }

    // #65 : ouverture d'une position RECUE d'un partage. Si une marque existe deja a cet endroit --
    // au sens du reglage "Same place", titre compris quand l'option est active -- on ouvre celle-la
    // plutot que d'en empiler une seconde par-dessus : se repartager le meme lieu est le cas
    // courant, pas l'exception. L'icone recue n'ecrase alors rien, la marque existante est celle de
    // l'utilisateur et son icone est un choix qu'il a fait.
    // L'editeur travaille TOUJOURS sur un clone, comme la page de recherche : rien ne touche a la
    // marque reelle tant qu'il n'a pas enregistre.
    // altitude: what the link carries, null if nothing. A WGS-84 one (RFC 5870 third coordinate) is
    // brought to sea level on Android >= 14, as the GPS altitude is; kept as is below that, where
    // every altitude is WGS-84.
    public void openSharedMarkAt( double lon, double lat, String title, int iconId, Double altitude, boolean altitudeWgs84 ) {
        if( altitude != null && altitudeWgs84
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ) {
            Context context;
            _readLock.lock();
            try {
                context = _context;
            } finally {
                _readLock.unlock();
            }
            Double correction = altitudeCorrectionAt( context, lat, lon );
            altitude = (correction != null) ? altitude + correction : null;
        }

        PersonalMark existing = findMarkAt( lat, lon, altitude, title );

        if( existing == null ) {
            addNewMarkAt( lon, lat, title, iconId, altitude );
            return;
        }

        ViewsController.getInstance().ShowPersonalMark( new PersonalMark( existing ) );
    }

    // #65 : meme chose avec l'icone recue d'un partage MyBuoy ; -1 garde la derniere icone utilisee.
    // No altitude here: a shared position only gets the one its link holds (openSharedMarkAt).
    public void addNewMarkAt( double lon, double lat, String title, int iconId ) {
        addNewMarkAt( lon, lat, title, iconId, null );
    }

    private void addNewMarkAt( double lon, double lat, String title, int iconId, Double altitude ) {
        Chunk chunk;
        _readLock.lock();
        try {
            chunk = _currentChunk;
        } finally {
            _readLock.unlock();
        }

        GBLog.d( TAG, String.format("addNewMarkAt lon:%.1f lat:%.1f", lon, lat));

        PersonalMark mark = new PersonalMark( chunk, lon, lat, title );
        mark.setAltitude( altitude );

        if( iconId >= 0 ) {
            mark.setBitmapIdFromShare( iconId );
        }

        ViewsController.getInstance().ShowPersonalMark( mark );
    }

    // #67 : distance et fleche d'une marque qui n'est PAS dans _marks -- un resultat de recherche,
    // pas encore enregistre. La boucle de fond ne cale que les marques de la liste ; celle-ci est
    // calee une fois, a l'arrivee des resultats, par le meme calcul (cf. la boucle, plus haut).
    public void updateMarkGeometry(PersonalMark mark) {

        if (mark == null)
            return;

        double latitude, longitude, heading;
        Double altitude;
        int elevMinM;

        _readLock.lock();
        try {
            latitude = _lastKnownCoordonate.getLatitude();
            longitude = _lastKnownCoordonate.getLongitude();
            altitude = _averageAltitude;
            elevMinM = _elevMinMeters;
            heading = directionHeadingNoLock();   // cap vue when stopped
        } finally {
            _readLock.unlock();
        }

        double groundKm = Chunk.measureKm(mark.getLatitude(), mark.getLongitude(), latitude, longitude);
        mark.setDistance(Chunk.measureKm(mark.getLatitude(), mark.getLongitude(), mark.getAltitude(),
                latitude, longitude, altitude));
        mark.setElevationFrom(altitude, groundKm, elevMinM);

        // Comme dans la boucle : les deux termes sont en cap VRAI, la fleche est un gisement
        // relatif au cap courant.
        double cap = Chunk.initialBearing(latitude, longitude, mark.getLatitude(), mark.getLongitude());
        mark.setHeadingFromBearing(cap - heading);
    }

    // #97 : what the sort reads, copied once per mark: a mark saved from the editor during the
    // sort would otherwise change under the comparator, and TimSort throws when it notices.
    private static final class DrawEntry {
        final PersonalMark mark;
        final boolean alarming;
        final double latitude;
        final double longitude;

        DrawEntry(PersonalMark mark) {
            this.mark = mark;
            this.alarming = mark.isAlarming();
            this.latitude = mark.getLatitude();
            this.longitude = mark.getLongitude();
        }
    }

    // #97 : sorts OUTSIDE any lock, then publishes under the write lock only if no mark was
    // added or removed since `marks` was copied. If one was, the add or the remove publishes
    // its own list.
    private void sortAndPublishDrawMarks(List<PersonalMark> marks, long version) {

        List<DrawEntry> entries = new ArrayList<DrawEntry>(marks.size());
        for (PersonalMark m : marks) {
            if (m != null)
                entries.add(new DrawEntry(m));
        }

        Collections.sort(entries, new Comparator<DrawEntry>() {
            @Override
            public int compare(DrawEntry e1, DrawEntry e2) {
                return PersonalMark.compareForDraw(
                        e1.alarming, e1.latitude, e1.longitude,
                        e2.alarming, e2.latitude, e2.longitude);
            }
        });

        List<PersonalMark> sorted = new ArrayList<PersonalMark>(entries.size());
        for (DrawEntry e : entries) {
            sorted.add(e.mark);
        }
        List<PersonalMark> published = Collections.unmodifiableList(sorted);

        _writeLock.lock();
        try {
            if (version == _marksVersion)
                _drawMarks = published;
        } finally {
            _writeLock.unlock();
        }
    }

    // #97 : after an add, a remove or a reload.
    private void refreshDrawMarks() {
        List<PersonalMark> marks;
        long version;

        _readLock.lock();
        try {
            marks = new ArrayList<PersonalMark>(_marks);
            version = _marksVersion;
        } finally {
            _readLock.unlock();
        }

        sortAndPublishDrawMarks(marks, version);
    }

    // #97 : the marks in draw order, for Chunk.draw. Read without any lock: the list is never
    // changed once published.
    public List<PersonalMark> getMarksInDrawOrder() {
        return _drawMarks;
    }

    // The mark is placed on the current chunk OUTSIDE the lock, then added quietly under it, and
    // the observers are called after. If the map switched in between, the render thread has already
    // re-pointed every listed mark without this one: it is re-pointed here instead.
    public void addMarkInList(PersonalMark mark) {
        Chunk chunk;
        Chunk chunkNow;

        _readLock.lock();
        try {
            chunk = _currentChunk;
        } finally {
            _readLock.unlock();
        }

        mark.setChunk(chunk);
        mark.contextScale();

        _writeLock.lock();
        try {
            _marks.addQuietly(mark);
            _marksVersion++;
            chunkNow = _currentChunk;
        } finally {
            _writeLock.unlock();
        }

        if( chunkNow != chunk )
            mark.setChunkWithContextScale(chunkNow);

        refreshDrawMarks();
        _marks.notifyObservers();
        // A newly added mark is selected by default -> it becomes the shown one (same as tapping it).
        onMarkSelectionToggled(mark);
    }

    // #67 : cette marque est-elle TOUJOURS dans la liste ? La page de recherche s'en sert pour
    // savoir si son bouton dit Create ou Edit : une marque supprimee depuis l'editeur garde son
    // ancien identifiant, seul le fait d'etre encore dans la liste fait foi.
    public boolean isMarkInList(PersonalMark mark) {

        if (mark == null)
            return false;

        _readLock.lock();
        try {
            return (_marks != null) && _marks.contains(mark);
        } finally {
            _readLock.unlock();
        }
    }

    public void removeMarkInList(PersonalMark mark) {
        _writeLock.lock();
        try {
            _marks.removeQuietly(mark);
            _marksVersion++;
        } finally {
            _writeLock.unlock();
        }

        refreshDrawMarks();
        _marks.notifyObservers();
        // If the shown mark was removed, re-resolve (nearest selected, or hide the panel).
        resolveShownMark();
        notifyMarkPanel();
    }

    // From the current position to a point; straight line when the point's altitude and the current
    // (averaged) one are both known.
    public double getKmFrom(double longitude, double latitude, Double altitude) {
        double lon;
        double lat;
        Double alt;

        _readLock.lock();
        try {
            GPSFix pcr = predictedCoordonateRaw();
            lon = pcr.getLongitude();
            lat = pcr.getLatitude();
            alt = pcr.getAltitude();
        } finally {
            _readLock.unlock();
        }

        return Chunk.measureKm(latitude, longitude, altitude, lat, lon, alt);
    }

    public double getKmFrom(double longitude, double latitude) {
        double lon;
        double lat;

        double d1, d2;

        _readLock.lock();
        try {
            GPSFix pcr = predictedCoordonateRaw();
            lon = pcr.getLongitude();
            lat = pcr.getLatitude();
        } finally {
            _readLock.unlock();
        }

        //d1 = Chunk.getKmFrom(longitude, latitude, lon, lat);
        d2 = Chunk.measureKm(latitude, longitude, lat, lon);

        //GBLog.d( "AppManager", String.format("d1:%.3f, d2:%.3f", d1, d2));

        return d2;
    }


    public List<File> getStorageLocations() {
        List<File> storageLocations = new ArrayList<>();

        File storageDir = Environment.getExternalStorageDirectory();


        if (_context != null) {
            String storagePath = storageDir.getAbsolutePath();

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                File[] extFileDirs = extFileDirs = _context.getExternalFilesDirs(null);

                String suffixToStrip = null;
                for (File nextFile : extFileDirs) {
                    String nextPath = nextFile.getAbsolutePath();
                    if (nextPath.startsWith(storagePath)) {
                        suffixToStrip = nextPath.substring(storagePath.length());
                        break;
                    }
                }
                if (suffixToStrip != null) {
                    for (File nextFile : extFileDirs) {
                        String nextPath = nextFile.getAbsolutePath();
                        int stripIndex = nextPath.lastIndexOf(suffixToStrip);
                        if (stripIndex > -1) {
                            File path = new File(nextPath.substring(0, stripIndex));
                            storageLocations.add(path);
                        }
                    }
                }
            }
        }

        if (storageLocations.size() == 0) {
            storageLocations.add(storageDir);
        }

        return storageLocations;
    }

    // #65 : vrai une fois initDB() passe. Une mark venue d'une autre application doit attendre ce
    // moment : un Save avant l'ouverture de la base n'ecrirait nulle part.
    public boolean isDBInitialized() {
        boolean initialized;

        _readLock.lock();
        try {
            initialized = _initializedDB;
        } finally {
            _readLock.unlock();
        }

        return initialized;
    }

    public void initDB(Activity activity) {
        boolean alreadyInit = false;

        _writeLock.lock();
        try {

            alreadyInit = _initializedDB;
            if( !alreadyInit ) {
                System.loadLibrary("sqliteX");
                _appDB = new AppDB(activity);
                _initializedDB = true;
            }
        } finally {
            _writeLock.unlock();
        }

        if( !alreadyInit ) {
            // Blocking, like a schema upgrade: the marks must be read already migrated.
            migrateEleWgs84(activity);
            dbMarksInfos();
            dbMapsInfos();
        }
    }

    public void GPSStart(Activity activity) {

        _writeLock.lock();
        try {
            if (_gps == null)
                _gps = new GPSTracker(activity.getApplicationContext());
        } finally {
            _writeLock.unlock();
        }

    }


    public void NotificationTest(Activity activity) {

        /*

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<String>() {
            @Override
            public void onComplete(@NonNull Task<String> task) {

                if (!task.isSuccessful()) {
                    GBLog.w("AppManager", "Fetching FCM registration token failed", task.getException());
                    return;
                }

                // Get new FCM registration token
                String token = task.getResult();

                // Log and toast
                //String msg = getString(R.string.msg_token_fmt, token);
                GBLog.d("AppManager", token);
                //Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();

            }
        });

        FirebaseInstallations.getInstance().getId()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (task.isSuccessful()) {
                            GBLog.d("AppManager", "Installation ID: " + task.getResult());
                        } else {
                            GBLog.e("AppManager", "Unable to get Installation ID");
                        }
                    }
                });


         */


        /*
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            GBLog.w(TAG, "Fetching FCM registration token failed", task.getException());
                            return;
                        }

                        // Get new FCM registration token
                        String token = task.getResult();

                        // Log and toast
                        String msg = getString(R.string.msg_token_fmt, token);
                        GBLog.d(TAG, msg);
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }
                });
*/

        String textTitle = "Title Text";
        String textContent = "Content Text";
        String CHANNEL_ID =  "My Notification";

        if(Build.VERSION.SDK_INT >=Build.VERSION_CODES.O){

            NotificationChannel channel= new NotificationChannel(CHANNEL_ID,CHANNEL_ID,NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager =activity.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(activity, CHANNEL_ID)
                .setSmallIcon(R.drawable.point001)
                .setContentTitle(textTitle)
                .setContentText(textContent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            //NotificationManager manager = activity.getSystemService(NotificationManager.class);
            //manager.notify(1,builder.build());

            NotificationManagerCompat managerCompat=NotificationManagerCompat.from(activity);
            managerCompat.notify(1,builder.build());
        }
    }
}
