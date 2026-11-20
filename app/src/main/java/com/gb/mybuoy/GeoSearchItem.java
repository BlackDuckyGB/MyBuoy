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

import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;

import androidx.core.content.ContextCompat;
import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import androidx.databinding.BindingAdapter;

/**
 * Tache #67, etape 3 : UNE ligne de la page de recherche.
 *
 * Elle adosse le resultat rendu par Nominatim a une PersonalMark qui n'est PAS enregistree : c'est
 * elle qui sait deja formater des coordonnees au format choisi par l'utilisateur, calculer une
 * distance et rendre la fleche du bon secteur. On ne redeveloppe donc rien de tout cela ici.
 *
 * Cette meme instance est celle que l'editeur recoit au clic sur Create. S'il l'enregistre, elle
 * prend un identifiant en base et entre dans la liste des marques (PersonalMark.save() appelle
 * addMarkInList, qui la selectionne au passage) : la ligne le voit, passe son bouton a Edit et
 * troque l'icone generique contre celle choisie dans l'editeur.
 */
public class GeoSearchItem extends BaseObservable {

    private final GeoSearch.Result _result;
    private final PersonalMark _mark;

    // Prevenu quand la ligne change d'etat (creation, edition, suppression), pour que la page
    // puisse retrier sa liste : une marque qui vient d'etre creee est selectionnee, et doit donc
    // remonter en tete comme elle le ferait dans la liste des marques perso.
    private Runnable _onChanged = null;

    public GeoSearchItem(GeoSearch.Result result) {
        _result = result;

        AppManager am = AppManager.getInstance();

        // Ce lieu porte-t-il DEJA une marque ? Meme position et meme titre. Si oui, la ligne
        // l'adopte : elle sortira avec l'icone reelle de la marque et un bouton Edit, au lieu de
        // proposer d'en creer une seconde. Sa distance est deja tenue a jour par la boucle de fond,
        // il n'y a rien a caler ici.
        PersonalMark existing = am.findMarkAt(
                result.getLatitude(), result.getLongitude(), result.getName());

        if (existing != null) {
            _mark = existing;
            return;
        }

        _mark = new PersonalMark(am.getCurrentChunk(),
                result.getLongitude(), result.getLatitude(), result.getName());

        // La boucle de fond ne met a jour que les marques ENREGISTREES : cette candidate est donc
        // calee une fois, a la reception des resultats. Elle ne suit pas les deplacements tant que
        // la page reste ouverte, ce qui n'a pas d'importance devant une liste qu'on lit et referme.
        am.updateMarkGeometry(_mark);
    }

    public void setOnChanged(Runnable onChanged) {
        _onChanged = onChanged;
    }

    /** Ordre de tri : une marque creee ET selectionnee passe devant, comme dans la liste des marques. */
    public boolean isSelected() {
        return isCreated() && _mark.isSelected();
    }

    /** #7 : et une marque qui SIGNALE passe devant tout, toujours comme la liste des marques. */
    public boolean isAlarming() {
        return isCreated() && _mark.isAlarming();
    }

    public double getDistance() {
        return _mark.getDistance();
    }

    /** La marque candidate. C'est elle qu'on passe a l'editeur, et elle est deja enregistree ou non. */
    public PersonalMark getMark() {
        return _mark;
    }

    /**
     * Enregistree = elle a un identifiant en base ET elle est toujours dans la liste des marques.
     * Les deux conditions comptent : l'editeur permet aussi de SUPPRIMER, et une marque supprimee
     * garde son ancien identifiant. Sans le second test, la ligne proposerait d'editer une marque
     * qui n'existe plus. Effacee, elle redevient un simple resultat de recherche, qu'on peut
     * recreer.
     */
    public boolean isCreated() {
        return (_mark.getId() > 0) && AppManager.getInstance().isMarkInList(_mark);
    }

    @Bindable
    public String getTitle() {
        return _mark.getTitle();
    }

    /**
     * #7 : titre en rouge quand la marque est armee. La condition passe par isCreated() comme
     * isSelected() : tant que la ligne n'est qu'un resultat de recherche, sa marque candidate n'est
     * pas enregistree et n'a donc rien a signaler.
     */
    @Bindable
    public boolean isAlarmed() {
        return isCreated() && _mark.isAlarmed();
    }

    /** L'adresse complete : c'est elle qui distingue dix "12 rue de la Gare" les uns des autres. */
    @Bindable
    public String getLabel() {
        return _result.getLabel();
    }

    @Bindable
    public String getLatitudeString() {
        return _mark.getLatitudeString();
    }

    @Bindable
    public String getLongitudeString() {
        return _mark.getLongitudeString();
    }

    @Bindable
    public String getDistanceString() {
        return _mark.getDistanceString();
    }

    @Bindable
    public Drawable getArrowBitmap() {
        return _mark.getArrowBitmap();
    }

    /**
     * L'icone generique tant que le lieu n'est qu'un resultat, celle de la marque une fois creee.
     * Nominatim rend une centaine de couples category/type et les faire correspondre a la planche
     * de sprites serait une table sans fin : la nature du lieu est ecrite en toutes lettres sous
     * l'icone, ce qui se lit mieux qu'un pictogramme a deviner.
     */
    @Bindable
    public Drawable getIcon() {

        if (isCreated())
            return _mark.getBitmap();

        return ContextCompat.getDrawable(AppManager.getInstance().getContext(),
                R.drawable.ic_search_result);
    }

