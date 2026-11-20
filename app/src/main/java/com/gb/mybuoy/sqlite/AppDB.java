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

import android.content.Context;
import android.util.Log;
import com.gb.mybuoy.GBLog;
//import android.database.sqlite.SQLiteDatabase;
//import android.database.sqlite.SQLiteOpenHelper;
import org.sqlite.database.sqlite.SQLiteDatabase;
import org.sqlite.database.sqlite.SQLiteOpenHelper;

public class AppDB extends SQLiteOpenHelper {

    // Last Build : Version 4 (colonne compressedSize ; onUpgrade non destructeur)
    public static final int DATABASE_VERSION = 4;
    public static final String DATABASE_NAME = "MyBuoy.db3";
    private static final String TAG = "AppDB";

    public AppDB(Context context) {
        //super(context, DATABASE_NAME, null, DATABASE_VERSION);
        super(context, context.getDatabasePath(DATABASE_NAME).getPath(), null, DATABASE_VERSION);
    }

    private static final String CREATE_MAPINFO = "CREATE TABLE IF NOT EXISTS MapInfo (" +
            "name TEXT NOT NULL, " +
            "title TEXT, " +
            "version INTEGER NOT NULL, " +
            "fileSize INTEGER, " +
            "compressedSize INTEGER, " +
            "url TEXT, " +
            "dbPath TEXT, " +
            "PRIMARY KEY(name,version) " +
            ");";

    private static final String DROP_MAPINFO = "DROP TABLE IF EXISTS MapInfo;";

    private static final String CREATE_NODES = "CREATE TABLE IF NOT EXISTS nodes (" +
            "node_id      INTEGER NOT NULL UNIQUE, " +
            "lon          REAL, " +
            "lat          REAL, " +
            "PRIMARY KEY(node_id AUTOINCREMENT) " +
            " );";

    private static final String DROP_NODES = "DROP TABLE nodes;";

    private static final String CREATE_NODES_TAGS = "CREATE TABLE IF NOT EXISTS node_tags (" +
            "node_id      INTEGER, " +
            "key          TEXT, " +
            "value        TEXT " +
            " );";

    private static final String DROP_NODES_TAGS = "DROP TABLE node_tags;";

    public void execSQL(SQLiteDatabase db, String query) {
        try {
            db.execSQL(query);
        } catch (Exception ex ) {
            GBLog.e( TAG, String.format("E:%s", ex.toString()));
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        //on crée la table à partir de la requête écrite dans la variable CREATE_BDD
        this.execSQL(db,CREATE_MAPINFO);
        this.execSQL(db,CREATE_NODES);
        this.execSQL(db,CREATE_NODES_TAGS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Migration SANS perte de données utilisateur.
        // nodes / node_tags = personal marks -> NE JAMAIS dropper.
        // MapInfo = simple cache des maps (repeuplé au démarrage) -> recréation OK.
        this.execSQL(db, DROP_MAPINFO);
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
