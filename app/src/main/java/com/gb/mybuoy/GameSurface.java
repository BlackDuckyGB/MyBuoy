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
import androidx.databinding.Observable;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class GameSurface extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = "GameSurface";
    private GameThread gameThread;
    private AppManager _am;
    private GestureDetector _doubleTapDetector;

    private final List<Explosion> explosionList = new ArrayList<Explosion>();

    // Easter egg: the bullets in flight. Modelled on explosionList -- objects that are born, live
    // a couple of seconds and take themselves off the list.
    private final List<Bullet> bulletList = new ArrayList<Bullet>();

    // Les tirs nes PENDANT une mise a jour attendent la fin de celle-ci pour rejoindre la liste.
    // Sans ca, une balle creee depuis l'update d'une autre -- Young-Hee riposte au moment ou une
    // tomate la touche, et c'est l'update de la tomate qui la fait tirer -- modifie la liste que
    // la boucle est en train de parcourir : ConcurrentModificationException, et le rendu s'arrete.
    private final List<Bullet> bulletsEnAttente = new ArrayList<Bullet>();

    // The points that float above the place where they were earned. Added from any update at all,
    // so the same waiting queue as the bullets: a list is not touched while it is being walked.
    // Easter egg: the rings thrown out by the poles. Same shape as the score figures -- born
    // during an update, so they wait in a second list rather than being added to one being walked.
    private final List<Shockwave> shockwaves = new ArrayList<Shockwave>();
    private final List<Shockwave> shockwavesEnAttente = new ArrayList<Shockwave>();

    // Easter egg: the hearts lying about to be picked up, and the ones leaping over the player as
    // he is mended. Same shape again -- born during an update, so they wait their turn.
    private final List<GameHeart> hearts = new ArrayList<GameHeart>();
    private final List<GameHeart> heartsEnAttente = new ArrayList<GameHeart>();

    private final List<HealSign> healSigns = new ArrayList<HealSign>();
    private final List<HealSign> healSignsEnAttente = new ArrayList<HealSign>();

    // Easter egg: the items lying about waiting to be walked into. Same shape again -- laid down
    // by a bullet in the middle of an update, so they wait their turn rather than joining a list
    // that is being walked.
    private final List<GameItem> items = new ArrayList<GameItem>();
    private final List<GameItem> itemsEnAttente = new ArrayList<GameItem>();

    private final List<ScorePopup> scorePopups = new ArrayList<ScorePopup>();
    private final List<ScorePopup> scorePopupsEnAttente = new ArrayList<ScorePopup>();
    private final List<ChibiCharacter> chibiList = new ArrayList<ChibiCharacter>();

    // Easter egg: the one that decides who appears and who the game is watching.
    private final GameOrchestrator orchestrator = new GameOrchestrator(this);


    // Full-screen announce image, drawn over everything while AppManager.getAnnounce() is not 0.
    private static final long ANNOUNCE_FADE_MS = 400;     // last 400 ms fade the image out
    private static final float ANNOUNCE_MAX_FRAC = 0.9f;  // at most 90 % of the screen width
    private static final float ANNOUNCE_WIDTH_PX = 480;   // and never wider than this

    // How far down the screen the announcement is centred: a quarter of the way rather than the
    // middle, which is where the player and whatever is happening to him are.
    private static final float ANNOUNCE_CENTER_Y = 0.25f;
    // The image of each announcement, by AppManager.ANNOUNCE_*, and the voice of the ones that do
    // not have a SoundPool field of their own. Zero where the material does not exist yet: the
    // announcement then draws nothing and says nothing, and there is one line here to fill the day
    // the files land.
    private static final int[] ANNOUNCE_IMAGE = {
            0,                              // none
            R.drawable.eg_fight,            // fight!
            R.drawable.eg_game_over,        // game over
            R.drawable.eg_hurry_up,         // hurry up!
            R.drawable.eg_too_slow,         // too slow!
            R.drawable.eg_coming,           // they're coming!
            R.drawable.eg_run,              // run!
            0,                              // level up: composed, not a file
            0,                              // level up (boss): the same banner, another tune
    };

    // The sound of each announcement. Fight and game over also have a SoundPool field of their
    // own, loaded with the other button sounds, and that is what plays them; they are named here
    // for their DURATION, which is what the banner's time on screen is built on.
    private static final int[] ANNOUNCE_VOICE = {
            0,                              // none
            R.raw.eg_fight,                 // fight!
            R.raw.eg_game_over,             // game over
            R.raw.eg_hurry_up,              // hurry up!
            R.raw.eg_too_slow,              // too slow!
            R.raw.eg_coming,                // they're coming!
            R.raw.eg_run,                   // run!
            R.raw.eg_levelup1,              // level up
            R.raw.eg_levelup_boss2,         // level up, into a level that ends on a boss
    };

    // The pieces the level banner is built from: the word, then the ten digits. One drawing per
    // digit and not one per level -- the number is written, not chosen from a shelf.
    private static final int RAW_LEVEL_WORD = R.drawable.eg_level;

    private static final int[] RAW_LEVEL_DIGITS = {
            R.drawable.eg_num0, R.drawable.eg_num1, R.drawable.eg_num2, R.drawable.eg_num3,
            R.drawable.eg_num4, R.drawable.eg_num5, R.drawable.eg_num6, R.drawable.eg_num7,
            R.drawable.eg_num8, R.drawable.eg_num9,
    };

    // Between the word and the number, and between two digits. The second is NEGATIVE on purpose:
    // the glyphs lean, so their boxes interlock without their ink touching, which is how a number
    // reads as a number instead of as digits standing in a row.
    private static final int LEVEL_WORD_GAP_PX = 30;
    private static final int LEVEL_DIGIT_GAP_PX = -16;

    // The banner built for that level, and which level it was built for. One composition per level
    // change, done in the update and never in the drawing.
    private Bitmap _egLevel = null;
    private int _egLevelFor = -1;

    // Les chiffres decodes un a un, pour le compte a rebours de l'appui du coin.
    private final android.util.SparseArray<Bitmap> _egDigits = new android.util.SparseArray<Bitmap>();

    // Decoded on first use and kept, as before: an announcement is shown often enough not to be
    // decoded twice, and rarely enough not to be held ready.
    private final android.util.SparseArray<Bitmap> _egImages = new android.util.SparseArray<Bitmap>();
    private final Rect _egSrc = new Rect();
    private final RectF _egDst = new RectF();
    private final Paint _egPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private int _egStreamId = 0;
    private int soundIdFight;
    private int soundIdGameOver;

    // Easter egg: the shot and the hit. The hit is loaded but not played yet -- nothing collides
    // so far -- and it is loaded all the same, for the reason given above: a sound decoded at the
    // moment it is first needed is silent that first time.
    private int soundIdFire;
    private int soundIdHit;
    // Easter egg: the voices a creature cries out with when it is hit and does not die. Five of
    // them, so that the map does not answer with one voice: a creature draws one when it is made
    // and keeps it, which is what makes them sound like different creatures rather than one
    // creature repeated.
    // The five voices of a creature being hit. Same recording of two words, pitched and
    // re-timbred apart: two "aie" and one more twisted, two "ouie". Kept as RESOURCES and not as
    // SoundPool ids, because that is what the duration is known by.
    private static final int[] RAW_CHIBI_HIT = {
            R.raw.eg_hurt_aie_man,
            R.raw.eg_hurt_aie_high,
            R.raw.eg_hurt_aie_evil,
            R.raw.eg_hurt_ouie_man,
            R.raw.eg_hurt_ouie_high,
    };

    private static final float CHIBI_HIT_VOLUME = 0.7f;

    // Easter egg: until when a sound is still being heard, by resource. SoundPool has no
    // end-of-play callback, so the only way to know is to know how long the file lasts and count.
    private final java.util.HashMap<Integer,Long> _sonsJusqua = new java.util.HashMap<Integer,Long>();
    private Chunk map = null;
    private MapGPSPos gps = null;

    private static final int MAX_STREAMS=100;
    private int soundIdExplosion;
    // #81 : les sons d'alarme ne sont plus figes a la construction : chacun se choisit dans les
    // parametres, et peut valoir "pas de son". On les charge donc A LA DEMANDE, par identifiant de
    // ressource, et on garde ce qui a deja ete charge -- SoundPool.load est couteux, et l'utilisateur
    // peut changer d'avis sans qu'on recharge tout.
    private final java.util.HashMap<Integer,Integer> _sonsCharges = new java.util.HashMap<Integer,Integer>();

    // Combien de temps dure chaque son, en millisecondes, mesure sur le fichier lui-meme au
    // chargement. SoundPool ne previent JAMAIS de la fin d'une lecture -- il n'a qu'un rappel de
    // fin de CHARGEMENT -- donc qui veut enchainer sur la fin d'un son doit compter. Autant
    // compter juste : la duree est lue dans le fichier, pas ecrite a la main, et reenregistrer un
    // son suffit a mettre l'attente a jour.
    private final java.util.HashMap<Integer,Long> _dureesSons = new java.util.HashMap<Integer,Long>();
    private int soundIdClick;       // #52 : clic de creation d'un segment de mesure
    private int soundIdPopupOpen;   // #52 : ouverture du popup de mark (fader bas->haut)
    private int soundIdPopupClose;  // #52 : fermeture du popup de mark (fader haut->bas)
    private int soundIdBroom;       // #75 : balai, effacement des chaines de mesure
    private int soundIdRecenter;    // #75 : recentrage manuel sur la position

    private int streamAlarmId;
    private int streamMarkAlarmId = 0;   // #81 : flux propre aux marks perso

    // #25 etape 5 : alarme de sortie de zone (surveillance). Hysteresis pour eviter le battement au bord.
    private int _zoneStreamId = 0;
    private boolean _zoneOutside = false;    // etat courant : la position est-elle hors de la zone ?
    private boolean _zoneAlarmMuted = false; // coupee au tap jusqu'a la prochaine sortie
    private static final double ZONE_HYST_M = 2.0;   // marge d'hysteresis au bord (m) - reglable

    private List<GameObject> alarmObjects = new ArrayList<GameObject>();

    // #40 : alerte visuelle peripherique (bande rouge pulsante), en complement de l'alarme sonore.
    // Design aligne sur le rond radar (MapPoint.draw_warning) : Color.RED alpha 64, onde triangulaire,
    // demi-cycle 700 ms. Couper le son au tap N'ETEINT PAS le visuel : seule la fin de la condition
    // d'alarme (rentree dans la zone + plus aucun objet radar) l'arrete.
    private boolean _visualAlertOn = false;      // condition d'alarme active (independante du mute)
    private long _alertPulseNanoTime = -1;       // origine du demi-cycle de pulsation courant
    private boolean _alertPulseDir = true;       // sens : la bande grossit / retrecit
    private float _alertBandFrac = 0.03f;        // epaisseur courante (fraction du petit cote ecran)
    private static final int ALERT_HALF_CYCLE_MS = 700;   // rythme de la bande douce pulsante
    private static final int ALERT_ALPHA = 64;            // rouge : meme translucidite que paintWarning
    private static final float ALERT_BAND_MIN = 0.03f;    // epaisseur mini
    private static final float ALERT_BAND_MAX = 0.12f;    // epaisseur maxi (pulsation 3 -> 12 %)
    // Bande supplementaire a arretes brutes (rectangle net, pas d'arrondi ni de flou) qui clignote
    // rouge <-> blanc plus vite (300 ms) pour rendre l'alerte plus agressive quand le son est coupe.
    private long _alertBlinkNanoTime = -1;       // origine du demi-cycle de clignotement couleur
    private boolean _alertBlinkWhite = false;    // false = rouge, true = blanc
    private static final int ALERT_BLINK_MS = 300;        // alternance rouge/blanc
    private static final float ALERT_HARD_FRAC = 0.03f;   // epaisseur fixe de la bande nette
    private static final float ALERT_HARD_RADIUS_FRAC = 0.05f; // rayon d'arrondi des coins interieurs
    private static final int ALERT_WHITE_ALPHA = 130;     // flash blanc plus opaque (contraste)

    // #60 : marks en alarme SORTIES de l'ecran. Le rond rouge (#7) ne se voit que si la mark est dans
    // le champ ; hors champ on la remplace par une grosse fleche collee au bord, pointe dans sa
    // direction, qui clignote rouge/blanc en phase avec la bande #40, et par sa distance a cote.
    private final Paint _arrowPaint = new Paint();
    private final Paint _arrowStrokePaint = new Paint();
    private final Paint _arrowTextPaint = new Paint();
    private final Path _arrowPath = new Path();
    // Pool de descripteurs de fleches { tipX, tipY, dirX, dirY, distKm } reutilise d'une frame a
    // l'autre : draw() tourne a 60 fps, on n'y alloue pas.
    private final ArrayList<double[]> _arrowSlots = new ArrayList<double[]>();
    private static final float ARROW_LEN_DP = 44f;      // longueur de la pointe seule
    private static final float ARROW_HALF_DP = 26f;     // demi-largeur de la pointe (52 dp de large)
    private static final float ARROW_SHAFT_DP = 70f;    // longueur de la queue, derriere la pointe
    private static final float ARROW_SHAFT_HALF_DP = 7f;// demi-epaisseur de la queue
    private static final float ARROW_INSET_DP = 6f;     // recul de la pointe par rapport au bord
    private static final float ARROW_GROUP_DP = 60f;    // en deca, deux fleches n'en font qu'une
    private static final int ARROW_GROUP_MAX = 1;       // marks detaillees sous une fleche : la plus proche
    private static final float ARROW_TEXT_DP = 24f;     // taille de la distance
    private static final float ARROW_SPEED_DP = 34f;    // taille du chiffre dans le panneau
    private static final float ARROW_SIGN_DP = 62f;     // diametre du panneau de limitation
    private static final float ARROW_SIGN_RING_DP = 7f; // epaisseur de l'anneau rouge du panneau
    private static final float ARROW_PAD_X_DP = 8f;     // marge horizontale de la pastille de distance
    private static final float ARROW_PAD_Y_DP = 3f;     // marge verticale, et ecart entre deux elements
    private static final float ARROW_TEXT_GAP_DP = 8f;  // ecart entre le bout de la queue et le texte
    // #7 : panneau de danger d'une mark perso. Il tient dans le MEME carre que le panneau de vitesse
    // d'un radar (ARROW_SIGN_DP) : le triangle et le rectangle du nom se partagent ces 62 dp au lieu
    // de s'y ajouter, sinon le placement anti-chevauchement des blocs, cale sur ces proportions,
    // serait fausse.
    private static final float ARROW_NAME_BOX_DP = 18f; // hauteur du rectangle du nom, prise sur les 62 dp
    private static final float ARROW_NAME_MAX_DP = 13f; // taille de police de depart du nom
    private static final float ARROW_NAME_MIN_DP = 8f;  // en dessous, le nom est illisible : on ne l'affiche pas
    private static final float ARROW_NAME_GAP_DP = 3f;  // respiration entre le triangle et le rectangle
    private static final float ARROW_NAME_RADIUS_DP = 4f;// arrondi des angles du rectangle
    // Lignes affichees au bout de la queue : la vitesse limite de la mark la plus proche -- ou son
    // NOM si c'est une mark perso (#7) --, puis sa distance. Les autres marks du meme groupe ne sont
    // qu'un compteur en bout de la ligne distance.
    private static final int ARROW_LINE_DIST = 0;
    private static final int ARROW_LINE_SPEED = 1;
    private static final int ARROW_LINE_NAME = 2;
    private final String[] _arrowLines = new String[ARROW_GROUP_MAX * 2];
    private final int[] _arrowLineKind = new int[ARROW_GROUP_MAX * 2];
    private final ArrayList<MapPoint> _arrowMarks = new ArrayList<MapPoint>();
    // Placement des blocs de texte : chaque bloc est traite comme un DISQUE { cx, cy, r } et chaque
    // fleche comme un SEGMENT epais { x1, y1, x2, y2, demi-largeur }. Un bloc se pose sur le cercle
    // centre au bout de sa queue, a l'angle libre le plus proche du prolongement de la fleche. Pools
    // reutilises d'une frame a l'autre, comme le reste.
    private static final float ARROW_RING_MAX = 2f;          // le cercle de placement ne double pas plus
    private float _arrowBlockRadius = 0f;                    // rayon commun des disques de blocs (frame courante)
    // #60 : mise au point du placement des blocs. A true, l'ecran montre la construction geometrique
    // (cercles, droites et reperes) et le contenu des blocs n'est pas dessine ; a false, la geometrie
    // disparait et les blocs s'affichent normalement.
    private static final boolean DebugRadarPos = false;
    private final Paint _arrowDebugPaint = new Paint();
    private long _arrowDebugLastNs = 0;
    private final RectF _arrowPill = new RectF();
    private final Paint _alertPaint = new Paint();
    private final Path _alertPath = new Path();
    private final RectF _alertInner = new RectF();

    private boolean soundPoolLoaded;
    private SoundPool soundPool;

    private boolean loadMap = false;

    // vrai tant que l'ecran d'attente couvre la carte (cf. AppManager.splashMapDrawn)
    private boolean _splashEnAttente = true;

    // #53 : taille utile (max largeur/hauteur de la vue) du dernier contextScale() des objets du chunk.
    // Zero tant qu'aucun scale n'a ete fait, ce qui couvre le constructeur : il pose le lien
    // chunk->surface AVANT la mise en page, donc avec une vue de taille nulle, et c'est update() qui
    // rattrape le vrai scale des que la surface est mesuree.
    private int _scaleBase = 0;

    // #53 : la surface est detruite/recreee a chaque passage en arriere-plan, sur la MEME instance
    // (configChanges de la #41) ; le centrage initial de la camera, lui, ne vaut que la premiere fois.
    private boolean _surfaceCreatedOnce = false;

    // #53 : nombre maximal de tentatives de join() du thread de rendu dans surfaceDestroyed. Reboucler
    // sans fin sur le thread UI provoquerait un ANR ; ne jamais reboucler laissait passer un thread
    // toujours vivant.
    private static final int MAX_JOIN_RETRY = 3;


    // Sets the zoom together with the chunk width (Chunk.areaKM/areaMarginKM):
    // Virtual-zoom is now per-chunk (Chunk.getVirtualZoom() = chunkWidthMeters/200), so the
    // scrollable area scales with the chunk while the on-screen 200 m stays constant. This
    // is only the fallback before the first chunk exists (map == null).
    private static final int DEFAULT_VIRTUAL_ZOOM = 10;

    // VZ of the current chunk (map), or the fallback if no chunk yet.
    private int currentVirtualZoom() {
        if( map != null )
            return map.getVirtualZoom();
        return DEFAULT_VIRTUAL_ZOOM;
    }
    private int _scrollX = 0;
    private int _scrollY = 0;
    private int _startScrollX = 0;
    private int _endScrollX = 0;
    private int _startScrollY = 0;
    private int _endScrollY = 0;

    // Duree (ms) pendant laquelle le recentrage auto reste suspendu apres une interaction (pan/fling/
    // 2 doigts). Seule valeur non nulle affectee a _disableScrollTime.
    private static final int SCROLL_DISABLE_MS = 5000;

    private int _disableScrollTime = 0;

    // #52 : chaines de mesure (une par viseur, index 0..2). Vertices en coords AREA (monde) : le point
    // GPS de depart puis chaque point clique. #64 : un clic (hors viseur) ajoute un vertex a la SEULE
    // chaine retenue ; reset a la sortie du mode pan. Lu par MapGPSPos pour le rendu (snapshot synchronise).
    private final java.util.List<java.util.List<double[]>> _measureChains = java.util.Arrays.asList(
            new java.util.ArrayList<double[]>(), new java.util.ArrayList<double[]>(), new java.util.ArrayList<double[]>());
    private final Object _measureLock = new Object();

    // #64 : viseur retenu pour la mesure (0 = haut-droite, 1 = milieu, 2 = bas-gauche), -1 tant qu'aucun
    // dessin n'a commence. Le PREMIER clic de mesure retient le viseur le plus proche du point clique ;
    // des cet instant les deux autres disparaissent (plus de reticule, plus de mesure, plus cliquables).
    // Seul le reset -- donc le balai -- ramene la valeur a -1 et fait reapparaitre les trois viseurs.
    private static final int MIDDLE_TARGET = 1;
    private static final int NO_TARGET = -1;
    private volatile int _measureTarget = NO_TARGET;
    private String _lastPushedCountdown = "";   // #52 : dernier palier de compte a rebours pousse a l'UI

    private long _scrollTimeMS = 0;

    private long _scrollNanoTime = -1;
    private long _lastUpdateNanoTime =-1;

    private float mLastMotionY;
    private float mLastMotionX;

    // Gestion generale des doigts : soit 1 doigt (pan), soit 2 doigts. Independant du mode edition.
    private boolean _twoFingers = false;

    // #52 : action CLIC sur les cibles de mesure. Detection via GestureDetector.onSingleTapConfirmed
    // (qui EXCLUT le double-tap #50). On ne traite le clic que si le mode pan etait DEJA actif depuis
    // un moment (le 1er tap ne fait qu'entrer en pan) : _panStartNs = debut du mode pan, _tapPanAgeMs =
    // age du mode pan au moment du DOWN de ce tap.
    private long _panStartNs = 0;
    private double _tapPanAgeMs = 0.0;
    private static final double CLICK_MIN_PAN_AGE_MS = 250.0;   // pan actif depuis > 250 ms avant de traiter un clic

    // #59 : un clic ne doit declencher qu'UNE action. Pose au DOWN (couper une alarme qui sonne, ou
    // exploser un chibi), relu ~300 ms plus tard par onSingleTapConfirmed pour ne pas enchainer sur la
    // mesure / les segments. Ecrit et lu par le seul thread UI.
    private boolean _tapConsumed = false;

    // #75 : un tap deja consomme (alarme coupee, chibi explose) ne doit pas devenir un pan. Or le
    // moindre tremblement du doigt produit des ACTION_MOVE -- 2 px suffisent -- et le pan y etait
    // arme sans aucun seuil : l'ecran se figeait et les viseurs de mesure apparaissaient. Tant que le
    // doigt n'a pas franchi le seuil ci-dessous depuis le point d'appui, ce geste n'est donc pas un
    // pan : ni recentrage differe, ni deplacement de la carte. Au-dela c'est un vrai glissement, et le
    // pan reprend son cours normal.
    // Le seuil systeme (8 dp, environ 2 mm) separe un tap d'un glissement chez quelqu'un d'immobile ;
    // il est trop fin ici, ou l'on coupe une alarme d'une main en conduisant, le doigt secoue par la
    // route. On le triple donc pour ce seul cas -- un glissement volontaire fait plusieurs centimetres
    // et depasse largement les 6 mm -- sans rien changer au pan ordinaire.
    private static final int TAP_SLOP_FACTOR = 3;
    private float _tapDownX = 0f, _tapDownY = 0f;
    private boolean _tapHoldsPan = false;
    private int _touchSlop = 0;
    // #17 : seuil du systeme, celui qui separe ordinairement un appui d'un glissement. Il s'applique
    // a TOUS les gestes ; le seuil triple ci-dessus reste reserve au tap deja consomme, ou le doigt
    // s'attarde sur l'ecran et tremble d'autant plus.
    private int _dragSlop = 0;

    // #17 : appui long sur une mark perso de la CARTE -> editeur, comme dans la liste de droite.
    // Tolerance autour de l'icone : viser une image de 100 px au doigt n'est pas viser un pixel, et
    // la mark est ancree par le BAS (la pointe), donc le doigt tombe souvent juste en dessous.
    private static final float MARK_TOUCH_SLOP_DP = 8f;

    // #9 : defilement inertiel (fling) au pan 1 doigt. Meme principe que l'inertie de zone :
    // on suit la vitesse du geste et on la prolonge amortie (demi-vie ~250 ms) au relachement.
    private boolean _panFling = false;
    private double _panVx, _panVy;   // vitesse du scroll (px/ms)
    private long _panVNs = 0;

    // #71 : geste transfere par GBRootLayout parce qu'il a demarre sur une vue cliquable posee
    // au-dessus de la carte. Il commence donc par un MOVE, sans le DOWN qui va d'habitude devant :
    // le GestureDetector, lui, exige cette sequence complete et ne doit pas le voir passer.
    private boolean _externalPan = false;

    // #25 etape 4 : geste 2 doigts en mode EDITION. Un seul mouvement isole a la fois (verrou au debut).
    // Modes : 0=indecis, 1=rotation, 2=resize longueur, 3=resize largeur, 4=deplacement.
    private boolean _zoneGesture = false;
    private int _zgMode = 0;
    private double _zgTheta0;   // orientation de la zone au debut
    private double _zgA0;       // angle de la ligne entre les doigts au debut
    private double _zgSu0;      // separation projetee sur l'axe longueur (debut)
    private double _zgSw0;      // separation projetee sur l'axe largeur (debut)
    private double _zgLen0;     // halfLenM au debut
    private double _zgWid0;     // halfWidthM au debut
    private double _zgF0x0, _zgF0y0, _zgF1x0, _zgF1y0;   // positions de depart des 2 doigts (ecran)
    private double _zgMidAX0, _zgMidAY0;   // milieu des doigts au debut (repere "area")
    private double _zgCAX0, _zgCAY0;       // centre de la zone au debut (repere "area")
    private double _zgPxPerM;              // echelle px/metre au centre de la zone
    private double _zgPivotAX, _zgPivotAY; // pivot de rotation (area) : milieu des doigts OU doigt fixe
    private boolean _zgSym;                // resize symetrique (2 cotes, centre fixe) ou asymetrique (1 cote)
    private int _zgMovingFinger;           // en asymetrique : index du doigt qui bouge (0/1)
    private double _zgSideSign;            // en asymetrique : cote qui bouge le long de l'axe (+1/-1)

    // Inertie au relachement : on prolonge le geste avec la vitesse de fin, amortie (demi-vie ~250 ms).
    // La vitesse initiale porte "l'intensite selon la vitesse du geste".
    private long _zgVNs = 0;               // horodatage du suivi de vitesse
    private double _zgPrevAngle, _zgPrevHalf, _zgPrevCx, _zgPrevCy;
    private double _zgVAngle, _zgVHalf, _zgVCx, _zgVCy;   // vitesses lissees (unite/ms)
    private boolean _ziActive = false;     // inertie en cours
    private int _ziMode = 0;
    private double _ziVAngle, _ziVHalf, _ziVCx, _ziVCy;
    private boolean _ziSym;
    private double _ziSideSign;
    private double _ziPxPerM;
    private double _ziPivotAX, _ziPivotAY;   // pivot de rotation conserve pour l'inertie

    private static final double ZONE_MIN_HALF_M = 10.0;   // demi-taille mini -> rectangle mini 20 m x 20 m

    private static double clampHalf(double v, double min) { return Math.max(min, Math.min(200000.0, v)); }

    public GameSurface(Context context)  {
        super(context);

        // Leak-hunt: persist the heap curve to a file that survives a field
        // crash, and capture the fatal exception into the same file.
        // #86: the memory log follows the storage preference like everything else the app writes;
        // preferredDir falls back on the other volume when the preferred one is missing.
        MemLog.init(AppManager.getInstance().preferredDir(context.getExternalFilesDirs(null), 0));
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            MemLog.logCrash(ex);
            if (prev != null) prev.uncaughtException(thread, ex);
        });

        // Make Game Surface focusable so it can handle events. .
        this.setFocusable(true);

        // Sét callback.
        this.getHolder().addCallback(this);

        this.initSoundPool();

        _dragSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
        _touchSlop = TAP_SLOP_FACTOR * _dragSlop;

        _am = AppManager.getInstance();
        _doubleTapDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDoubleTap(MotionEvent e) {
                // Easter egg: not while a game is on. Two taps in a row on a creature are two
                // blows, and they have no business reloading the ground under it.
                if( _am.getIsGameMode() )
                    return false;

                _am.requestChunkReload(); // double-tap carte = forcer un reload de chunk (_chunkReloadRequested)
                return true;
            }
            @Override public void onLongPress(MotionEvent e) {
                // #17 : l'appui long ouvre l'editeur de la mark perso visee. Il ne prend pas la main
                // sur un clic qui a deja servi (alarme coupee, chibi explose).
                if( _tapConsumed || _externalPan )
                    return;

                PersonalMark mark = findMarkAt(e.getX(), e.getY());

                if( mark == null )
                    return;

                _tapConsumed = true;   // ni mesure, ni segment derriere cet appui, et pas de mode pan
                _tapHoldsPan = true;   // le doigt qui bouge encore ne traine pas la carte

                mark.edit();
            }

            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                // #52 : clic simple CONFIRME (pas un double-tap). On ne traite que si on etait deja en
                // mode pan depuis > 250 ms (le 1er tap ne fait qu'entrer en pan) et si les cibles sont la.
                // #59 : ... et seulement si le clic n'a pas deja servi (alarme coupee / chibi explose).
                if( !_tapConsumed && _tapPanAgeMs > CLICK_MIN_PAN_AGE_MS && getMap() != null )
                    handleTargetClick(e.getX(), e.getY());
                return false;
            }
        });
        _am.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(Observable sender, int propertyId) {

                if( propertyId == BR.currentChunk ) {
                    GBLog.d(getClass().getName(), "currentChunk changed");
                    loadMap = true;
                }
                else if( propertyId == BR.latitude ) {
                    //GBLog.d(getClass().getName().replace(getContext().getPackageName(), ""), "latitude changed");
                }
                else if( propertyId == BR.longitude ) {
                    //GBLog.d(getClass().getSimpleName(), "longitude changed");
                }
                else {
                    //GBLog.d(getClass().getName(), String.format( "%d changed", propertyId));
                }
            }
        });

        // Easter egg: one sheet per kind, decoded once and held statically, for the bullets as
        // for the creatures. BEFORE anything is created from them: a creature built on a sheet
        // that has not been decoded yet gets no image at all, and walks the map invisible.
        Bullet.loadSheet(this.getResources());
        ChibiCreeper.loadSheet(this.getResources());
        ChibiChucky.loadSheet(this.getResources());
        ChibiGhost.loadSheet(this.getResources());
        ChibiYoungHee.loadSheet(this.getResources());
        Explosion.loadSheet(this.getResources());
        GameHeart.loadSheet(this.getResources());
        GameItem.loadSheet(this.getResources());
        ItemBullet.loadSheet(this.getResources());
        HealSign.loadSheet(this.getResources());

        this.map = _am.getCurrentChunk();
        if( this.map != null )
            this.map.setGameSurface(this);

        gps =  new MapGPSPos(this);

        // No creature is made here any more. The orchestrator finds the map empty on the first
        // frame and populates it, which is one rule in one place -- and it runs after the sheets
        // are loaded and the player exists, instead of depending on the order of these lines.
    }

    public Chunk getMap() {
        return map;
    }

    /** The player, for whoever has to place itself relative to him. */
    public MapGPSPos getGps() {
        return gps;
    }

    /**
     * Area pixels per metre at the current scale.
     *
     * Measured rather than derived: a hundred metres of LATITUDE are projected through the chunk
     * and the result read in pixels. Latitude because it is not shortened by the cosine the way
     * longitude is, so the figure is the same wherever one stands.
     *
     * Not constant: the chunk widens with speed, from 1.2 km across to 3, and the zoom moves it
     * too. Anything expressed in metres must therefore convert at the moment it is used.
     */
    public double pixelsPerMeter() {
        Chunk ch = this.map;

        if( ch == null )
            return 1.0;

        double lat = _am.getLatitude();
        double y1 = ch.getY(lat, getAreaHeight());
        double y2 = ch.getY(lat + 100.0 / 111320.0, getAreaHeight());
        double dpx = Math.abs(y2 - y1);

        return (dpx > 1e-6) ? (dpx / 100.0) : 1.0;
    }

    // Easter egg: the surface owns the SoundPool, so it is the one that can play. It decides
    // nothing, though: it hands back the stream number, and the bullet that asked keeps it and
    // stops its own when it wants to. One bullet's shot must never be cut by another's impact.

    public int playSoundFire() {
        if( !this.soundPoolLoaded || (this.soundIdFire == 0) )
            return 0;

        return this.soundPool.play(this.soundIdFire, 0.5f, 0.5f, 1, 0, 1f);
    }

    /** How many voices there are to draw from. The creature draws, the surface only plays. */
    public int getChibiHitVoiceCount() {
        return RAW_CHIBI_HIT.length;
    }

    /**
     * A creature hit but still standing, in the voice it was given when it was made.
     *
     * Out of range says nothing rather than throwing: a sound is not worth a crash in a render
     * loop.
     */
    public int playSoundChibiHit(int voice) {

        if( (voice < 0) || (voice >= RAW_CHIBI_HIT.length) )
            return 0;

        // Once at a time, like every other voice: two creatures hit on the same frame give one
        // cry and not a chord.
        return jouerUneFois(RAW_CHIBI_HIT[voice], CHIBI_HIT_VOLUME);
    }

    public int playSoundHit() {
        if( !this.soundPoolLoaded || (this.soundIdHit == 0) )
            return 0;

        return this.soundPool.play(this.soundIdHit, 0.6f, 0.6f, 1, 0, 1f);
    }


    /**
     * Easter egg: what a bullet may hit -- the creatures, and the scenery it flies through.
     *
     * Not the player: a bullet leaves from his own position and would die on the spot.
     *
     * A snapshot, since the render thread walks it while a tap may be adding a chibi to the list.
     */
    /**
     * Easter egg: what a creature may not walk through -- the other creatures, the scenery, and
     * the player.
     *
     * The player is in this one and not in getTargets(): a bullet leaves from his own position
     * and would die on the spot, whereas a creature has to bump into him.
     *
     * A snapshot, since the render thread walks it while a tap may be adding a chibi to the list.
     */
    public List<GameObject> getObstacles() {
        List<GameObject> obstacles = new ArrayList<GameObject>(this.chibiList);
        obstacles.addAll(_am.getMarksEnumerable());

        if( this.gps != null )
            obstacles.add(this.gps);

        return obstacles;
    }

    public List<GameObject> getTargets() {
        List<GameObject> targets = new ArrayList<GameObject>(this.chibiList);
        targets.addAll(_am.getMarksEnumerable());
        targets.addAll(this.bulletList);
        return targets;
    }

    /**
     * Easter egg: the shots in flight.
     *
     * A bullet is a target like any other: two of them meeting cancel each other out, so a tomato
     * can knock down what Young-Hee fires. A snapshot, since the render thread walks it while a
     * shot may be joining the list.
     */
    public List<GameObject> getBullets() {
        return new ArrayList<GameObject>(this.bulletList);
    }

    // Easter egg: a creature puts itself on the map and takes itself off it. The surface holds the
    // list, it does not decide who is on it.

    public void addChibi(ChibiCharacter chibi) {
        if( chibi != null )
            this.chibiList.add(chibi);
    }

    public void removeChibi(ChibiCharacter chibi) {
        this.chibiList.remove(chibi);
    }

    /** How many creatures are on the map. The orchestrator's question, once a frame. */
    public int chibiCount() {
        return this.chibiList.size();
    }

    /** How many creatures of exactly that kind are on the map. */
    public int chibiCount(Class<?> kind) {

        int count = 0;

        for( ChibiCharacter chibi : this.chibiList ) {
            if( chibi.getClass() == kind )
                count++;
        }

        return count;
    }

    /**
     * Toutes les creatures, en instantane.
     *
     * Une copie, parce que l'appelant va probablement en faire exploser : chacune se retire alors
     * de la vraie liste, qu'on ne peut donc pas parcourir pendant ce temps.
     */
    public List<ChibiCharacter> getChibis() {
        return chibisSnapshot();
    }

    private List<ChibiCharacter> chibisSnapshot() {
        return new ArrayList<ChibiCharacter>(this.chibiList);
    }

    /** Any creature at all, for the camera when the one it was watching is gone. */
    public ChibiCharacter firstChibi() {
        return this.chibiList.isEmpty() ? null : this.chibiList.get(0);
    }

    /** Is that creature still on the map, or has it been blown up since? */
    public boolean hasChibi(ChibiCharacter chibi) {
        return this.chibiList.contains(chibi);
    }

    /** Easter egg: the one that decides who appears and who the game is watching. */
    public GameOrchestrator getOrchestrator() {
        return orchestrator;
    }

    public void addExplosion(Explosion explosion) {
        if( explosion != null )
            this.explosionList.add(explosion);
    }

    /** Easter egg: a gain of points to be shown on the map. */
    /** Easter egg: a mark has dropped a heart, and here it is. */
    public void addHeart(GameHeart heart) {
        if( heart != null )
            this.heartsEnAttente.add(heart);
    }

    /**
     * Easter egg: the hearts on the ground, in a snapshot.
     *
     * A copy, because the caller is about to pick some of them up, and a list cannot be walked
     * while what it holds is being taken out of it.
     */
    public List<GameHeart> getHearts() {
        return new ArrayList<GameHeart>(this.hearts);
    }

    /**
     * The same, as plain game objects: what the player's collider tests itself against.
     *
     * A copy, like the obstacles and the targets, since the collider walks it while a heart may
     * be taken off the list by the very pass that is walking it.
     */
    public List<GameObject> getHeartObjects() {
        return new ArrayList<GameObject>(this.hearts);
    }

    /** Taken: it leaves the map at once. */
    public void removeHeart(GameHeart heart) {
        this.hearts.remove(heart);
        this.heartsEnAttente.remove(heart);
    }

    /** The game is over: nothing it had dropped stays lying about. */
    public void clearHearts() {
        this.hearts.clear();
        this.heartsEnAttente.clear();
    }

    /** Easter egg: a thrown item has landed, and here it is. */
    public void addItem(GameItem item) {
        if( item != null )
            this.itemsEnAttente.add(item);
    }

    /** Spent, or rotted away: it leaves the map at once. */
    public void removeItem(GameItem item) {
        this.items.remove(item);
        this.itemsEnAttente.remove(item);
    }

    /** The game is over: nothing anybody laid stays lying about. */
    public void clearItems() {
        this.items.clear();
        this.itemsEnAttente.clear();
    }

    /** Easter egg: a portion of heart has just come back, and it says so over the player. */
    public void addHealSign(HealSign sign) {
        if( sign != null )
            this.healSignsEnAttente.add(sign);
    }

    /** Easter egg: a pole has been taken, and this is the ring it throws out. */
    public void addShockwave(Shockwave wave) {
        if( wave != null )
            this.shockwavesEnAttente.add(wave);
    }

    public void addScorePopup(ScorePopup popup) {
        if( popup != null )
            this.scorePopupsEnAttente.add(popup);
    }

    /** Easter egg: hands a freshly fired bullet to the surface, which flies it and then forgets it. */
    public void addBullet(Bullet bullet) {
        if( bullet != null )
            this.bulletsEnAttente.add(bullet);
    }

    public final int getAreaHeight() {

        int sizeSurface = this.getWidth();
        if( this.getHeight() > sizeSurface )
            sizeSurface = getHeight();
        sizeSurface *= currentVirtualZoom();

        return sizeSurface;
    }

    public final int getAreaWidth() {

        int sizeSurface = this.getWidth();
        if( this.getHeight() > sizeSurface )
            sizeSurface = getHeight();
        sizeSurface *= currentVirtualZoom();

        return sizeSurface;
    }

    public final int getCanvasX(int x) {
        return x -_scrollX;
    }
    public final int getCanvasX(double x) {
        return (int)x -_scrollX;
    }

    public final int getCanvasY(int y) {
        return y -_scrollY;
    }
    public final int getCanvasY(double y) {
        return (int)y -_scrollY;
    }

    public final int getAreaX(int x) {
        return x + _scrollX;
    }
    public final int getAreaX(float x) {
        return (int)x + _scrollX;
    }

    public final int getAreaY(int y) {
        return y + _scrollY;
    }

    public final int getAreaY(float y) {
        return (int)y + _scrollY;
    }


    public final boolean isVisible( GameObject go ) {
        boolean bRet = true;

        //Todo: Modify the code for go.anchor param

        if( go.getX()+go.getWidth() < _scrollX )
            bRet = false;
        else if( go.getX() > _scrollX + getWidth() )
            bRet = false;
        else if( go.getY()+go.getHeight() < _scrollY )
            bRet = false;
        else if( go.getY() > _scrollY + getHeight() )
            bRet = false;

        return bRet;
    }

    // #45 / #26 : vrai tant que le pan a ete deplace au doigt (tempo de suspension
    // du recentrage auto encore active). Sert a n'afficher l'indicateur hors-champ
    // et le bouton de recentrage que dans ce cas.
    public final boolean isPanActive() {
        return _disableScrollTime > 0;
    }

    // #26 : etat precedent du pan, pour ne notifier l'UI qu'aux transitions.
    private boolean _lastPanActive = false;

    // #26 : recentrage manuel sur la position. On coupe d'abord le fling en cours
    // (sinon il re-arme _disableScrollTime a chaque frame et le recentrage n'a aucun
    // effet), puis on leve la tempo de suspension -> la camera revient sur la
    // position des la frame suivante.
    public void recenterOnPosition() {
        _panFling = false;
        _panVx = _panVy = 0.0;
        _disableScrollTime = 0;
    }

    public void setCamera( double x, double y, int moveTimeMs ) {

        boolean blockedMap = (_disableScrollTime > 0);

        double newX = x;
        double newY = y;

        if( newX < 0 )
            newX = 0;
        else if( newX > getAreaWidth() - getWidth() )
            newX = getAreaWidth() - getWidth() - 1;


        if( newY < 0 )
            newY = 0;
        else if( newY > getAreaHeight() - getHeight() )
            newY = getAreaHeight() - getHeight() - 1;

        if( (moveTimeMs <= 0) && !blockedMap ) {
            _scrollX = (int)newX;
            _scrollY = (int)newY;
        }
        _startScrollX = _scrollX;
        _endScrollX = (int)newX;
        _startScrollY = _scrollY;
        _endScrollY = (int)newY;

        _scrollTimeMS = moveTimeMs;
        _scrollNanoTime = System.nanoTime();
    }

    private int lerp( int start, int end, float fraction) {
        int lerpVal;

        lerpVal = Math.round(start + (end - start) * fraction);

        return lerpVal;
    }


    private void initSoundPool()  {
        // With Android API >= 21.
        if (Build.VERSION.SDK_INT >= 21 ) {

            AudioAttributes audioAttrib = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            SoundPool.Builder builder= new SoundPool.Builder();
            builder.setAudioAttributes(audioAttrib).setMaxStreams(MAX_STREAMS);

            this.soundPool = builder.build();
        }
        // With Android API < 21
        else {
            // SoundPool(int maxStreams, int streamType, int srcQuality)
            this.soundPool = new SoundPool(MAX_STREAMS, AudioManager.STREAM_MUSIC, 0);
        }

        // When SoundPool load complete.
        this.soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                soundPoolLoaded = true;
            }
        });

        // Load the sound explosion.wav into SoundPool
        this.soundIdExplosion = this.soundPool.load(this.getContext(), R.raw.explosion,1);


        // #52 : clic de mesure + ouverture/fermeture du popup de mark
        this.soundIdClick = this.soundPool.load(this.getContext(), R.raw.click_measure, 1);
        this.soundIdPopupOpen = this.soundPool.load(this.getContext(), R.raw.popup_open, 1);
        this.soundIdPopupClose = this.soundPool.load(this.getContext(), R.raw.popup_close, 1);
        this.soundIdBroom = this.soundPool.load(this.getContext(), R.raw.broom, 1);
        this.soundIdRecenter = this.soundPool.load(this.getContext(), R.raw.recenter, 1);
        this.soundIdFight = this.soundPool.load(this.getContext(), R.raw.eg_fight, 1);
        this.soundIdGameOver = this.soundPool.load(this.getContext(), R.raw.eg_game_over, 1);
        this.soundIdFire = this.soundPool.load(this.getContext(), R.raw.eg_fire, 1);
        this.soundIdHit = this.soundPool.load(this.getContext(), R.raw.eg_hit, 1);

        // The voices of the creatures, loaded HERE and not on first use. SoundPool.load is
        // asynchronous: a sound asked for and played in the same breath is played before it is
        // ready, which comes out as silence. Only the first time, which is exactly the time it is
        // noticed. They go through the same cache as the rest, so nothing loads twice.
        for( int res : RAW_CHIBI_HIT )
            precharger(res);

        precharger(R.raw.eg_gun);
        precharger(R.raw.eg_hou);
        precharger(R.raw.eg_pouet);
        precharger(R.raw.eg_pole);
        precharger(R.raw.eg_pole_blast);
        precharger(R.raw.eg_heart_pop);
        precharger(R.raw.eg_heal);

        precharger(R.raw.eg_item_set_mine);
        precharger(R.raw.eg_item_set_trap);
        precharger(R.raw.eg_item_set_wasps);
        precharger(R.raw.eg_item_set_banana);
        precharger(R.raw.eg_item_blast_mine);
        precharger(R.raw.eg_item_snap_trap);
        precharger(R.raw.eg_item_swarm_wasps);
        precharger(R.raw.eg_item_slip_banana);

        for( int res : ANNOUNCE_VOICE )
            precharger(res);

        mesurerAnnonces();
        precharger(R.raw.eg_brain);
        precharger(R.raw.eg_yum);
        precharger(R.raw.eg_houuu);

        precharger(R.raw.eg_younghee_1_2_3_green_light);
        precharger(R.raw.eg_younghee_green_light);
        precharger(R.raw.eg_younghee_red_light);
        precharger(R.raw.eg_younghee_you_moved);
        precharger(R.raw.eg_younghee_aie);
        precharger(R.raw.eg_younghee_ouie);
        precharger(R.raw.eg_younghee_you_are_dead);
        precharger(R.raw.eg_younghee_you_won);

    }

    public void playSoundExplosion()  {
        if(this.soundPoolLoaded) {
            float leftVolumn = 0.8f;
            float rightVolumn =  0.8f;
            // Play sound explosion.wav
            int streamId = this.soundPool.play(this.soundIdExplosion,leftVolumn, rightVolumn, 1, 0, 1f);
        }
    }

    /**
     * Builds the "LEVEL n" banner, and keeps it until the number changes.
     *
     * Called from the game's update and NEVER from the drawing: it decodes eleven drawables and
     * paints them into a new bitmap, which has no business happening while the canvas is being
     * painted. Once per level change, which is to say once every few minutes.
     */
    public void prepareLevelBanner(int level) {

        if( (_egLevel != null) && (_egLevelFor == level) )
            return;

        String digits = Integer.toString(Math.max(0, level));

        Bitmap word = BitmapFactory.decodeResource(getResources(), RAW_LEVEL_WORD);

        if( word == null )
            return;

        Bitmap[] glyphs = new Bitmap[digits.length()];

        int width = word.getWidth() + LEVEL_WORD_GAP_PX;
        int height = word.getHeight();

        for( int i = 0; i < digits.length(); i++ ) {

            glyphs[i] = BitmapFactory.decodeResource(getResources(),
                                                     RAW_LEVEL_DIGITS[digits.charAt(i) - '0']);

            if( glyphs[i] == null )
                return;

            width += glyphs[i].getWidth() + ((i > 0) ? LEVEL_DIGIT_GAP_PX : 0);
            height = Math.max(height, glyphs[i].getHeight());
        }

        Bitmap banner = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(banner);

        // Everything sits on the same line, so they are laid on the BOTTOM edge: the pieces were
        // trimmed, and a trimmed glyph's bottom is its baseline.
        canvas.drawBitmap(word, 0, height - word.getHeight(), null);

        int x = word.getWidth() + LEVEL_WORD_GAP_PX;

        for( int i = 0; i < glyphs.length; i++ ) {

            canvas.drawBitmap(glyphs[i], x, height - glyphs[i].getHeight(), null);

            x += glyphs[i].getWidth() + LEVEL_DIGIT_GAP_PX;
        }

        _egLevel = banner;
        _egLevelFor = level;
    }

    /**
     * Tells AppManager how long each announcement really lasts.
     *
     * The length of a banner on screen is the length of what it says, and that is written in the
     * file and nowhere else: re-record a voice and the banner follows, with nothing to edit. Free
     * to call here, since precharger() has just measured every one of those files.
     */
    private void mesurerAnnonces() {

        long[] ms = new long[ANNOUNCE_VOICE.length];

        for( int what = 1; what < ms.length; what++ ) {

            int res = ANNOUNCE_VOICE[what];

            ms[what] = (res == 0) ? 0 : soundDurationMs(res) + ANNOUNCE_FADE_MS;
        }

        // getInstance() and not _am: this runs from initSoundPool(), which the constructor calls
        // before it has taken its own reference. The singleton itself is long since up.
        AppManager.getInstance().setAnnounceDurationsMs(ms);
    }

    // #81 : identifiant SoundPool d'une ressource, chargee au premier besoin puis gardee. Rend 0
    // pour "pas de son", ce qui laisse simplement l'alerte visuelle faire son travail.
    // Charge un son et le garde, SANS attendre que le pool se declare pret : au moment de
    // l'initialisation il ne l'est jamais -- le drapeau ne se leve qu'au premier chargement
    // termine -- et sonDe() rendrait 0 sans rien charger du tout.
    private void precharger(int resId) {

        if( (resId == 0) || _sonsCharges.containsKey(resId) )
            return;

        _sonsCharges.put(resId, Integer.valueOf(this.soundPool.load(this.getContext(), resId, 2)));
        _dureesSons.put(resId, Long.valueOf(mesurerDuree(resId)));
    }

    /**
     * Combien de temps dure ce son, en millisecondes, ou 0 si la mesure a echoue.
     *
     * La seule facon de savoir quand un son joue par SoundPool se termine : il n'y a pas de rappel
     * de fin de lecture, il faut donc connaitre sa duree et compter.
     */
    public long soundDurationMs(int resId) {
        return soundDuration(resId);
    }

    private long soundDuration(int resId) {

        Long duree = _dureesSons.get(resId);

        if( duree == null ) {
            duree = Long.valueOf(mesurerDuree(resId));
            _dureesSons.put(resId, duree);
        }

        return duree.longValue();
    }

    // Lit la duree dans le fichier. Appele une fois par ressource, au chargement.
    private long mesurerDuree(int resId) {

        android.media.MediaMetadataRetriever lecteur = new android.media.MediaMetadataRetriever();

        try {
            android.content.res.AssetFileDescriptor fd = getResources().openRawResourceFd(resId);

            if( fd == null )
                return 0;

            try {
                lecteur.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());

                String ms = lecteur.extractMetadata(
                        android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);

                return (ms == null) ? 0 : Long.parseLong(ms);
            } finally {
                fd.close();
            }
        } catch( Exception e ) {
            return 0;
        } finally {
            try { lecteur.release(); } catch( Exception ignored ) { }
        }
    }

    private int sonDe(int resId) {

        if( (resId == 0) || !this.soundPoolLoaded )
            return 0;

        Integer id = _sonsCharges.get(resId);

        if( id == null ) {
            id = Integer.valueOf(this.soundPool.load(this.getContext(), resId, 2));
            _sonsCharges.put(resId, id);
        }

        return id.intValue();
    }

    // #81 : le son d'une des trois alarmes, tel que choisi dans les parametres. Il vient soit d'une
    // ressource livree avec l'application, soit d'un fichier que l'utilisateur a ajoute.
    private int sonAlarme(int quoi) {

        String choix = _am.getAlarmSound(quoi);

        if( AlarmSoundCatalog.isUser(choix) ) {

            java.io.File f = AlarmSoundCatalog.userFile(getContext(), choix);

            if( (f == null) || !f.exists() )
                return 0;   // le fichier a disparu : silence, plutot qu'une alarme muette qui inquiete

            return sonDeFichier(f.getAbsolutePath());
        }

        return sonDe(AlarmSoundCatalog.resId(choix));
    }

    // Meme cache que sonDe, indexe par le chemin. La cle negative evite de se croiser avec les
    // identifiants de ressources.
    private final java.util.HashMap<String,Integer> _sonsFichiers = new java.util.HashMap<String,Integer>();

    private int sonDeFichier(String chemin) {

        if( !this.soundPoolLoaded )
            return 0;

        Integer id = _sonsFichiers.get(chemin);

        if( id == null ) {
            id = Integer.valueOf(this.soundPool.load(chemin, 2));
            _sonsFichiers.put(chemin, id);
        }

        return id.intValue();
    }

    /** Alarme des radars (#7). Rend 0 si l'utilisateur a choisi "pas de son". */
    public int playSoundAlarm()  {
        int son = sonAlarme(0);
        return (son == 0) ? 0 : this.soundPool.play(son, 1f, 1f, 2, -1, 1f);
    }

    /** #81 : alarme des marks personnelles, son propre et flux propre. */
    public int playSoundMarkAlarm()  {
        int son = sonAlarme(1);
        return (son == 0) ? 0 : this.soundPool.play(son, 1f, 1f, 2, -1, 1f);
    }

    public int playSoundZoneAlarm()  {
        int son = sonAlarme(2);
        return (son == 0) ? 0 : this.soundPool.play(son, 1f, 1f, 2, -1, 1f);
    }


    // #85: the COSMETIC sounds -- measuring click, mark popup, broom, recentre -- go quiet in
    // low-resource mode. The explosion and the alarms are not concerned: they inform, they do not
    // decorate.
    private boolean cosmeticSoundsOff() {
        return (_am != null) && _am.getLowResourceMode();
    }

    // #52 : son court joue a chaque clic qui cree un segment de mesure.
    public void playSoundClick()  {
        if(cosmeticSoundsOff())
            return;

        if(this.soundPoolLoaded)
            this.soundPool.play(this.soundIdClick, 0.9f, 0.9f, 1, 0, 1f);
    }

    // #52 : sons d'ouverture / fermeture du popup de mark.
    public void playSoundPopupOpen()  {
        if(cosmeticSoundsOff())
            return;

        if(this.soundPoolLoaded)
            this.soundPool.play(this.soundIdPopupOpen, 0.9f, 0.9f, 1, 0, 1f);
    }

    public void playSoundPopupClose()  {
        if(cosmeticSoundsOff())
            return;

        if(this.soundPoolLoaded)
            this.soundPool.play(this.soundIdPopupClose, 0.9f, 0.9f, 1, 0, 1f);
    }

    // #75 : sons des deux actions du bouton, joues au CLIC seulement. Le recentrage automatique,
    // lui, reste silencieux : il passe par l'expiration de _disableScrollTime dans update(), jamais
    // par recenterOnPosition().
    // Volume tres bas (0.08 contre 0.9 pour les autres sons, soit environ 20 dB en dessous) : ce sont
    // deux gestes frequents, la confirmation ne doit s'entendre que telephone en main.
    private static final float BTN_SOUND_VOLUME = 0.08f;

    public void playSoundBroom()  {
        if(cosmeticSoundsOff())
            return;

        if(this.soundPoolLoaded)
            this.soundPool.play(this.soundIdBroom, BTN_SOUND_VOLUME, BTN_SOUND_VOLUME, 1, 0, 1f);
    }

    public void playSoundRecenter()  {
        if(cosmeticSoundsOff())
            return;

        if(this.soundPoolLoaded)
            this.soundPool.play(this.soundIdRecenter, BTN_SOUND_VOLUME, BTN_SOUND_VOLUME, 1, 0, 1f);
    }

    /**
     * Easter egg: plays a sound given its raw resource, and hands back the stream it runs on.
     *
     * The stream is what makes it stoppable -- a creature that is hit cuts its own voice short to
     * make way for the cry. The resource is loaded the first time it is asked for and kept, since
     * loading is the costly part. Zero means nothing was played, for want of a resource or because
     * the pool is not ready yet.
     */
    public int playSound(int resId, float volume) {
        return jouer(resId, volume);
    }

    /**
     * Easter egg: plays a sound UNLESS the same one is still being heard.
     *
     * For the voices that must not pile up on themselves -- a creature's cry, the squeak it
     * answers a powerless finger with. Tapping like mad then gives one squeak and not a rattle,
     * and the refusal is silent: zero, as for anything else that did not play.
     */
    public int playSoundOnce(int resId, float volume) {
        return jouerUneFois(resId, volume);
    }

    /** The same, read faster or slower: the rate is clamped to what a SoundPool accepts. */
    public int playSoundOnce(int resId, float volume, float rate) {
        return jouerUneFois(resId, volume, rate);
    }

    /**
     * Plays it whatever else is playing, at that rate.
     *
     * For the handful that are MEANT to pile up: three hearts dropped together are three pops a
     * semitone apart, and the second and third are the point of it.
     */
    public int playSound(int resId, float volume, float rate) {
        return jouer(resId, volume, rate);
    }

    // Easter egg: until when the game is in the middle of an action that SPEAKS. Not until when a
    // sound is playing -- the thing being reserved is the action, and the sound is only a part of
    // it: a hurry up is a banner, a voice, a moment to read it and a fade, and nothing else may
    // speak over any of that.
    //
    // No flag to give back and no timer: whoever takes it writes down when it will be free, and
    // the next caller finds out by comparing. The same idiom as playSoundOnce and as the five
    // seconds between two calls of the same creature.
    //
    // Taken and read from the game thread only, which is why there is no lock here. It must stay
    // that way.
    private long _actionUntilMs = 0;

    /**
     * May I start an action that is going to speak, for about this long?
     *
     * THE RULE, and it is worth stating exactly, because it is easy to reserve too much or too
     * little. The floor is held from the START OF THE ACTION to the END OF WHAT IT SAYS, plus a
     * little silence so the next voice does not land on this one's tail. Nothing more.
     *
     * An action of two seconds of something, then three seconds of speech, then five seconds of
     * something, lasts ten seconds and reserves 2 + 3 + {@link #ACTION_SILENCE_MS}. The two
     * seconds before are in, because during them nobody else may START speaking -- their sentence
     * would still be running when ours begins. The five seconds after are out: they stop nobody.
     *
     * True takes the floor. False means somebody else is speaking, and it is the caller who
     * decides what that means for it: a creature crying out because it has seen the player drops
     * the whole idea, since a cry two seconds late lies about when it saw him, while a notch of
     * hurry up or a phase of the boss asks again on the next frame and happens a second late,
     * which nobody can tell.
     */
    public boolean mayStartAnnouncedAction(long dureeMs) {

        long now = System.nanoTime() / 1000000;

        if( now < _actionUntilMs )
            return false;

        _actionUntilMs = now + dureeMs;

        return true;
    }

    /**
     * Takes the floor without asking, for an action that cannot be put off.
     *
     * One caller, and it should stay that way: the boss being touched. The win ends her there and
     * then, so it cannot wait for the next frame -- and nothing should start speaking over it.
     */
    public void takeAnnouncedAction(long dureeMs) {
        _actionUntilMs = (System.nanoTime() / 1000000) + dureeMs;
    }

    /** Silence between the end of one spoken action and the start of the next. */
    public static final long ACTION_SILENCE_MS = 400;

    /**
     * How long the floor must be held for an announcement: what it SAYS, plus that silence.
     *
     * Not its time on screen, which is longer: a banner goes on fading for ANNOUNCE_FADE_MS after
     * the voice has stopped, and a picture fading out is not speech. Reserving the whole window
     * held the floor 400 ms too long.
     */
    public long announceFloorMs(int what) {

        int res = ((what > 0) && (what < ANNOUNCE_VOICE.length)) ? ANNOUNCE_VOICE[what] : 0;

        return soundDuration(res) + ACTION_SILENCE_MS;
    }

    private int jouer(int resId, float volume) {
        return jouer(resId, volume, 1f);
    }

    // The rate is the speed it is read at, between a half and twice: a sound stretched to the
    // length of what it is illustrating. It drops the pitch as it slows down, which is right for
    // a blast -- a wider ring sounds deeper, and it does so for nothing.
    private int jouer(int resId, float volume, float rate) {

        int soundId = sonDe(resId);

        if( soundId == 0 )
            return 0;

        if( rate < 0.5f )
            rate = 0.5f;
        else if( rate > 2f )
            rate = 2f;

        return this.soundPool.play(soundId, volume, volume, 1, 0, rate);
    }

    private int jouerUneFois(int resId, float volume) {
        return jouerUneFois(resId, volume, 1f);
    }

    private int jouerUneFois(int resId, float volume, float rate) {

        if( resId == 0 )
            return 0;

        long now = System.nanoTime() / 1000000;

        Long jusqua = _sonsJusqua.get(Integer.valueOf(resId));

        if( (jusqua != null) && (now < jusqua.longValue()) )
            return 0;

        int stream = jouer(resId, volume, rate);

        if( stream == 0 )
            return 0;

        _sonsJusqua.put(Integer.valueOf(resId), Long.valueOf(now + soundDuration(resId)));

        return stream;
    }

    public void stopSound(int streamId) {
        if(this.soundPoolLoaded) {
            this.soundPool.stop(streamId);
        }
    }

    public void addAlarmObject( GameObject obj ) {

        if( alarmObjects.contains(obj) )
            return;

        // #81 : au demarrage, ce qui est DEJA en alarme part en sourdine. On habite peut-etre a cote
        // d'un radar de feu rouge ou d'une marque armee : lancer l'application ne doit pas faire
        // hurler le telephone qu'on a dans la main. L'alerte visuelle suffit a cet instant -- le son
        // n'est la que pour dire de regarder l'ecran, et on le regarde deja. Seules les alarmes qui
        // se declenchent APRES, celles dont on s'approche vraiment, font du bruit.
        if( _splashEnAttente )
            obj.setMuted(true);

        alarmObjects.add(obj);
    }

    /**
     * #81 : coupe les flux d'alarme en cours. Appele quand on change le son d'une alarme dans les
     * parametres : la boucle de rendu les relance au tour suivant avec le nouveau son, au lieu de
     * laisser l'ancien tourner jusqu'a ce que l'alarme s'arrete d'elle-meme.
     */
    public void resetAlarmStreams() {

        if( streamAlarmId > 0 ) {
            stopSound(streamAlarmId);
            streamAlarmId = 0;
        }

        if( streamMarkAlarmId > 0 ) {
            stopSound(streamMarkAlarmId);
            streamMarkAlarmId = 0;
        }

        if( _zoneStreamId > 0 ) {
            stopSound(_zoneStreamId);
            _zoneStreamId = 0;
        }
    }

    public void removeAlarmObject( GameObject obj ) {
        if( alarmObjects.contains(obj) )
            alarmObjects.remove(obj);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean bIntercept = false;

        final int action = event.getActionMasked();
        final int pointerCount = event.getPointerCount();

        // #33 : le tap qui referme le menu burger arrive aussi jusqu'ici (le popup ne peut pas etre
        // modal au toucher sans voler le focus fenetre). On l'avale avant tout traitement, sinon il
        // demarre un pan ou pose un segment de mesure.
        if( ViewsController.consumeMenuTouch(action) )
            return true;

        if( (_doubleTapDetector != null) && !_externalPan )
            _doubleTapDetector.onTouchEvent(event); // observe le double-tap sans consommer (pan/zoom intacts)

        final float xScreen = event.getX();
        final float yScreen = event.getY();
        final int x = getAreaX(xScreen);
        final int y = getAreaY(yScreen);

        boolean zoneEdit = (_am.getZoneState() == SurveillanceZone.EDIT) && (_am.getZone() != null);

        switch( action ) {

            case MotionEvent.ACTION_MOVE:
                if( _twoFingers && pointerCount >= 2 ) {
                    // --- Mode 2 doigts : recentrage auto suspendu tout le long ---
                    _disableScrollTime = SCROLL_DISABLE_MS;
                    if( zoneEdit && _zoneGesture )
                        updateZoneGesture(event);
                    // Hors edition : rien (le pinch-to-zoom est la tache #15).
                } else if( !_twoFingers ) {
                    // --- Mode 1 doigt : pan ---
                    // #75 : geste issu d'un tap deja consomme -> tant qu'on n'a pas franchi le seuil
                    // systeme, ce n'est qu'un tremblement du doigt : ni pan, ni deplacement de carte.
                    if( _tapHoldsPan ) {
                        if( Math.hypot(xScreen - _tapDownX, yScreen - _tapDownY) < (_tapConsumed ? _touchSlop : _dragSlop) ) {
                            bIntercept = true;
                            break;
                        }
                        _tapHoldsPan = false;
                        mLastMotionX = xScreen;   // re-ancrage : la carte ne saute pas au franchissement
                        mLastMotionY = yScreen;
                    }

                    _disableScrollTime = SCROLL_DISABLE_MS;
                    int deltaX = (int) (mLastMotionX - xScreen);
                    int deltaY = (int) (mLastMotionY - yScreen);
                    mLastMotionX = xScreen;
                    mLastMotionY = yScreen;

                    // Suivi de vitesse (px/ms) pour le fling au relachement.
                    long nowP = System.nanoTime();
                    if( _panVNs != 0 ) {
                        double dtMs = (nowP - _panVNs) / 1.0e6;
                        if( dtMs > 1.0 ) {
                            double a = 0.5;
                            _panVx = a * (deltaX / dtMs) + (1 - a) * _panVx;
                            _panVy = a * (deltaY / dtMs) + (1 - a) * _panVy;
                        }
                    }
                    _panVNs = nowP;

                    double newX = _scrollX+deltaX;
                    double newY = _scrollY+deltaY;

                    if( newX < 0 )
                        newX = 0;
                    else if( newX > getAreaWidth() - getWidth() )
                        newX = getAreaWidth() - getWidth() - 1;

                    if( newY < 0 )
                        newY = 0;
                    else if( newY > getAreaHeight() - getHeight() )
                        newY = getAreaHeight() - getHeight() - 1;

                    synchronized (this) {
                        _scrollX = (int) newX;
                        _scrollY = (int) newY;
                    }
                }
                bIntercept = true;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                // 2e doigt pose -> on bascule en mode 2 doigts (recentrage auto off).
                if( pointerCount == 2 ) {
                    _tapHoldsPan = false;   // #75 : a deux doigts, ce n'est plus un tap
                    _twoFingers = true;
                    _disableScrollTime = SCROLL_DISABLE_MS;
                    if( zoneEdit )
                        beginZoneGesture(event);
                }
                bIntercept = true;
                break;

            case MotionEvent.ACTION_POINTER_UP:
                // Un doigt se leve. Si on repasse a 1 doigt, re-ancrer le pan sur le doigt QUI RESTE.
                if( pointerCount == 2 ) {
                    if( _zoneGesture )
                        startZoneInertia();   // prolonge le geste avec la vitesse de fin (amortie)
                    _twoFingers = false;
                    _zoneGesture = false;
                    _disableScrollTime = SCROLL_DISABLE_MS;   // relance la tempo de suspension du recentrage
                    int remain = (event.getActionIndex() == 0) ? 1 : 0;
                    mLastMotionX = event.getX(remain);
                    mLastMotionY = event.getY(remain);
                }
                bIntercept = true;
                break;

            case MotionEvent.ACTION_UP:
                _tapHoldsPan = false;

                // #17 : un tap qui n'a rien fait d'autre entre en mode pan MAINTENANT, ce que faisait
                // l'ACTION_DOWN jusqu'ici. Le deplacer au relachement ne change rien pour la mesure
                // (#52), qui demande que le mode pan soit actif depuis plus de 250 ms au tap suivant :
                // le 1er tap l'arme toujours, simplement a la fin du geste au lieu du debut. En
                // revanche un appui long sur une mark, lui, a consomme le tap et n'arme donc rien.
                // Un glissement, lui, a deja arme le mode pan dans l'ACTION_MOVE.
                if( !_tapConsumed && !_twoFingers && !_externalPan )
                    _disableScrollTime = SCROLL_DISABLE_MS;

                // Fin d'un pan a 1 doigt : on lance le fling si la vitesse de fin est suffisante.
                if( !_twoFingers && Math.hypot(_panVx, _panVy) > 0.4 )   // px/ms
                    _panFling = true;

                _twoFingers = false;
                _zoneGesture = false;
                _externalPan = false;
                bIntercept = true;
                break;

            case MotionEvent.ACTION_CANCEL:
                _tapHoldsPan = false;
                _twoFingers = false;
                _zoneGesture = false;
                _externalPan = false;
                bIntercept = true;
                break;

            case MotionEvent.ACTION_DOWN:
                // #52 : age du mode pan AVANT que ce down ne le re-arme (0 si on entre en pan maintenant).
                boolean panWasActive = (_disableScrollTime > 0);
                if( !panWasActive )
                    _panStartNs = System.nanoTime();
                _tapPanAgeMs = panWasActive ? (System.nanoTime() - _panStartNs) / 1.0e6 : 0.0;

                _tapDownX = xScreen;
                _tapDownY = yScreen;

                _twoFingers = false;
                _zoneGesture = false;
                _ziActive = false;   // un appui stoppe l'inertie de zone en cours
                _panFling = false;   // ... et le fling du pan
                _panVNs = 0;
                _panVx = _panVy = 0.0;

                mLastMotionX = xScreen;
                mLastMotionY = yScreen;



                // #59 : un clic = UNE action et une seule. Priorite : couper une alarme qui sonne >
                // exploser un chibi > mesure/segments (traites ~300 ms plus tard par onSingleTapConfirmed).
                _tapConsumed = false;

                // Mute all Alarms
                boolean alarmSounding = _zoneOutside && !_zoneAlarmMuted;   // #25 : alarme de sortie de zone
                for (GameObject go : alarmObjects) {                        // #7  : marks warning en portee
                    if( !go.isMuted() ) {
                        alarmSounding = true;
                        go.setMuted(true);
                    }
                }

                if( alarmSounding ) {
                    _zoneAlarmMuted = true;   // #25 : le tap coupe aussi l'alarme de zone
                    _tapConsumed = true;      // ... et ne fait QUE ca
                }


                if( !_tapConsumed ) {

                    // A copy: a creature that dies takes itself off the list, and an iterator
                    // would not forgive that.
                    for( ChibiCharacter chibi : new ArrayList<ChibiCharacter>(this.chibiList) ) {

                        if( chibi.hit(x, y) ) {
                            _tapConsumed = true;   // #59 : clic sur un chibi -> explosion, et rien d'autre
                            break;
                        }
                    }

                }

                // #75 : un tap qui a servi a couper une alarme ou a exploser un chibi ne doit pas, EN
                // PLUS, faire entrer en mode pan : l'ecran se figeait 5 s et les viseurs de mesure
                // apparaissaient. Le pan n'est donc arme que si le tap n'a rien consomme -- ou si l'on
                // y etait deja, auquel cas la tempo se relance comme avant. Si le doigt se met ensuite
                // a glisser franchement, la garde du ACTION_MOVE rend la main au pan.
                // #17 : tout appui commence par ne PAS etre un pan. Il le devient quand le doigt
                // franchit le seuil (ACTION_MOVE) ou, a defaut, au relachement s'il n'a servi a rien
                // d'autre. Auparavant seul un tap deja consomme etait retenu ainsi, si bien que le
                // moindre ACTION_MOVE -- meme de zero pixel, le systeme en emet -- armait le pan.
                _tapHoldsPan = true;

                // #17 : le mode pan ne s'arme PLUS ici. Un appui ne devient un pan que s'il bouge
                // (ACTION_MOVE, au-dela du seuil) ou, a defaut, au relachement s'il n'a servi a rien
                // d'autre (ACTION_UP). Poser le doigt ne suffit plus : sans cela un appui long, qui
                // ne se declare que ~500 ms apres le DOWN, arrivait toujours trop tard -- la carte
                // etait deja figee 5 s et les viseurs de mesure affiches.

                // Les chibis se dirigent vers le point clique dans tous les cas (pas une "action" du clic).
                // Sauf un boss : il mene sa propre partie, et un doigt sur l'ecran ne la detourne pas.
                //
                // Et sauf celles qui VOIENT le joueur : elles ont trouve mieux qu'un point sur la
                // carte, et un appat ne doit pas leur faire lacher ce qu'elles poursuivent. Tant
                // qu'elles le voient, donc : couper la ligne de vue -- un batiment, la distance --
                // les rend appatables, ce qui est la seule main que le joueur garde sur elles.
                for (ChibiCharacter chibi : chibiList) {

                    if( chibi.isBoss() || chibi.seesPlayer() )
                        continue;

                    chibi.setDirection(x - chibi.getX(), y - chibi.getY());
                }
                bIntercept = true;
                break;

            default: break;
        }
        return bIntercept;
    }

    // #17 : la mark perso sous le doigt, ou null. Le repere est celui de la carte (getAreaX/Y), ou
    // une unite vaut un pixel ecran. La boite testee est celle de l'ICONE telle qu'elle est dessinee
    // -- meme ancrage, meme correction de 15/192 vers le bas -- elargie de MARK_TOUCH_SLOP_DP.
    //
    // Quand plusieurs marks se recouvrent, c'est la plus proche du doigt qui gagne, et non la
    // premiere de la liste : sur une carte chargee, deux marks voisines se chevauchent souvent et
    // prendre la premiere revient a en designer une au hasard.
    private PersonalMark findMarkAt(float xScreen, float yScreen) {

        final int x = getAreaX(xScreen);
        final int y = getAreaY(yScreen);
        final float slop = MARK_TOUCH_SLOP_DP * getResources().getDisplayMetrics().density;

        PersonalMark best = null;
        double bestDist = Double.MAX_VALUE;

        List<PersonalMark> marks = _am.getMarksEnumerable();

        // Parcours par indice : cette liste est aussi lue par le thread de rendu, un iterateur
        // n'aimerait pas qu'elle change en cours de route.
        for( int i = 0; i < marks.size(); i++ ) {

            PersonalMark mark = marks.get(i);

            if( (mark == null) || (mark.getChunk() != map) || !mark.isVisible() )
                continue;

            double left = mark.getAnchorX();
            double top = mark.getAnchorY() + mark.getHeight() * (15.0 / 192.0);
            double right = left + mark.getWidth();
            double bottom = top + mark.getHeight();

            if( (x < left - slop) || (x > right + slop) || (y < top - slop) || (y > bottom + slop) )
                continue;

            double dist = Math.hypot(x - (left + right) / 2.0, y - (top + bottom) / 2.0);

            if( dist < bestDist ) {
                bestDist = dist;
                best = mark;
            }
        }

        return best;
    }

    // #71 : entree du pan pour un geste transfere par GBRootLayout. On ne fabrique pas un faux
    // ACTION_DOWN : celui-ci declencherait tout ce qu'un appui declenche -- couper une alarme qui
    // sonne, exploser un chibi, reorienter les chibis, armer le clic de mesure -- alors que le doigt
    // partait d'un bouton et ne demande qu'a deplacer la carte. On arme donc le seul pan : point
    // d'ancrage du geste, mode deplacement, et remise a zero des inerties en cours. Les MOVE puis
    // l'UP qui suivent passent par onTouchEvent comme n'importe quel pan, fling compris.
    public void beginExternalPan(float x, float y) {
        _tapHoldsPan = false;   // #75 : geste transfere par GBRootLayout, jamais un tap sur la carte
        _externalPan = true;

        boolean panWasActive = (_disableScrollTime > 0);
        if( !panWasActive )
            _panStartNs = System.nanoTime();

        _twoFingers = false;
        _zoneGesture = false;
        _ziActive = false;
        _panFling = false;
        _panVNs = 0;
        _panVx = 0.0;
        _panVy = 0.0;

        _disableScrollTime = SCROLL_DISABLE_MS;

        mLastMotionX = x;
        mLastMotionY = y;
    }

    // #52 : clic sur la carte en mode PAN. Si le clic tombe sur un des 3 viseurs de mesure,
    // on ouvre le popup de creation de mark (comme le bouton "+") mais a la position VISEE par
    // le centre du viseur, et non a la position courante. Hors viseur : rien.
    private void handleTargetClick(float sx, float sy) {
        float[][] pts = MapGPSPos.computeTargetPositions(this);
        Chunk chunk = getMap();
        if( pts == null || chunk == null )
            return;

        float hitR = 30f * getResources().getDisplayMetrics().density;   // tolerance autour du viseur
        for( int i = 0; i < pts.length; i++ ) {
            if( _am.getSingleTarget() && i != 1 )   // #52 : cible unique -> seul le milieu est cliquable
                continue;
            if( !isTargetShown(i) )                 // #64 : viseur efface par le choix du 1er clic
                continue;
            if( Math.hypot(sx - pts[i][0], sy - pts[i][1]) <= hitR ) {
                // Clic SUR un viseur -> creation d'une mark au point vise (centre du viseur).
                double lon = chunk.getLongitude(getAreaX(pts[i][0]), getAreaWidth());
                double lat = chunk.getLatitude(getAreaY(pts[i][1]), getAreaHeight());
                _am.addNewMarkAt(lon, lat);
                return;
            }
        }

        // Clic HORS viseur -> mesure : avance la chaine du viseur retenu (le 1er clic le designe).
        addMeasureClick(pts, chunk, sx, sy);
    }

    // #64 : index du viseur le plus proche d'un point ecran, ou -1 si les viseurs ne sont pas la. En
    // mode cible unique il n'y a que le milieu, donc aucun choix a faire.
    private int nearestTarget(float[][] pts, float sx, float sy) {
        if( _am.getSingleTarget() )
            return MIDDLE_TARGET;
        if( pts == null )
            return NO_TARGET;
        int best = NO_TARGET;
        double bestD2 = Double.MAX_VALUE;
        for( int i = 0; i < pts.length; i++ ) {
            double dx = sx - pts[i][0], dy = sy - pts[i][1];
            double d2 = dx * dx + dy * dy;
            if( d2 < bestD2 ) {
                bestD2 = d2;
                best = i;
            }
        }
        return best;
    }

    // #64 : un viseur est affiche tant qu'aucun dessin n'a commence ; ensuite seul celui qui a ete
    // retenu au premier clic subsiste. Lu par le rendu (MapGPSPos) et par le hit-test du clic.
    public boolean isTargetShown(int i) {
        int t = _measureTarget;
        return (t == NO_TARGET) || (t == i);
    }

    // #64 : un clic hors viseur fait avancer la SEULE chaine retenue. Le premier clic la designe : le
    // viseur le plus proche du point clique. Premier point de la chaine : le viseur du MILIEU demarre a
    // la position GPS et trace donc un vrai segment des ce 1er clic (comportement d'origine), tandis que
    // les deux viseurs lateraux ne posent que le point de reference, a l'endroit vise au moment du clic.
    private void addMeasureClick(float[][] pts, Chunk chunk, float sx, float sy) {

        // Easter egg: not while a game is on. A tap on the map is a tomato thrown at a creature,
        // and the player should not be leaving a trail of measuring segments behind every miss.
        if( _am.getIsGameMode() )
            return;

        if( _measureTarget == NO_TARGET )
            _measureTarget = nearestTarget(pts, sx, sy);   // 1er clic -> choix definitif jusqu'au balai

        int i = _measureTarget;
        if( i < 0 || i >= pts.length )
            return;

        double gpsX = chunk.getX(_am.getLongitude(), getAreaWidth());
        double gpsY = chunk.getY(_am.getLatitude(), getAreaHeight());
        synchronized( _measureLock ) {
            java.util.List<double[]> chain = _measureChains.get(i);
            if( chain.isEmpty() && i == MIDDLE_TARGET )
                chain.add(new double[]{ gpsX, gpsY });   // milieu seul : point de depart = GPS
            chain.add(new double[]{ getAreaX(pts[i][0]), getAreaY(pts[i][1]) });
        }
        playSoundClick();          // #52 : retour sonore de la creation des segments
        // #52/#64 : des le 1er clic de mesure -> mode pause (balai), gel du retour a la normale. Y compris
        // le clic lateral qui ne pose qu'un point de reference : sans le gel, le compte a rebours l'effacerait.
        _am.setMeasurePaused(true);
    }

    // #52 : reset des chaines de mesure (appele a la sortie du mode pan, donc au balai).
    private void resetMeasure() {
        synchronized( _measureLock ) {
            for( java.util.List<double[]> chain : _measureChains )
                chain.clear();
        }
        _measureTarget = NO_TARGET;   // #64 : les trois viseurs reapparaissent
    }

    // #75 : balai -- efface les chaines de mesure SANS quitter le mode pan. La tempo de suspension
    // du recentrage auto est relancee au maximum : la vue reste exactement ou elle est et le compte
    // a rebours 5..1 repart, comme apres un pan. Sans cela un premier point pose par erreur ne
    // pouvait s'effacer qu'en perdant la portion de chunk affichee a l'ecran.
    public void clearMeasureAndKeepPan() {
        resetMeasure();
        _disableScrollTime = SCROLL_DISABLE_MS;
    }

    // #53 : conversion des points memorises quand la taille utile de la vue change (fenetre
    // redimensionnee). Les coordonnees de zone sont lineaires en base, un facteur suffit. On REMPLACE
    // les points au lieu de les modifier, pour tenir l'invariant du snapshot ci-dessous (les double[]
    // ne sont jamais mutes une fois crees).
    private void rescaleMeasureChains(double ratio) {

        synchronized( _measureLock ) {
            for( java.util.List<double[]> chain : _measureChains ) {
                for( int i = 0; i < chain.size(); i++ ) {
                    double[] pt = chain.get(i);
                    chain.set(i, new double[]{ pt[0] * ratio, pt[1] * ratio });
                }
            }
        }
    }

    // #52 : snapshot des chaines de mesure pour le rendu. Copie superficielle : les points (double[])
    // ne sont jamais mutes une fois crees.
    public java.util.List<java.util.List<double[]>> getMeasureChains() {
        synchronized( _measureLock ) {
            java.util.List<java.util.List<double[]>> copy = new java.util.ArrayList<>(3);
            for( java.util.List<double[]> chain : _measureChains )
                copy.add(new java.util.ArrayList<>(chain));
            return copy;
        }
    }

    // Axe "longueur" de la zone en repere ecran (carte au nord) : cap b -> (sin b, -cos b).
    // Axe "largeur" perpendiculaire : (cos b, sin b).
    private void beginZoneGesture(MotionEvent e) {
        SurveillanceZone z = _am.getZone();
        if( z == null ) return;
        _zgF0x0 = e.getX(0); _zgF0y0 = e.getY(0);
        _zgF1x0 = e.getX(1); _zgF1y0 = e.getY(1);
        double dx = _zgF1x0 - _zgF0x0;
        double dy = _zgF1y0 - _zgF0y0;
        double b = Math.toRadians(z.angleDeg);
        double lux = Math.sin(b), luy = -Math.cos(b);   // axe longueur
        double wux = Math.cos(b), wuy = Math.sin(b);    // axe largeur
        _zgTheta0 = z.angleDeg;
        _zgA0 = Math.atan2(dy, dx);
        _zgSu0 = dx * lux + dy * luy;
        _zgSw0 = dx * wux + dy * wuy;
        _zgLen0 = z.halfLenM;
        _zgWid0 = z.halfWidthM;
        _zgMidAX0 = (_zgF0x0 + _zgF1x0) / 2.0 + _scrollX;
        _zgMidAY0 = (_zgF0y0 + _zgF1y0) / 2.0 + _scrollY;
        _zgPxPerM = 1.0;
        Chunk ch = map;   // meme chunk que getAreaWidth (VZ coherent) : evite le mismatch pendant un swap
        if( ch != null ) {
            _zgCAX0 = ch.getX(z.centerLon, getAreaWidth());
            _zgCAY0 = ch.getY(z.centerLat, getAreaHeight());
            // Echelle px/metre via la latitude (non deformee par cos(lat)).
            double y1 = ch.getY(z.centerLat, getAreaHeight());
            double y2 = ch.getY(z.centerLat + 100.0 / 111320.0, getAreaHeight());
            double dpx = Math.abs(y2 - y1);
            if( dpx > 1e-6 ) _zgPxPerM = dpx / 100.0;
        }
        _zgMode = 0;   // indecis : le 1er mouvement dominant verrouillera le geste
        _zoneGesture = true;
        // Reset du suivi de vitesse + on stoppe toute inertie en cours (on reprend la main).
        _zgVNs = 0;
        _zgVAngle = _zgVHalf = _zgVCx = _zgVCy = 0.0;
        _ziActive = false;
    }

    private void updateZoneGesture(MotionEvent e) {
        SurveillanceZone z = _am.getZone();
        if( z == null ) { _zoneGesture = false; return; }
        Chunk ch = map;   // meme chunk que getAreaWidth (VZ coherent) : evite le mismatch pendant un swap

        double f0x = e.getX(0), f0y = e.getY(0);
        double f1x = e.getX(1), f1y = e.getY(1);
        double dx = f1x - f0x, dy = f1y - f0y;
        double aNow = Math.atan2(dy, dx);
        double dAlpha = aNow - _zgA0;   // rotation de la ligne des doigts (rad)

        // Axes de la zone FIGES au debut du geste (repere ecran, carte au nord).
        double b0 = Math.toRadians(_zgTheta0);
        double lux = Math.sin(b0), luy = -Math.cos(b0);
        double wux = Math.cos(b0), wuy = Math.sin(b0);

        // Deplacement de chaque doigt depuis le debut.
        double d0x = f0x - _zgF0x0, d0y = f0y - _zgF0y0;
        double d1x = f1x - _zgF1x0, d1y = f1y - _zgF1y0;
        double n0 = Math.hypot(d0x, d0y), n1 = Math.hypot(d1x, d1y);

        // --- VERROU au debut : on classe le geste (une seule fois) ---
        if( _zgMode == 0 ) {
            double mx = Math.max(n0, n1);
            if( mx < 35.0 ) return;   // seuil : on attend un mouvement assez net avant de verrouiller
            boolean oneFixed = Math.min(n0, n1) < 0.35 * mx;

            if( !oneFixed ) {
                // Les 2 doigts bougent : deplacement / rotation-milieu / resize symetrique.
                double cc = Math.cos(-dAlpha), ss = Math.sin(-dAlpha);   // de-rotation pour isoler le resize
                double dxr = dx * cc - dy * ss, dyr = dx * ss + dy * cc;
                double suR = dxr * lux + dyr * luy, swR = dxr * wux + dyr * wuy;
                double l0 = Math.hypot(_zgSu0, _zgSw0);
                double mTrans = Math.hypot((d0x + d1x) / 2.0, (d0y + d1y) / 2.0);
                double mRot = (l0 * 0.5) * Math.abs(dAlpha);
                double mLen = Math.abs(Math.abs(suR) - Math.abs(_zgSu0));
                double mWid = Math.abs(Math.abs(swR) - Math.abs(_zgSw0));
                double best = Math.max(Math.max(mTrans, mRot), Math.max(mLen, mWid));
                if( best == mTrans ) { _zgMode = 4; }
                else if( best == mRot ) { _zgMode = 1; _zgPivotAX = _zgMidAX0; _zgPivotAY = _zgMidAY0; }
                else { _zgMode = (best == mLen) ? 2 : 3; _zgSym = true; _zgMovingFinger = -1; }
            } else {
                // Un doigt fixe (A), l'autre bouge (B) : rotation-autour-de-A ou resize asymetrique.
                int moving = (n0 >= n1) ? 0 : 1;
                _zgMovingFinger = moving;
                double asx = (moving == 0) ? _zgF1x0 : _zgF0x0;   // depart doigt fixe A
                double asy = (moving == 0) ? _zgF1y0 : _zgF0y0;
                double bsx = (moving == 0) ? _zgF0x0 : _zgF1x0;   // depart doigt mobile B
                double bsy = (moving == 0) ? _zgF0y0 : _zgF1y0;
                double dbx = (moving == 0) ? d0x : d1x;
                double dby = (moving == 0) ? d0y : d1y;
                double lx = bsx - asx, ly = bsy - asy;             // ligne A->B
                double ll = Math.hypot(lx, ly);
                if( ll < 1e-3 ) return;
                double ux = lx / ll, uy = ly / ll;
                double radial = dbx * ux + dby * uy;
                double tangential = dbx * (-uy) + dby * ux;
                if( Math.abs(tangential) > Math.abs(radial) ) {
                    _zgMode = 1;                                   // rotation autour du doigt fixe A
                    _zgPivotAX = asx + _scrollX;
                    _zgPivotAY = asy + _scrollY;
                } else {
                    // resize asymetrique : axe le plus aligne avec la ligne A->B
                    double alongLen = Math.abs(ux * lux + uy * luy);
                    double alongWid = Math.abs(ux * wux + uy * wuy);
                    _zgMode = (alongLen >= alongWid) ? 2 : 3;
                    _zgSym = false;
                    // Cote pilote = selon l'ORDRE des 2 doigts sur l'axe (fige au depart, 1 doigt <-> 1 cote) :
                    // le doigt le plus "bas" sur l'axe pilote le bord -, le plus "haut" pilote le bord +.
                    double axU = (_zgMode == 2) ? lux : wux;
                    double axV = (_zgMode == 2) ? luy : wuy;
                    double projB = bsx * axU + bsy * axV;   // doigt mobile B
                    double projA = asx * axU + asy * axV;   // doigt fixe A
                    _zgSideSign = (projB < projA) ? -1.0 : 1.0;
                }
            }
            if( _zgMode == 0 ) return;
        }

        // --- APPLICATION ---
        switch( _zgMode ) {
            case 4: {   // deplacer la zone (translation = moyenne des 2 deplacements)
                if( ch != null ) {
                    double nAX = _zgCAX0 + (d0x + d1x) / 2.0;
                    double nAY = _zgCAY0 + (d0y + d1y) / 2.0;
                    z.centerLon = ch.getLongitude(nAX, getAreaWidth());
                    z.centerLat = ch.getLatitude(nAY, getAreaHeight());
                }
                break;
            }
            case 1: {   // rotation autour du pivot (milieu des doigts OU doigt fixe)
                if( ch != null ) {
                    double relx = _zgCAX0 - _zgPivotAX, rely = _zgCAY0 - _zgPivotAY;
                    double cc = Math.cos(dAlpha), ss = Math.sin(dAlpha);
                    double nAX = _zgPivotAX + (relx * cc - rely * ss);
                    double nAY = _zgPivotAY + (relx * ss + rely * cc);
                    z.centerLon = ch.getLongitude(nAX, getAreaWidth());
                    z.centerLat = ch.getLatitude(nAY, getAreaHeight());
                }
                z.angleDeg = _zgTheta0 + Math.toDegrees(dAlpha);
                break;
            }
            case 2:
            case 3: {
                if( _zgSym ) {
                    // resize symetrique : ratio des projections de-rotees, centre inchange.
                    double cc = Math.cos(-dAlpha), ss = Math.sin(-dAlpha);
                    double dxr = dx * cc - dy * ss, dyr = dx * ss + dy * cc;
                    double suR = dxr * lux + dyr * luy, swR = dxr * wux + dyr * wuy;
                    if( _zgMode == 2 && Math.abs(_zgSu0) > 20 )
                        z.halfLenM = clampHalf(_zgLen0 * Math.abs(suR) / Math.abs(_zgSu0), ZONE_MIN_HALF_M);
                    else if( _zgMode == 3 && Math.abs(_zgSw0) > 20 )
                        z.halfWidthM = clampHalf(_zgWid0 * Math.abs(swR) / Math.abs(_zgSw0), ZONE_MIN_HALF_M);
                } else if( ch != null ) {
                    // resize asymetrique : le cote du doigt mobile suit le doigt ; cote oppose ancre.
                    double axU = (_zgMode == 2) ? lux : wux;
                    double axV = (_zgMode == 2) ? luy : wuy;
                    double dbx = (_zgMovingFinger == 0) ? d0x : d1x;
                    double dby = (_zgMovingFinger == 0) ? d0y : d1y;
                    double moveEdge = dbx * axU + dby * axV;                       // px le long de l'axe
                    double half0px = ((_zgMode == 2) ? _zgLen0 : _zgWid0) * _zgPxPerM;
                    double fixedEdge = -_zgSideSign * half0px;
                    double movingEdge = _zgSideSign * half0px + moveEdge;
                    // Borne : le cote mobile ne franchit pas la taille mini (sinon a la limite le centre
                    // driverait et le rectangle se retournerait). On epingle la distance au cote fixe.
                    double minPx = ZONE_MIN_HALF_M * _zgPxPerM, maxPx = 200000.0 * _zgPxPerM;
                    double mag = _zgSideSign * (movingEdge - fixedEdge);          // distance signee, >0 normalement
                    if( mag < 2 * minPx ) mag = 2 * minPx;
                    else if( mag > 2 * maxPx ) mag = 2 * maxPx;
                    movingEdge = fixedEdge + _zgSideSign * mag;
                    double newHalfM = (mag / 2.0) / _zgPxPerM;
                    double newCenterU = (movingEdge + fixedEdge) / 2.0;
                    double nAX = _zgCAX0 + newCenterU * axU;
                    double nAY = _zgCAY0 + newCenterU * axV;
                    if( _zgMode == 2 ) z.halfLenM = newHalfM; else z.halfWidthM = newHalfM;
                    z.centerLon = ch.getLongitude(nAX, getAreaWidth());
                    z.centerLat = ch.getLatitude(nAY, getAreaHeight());
                }
                break;
            }
        }
        trackZoneVelocity(z, ch);
    }

    // Suit la vitesse lissee (EMA) de la grandeur pilotee par le mode courant, pour l'inertie.
    private void trackZoneVelocity(SurveillanceZone z, Chunk ch) {
        long now = System.nanoTime();
        double curAngle = z.angleDeg;
        double curHalf = (_zgMode == 2) ? z.halfLenM : (_zgMode == 3) ? z.halfWidthM : 0.0;
        double curCx = _zgCAX0, curCy = _zgCAY0;
        if( ch != null ) {
            curCx = ch.getX(z.centerLon, getAreaWidth());
            curCy = ch.getY(z.centerLat, getAreaHeight());
        }
        if( _zgVNs != 0 ) {
            double dtMs = (now - _zgVNs) / 1.0e6;
            if( dtMs > 1.0 ) {
                double a = 0.5;   // lissage
                _zgVAngle = a * ((curAngle - _zgPrevAngle) / dtMs) + (1 - a) * _zgVAngle;
                _zgVHalf  = a * ((curHalf  - _zgPrevHalf ) / dtMs) + (1 - a) * _zgVHalf;
                _zgVCx    = a * ((curCx    - _zgPrevCx   ) / dtMs) + (1 - a) * _zgVCx;
                _zgVCy    = a * ((curCy    - _zgPrevCy   ) / dtMs) + (1 - a) * _zgVCy;
            }
        }
        _zgVNs = now;
        _zgPrevAngle = curAngle; _zgPrevHalf = curHalf; _zgPrevCx = curCx; _zgPrevCy = curCy;
    }

    // Au relachement : lance l'inertie si la vitesse de fin de geste depasse un petit seuil.
    private void startZoneInertia() {
        if( _zgMode == 0 ) { _ziActive = false; return; }
        _ziMode = _zgMode; _ziSym = _zgSym; _ziSideSign = _zgSideSign; _ziPxPerM = _zgPxPerM;
        _ziPivotAX = _zgPivotAX; _ziPivotAY = _zgPivotAY;
        _ziVAngle = _zgVAngle; _ziVHalf = _zgVHalf; _ziVCx = _zgVCx; _ziVCy = _zgVCy;
        boolean has = Math.abs(_ziVAngle) > 0.002        // deg/ms (~2 deg/s)
                   || Math.abs(_ziVHalf) > 0.01          // m/ms (~10 m/s)
                   || Math.hypot(_ziVCx, _ziVCy) > 0.02; // px/ms
        _ziActive = has;
    }

    // Applique l'inertie chaque frame (appelee depuis update()). dtMs = delta de la frame.
    private void applyZoneInertia(double dtMs) {
        if( !_ziActive ) return;
        SurveillanceZone z = _am.getZone();
        if( z == null || _am.getZoneState() != SurveillanceZone.EDIT ) { _ziActive = false; return; }
        Chunk ch = map;   // meme chunk que getAreaWidth (VZ coherent) : evite le mismatch pendant un swap
        double decay = Math.pow(0.5, dtMs / 250.0);   // demi-vie 250 ms

        switch( _ziMode ) {
            case 1: {   // rotation : on prolonge AUTOUR DU MEME PIVOT que le geste (pas de saut du centre)
                double dAng = _ziVAngle * dtMs;
                if( ch != null ) {
                    double cx = ch.getX(z.centerLon, getAreaWidth());
                    double cy = ch.getY(z.centerLat, getAreaHeight());
                    double relx = cx - _ziPivotAX, rely = cy - _ziPivotAY;
                    double cc = Math.cos(Math.toRadians(dAng)), ss = Math.sin(Math.toRadians(dAng));
                    double nx = _ziPivotAX + (relx * cc - rely * ss);
                    double ny = _ziPivotAY + (relx * ss + rely * cc);
                    z.centerLon = ch.getLongitude(nx, getAreaWidth());
                    z.centerLat = ch.getLatitude(ny, getAreaHeight());
                }
                z.angleDeg += dAng;
                break;
            }
            case 2:
            case 3: {
                double dHalf = _ziVHalf * dtMs;
                double cur = (_ziMode == 2) ? z.halfLenM : z.halfWidthM;
                double nv = clampHalf(cur + dHalf, ZONE_MIN_HALF_M);
                double applied = nv - cur;
                if( _ziMode == 2 ) z.halfLenM = nv; else z.halfWidthM = nv;
                if( applied == 0.0 ) { _ziVHalf = 0.0; }   // borne atteinte -> on tue cette vitesse
                else if( !_ziSym && ch != null ) {
                    // asymetrique : ancrer le cote fixe -> deplacer le centre de sideSign*applied le long de l'axe.
                    double b = Math.toRadians(z.angleDeg);
                    double axU = (_ziMode == 2) ? Math.sin(b) : Math.cos(b);
                    double axV = (_ziMode == 2) ? -Math.cos(b) : Math.sin(b);
                    double shiftPx = _ziSideSign * applied * _ziPxPerM;
                    double cx = ch.getX(z.centerLon, getAreaWidth()) + shiftPx * axU;
                    double cy = ch.getY(z.centerLat, getAreaHeight()) + shiftPx * axV;
                    z.centerLon = ch.getLongitude(cx, getAreaWidth());
                    z.centerLat = ch.getLatitude(cy, getAreaHeight());
                }
                break;
            }
            case 4:
                if( ch != null ) {
                    double cx = ch.getX(z.centerLon, getAreaWidth()) + _ziVCx * dtMs;
                    double cy = ch.getY(z.centerLat, getAreaHeight()) + _ziVCy * dtMs;
                    z.centerLon = ch.getLongitude(cx, getAreaWidth());
                    z.centerLat = ch.getLatitude(cy, getAreaHeight());
                }
                break;
        }

        _ziVAngle *= decay; _ziVHalf *= decay; _ziVCx *= decay; _ziVCy *= decay;
        if( Math.abs(_ziVAngle) < 0.0005 && Math.abs(_ziVHalf) < 0.002
                && Math.hypot(_ziVCx, _ziVCy) < 0.005 )
            _ziActive = false;
    }

    // #9 : fling du pan. Prolonge le defilement avec la vitesse de fin, amortie ; s'arrete aux bords.
    private void applyPanFling(double dtMs) {
        if( !_panFling ) return;
        _disableScrollTime = SCROLL_DISABLE_MS;   // le fling garde le recentrage auto suspendu
        double nx = _scrollX + _panVx * dtMs;
        double ny = _scrollY + _panVy * dtMs;
        boolean hitX = false, hitY = false;
        if( nx < 0 ) { nx = 0; hitX = true; }
        else if( nx > getAreaWidth() - getWidth() ) { nx = getAreaWidth() - getWidth() - 1; hitX = true; }
        if( ny < 0 ) { ny = 0; hitY = true; }
        else if( ny > getAreaHeight() - getHeight() ) { ny = getAreaHeight() - getHeight() - 1; hitY = true; }
        synchronized (this) { _scrollX = (int) nx; _scrollY = (int) ny; }
        if( hitX ) _panVx = 0.0;
        if( hitY ) _panVy = 0.0;

        double decay = Math.pow(0.5, dtMs / 250.0);   // demi-vie 250 ms (comme l'inertie de zone)
        _panVx *= decay; _panVy *= decay;
        if( Math.hypot(_panVx, _panVy) < 0.02 )
            _panFling = false;
    }

    // #25 etape 5 : alarme de sortie de zone. Active seulement quand la zone est ACTIVE (pas en edition/none).
    // Hysteresis : on ne passe "dehors" qu'au-dela de la marge, et on ne revient "dedans" qu'au-dela aussi.
    private void updateZoneAlarm() {
        SurveillanceZone z = _am.getZone();
        int st = _am.getZoneState();
        if( z == null || st != SurveillanceZone.ACTIVE ) {
            // Pas de zone active (edition ou aucune) : alarme en pause + re-armement (on repart "dedans").
            if( _zoneStreamId > 0 ) { stopSound(_zoneStreamId); _zoneStreamId = 0; }
            _zoneOutside = false;
            _zoneAlarmMuted = false;
            return;
        }
        double lat = _am.getLatitude(), lon = _am.getLongitude();
        if( !_zoneOutside ) {
            if( !z.contains(lat, lon, ZONE_HYST_M) ) {   // sortie franche
                _zoneOutside = true;
                _zoneAlarmMuted = false;                 // nouvelle sortie -> (re)declenche
            }
        } else {
            if( z.contains(lat, lon, -ZONE_HYST_M) )     // rentree franche
                _zoneOutside = false;
        }
        if( _zoneOutside && !_zoneAlarmMuted ) {
            if( _zoneStreamId == 0 ) _zoneStreamId = playSoundZoneAlarm();
        } else if( _zoneStreamId > 0 ) {
            stopSound(_zoneStreamId);
            _zoneStreamId = 0;
        }
    }

    public double repositionX( Chunk oldChunk, Chunk newChunk, double x, int widthVisible ) {
        double newX = x;

        if( (oldChunk != null) && (newChunk != null) ) {

            // Le VZ (echelle) peut differer entre chunks (bande de vitesse differente) : inverser
            // l'ancienne position avec l'echelle de l'ANCIEN chunk, projeter avec celle du NOUVEAU.
            // Sinon decalage du facteur VZ_ancien/VZ_nouveau (bug d'echelle au changement de vitesse).
            int base = getWidth();
            if( getHeight() > base )
                base = getHeight();
            int oldScale = base * oldChunk.getVirtualZoom();
            int newScale = base * newChunk.getVirtualZoom();

            double longitude = oldChunk.getLongitude(x, oldScale);
            newX = (int)newChunk.getX(longitude, newScale);

            if( widthVisible > 0 ) {
                if (newX < 0)
                    newX = 0;
                else if (newX > newScale - widthVisible)
                    newX = newScale - widthVisible - 1;
            }
        }

        return newX;
    }

    public double repositionY( Chunk oldChunk, Chunk newChunk, double y, int heightVisible ) {
        double newY = y;

        if( (oldChunk != null) && (newChunk != null) ) {

            // Idem repositionX : inverser avec l'echelle de l'ANCIEN chunk, projeter avec le NOUVEAU
            // (le VZ peut differer entre chunks de bandes de vitesse differentes).
            int base = getWidth();
            if( getHeight() > base )
                base = getHeight();
            int oldScale = base * oldChunk.getVirtualZoom();
            int newScale = base * newChunk.getVirtualZoom();

            double latitude = oldChunk.getLatitude(y, oldScale);
            newY = (int)newChunk.getY(latitude, newScale);

            if( heightVisible > 0 ) {
                if (newY < 0)
                    newY = 0;
                else if (newY > newScale - heightVisible)
                    newY = newScale - heightVisible - 1;
            }
        }

        return newY;
    }



    public void update()  {
        Chunk oMap = null;
        boolean blockedMap = false;

        // Current time in nanoseconds
        long now = System.nanoTime();

        // Never once did draw.
        if(_lastUpdateNanoTime ==-1) {
            _lastUpdateNanoTime = now;
        }
        if(_scrollNanoTime ==-1) {
            _scrollNanoTime = now;
        }
        // Change nanoseconds to milliseconds (1 nanosecond = 1000000 milliseconds).
        int deltaUpdateTime = (int) ((now - _lastUpdateNanoTime)/ 1000000 );

        long _scrollEndNanoTime = _scrollNanoTime+_scrollTimeMS*1000000;
        int deltaScrollTime = (int) ((_scrollEndNanoTime - now)/ 1000000 );

        // #52 : GEL du compte a rebours du recentrage auto (maintenu au max) quand on etait deja en pan
        // ET soit la popup mark est ouverte, soit on est en mode pause mesure (1 segment cree). Sinon,
        // decompte normal. (Restreint : ni les dialogs plein ecran du menu, ni un ajout hors pan.)
        if( (ViewsController.isMarkEditorOpen() || _am.isMeasurePaused()) && _disableScrollTime > 0 ) {
            _disableScrollTime = SCROLL_DISABLE_MS;
        } else {
            _disableScrollTime -= deltaUpdateTime;
            if( _disableScrollTime < 0)
                _disableScrollTime = 0;
        }

        // #26 : la visibilite du bouton de recentrage suit l'etat du pan. On pousse
        // vers l'UI (via post) uniquement aux transitions arme<->expire.
        final boolean panNow = (_disableScrollTime > 0);
        if( panNow != _lastPanActive ) {
            _lastPanActive = panNow;
            if( !panNow )
                resetMeasure();   // #52 : sortie du mode pan -> efface les chaines de mesure
            post(new Runnable() {
                @Override public void run() { _am.setRecenterBtnVisible(panNow); }
            });
        }

        // #52 : chiffre du compte a rebours (5..1) affiche sur le bouton, pousse a chaque changement de
        // palier. "" en mode pause (balai) ou hors pan. Seuils : >4s->5, >3s->4, >2s->3, >1s->2, sinon 1.
        String cd;
        if( !panNow || _am.isMeasurePaused() )
            cd = "";
        else if( _disableScrollTime > 4000 ) cd = "5";
        else if( _disableScrollTime > 3000 ) cd = "4";
        else if( _disableScrollTime > 2000 ) cd = "3";
        else if( _disableScrollTime > 1000 ) cd = "2";
        else                                 cd = "1";
        if( !cd.equals(_lastPushedCountdown) ) {
            _lastPushedCountdown = cd;
            final String fcd = cd;
            post(new Runnable() {
                @Override public void run() { _am.setRecenterCountdown(fcd); }
            });
        }

        applyZoneInertia(deltaUpdateTime);   // #25 : inertie de la zone au relachement
        applyPanFling(deltaUpdateTime);      // #9  : fling du pan au relachement

        blockedMap = (_disableScrollTime > 0);

        if( loadMap && !blockedMap )
        {
            oMap = map;
            alarmObjects.clear();

            map = _am.getCurrentChunk();
            if( map != null ) {
                map.setGameSurface(this);                 // chunk-owned objects (areas/roads/seamarks)
                _am.switchAndReContextMarksToChunk(map);   // shared marks, in lockstep with the camera swap
            }

            _scrollX = (int)repositionX(oMap, map, _scrollX, getWidth() );
            _scrollY = (int)repositionY(oMap, map, _scrollY, getHeight() );

            _startScrollX = (int)repositionX(oMap, map, _startScrollX, getWidth() );
            _startScrollY = (int)repositionY(oMap, map, _startScrollY, getHeight() );

            _endScrollX = (int)repositionX(oMap, map, _endScrollX, getWidth() );
            _endScrollY = (int)repositionY(oMap, map, _endScrollY, getHeight() );

            // We just switched the render target from oMap to map. This is the game
            // thread (the only one that draws), so oMap is guaranteed not to be drawn
            // anymore: free its per-instance road/area textures to cap native memory.
            if( oMap != null && oMap != map )
                oMap.disposeTextures();

            loadMap = false;
        }

        // #53 : les objets du chunk (cotes, routes, seamarks, marks) figent leurs x,y dans
        // contextScale() a partir de la taille utile de la vue, base = max(largeur, hauteur), alors
        // que la position GPS, la trace et la zone sont reprojetees a chaque frame. Les deux ne
        // s'accordent donc que si l'on re-scale des que cette base change. C'etait jusqu'ici un effet
        // de bord du cycle setGameSurface(null)/setGameSurface(this) de surfaceCreated : absent quand
        // la fenetre est redimensionnee (meme surface, meme chunk -> les deux appels sont des no-op),
        // et fatal en mode balai, ou le lien casse n'etait jamais retabli. On le declenche desormais
        // sur la seule grandeur dont il depend, ici, sur le thread de rendu, seul proprietaire de map.
        // Trois cas couverts : premier affichage (le constructeur a scale a taille nulle, base passe
        // de 0 a la vraie taille), rotation (base invariante, aucun travail) et fenetre redimensionnee.
        int base = getWidth();
        if( getHeight() > base )
            base = getHeight();

        if( base > 0 && base != _scaleBase ) {
            if( map != null )
                map.contextScaleObjects();

            // #53 : les chaines de mesure sont memorisees en coordonnees de zone, donc elles aussi
            // proportionnelles a la base. Sans conversion, un redimensionnement pendant un pan fige
            // laissait les segments a l'ancienne echelle : trace decale et distances fausses.
            if( _scaleBase > 0 )
                rescaleMeasureChains((double) base / (double) _scaleBase);

            _scaleBase = base;
        }


        if( !blockedMap ) {

            if (deltaScrollTime > 0) {
                float fraction = (float) (_scrollTimeMS - deltaScrollTime) / _scrollTimeMS;

                _scrollX = lerp(_startScrollX, _endScrollX, fraction);
                _scrollY = lerp(_startScrollY, _endScrollY, fraction);
            } else {
                _scrollX = _endScrollX;
                _scrollY = _endScrollY;
                _scrollNanoTime = now;
                _scrollTimeMS = 0;
            }
        }




        if( gps != null ) {
            if( oMap != null ) {
                gps.setX(repositionX(oMap, map, gps.getRealX(), 0));
                gps.setY(repositionY(oMap, map, gps.getRealY(), 0));
            }

            gps.update();
        }

        if( map != null )
            map.update();

        orchestrator.update();

        // Sur une COPIE, et c'est la meme raison que pour getChibis() : une creature qui touche
        // le joueur explose dans sa propre update() et se retire de la liste. La parcourir en
        // direct levait alors une ConcurrentModificationException qui faisait perdre l'image --
        // avalee par le catch du thread de rendu, donc invisible, et c'etait exactement le
        // clignotement cherche le 08/10/2026.
        for(ChibiCharacter chibi: chibisSnapshot()) {
            if( oMap != null ) {
                chibi.setX(repositionX(oMap, map, chibi.getRealX(), chibi.getWidth()));
                chibi.setY(repositionY(oMap, map, chibi.getRealY(), chibi.getHeight()));
            }

            chibi.update();
        }


        for(Bullet bullet: this.bulletList)  {
            if( oMap != null ) {
                bullet.setX(repositionX(oMap, map, bullet.getRealX(), 0));
                bullet.setY(repositionY(oMap, map, bullet.getRealY(), 0));
            }

            bullet.update();
        }

        // Les tirs nes pendant la boucle entrent maintenant, la liste n'etant plus parcourue. Ils
        // commencent donc a la frame suivante, ce qui evite aussi qu'une balle percute a sa
        // naissance celle qui vient de la declencher.
        if( !this.bulletsEnAttente.isEmpty() ) {
            this.bulletList.addAll(this.bulletsEnAttente);
            this.bulletsEnAttente.clear();
        }

        if( !this.scorePopupsEnAttente.isEmpty() ) {
            this.scorePopups.addAll(this.scorePopupsEnAttente);
            this.scorePopupsEnAttente.clear();
        }

        if( !this.shockwavesEnAttente.isEmpty() ) {
            this.shockwaves.addAll(this.shockwavesEnAttente);
            this.shockwavesEnAttente.clear();
        }

        Iterator<Shockwave> itWave = this.shockwaves.iterator();
        while( itWave.hasNext() ) {
            if( itWave.next().update() )
                itWave.remove();
        }

        if( !this.heartsEnAttente.isEmpty() ) {
            this.hearts.addAll(this.heartsEnAttente);
            this.heartsEnAttente.clear();
        }

        Iterator<GameHeart> itHeart = this.hearts.iterator();
        while( itHeart.hasNext() ) {

            GameHeart heart = itHeart.next();

            heart.update();

            if( heart.isFinished() )
                itHeart.remove();
        }

        if( !this.itemsEnAttente.isEmpty() ) {
            this.items.addAll(this.itemsEnAttente);
            this.itemsEnAttente.clear();
        }

        Iterator<GameItem> itItem = this.items.iterator();
        while( itItem.hasNext() ) {

            GameItem item = itItem.next();

            item.update();

            if( item.isFinished() )
                itItem.remove();
        }

        if( !this.healSignsEnAttente.isEmpty() ) {
            this.healSigns.addAll(this.healSignsEnAttente);
            this.healSignsEnAttente.clear();
        }

        Iterator<HealSign> itSign = this.healSigns.iterator();
        while( itSign.hasNext() ) {
            if( itSign.next().update() )
                itSign.remove();
        }

        Iterator<ScorePopup> itPopup = this.scorePopups.iterator();
        while( itPopup.hasNext() ) {
            if( itPopup.next().update() )
                itPopup.remove();
        }

        // A bullet that has run out of range, or left the world, takes itself off the list.
        Iterator<Bullet> itBullet = this.bulletList.iterator();
        while( itBullet.hasNext() ) {
            if( itBullet.next().isFinished() )
                itBullet.remove();
        }

        for(Explosion explosion: this.explosionList)  {
            if( oMap != null ) {
                explosion.setX(repositionX(oMap, map, explosion.getRealX(), 0));
                explosion.setY(repositionY(oMap, map, explosion.getRealY(), 0));
            }

            explosion.update();
        }

        Iterator<Explosion> iterator= this.explosionList.iterator();
        while(iterator.hasNext())  {
            Explosion explosion = iterator.next();

            if(explosion.isFinish()) {
                // If explosion finish, Remove the current element from the iterator & list.
                iterator.remove();
                continue;
            }
        }


        // #81 : les radars et les marks perso ont chacun LEUR son, donc chacun leur flux. Un radar
        // qui sonne pendant qu'une mark sonne aussi : on entend les deux, et on les reconnait.
        boolean radarSonne = false;
        boolean markSonne = false;

        for( GameObject go : alarmObjects ) {

            if( go.isMuted() )
                continue;

            if( go instanceof PersonalMark )
                markSonne = true;
            else
                radarSonne = true;
        }

        if( radarSonne ) {
            if( streamAlarmId == 0 )
                streamAlarmId = playSoundAlarm();
        } else if( streamAlarmId > 0 ) {
            stopSound(streamAlarmId);
            streamAlarmId = 0;
        }

        if( markSonne ) {
            if( streamMarkAlarmId == 0 )
                streamMarkAlarmId = playSoundMarkAlarm();
        } else if( streamMarkAlarmId > 0 ) {
            stopSound(streamMarkAlarmId);
            streamMarkAlarmId = 0;
        }

        updateZoneAlarm();   // #25 : alarme de sortie de zone

        updateVisualAlert(now);   // #40 : condition + pulsation de la bande peripherique

        _lastUpdateNanoTime = now;
    }

    // #40 : la bande peripherique est allumee des qu'UNE condition d'alarme est vraie, quel que soit
    // l'etat "mute" du son (sortie de zone #25, ou au moins un objet radar/mark en portee #7).
    // Pulsation triangulaire identique au rond radar : demi-cycle 700 ms, sens qui bascule.
    private void updateVisualAlert(long now) {
        boolean alertOn = _zoneOutside || !alarmObjects.isEmpty();

        if( alertOn && !_visualAlertOn ) {   // (re)demarrage : pulsation et clignotement repartent proprement
            _alertPulseNanoTime = now;
            _alertPulseDir = true;
            _alertBlinkNanoTime = now;
            _alertBlinkWhite = false;        // demarre sur le rouge
        }
        _visualAlertOn = alertOn;

        if( !_visualAlertOn )
            return;

        if( _alertPulseNanoTime == -1 )
            _alertPulseNanoTime = now;

        int deltaWarn = (int) ((now - _alertPulseNanoTime) / 1000000);
        if( deltaWarn > ALERT_HALF_CYCLE_MS ) {
            _alertPulseNanoTime = now;
            deltaWarn %= ALERT_HALF_CYCLE_MS;
            _alertPulseDir = !_alertPulseDir;
        }

        float frac = (float) deltaWarn / ALERT_HALF_CYCLE_MS;   // 0 -> 1 sur le demi-cycle
        float t = _alertPulseDir ? frac : (1f - frac);         // triangle 0 -> 1 -> 0
        _alertBandFrac = ALERT_BAND_MIN + (ALERT_BAND_MAX - ALERT_BAND_MIN) * t;

        // Clignotement rouge <-> blanc de la bande a arretes brutes (rythme propre, plus rapide).
        if( _alertBlinkNanoTime == -1 )
            _alertBlinkNanoTime = now;
        int deltaBlink = (int) ((now - _alertBlinkNanoTime) / 1000000);
        if( deltaBlink > ALERT_BLINK_MS ) {
            _alertBlinkNanoTime = now;
            _alertBlinkWhite = !_alertBlinkWhite;
        }
    }



    @Override
    public void draw(Canvas canvas)  {

        synchronized(this) {
            super.draw(canvas);

            // Default Background Color
            MapArea.TypeArea ocean = MapArea.TypeArea.getTypeArea("water", false);
            canvas.drawColor(ocean.getPaintLayer1().getColor());

            if (map != null) {
                //for( int layer = 0; layer<2; layer++)
                //    map.draw(canvas, layer);
                map.draw(canvas);
            }

            SurveillanceZone zone = _am.getZone();
            if( zone != null )
                zone.draw(canvas, 0);

            // The play areas, under the creatures: they are the ground those are placed on, not
            // something they walk in front of. Every creature has one and they all come through
            // here, but only the one the game is watching draws anything.
            for (ChibiCharacter chibi : chibiList) {
                PlayArea playArea = chibi.getPlayArea();
                playArea.draw(canvas, 0);
            }

            orchestrator.draw(canvas, 0);

            // Under the creatures: the ring is there to show them going down, not to cover them.
            for (Shockwave wave : this.shockwaves)
                wave.draw(canvas);

            // And under them too: a heart on the ground is on the ground.
            for (GameHeart heart : this.hearts) {
                heart.draw(canvas, 0);
                heart.draw(canvas, 1);
            }

            // So is a mine. Under the creatures as well, so that what walks into one is seen to
            // walk OVER it, which is the whole of what a thing on the floor looks like.
            for (GameItem item : this.items) {
                item.draw(canvas, 0);
                item.draw(canvas, 1);
            }

            for (ChibiCharacter chibi : chibiList) {

                if (chibi.isVisible())
                    chibi.draw(canvas, 0);

            }

            if ((gps != null) ) //&& gps.isVisible())
                gps.draw(canvas, 0);

            for (Bullet bullet : this.bulletList) {
                if (bullet.isVisible())
                    bullet.draw(canvas, 0);
            }

            for (Explosion explosion : this.explosionList) {
                if (explosion.isVisible())
                    explosion.draw(canvas, 0);
            }

            // Over the creatures and the shots, under the banners: a figure hidden behind an
            // explosion is a figure nobody reads.
            for (ScorePopup popup : this.scorePopups)
                popup.draw(canvas, this);

            for (HealSign sign : this.healSigns)
                sign.draw(canvas);

            if (_visualAlertOn)
                drawPeripheralAlert(canvas);   // #40 : bande rouge pulsante par-dessus tout

            drawOffscreenAlarmArrows(canvas);   // #60 : fleches de bord vers les marks en alarme hors champ

            drawGameAnnounce(canvas);
            drawCornerCountdown(canvas);

            // L'ecran d'attente ne se retire pas sur la seule existence d'un chunk : a cet instant
            // il n'est pas encore la cible du rendu et l'on verrait la mer vide. C'est ici, une
            // image reellement tracee, qu'on le signale. AppManager juge du reste et rend vrai
            // quand le retrait est acquis, ce qui met fin a l'appel.
            if( _splashEnAttente && (map != null) ) {
                _splashEnAttente = !_am.splashMapDrawn(map);
            }
        }
    }

    // #40 : bande rouge pulsante sur le pourtour de l'ecran (espace ecran, pas espace carte).
    // Cadre dessine EN UNE SEULE PASSE : rectangle plein-ecran MOINS un rectangle interieur arrondi
    // (regle even-odd). Chaque pixel n'est peint qu'une fois -> pas d'accumulation d'alpha dans les
    // coins ; bord interieur arrondi ; epaisseur "band" identique sur les 4 cotes. Le flou (BlurMaskFilter)
    // fait le fondu vers la carte. Meme rouge/alpha 64 que le rond radar pour rester coherent.
    private void drawPeripheralAlert(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if( w <= 0 || h <= 0 )
            return;

        float band = Math.min(w, h) * _alertBandFrac;   // meme epaisseur sur les 4 bords
        float radius = band;                             // arrondi du bord interieur

        _alertInner.set(band, band, w - band, h - band);
        _alertPath.reset();
        _alertPath.setFillType(Path.FillType.EVEN_ODD);
        _alertPath.addRect(0, 0, w, h, Path.Direction.CW);
        if( _alertInner.right > _alertInner.left && _alertInner.bottom > _alertInner.top )
            _alertPath.addRoundRect(_alertInner, radius, radius, Path.Direction.CW);

        _alertPaint.setAntiAlias(true);
        _alertPaint.setStyle(Paint.Style.FILL);
        _alertPaint.setColor(Color.argb(ALERT_ALPHA, 255, 0, 0));
        _alertPaint.setMaskFilter(new BlurMaskFilter(Math.max(1f, band * 0.5f), BlurMaskFilter.Blur.NORMAL));

        canvas.drawPath(_alertPath, _alertPaint);

        _alertPaint.setMaskFilter(null);

        // Bande supplementaire nette (pas de flou), epaisseur fixe, coins interieurs arrondis, qui
        // clignote rouge <-> blanc pour rendre l'alerte plus agressive. Dessinee PAR-DESSUS, en une
        // seule passe (even-odd) pour ne pas cumuler l'alpha dans les coins.
        float hard = Math.min(w, h) * ALERT_HARD_FRAC;
        float hardRadius = Math.min(w, h) * ALERT_HARD_RADIUS_FRAC;
        _alertInner.set(hard, hard, w - hard, h - hard);
        _alertPath.reset();
        _alertPath.setFillType(Path.FillType.EVEN_ODD);
        _alertPath.addRect(0, 0, w, h, Path.Direction.CW);
        if( _alertInner.right > _alertInner.left && _alertInner.bottom > _alertInner.top )
            _alertPath.addRoundRect(_alertInner, hardRadius, hardRadius, Path.Direction.CW);   // coins arrondis

        _alertPaint.setAntiAlias(true);   // arrondi lisse (les bords droits restent nets)
        if( _alertBlinkWhite )
            _alertPaint.setColor(Color.argb(ALERT_WHITE_ALPHA, 255, 255, 255));
        else
            _alertPaint.setColor(Color.argb(ALERT_ALPHA, 255, 0, 0));
        canvas.drawPath(_alertPath, _alertPaint);
    }


    // #60 : fleches de bord vers les marks en alarme sorties du champ. Espace ecran, par-dessus tout.
    // Une fleche par mark, sauf celles qui tombent au meme endroit du bord : elles fusionnent en une
    // seule fleche qui empile leurs distances, LA PLUS PROCHE EN TETE (c'est celle qui compte). Le
    // clignotement rouge/blanc reprend _alertBlinkWhite, donc en phase exacte avec la bande #40.
    // Announce image asked for by AppManager: 1 = fight, anything else = game over. The image is
    // decoded on first use and kept, the sound plays once (consumeAnnounceSound) and replaces any
    // announce sound still running, and the last 400 ms fade the image out.
    // Hauteur du chiffre du compte a rebours, en PIXELS comme les bandeaux et pour la meme
    // raison : une fraction de l'ecran changerait de taille a la rotation, alors que le "Fight!"
    // qui prend la suite, lui, n'en change pas.
    //
    // Agrandi jusqu'a une fois et demie sa taille native, et c'est la seule image du jeu a laquelle
    // on l'autorise : les chiffres n'existent qu'en 122 px dans la planche d'origine, et un
    // compte a rebours a besoin d'etre lu d'un coup d'oeil. Ce sont des degrades lisses a gros
    // contour, pas du pixel art, donc l'agrandissement ne se voit pas. Le vrai remede, le jour ou
    // il faudra plus grand, est une planche de chiffres dessinee plus grande.
    private static final float COUNTDOWN_HEIGHT_PX = 180;
    private static final float COUNTDOWN_MAX_SCALE = 1.5f;

    /**
     * Le 3, le 2, le 1 d'un appui du coin qui va lancer une partie.
     *
     * Au meme endroit que le "Fight!" qui prend la suite, ce qui en fait un seul geste. Sans
     * fondu en revanche : un chiffre qui s'efface avant le suivant donne un compte mou, alors
     * qu'un compte a rebours doit claquer d'un chiffre a l'autre.
     *
     * Rien a retenir ici : AppManager dit combien de temps il reste, le chiffre et son alpha s'en
     * deduisent. Le doigt qui se leve remet ce temps a zero, et il n'y a rien a effacer.
     */
    private void drawCornerCountdown(Canvas canvas) {

        long left = _am.getCornerHoldRemainingMs();

        if( left <= 0 )
            return;

        int digit = (int) ((left - 1) / 1000) + 1;

        if( (digit < 0) || (digit > 9) )
            return;

        Bitmap image = _egDigits.get(digit);

        if( image == null ) {
            image = BitmapFactory.decodeResource(getResources(), RAW_LEVEL_DIGITS[digit]);
            _egDigits.put(digit, image);
        }

        if( image == null )
            return;

        int w = getWidth();
        int h = getHeight();

        if( (w <= 0) || (h <= 0) )
            return;

        float dh = Math.min(COUNTDOWN_HEIGHT_PX, h * ANNOUNCE_MAX_FRAC);
        dh = Math.min(dh, image.getHeight() * COUNTDOWN_MAX_SCALE);

        float dw = image.getWidth() * dh / image.getHeight();

        float cy = h * ANNOUNCE_CENTER_Y;

        _egSrc.set(0, 0, image.getWidth(), image.getHeight());
        _egDst.set((w - dw) / 2, cy - (dh / 2), (w + dw) / 2, cy + (dh / 2));
        _egPaint.setAlpha(255);

        canvas.drawBitmap(image, _egSrc, _egDst, _egPaint);
    }

    private void drawGameAnnounce(Canvas canvas) {
        int announce = _am.getAnnounce();

        if( (announce <= 0) || (announce >= ANNOUNCE_IMAGE.length) )
            return;

        Bitmap image;

        if( (announce == AppManager.ANNOUNCE_LEVEL_UP)
         || (announce == AppManager.ANNOUNCE_LEVEL_UP_BOSS) ) {

            // Composed, not decoded: the number changes, so the banner is built when the level
            // does -- see prepareLevelBanner().
            image = _egLevel;
        }
        else {

            int imageRes = ANNOUNCE_IMAGE[announce];

            // Nothing drawn for an announcement whose image has not been made yet, and nothing
            // said either: it goes by in silence rather than borrowing somebody else's face.
            if( imageRes == 0 )
                return;

            image = _egImages.get(announce);

            if( image == null ) {
                image = BitmapFactory.decodeResource(getResources(), imageRes);
                _egImages.put(announce, image);
            }
        }

        if( image == null )
            return;

        if( _am.consumeAnnounceSound() && soundPoolLoaded ) {

            // Whatever asked for it, an announcement owns the floor until it has finished
            // speaking: that is what stops a creature crying out over the "Fight!". For as long as
            // it SPEAKS -- the fade of the image that follows is not speech.
            mayStartAnnouncedAction(announceFloorMs(announce));

            int sound;

            if( announce == AppManager.ANNOUNCE_FIGHT ) {
                sound = soundIdFight;
            }
            else if( announce == AppManager.ANNOUNCE_GAME_OVER ) {
                // La fin de partie peut avoir sa propre voix : tuee par la poupee, c'est elle qui
                // l'annonce. Zero rend le son du jeu.
                int demande = _am.getGameOverSoundRes();

                sound = (demande == 0) ? soundIdGameOver : sonDe(demande);
            }
            else {
                sound = sonDe(ANNOUNCE_VOICE[announce]);
            }

            if( _egStreamId != 0 ) {
                soundPool.stop(_egStreamId);
                _egStreamId = 0;
            }

            if( sound != 0 )
                _egStreamId = soundPool.play(sound, 1f, 1f, 2, 0, 1f);
        }

        int w = getWidth();
        int h = getHeight();

        if( (w <= 0) || (h <= 0) )
            return;

        float dw = Math.min(ANNOUNCE_WIDTH_PX, w * ANNOUNCE_MAX_FRAC);
        dw = Math.min(dw, image.getWidth());
        float dh = image.getHeight() * dw / image.getWidth();

        long remaining = _am.getAnnounceRemainingMs();
        int alpha = (remaining >= ANNOUNCE_FADE_MS) ? 255 : (int)(255 * remaining / ANNOUNCE_FADE_MS);

        _egSrc.set(0, 0, image.getWidth(), image.getHeight());
        float cy = h * ANNOUNCE_CENTER_Y;

        _egDst.set((w - dw) / 2, cy - (dh / 2), (w + dw) / 2, cy + (dh / 2));
        _egPaint.setAlpha(alpha);

        canvas.drawBitmap(image, _egSrc, _egDst, _egPaint);
    }

    private void drawOffscreenAlarmArrows(Canvas canvas) {

        int w = getWidth();
        int h = getHeight();

        if( (w <= 0) || (h <= 0) || alarmObjects.isEmpty() )
            return;

        float density = getResources().getDisplayMetrics().density;
        float cx = w / 2f;
        float cy = h / 2f;
        float inset = ARROW_INSET_DP * density;


        // 1. Une entree par mark en alarme HORS champ : point de sortie sur le bord (rayon centre
        //    ecran -> mark), direction, et distance GPS reelle (pas une mesure en pixels).
        int count = 0;
        _arrowMarks.clear();

        for( GameObject go : alarmObjects ) {

            if( !(go instanceof MapPoint) )
                continue;

            MapPoint mark = (MapPoint) go;

            float sx = getCanvasX(mark.getAnchorOriginX());
            float sy = getCanvasY(mark.getAnchorOriginY());

            if( (sx >= 0) && (sx <= w) && (sy >= 0) && (sy <= h) )
                continue;   // dans le champ : le rond rouge pulsant de la mark suffit

            double dx = sx - cx;
            double dy = sy - cy;
            double len = Math.hypot(dx, dy);

            if( len < 1e-3 )
                continue;

            // Intersection du rayon avec le rectangle ecran rentre de `inset` : le plus petit facteur
            // qui amene sur un bord.
            double t = Double.MAX_VALUE;
            if( Math.abs(dx) > 1e-6 )
                t = Math.min(t, (cx - inset) / Math.abs(dx));
            if( Math.abs(dy) > 1e-6 )
                t = Math.min(t, (cy - inset) / Math.abs(dy));

            double[] slot = arrowSlot(count);
            slot[0] = cx + dx * t;
            slot[1] = cy + dy * t;
            slot[2] = dx / len;
            slot[3] = dy / len;
            slot[4] = _am.getKmFrom(mark.getLongitude(), mark.getLatitude(), mark.getAltitude());
            slot[6] = _arrowMarks.size();   // la mark elle-meme, pour son libelle (vitesse limite)
            _arrowMarks.add(mark);
            count++;
        }

        if( count == 0 )
            return;

        // 2. Tri par distance croissante (insertion : on en a une poignee).
        for( int i = 1; i < count; i++ ) {

            double[] cur = _arrowSlots.get(i);
            int j = i - 1;

            while( (j >= 0) && (_arrowSlots.get(j)[4] > cur[4]) ) {
                _arrowSlots.set(j + 1, _arrowSlots.get(j));
                j--;
            }

            _arrowSlots.set(j + 1, cur);
        }

        // 3. Fusion des fleches qui se chevauchent : la tete de groupe est la plus proche, c'est elle
        //    qui porte la fleche et le texte ; les autres ne sont qu'un compteur.
        float groupR = ARROW_GROUP_DP * density;

        for( int i = 0; i < count; i++ ) {

            double[] head = _arrowSlots.get(i);
            if( head[5] != 0 )
                continue;

            int hidden = 0;

            for( int j = i + 1; j < count; j++ ) {

                double[] other = _arrowSlots.get(j);

                if( other[5] != 0 )
                    continue;

                if( Math.hypot(other[0] - head[0], other[1] - head[1]) > groupR )
                    continue;

                other[5] = 1;   // absorbee par ce groupe
                hidden++;
            }

            head[5] = 2;        // tete de groupe
            head[7] = hidden;
        }

        // 4. Les fleches.
        for( int i = 0; i < count; i++ ) {

            double[] head = _arrowSlots.get(i);
            if( head[5] != 2 )
                continue;

            drawArrowShape(canvas, head, density);
        }

        // 5. Mesure de chaque bloc de texte (le texte lui-meme est refait au dessin : _arrowLines est
        //    un tampon unique, reutilise d'un bloc a l'autre).
        for( int i = 0; i < count; i++ ) {

            double[] head = _arrowSlots.get(i);
            if( head[5] != 2 )
                continue;

            measureArrowBlock(head, addArrowLines(head, 0), (int) head[7], density);
        }

        // 6. Rayon commun des disques : le plus encombrant des blocs mesures.
        _arrowBlockRadius = 0f;

        for( int i = 0; i < count; i++ ) {

            double[] head = _arrowSlots.get(i);
            if( head[5] != 2 )
                continue;

            _arrowBlockRadius = Math.max(_arrowBlockRadius, (float) Math.hypot(head[8], head[9]) / 2f);
        }

        for( int i = 0; i < count; i++ ) {

            double[] head = _arrowSlots.get(i);
            if( head[5] != 2 )
                continue;

            head[11] = _arrowBlockRadius + ARROW_TEXT_GAP_DP * density;   // cercle de placement nominal
        }

        // 7. Placement de TOUS les blocs a la fois.
        solveArrowBlocks(count, density, w, h);

        if( DebugRadarPos )
            drawArrowDebugGeometry(canvas, count, density, w, h);

        // 8. Dessin.
        for( int i = 0; i < count; i++ ) {

            double[] head = _arrowSlots.get(i);
            if( head[5] != 2 )
                continue;

            drawArrowBlock(canvas, head, addArrowLines(head, 0), (int) head[7], density);
        }
    }

    // #60 : lignes de texte d'une mark du groupe : ce qui l'identifie, et en dessous sa distance.
    // Le titre ne PORTE une vitesse que pour un radar de vitesse (tag OSM maxspeed) : un radar de feu
    // rouge ou un radar de troncon ont un titre quelconque, et on n'affiche donc le panneau que si le
    // titre se lit comme une limitation plausible.
    // #7 : une mark PERSO, elle, n'a jamais de vitesse -- son titre est le nom saisi par
    // l'utilisateur. C'est ce nom qu'on montre, dans une pastille comme celle de la distance. Sans
    // titre (le champ est libre), la fleche ne porte que la distance, comme un radar de feu rouge.
    private int addArrowLines(double[] arrow, int lines) {

        if( lines >= _arrowLines.length )
            return lines;

        MapPoint mark = _arrowMarks.get((int) arrow[6]);
        String title = mark.getTitle();

        if( mark instanceof PersonalMark ) {

            // Le panneau est la meme sans nom : c'est lui qui dit "mark perso en alarme", le nom ne
            // fait que preciser laquelle.
            _arrowLines[lines] = (title == null) ? "" : title.trim();
            _arrowLineKind[lines] = ARROW_LINE_NAME;
            lines++;

        } else if( speedFromTitle(title) > 10 ) {
            _arrowLines[lines] = title.trim();
            _arrowLineKind[lines] = ARROW_LINE_SPEED;
            lines++;
        }

        if( lines < _arrowLines.length ) {
            _arrowLines[lines] = PersonalMark.formatDistance(arrow[4], 3);
            _arrowLineKind[lines] = ARROW_LINE_DIST;
            lines++;
        }

        return lines;
    }

    // #60 : pool de descripteurs de fleche, reutilise d'une frame a l'autre pour ne rien allouer dans
    // draw() :
    //   0,1   pointe (ecran)          5    etat : 0 libre, 1 absorbee par un groupe, 2 tete de groupe
    //   2,3   direction vers la mark  6    index de la mark dans _arrowMarks
    //   4     distance en km          7    nombre de marks absorbees par le groupe
    //   8,9   taille du bloc de texte 10   angle ideal (prolongement de la queue)
    //   11    rayon du cercle de placement ("cercle bleu"), elargi au besoin
    //   12    angle retenu sur ce cercle   13,14  centre du bloc qui en decoule
    //   15,16 position optimale (dot marron)  17,18 position de repli tangente (dot noir)
    //   19    index du partenaire de repli (+1), 0 si le bloc reste sur sa position optimale
    //   20,21 position de repli apres deplacement du cercle (dot gris)   22  1 quand c'est le cas
    //   23,24 centre du cercle deplace (dot bleu ciel)
    private double[] arrowSlot(int index) {

        while( _arrowSlots.size() <= index )
            _arrowSlots.add(new double[25]);

        double[] slot = _arrowSlots.get(index);
        slot[5] = 0;   // pas encore absorbee par un groupe

        return slot;
    }

    // #60 : bout de la queue d'une fleche, centre de son cercle de placement.
    private float arrowEndX(double[] arrow, float density) {
        return (float) (arrow[0] - arrow[2] * ((ARROW_LEN_DP + ARROW_SHAFT_DP) * density));
    }

    private float arrowEndY(double[] arrow, float density) {
        return (float) (arrow[1] - arrow[3] * ((ARROW_LEN_DP + ARROW_SHAFT_DP) * density));
    }

    // #60 : rayon du disque qui enveloppe un bloc de texte ("cercle vert"). Il est COMMUN a tous les
    // blocs de la frame : pris sur le plus encombrant, il en enveloppe donc forcement chacun. Des
    // disques de meme taille rendent la separation de deux blocs exactement calculable (voir
    // separateArrowPair), et le contenu se centre dedans.
    private float arrowBlockRadius(double[] arrow) {
        return _arrowBlockRadius;
    }

    // #60 : encombrement d'un bloc (panneau de vitesse eventuel + pastille de distance), et valeurs
    // de depart de son placement : angle ideal dans le prolongement de la queue, cercle de placement
    // au rayon nominal.
    private void measureArrowBlock(double[] arrow, int lines, int hidden, float density) {

        float sign = ARROW_SIGN_DP * density;
        float distSize = ARROW_TEXT_DP * density;
        float padX = ARROW_PAD_X_DP * density;
        float padY = ARROW_PAD_Y_DP * density;
        float pillH = distSize * 1.1f + 2f * padY;

        _arrowTextPaint.setAntiAlias(true);
        _arrowTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
        _arrowTextPaint.setTextAlign(Paint.Align.CENTER);
        _arrowTextPaint.setStyle(Paint.Style.FILL);
        _arrowTextPaint.setTextSize(distSize);

        float blockW = 0f;
        float blockH = 0f;

        for( int i = 0; i < lines; i++ ) {

            if( _arrowLineKind[i] == ARROW_LINE_SPEED ) {
                blockW = Math.max(blockW, sign);
                blockH += sign + padY;
            } else if( _arrowLineKind[i] == ARROW_LINE_NAME ) {
                blockW = Math.max(blockW, sign);   // #7 : le carre d'un panneau de vitesse, pas un pixel de plus
                blockH += sign + padY;
            } else {
                blockW = Math.max(blockW, _arrowTextPaint.measureText(arrowDistanceText(i, hidden)) + 2f * padX);
                blockH += pillH + padY;
            }
        }

        arrow[8] = blockW;
        arrow[9] = blockH;
        arrow[10] = Math.atan2(-arrow[3], -arrow[2]);                          // prolongement de la queue
        arrow[11] = Math.hypot(blockW, blockH) / 2f + ARROW_TEXT_GAP_DP * density;
        arrow[12] = arrow[10];
    }

    // #60 : le centre du bloc decoule de l'angle retenu : il est SUR le cercle de placement.
    private void applyArrowAngle(double[] arrow, float density) {

        float ex = arrowEndX(arrow, density);
        float ey = arrowEndY(arrow, density);
        float radius = (float) arrow[11];

        arrow[13] = ex + radius * Math.cos(arrow[12]);
        arrow[14] = ey + radius * Math.sin(arrow[12]);
    }

    // #60 : placement, version en cours de mise au point. La droite qui porte la fleche (elle passe
    // par la pointe ET par le bout de queue) coupe le cercle de placement en deux points, opposes l'un
    // a l'autre ; le centre du bloc est celui des deux qui est le plus proche du centre de l'ecran.
    // Aucune autre contrainte pour l'instant : ni anti-chevauchement, ni maintien dans l'ecran.
    private void solveArrowBlocks(int count, float density, int w, int h) {

        float screenX = w / 2f;
        float screenY = h / 2f;

        for( int i = 0; i < count; i++ ) {

            double[] a = _arrowSlots.get(i);
            if( a[5] != 2 )
                continue;

            float ex = arrowEndX(a, density);
            float ey = arrowEndY(a, density);
            float radius = (float) a[11];

            float ux = (float) -a[2];   // de la pointe vers la queue
            float uy = (float) -a[3];

            float c1x = ex + radius * ux;
            float c1y = ey + radius * uy;
            float c2x = ex - radius * ux;
            float c2y = ey - radius * uy;

            boolean firstIsCloser = Math.hypot(c1x - screenX, c1y - screenY)
                                 <= Math.hypot(c2x - screenX, c2y - screenY);

            a[12] = firstIsCloser ? Math.atan2(uy, ux) : Math.atan2(-uy, -ux);

            applyArrowAngle(a, density);

            a[15] = a[13];   // position optimale, conservee pour la construction et le debug
            a[16] = a[14];
            a[19] = 0;
            a[22] = 0;
        }

        // Deux blocs qui se chevauchent a leur position optimale : on les rend tangents sans les sortir
        // de leur cercle de placement. Le cercle de meme rayon centre au milieu du segment qui joint
        // les deux positions optimales coupe chaque cercle de placement en deux points ; la paire la
        // plus eloignee est diametralement opposee sur ce cercle, donc distante de deux rayons : les
        // deux blocs y sont exactement tangents.
        //
        // A trois marks ou plus : tout le monde part de sa position optimale, et le chevauchement se
        // juge sur ces positions-la. Une paire en conflit bascule sur son repli, et chaque bloc n'est
        // deplace qu'une fois : si le repli d'une paire vient ensuite toucher un troisieme bloc, on le
        // laisse ainsi pour l'instant, de meme que si trois blocs se chevauchent des le depart.
        for( int i = 0; i < count; i++ ) {

            double[] a = _arrowSlots.get(i);
            if( a[5] != 2 )
                continue;

            for( int j = i + 1; j < count; j++ ) {

                double[] b = _arrowSlots.get(j);
                if( b[5] != 2 )
                    continue;

                if( (a[19] != 0) || (b[19] != 0) )
                    continue;   // l'un des deux a deja ete deplace : on n'y revient pas

                float blockR = arrowBlockRadius(a);

                if( Math.hypot(a[15] - b[15], a[16] - b[16]) >= 2f * blockR )
                    continue;   // le chevauchement se juge sur les positions optimales

                float mx = (float) ((a[15] + b[15]) / 2.0);   // milieu des deux positions optimales
                float my = (float) ((a[16] + b[16]) / 2.0);

                float[] hitsA = circleIntersections(arrowEndX(a, density), arrowEndY(a, density), (float) a[11], mx, my, blockR);
                float[] hitsB = circleIntersections(arrowEndX(b, density), arrowEndY(b, density), (float) b[11], mx, my, blockR);

                if( (hitsA == null) || (hitsB == null) )
                    continue;   // pas d'intersection : on garde les positions optimales

                int bestA = 0;
                int bestB = 0;
                double bestSpan = -1.0;

                for( int pa = 0; pa < 2; pa++ ) {

                    for( int pb = 0; pb < 2; pb++ ) {

                        double span = Math.hypot(hitsA[pa * 2] - hitsB[pb * 2], hitsA[pa * 2 + 1] - hitsB[pb * 2 + 1]);

                        if( span > bestSpan ) {
                            bestSpan = span;
                            bestA = pa;
                            bestB = pb;
                        }
                    }
                }

                a[17] = hitsA[bestA * 2];
                a[18] = hitsA[bestA * 2 + 1];
                a[19] = j + 1;   // partenaire de repli (+1 : 0 signifie "reste sur sa position optimale")

                b[17] = hitsB[bestB * 2];
                b[18] = hitsB[bestB * 2 + 1];
                b[19] = i + 1;

                setArrowBlockCenter(a, hitsA[bestA * 2], hitsA[bestA * 2 + 1], density);
                setArrowBlockCenter(b, hitsB[bestB * 2], hitsB[bestB * 2 + 1], density);
            }
        }

        fixArrowBlocksOnEdges(count, density, 4f * density, w, h);

        if( DebugRadarPos )
            logArrowBlocks(count, density, 4f * density, w, h);
    }

    // #60 : mesures du placement, pour verifier au chiffre plutot qu'a l'oeil ce que montre la
    // construction. Une trace toutes les deux secondes, et seulement en mise au point.
    private void logArrowBlocks(int count, float density, float margin, int w, int h) {

        long now = System.nanoTime();

        if( now - _arrowDebugLastNs <= 2000000000L )
            return;

        _arrowDebugLastNs = now;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "ecran %dx%d rayonBloc=%.0f | ", w, h, _arrowBlockRadius));

        for( int i = 0; i < count; i++ ) {

            double[] x = _arrowSlots.get(i);
            if( x[5] != 2 )
                continue;

            float ex = arrowEndX(x, density);
            float ey = arrowEndY(x, density);
            float onRing = (float) Math.hypot(x[13] - ex, x[14] - ey) - (float) x[11];

            sb.append(String.format(Locale.US, "[queue=(%.0f,%.0f) centre=(%.0f,%.0f) surCercle=%+.1f debord=%.0f%s%s] ",
                    ex, ey, x[13], x[14], onRing,
                    arrowEdgeOverflow(x, _arrowBlockRadius, margin, w, h),
                    (x[19] != 0) ? " repli" : "",
                    (x[22] != 0) ? " deplace" : ""));
        }

        for( int i = 0; i < count; i++ ) {

            double[] x = _arrowSlots.get(i);
            if( x[5] != 2 )
                continue;

            for( int j = i + 1; j < count; j++ ) {

                double[] y = _arrowSlots.get(j);
                if( y[5] != 2 )
                    continue;

                double gap = Math.hypot(x[13] - y[13], x[14] - y[14]) - (2f * _arrowBlockRadius);
                sb.append(String.format(Locale.US, "| ecart=%+.0f ", gap));
            }
        }

        GBLog.i("ARROWDBG", sb.toString());
    }

    // #60 : un disque qui deborde de l'ecran. Deux situations. Si le bloc est reste sur sa position
    // optimale, il suffit de le faire glisser sur son cercle de placement jusqu'a rentrer. S'il fait
    // partie d'une paire deja basculee sur ses points de repli, on ne touche pas aux points eux-memes
    // mais on DEPLACE le centre du cercle noir le long du segment qui joint les deux positions
    // optimales : les deux replis se recalculent alors depuis ce nouveau centre et restent tangents.
    private void fixArrowBlocksOnEdges(int count, float density, float margin, int w, int h) {

        for( int i = 0; i < count; i++ ) {

            double[] a = _arrowSlots.get(i);
            if( a[5] != 2 )
                continue;

            float blockR = arrowBlockRadius(a);

            if( arrowEdgeOverflow(a, blockR, margin, w, h) <= 0f )
                continue;

            if( a[19] == 0 ) {   // bloc isole : il glisse sur son propre cercle
                a[12] = projectArrowAngleOnScreen(a, (float) a[12], density, margin, w, h);
                applyArrowAngle(a, density);
                continue;
            }

            int partner = (int) a[19] - 1;

            if( (partner < 0) || (partner >= count) )
                continue;

            double[] b = _arrowSlots.get(partner);

            if( b[5] != 2 )
                continue;

            slideBlackCircle(a, b, blockR, density, margin, w, h);
        }
    }

    // #60 : de combien le disque d'un bloc sort-il de l'ecran ? 0 s'il tient entierement.
    private float arrowEdgeOverflow(double[] arrow, float blockR, float margin, int w, int h) {

        float cx = (float) arrow[13];
        float cy = (float) arrow[14];

        float over = Math.max(0f, (margin + blockR) - cx);
        over = Math.max(over, (cx + blockR) - (w - margin));
        over = Math.max(over, (margin + blockR) - cy);
        over = Math.max(over, (cy + blockR) - (h - margin));

        return over;
    }

    // #60 : deplace le centre du cercle noir sur le segment des deux positions optimales, juste assez
    // pour que le bloc `a` cesse de deborder, puis recalcule les deux positions de repli.
    private void slideBlackCircle(double[] a, double[] b, float blockR, float density, float margin, int w, int h) {

        float cx = (float) a[13];
        float cy = (float) a[14];

        // Ou devrait etre ce centre pour affleurer le bord qu'il franchit ? Son cercle de placement
        // coupe la droite de ce bord en deux points : on garde le plus proche de sa position actuelle.
        float ex = arrowEndX(a, density);
        float ey = arrowEndY(a, density);
        float radius = (float) a[11];

        float limit;
        boolean horizontal;

        if( (cy + blockR) > (h - margin) ) {
            limit = (h - margin) - blockR;
            horizontal = true;
        } else if( (cy - blockR) < margin ) {
            limit = margin + blockR;
            horizontal = true;
        } else if( (cx + blockR) > (w - margin) ) {
            limit = (w - margin) - blockR;
            horizontal = false;
        } else {
            limit = margin + blockR;
            horizontal = false;
        }

        float delta = horizontal ? (limit - ey) : (limit - ex);
        float side2 = radius * radius - delta * delta;

        if( side2 < 0f )
            return;   // ce cercle de placement n'atteint pas ce bord : rien a faire ici

        float side = (float) Math.sqrt(side2);

        float t1x = horizontal ? (ex + side) : limit;
        float t1y = horizontal ? limit : (ey + side);
        float t2x = horizontal ? (ex - side) : limit;
        float t2y = horizontal ? limit : (ey - side);

        boolean firstCloser = Math.hypot(t1x - cx, t1y - cy) <= Math.hypot(t2x - cx, t2y - cy);
        float targetX = firstCloser ? t1x : t2x;
        float targetY = firstCloser ? t1y : t2y;

        // Le nouveau centre du cercle noir est a distance blockR de ce point ET sur le segment qui joint
        // les deux positions optimales : intersection d'un cercle et d'une droite.
        float p1x = (float) a[15];
        float p1y = (float) a[16];
        float p2x = (float) b[15];
        float p2y = (float) b[16];

        float sx = p2x - p1x;
        float sy = p2y - p1y;
        float segLen = (float) Math.hypot(sx, sy);

        if( segLen < 1e-3f )
            return;

        float ux = sx / segLen;
        float uy = sy / segLen;
        float fx = p1x - targetX;
        float fy = p1y - targetY;

        float bq = 2f * (fx * ux + fy * uy);
        float cq = (fx * fx + fy * fy) - blockR * blockR;
        float disc = bq * bq - 4f * cq;

        if( disc < 0f )
            return;   // aucun point du segment n'est a la bonne distance

        float rootDisc = (float) Math.sqrt(disc);
        float k1 = (-bq + rootDisc) / 2f;
        float k2 = (-bq - rootDisc) / 2f;

        float mOldX = (p1x + p2x) / 2f;
        float mOldY = (p1y + p2y) / 2f;
        float kOld = (mOldX - p1x) * ux + (mOldY - p1y) * uy;

        float k = pickSlide(k1, k2, kOld, segLen);

        float mx = p1x + ux * k;
        float my = p1y + uy * k;

        // Les deux replis se recalculent depuis ce centre deplace.
        float[] hitsA = circleIntersections(ex, ey, radius, mx, my, blockR);
        float[] hitsB = circleIntersections(arrowEndX(b, density), arrowEndY(b, density), (float) b[11], mx, my, blockR);

        if( (hitsA == null) || (hitsB == null) )
            return;

        int bestA = 0;
        int bestB = 0;
        double bestSpan = -1.0;

        for( int pa = 0; pa < 2; pa++ ) {

            for( int pb = 0; pb < 2; pb++ ) {

                double span = Math.hypot(hitsA[pa * 2] - hitsB[pb * 2], hitsA[pa * 2 + 1] - hitsB[pb * 2 + 1]);

                if( span > bestSpan ) {
                    bestSpan = span;
                    bestA = pa;
                    bestB = pb;
                }
            }
        }

        a[20] = hitsA[bestA * 2];
        a[21] = hitsA[bestA * 2 + 1];
        a[22] = 1;
        a[23] = mx;
        a[24] = my;

        b[20] = hitsB[bestB * 2];
        b[21] = hitsB[bestB * 2 + 1];
        b[22] = 1;
        b[23] = mx;
        b[24] = my;

        setArrowBlockCenter(a, hitsA[bestA * 2], hitsA[bestA * 2 + 1], density);
        setArrowBlockCenter(b, hitsB[bestB * 2], hitsB[bestB * 2 + 1], density);
    }

    // #60 : des deux positions possibles du centre sur le segment, on garde celle qui y reste et qui
    // bouge le moins ; si aucune n'est dans le segment, on se contente de l'extremite la plus proche.
    private static float pickSlide(float k1, float k2, float kOld, float segLen) {

        boolean in1 = (k1 >= 0f) && (k1 <= segLen);
        boolean in2 = (k2 >= 0f) && (k2 <= segLen);

        if( in1 && in2 )
            return (Math.abs(k1 - kOld) <= Math.abs(k2 - kOld)) ? k1 : k2;

        if( in1 )
            return k1;

        if( in2 )
            return k2;

        float best = (Math.abs(k1 - kOld) <= Math.abs(k2 - kOld)) ? k1 : k2;

        return Math.max(0f, Math.min(best, segLen));
    }

    // #60 : pose le centre d'un bloc en un point donne (qui est sur son cercle de placement) et remet
    // l'angle correspondant, pour que tout le reste continue de raisonner en angle.
    private void setArrowBlockCenter(double[] arrow, float cx, float cy, float density) {

        float ex = arrowEndX(arrow, density);
        float ey = arrowEndY(arrow, density);

        arrow[12] = Math.atan2(cy - ey, cx - ex);
        arrow[13] = cx;
        arrow[14] = cy;
    }

    // #60 : ramene un angle dans le domaine ou le disque du bloc tient entierement a l'ecran. Ce
    // domaine est l'intersection du cercle de placement et du rectangle de l'ecran reduit du rayon du
    // bloc ; ses frontieres sont les angles ou le cercle coupe l'un des quatre bords, donnes par acos
    // et asin. Si l'angle demande n'y est pas, l'optimum est forcement sur une de ces frontieres : on
    // les evalue toutes et on garde la plus proche. Corriger bord par bord ne marche pas des que deux
    // bords sont violes en meme temps (coin d'ecran) : la correction oscille de l'un a l'autre.
    private float projectArrowAngleOnScreen(double[] arrow, float theta, float density, float margin, int w, int h) {

        float ex = arrowEndX(arrow, density);
        float ey = arrowEndY(arrow, density);
        float radius = (float) arrow[11];
        float blockR = arrowBlockRadius(arrow);

        float xMin = margin + blockR;
        float xMax = (w - margin) - blockR;
        float yMin = margin + blockR;
        float yMax = (h - margin) - blockR;

        if( arrowAngleFitsScreen(theta, ex, ey, radius, xMin, xMax, yMin, yMax) )
            return theta;

        float best = theta;
        float bestTurn = Float.MAX_VALUE;
        boolean found = false;

        for( int edge = 0; edge < 4; edge++ ) {

            boolean vertical = (edge < 2);
            float k;

            if( edge == 0 )      k = (xMin - ex) / radius;
            else if( edge == 1 ) k = (xMax - ex) / radius;
            else if( edge == 2 ) k = (yMin - ey) / radius;
            else                 k = (yMax - ey) / radius;

            if( (k < -1f) || (k > 1f) )
                continue;   // le cercle ne rencontre pas ce bord

            float base = vertical ? (float) Math.acos(k) : (float) Math.asin(k);

            for( int branch = 0; branch < 2; branch++ ) {

                float candidate = vertical ? ((branch == 0) ? base : -base)
                                           : ((branch == 0) ? base : (float) (Math.PI - base));

                if( !arrowAngleFitsScreen(candidate, ex, ey, radius, xMin, xMax, yMin, yMax) )
                    continue;

                float turn = Math.abs(angleDelta(candidate, theta));

                if( turn < bestTurn ) {
                    bestTurn = turn;
                    best = candidate;
                    found = true;
                }
            }
        }

        return found ? best : theta;
    }

    // #60 : le disque du bloc tient-il a l'ecran pour cet angle ? (bornes deja reduites du rayon)
    private boolean arrowAngleFitsScreen(float theta, float ex, float ey, float radius, float xMin, float xMax, float yMin, float yMax) {

        float cx = ex + radius * (float) Math.cos(theta);
        float cy = ey + radius * (float) Math.sin(theta);
        float slack = 0.5f;   // ces angles sont calcules SUR la frontiere : on tolere l'arrondi

        return (cx >= xMin - slack) && (cx <= xMax + slack) && (cy >= yMin - slack) && (cy <= yMax + slack);
    }

    // #60 : ecart angulaire signe, ramene dans [-pi, pi].
    private static float angleDelta(float a, float b) {

        float d = a - b;

        while( d > Math.PI )
            d -= 2f * (float) Math.PI;

        while( d < -Math.PI )
            d += 2f * (float) Math.PI;

        return d;
    }

    // #60 DEBUG : la construction geometrique. ORANGE, la droite qui porte chaque fleche (pointe et
    // bout de queue) ; son intersection avec le cercle de placement donne le centre du bloc. VIOLET, la
    // droite qui joint les deux centres de blocs. JAUNE, un cercle de meme diametre que ceux des blocs,
    // centre au milieu du segment qui les joint.
    private void drawArrowDebugGeometry(Canvas canvas, int count, float density, int w, int h) {

        float span = w + h;

        _arrowDebugPaint.setAntiAlias(true);
        _arrowDebugPaint.setStyle(Paint.Style.STROKE);
        _arrowDebugPaint.setStrokeWidth(2f);

        for( int i = 0; i < count; i++ ) {

            double[] x = _arrowSlots.get(i);
            if( x[5] != 2 )
                continue;

            float ex = arrowEndX(x, density);
            float ey = arrowEndY(x, density);
            float ux = (float) -x[2];
            float uy = (float) -x[3];

            _arrowDebugPaint.setColor(0xFFFF9800);   // orange : la droite de la fleche
            canvas.drawLine(ex - ux * span, ey - uy * span, ex + ux * span, ey + uy * span, _arrowDebugPaint);
        }

        for( int i = 0; i < count; i++ ) {

            double[] x = _arrowSlots.get(i);
            if( x[5] != 2 )
                continue;

            for( int j = i + 1; j < count; j++ ) {

                double[] y = _arrowSlots.get(j);
                if( y[5] != 2 )
                    continue;

                float x1 = (float) x[15];   // positions optimales : c'est d'elles que part la construction
                float y1 = (float) x[16];
                float x2 = (float) y[15];
                float y2 = (float) y[16];

                float dx = x2 - x1;
                float dy = y2 - y1;
                float len = (float) Math.hypot(dx, dy);

                if( len < 1e-3f )
                    continue;

                _arrowDebugPaint.setColor(0xFF9C27B0);   // violet : la droite des deux centres
                canvas.drawLine(x1 - dx / len * span, y1 - dy / len * span,
                                x2 + dx / len * span, y2 + dy / len * span, _arrowDebugPaint);

                float mx = (x1 + x2) / 2f;
                float my = (y1 + y2) / 2f;
                float blockR = arrowBlockRadius(x);

                _arrowDebugPaint.setColor(Color.BLACK);   // noir : cercle au milieu du segment
                canvas.drawCircle(mx, my, blockR, _arrowDebugPaint);

                _arrowDebugPaint.setStyle(Paint.Style.FILL);
                _arrowDebugPaint.setColor(Color.BLUE);    // bleu : centre de ce cercle
                canvas.drawCircle(mx, my, 14f, _arrowDebugPaint);
                _arrowDebugPaint.setStyle(Paint.Style.STROKE);

            }
        }
    }

    // #60 : les deux points ou deux cercles se coupent, ou null s'ils ne se coupent pas. Rendus sous
    // la forme { x1, y1, x2, y2 }.
    private static float[] circleIntersections(float c1x, float c1y, float r1, float c2x, float c2y, float r2) {

        float dx = c2x - c1x;
        float dy = c2y - c1y;
        float d = (float) Math.hypot(dx, dy);

        if( (d < 1e-3f) || (d > r1 + r2) || (d < Math.abs(r1 - r2)) )
            return null;

        float a = (r1 * r1 - r2 * r2 + d * d) / (2f * d);
        float h2 = r1 * r1 - a * a;

        if( h2 < 0f )
            return null;

        float h = (float) Math.sqrt(h2);
        float ux = dx / d;
        float uy = dy / d;
        float px = c1x + a * ux;
        float py = c1y + a * uy;

        return new float[] { px + h * uy, py - h * ux, px - h * uy, py + h * ux };
    }

    // #60 : la silhouette de la fleche : pointe triangulaire prolongee d'une queue, d'un seul tenant,
    // qui clignote rouge/blanc en phase avec la bande #40.
    private void drawArrowShape(Canvas canvas, double[] arrow, float density) {

        float tipX = (float) arrow[0];
        float tipY = (float) arrow[1];
        float ux = (float) arrow[2];   // vers la mark, donc vers l'exterieur de l'ecran
        float uy = (float) arrow[3];
        float ax = -ux;                // sens de la queue : vers l'interieur
        float ay = -uy;
        float px = -uy;                // perpendiculaire a l'axe de la fleche
        float py = ux;

        float len = ARROW_LEN_DP * density;
        float half = ARROW_HALF_DP * density;
        float shaft = ARROW_SHAFT_DP * density;
        float shaftHalf = ARROW_SHAFT_HALF_DP * density;

        float baseX = tipX + ax * len;
        float baseY = tipY + ay * len;
        float endX = baseX + ax * shaft;
        float endY = baseY + ay * shaft;

        _arrowPath.reset();
        _arrowPath.moveTo(tipX, tipY);
        _arrowPath.lineTo(baseX + px * half, baseY + py * half);
        _arrowPath.lineTo(baseX + px * shaftHalf, baseY + py * shaftHalf);
        _arrowPath.lineTo(endX + px * shaftHalf, endY + py * shaftHalf);
        _arrowPath.lineTo(endX - px * shaftHalf, endY - py * shaftHalf);
        _arrowPath.lineTo(baseX - px * shaftHalf, baseY - py * shaftHalf);
        _arrowPath.lineTo(baseX - px * half, baseY - py * half);
        _arrowPath.close();

        _arrowPaint.setAntiAlias(true);
        _arrowPaint.setStyle(Paint.Style.FILL);
        _arrowPaint.setColor(_alertBlinkWhite ? Color.WHITE : Color.RED);
        canvas.drawPath(_arrowPath, _arrowPaint);

        // Contour sombre : la fleche reste lisible sur une carte claire pendant le flash blanc.
        _arrowStrokePaint.setAntiAlias(true);
        _arrowStrokePaint.setStyle(Paint.Style.STROKE);
        _arrowStrokePaint.setStrokeWidth(2f * density);
        _arrowStrokePaint.setColor(Color.BLACK);
        canvas.drawPath(_arrowPath, _arrowStrokePaint);
    }

    // #60 : le bloc de texte, a la place calculee : panneau de limitation puis pastille de distance.
    private void drawArrowBlock(Canvas canvas, double[] arrow, int lines, int hidden, float density) {

        float sign = ARROW_SIGN_DP * density;
        float distSize = ARROW_TEXT_DP * density;
        float padX = ARROW_PAD_X_DP * density;
        float padY = ARROW_PAD_Y_DP * density;
        float pillH = distSize * 1.1f + 2f * padY;

        float centerX = (float) arrow[13];
        float centerY = (float) arrow[14];
        float lineTop = centerY - (float) arrow[9] / 2f;

        if( DebugRadarPos ) {

            float ex = arrowEndX(arrow, density);
            float ey = arrowEndY(arrow, density);

            _arrowDebugPaint.setAntiAlias(true);
            _arrowDebugPaint.setStyle(Paint.Style.STROKE);
            _arrowDebugPaint.setStrokeWidth(3f);

            _arrowDebugPaint.setColor(Color.BLUE);                          // cercle de placement
            canvas.drawCircle(ex, ey, (float) arrow[11], _arrowDebugPaint);

            _arrowDebugPaint.setColor(Color.GREEN);                         // disque du bloc
            canvas.drawCircle(centerX, centerY, arrowBlockRadius(arrow), _arrowDebugPaint);

            _arrowDebugPaint.setStyle(Paint.Style.FILL);
            _arrowDebugPaint.setColor(Color.MAGENTA);                       // bout de queue
            canvas.drawCircle(ex, ey, 5f, _arrowDebugPaint);

            _arrowDebugPaint.setColor(0xFF8B4513);                          // marron : position optimale
            canvas.drawCircle((float) arrow[15], (float) arrow[16], 14f, _arrowDebugPaint);

            if( arrow[19] != 0 ) {
                _arrowDebugPaint.setColor(Color.BLACK);                     // noir : repli d'origine
                canvas.drawCircle((float) arrow[17], (float) arrow[18], 14f, _arrowDebugPaint);
            }

            if( arrow[22] != 0 ) {

                _arrowDebugPaint.setColor(0xFF808080);                      // gris : repli apres deplacement
                canvas.drawCircle((float) arrow[20], (float) arrow[21], 14f, _arrowDebugPaint);

                _arrowDebugPaint.setColor(0xFF87CEEB);                      // bleu ciel : centre deplace
                canvas.drawCircle((float) arrow[23], (float) arrow[24], 14f, _arrowDebugPaint);

                _arrowDebugPaint.setStyle(Paint.Style.STROKE);
                _arrowDebugPaint.setStrokeWidth(3f);
                _arrowDebugPaint.setColor(0xFF808080);                      // le cercle deplace lui-meme
                canvas.drawCircle((float) arrow[23], (float) arrow[24], arrowBlockRadius(arrow), _arrowDebugPaint);
                _arrowDebugPaint.setStyle(Paint.Style.FILL);
            }

            return;   // en mise au point, on ne dessine que la geometrie
        }

        for( int i = 0; i < lines; i++ ) {

            if( _arrowLineKind[i] == ARROW_LINE_SPEED ) {
                drawSpeedSign(canvas, centerX, lineTop + sign / 2f, sign, _arrowLines[i], density);
                lineTop += sign + padY;
            } else if( _arrowLineKind[i] == ARROW_LINE_NAME ) {
                drawDangerSign(canvas, centerX, lineTop, sign, _arrowLines[i], density);
                lineTop += sign + padY;
            } else {
                drawDistancePill(canvas, centerX, lineTop, arrowDistanceText(i, hidden), pillH, padX, distSize, density);
                lineTop += pillH + padY;
            }
        }
    }

    // #60 : le titre lu comme une limitation de vitesse, ou 0 s'il n'en est pas une.
    private int speedFromTitle(String title) {

        if( title == null )
            return 0;

        try {
            return Integer.parseInt(title.trim());
        } catch( NumberFormatException e ) {
            return 0;
        }
    }

    // #60 : la distance affichee, suffixee du nombre de marks en alarme masquees derriere celle-ci.
    private String arrowDistanceText(int index, int hidden) {

        if( hidden <= 0 )
            return _arrowLines[index];

        return _arrowLines[index] + " (+" + hidden + ")";
    }

    // #60 : panneau de limitation de vitesse, dessine comme celui du bord de route (disque blanc,
    // anneau rouge, chiffre noir) : reconnaissable d'un coup d'oeil, et lisible sur n'importe quel
    // fond de carte. Le chiffre est reduit si besoin pour tenir dans le disque (ex. "110").
    private void drawSpeedSign(Canvas canvas, float cx, float cy, float diameter, String text, float density) {

        float radius = diameter / 2f;
        float ring = ARROW_SIGN_RING_DP * density;

        _arrowPaint.setAntiAlias(true);
        _arrowPaint.setStyle(Paint.Style.FILL);
        _arrowPaint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, radius, _arrowPaint);

        _arrowStrokePaint.setAntiAlias(true);
        _arrowStrokePaint.setStyle(Paint.Style.STROKE);
        _arrowStrokePaint.setStrokeWidth(ring);
        _arrowStrokePaint.setColor(Color.RED);
        canvas.drawCircle(cx, cy, radius - ring / 2f, _arrowStrokePaint);

        float size = ARROW_SPEED_DP * density;
        _arrowTextPaint.setTextSize(size);

        float room = diameter - 2f * ring - 6f * density;
        float textW = _arrowTextPaint.measureText(text);
        if( textW > room ) {
            size *= room / textW;
            _arrowTextPaint.setTextSize(size);
        }

        _arrowTextPaint.setColor(Color.BLACK);
        canvas.drawText(text, cx, cy + size * 0.35f, _arrowTextPaint);
    }

    // #7 : panneau de danger d'une mark perso -- le triangle rouge a point d'exclamation du code de
    // la route --, et sous lui le nom de la marque. L'ensemble tient dans le carre `size` x `size`,
    // celui-la meme qu'occupe le panneau de vitesse d'un radar : le rectangle du nom est PRIS SUR le
    // triangle, qui rapetisse d'autant, et jamais ajoute en dessous.
    //
    // Le rectangle a une largeur fixe, celle du panneau, et c'est la police qui se reduit pour tenir
    // dedans. En dessous de ARROW_NAME_MIN_DP le nom ne se lit plus : on le laisse tomber, rectangle
    // compris, et le triangle reprend toute la hauteur -- exactement le rendu d'une marque sans nom.
    private void drawDangerSign(Canvas canvas, float cx, float top, float size, String name, float density) {

        float boxH = 0f;
        float fontSize = 0f;

        if( (name != null) && (name.length() > 0) ) {

            float maxTextW = size - 2f * ARROW_PAD_X_DP * density;

            _arrowTextPaint.setAntiAlias(true);
            _arrowTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
            _arrowTextPaint.setTextAlign(Paint.Align.CENTER);
            _arrowTextPaint.setStyle(Paint.Style.FILL);

            // Le nom rentre en trois essais successifs, du plus fidele au plus court. La liste de
            // droite, elle, se contente de reduire la police puis de couper au caractere : elle rend
            // des "St. Nicol...". Ici le PREMIER MOT passe avant, il porte l'essentiel du nom.
            fontSize = fitNameSize(name, maxTextW, density);

            if( fontSize <= 0f ) {

                int cut = name.indexOf(' ');

                if( cut > 0 ) {
                    name = name.substring(0, cut) + "\u2026";
                    fontSize = fitNameSize(name, maxTextW, density);
                }
            }

            if( fontSize <= 0f ) {

                // Un seul mot, trop long meme a la plus petite taille : coupe au caractere, comme la
                // liste de droite. On garde ainsi toujours quelque chose plutot que rien.
                fontSize = ARROW_NAME_MIN_DP * density;
                _arrowTextPaint.setTextSize(fontSize);

                int keep = name.length();

                while( (keep > 1) && (_arrowTextPaint.measureText(name.substring(0, keep) + "\u2026") > maxTextW) )
                    keep--;

                if( keep > 1 )
                    name = name.substring(0, keep) + "\u2026";
                else
                    fontSize = 0f;   // meme une lettre ne tient pas -> pas de nom, pas de rectangle
            }

            if( fontSize > 0f )
                boxH = ARROW_NAME_BOX_DP * density;
        }

        // --- le triangle, dans la hauteur qui reste une fois le rectangle et son ecart preleves
        float gap = (boxH > 0f) ? ARROW_NAME_GAP_DP * density : 0f;
        float triZone = size - boxH - gap;
        float side = Math.min(size, triZone * 2f / 1.7320508f);   // equilateral inscrit dans la zone
        float triH = side * 0.8660254f;
        float apexY = top + (triZone - triH) / 2f;
        float baseY = apexY + triH;

        _arrowPath.reset();
        _arrowPath.moveTo(cx, apexY);
        _arrowPath.lineTo(cx + side / 2f, baseY);
        _arrowPath.lineTo(cx - side / 2f, baseY);
        _arrowPath.close();

        _arrowPaint.setAntiAlias(true);
        _arrowPaint.setStyle(Paint.Style.FILL);
        _arrowPaint.setColor(Color.RED);
        canvas.drawPath(_arrowPath, _arrowPaint);

        // Interieur blanc : le meme triangle reduit autour de son centre de gravite, de facon que le
        // liseré rouge garde une epaisseur constante sur les trois cotes.
        float ring = ARROW_SIGN_RING_DP * density * 0.7f;
        float inradius = side / (2f * 1.7320508f);
        float k = (inradius > ring) ? (1f - ring / inradius) : 0f;

        if( k > 0f ) {
            float gx = cx;
            float gy = apexY + triH * 2f / 3f;

            _arrowPath.reset();
            _arrowPath.moveTo(gx + (cx - gx) * k, gy + (apexY - gy) * k);
            _arrowPath.lineTo(gx + (cx + side / 2f - gx) * k, gy + (baseY - gy) * k);
            _arrowPath.lineTo(gx + (cx - side / 2f - gx) * k, gy + (baseY - gy) * k);
            _arrowPath.close();

            _arrowPaint.setColor(Color.WHITE);
            canvas.drawPath(_arrowPath, _arrowPaint);
        }

        // Le point d'exclamation : barre puis point, en noir, dans le tiers bas du triangle ou la
        // largeur disponible est la plus grande.
        _arrowPaint.setColor(Color.BLACK);
        float barW = Math.max(2f, side * 0.09f);
        float barTop = apexY + triH * 0.40f;
        float barBottom = apexY + triH * 0.70f;
        _arrowPill.set(cx - barW / 2f, barTop, cx + barW / 2f, barBottom);
        canvas.drawRoundRect(_arrowPill, barW / 2f, barW / 2f, _arrowPaint);
        canvas.drawCircle(cx, apexY + triH * 0.80f, barW * 0.62f, _arrowPaint);

        if( boxH <= 0f )
            return;

        // --- le rectangle du nom, largeur fixe, sous le triangle
        float boxTop = top + size - boxH;
        _arrowPill.set(cx - size / 2f, boxTop, cx + size / 2f, boxTop + boxH);

        float radius = ARROW_NAME_RADIUS_DP * density;

        _arrowPaint.setColor(Color.WHITE);
        canvas.drawRoundRect(_arrowPill, radius, radius, _arrowPaint);

        _arrowStrokePaint.setAntiAlias(true);
        _arrowStrokePaint.setStyle(Paint.Style.STROKE);
        _arrowStrokePaint.setStrokeWidth(2f * density);
        _arrowStrokePaint.setColor(Color.RED);
        canvas.drawRoundRect(_arrowPill, radius, radius, _arrowStrokePaint);

        _arrowTextPaint.setTextSize(fontSize);
        _arrowTextPaint.setColor(Color.BLACK);   // le rouge est deja partout : le nom se lit mieux en noir
        canvas.drawText(name, cx, boxTop + boxH - (boxH - fontSize) / 2f - fontSize * 0.15f, _arrowTextPaint);
    }

    // #7 : la plus grande taille de police, entre ARROW_NAME_MAX_DP et ARROW_NAME_MIN_DP, qui fait
    // tenir `text` dans `maxWidth`. Rend 0 si meme la plus petite ne suffit pas. Le paint est laisse
    // sur la taille rendue, prete a servir.
    private float fitNameSize(String text, float maxWidth, float density) {

        float minSize = ARROW_NAME_MIN_DP * density;

        for( float size = ARROW_NAME_MAX_DP * density; size >= minSize; size -= density ) {

            _arrowTextPaint.setTextSize(size);

            if( _arrowTextPaint.measureText(text) <= maxWidth )
                return size;
        }

        _arrowTextPaint.setTextSize(minSize);

        return (_arrowTextPaint.measureText(text) <= maxWidth) ? minSize : 0f;
    }

    // #60 : la distance, dans une pastille blanche bordee de rouge assortie au panneau.
    private void drawDistancePill(Canvas canvas, float cx, float top, String text, float pillH, float padX, float distSize, float density) {

        _arrowTextPaint.setTextSize(distSize);

        float textW = _arrowTextPaint.measureText(text);
        float pillW = textW + 2f * padX;

        _arrowPill.set(cx - pillW / 2f, top, cx + pillW / 2f, top + pillH);

        _arrowPaint.setAntiAlias(true);
        _arrowPaint.setStyle(Paint.Style.FILL);
        _arrowPaint.setColor(Color.WHITE);
        canvas.drawRoundRect(_arrowPill, pillH / 2f, pillH / 2f, _arrowPaint);

        _arrowStrokePaint.setAntiAlias(true);
        _arrowStrokePaint.setStyle(Paint.Style.STROKE);
        _arrowStrokePaint.setStrokeWidth(2f * density);
        _arrowStrokePaint.setColor(Color.RED);
        canvas.drawRoundRect(_arrowPill, pillH / 2f, pillH / 2f, _arrowStrokePaint);

        _arrowTextPaint.setColor(Color.RED);
        canvas.drawText(text, cx, top + pillH - (pillH - distSize) / 2f - distSize * 0.15f, _arrowTextPaint);
    }

    // Implements method of SurfaceHolder.Callback
    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        // #53 : centrage initial de la camera, UNIQUEMENT a la premiere creation. La surface est
        // detruite quand l'application passe en arriere-plan et recreee au retour, sur la MEME
        // instance depuis les configChanges de la #41 : recentrer a chaque fois ecrasait le pan en
        // cours et faisait sauter la carte au centre alors qu'on revenait sur une vue justement
        // figee (mode balai).
        final boolean first = !_surfaceCreatedOnce;

        if( first ) {

            // Scrollto the center
            _scrollX = (getAreaWidth() - getWidth()) / 2;
            _scrollY = (getAreaHeight() - getHeight()) / 2;
            _startScrollX = _scrollX;
            _endScrollX = _scrollX;
            _startScrollY = _scrollY;
            _endScrollY = _scrollY;

            _surfaceCreatedOnce = true;
        }

        //Bitmap chibiBitmap1 = BitmapFactory.decodeResource(this.getResources(), R.drawable.av_walk_creeper1);
        //this.chibi1 = new ChibiCharacter(this, chibiBitmap1, 100, 50);

        // #53 : on ne touche plus ni a map ni au lien chunk->surface ici. Le lien est pose par le
        // constructeur et n'a plus de raison d'etre casse : le mettre a null datait du temps ou la
        // rotation detruisait et recreait la GameSurface, alors qu'aujourd'hui c'est la meme instance
        // qui revient. En mode balai le gel de _disableScrollTime maintient blockedMap vrai, donc le
        // lien n'etait jamais retabli et plus rien n'etait dessine (tous les objets passent par
        // chunk.getGameSurface()). Quant a la cible de rendu, sa seule bascule legitime est le bloc
        // if(loadMap && !blockedMap) de update(), qui fait aussi le repositionnement du scroll et le
        // re-pointage des marks : l'affecter directement ici court-circuitait tout cela. On se
        // contente d'armer le drapeau, consomme des que le rendu n'est plus bloque.
        loadMap = true;

        if( gps != null ) {
            gps.setPosition();
        }

        // Un Thread ne se relance pas, et la Surface native est invalide entre surfaceDestroyed et
        // surfaceCreated : le thread de rendu est donc bien recree a chaque fois, l'ancien ayant ete
        // arrete et joint dans surfaceDestroyed.
        this.gameThread = new GameThread(this, holder);
        this.gameThread.setRunning(true);
        this.gameThread.start();

        GBLog.d(getClass().getName(), "surfaceCreated: " + getWidth() + "x" + getHeight()
                + ", render thread started (first=" + first + ")");
    }

    // Implements method of SurfaceHolder.Callback
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    // #41 puis #70 : la surface est ajoutee au LLvw (wrap_content) dans le GBScrollView custom,
    // qui mesure ses enfants en UNSPECIFIED -> un MATCH_PARENT y vaut 0. Elle garde donc une
    // taille explicite. Celle-ci etait recalculee ici a chaque changement de configuration, a
    // partir de la taille ECRAN : faux des que la fenetre n'occupe pas tout l'ecran. C'est
    // desormais ViewsController.ShowMainView qui la recale sur la taille reelle du GBScrollView,
    // rotation comprise, et le onSizeChanged ci-dessous conserve le pan comme avant.

    // #41 : a la rotation (configChanges), la vue est redimensionnee sans recreer
    // l'Activity ni la GameSurface. onSizeChanged donne l'ancienne (oldw/oldh) et la
    // nouvelle (w/h) taille -> on garde au centre le meme point-carte (pan conserve).
    // La zone scrollable est un carre invariant (max(w,h)*VIRTUAL_ZOOM), donc un simple
    // recentrage sur le point memorise suffit, sans recalcul d'echelle.
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        GBLog.d(getClass().getName(), "onSizeChanged: " + oldw + "x" + oldh + " -> " + w + "x" + h);

        if( oldw <= 0 || oldh <= 0 )
            return;   // premiere mise en page : surfaceCreated fait le centrage initial

        double cx = _scrollX + oldw / 2.0;   // point-carte au centre de l'ecran, avant resize
        double cy = _scrollY + oldh / 2.0;

        // #53 : les coordonnees de zone sont proportionnelles a la taille utile de la vue,
        // base = max(largeur, hauteur) : la zone scrollable vaut base * VZ. Elle est invariante a la
        // rotation, d'ou le simple report du point central ci-dessus, mais PAS quand la fenetre est
        // redimensionnee (split screen, vue pop-up, DeX), ou la base peut changer du simple au double.
        // On convertit donc le point central dans la nouvelle echelle ; a base constante le rapport
        // vaut 1 et le calcul est exactement celui d'avant. Les objets du chunk, eux, sont re-scales
        // par update() (contextScaleObjects) des que la base change.
        int oldBase = (oldw > oldh) ? oldw : oldh;
        int newBase = (w > h) ? w : h;

        if( (oldBase > 0) && (newBase != oldBase) ) {
            double ratio = (double) newBase / (double) oldBase;
            cx *= ratio;
            cy *= ratio;
        }

        double nx = cx - w / 2.0;
        double ny = cy - h / 2.0;

        // clamp aux bords (meme logique que le pan/fling)
        if( nx < 0 ) nx = 0;
        else if( nx > getAreaWidth() - w ) nx = getAreaWidth() - w - 1;
        if( ny < 0 ) ny = 0;
        else if( ny > getAreaHeight() - h ) ny = getAreaHeight() - h - 1;

        synchronized (this) {
            _scrollX = (int) nx;
            _scrollY = (int) ny;
        }
        _startScrollX = _endScrollX = _scrollX;   // couper l'inertie en cours
        _startScrollY = _endScrollY = _scrollY;
        loadMap = true;   // redessiner la carte a la nouvelle taille
    }

    // Implements method of SurfaceHolder.Callback
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

        // #53 : la boucle d'origine posait retry = false dans TOUS les cas, y compris apres une
        // InterruptedException : elle ne retentait donc jamais rien et pouvait rendre la main en
        // laissant le thread de rendu en vie, que surfaceCreated doublait ensuite d'un second thread
        // sur le meme canvas. On retente desormais reellement, mais borne a MAX_JOIN_RETRY : une
        // attente sans fin ici bloquerait le thread UI (ANR).
        boolean retry = true;
        int attempt = 0;

        while( retry ) {

            attempt++;

            try {
                this.gameThread.setRunning(false);

                // Parent thread must wait until the end of GameThread.
                this.gameThread.join();

                retry = false;   // join abouti : le thread de rendu est bien termine
                GBLog.d(getClass().getName(), "surfaceDestroyed: render thread joined (attempt " + attempt + ")");

            } catch(InterruptedException e)  {

                // Notre propre attente a ete interrompue : reboucler ne ferait que se faire
                // interrompre a nouveau. Le thread de rendu a deja recu son ordre d'arret (running
                // est volatile) et sortira de lui-meme a l'iteration suivante ; d'ici la ses dessins
                // sont sans effet, lockCanvas echouant sur une Surface morte dans son propre try.
                retry = false;
                GBLog.d(getClass().getName(), "surfaceDestroyed: join interrupted, giving up (" + e + ")");

            } catch(Exception e)  {

                // Tout autre echec : on retente, dans la limite fixee.
                retry = (attempt < MAX_JOIN_RETRY);
                GBLog.d(getClass().getName(), "surfaceDestroyed: join failed (attempt " + attempt + ", retry=" + retry + "): " + e);
            }
        }
    }
}
