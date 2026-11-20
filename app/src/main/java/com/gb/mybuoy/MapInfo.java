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

import android.graphics.Color;
import android.os.Environment;
import android.util.Log;

import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import androidx.databinding.Observable;


public class MapInfo extends BaseObservable {

    enum TypeStatus {
        NOT_INSTALLED("Not installed",Color.RED),
        INSTALL_IN_PROGRESS("Installing",Color.BLUE),
        INFLATE_IN_PROGRESS("Inflating",Color.BLUE),
        INSTALLED("Installed","#39A275");

        private String name;
        private int color;

        private TypeStatus(String name, int color) {
            this.name = name;
            this.color = color;
        }

        private TypeStatus(String name, String color)  {
            this( name, Color.parseColor(color));
        }

        public int getColor() {
            return color;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final String TAG = "MapInfo";

    // Candidate sub-paths (under each storage volume root from AppManager.getStorageLocations())
    // where a map DB may be installed. Shared by checkStatus() and findInstalledMaps().
    public static final String[] SUBDIRS = {
            "/Android/data/" + BuildConfig.APPLICATION_ID + "/files/", "/", "/MyBuoy/", "/Download/"
    };

    // Name of the world tide grid in the manifest. The tide engine looks its file up by this
    // name, the way Chunk looks up "land_polygons" for the land/sea database.
    public static final String NAME_TIDES = "tides_world";

    // Extensions an installed data file may carry. Not every entry is a database: the tide
    // grid is a packed binary, and naming it ".sqlite3" would be a lie that breaks anything
    // trusting the extension.
    public static final String[] INSTALL_EXTENSIONS = { ".sqlite3", ".bin" };

    // Every entry is a database unless the client knows otherwise. Never read from the
    // manifest: the server names entries, it does not get to name file types.
    private static final String DEFAULT_EXTENSION = ".sqlite3";

    // The tide grid is a packed binary, not a database.
    private static final String EXTENSION_TIDES = ".bin";

    @SerializedName("Title") private String _title;
    @SerializedName("Name") private  String _name;
    @SerializedName("Version") private  Long _version;
    @SerializedName("FileSize") private  Long _fileSize;
    @SerializedName("CompressedSize") private  Long _compressedSize;
    @SerializedName("Url") private  String _url;

    private String _dbPath = null;

    private GBDownloader _dlObj = null;

    private TypeStatus _downloadStatus = TypeStatus.NOT_INSTALLED;

    private boolean _statusIsDirty = true;

    public MapInfo() {
        _statusIsDirty = true;
    }

    /**
     * Extension of the installed file, decided by the client alone.
     *
     * The manifest names an entry, never a file type: whatever a tampered or compromised
     * server sends, the only thing it can influence is which of the names below we match.
     * An entry we do not know lands as a database, as every entry did before tides existed.
     */
    private String installExtension() {
        return extensionFor(_name);
    }

    /**
     * The one extension an entry of this name is allowed to carry. Single source of truth:
     * the same rule names the file we install and recognises the file we find, so the two
     * can never drift apart.
     */
    private static String extensionFor(String name) {

        if (NAME_TIDES.equalsIgnoreCase(name))
            return EXTENSION_TIDES;

        return DEFAULT_EXTENSION;
    }

    // No entry of ours is named longer than this; anything beyond is not ours.
    private static final int MAX_NAME_LENGTH = 64;

    /**
     * The entry name reduced to something that can safely become a file name, or null when
     * it cannot be.
     *
     * The manifest is fetched over the network, so its Name field is untrusted input that
     * ends up in a path. Only the last path element is kept, and only plain characters are
     * allowed: no separator can survive, "." and ".." cannot pass, and nothing can hide
     * behind a leading dot. Our own names -- land_polygons, tides_world, france -- are
     * unaffected.
     */
    static String safeName(String name) {

        if (name == null)
            return null;

        int cut = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String kept = (cut >= 0) ? name.substring(cut + 1) : name;

        if (kept.length() == 0 || kept.length() > MAX_NAME_LENGTH)
            return null;

        if (kept.charAt(0) == '.' || kept.charAt(0) == '-')
            return null;

        for (int index = 0; index < kept.length(); index++) {

            char c = kept.charAt(index);

            boolean allowed = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-';

            if (!allowed)
                return null;
        }

        return kept;
    }

    // "<name>_<version><extension>" : the name the file takes once installed, or null when
    // the entry cannot safely produce one.
    private String installFileName() {

        String name = safeName(_name);

        if (name == null || _version == null)
            return null;

        return String.format("%s_%d%s", name, _version, extensionFor(name));
    }

    private void checkStatus() {

        if( _statusIsDirty ) {
            String dbPath = null;
            String bestDbPath = null;

            String dbFileName = installFileName();

            if (dbFileName != null) {
                AppManager _am = AppManager.getInstance();

                List<File> lstDirs = _am.getStorageLocations();
                String APPLICATION_ID = BuildConfig.APPLICATION_ID;
                String[] subDirs = SUBDIRS;

                File dirDownload = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);


                GBLog.d(TAG, String.format("dirDownload:%s", dirDownload));


                // Room needed = the disk peak during install: the downloaded .gz (.tmp) and the
                // inflated database (.sqlite3) live side by side for a moment, plus ~5% headroom.
                // CompressedSize missing (old manifest) -> fall back on FileSize; uncompressed ->
                // CompressedSize == FileSize.
                long fsz = (_fileSize != null) ? _fileSize : 0;        // inflated database
                long comp = (_compressedSize != null && _compressedSize > 0) ? _compressedSize : fsz; // .gz
                long needed = fsz + comp;
                needed += needed / 20;

                // #86: the volume the user prefers comes first -- the SD card when the developer
                // setting asks for it, the internal storage otherwise -- but it is only a
                // preference: preferredDir falls back on the other volume when the preferred one
                // cannot hold the map, which matters for a database of several gigabytes.
                File preferred = _am.preferredDir(lstDirs.toArray(new File[0]), needed);
                if (preferred != null && preferred.getFreeSpace() > needed) {
                    bestDbPath = preferred.getAbsolutePath() + "/Android/data/" + APPLICATION_ID + "/files/" + dbFileName;
                }

                for (File dir : lstDirs) {
                    for (String subDir : subDirs) {
                        File file = new File(dir.getAbsolutePath() + subDir + dbFileName);
                        if (file.exists()) {
                            dbPath = file.getAbsolutePath();
                            break;
                        }
                    }
                }
            }


            TypeStatus new_downloadStatus = _downloadStatus;
            String new_dbPath = _dbPath;

            if (dbPath != null) {
                File infoFileName = new File(dbPath + ".info");

                if (_dlObj != null) {
                    if (_dlObj.getDlFullSize() > 0 && _dlObj.getDlCurSize() < _dlObj.getDlFullSize()) {
                        new_downloadStatus = TypeStatus.INSTALL_IN_PROGRESS;
                    } else if (_dlObj.getDlInflateFullSize() > 0 && _dlObj.getDlInflateCurSize() < _dlObj.getDlInflateFullSize()) {
                        new_downloadStatus = TypeStatus.INFLATE_IN_PROGRESS;
                    } else {
                        new_downloadStatus = TypeStatus.INSTALL_IN_PROGRESS;
                    }
                } else if (infoFileName.exists()) {
                    new_downloadStatus = TypeStatus.NOT_INSTALLED;
                } else {
                    new_downloadStatus = TypeStatus.INSTALLED;
                }

                new_dbPath = dbPath;
            } else {
                if (_dlObj != null) {
                    if (_dlObj.getDlFullSize() > 0 && _dlObj.getDlCurSize() < _dlObj.getDlFullSize()) {
                        new_downloadStatus = TypeStatus.INSTALL_IN_PROGRESS;
                    } else if (_dlObj.getDlInflateFullSize() > 0 && _dlObj.getDlInflateCurSize() < _dlObj.getDlInflateFullSize()) {
                        new_downloadStatus = TypeStatus.INFLATE_IN_PROGRESS;
                    } else {
                        new_downloadStatus = TypeStatus.INSTALL_IN_PROGRESS;
                    }
                } else
                    new_downloadStatus = TypeStatus.NOT_INSTALLED;
                new_dbPath = bestDbPath;
            }

            if (new_downloadStatus != _downloadStatus) {
                _downloadStatus = new_downloadStatus;
                notifyPropertyChanged(BR.downloadStatusString);
                notifyPropertyChanged(BR.downloadStatusColor);
            }
            if (new_dbPath != _dbPath) {
                _dbPath = new_dbPath;
                notifyPropertyChanged(BR.dbPath);
            }

            _statusIsDirty = false;
        }
    }

