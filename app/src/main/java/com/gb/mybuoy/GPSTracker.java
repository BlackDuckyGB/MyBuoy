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

import android.Manifest;
import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;
import android.util.Log;


public class GPSTracker extends Service implements LocationListener {

    private static final String TAG = "GPSTracker";
    private final Context mContext;

    // flag for GPS status
    boolean isGPSEnabled = false;

    // flag for network status
    boolean isNetworkEnabled = false;

    // flag for GPS status
    boolean canGetLocation = false;


    // The minimum distance to change Updates in meters
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 0; //10 : 10 meters 0 -> toute les MIN_TIME_BW_UPDATES

    // The minimum time between updates in milliseconds
    private static final long MIN_TIME_BW_UPDATES = (long)(1000 * 1); //1000 * 60 * 1; // 1 minute

    // Declaring a Location Manager
    protected LocationManager locationManager;

    // Access to the AppManager
    private AppManager _am = AppManager.getInstance();

    public GPSTracker(Context context) {
        this.mContext = context;
        init();
    }

    public void init_old() {
        try {
            locationManager = (LocationManager) mContext
                    .getSystemService(LOCATION_SERVICE);

            // getting GPS status
            isGPSEnabled = locationManager
                    .isProviderEnabled(LocationManager.GPS_PROVIDER);

            // getting network status
            isNetworkEnabled = locationManager
                    .isProviderEnabled(LocationManager.NETWORK_PROVIDER);


            if (!isGPSEnabled && !isNetworkEnabled && false) {
                // no network provider is enabled
            } else {
                this.canGetLocation = true;

                /*
                if (isNetworkEnabled) {
                    locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            MIN_TIME_BW_UPDATES,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                    // GBLog.d("Network", "Network");
                    if (locationManager != null) {
                        location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        if (location != null) {
                            latitude = location.getLatitude();
                            longitude = location.getLongitude();
                        }
                    }
                }
                 */

                // if GPS Enabled get lat/long using GPS Services
                if (isGPSEnabled) {
                    boolean bErr = false;

                    if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ) {
                        GBLog.e( "GPS", "ACCESS_FINE_LOCATION KO");
                        bErr = true;
                    }

                    if( ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        GBLog.e( "GPS", "ACCESS_COARSE_LOCATION KO");
                        bErr = true;
                    }

                    if( !bErr ) {

                        locationManager.requestLocationUpdates(
                                LocationManager.GPS_PROVIDER,
                                MIN_TIME_BW_UPDATES,
                                MIN_DISTANCE_CHANGE_FOR_UPDATES, this);


                        Location location = locationManager
                                .getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        if (location != null) {
                            _am.setLocation(location);
                        }
                    }
                }
            }

        } catch (Exception e) {
            GBLog.e( "GPS", "ERR"+e.toString());
        }
    }

    public void init() {
        try {
            locationManager = (LocationManager) mContext
                    .getSystemService(LOCATION_SERVICE);

            // getting GPS status
            isGPSEnabled = locationManager
                    .isProviderEnabled(LocationManager.GPS_PROVIDER);

            // getting network status
            isNetworkEnabled = locationManager
                    .isProviderEnabled(LocationManager.NETWORK_PROVIDER);


            this.canGetLocation = isGPSEnabled || isNetworkEnabled;

            boolean bErr = false;

            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ) {
                GBLog.e( "GPS", "ACCESS_FINE_LOCATION KO");
                bErr = true;
            }

            if( ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                GBLog.e( "GPS", "ACCESS_COARSE_LOCATION KO");
                bErr = true;
            }

            if( !bErr ) {

                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        MIN_TIME_BW_UPDATES,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES, this);


                if( canGetLocation ) {
                    Location location = locationManager
                            .getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (location != null) {
                        _am.setLocation(location);
                    }
                }
            }

        } catch (Exception e) {
            GBLog.e( "GPS", "ERR"+e.toString());
        }
    }

    /**
     * Stop using GPS listener Calling this function will stop using GPS in your
     * app.
     * */
    public void stopUsingGPS() {
        if (locationManager != null) {

            boolean bErr = false;

            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ) {
                GBLog.e( "GPS", "ACCESS_FINE_LOCATION KO");
                bErr = true;
            }

            if( ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                GBLog.e( "GPS", "ACCESS_COARSE_LOCATION KO");
                bErr = true;
            }

            if( !bErr )
                locationManager.removeUpdates(GPSTracker.this);
        }
    }



    /**
     * Function to check GPS/wifi enabled
     *
     * @return boolean
     * */
    public boolean canGetLocation() {
        return this.canGetLocation;
    }

    /**
     * Function to show settings alert dialog On pressing Settings btn_plus.xml will
     * lauch Settings Options
     * */
    public void showSettingsAlert() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(mContext);

        // Setting DialogHelp Title
        alertDialog.setTitle("GPS is settings");

        // Setting DialogHelp Message
        alertDialog
                .setMessage("GPS is not enabled. Do you want to go to settings menu?");

        // On pressing Settings btn_plus.xml
        alertDialog.setPositiveButton("Settings",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(
                                Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        mContext.startActivity(intent);
                    }
                });

        // on pressing cancel btn_plus.xml
        alertDialog.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

        // Showing Alert Message
        alertDialog.show();
    }

    @Override
    public void onLocationChanged(Location location) {

        _am.setLocation(location);

        /*
        try {

            float bestAccuracy = -1f;
            if (location.getAccuracy() != 0.0f && (location.getAccuracy() < bestAccuracy) || bestAccuracy == -1f) {

                boolean bErr = false;

                if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ) {
                    GBLog.e( "GPS", "ACCESS_FINE_LOCATION KO");
                    bErr = true;
                }

                if( ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    GBLog.e( "GPS", "ACCESS_COARSE_LOCATION KO");
                    bErr = true;
                }

                if( !bErr )
                    locationManager.removeUpdates(this);
            }
            bestAccuracy = location.getAccuracy();
        } catch (NullPointerException e) {
            System.out.print("Caught the NullPointerException");
        }
         */
    }

    @Override
    public void onProviderDisabled(String provider) {
        GBLog.d( TAG, "GPS OFF");
    }

    @Override
    public void onProviderEnabled(String provider) {
        GBLog.d( TAG, "GPS ON");
        init();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

}