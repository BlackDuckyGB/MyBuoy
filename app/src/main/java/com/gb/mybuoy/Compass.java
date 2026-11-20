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

import java.util.Locale;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

/**
 * #54 : un compas et sa carte de deviation.
 *
 * Ce que l'application appelle aujourd'hui "cap magnetique" est en realite un CAP COMPAS. Deux
 * corrections de nature differente s'y appliquent : la DECLINAISON, qui depend du LIEU (modele
 * geomagnetique + reglage manuel Decl, global a l'application), et la DEVIATION, qui depend du
 * MATERIEL -- ferraille, enceinte, radio... -- et varie donc selon le cap suivi. Cette classe porte
 * la seconde : une carte de 8 corrections, une par point cardinal, indexee sur le CAP COMPAS
 * (l'utilisateur met son bateau au cap N d'apres son compas, puis regle jusqu'a ce que MyBuoy
 * affiche N lui aussi).
 *
 * L'interpolation entre deux points cardinaux n'est PAS encore implementee : pour l'instant un
 * compas se cree et se regle, mais il n'influe pas sur le cap affiche en navigation.
 */
public class Compass extends BaseObservable {

    // Les 8 points de la carte, dans l'ordre des index : un pas de 45 degres a partir du nord.
    public static final String[] SECTORS = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };
    public static final int SECTOR_COUNT = 8;

    // Memes bornes et meme pas que la correction Decl : les deux se saisissent pareil.
    public static final double DEV_MIN  = -25.0;
    public static final double DEV_MAX  =  25.0;
    public static final double DEV_STEP =   0.5;

    private String _name;
    private final double[] _deviations = new double[SECTOR_COUNT];

    // Transitoire (jamais persiste) : ce compas est celui choisi dans les parametres. Sert au
    // marqueur "In use" de la liste.
    private boolean _inUse = false;

    public Compass(String name) {
        _name = (name == null) ? "" : name;
    }

    @Bindable
    public String getName() {
        return _name;
    }

    public void setName(String name) {
        if (name == null)
            name = "";

        if (name.equals(_name))
            return;

        _name = name;
        notifyPropertyChanged(BR.name);
    }

    @Bindable
    public boolean getInUse() {
        return _inUse;
    }

    public void setInUse(boolean inUse) {
        if (inUse == _inUse)
            return;

        _inUse = inUse;
        notifyPropertyChanged(BR.inUse);
    }

    // Correction de ce compas pour un point cardinal, en degres. Index hors bornes -> 0.
    public double getDeviation(int sector) {
        if ((sector < 0) || (sector >= SECTOR_COUNT))
            return 0.0;

        return _deviations[sector];
    }

    public void setDeviation(int sector, double value) {
        if ((sector < 0) || (sector >= SECTOR_COUNT))
            return;

        if (value < DEV_MIN) value = DEV_MIN;
        if (value > DEV_MAX) value = DEV_MAX;

        _deviations[sector] = value;
    }

    // Libelle d'une valeur de correction, signe compris : "+2.5°", "-1.0°". Pas de signe sur zero,
    // un "+0.0" n'ayant pas de sens. Locale.US pour le point decimal, comme le reste des affichages
    // chiffres.
    public static String formatDeviation(double value) {
        if (Math.abs(value) < (DEV_STEP / 2))
            return "0.0°";

        return String.format(Locale.US, "%+.1f°", value);
    }

    // Cap d'un point cardinal en degres : N = 0, NE = 45, E = 90...
    public static int sectorHeading(int sector) {
        return ((sector < 0) || (sector >= SECTOR_COUNT)) ? 0 : (sector * 45);
    }

    // Libelle complet d'un point cardinal : "S (180°)". Le cap est rappele parce que personne n'a de
    // tete la valeur en degres de NW ou SE au moment de mettre son bateau au cap.
    public static String sectorLabel(int sector) {
        if ((sector < 0) || (sector >= SECTOR_COUNT))
            return "";

        return SECTORS[sector] + " (" + (sector * 45) + "°)";
    }

    // #54 : correction interpolee pour un cap compas quelconque, en degres.
    //
    // Une carte de deviation est PERIODIQUE : la correction du NW doit rejoindre celle du N sans
    // cassure. On interpole donc par la courbe classique des compas (Archibald Smith),
    //     d(C) = A + B.sinC + C.cosC + D.sin2C + E.cos2C + ...
    // dont les termes ont un sens physique (A : erreur d'alignement, B et C : magnetisme permanent,
    // D et E : fer doux). Les coefficients se tirent des 8 valeurs par transformee de Fourier
    // discrete ; en gardant TOUTES les harmoniques (jusqu'a la 4e, celle de Nyquist), la courbe
    // repasse exactement par les 8 valeurs saisies au lieu de les lisser.
    public double deviationAt(double headingDeg) {
        double c = Math.toRadians(headingDeg);
        double value = 0.0;

        // Terme constant (harmonique 0).
        for (int k = 0; k < SECTOR_COUNT; k++)
            value += _deviations[k];
        value /= SECTOR_COUNT;

        // Harmoniques 1 a 3, chacune avec son terme en cosinus et son terme en sinus.
        for (int n = 1; n <= 3; n++) {
            double an = 0.0;
            double bn = 0.0;

            for (int k = 0; k < SECTOR_COUNT; k++) {
                double ck = Math.toRadians(45.0 * k);

                an += _deviations[k] * Math.cos(n * ck);
                bn += _deviations[k] * Math.sin(n * ck);
            }

            value += (2.0 / SECTOR_COUNT) * ((an * Math.cos(n * c)) + (bn * Math.sin(n * c)));
        }

        // Harmonique 4 : a ce pas d'echantillonnage elle n'a plus de terme en sinus, et son cosinus
        // vaut alternativement +1 et -1 sur les 8 points.
        double a4 = 0.0;

        for (int k = 0; k < SECTOR_COUNT; k++)
            a4 += ((k % 2) == 0) ? _deviations[k] : -_deviations[k];

        value += (a4 / SECTOR_COUNT) * Math.cos(4 * c);

        return value;
    }

    // Copie complete, nom compris : sert au bouton Clone.
    public Compass copy(String newName) {
        Compass clone = new Compass(newName);

        for (int s = 0; s < SECTOR_COUNT; s++)
            clone._deviations[s] = _deviations[s];

        return clone;
    }

    // --- Persistance ----------------------------------------------------------------------------
    // Les 8 valeurs sur une seule ligne, separees par ';', dans l'ordre des index. Une entree
    // illisible retombe a 0 sans faire echouer le chargement du reste.

    public String serializeDeviations() {
        StringBuilder sb = new StringBuilder();

        for (int s = 0; s < SECTOR_COUNT; s++) {
            if (s > 0)
                sb.append(';');
            sb.append(String.format(Locale.US, "%.1f", _deviations[s]));
        }

        return sb.toString();
    }

    public void parseDeviations(String serialized) {
        for (int s = 0; s < SECTOR_COUNT; s++)
            _deviations[s] = 0.0;

        if ((serialized == null) || serialized.isEmpty())
            return;

        String[] parts = serialized.split(";");

        for (int s = 0; (s < SECTOR_COUNT) && (s < parts.length); s++) {
            try {
                setDeviation(s, Double.parseDouble(parts[s].trim()));
            } catch (NumberFormatException ignored) {
                _deviations[s] = 0.0;
            }
        }
    }
}