    // Discovery only (no DB writes): scans the same candidate locations as checkStatus()
    // for "<name>_<version>.sqlite3" files already present and builds MapInfo objects from them.
    // Persistence is AppManager's job (see scanLocalMapsInfos), mirroring downloadMapsInfos().
    public static List<MapInfo> findInstalledMaps() {
        List<MapInfo> found = new ArrayList<>();

        List<File> storageLocations = AppManager.getInstance().getStorageLocations();
        if (storageLocations == null)
            return found;

        for (File root : storageLocations) {
            for (String subDir : SUBDIRS) {
                File[] files = new File(root.getAbsolutePath() + subDir).listFiles();
                if (files == null) // public dir blocked by scoped storage, or missing
                    continue;

                for (File f : files) {
                    String fileName = f.getName();
                    if (!f.isFile())
                        continue;

                    String extension = null;
                    for (String candidate : INSTALL_EXTENSIONS) {
                        if (fileName.endsWith(candidate)) {
                            extension = candidate;
                            break;
                        }
                    }

                    if (extension == null)
                        continue; // ignore .tmp / .info / subfolders

                    String base = fileName.substring(0, fileName.length() - extension.length());
                    int us = base.lastIndexOf('_'); // last '_' : names like "land_polygons" contain '_'
                    if (us <= 0 || us >= base.length() - 1)
                        continue;

                    long version;
                    try {
                        version = Long.parseLong(base.substring(us + 1));
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    String name = base.substring(0, us);

                    name = safeName(name);

                    if (name == null)
                        continue;

                    // A ".bin" is only ours when it is the tide grid, and the tide grid is
                    // never anything else. Without this pairing any file dropped in these
                    // folders with a plausible name would be picked up as our data.
                    if (!extension.equals(extensionFor(name)))
                        continue;

                    MapInfo mi = new MapInfo();
                    mi.setName(name);
                    mi.setTitle(titleFromName(name));
                    mi.setVersion(version);
                    mi.setFileSize(f.length());
                    mi.setCompressedSize(f.length()); // fichier installé non compressé : compressé == base
                    mi.setUrl(null); // no server: found locally
                    mi.setDbPath(f.getAbsolutePath());
                    found.add(mi);

                    GBLog.d(TAG, String.format("findInstalledMaps: %s v%d -> %s", name, version, f.getAbsolutePath()));
                }
            }
        }
        return found;
    }

    // "land_polygons" -> "Land Polygons" : '_' -> espace + capitale par mot (équiv. ucwords),
    // pour des titres homogènes avec ceux du manifest (cf. gb_scan_maps_dir côté PHP).
    private static String titleFromName(String name) {
        StringBuilder sb = new StringBuilder();
        for (String w : name.replace('_', ' ').split(" ")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) sb.append(w.substring(1));
        }
        return sb.toString();
    }

