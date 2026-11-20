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

import com.gb.mybuoy.AppManager;

import android.database.Cursor;
//import android.database.sqlite.SQLiteDatabase;
import org.sqlite.database.sqlite.SQLiteDatabase;

public abstract class ObjDB<T> {

    private final AppDB _appDB;
    protected final SQLiteDatabase _bdd;

    public ObjDB(AppDB appDB){
        this._appDB = appDB;
        _bdd = this._appDB.getWritableDatabase();

    }

    protected void finalize() {
        if( _bdd != null ) {
            _bdd.close();
        }
    }

    public abstract T selectItem(long key);
    public abstract Cursor selectAll();
    public abstract long insert(T obj);
    public abstract long update(long key, T obj);
    public abstract  long replace(T obj);
    public abstract long remove(long key);
    public abstract T cursorToObj(Cursor c);
}
