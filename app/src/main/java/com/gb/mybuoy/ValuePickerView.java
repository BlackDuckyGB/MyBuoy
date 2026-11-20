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
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import androidx.databinding.BindingAdapter;

/**
 * #54 : choix d'une valeur dans une liste, en remplacement du Spinner du systeme.
 *
 * Le Spinner standard reserve dans son habillage une marge interne pour sa fleche : le controle
 * ferme s'etale, un large vide separe la valeur de la fleche, et l'ensemble s'arrete avant le bord
 * au lieu de s'aligner sur les autres controles d'une page de reglages. Ici le controle ferme est
 * une simple valeur suivie d'un triangle, colles, dans une largeur fixe ; le triangle n'est qu'un
 * decor, c'est le bloc entier qui est cliquable. La liste s'ouvre ancree sous le bloc, a la MEME
 * largeur, et non en boite de dialogue.
 */
public class ValuePickerView extends LinearLayout {

    /** Choix de l'utilisateur dans la liste. Interface a methode unique : utilisable en lambda. */
    public interface OnPositionChanged {
        void onPositionChanged(int position);
    }

    private static final int COLOR_VALUE   = 0xFF1565C0;
    private static final int COLOR_CURRENT = 0xFFBBDEFB;   // ligne de la valeur en cours, dans la liste
    private static final int LIST_MAX_DP   = 320;          // au-dela, la liste defile

    private final TextView _value;
    private final float _density;

    private List<String> _values = new ArrayList<>();
    private int _position = 0;
    private OnPositionChanged _listener = null;
    private ListPopupWindow _popup = null;

    public ValuePickerView(Context context) {
        this(context, null);
    }

    public ValuePickerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        _density = getResources().getDisplayMetrics().density;

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        setClickable(true);
        setFocusable(true);
        setBackgroundResource(R.drawable.value_picker_bg);

        _value = new TextView(context);
        _value.setTextSize(16);
        _value.setTextColor(COLOR_VALUE);
        _value.setSingleLine(true);
        addView(_value, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        ImageView triangle = new ImageView(context);
        triangle.setImageResource(R.drawable.ic_triangle_down);
        LayoutParams lp = new LayoutParams((int) (10 * _density), (int) (10 * _density));
        lp.leftMargin = (int) (5 * _density);
        addView(triangle, lp);

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                showList();
            }
        });
    }

    // Les valeurs et le choix courant arrivent par data binding.
    @BindingAdapter({ "values", "position", "onPositionChanged" })
    public static void bindPicker(ValuePickerView view, List<String> values, int position,
                                  OnPositionChanged listener) {
        view._values = (values == null) ? new ArrayList<String>() : values;
        view._listener = listener;
        view.setPosition(position);
    }

    private void setPosition(int position) {
        if ((position < 0) || (position >= _values.size()))
            return;

        _position = position;
        _value.setText(_values.get(position));
    }

    private void showList() {
        if (_values.isEmpty())
            return;

        // Un adaptateur qui marque la valeur en cours : dans une liste de valeurs qui se ressemblent
        // toutes, rien d'autre ne dit ou l'on en est. Le fond est pose sur CHAQUE ligne, les vues
        // etant recyclees.
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(),
                R.layout.layout_value_picker_item, _values) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = super.getView(position, convertView, parent);

                row.setBackgroundColor((position == _position) ? COLOR_CURRENT : Color.TRANSPARENT);

                return row;
            }
        };

        final ListPopupWindow popup = new ListPopupWindow(getContext());

        popup.setAnchorView(this);
        popup.setAdapter(adapter);
        popup.setModal(true);
        popup.setWidth(getWidth());                       // la liste fait la largeur du bloc
        popup.setHeight(listHeight(adapter));

        popup.setOnItemClickListener((parent, view, position, id) -> {
            setPosition(position);
            popup.dismiss();

            if (_listener != null)
                _listener.onPositionChanged(position);
        });

        _popup = popup;
        popup.show();

        // Ouvrir la liste sur la valeur en cours plutot qu'a son debut.
        if (popup.getListView() != null)
            popup.getListView().setSelection(_position);
    }

    /**
     * Hauteur de la liste : celle qu'il faut pour ses valeurs, PLAFONNEE a LIST_MAX_DP au-dela de
     * quoi elle defile. LIST_MAX_DP etait applique tel quel, si bien qu'une liste de trois valeurs
     * ouvrait une fenetre de 320 dp suivie d'un grand vide blanc. Le defaut ne se voyait pas
     * partout : une ListPopupWindow est bornee par la place disponible sous son ancre, qui peut
     * deja etre inferieure au plafond selon l'ecran et la position de la ligne dans la page.
     *
     * La hauteur d'une ligne est MESUREE sur une vue de l'adaptateur plutot que codee en dur : elle
     * depend du theme et de la taille de police du telephone.
     */
    private int listHeight(ArrayAdapter<String> adapter) {
        int max = (int) (LIST_MAX_DP * _density);
        int count = adapter.getCount();

        if( count <= 0 )
            return max;

        View item = adapter.getView(0, null, this);
        if( item == null )
            return max;

        int widthSpec = MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST);
        int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        item.measure(widthSpec, heightSpec);

        int rowHeight = item.getMeasuredHeight();
        if( rowHeight <= 0 )
            return max;

        // Les separateurs entre lignes comptent aussi, sans quoi la derniere ligne serait rognee
        // d'un ou deux pixels par ligne et la liste defilerait pour rien.
        int wanted = (rowHeight * count) + dividerHeight(count);

        return Math.min(wanted, max);
    }

    // Hauteur totale des separateurs de la ListView interne : un de moins que le nombre de lignes,
    // a l'epaisseur par defaut d'un divider (1 px), et une marge d'un pixel pour les arrondis.
    private int dividerHeight(int count) {
        return (count > 1) ? (count - 1) + 1 : 0;
    }

    @Override
    protected void onDetachedFromWindow() {
        if ((_popup != null) && _popup.isShowing())
            _popup.dismiss();

        _popup = null;

        super.onDetachedFromWindow();
    }
}
