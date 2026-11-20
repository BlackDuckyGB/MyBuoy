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

import android.content.Intent;
import android.net.Uri;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// #65 : lecture des URI geo: (RFC 5870) recues des autres applications. Formes rencontrees :
//   geo:48.6493,-2.0257              position seche
//   geo:48.6493,-2.0257;u=35         avec parametres RFC (incertitude, systeme geodesique)
//   geo:0,0?q=48.6493,-2.0257(Nom)   forme la plus courante des applications de partage
//   geo:0,0?q=12+rue+de+la+Plage     adresse en clair -> refusee, pas de geocodeur hors ligne
public final class GeoUriParser {

    public static final String SCHEME = "geo";

    // Position lue dans l'URI. Coordonnees TOUJOURS en degres decimaux : une URI geo: ne transporte
    // que du WGS84 decimal, le format d'affichage (DD / DM / DMS) restant interne a MyBuoy.
    public static final class Location {
        public final double latitude;
        public final double longitude;
        public final String title;   // libelle raccourci, jamais null (vide si l'URI n'en porte pas)
        public final int iconId;     // #65 : icone MyBuoy (mb-icon), -1 si l'URI n'en porte pas
        // Metres, null when the link carries none. Above sea level (our mb-ele / e), except when it
        // came as the RFC 5870 third coordinate: that one is above the WGS-84 ellipsoid.
        public final Double altitude;
        public final boolean altitudeWgs84;

