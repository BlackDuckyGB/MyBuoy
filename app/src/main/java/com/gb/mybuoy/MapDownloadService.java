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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

// #28 : service foreground qui garde le processus vivant (+ wakelock partiel + notification
// persistante) pendant le telechargement d'une map offline, pour qu'il ne soit pas suspendu/tue
// quand l'app passe en arriere-plan ou que l'ecran s'eteint. Le telechargement reste gere par le
// daemon de GBDownloader ; ce service le protege et s'arrete des que GBDownloader.hasWork() est faux.
public class MapDownloadService extends Service {

    private static final String CHANNEL_ID = "map_download";
    private static final int NOTIF_ID = 4201;

    private PowerManager.WakeLock _wakeLock = null;
    private Thread _monitor = null;
    private volatile boolean _stop = false;

    public static void start(Context ctx) {
        if( ctx == null )
            return;
        Context app = ctx.getApplicationContext();
        Intent i = new Intent(app, MapDownloadService.class);
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O )
            app.startForegroundService(i);
        else
            app.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if( pm != null ) {
            _wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyBuoy:mapdl");
            _wakeLock.setReferenceCounted(false);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification());
        if( _wakeLock != null && !_wakeLock.isHeld() )
            _wakeLock.acquire(60*60*1000L); // garde-fou 1h
        startMonitor();
        return START_STICKY;
    }

    private void startMonitor() {
        if( _monitor != null )
            return;
        _monitor = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1500); // laisse le daemon prendre la 1ere tache
                    while( !_stop && GBDownloader.hasWork() )
                        Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                stopSelf();
            }
        });
        _monitor.start();
    }

    @Override
    public void onDestroy() {
        _stop = true;
        if( _monitor != null )
            _monitor.interrupt();
        if( _wakeLock != null && _wakeLock.isHeld() )
            _wakeLock.release();
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N )
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        else
            stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setContentTitle("MyBuoy");
        b.setContentText("Downloading offline map…");
        b.setSmallIcon(R.mipmap.ic_launcher);
        b.setOngoing(true);
        return b.build();
    }

    private void createChannel() {
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Offline map download", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if( nm != null )
                nm.createNotificationChannel(ch);
        }
    }
}
