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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * #81 : la bibliotheque de sons d'alarme livree avec l'application, decouverte par enumeration de
 * R.raw. Meme principe que AvatarCatalog pour les sprites : deposer un .ogg prefixe "alm_" dans
 * res/raw suffit a le faire apparaitre dans les listes de choix, sans toucher au code.
 *
 * Tous ces sons sont synthetises pour MyBuoy : aucun droit de tiers, donc rien qui empeche la
 * redistribution du depot (#22). C'est la raison d'etre du prefixe -- il marque ce qui est a nous.
 */
public final class AlarmSoundCatalog {

    /** Le prefixe qui fait qu'un fichier de res/raw est un son d'alarme proposable. */
    public static final String PREFIX = "alm_";

    /** Valeur du reglage quand l'utilisateur ne veut pas de son : l'alerte reste visuelle. */
    public static final String SILENCE = "";

    /** Nature d'une entree : ce qui distingue les trois familles de choix, et donc l'icone. */
    public static final int KIND_NONE    = 0;   // pas de son, l'alerte reste visuelle
    public static final int KIND_LIBRARY = 1;   // un son livre avec l'application
    public static final int KIND_CUSTOM  = 2;   // un fichier de l'utilisateur (pas encore propose)

    public static final class Entry {
        public final String name;    // valeur enregistree : "alm_x" pour une ressource, "usr:fichier" pour un ajout
        public final int resId;      // 0 pour un son ajoute
        public final String label;   // libelle affiche, ex. "Melody boudin"
        public final int kind;
        public final String path;    // chemin du fichier pour un son ajoute, null sinon

        Entry(String name, int resId, String label, int kind) {
            this(name, resId, label, kind, null);
        }

        Entry(String name, int resId, String label, int kind, String path) {
            this.name = name;
            this.resId = resId;
            this.label = label;
            this.kind = kind;
            this.path = path;
        }

        public String getPath() { return path; }

        /** Vrai des qu'il y a quelque chose a jouer : une ressource OU un fichier ajoute. */
        public boolean isPlayable() { return (resId != 0) || (path != null); }

        public String getName()  { return name; }
        public String getLabel() { return label; }
        public int getResId()    { return resId; }
        public int getKind()     { return kind; }

        /** L'icone de gauche, qui dit d'un coup d'oeil de quelle famille vient ce choix. */
        public int getIconRes() {
            switch( kind ) {
                case KIND_NONE:   return R.drawable.ic_snd_none;
                case KIND_CUSTOM: return R.drawable.ic_snd_custom;
                default:          return R.drawable.ic_snd_library;
            }
        }
    }

    /** Prefixe des valeurs qui designent un son ajoute par l'utilisateur. */
    public static final String USER_PREFIX = "usr:";

    private static List<Entry> _entries = null;

    private AlarmSoundCatalog() { }

    /** Les sons de la bibliotheque, tries par libelle. La premiere entree est "No sound". */
    public static synchronized List<Entry> list() {

        if( _entries != null )
            return _entries;

        List<Entry> found = new ArrayList<Entry>();

        for( Field f : R.raw.class.getFields() ) {

            if( !f.getName().startsWith(PREFIX) )
                continue;

            try {
                found.add(new Entry(f.getName(), f.getInt(null), label(f.getName()), KIND_LIBRARY));
            } catch( IllegalAccessException ignored ) {
            }
        }

        Collections.sort(found, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) { return a.label.compareTo(b.label); }
        });

        _entries = Collections.unmodifiableList(found);

        return _entries;
    }

    /**
     * Le dossier des sons ajoutes par l'utilisateur. C'est le stockage PRIVE de l'application :
     * aucune permission a demander, invisible des autres applications, conserve d'une mise a jour a
     * l'autre et efface a la desinstallation. On y COPIE le fichier choisi plutot que de garder son
     * URI : le son reste alors disponible meme si l'utilisateur deplace ou supprime l'original.
     */
    public static File userDir(Context ctx) {

        File d = new File(ctx.getFilesDir(), "alarms");

        if( !d.exists() )
            d.mkdirs();

        return d;
    }

    /**
     * Tous les sons proposables : "No sound" en tete, puis les sons livres et ceux que
     * l'utilisateur a ajoutes, MELANGES et tries par libelle. Un son ajoute n'a pas de raison de
     * finir en bas de liste : ce qui le distingue est son icone, pas sa place.
     */
    public static List<Entry> list(Context ctx) {

        List<Entry> tout = new ArrayList<Entry>(list());

        if( ctx != null ) {

            File[] fichiers = userDir(ctx).listFiles();

            if( fichiers != null ) {
                for( File f : fichiers ) {
                    if( f.isFile() )
                        tout.add(new Entry(USER_PREFIX + f.getName(), 0, label(f.getName()),
                                           KIND_CUSTOM, f.getAbsolutePath()));
                }
            }
        }

        Collections.sort(tout, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) { return a.label.compareTo(b.label); }
        });

        tout.add(0, new Entry(SILENCE, 0, "No sound", KIND_NONE));

        return tout;
    }

    public static boolean isUser(String value) {
        return (value != null) && value.startsWith(USER_PREFIX);
    }

    /** Le fichier d'un son ajoute, ou null si la valeur ne designe pas un ajout. */
    public static File userFile(Context ctx, String value) {

        if( !isUser(value) || (ctx == null) )
            return null;

        return new File(userDir(ctx), value.substring(USER_PREFIX.length()));
    }

    /**
     * Copie le contenu d'un flux dans le dossier des sons ajoutes, sous `nom`. Un nom deja pris ne
     * remplace jamais l'existant : on suffixe (2), (3)... Rend la VALEUR a enregistrer, ou null.
     */
    public static String addUserSound(Context ctx, InputStream in, String nom) {

        if( (ctx == null) || (in == null) )
            return null;

        String base = sansExtension(nom);
        String ext = nom.contains(".") ? nom.substring(nom.lastIndexOf('.')) : "";
        File cible = new File(userDir(ctx), base + ext);

        for( int n = 2; cible.exists() && (n < 100); n++ )
            cible = new File(userDir(ctx), base + " (" + n + ")" + ext);

        OutputStream out = null;

        try {
            out = new FileOutputStream(cible);
            byte[] buf = new byte[64 * 1024];
            int lu;
            while( (lu = in.read(buf)) > 0 )
                out.write(buf, 0, lu);
            out.flush();
        } catch( Exception ex ) {
            GBLog.e("AlarmSoundCatalog", "copie impossible : " + ex);
            cible.delete();
            return null;
        } finally {
            try { if( out != null ) out.close(); } catch( Exception ignored ) { }
        }

        return USER_PREFIX + cible.getName();
    }

    /** Efface un son ajoute. Sans effet sur les sons livres avec l'application. */
    public static boolean removeUserSound(Context ctx, String value) {

        File f = userFile(ctx, value);

        return (f != null) && f.exists() && f.delete();
    }

    private static String sansExtension(String nom) {

        int i = nom.lastIndexOf('.');

        return (i > 0) ? nom.substring(0, i) : nom;
    }

    /** L'identifiant de ressource d'un son, ou 0 pour "pas de son" comme pour un nom inconnu. */
    public static int resId(String name) {

        if( (name == null) || name.isEmpty() )
            return 0;

        for( Entry e : list() ) {
            if( e.name.equals(name) )
                return e.resId;
        }

        return 0;
    }

    /** Le libelle affiche pour un nom de son ; "No sound" si le son n'existe plus. */
    public static String label(String name, String siInconnu) {

        if( (name == null) || name.isEmpty() )
            return siInconnu;

        if( isUser(name) )
            return label(name.substring(USER_PREFIX.length()));

        for( Entry e : list() ) {
            if( e.name.equals(name) )
                return e.label;
        }

        return siInconnu;
    }

    /**
     * Le libelle affiche : "alm_melody_boudin" donne "Melody boudin", et "ma_sirene.mp3" donne
     * "Ma sirene". Meme traitement pour les deux sources -- prefixe et extension retires, tirets bas
     * remplaces par des espaces, premiere lettre en capitale --, sinon un son ajoute detonnerait
     * dans la liste.
     */
    private static String label(String resName) {

        String s = sansExtension(resName);

        if( s.startsWith(PREFIX) )
            s = s.substring(PREFIX.length());

        s = s.replace('_', ' ').replace('-', ' ').trim();

        while( s.contains("  ") )
            s = s.replace("  ", " ");

        if( s.isEmpty() )
            return resName;

        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
