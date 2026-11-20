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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import java.util.List;

import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;

public class MyListAdapter<T> extends ArrayAdapter<T> {
    private final int layoutResourceId;
    private final int variableResourceId;

    private static final String LOG_TAG = "MyListAdapter";

    public MyListAdapter(Context context, int layoutResourceId, int variableResourceId, List<T> objects) {
        super(context, layoutResourceId, objects);
        this.layoutResourceId = layoutResourceId;
        this.variableResourceId = variableResourceId;

        //this.setDropDownViewResource(layoutResourceId);
    }

    private class ViewHolder {
        public T item;
        public ViewDataBinding binding;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        try {
            T item = getItem(position);
            View v = null;
            ViewDataBinding binding = null;

            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                //v = inflater.inflate(layoutResourceId, null);

                binding = DataBindingUtil.inflate(inflater, layoutResourceId,parent, false );

                v = binding.getRoot();

                ViewHolder viewHolder = new ViewHolder();
                viewHolder.item = item;
                viewHolder.binding = binding;
                v.setTag(viewHolder);
            } else {
                v = convertView;
                ViewHolder viewHolder = (ViewHolder)v.getTag();
                binding = viewHolder.binding;

                if( viewHolder.item == item) {
                    return v;
                } else {
                    viewHolder.item = item;
                }
            }

            boolean bret = binding.setVariable(variableResourceId, item);
            GBLog.d( LOG_TAG, String.format("%d bRet:%s", position, bret ? "TRUE" :"FALSE"));

            return v;
        } catch (Exception ex) {
            GBLog.e(LOG_TAG, "error", ex);
            return null;
        }
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getView( position, convertView, parent);
    }

}
