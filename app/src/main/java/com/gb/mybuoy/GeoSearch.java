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

import com.google.gson.Gson;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Tache #67, etape 1 : la recherche d'un lieu par son nom ou son adresse, chez Nominatim.
 *
 * Ce que Nominatim n'est PAS : un moteur de POI par categorie. Il cherche du texte et classe des
 * lieux ; "les supermarches autour de moi" reste le metier d'Overpass, deja en place dans Chunk.
 *
 * POLITIQUE D'USAGE de l'instance publique (nominatim.openstreetmap.org, une seule machine payee
 * par la fondation OSM) -- la respecter ou se faire bloquer :
 *   - User-Agent identifiable, avec nom d'application et contact. Un client anonyme est bloque,
 *     un client qui usurpe le User-Agent d'un autre l'est a coup sur.
 *   - UNE requete par seconde au maximum. Cette classe ne temporise PAS : c'est le thread de fond
 *     de l'etape 2 qui espacera les appels, comme AppManager le fait deja pour Overpass.
 *   - Pas d'autocompletion au fil de la frappe : la recherche part a la validation, pas a la frappe.
 *   - Pas de geocodage en masse, et mise en cache de ce qui peut l'etre.
 *   - Le quota se compte PAR IP : derriere un CGNAT d'operateur, plusieurs utilisateurs partagent
 *     le meme compteur.
 * (Source: operations.osmfoundation.org/policies/nominatim/)
 *
 * Les donnees sont sous ODbL : tout affichage de ces resultats doit porter l'attribution
 * "© OpenStreetMap contributors", deja presente dans l'application.
 */
public class GeoSearch {

    private static final String TAG = "SEARCH";

    /**
     * Le plafond DUR du serveur. Mesure, pas suppose : sur une requete a plusieurs milliers de
     * candidats, limit=40 rend 40, limit=50 rend 50, et limit=60, 100 ou 200 rendent 50. Demander
     * davantage ne sert donc a rien.
     *
     * C'est le defaut et le plafond du reglage "Search results" : au-dela, le serveur ne rendrait
     * rien de plus.
     */
    public static final int SERVER_MAX_RESULTS = 50;

    // Bornes d'attente. Une recherche est declenchee a la main et l'utilisateur attend devant son
    // ecran : mieux vaut echouer vite que laisser le thread suspendu.
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS    = 15000;

    /**
     * Un lieu trouve : ses coordonnees, et de quoi dire a l'utilisateur ce que c'est.
     * Immuable, donc transmissible tel quel d'un thread a l'autre (etape 2).
     */
    public static class Result {

        private final double _latitude;
        private final double _longitude;
        private final String _name;         // le nom court, "Phare de Cordouan"
        private final String _label;        // l'adresse complete, qui leve l'ambiguite
        private final String _kind;         // la nature du lieu, "man_made/lighthouse"
        // Le type d'objet OSM : node, way ou relation. Seul champ qui distingue deux reponses par
        // ailleurs identiques -- le phare de Cordouan sort en way (son contour) ET en node (son
        // point), meme nom, meme adresse, meme importance, a un metre l'un de l'autre.
        private final String _osmType;

        public Result(double latitude, double longitude, String name, String label, String kind,
                      String osmType) {
            _latitude = latitude;
            _longitude = longitude;
            _name = name;
            _label = label;
            _kind = kind;
            _osmType = osmType;
        }

        public double getLatitude() {
            return _latitude;
        }

        public double getLongitude() {
            return _longitude;
        }

        public String getName() {
            return _name;
        }

        public String getLabel() {
            return _label;
        }

        public String getKind() {
            return _kind;
        }