    @Bindable
    public String getTitle() {
        checkStatus();

        return _title;
    }

    public void setTitle(String title) {
        this._title = title;

        notifyPropertyChanged(BR.title);
    }

    @Bindable
    public String getName() {
        checkStatus();

        return _name;
    }


    public void setName(String name) {
        this._name = name;
        this._statusIsDirty = true;

        notifyPropertyChanged(BR.name);
    }



    @Bindable
    public Long getVersion() {
        checkStatus();

        return _version;
    }


    public void setVersion(long version) {
        this._version = version;
        this._statusIsDirty = true;

        notifyPropertyChanged(BR.version);
    }


    @Bindable
    public Long getFileSize() {
        checkStatus();

        return _fileSize;
    }


    public void setFileSize(Long fileSize) {
        this._fileSize = fileSize;

        notifyPropertyChanged(BR.fileSize);
    }


    // Taille du fichier compressé (.gz) — manifest "CompressedSize". Non bindable :
    // usage interne (calcul d'espace disque) + persistance. == FileSize si non compressée.
    public Long getCompressedSize() {
        return _compressedSize;
    }

    public void setCompressedSize(Long compressedSize) {
        this._compressedSize = compressedSize;
    }


    // Supprime cette map : ligne en base + fichier(s) sur le stockage, puis rafraîchit la liste.
    // Appelé depuis le bouton "Delete" (layout_mapinfo.xml).
    public void delete() {
        AppManager.getInstance().deleteMap(this);
    }



    @Bindable
    public String getUrl() {
        checkStatus();

        return _url;
    }


    public void setUrl(String url) {
        this._url = url;

        notifyPropertyChanged(BR.url);
    }


    @Bindable
    public String getDbPath() {
        checkStatus();

        return _dbPath;
    }


    public void setDbPath(String dbPath) {
        this._dbPath = dbPath;
        this._statusIsDirty = true;

        notifyPropertyChanged(BR.dbPath);
    }

    // Steps of 1024, with the symbols that mean 1024: kB and MB officially mean powers of
    // ten, and the difference reaches seven percent by the gigabyte -- enough to make a map
    // look like it lied about its size.
    private static final long KIBIBYTE = 1024L;
    private static final long MEBIBYTE = KIBIBYTE * 1024L;
    private static final long GIBIBYTE = MEBIBYTE * 1024L;

