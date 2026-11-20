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

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import android.Manifest;
//import android.support.v7.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;


//import android.database.sqlite.SQLiteDatabase;
import org.sqlite.database.sqlite.SQLiteDatabase;

import com.gb.mybuoy.sqlite.AppDB;


import java.io.File;

import static androidx.core.app.ActivityCompat.startActivityForResult;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private AppManager _am = null;
    private ViewsController _vc = null;
    private DeviceHeadingSensor _deviceHeading = null;
    private MotionSensor _motionSensor = null;

    private final int TAG_CODE_PERMISSION_ALL = 1001;

    // #65 : position recue d'une autre application, mise de cote tant que l'app n'est pas prete
    // (permissions accordees et base ouverte), puis consommee une seule fois.
    private Intent _pendingGeoIntent = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // L'activite est declaree avec AppTheme.Launch, dont le seul role est de donner a l'ecran
        // de demarrage du systeme le bleu et l'icone de notre ecran d'attente, pour que la bascule
        // de l'un a l'autre ne se voie pas. Une fois passe, on revient au theme normal.
        setTheme(R.style.AppTheme);

        _am = AppManager.getInstance(this);
        _vc = ViewsController.getInstance(this);
        _deviceHeading = new DeviceHeadingSensor(getApplicationContext(), _am);
        _motionSensor = new MotionSensor(getApplicationContext(), _am);

        // #67 : le thread de recherche est aussi demarre par AppManager.startAllThreadsOnce(), avec
        // les autres, mais CE point d'entree est celui qui le fait repartir. Un retour arriere
        // termine l'Activity (onDestroy -> GeoSearch.stop()) sans forcement tuer le processus ; a la
        // relance suivante, AppManager est toujours le meme singleton et startAllThreadsOnce() ne
        // refait rien, si bien que le thread resterait mort pour le restant de la session et que
        // toute recherche attendrait dans le vide jusqu'a expiration. L'appel est idempotent.
        GeoSearch.start();

        // #65 : lancement a froid depuis une position partagee. On garde l'intent sans l'ouvrir ici :
        // la suite de onCreate() enchaine la demande de permissions puis l'ecran "acces a tous les
        // fichiers", qui passeraient devant le popup, et la base n'est ouverte qu'apres.
        if( GeoUriParser.isSharedLocationIntent(getIntent()) ) {
            _pendingGeoIntent = getIntent();
        }

        // Draw edge-to-edge so the map can extend behind the system bars.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Set No Title
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getSupportActionBar().hide();

        // Hide the system bars in sticky-immersive mode (swipe from edge to reveal).
        applyImmersiveMode();

        _vc.ShowMainView();

        if (true) {


            ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.INTERNET,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                //            Manifest.permission.ACCESS_NOTIFICATION_POLICY
                Manifest.permission.POST_NOTIFICATIONS,}, // #28 : notif du service foreground (ignore < Android 13)
                TAG_CODE_PERMISSION_ALL);
        }

        // Scoped storage (Android 11+): WRITE/READ_EXTERNAL_STORAGE no longer grant
        // raw File access to public SD locations where the land_polygons DB lives.
        // Request "All files access" so MapInfo/Chunk can keep reading the DB by path.
        requestAllFilesAccess();

        //int requestCode = 0;
        //startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), requestCode);
    }

    private void requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    // Fallback to the global all-files-access list if the per-app screen is unavailable.
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                }
            }
        }
    }

    // #81 : code de la demande "choisir un son a ajouter a la bibliotheque".
    private static final int REQ_PICK_ALARM_SOUND = 8101;

    /**
     * #81 : ouvre le selecteur de fichiers du systeme sur les fichiers audio. ACTION_OPEN_DOCUMENT
     * ne demande AUCUNE permission : c'est l'utilisateur qui designe le fichier, et l'application
     * n'obtient de droit que sur celui-la. Le contenu est ensuite COPIE dans le stockage prive de
     * l'application, ce qui le rend independant de l'original.
     */
    public void pickAlarmSound() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        startActivityForResult(i, REQ_PICK_ALARM_SOUND);
    }

    // Le nom que le systeme donne au document choisi, pour nommer la copie.
    private String nomDuDocument(Uri uri) {
        String nom = null;
        try( android.database.Cursor c = getContentResolver().query(uri, null, null, null, null) ) {
            if( (c != null) && c.moveToFirst() ) {
                int i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if( i >= 0 )
                    nom = c.getString(i);
            }
        } catch( Exception ignored ) {
        }
        if( (nom == null) || nom.isEmpty() )
            nom = "sound.mp3";
        return nom;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (resultCode != RESULT_OK)
            return;

        if (requestCode == REQ_PICK_ALARM_SOUND) {
            Uri uri = (resultData != null) ? resultData.getData() : null;
            if (uri == null)
                return;
            try( java.io.InputStream in = getContentResolver().openInputStream(uri) ) {
                String valeur = AlarmSoundCatalog.addUserSound(this, in, nomDuDocument(uri));
                if (valeur != null)
                    ViewsController.getInstance().onAlarmSoundAdded();
            } catch (Exception ex) {
                GBLog.e(getClass().getName(), "ajout du son impossible : " + ex);
            }
            return;
        }

        {
            Uri treeUri = resultData.getData();
            DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);
            grantUriPermission(getPackageName(), treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            //copyFile(sdCard.toString(), "/File.txt", path + "/new", pickedDir);
        }
    }

    // #70 : en multi-fenetre (split screen, vue pop-up, DeX) les barres systeme appartiennent a
    // l'ecran partage : aucune application ne peut les cacher. Insister ne coute pas rien, au
    // contraire -- le systeme marque alors ses insets FORCE_CONSUMING, protection destinee a une
    // application qui croirait l'ecran libre, et le decor mange leur hauteur. D'ou la bande de fond
    // de fenetre en haut, et une GameSurface plus haute que le GBScrollView qui la porte, lequel
    // se mettait a intercepter le geste de pan. On ne demande donc le masquage qu'en plein ecran ;
    // en fenetre la carte passe SOUS les barres, comme le font les autres applications de
    // navigation, et ce sont les commandes qui se decalent (ViewsController.applyOverlayInsets).
    private void applyImmersiveMode() {
        Window window = getWindow();
        if (window == null) return;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller == null) return;

        boolean multiWindow = isMultiWindow();

        if (multiWindow) {
            controller.show(WindowInsetsCompat.Type.systemBars());
        } else {
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }

        GBLog.d(TAG, "applyImmersiveMode: multiWindow=" + multiWindow
                + " -> systemBars " + (multiWindow ? "shown (draw behind)" : "hidden (immersive)"));
    }

    // isInMultiWindowMode() n'existe qu'a partir d'Android 7 ; en dessous le mode n'existe pas.
    private boolean isMultiWindow() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) && isInMultiWindowMode();
    }

    // #70 : l'entree et la sortie de multi-fenetre passent deja par onConfigurationChanged (la
    // taille change), mais ce rappel-ci est le seul garanti quand seul le mode bascule.
    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        GBLog.d(TAG, "onMultiWindowModeChanged: " + isInMultiWindowMode);
        applyImmersiveMode();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveMode();
        _deviceHeading.start(getWindowManager().getDefaultDisplay().getRotation());
        _motionSensor.start();
    }

    // #41 : avec android:configChanges, la rotation n'est plus geree en detruisant/
    // recreant l'Activity -> reappliquer l'immersif (les insets systeme changent).
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyImmersiveMode();
        _deviceHeading.setDisplayRotation(getWindowManager().getDefaultDisplay().getRotation());

        if( _vc != null ) {
            _vc.closeMenuOnRotation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // The sensor listener is not a thread: stop it off-screen, it would drain the battery.
        _deviceHeading.stop();
        _motionSensor.stop();
        // Persist the last position so a restart reopens here instead of 0,0.
        if (_am != null) _am.saveStateNow();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // #86: the place-search thread (#67) and the cache housekeeping thread are NOT stopped here
        // any more. isFinishing() was read as "the app goes away for good", but Back on the map
        // finishes the Activity while Android keeps the process cached: relaunching then reuses
        // that process without going through startAllThreadsOnce() again, and both threads stayed
        // dead for the rest of the session -- a purge request was accepted and never served
        // ("A cleanup is already running" for ever). They now live with the process, like
        // AppManager's two loops, which were never stopped either: both are daemons that sleep
        // almost all the time, and Android takes them down with the process when it reclaims it.
    }

    @Override
    protected void onStart() {
        super.onStart();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStop() {
        super.onStop();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {

        GBLog.d( "MainActivity", String.format("requestCode:%d", requestCode));
        switch (requestCode) {
            case TAG_CODE_PERMISSION_ALL:
                if( _am != null ) {
                    _am.initDB(this);
                    _am.GPSStart(this);
                    //_am.NotificationTest(this);
                }
                consumeGeoIntent();   // #65 : la base est ouverte, la position mise de cote peut s'afficher
                break;
        }
    }

    // #65 : l'app tourne deja et une position arrive d'une autre application (singleTask -> pas de
    // seconde instance, l'intent atterrit ici). La base est ouverte, on traite tout de suite.
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);   // getIntent() doit refleter le dernier intent recu

        if( !GeoUriParser.isSharedLocationIntent(intent) ) {
            return;
        }

        _pendingGeoIntent = intent;
        consumeGeoIntent();
    }

    // Ouvre l'editeur de mark pre-rempli sur la position recue. Ne fait rien tant que la base n'est
    // pas ouverte : l'appel est alors rejoue depuis onRequestPermissionsResult().
    private void consumeGeoIntent() {
        Intent intent = _pendingGeoIntent;
        if( intent == null || _am == null || !_am.isDBInitialized() ) {
            return;
        }

        _pendingGeoIntent = null;

        GeoUriParser.Location loc = GeoUriParser.parse(intent.getData());

        if( loc == null ) {
            // #67 : geo: sans coordonnees, mais avec une adresse en clair. MyBuoy n'a toujours pas
            // de geocodeur hors ligne ; il a maintenant une page de recherche, qui s'ouvre
            // pre-remplie et cherche aussitot.
            String text = GeoUriParser.searchText(intent.getData());

            if( text == null ) {
                ViewsController.notifyError("No coordinates in the shared location");
                return;
            }

            if( _vc != null ) {
                _vc.closeAllDialogs();
                _vc.ShowSearchPage(text);
            }
            return;
        }

        if( _vc != null ) {
            _vc.closeAllDialogs();
        }

        _am.openSharedMarkAt(loc.longitude, loc.latitude, loc.title, loc.iconId, loc.altitude, loc.altitudeWgs84);
    }
}