    /**
     * La nature du lieu, telle qu'elle s'affiche sous l'icone : "Lighthouse", "Supermarket",
     * "Bus stop". Nominatim la donne sous la forme category/type ; le type est le plus parlant des
     * deux, sauf quand il ne dit rien ("building/yes"), ou l'on retombe sur la categorie.
     */
    @Bindable
    public String getTypeLabel() {

        String kind = _result.getKind();

        if ((kind == null) || (kind.length() == 0))
            return "";

        String category = kind;
        String type = kind;
        int slash = kind.indexOf('/');

        if (slash >= 0) {
            category = kind.substring(0, slash);
            type = kind.substring(slash + 1);
        }

        // "yes" est le tag fourre-tout d'OSM : il ne dit rien, la categorie en dit plus.
        String kept = ("yes".equals(type) || (type.length() == 0)) ? category : type;

        return humanize(kept);
    }

    /** "speed_camera" -> "Speed camera". */
    private static String humanize(String tag) {

        String s = tag.replace('_', ' ').trim();

        if (s.length() == 0)
            return "";

        return s.substring(0, 1).toUpperCase(Locale.US) + s.substring(1);
    }

    /**
     * Le type d'objet OSM, sous la nature du lieu. C'est le SEUL champ qui distingue deux reponses
     * par ailleurs identiques : le phare de Cordouan sort en "way" (le contour du batiment) et en
     * "node" (le point du phare), meme nom, meme adresse, meme importance, a un metre l'un de
     * l'autre. C'est du jargon OSM, d'ou la place discrete qu'on lui donne.
     */
    @Bindable
    public String getOsmTypeLabel() {
        return _result.getOsmType();
    }

    @Bindable
    public String getButtonLabel() {
        return isCreated() ? "Edit" : "Create";
    }

    /**
     * Le bouton de droite. Avant creation il ouvre l'editeur pre-rempli, le meme que celui d'une
     * position recue par lien geo: (#65) ; apres creation il rouvre la marque en edition. Dans les
     * deux cas la ligne se rafraichit a la fermeture de l'editeur, l'utilisateur ayant pu
     * enregistrer, changer l'icone ou renommer.
     */
    public void onButton() {

        Runnable onClosed = new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        };

        // TOUJOURS un clone, creation comprise, exactement comme la page des marques perso. C'est le
        // clone qui donne son sens au bouton Save : l'editeur travaille sur une copie, et rien ne
        // touche a l'objet reel tant que l'utilisateur n'a pas enregistre. En lui passant la marque
        // elle-meme, le nom, l'icone et les coordonnees etaient repris au fil de la frappe, et
        // fermer sans enregistrer laissait les modifications en place.
        //
        // PersonalMark.save() sait traiter les deux cas de la meme facon : il recopie les champs du
        // clone dans son cloneFrom puis l'ajoute a la liste -- que celui-ci soit deja enregistre
        // (edition) ou pas encore (creation). On ne passe pas par PersonalMark.edit(), qui n'offre
        // aucun moyen d'etre prevenu de la fermeture ; la ligne resterait figee sur l'ancien etat.
        // La profondeur de clonage reste de UN : le clone est jete a la fermeture et ne devient
        // jamais lui-meme la source d'un autre clone.
        ViewsController.getInstance().ShowPersonalMark(new PersonalMark(_mark), onClosed);
    }

    // Tout ce qui a pu changer dans l'editeur : le nom, l'icone, l'etat enregistre ou non, et donc
    // le libelle du bouton.
    public void refresh() {

        // Supprimee depuis l'editeur : la marque garde son ancien identifiant de base. Sans cette
        // remise a zero, le Create suivant rouvrirait l'editeur en mode EDITION d'une ligne qui
        // n'existe plus -- l'ecran afficherait un bouton Delete et l'enregistrement porterait sur un
        // identifiant mort. On la ramene a l'etat de simple candidate, ce qu'elle est redevenue.
        if ((_mark.getId() > 0) && !AppManager.getInstance().isMarkInList(_mark))
            _mark.setId(0);

        notifyPropertyChanged(BR.title);
        notifyPropertyChanged(BR.alarmed);
        notifyPropertyChanged(BR.icon);
        notifyPropertyChanged(BR.buttonLabel);
        notifyPropertyChanged(BR.latitudeString);
        notifyPropertyChanged(BR.longitudeString);
        notifyPropertyChanged(BR.distanceString);
        notifyPropertyChanged(BR.arrowBitmap);

        if (_onChanged != null)
            _onChanged.run();
    }

    /**
     * Pose le texte sous l'icone, et l'efface s'il ne tient pas.
     *
     * La reduction automatique de la police descend jusqu'a sa borne basse, apres quoi Android
     * coupe le mot ou l'ecrase : un "Supermar..." sous une icone n'apprend rien a personne. On
     * mesure donc le texte a la taille reellement appliquee, une fois la vue mise en page, et on
     * efface la ligne plutot que d'afficher un moignon.
     */
    @BindingAdapter("fitOrHide")
    public static void bindFitOrHide(final TextView view, final String text) {

        final String value = (text == null) ? "" : text;

        view.setText(value);
        view.setVisibility(value.length() == 0 ? View.INVISIBLE : View.VISIBLE);

        if (value.length() == 0)
            return;

        // post() : avant la mise en page la largeur vaut zero et la taille de police n'a pas encore
        // ete reduite, il n'y a rien a mesurer.
        view.post(new Runnable() {
            @Override
            public void run() {

                int available = view.getWidth() - view.getPaddingLeft() - view.getPaddingRight();

                if (available <= 0)
                    return;

                TextPaint paint = view.getPaint();

                view.setVisibility((paint.measureText(value) > available)
                        ? View.INVISIBLE : View.VISIBLE);
            }
        });
    }
}