        Location(double latitude, double longitude, String title, int iconId, Double altitude, boolean altitudeWgs84) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.title = title;
            this.iconId = iconId;
            this.altitude = altitude;
            this.altitudeWgs84 = altitudeWgs84;
        }
    }

    // "48.6493,-2.0257", eventuellement "48.6493,-2.0257,120" (altitude, RFC 5870), eventuellement
    // suivi de "(Libelle)".
    private static final Pattern LATLON = Pattern.compile(
            "^\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*,\\s*([+-]?\\d+(?:\\.\\d+)?)(?:\\s*,\\s*([+-]?\\d+(?:\\.\\d+)?))?\\s*(?:\\((.*)\\))?\\s*$");

    private GeoUriParser() {
    }

    // Vrai si l'intent est une position partagee, MEME illisible : distingue le lancement normal
    // (MAIN/LAUNCHER, on ne dit rien) d'une position sans coordonnees (on affiche une erreur). Deux
    // formes arrivent ici : une URI geo: venue d'une autre application, et notre propre page de
    // partage ouverte en App Link.
    public static boolean isSharedLocationIntent(Intent intent) {
        if( intent == null ) {
            return false;
        }

        Uri uri = intent.getData();
        if( uri == null ) {
            return false;
        }

        return SCHEME.equalsIgnoreCase(uri.getScheme()) || isSharePage(uri);
    }

    // #65 : l'adresse de notre page de partage, celle que le manifeste fait verifier a Android. Le
    // chemin est compare en entier : c'est le lien d'une position, pas le reste du site.
    public static boolean isSharePage(Uri uri) {
        if( uri == null ) {
            return false;
        }

        Uri page = Uri.parse(SHARE_PAGE_URL);

        return "https".equalsIgnoreCase(uri.getScheme())
                && page.getHost().equalsIgnoreCase(uri.getHost())
                && page.getPath().equals(uri.getPath());
    }

    // Position lue, ou null si l'URI ne porte aucune coordonnee exploitable.
    public static Location parse(Uri uri) {
        if( uri == null ) {
            return null;
        }

        if( isSharePage(uri) ) {
            return parseSharePage(uri);
        }

        if( !SCHEME.equalsIgnoreCase(uri.getScheme()) ) {
            return null;
        }

        // Une URI geo: est OPAQUE (pas de "//"), donc getQueryParameter() ne renvoie jamais rien :
        // on decoupe la partie specifique au schema nous-memes, en gardant les %xx pour ne decoder
        // que les valeurs.
        String ssp = uri.getEncodedSchemeSpecificPart();
        if( ssp == null ) {
            return null;
        }

        String path = ssp;
        String query = null;

        int mark = ssp.indexOf('?');
        if( mark >= 0 ) {
            path = ssp.substring(0, mark);
            query = ssp.substring(mark + 1);
        }

        // #65 : le nom et l'icone voyagent HORS coordonnees, donc de la meme facon quelle que soit la
        // forme de l'URI : on les lit une fois, et on en decore la position trouvee ensuite.
        String mbName = extension(path, query, "mb-name");
        int mbIcon = parseIcon(extension(path, query, "mb-icon"));
        Double mbEle = parseEle(extension(path, query, "mb-ele"));

        // Le q= prime : dans "geo:0,0?q=48.65,-2.03(Saint-Malo)" le couple du chemin n'est qu'un
        // remplissage conventionnel.
        Location fromQuery = parseLatLon(queryParam(query, "q"));
        if( fromQuery != null ) {
            return decorate(fromQuery, mbName, mbIcon, mbEle);
        }

        // Les parametres RFC suivent le couple, apres un ';' : "geo:48.65,-2.03;u=35".
        int semi = path.indexOf(';');
        if( semi >= 0 ) {
            path = path.substring(0, semi);
        }

        Location fromPath = parseLatLon(Uri.decode(path));
        if( fromPath == null ) {
            return null;
        }

        // Sans q= exploitable, un 0,0 est le remplissage conventionnel et non une position : il n'y
        // a rien a placer, et surement pas Null Island.
        if( fromPath.latitude == 0.0 && fromPath.longitude == 0.0 ) {
            return null;
        }

        return decorate(fromPath, mbName, mbIcon, mbEle);
    }

    /**
     * #67 : le texte libre du q=, quand il n'est PAS un couple de coordonnees. C'est le cas du
     * partage d'une adresse en clair, "geo:0,0?q=12+rue+de+la+Plage" : il n'y a rien a placer sur
     * la carte, mais il y a quelque chose a CHERCHER. Rend null des que l'URI porte une position
     * exploitable (parse() suffit alors) ou qu'elle ne porte pas de q= du tout.
     */
    public static String searchText(Uri uri) {
        if( uri == null || !SCHEME.equalsIgnoreCase(uri.getScheme()) ) {
            return null;
        }

        String ssp = uri.getEncodedSchemeSpecificPart();
        if( ssp == null ) {
            return null;
        }

        int mark = ssp.indexOf('?');
        if( mark < 0 ) {
            return null;
        }

        String q = queryParam(ssp.substring(mark + 1), "q");
        if( q == null || q.trim().isEmpty() ) {
            return null;
        }

        // Un q= qui EST une position n'est pas une recherche : parse() l'a deja traite.
        if( parseLatLon(q) != null ) {
            return null;
        }

        // Le libelle entre parentheses n'a de sens qu'a cote d'un couple de coordonnees ; sur une
        // adresse en clair il n'y en a pas, mais on l'ecarte par precaution.
        int paren = q.indexOf('(');
        if( paren > 0 ) {
            q = q.substring(0, paren);
        }

        return q.trim();
    }

    // Valeur decodee d'un parametre de la query, ou null. Le '+' vaut espace dans une query encodee,
    // ce que Uri.decode() ne fait pas.
    private static String queryParam(String query, String name) {
        if( query == null ) {
            return null;
        }

        for( String part : query.split("&") ) {
            int eq = part.indexOf('=');
            if( eq < 0 ) {
                continue;
            }
            if( name.equalsIgnoreCase(part.substring(0, eq)) ) {
                return Uri.decode(part.substring(eq + 1).replace('+', ' '));
            }
        }

        return null;
    }

    private static Location parseLatLon(String s) {
        if( s == null ) {
            return null;
        }

        Matcher m = LATLON.matcher(s);
        if( !m.matches() ) {
            return null;
        }

        double lat;
        double lon;
        Double alt = null;
        try {
            lat = Double.parseDouble(m.group(1));
            lon = Double.parseDouble(m.group(2));
            if( m.group(3) != null ) {
                alt = Double.parseDouble(m.group(3));
            }
        } catch( NumberFormatException ex ) {
            return null;
        }

        if( lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0 ) {
            return null;
        }

        return new Location(lat, lon, shortTitle(m.group(4)), -1, alt, true);
    }

    // Un libelle partage se lit presque toujours "Nom du lieu, adresse, ville" : on ne garde que ce
    // qui precede la premiere virgule. Volontairement sans longueur maximale, le nom d'un lieu
    // pouvant etre long ; le titre reste modifiable dans le popup avant l'enregistrement. Les
    // retours a la ligne sont ecrases, le titre etant dessine tel quel sur la carte.
    private static String shortTitle(String label) {
        if( label == null ) {
            return "";
        }

        String s = label;

        int comma = s.indexOf(',');
        if( comma >= 0 ) {
            s = s.substring(0, comma);
        }

        return s.replaceAll("\\s+", " ").trim();
    }

    // #65 : notre page de partage, ouverte en App Link. C'est une URI hierarchique ordinaire, donc
    // getQueryParameter() fonctionne -- ce que ne permet pas un geo:, qui est opaque. Les noms de
    // parametres sont courts (q, n, i) : ils se voient dans une conversation, autant qu'ils n'y
    // prennent pas trois lignes.
    private static Location parseSharePage(Uri uri) {
        Location loc = parseLatLon(uri.getQueryParameter("q"));

        if( loc == null ) {
            return null;
        }

        return decorate(loc, uri.getQueryParameter("n"), parseIcon(uri.getQueryParameter("i")),
                parseEle(uri.getQueryParameter("e")));
    }

    // #65 : un nom explicite l'emporte sur le libelle du "q=(...)". Il est percent-encode, donc il
    // arrive intact -- virgules et parentheses comprises -- et il n'est PAS raccourci a la premiere
    // virgule comme l'est un libelle recu d'une autre application : c'est le titre voulu par
    // l'expediteur, pas une adresse postale.
    // Our altitude (mb-ele / e, sea level) wins over an RFC third coordinate (WGS-84).
    private static Location decorate(Location loc, String name, int iconId, Double ele) {
        if( loc == null ) {
            return null;
        }

        String title = loc.title;
        if( name != null && !name.trim().isEmpty() ) {
            title = name.replaceAll("\\s+", " ").trim();
        }

        Double altitude = loc.altitude;
        boolean altitudeWgs84 = loc.altitudeWgs84;
        if( ele != null ) {
            altitude = ele;
            altitudeWgs84 = false;
        }

        return new Location(loc.latitude, loc.longitude, title, iconId, altitude, altitudeWgs84);
    }

    // Altitude in metres, null when absent, unreadable or out of any plausible ground altitude.
    private static Double parseEle(String value) {
        if( value == null ) {
            return null;
        }

        try {
            double ele = Double.parseDouble(value.trim());
            return (ele >= -500.0 && ele <= 9000.0) ? ele : null;
        } catch( NumberFormatException ex ) {
            return null;
        }
    }

    // Parametre d'extension MyBuoy, cherche d'abord dans la query puis parmi les parametres ';' de la
    // RFC : les deux ecritures sont emises, la premiere trouvee suffit.
    private static String extension(String path, String query, String name) {
        String value = queryParam(query, name);

        return (value != null) ? value : pathParam(path, name);
    }

    // Parametres ";nom=valeur" qui suivent les coordonnees (RFC 5870, section 3.3). Contrairement a
    // une query, le '+' y est un PLUS litteral et non un espace : on decode sans le traduire.
    private static String pathParam(String path, String name) {
        if( path == null ) {
            return null;
        }

        String[] parts = path.split(";");

        for( int i = 1; i < parts.length; i++ ) {
            int eq = parts[i].indexOf('=');
            if( eq < 0 ) {
                continue;
            }
            if( name.equalsIgnoreCase(parts[i].substring(0, eq)) ) {
                return Uri.decode(parts[i].substring(eq + 1));
            }
        }

        return null;
    }

    private static int parseIcon(String value) {
        if( value == null ) {
            return -1;
        }

        try {
            int id = Integer.parseInt(value.trim());

            return (id >= 0) ? id : -1;
        } catch( NumberFormatException ex ) {
            return -1;
        }
    }

    // #65 : adresse partagee par le bouton Share d'une mark. Ce n'est volontairement PAS une URI
    // geo: : une messagerie ne rend cliquables que les adresses http(s), un geo: colle dans WhatsApp
    // reste du texte mort chez le destinataire. La page renvoyee, elle, montre un apercu (Open
    // Graph), affiche la position et propose le geo: derriere un bouton -- ou il marche, puisque
    // c'est alors un clic dans un navigateur et non du texte dans un message. C'est cette page qui
    // ecrit l'URI geo: avec le nom et l'icone, et un autre MyBuoy la recoit par parse().
    //
    // En dur, et pas tire du serveur de cartes hors ligne : celui-la est un reglage que l'on change
    // pour publier sa propre liste de cartes, ce qui n'a rien a voir avec l'adresse de cette page.
    //
    // Six decimales valent environ 11 cm, largement au-dela de la precision d'un fix GPS.
    public static final String SHARE_PAGE_URL = "https://golden-block.fr/mybuoy/p.php";

    // altitude: metres above sea level, null when the mark has none (then no e=).
    public static String buildShareUrl(double latitude, double longitude, Double altitude, String name, int iconId) {
        Uri.Builder builder = Uri.parse(SHARE_PAGE_URL).buildUpon()
                .appendQueryParameter("q", String.format(Locale.US, "%.6f,%.6f", latitude, longitude));

        String label = (name == null) ? "" : name.replaceAll("\\s+", " ").trim();
        if( !label.isEmpty() ) {
            builder.appendQueryParameter("n", label);
        }

        if( iconId >= 0 ) {
            builder.appendQueryParameter("i", Integer.toString(iconId));
        }

        if( altitude != null ) {
            builder.appendQueryParameter("e", Long.toString(Math.round(altitude)));
        }

        return builder.build().toString();
    }
}