    private String formatSize( long size ) {
        String sSize;

        if( size >= GIBIBYTE )
            sSize = String.format(Locale.UK, "%.1f GiB", (double)size / GIBIBYTE );
        else if( size >= MEBIBYTE )
            sSize = String.format(Locale.UK, "%.1f MiB", (double)size / MEBIBYTE );
        else if( size >= KIBIBYTE )
            sSize = String.format(Locale.UK, "%.1f KiB", (double)size / KIBIBYTE );
        else
            sSize = String.format(Locale.UK, "%d B", size );

        return sSize;
    }

    @Bindable
    public String getGZFileSize() {
        checkStatus();

        return formatSize(_fileSize);
    }

    @Bindable
    public String getDownloadStatusString() {
        checkStatus();

        String status = _downloadStatus.toString();

        if( _dlObj != null ) {

            double dPercent = 0;
            if( _dlObj.getDlFullSize() > 0 && _dlObj.getDlCurSize() < _dlObj.getDlFullSize() ) {
                dPercent = (double) _dlObj.getDlCurSize() * 100 / _dlObj.getDlFullSize();
            } else if( _dlObj.getDlInflateFullSize() > 0  && _dlObj.getDlInflateCurSize() < _dlObj.getDlInflateFullSize() ) {
                dPercent = (double) _dlObj.getDlInflateCurSize() * 100 / _dlObj.getDlInflateFullSize();
            }
            long speed = _dlObj.getDlSpeed();
            status = String.format("%s (%.2f%% %s/s)", _downloadStatus.toString(), dPercent, formatSize(speed)  );
        }

        return status;
    }

    public TypeStatus getDownloadStatus() {
        checkStatus();

        return _downloadStatus;
    }

    @Bindable
    public int getDownloadStatusColor() {
        checkStatus();

        return _downloadStatus.getColor();
    }

    public void startUpdate() {
        _statusIsDirty = true; // Force recheck-status
        checkStatus();

        GBLog.d( TAG, "startUpdate");

        if( _downloadStatus == TypeStatus.NOT_INSTALLED && _dbPath != null ) {

            _dlObj = new GBDownloader(this._url, this._dbPath);
            // #28 : garder le download vivant en arriere-plan (service foreground + wakelock).
            MapDownloadService.start(AppManager.getInstance().getContext());

            if( _dlObj != null ) {
                _dlObj.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
                    @Override
                    public void onPropertyChanged(Observable sender, int propertyId) {

                        if( propertyId == BR.dlFullSize ||
                            propertyId == BR.dlInflateFullSize ||
                            propertyId == BR.dlSpeed) {
                            //GBLog.d(getClass().getName(), "dlSize changed");
                            _statusIsDirty = true;
                            notifyPropertyChanged(BR.downloadStatusString);
                        }
                        else if( propertyId == BR.dlCurSize ) {
                            //GBLog.d(getClass().getName(), "dlSize changed");
                            //_downloadStatus = TypeStatus.INSTALL_IN_PROGRESS;
                            _statusIsDirty = true;
                            notifyPropertyChanged(BR.downloadStatusString);
                        }
                        else if( propertyId == BR.dlInflateCurSize) {
                            //GBLog.d(getClass().getName(), "dlSize changed");
                            //_downloadStatus = TypeStatus.INFLATE_IN_PROGRESS;
                            _statusIsDirty = true;
                            notifyPropertyChanged(BR.downloadStatusString);
                        }
                        else if( propertyId == BR.finished ) {
                            _dlObj = null;
                            //_downloadStatus = TypeStatus.INSTALLED;
                            _statusIsDirty = true;
                            notifyPropertyChanged(BR.downloadStatusString);
                            notifyPropertyChanged(BR.downloadStatusColor);
                            notifyPropertyChanged(BR.finished); // To Alert AppManager -> New Map OK
                        }
                    }
                });
                //_downloadStatus = TypeStatus.INSTALL_IN_PROGRESS;
                _statusIsDirty = true;
                notifyPropertyChanged(BR.downloadStatusString);
                notifyPropertyChanged(BR.downloadStatusColor);
            }
        } else if( _downloadStatus == TypeStatus.NOT_INSTALLED && _dbPath == null ) {
            // Aucun volume n'a la place (checkStatus n'a pas pu choisir de destination) -> prevenir
            // l'utilisateur au lieu de ne rien faire silencieusement.
            ViewsController.notifyError("Not enough free space to download this map.");
        }
    }
}
