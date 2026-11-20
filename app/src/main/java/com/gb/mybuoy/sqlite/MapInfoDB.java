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
package com.gb.mybuoy.sqlite;

import android.content.ContentValues;
import android.database.Cursor;

import com.gb.mybuoy.MapInfo;

public class MapInfoDB extends ObjDB<MapInfo>{

    public MapInfoDB(AppDB appDB){
        super(appDB);
    }

    @Override
    public MapInfo selectItem(long key) {
        return null;
    }

    @Override
    public Cursor selectAll() {
        Cursor c = _bdd.query("MapInfo", new String[]{"*"}, null, null, null, null, "title, version");
        return c;
    }

    @Override
    public long insert(MapInfo obj) {
        return 0;
    }

    @Override
    public long update(long key, MapInfo obj) {
        return 0;
    }

    @Override
    public long replace(MapInfo obj) {
        ContentValues values = new ContentValues();

        values.put("name", obj.getName());
        values.put("version", obj.getVersion());
        values.put("title", obj.getTitle());
        values.put("fileSize", obj.getFileSize());
        values.put("compressedSize", obj.getCompressedSize());
        values.put("url", obj.getUrl());
        values.put("dbPath", obj.getDbPath());

        _bdd.replace("MapInfo", null, values);
        return 0;
    }

    @Override
    public long remove(long key) {
        return 0;
    }

    // Supprime une map par sa clé primaire (name, version).
    public int delete(String name, long version) {
        return _bdd.delete("MapInfo", "name=? AND version=?",
                new String[]{ name, String.valueOf(version) });
    }

    @Override
    public MapInfo cursorToObj(Cursor c) {
        MapInfo mi = new MapInfo();

        //mi.setId(c.getLong(c.getColumnIndex("id")));
        mi.setName(c.getString(c.getColumnIndex("name")));
        mi.setVersion(c.getLong(c.getColumnIndex("version")));
        mi.setTitle(c.getString(c.getColumnIndex("title")));
        mi.setFileSize(c.getLong(c.getColumnIndex("fileSize")));
        mi.setCompressedSize(c.getLong(c.getColumnIndex("compressedSize")));
        mi.setUrl(c.getString(c.getColumnIndex("url")));
        mi.setDbPath(c.getString(c.getColumnIndex("dbPath")));


        return mi;
    }
}
