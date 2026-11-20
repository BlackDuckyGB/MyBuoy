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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Les astuces de l'ecran d'attente : tirage au hasard, sans jamais redonner une astuce deja vue
 * tant qu'il en reste. Quand toutes sont passees, on rebat les cartes. Rien n'est conserve d'une
 * session a l'autre : chaque lancement repart d'un paquet neuf.
 */
public class SplashTips {

    private final String[] _astuces;
    private final List<Integer> _restantes = new ArrayList<>();

    private int _derniere = -1;

    public SplashTips(Context context) {
        _astuces = context.getResources().getStringArray(R.array.splash_tips);
    }

    public boolean isEmpty() {
        return _astuces.length == 0;
    }

    /** L'astuce suivante, tiree parmi celles qui n'ont pas encore ete vues. */
    public String suivante() {

        if( _astuces.length == 0 ) {
            return "";
        }

        if( _restantes.isEmpty() ) {

            for( int i = 0; i < _astuces.length; i++ ) {
                _restantes.add(i);
            }
            Collections.shuffle(_restantes);

            // au rebattage, eviter de redonner tout de suite celle qu'on vient de lire
            if( _astuces.length > 1 && _restantes.get(0) == _derniere ) {
                Collections.swap(_restantes, 0, _restantes.size() - 1);
            }
        }

        _derniere = _restantes.remove(0);

        return _astuces[_derniere];
    }
}
