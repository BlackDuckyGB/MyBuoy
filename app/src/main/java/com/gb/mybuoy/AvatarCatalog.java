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
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// Catalogue des sprites d'avatar GPS. Les sprites vivent dans res/drawable-v24 et sont ranges par
// categorie via un prefixe de nom de fichier (le systeme de ressources Android n'accepte pas de
// sous-dossier dans res/drawable) : av_sea_ / av_walk_ / av_ride_ / av_drive_. Le catalogue enumere
// R.drawable par reflexion, donc ajouter un avatar = deposer un PNG bien nomme, aucune ligne de code
// a toucher. minifyEnabled est a false ; une regle -keep dans proguard-rules.pro protege le jour ou
// il passerait a true.
//
// Format d'un sprite : 4 lignes (les 4 directions) et des frames CARREES, donc cote de frame =
// hauteur/4 et nombre de colonnes = largeur/(hauteur/4). En pratique 3 colonnes (l'ancien format) ou
// 4 colonnes (le nouveau, dont la derniere colonne est la pose d'arret dediee).
public class AvatarCatalog {

    public static final int CAT_SEA   = 0;   // sur l'eau
    public static final int CAT_WALK  = 1;   // terre, vitesse lente (pieton)
    public static final int CAT_RIDE  = 2;   // terre, vitesse moyenne (velo, scooter, tracteur...)
    public static final int CAT_DRIVE = 3;   // terre, vitesse rapide (voiture, moto, camion, avion...)
    public static final int CAT_COUNT = 4;

    // Nombre de lignes d'un sprite : une par direction. Fixe par le format.
    public static final int ROW_COUNT = 4;

    // Sequence d'animation de deplacement, en index de colonne : colonnes 1,2,3,2,1,2,3,2...
    public static final int[] MOVE_SEQUENCE = { 0, 1, 2, 1 };

    // Duree d'affichage d'une image de la sequence de marche.
    public static final int MOVE_STEP_MS = 250;

    // Page de configuration : duree de la pose d'arret, puis de la sequence de marche, par direction.
    private static final int DEMO_IDLE_MS = 2000;
    private static final int DEMO_MOVE_MS = 3000;

    // Seuils de vitesse terrestres (km/h) : pieton en dessous de 8, moyen de 8 a 50, rapide au-dessus.
    public static final double SPEED_SLOW_MAX = 8.0;
    public static final double SPEED_MID_MAX  = 50.0;
    // Hysteresis : il faut depasser le seuil de HYST pour changer de categorie, et redescendre de HYST
    // pour revenir. Sans ca l'avatar clignoterait d'une categorie a l'autre en roulant pile au seuil.
    public static final double SPEED_HYST     = 1.0;

    private static final String[] PREFIX = { "av_sea_", "av_walk_", "av_ride_", "av_drive_" };

    // Titre de la categorie, affiche en tete du selecteur (l'UI de l'appli est en anglais).
    private static final String[] TITLE = {
            "Water avatar", "Slow avatar", "Middle avatar", "Fast avatar"
    };

    // Choix par defaut d'une categorie, utilise tant que rien n'est enregistre. Vide pour les
    // categories sans sprite : elles retombent alors sur l'avatar pieton (cf. resolve()).
    private static final String[] DEFAULT_NAME = {
            "av_sea_boat_rhid1", "av_walk_chibi1", "", ""
    };

    // Une entree du catalogue = un sprite disponible.
    public static class Entry {
        public final String name;      // nom de ressource, ex. av_walk_chibi1
        public final int resId;
        public final String label;     // libelle affiche, ex. "Chibi1"
        public Drawable preview;       // vignette (pose d'arret, vue de face), chargee a la demande

        Entry(String name, int resId) {
            this.name = name;
            this.resId = resId;
            this.label = toLabel(name);
        }
    }

    private static List<List<Entry>> _catalog = null;

    // Enumeration des sprites d'une categorie, triee par libelle. Le scan de R.drawable n'a lieu
    // qu'une fois : la liste des ressources ne bouge pas a l'execution.
    public static synchronized List<Entry> list(int category) {
        if( _catalog == null )
            scan();

        if( (category < 0) || (category >= CAT_COUNT) )
            return new ArrayList<Entry>();

        return _catalog.get(category);
    }