        public String getOsmType() {
            return _osmType;
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "%.6f,%.6f [%s/%s] %s",
                    _latitude, _longitude, _kind, _osmType, _label);
        }
    }

    /**
     * Le JSON de Nominatim, en format jsonv2. Champs publics et noms d'origine : Gson les remplit
     * directement, et ignore en silence tous ceux que l'on ne declare pas (place_id, osm_id,
     * boundingbox...). jsonv2 plutot que json parce qu'il publie "category" la ou json publie
     * "class", qui est un mot reserve du langage.
     *
     * lat et lon arrivent en CHAINES, pas en nombres : on les convertit nous-memes pour pouvoir
     * ecarter proprement une reponse malformee au lieu de laisser Gson lever.
     */
    private static class NominatimPlace {
        public String lat;
        public String lon;
        public String osm_type;
        public String name;
        public String display_name;
        public String category;
        public String type;
    }

    /**
     * Cherche un lieu par son nom ou son adresse. BLOQUANTE : a n'appeler que depuis un thread de
     * fond, jamais depuis le thread d'interface.
     *
     * @param text ce que l'utilisateur a tape, "phare de Cordouan" ou "12 rue de la Gare, Orleans"
     * @return les lieux trouves, du plus pertinent au moins pertinent. Liste VIDE si Nominatim n'a
     *         rien trouve, ce qui est une reponse et pas une panne.
     * @throws RuntimeException si la requete n'a pas abouti (reseau, timeout, statut HTTP != 200,
     *         JSON illisible). Comme le telechargement de chunk, on echoue franchement plutot que
     *         de rendre une liste vide qui ferait passer une panne pour une absence de resultat.
     */
    public static List<Result> search(String text) {
        return search(text, null);
    }

    /**
     * La meme, restreinte a une boite geographique. Ce cadre change la NATURE de la recherche :
     * a l'interieur, un simple mot de categorie suffit ("bakery" rend les boulangeries), la ou une
     * recherche sans cadre ne trouverait qu'un lieu PORTANT ce nom -- "boulangerie" tout seul rend
     * une riviere de la Somme et un hameau de Dordogne. En contrepartie bounded=1 EXCLUT tout ce
     * qui sort de la boite : un phare a cent kilometres ne sort plus. D'ou le choix laisse a
     * l'utilisateur plutot qu'un cadre impose en douce.
     *
     * @param viewbox la boite, telle que viewbox() la construit, ou null pour chercher partout
     */
    public static List<Result> search(String text, String viewbox) {

        if ((text == null) || (text.trim().length() == 0))
            return new ArrayList<Result>();

        // L'adresse est lue UNE fois : la relire plus loin pourrait tomber de l'autre cote d'un
        // changement de reglage en cours de recherche.
        String url = AppManager.getInstance().getNominatimSearchUrl()
                + "?q=" + urlEncode(text.trim())
                + "&format=jsonv2"
                + "&limit=" + AppManager.getInstance().getSearchLimit();

        // bounded=1 n'a de sens qu'avec une boite, et la boite ne sert a rien sans lui : mesure
        // faite, un viewbox seul ne change pas les reponses d'un iota.
        if (viewbox != null)
            url += "&viewbox=" + urlEncode(viewbox) + "&bounded=1";

        GBLog.d(TAG, "search \"" + text.trim() + "\" -> " + url);

        HttpClient client = null;
        InputStream in = null;
        try {
            client = new DefaultHttpClient();
            HttpParams hp = client.getParams();
            HttpConnectionParams.setConnectionTimeout(hp, CONNECT_TIMEOUT_MS);
            HttpConnectionParams.setSoTimeout(hp, READ_TIMEOUT_MS);

            HttpGet get = new HttpGet(url);
            get.setHeader("User-Agent", AppManager.USER_AGENT);
            HttpResponse response = client.execute(get);

            int statusCode = response.getStatusLine().getStatusCode();
            GBLog.d(TAG, "HTTP " + statusCode);

            HttpEntity entity = response.getEntity();

            // 403 = User-Agent refuse ou IP bloquee, 429 = quota depasse. Le corps est alors du
            // texte ou du HTML, surtout pas du JSON : ne pas le donner a Gson.
            if (statusCode != 200)
                throw new RuntimeException("Nominatim HTTP " + statusCode);

            in = entity.getContent();

            // La reponse d'une recherche tient en quelques kilo-octets (10 lieux au plus) : pas
            // besoin du parcours en flux que Chunk impose a ses dizaines de megaoctets.
            NominatimPlace[] places = new Gson().fromJson(
                    new InputStreamReader(in, StandardCharsets.UTF_8), NominatimPlace[].class);

            if (places == null)
                throw new RuntimeException("Nominatim: unreadable answer");

            List<Result> results = new ArrayList<Result>(places.length);

            for (NominatimPlace place : places) {
                Result result = toResult(place);

                // Un lieu sans coordonnees lisibles est inutilisable : on l'ecarte, sans faire
                // echouer les autres.
                if (result == null) {
                    GBLog.d(TAG, "skipped, no usable coordinates");
                    continue;
                }

                results.add(result);
            }

            GBLog.d(TAG, "found " + results.size() + " result(s)");

            for (int r = 0; r < results.size(); r++)
                GBLog.d(TAG, "  [" + r + "] " + results.get(r));

            return results;

        } catch (RuntimeException e) {
            GBLog.e(TAG, "search KO: " + e);
            throw e;
        } catch (Exception e) {
            GBLog.e(TAG, "search KO: " + e);
            throw new RuntimeException("Nominatim search failed", e);
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignore) {}
            }
            if (client != null) {
                try { client.getConnectionManager().shutdown(); } catch (Exception ignore) {}
            }
        }
    }

    // Un lieu du JSON vers un resultat utilisable, ou null s'il n'a pas de coordonnees lisibles.
    private static Result toResult(NominatimPlace place) {

        if ((place == null) || (place.lat == null) || (place.lon == null))
            return null;

        double latitude;
        double longitude;
        try {
            // parseDouble lit le point decimal quelle que soit la langue du telephone, ce que
            // ferait pas un NumberFormat regional.
            latitude = Double.parseDouble(place.lat);
            longitude = Double.parseDouble(place.lon);
        } catch (NumberFormatException e) {
            return null;
        }

        if ((latitude < -90) || (latitude > 90) || (longitude < -180) || (longitude > 180))
            return null;

        String label = (place.display_name != null) ? place.display_name : "";
        String name = (place.name != null && place.name.length() > 0) ? place.name : shortName(label);

        // "category/type" est le couple qui dit ce qu'est le lieu : man_made/lighthouse,
        // shop/supermarket, highway/bus_stop. On le garde brut, l'affichage le traduira.
        String kind;
        if (place.category == null)
            kind = (place.type != null) ? place.type : "";
        else if (place.type == null)
            kind = place.category;
        else
            kind = place.category + "/" + place.type;

        return new Result(latitude, longitude, name, label, kind,
                (place.osm_type != null) ? place.osm_type : "");
    }

    // --- #67, etape 2 : la file d'attente et son thread ----------------------------------------
    //
    // Une seule file, un seul thread, donc une seule requete a la fois : c'est ce qui rend tenable
    // la limite de debit de Nominatim, qu'aucun appelant n'a alors a connaitre. L'ecart est compte
    // depuis la FIN de la requete precedente et non depuis son depart, comme AppManager le fait
    // deja pour Overpass : deux requetes lentes ne se rattrapent pas l'une l'autre.
    //
    // Nominatim demande une seconde ; on en laisse deux, marge confortable et imperceptible face a
    // la seconde de latence du serveur.
    private static final long QUERY_SPACING_MS = 2000;

    // La file est bornee : un bug d'interface qui empilerait sans fin se fait refuser au lieu de
    // remplir la memoire en silence. Seize demandes en attente sont deja plus que de raison.
    private static final int QUEUE_CAPACITY = 16;

    private static final BlockingQueue<GeoQuery> _queue =
            new LinkedBlockingQueue<GeoQuery>(QUEUE_CAPACITY);

    private static Thread _thread = null;
    private static volatile boolean _running = false;

    /**
     * Demarre le thread de recherche. Appele par AppManager.startAllThreadsOnce(), avec les autres
     * threads de fond : pas de demarrage a la premiere recherche, qui rendrait l'arret impossible a
     * maitriser. Idempotent, et capable de repartir apres un stop().
     */
    public static synchronized void start() {

        if (_running && (_thread != null) && _thread.isAlive())
            return;

        _running = true;
        _thread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "GeoSearch");

        // Demon : meme si l'arret propre ci-dessous etait rate un jour, ce thread ne retiendrait
        // pas le processus en vie.
        _thread.setDaemon(true);
        _thread.start();

        GBLog.d(TAG, "queue thread started");
    }

    /**
     * Arrete le thread. Appele quand l'application se termine pour de bon (MainActivity.onDestroy
     * avec isFinishing()). Les demandes encore en attente sont abandonnees : celui qui attendait
     * une reponse voit sa recherche passer en echec plutot que d'attendre pour rien.
     */
    public static synchronized void stop() {

        if (!_running)
            return;

        _running = false;

        Thread thread = _thread;
        _thread = null;

        if (thread != null)
            thread.interrupt();

        // Vider la file APRES avoir coupe le thread : ce qui reste dedans n'ira nulle part.
        List<GeoQuery> pending = new ArrayList<GeoQuery>();
        _queue.drainTo(pending);

        for (GeoQuery query : pending) {
            if (query.markRunning())
                query.setError("search stopped");
        }

        GBLog.d(TAG, "queue thread stopped, " + pending.size() + " query(ies) dropped");
    }

    // Empile une demande. false = file pleine, la demande n'est pas prise.
    static boolean submit(GeoQuery query) {

        if (!_running)
            GBLog.w(TAG, "query submitted while the search thread is down");

        boolean accepted = _queue.offer(query);

        GBLog.d(TAG, "queued \"" + query.getText() + "\""
                + (accepted ? (", " + _queue.size() + " waiting") : " REFUSED, queue full"));

        return accepted;
    }

    // Retire une demande abandonnee avant son depart. Sans effet si elle est deja partie.
    static void withdraw(GeoQuery query) {
        _queue.remove(query);
    }

    private static void loop() {

        long lastEndMs = 0;

        while (_running) {
            try {
                // take() endort le thread jusqu'a la demande suivante : pas de scrutation, pas de
                // reveil pour rien. L'interruption du stop() le fait sortir d'ici.
                GeoQuery query = _queue.take();

                // Abandonnee pendant qu'elle attendait : rien ne part sur le reseau.
                if (query.getState() == GeoQuery.STATE_CANCELLED)
                    continue;

                long since = System.currentTimeMillis() - lastEndMs;

                if (since < QUERY_SPACING_MS) {
                    long wait = QUERY_SPACING_MS - since;
                    GBLog.d(TAG, "fair-use spacing, wait " + wait + " ms");
                    Thread.sleep(wait);
                }

                // Deuxieme controle : l'attente ci-dessus a pu lui laisser le temps d'etre annulee.
                if (!query.markRunning())
                    continue;

                try {
                    query.setResults(search(query.getText(), query.getViewbox()));
                } catch (Exception e) {
                    query.setError(errorText(e));
                } finally {
                    lastEndMs = System.currentTimeMillis();
                }

            } catch (InterruptedException e) {
                // C'est le stop() qui nous reveille : on sort sans bruit.
                break;
            } catch (Exception e) {
                // Rien ne doit pouvoir tuer ce thread : une recherche ratee n'emporte pas les
                // suivantes avec elle.
                GBLog.e(TAG, "queue loop error: " + e);
            }
        }

        GBLog.d(TAG, "queue thread ended");
    }

    // Le message le plus parlant que porte l'exception, sans le fatras des causes chainees.
    private static String errorText(Throwable t) {

        Throwable cause = t;

        while (cause.getCause() != null)
            cause = cause.getCause();

        String message = cause.getMessage();

        return ((message != null) && (message.length() > 0))
                ? message : cause.getClass().getSimpleName();
    }

    /**
     * Le nom court d'un lieu qui n'en a pas. Une adresse n'a pas de champ "name" : Nominatim ne
     * rend que l'adresse complete, "12, Rue de la Gare, Dunois Madeleine, Cite Emile Zola, Orleans,
     * Loiret, ..., 45000, France". L'afficher en titre ET en sous-titre ne dit rien deux fois, et en
     * faire le nom d'une marque donne une marque au nom illisible.
     *
     * On garde donc les deux premiers segments, "12, Rue de la Gare" : le numero seul ne veut rien
     * dire, la rue seule non plus. Ce qui distingue les dix "12 rue de la Gare" du Loiret les uns
     * des autres reste sous les yeux, sur la ligne d'adresse complete.
     */
    private static String shortName(String label) {

        if (label == null)
            return "";

        int first = label.indexOf(',');

        if (first < 0)
            return label.trim();

        int second = label.indexOf(',', first + 1);

        return ((second < 0) ? label : label.substring(0, second)).trim();
    }

    /**
     * La boite passee a Nominatim, au format "ouest,nord,est,sud" en degres decimaux.
     *
     * Un degre de LATITUDE vaut ~111 km partout. Un degre de LONGITUDE se resserre avec le cosinus
     * de la latitude : sans cette correction, une boite de 20 km serait deux fois trop large au
     * cap Nord. Le garde-fou sur le cosinus evite la division par zero aux poles.
     *
     * Les bornes sont ramenees dans le globe. Une boite qui enjambe l'antimeridien est simplement
     * tronquee : le cas est marginal et Nominatim n'accepte de toute facon pas une boite qui se
     * replie sur elle-meme.
     */
    public static String viewbox(double latitude, double longitude, double radiusKm) {

        double dLat = radiusKm / 111.0;

        double cos = Math.cos(Math.toRadians(latitude));

        if (cos < 0.01)
            cos = 0.01;

        double dLon = radiusKm / (111.0 * cos);

        double north = Math.min(latitude + dLat, 90.0);
        double south = Math.max(latitude - dLat, -90.0);
        double west = Math.max(longitude - dLon, -180.0);
        double east = Math.min(longitude + dLon, 180.0);

        return String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f", west, north, east, south);
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            GBLog.e(TAG, "urlEncode KO", e);
            return s;
        }
    }
}
