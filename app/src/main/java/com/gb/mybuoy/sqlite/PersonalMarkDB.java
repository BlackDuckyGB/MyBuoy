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
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.util.Log;
import com.gb.mybuoy.AppManager;
import com.gb.mybuoy.GBLog;

import com.gb.mybuoy.PersonalMark;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PersonalMarkDB extends ObjDB<PersonalMark>{

    private static final String TAG = "PersonalMarkDB";

    // Altitude tag, in metres. ele = above mean sea level, as in OpenStreetMap (Android >= 14 converts
    // the GPS altitude); ele_wgs84 = raw GPS altitude above the WGS-84 ellipsoid (older Android).
    private static final String KEY_ELE = "ele";
    private static final String KEY_ELE_WGS84 = "ele_wgs84";

    private static String altitudeKey() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ? KEY_ELE : KEY_ELE_WGS84;
    }

    private static String altitudeValue(double altitude) {
        return String.format(Locale.US, "%.1f", altitude);
    }

    public PersonalMarkDB(AppDB appDB){
        super(appDB);
    }

    @Override
    public PersonalMark selectItem(long key) {
        return null;
    }

    @Override
    public Cursor selectAll() {
        Cursor c = _bdd.query("nodes n LEFT JOIN node_tags nt ON (n.node_id = nt.node_id)",
                new String[]{
                        "n.node_id AS n_node_id",
                        "n.lon",
                        "n.lat",
                        "nt.node_id AS nt_node_id",
                        "nt.key",
                        "nt.value"},
                null, null, null, null,
                "n.node_id, nt.key");
        return c;
    }

    @Override
    public long insert(PersonalMark obj) {
        long key = 0;
        long retval = 0;
        boolean insertOK = true;
        ContentValues values = new ContentValues();

        try {
            _bdd.beginTransaction();

            if( insertOK ) {
                values.clear();
                values.put("lon", obj.getLongitude());
                values.put("lat", obj.getLatitude());

                key = _bdd.insert("nodes", null, values);
                insertOK = (key > 0);
            }

            if( insertOK ) {
                values.clear();
                values.put("node_id", key);
                values.put("key", "place");
                values.put("value", String.format("personal:mark:%d_%d_%d", obj.getSheetBitmap(), obj.getRowBitmap(), obj.getColBitmap()));

                retval = _bdd.insert("node_tags", null, values);
                insertOK = (retval > 0);
            }

            if( insertOK ) {
                values.clear();
                values.put("node_id", key);
                values.put("key", "title");
                values.put("value", obj.getTitle());

                retval = _bdd.insert("node_tags", null, values);
                insertOK = (retval > 0);
            }

            if( insertOK ) {
                values.clear();
                values.put("node_id", key);
                values.put("key", "share");
                values.put("value", obj.isShared() ? "true" : "false");

                retval = _bdd.insert("node_tags", null, values);
                insertOK = (retval > 0);
            }

            if( insertOK ) {
                values.clear();
                values.put("node_id", key);
                values.put("key", "alarm");
                values.put("value", obj.isAlarmed() ? "true" : "false");

                retval = _bdd.insert("node_tags", null, values);
                insertOK = (retval > 0);
            }

            // No tag when the altitude is unknown.
            if( insertOK ) {
                Double altitude = obj.getAltitude();
                if (altitude != null) {
                    values.clear();
                    values.put("node_id", key);
                    values.put("key", altitudeKey());
                    values.put("value", altitudeValue(altitude));

                    retval = _bdd.insert("node_tags", null, values);
                    insertOK = (retval > 0);
                }
            }

            if( insertOK ) {
                if (obj.getSharedTime() != null) {

                    Timestamp ts = new Timestamp(obj.getSharedTime().getTime());
                    values.clear();
                    values.put("node_id", key);
                    values.put("key", "shared_time");
                    values.put("value", ts.toString());

                    retval = _bdd.insert("node_tags", null, values);
                    insertOK = (retval > 0);
                }
            }

            if( insertOK ) {
                _bdd.setTransactionSuccessful();
            }
            else {
                GBLog.e( TAG, "Insert error -> Rollbacked");
            }

        } catch (Exception ex ) {
            GBLog.e( TAG, String.format("E:%s", ex.toString()));
            insertOK = false;
        } finally {
            _bdd.endTransaction();
        }

        return insertOK ? key : 0;
    }

    @Override
    public long update(long key, PersonalMark obj) {
        long retval = 0;
        boolean updateOK = true;
        ContentValues values = new ContentValues();


        try {
            _bdd.beginTransaction();

            if( updateOK ) {
                retval = _bdd.delete("node_tags","node_id = ?", new String[]{ String.format(Locale.ROOT,"%d", key) });
                updateOK = (retval >= 0);
            }

            if( updateOK ) {
                values.clear();
                values.put("node_id", key);
                values.put("key", "place");
                values.put("value", String.format("personal:mark:%d_%d_%d", obj.getSheetBitmap(), obj.getRowBitmap(), obj.getColBitmap()));

                retval = _bdd.insert("node_tags", null, values);
                updateOK = (retval > 0);
            }

            if( updateOK ) {
                values.clear();
                values.put("node_id", key);
                values.put("key", "title");
                values.put("value", obj.getTitle());

                retval = _bdd.insert("node_tags", null, values);
                updateOK = (retval > 0);
            }

            if( updateOK ) {
                values.clear();
                values.put("node_id", key);
                values.put("key", "share");
                values.put("value", obj.isShared() ? "true" : "false");

                retval = _bdd.insert("node_tags", null, values);
                updateOK = (retval > 0);
            }

            if( updateOK ) {
                values.clear();
                values.put("node_id", key);
                values.put("key", "alarm");
                values.put("value", obj.isAlarmed() ? "true" : "false");

                retval = _bdd.insert("node_tags", null, values);
                updateOK = (retval > 0);
            }

            // No tag when the altitude is unknown.
            if( updateOK ) {
                Double altitude = obj.getAltitude();
                if (altitude != null) {
                    values.clear();
                    values.put("node_id", key);
                    values.put("key", altitudeKey());
                    values.put("value", altitudeValue(altitude));

                    retval = _bdd.insert("node_tags", null, values);
                    updateOK = (retval > 0);
                }
            }

            if( updateOK ) {
                if (obj.getSharedTime() != null) {

                    Timestamp ts = new Timestamp(obj.getSharedTime().getTime());
                    values.clear();
                    values.put("node_id", key);
                    values.put("key", "shared_time");
                    values.put("value", ts.toString());

                    retval = _bdd.insert("node_tags", null, values);
                    updateOK = (retval > 0);
                }
            }

            if( updateOK ) {
                values.clear();
                values.put("lon", obj.getLongitude());
                values.put("lat", obj.getLatitude());

                retval = _bdd.update("nodes",values,"node_id = ?", new String[]{ String.format(Locale.ROOT,"%d", key) });
                updateOK = (retval > 0);
            }

            if( updateOK ) {
                _bdd.setTransactionSuccessful();
            }
            else {
                GBLog.e( TAG, "Update error -> Rollbacked");
            }

        } catch (Exception ex ) {
            GBLog.e( TAG, String.format("E:%s", ex.toString()));
            updateOK = false;
        } finally {
            _bdd.endTransaction();
        }

        //return updateOK ? retval : 0;
        return updateOK ? key : 0;
    }

    @Override
    public long replace(PersonalMark obj) {
        long key = 0;

        if( true ) {

            if (obj.getId() > 0) {
                key = update(obj.getId(), obj);
            } else {
                key = insert(obj);
            }
        } else {
            ContentValues values = new ContentValues();

            /*
            values.put("name", obj.getName());
            values.put("version", obj.getVersion());
            values.put("title", obj.getTitle());
            values.put("fileSize", obj.getFileSize());
            values.put("url", obj.getUrl());
            values.put("dbPath", obj.getDbPath());

            _bdd.replace("MapInfo", null, values);
             */
        }

        return key;
    }

    @Override
    public long remove(long key) {
        long retval = 0;
        boolean deleteOK = true;

        try {
            _bdd.beginTransaction();

            if( deleteOK ) {
                retval = _bdd.delete("node_tags","node_id = ?", new String[]{ String.format(Locale.ROOT,"%d", key) });
                deleteOK = (retval >= 0);
            }

            if( deleteOK ) {
                retval = _bdd.delete("nodes","node_id = ?", new String[]{ String.format(Locale.ROOT,"%d", key) });
                deleteOK = (retval > 0);
            }

            if( deleteOK ) {
                _bdd.setTransactionSuccessful();
            }
            else {
                GBLog.e( TAG, "Delete error -> Rollbacked");
            }
        } catch (Exception ex ) {
            GBLog.e( TAG, String.format("E:%s", ex.toString()));
            deleteOK = false;
        } finally {
            _bdd.endTransaction();
        }

        return deleteOK ? retval : 0;
    }

    // Android >= 14 only, before the marks are loaded: turns the ele_wgs84 tags written under an
    // older Android (the phone has been upgraded since) into ele, each at its own mark's position.
    // Usually finds nothing. A mark whose conversion fails keeps its ele_wgs84.
    public int migrateEleWgs84(Context context) {
        int migrated = 0;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return 0;
        }

        // 1) Read everything, then close the cursor: the updates below rename these very rows, and
        // a cursor refilled after that would skip some of them.
        List<Long> nodeIds = new ArrayList<>();
        List<double[]> latLonWgs84 = new ArrayList<>();

        Cursor c = null;
        try {
            c = _bdd.rawQuery("SELECT n.node_id, n.lat, n.lon, nt.value " +
                            "FROM nodes n JOIN node_tags nt ON (n.node_id = nt.node_id) " +
                            "WHERE nt.key = ?",
                    new String[]{ KEY_ELE_WGS84 });

            while (c.moveToNext()) {
                String value = c.getString(3);
                try {
                    double wgs84 = Double.parseDouble(value);
                    nodeIds.add(c.getLong(0));
                    latLonWgs84.add(new double[]{ c.getDouble(1), c.getDouble(2), wgs84 });
                } catch (NumberFormatException e) {
                    GBLog.e( TAG, String.format("Bad altitude:%s", value));
                }
            }
        } catch (Exception ex) {
            GBLog.e( TAG, String.format("E:%s", ex.toString()));
        } finally {
            if (c != null) {
                c.close();
            }
        }

        if (nodeIds.isEmpty()) {
            return 0;
        }

        // 2) Convert, outside any transaction (reads the system geoid map).
        List<String> eleValues = new ArrayList<>();
        for (double[] llw : latLonWgs84) {
            Double correction = AppManager.altitudeCorrectionAt(context, llw[0], llw[1]);
            eleValues.add((correction != null) ? altitudeValue(llw[2] + correction) : null);
        }

        // 3) Write, in one transaction. A mark whose conversion failed keeps its ele_wgs84.
        try {
            _bdd.beginTransaction();

            for (int i = 0; i < nodeIds.size(); i++) {
                String eleValue = eleValues.get(i);
                if (eleValue == null) {
                    continue;
                }

                ContentValues values = new ContentValues();
                values.put("key", KEY_ELE);
                values.put("value", eleValue);
                int n = _bdd.update("node_tags", values, "node_id = ? AND key = ?",
                        new String[]{ String.format(Locale.ROOT, "%d", nodeIds.get(i)), KEY_ELE_WGS84 });
                if (n > 0) {
                    migrated++;
                }
            }

            _bdd.setTransactionSuccessful();
        } catch (Exception ex) {
            GBLog.e( TAG, String.format("E:%s", ex.toString()));
            migrated = 0;
        } finally {
            _bdd.endTransaction();
        }

        GBLog.i( TAG, String.format(Locale.ROOT, "migrateEleWgs84: %d mark(s)", migrated));
        return migrated;
    }

    @Override
    public PersonalMark cursorToObj(Cursor c) {
        PersonalMark pm = new PersonalMark();
        boolean bNext = false;
        long n_id = 0;
        long next_n_id = 0;
        long nt_id;
        String key;
        String value;

        n_id = c.getLong(c.getColumnIndex("n_node_id"));
        pm.setId(n_id);
        pm.setLongitude(c.getDouble(c.getColumnIndex("lon")));
        pm.setLatitude(c.getDouble(c.getColumnIndex("lat")));

        do {
            nt_id = c.getLong(c.getColumnIndex("nt_node_id"));
            key = c.getString(c.getColumnIndex("key"));
            value = c.getString(c.getColumnIndex("value"));

            if( nt_id > 0 && key != null ) {
                switch( key ) {
                    case "title":
                        pm.setTitle(value);
                        break;
                    case "place":
                        // Nouveau format : personal:mark:SHEET_ROW_COL. Ancien (2 nombres) :
                        // personal:mark:ROW_COL, lu comme SHEET 0 (retro-compat, ne casse rien).
                        Pattern pattern = Pattern.compile("^personal:mark:(\\d+)_(\\d+)(?:_(\\d+))?$");
                        Matcher matcher = pattern.matcher(value);
                        if( matcher.find() ) {
                            if( matcher.group(3) != null ) {
                                pm.setSheetBitmap(Integer.parseInt(matcher.group(1)));
                                pm.setRowBitmap(Integer.parseInt(matcher.group(2)));
                                pm.setColBitmap(Integer.parseInt(matcher.group(3)));
                            } else {
                                pm.setSheetBitmap(0);
                                pm.setRowBitmap(Integer.parseInt(matcher.group(1)));
                                pm.setColBitmap(Integer.parseInt(matcher.group(2)));
                            }
                        }
                        break;
                    case "share":
                        pm.setShared("true".compareTo(value) == 0);
                        break;
                    // #7 : tag ajoute apres coup. Une mark creee avant ne le porte pas, le champ
                    // reste alors a false : rien a migrer, la base ne change pas de schema.
                    case "alarm":
                        pm.setAlarmed("true".compareTo(value) == 0);
                        break;
                    // Read as is: ele_wgs84 left on Android >= 14 was converted by migrateEleWgs84()
                    // before the marks are loaded.
                    case KEY_ELE:
                    case KEY_ELE_WGS84:
                        try {
                            pm.setAltitude(Double.parseDouble(value));
                        } catch (NumberFormatException e) {
                            GBLog.e( TAG, String.format("Bad altitude:%s", value));
                        }
                        break;
                    case "shared_time":
                        Timestamp ts = Timestamp.valueOf(value);
                        long time = ts.getTime();
                        pm.setSharedTime(new Date(time));
                        break;
                    default: break;
                }
            }

            if( c.moveToNext() ) {
                next_n_id = c.getLong(c.getColumnIndex("n_node_id"));

                if( next_n_id == n_id ) {
                    bNext = true;
                } else {
                    c.moveToPrevious();
                    bNext = false;
                }
            } else {
                c.moveToPrevious();
                bNext = false;
            }


        }while( bNext );
        /*

        //mi.setId(c.getLong(c.getColumnIndex("id")));
        pm.setName(c.getString(c.getColumnIndex("name")));
        pm.setVersion(c.getLong(c.getColumnIndex("version")));
        pm.setTitle(c.getString(c.getColumnIndex("title")));
        pm.setFileSize(c.getLong(c.getColumnIndex("fileSize")));
        pm.setUrl(c.getString(c.getColumnIndex("url")));
        pm.setDbPath(c.getString(c.getColumnIndex("dbPath")));
         */


        return pm;
    }
}
