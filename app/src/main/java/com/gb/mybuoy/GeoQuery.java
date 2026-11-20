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

import java.util.List;

/**
 * Tache #67, etape 2 : UNE recherche de lieu, du point de vue de celui qui la demande.
 *
 * L'objet EST le ticket. On le cree avec le texte cherche, on l'empile avec start(), et on vient
 * relever sa reponse avec getResult(), avec ou sans attente. Aucun numero a retenir, aucune table
 * a consulter : la file d'attente et son thread sont ranges dans GeoSearch et ne se voient pas
 * d'ici.
 *
 * Un objet ne sert QU'UNE FOIS : une fois la reponse arrivee il la garde, et un second start()
 * est refuse. Une nouvelle recherche, c'est un nouvel objet.
 *
 * Utilisation type, depuis n'importe quel thread :
 *
 *     GeoQuery query = new GeoQuery("phare de Cordouan").start();
 *     ...
 *     if (query.isDone()) {                       // sans attente, pour un rafraichissement d'ecran
 *         List<GeoSearch.Result> found = query.getResult();
 *         ...
 *     }
 *
 * ou, depuis un thread de fond qui peut se permettre d'attendre :
 *
 *     List<GeoSearch.Result> found = query.getResult(10000);
 */
public class GeoQuery {

    private static final String TAG = "SEARCH";

    /** En attente dans la file, pas encore partie. */
    public static final int STATE_QUEUED = 0;
    /** Le thread de recherche s'en occupe en ce moment. */
    public static final int STATE_RUNNING = 1;
    /** Terminee : la reponse est la, eventuellement vide si rien n'a ete trouve. */
    public static final int STATE_DONE = 2;
    /** Echouee : voir getError(). */
    public static final int STATE_FAILED = 3;
    /** Abandonnee par celui qui l'avait demandee. */
    public static final int STATE_CANCELLED = 4;
    /** Creee, mais start() n'a pas encore ete appele. */
    public static final int STATE_NEW = 5;

    private final String _text;

    // La boite de recherche, ou null pour chercher partout. Figee a la creation : la position a pu
    // changer entre le moment ou l'utilisateur lance sa recherche et celui ou elle sort de la file,
    // et c'est bien le cadre qu'il avait sous les yeux qui doit s'appliquer.
    private final String _viewbox;

    // Le verrou de CET objet. wait/notify demandent un moniteur, la ou le reste de l'application
    // travaille au ReentrantReadWriteLock : ici on attend une reponse, c'est le bon outil.
    private final Object _lock = new Object();

    private int _state = STATE_NEW;
    private List<GeoSearch.Result> _results = null;
    private String _error = null;

    public GeoQuery(String text) {
        this(text, null);
    }

    /** @param viewbox la boite construite par GeoSearch.viewbox(), ou null pour chercher partout */
    public GeoQuery(String text, String viewbox) {
        _text = (text == null) ? "" : text.trim();
        _viewbox = viewbox;
    }

    public String getText() {
        return _text;
    }

    public String getViewbox() {
        return _viewbox;
    }

    /**
     * Empile la recherche. Rend l'objet lui-meme, pour pouvoir ecrire la creation et le depart sur
     * une seule ligne. Un texte vide, un second appel ou une file pleine laissent l'objet en echec
     * plutot que de lever : l'appelant a de toute facon a traiter le cas "ca n'a pas marche".
     */
    public GeoQuery start() {

        synchronized (_lock) {
            if (_state != STATE_NEW) {
                GBLog.w(TAG, "start ignored, query already used: \"" + _text + "\"");
                return this;
            }

            if (_text.length() == 0) {
                _state = STATE_FAILED;
                _error = "empty search text";
                _lock.notifyAll();
                return this;
            }

            _state = STATE_QUEUED;
        }

        if (!GeoSearch.submit(this)) {
            synchronized (_lock) {
                _state = STATE_FAILED;
                _error = "search queue is full";
                _lock.notifyAll();
            }
        }

        return this;
    }

    /**
     * Abandonne la recherche. Encore dans la file, elle en est retiree et ne partira jamais ; deja
     * partie, elle ira jusqu'au bout cote reseau (on ne coupe pas une requete HTTP en cours) mais
     * sa reponse sera jetee. Terminee, l'appel ne fait rien : la reponse reste disponible.
     */
    public void cancel() {

        synchronized (_lock) {
            if ((_state == STATE_DONE) || (_state == STATE_FAILED) || (_state == STATE_CANCELLED))
                return;

            _state = STATE_CANCELLED;
            _lock.notifyAll();
        }

        // Hors du verrou : la file a le sien, et les imbriquer dans un ordre ici et dans l'autre
        // ailleurs est la recette d'un blocage mutuel.
        GeoSearch.withdraw(this);

        GBLog.d(TAG, "cancelled \"" + _text + "\"");
    }

    /** Plus rien a attendre : terminee, en echec ou abandonnee. */
    public boolean isDone() {
        synchronized (_lock) {
            return (_state == STATE_DONE) || (_state == STATE_FAILED) || (_state == STATE_CANCELLED);
        }
    }

    public int getState() {
        synchronized (_lock) {
            return _state;
        }
    }

    /**
     * La reponse, SANS attendre : null tant que la recherche n'a pas abouti, et null aussi si elle
     * a echoue ou a ete abandonnee. Une liste vide est une vraie reponse : Nominatim n'a rien
     * trouve. Pour distinguer les cas, isDone() et getError().
     */
    public List<GeoSearch.Result> getResult() {
        synchronized (_lock) {
            return (_state == STATE_DONE) ? _results : null;
        }
    }

    /**
     * La meme, en attendant au plus timeoutMs millisecondes. Rend null si le delai s'ecoule sans
     * reponse. A n'appeler que depuis un thread de fond : elle bloque.
     */
    public List<GeoSearch.Result> getResult(long timeoutMs) {

        long deadline = System.currentTimeMillis() + timeoutMs;

        synchronized (_lock) {
            while ((_state != STATE_DONE) && (_state != STATE_FAILED) && (_state != STATE_CANCELLED)) {

                long left = deadline - System.currentTimeMillis();

                if (left <= 0)
                    return null;

                try {
                    // wait() peut rendre la main toute seule, sans notification : d'ou la boucle,
                    // qui reteste l'etat au lieu de croire au reveil.
                    _lock.wait(left);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }

            return (_state == STATE_DONE) ? _results : null;
        }
    }

    /** Le message d'echec, ou null si la recherche n'a pas echoue. */
    public String getError() {
        synchronized (_lock) {
            return (_state == STATE_FAILED) ? _error : null;
        }
    }

    // --- Reserve au thread de recherche ---------------------------------------------------------

    // Rend false si la recherche a ete abandonnee entre-temps : le thread passe alors a la suivante
    // sans rien envoyer sur le reseau.
    boolean markRunning() {
        synchronized (_lock) {
            if (_state != STATE_QUEUED)
                return false;

            _state = STATE_RUNNING;
            return true;
        }
    }

    void setResults(List<GeoSearch.Result> results) {
        synchronized (_lock) {
            // Abandonnee pendant la requete : la reponse est jetee, l'etat d'abandon tient.
            if (_state != STATE_RUNNING)
                return;

            _results = results;
            _state = STATE_DONE;
            _lock.notifyAll();
        }
    }

    void setError(String error) {
        synchronized (_lock) {
            if (_state != STATE_RUNNING)
                return;

            _error = error;
            _state = STATE_FAILED;
            _lock.notifyAll();
        }
    }
}