    private static void scan() {
        _catalog = new ArrayList<List<Entry>>();
        for( int c = 0; c < CAT_COUNT; c++ )
            _catalog.add(new ArrayList<Entry>());

        for( Field field : R.drawable.class.getFields() ) {
            String name = field.getName();
            int category = categoryOf(name);
            if( category < 0 )
                continue;

            try {
                _catalog.get(category).add(new Entry(name, field.getInt(null)));
            } catch( IllegalAccessException ex ) {
                GBLog.e("AvatarCatalog", "cannot read R.drawable." + name, ex);
            }
        }

        Comparator<Entry> byLabel = new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        };

        for( int c = 0; c < CAT_COUNT; c++ )
            Collections.sort(_catalog.get(c), byLabel);
    }

    // Categorie portee par le prefixe du nom de fichier, ou -1 si ce n'est pas un sprite d'avatar.
    private static int categoryOf(String resourceName) {
        for( int c = 0; c < CAT_COUNT; c++ ) {
            if( resourceName.startsWith(PREFIX[c]) )
                return c;
        }
        return -1;
    }

    // Libelle affiche : prefixe de categorie retire, underscores en espaces, initiale en majuscule.
    // av_walk_chibi1 -> "Chibi1", av_ride_scooter_bob1 -> "Scooter bob1".
    private static String toLabel(String resourceName) {
        String label = resourceName;

        int category = categoryOf(resourceName);
        if( category >= 0 )
            label = resourceName.substring(PREFIX[category].length());

        label = label.replace('_', ' ').trim();

        if( label.length() == 0 )
            return resourceName;

        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }

    public static String title(int category) {
        if( (category < 0) || (category >= CAT_COUNT) )
            return "Avatar";
        return TITLE[category];
    }

    // Ressource du sprite choisi pour une categorie. Repli en cascade : le nom enregistre, sinon le
    // defaut de la categorie, sinon son premier sprite, sinon (categorie vide) l'avatar pieton.
    // walkResId est la ressource deja resolue du pieton, passee par l'appelant pour eviter la
    // recursion et garder cette classe sans etat persistant.
    public static int resolve(int category, String storedName, int walkResId) {
        int resId = resIdOf(category, storedName);

        if( resId == 0 )
            resId = resIdOf(category, DEFAULT_NAME[category]);

        if( resId == 0 ) {
            List<Entry> entries = list(category);
            if( !entries.isEmpty() )
                resId = entries.get(0).resId;
        }

        if( (resId == 0) && (category != CAT_WALK) )
            resId = walkResId;

        // Dernier filet : plus aucun sprite d'avatar dans l'appli. Sans ca le rendu dessinerait un
        // bitmap null. av_walk_chibi1 est livre avec l'appli.
        if( resId == 0 )
            resId = R.drawable.av_walk_chibi1;

        return resId;
    }

    // Ressource d'un sprite de la categorie par son nom, ou 0 s'il n'y est pas (fichier supprime,
    // renomme, ou reglage venant d'une ancienne version).
    public static int resIdOf(int category, String name) {
        if( (name == null) || (name.length() == 0) )
            return 0;

        for( Entry entry : list(category) ) {
            if( entry.name.equals(name) )
                return entry.resId;
        }

        return 0;
    }

    // Entree correspondant a une ressource, toutes categories confondues (sert au libelle affiche
    // dans la page Parameters, ou la categorie affichee peut etre un repli sur le pieton).
    public static Entry entryOf(int resId) {
        for( int c = 0; c < CAT_COUNT; c++ ) {
            for( Entry entry : list(c) ) {
                if( entry.resId == resId )
                    return entry;
            }
        }
        return null;
    }

    // Planche decodee SANS mise a l'echelle : dimensions brutes du PNG, donc cellules exactes.
    public static Bitmap decodeRaw(Resources res, int resId) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;

        return BitmapFactory.decodeResource(res, resId, options);
    }

    // Facteur d'agrandissement des sprites : les PNG sont dans drawable-v24, sans qualificatif de
    // densite, donc Android les considere en mdpi (160 dpi) et les affiche a la densite de l'ecran.
    public static float densityScale(Resources res) {
        return res.getDisplayMetrics().densityDpi / (float) DisplayMetrics.DENSITY_DEFAULT;
    }

    // Nombre de colonnes de la planche. Frames carrees + 4 lignes -> colonnes = largeur / (hauteur/4).
    public static int columnsOf(Bitmap sheet) {
        if( sheet == null )
            return 3;

        return columnsOf(sheet.getWidth(), sheet.getHeight());
    }

    public static int columnsOf(int width, int height) {
        if( (width <= 0) || (height <= 0) )
            return 3;

        int columns = Math.round(ROW_COUNT * (float) width / (float) height);
        if( columns < 1 )
            columns = 1;

        return columns;
    }

    // Colonne de la pose d'arret : la 4e (dediee) en format 4 colonnes, la 2e en format 3 colonnes.
    public static int idleColumn(int columns) {
        if( columns >= 4 )
            return 3;
        if( columns >= 2 )
            return 1;
        return 0;
    }

    // Une planche prete a l'emploi : ses frames deja decoupees et mises a l'echelle.
    public static class Sheet {
        public final Bitmap[] frames;   // indexees row * columns + col
        public final int columns;
        public final int frameWidth;    // taille affichee d'une frame (apres mise a l'echelle)
        public final int frameHeight;

        Sheet(Bitmap[] frames, int columns, int frameWidth, int frameHeight) {
            this.frames = frames;
            this.columns = columns;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
        }

        public Bitmap frame(int row, int col) {
            int index = row * columns + col;
            if( (frames == null) || (index < 0) || (index >= frames.length) )
                return null;

            return frames[index];
        }
    }

    // Decoupe la planche en frames PUIS agrandit chaque frame. L'ORDRE COMPTE : agrandir la planche
    // entiere avant de la decouper (ce que fait decodeResource, qui applique la densite en filtrant)
    // fait deborder la derniere ligne d'une cellule sur la premiere ligne de la cellule suivante ->
    // un liseré du sprite du dessus en haut de chaque frame. En decoupant d'abord, chaque cellule est
    // isolee avant tout filtrage.
    public static Sheet loadSheet(Resources res, int resId) {
        Bitmap sheet = decodeRaw(res, resId);
        if( sheet == null )
            return null;

        int columns = columnsOf(sheet);
        int frameWidth = sheet.getWidth() / columns;
        int frameHeight = sheet.getHeight() / ROW_COUNT;

        if( (frameWidth <= 0) || (frameHeight <= 0) )
            return null;

        float scale = densityScale(res);
        int scaledWidth = Math.max(1, Math.round(frameWidth * scale));
        int scaledHeight = Math.max(1, Math.round(frameHeight * scale));
        int densityDpi = res.getDisplayMetrics().densityDpi;

        Bitmap[] frames = new Bitmap[ROW_COUNT * columns];

        for( int row = 0; row < ROW_COUNT; row++ ) {
            for( int col = 0; col < columns; col++ ) {
                Bitmap frame = Bitmap.createBitmap(sheet, col * frameWidth, row * frameHeight, frameWidth, frameHeight);

                if( (scaledWidth != frameWidth) || (scaledHeight != frameHeight) )
                    frame = Bitmap.createScaledBitmap(frame, scaledWidth, scaledHeight, true);

                // La frame est DEJA a la taille de l'ecran : on la marque a la densite de l'ecran,
                // sinon le Canvas et BitmapDrawable la reagrandiraient une seconde fois.
                frame.setDensity(densityDpi);

                frames[row * columns + col] = frame;
            }
        }

        return new Sheet(frames, columns, scaledWidth, scaledHeight);
    }

    // Animation de demonstration pour la page de configuration : par direction, la pose d'arret
    // pendant DEMO_IDLE_MS puis le cycle de marche pendant DEMO_MOVE_MS, les 4 directions a la
    // suite, en boucle. AnimationDrawable accepte une duree par image, donc une simple ImageView
    // suffit : pas besoin d'une View a canvas avec son propre timer.
    public static Drawable demoAnimation(Resources res, int resId) {
        Sheet sheet = loadSheet(res, resId);
        if( sheet == null )
            return null;

        AnimationDrawable animation = new AnimationDrawable();
        animation.setOneShot(false);

        int idle = idleColumn(sheet.columns);
        int cycles = Math.max(1, DEMO_MOVE_MS / (MOVE_SEQUENCE.length * MOVE_STEP_MS));

        for( int row = 0; row < ROW_COUNT; row++ ) {
            addFrame(animation, res, sheet, row, idle, DEMO_IDLE_MS);

            for( int cycle = 0; cycle < cycles; cycle++ ) {
                for( int step : MOVE_SEQUENCE )
                    addFrame(animation, res, sheet, row, Math.min(step, sheet.columns - 1), MOVE_STEP_MS);
            }
        }

        return animation;
    }

    // #85: the STILL preview of low-resource mode. The idle pose seen from the side, a single frame
    // cut out and scaled, where demoAnimation keeps dozens of them alive for the four directions --
    // which is what makes the Parameters page stutter on a modest phone. The row is the one facing
    // right (same convention as ChibiCharacter: 0 front, 1 and 2 profile, 3 back) and the column
    // comes from idleColumn(), which adapts to the sheet's column count instead of assuming it.
    public static final int STILL_ROW = 2;

    public static Drawable stillFrame(Resources res, int resId) {
        Bitmap sheet = decodeRaw(res, resId);
        if( sheet == null )
            return null;

        int columns = columnsOf(sheet);
        int frameWidth = sheet.getWidth() / columns;
        int frameHeight = sheet.getHeight() / ROW_COUNT;

        if( (frameWidth <= 0) || (frameHeight <= 0) )
            return null;

        int col = Math.min(idleColumn(columns), columns - 1);
        int row = Math.min(STILL_ROW, ROW_COUNT - 1);

        Bitmap frame = Bitmap.createBitmap(sheet, col * frameWidth, row * frameHeight, frameWidth, frameHeight);

        float scale = densityScale(res);
        int scaledWidth = Math.max(1, Math.round(frameWidth * scale));
        int scaledHeight = Math.max(1, Math.round(frameHeight * scale));

        if( (scaledWidth != frameWidth) || (scaledHeight != frameHeight) )
            frame = Bitmap.createScaledBitmap(frame, scaledWidth, scaledHeight, true);

        // Same precaution as loadSheet: the frame is already at screen size, so it is tagged with
        // the screen density to stop BitmapDrawable from scaling it up a second time.
        frame.setDensity(res.getDisplayMetrics().densityDpi);

        return new BitmapDrawable(res, frame);
    }

    private static void addFrame(AnimationDrawable animation, Resources res, Sheet sheet, int row, int col, int durationMs) {
        Bitmap frame = sheet.frame(row, col);
        if( frame != null )
            animation.addFrame(new BitmapDrawable(res, frame), durationMs);
    }

    // Remplit les vignettes animees d'une liste d'entrees (appele a l'ouverture du selecteur). On
    // reconstruit a chaque ouverture : un AnimationDrawable porte son etat de lecture et ne doit pas
    // etre partage entre deux affichages.
    public static void loadPreviews(Resources res, List<Entry> entries) {
        loadPreviews(res, entries, false);
    }

    // #85: still = low-resource mode, one frozen image per entry instead of an animation. The picker
    // opens a whole list at once, which is where the difference shows most.
    public static void loadPreviews(Resources res, List<Entry> entries, boolean still) {
        for( Entry entry : entries )
            entry.preview = still ? stillFrame(res, entry.resId) : demoAnimation(res, entry.resId);
    }

    // Categorie terrestre pour une vitesse donnee. current = categorie affichee actuellement : on la
    // conserve tant que le seuil elargi de l'hysteresis n'est pas franchi. Toute valeur de current
    // qui n'est pas une categorie terrestre (mer, ou -1 au demarrage) tombe sur le choix direct.
    public static int landCategory(double speedKm, int current) {
        double slowUp = SPEED_SLOW_MAX + SPEED_HYST;
        double slowDown = SPEED_SLOW_MAX - SPEED_HYST;
        double midUp = SPEED_MID_MAX + SPEED_HYST;
        double midDown = SPEED_MID_MAX - SPEED_HYST;

        switch( current ) {
            case CAT_WALK:
                if( speedKm >= midUp )
                    return CAT_DRIVE;
                if( speedKm >= slowUp )
                    return CAT_RIDE;
                return CAT_WALK;

            case CAT_RIDE:
                if( speedKm >= midUp )
                    return CAT_DRIVE;
                if( speedKm < slowDown )
                    return CAT_WALK;
                return CAT_RIDE;

            case CAT_DRIVE:
                if( speedKm < slowDown )
                    return CAT_WALK;
                if( speedKm < midDown )
                    return CAT_RIDE;
                return CAT_DRIVE;

            default:
                if( speedKm < SPEED_SLOW_MAX )
                    return CAT_WALK;
                if( speedKm < SPEED_MID_MAX )
                    return CAT_RIDE;
                return CAT_DRIVE;
        }
    }
}
