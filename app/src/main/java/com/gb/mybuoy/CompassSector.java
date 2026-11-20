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

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

/**
 * #54 : une case de la rose de reglage (page de configuration d'un compas). Huit exemplaires fixes,
 * un par point cardinal, crees une fois pour toutes par AppManager : la page s'y attache par data
 * binding et chaque case se redessine seule quand sa valeur change ou quand la selection se deplace.
 */
public class CompassSector extends BaseObservable {

    private static final int COLOR_SELECTED_TEXT = 0xFFFFFFFF;   // case en cours de reglage (fond bleu)
    private static final int COLOR_TEXT          = 0xFF000000;

    private final int _index;
    private String _value = Compass.formatDeviation(0.0);
    private boolean _selected = false;

    public CompassSector(int index) {
        _index = index;
    }

    public int getIndex() {
        return _index;
    }

    public String getLabel() {
        return Compass.sectorLabel(_index);
    }

    @Bindable
    public String getValue() {
        return _value;
    }

    public void setValue(String value) {
        if ((value == null) || value.equals(_value))
            return;

        _value = value;
        notifyPropertyChanged(BR.value);
    }

    @Bindable
    public boolean getSelected() {
        return _selected;
    }

    public void setSelected(boolean selected) {
        if (selected == _selected)
            return;

        _selected = selected;
        notifyPropertyChanged(BR.selected);
        notifyPropertyChanged(BR.textColor);
    }

    @Bindable
    public int getTextColor() {
        return _selected ? COLOR_SELECTED_TEXT : COLOR_TEXT;
    }
}
