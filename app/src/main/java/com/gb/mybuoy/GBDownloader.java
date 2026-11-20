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

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;


public class GBDownloader extends BaseObservable {

    private static final String TAG = "GBDownloader";

    private static final ReadWriteLock _readWriteLock = new ReentrantReadWriteLock();
    private static final Lock _writeLock = _readWriteLock.writeLock();
    private static final Lock _readLock = _readWriteLock.readLock();



    @Expose(serialize = true, deserialize = true)
    @SerializedName("Url")
    private final String _url;

    private final String _outFileName;
    private final String _tmpFileName;
    private final String _infoFileName;


    @Expose(serialize = true, deserialize = true)
    @SerializedName("FullSize")
    private Long _dlFullSize;
    private long _dlInflateFullSize = 0;

    @Expose(serialize = true, deserialize = true)
    @SerializedName("StepSize")
    private Long _dlCurSize;
    private long _dlInflateCurSize = 0;

    private long _dlSpeed = 0;


    private boolean _finished = false;

    private volatile boolean _fatal = false; // echec definitif (gzip corrompu / disque plein a l'inflate)


    private static List<GBDownloader> tasks = new ArrayList<GBDownloader>();

    private static volatile boolean _daemonBusy = false;

    private static Thread daemonThread =  new Thread() {
        @Override
        public void run() {
            GBDownloader curTask = null;

            try {
                while (true) {

                    curTask = null;

                    _writeLock.lock();
                    try {
                        if( tasks.size() > 0 ) {
                            curTask = tasks.get(0);
                            tasks.remove(curTask);
                        }
                    } finally {
                        _writeLock.unlock();
                    }

                    if( curTask != null ) {
                        _daemonBusy = true;
                        boolean bOK = curTask.Download();
                        if( !bOK ) {
                            if( !curTask._fatal ) {
                                _writeLock.lock();
                                try {
                                    tasks.add( curTask );  // Push at the End (reprise reseau)
                                } finally {
                                    _writeLock.unlock();
                                }
                            }
                            // fatal -> deja nettoye + utilisateur prevenu : on abandonne
                        } else {
                            curTask.setFinished(true);
                        }
                        _daemonBusy = false;
                    }

                    sleep(1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                if( curTask != null ) {
                    _writeLock.lock();
                    try {
                        tasks.add( curTask );  // Push at the End
                    } finally {
                        _writeLock.unlock();
                    }
                }
            }
        }
    };


    public GBDownloader( String url, String outFileName) {

        this._url = url;
        this._outFileName = outFileName;
        this._tmpFileName = outFileName + ".tmp";
        this._infoFileName = outFileName + ".info";

        _writeLock.lock();
        try {
            tasks.add(this);
            if( daemonThread.getState() == Thread.State.NEW )
                daemonThread.start();
        } finally {
            _writeLock.unlock();
        }
    }

    // #28 : le service foreground s'en sert pour savoir s'il doit rester actif.
    public static boolean hasWork() {
        _readLock.lock();
        try {
            return _daemonBusy || tasks.size() > 0;
        } finally {
            _readLock.unlock();
        }
    }

    public String getUrl() {
        String url = "";

        _readLock.lock();
        try {
            if( _url != null )
                url = _url;
        } finally {
            _readLock.unlock();
        }

        return url;
    }

    @Bindable
    public long getDlFullSize() {
        long dlSize = 0;

        _readLock.lock();
        try {
            if( _dlFullSize != null )
                dlSize = _dlFullSize;
        } finally {
            _readLock.unlock();
        }

        return dlSize;
    }

    @Bindable
    public long getDlCurSize() {
        long dlSize = 0;

        _readLock.lock();
        try {
            if( _dlCurSize != null )
                dlSize = _dlCurSize;
        } finally {
            _readLock.unlock();
        }

        return dlSize;
    }

    @Bindable
    public long getDlSpeed() {
        long dlSpeed = 0;

        _readLock.lock();
        try {
            dlSpeed = _dlSpeed;
        } finally {
            _readLock.unlock();
        }

        return dlSpeed;
    }


    @Bindable
    public long getDlInflateFullSize() {
        long dlSize = 0;

        _readLock.lock();
        try {
            dlSize = _dlInflateFullSize;
        } finally {
            _readLock.unlock();
        }

        return dlSize;
    }

    @Bindable
    public long getDlInflateCurSize() {
        long dlSize = 0;

        _readLock.lock();
        try {
            dlSize = _dlInflateCurSize;
        } finally {
            _readLock.unlock();
        }

        return dlSize;
    }

    @Bindable
    public boolean isFinished() {
        boolean finished = false;

        _readLock.lock();
        try {
            finished = _finished;
        } finally {
            _readLock.unlock();
        }

        return finished;
    }

    public void setFinished(boolean _finished) {
        _writeLock.lock();
        try {
            this._finished = _finished;
        } finally {
            _writeLock.unlock();
        }
        notifyPropertyChanged(BR.finished);
    }

    public String getInfoFileName() {
        String infoFileName;

        _readLock.lock();
        try {
            infoFileName = this._infoFileName;
        } finally {
            _readLock.unlock();
        }
        return infoFileName;
    }

    public boolean Download() {
        boolean bOK = false;
        _fatal = false;
        long contentLenght = 0;
        boolean acceptRangesBytes = false;

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.excludeFieldsWithoutExposeAnnotation();
        Gson gson = gsonBuilder.create();

        File fileInfo = null;
        File fileTmp  = null;
        File fileOut  = null;

        long dlFullSize = 0;
        long dlCurSize = 0;
        long dlInflateFullSize = 0;
        long dlInflateCurSize = 0;
        String dlURL = null;

        _readLock.lock();
        try {
            fileInfo = new File(_infoFileName);
            fileTmp = new File(_tmpFileName);
            fileOut = new File(_outFileName);

            dlURL = this._url;

            if( this._dlFullSize != null )
                dlFullSize = this._dlFullSize;

            if( this._dlCurSize != null )
                dlCurSize = this._dlCurSize ;

            dlInflateFullSize = 0;
            dlInflateCurSize = 0;

        } finally {
            _readLock.unlock();
        }


        // Step1 get SizeFile
        try {
            HttpClient client = new DefaultHttpClient();
            HttpHead headMethod = new HttpHead(dlURL);
            HttpResponse response = client.execute(headMethod);
            // Examine the response status
            GBLog.d(TAG, response.getStatusLine().toString());

            int statusCode = response.getStatusLine().getStatusCode();
            Header[] headers = response.getAllHeaders();

            if( statusCode == 200 && headers != null ) {
                for(Header header : headers) {
                    if( header.getName().equalsIgnoreCase("content-length"))
                        contentLenght = Long.parseLong(header.getValue());
                    else if( header.getName().equalsIgnoreCase("accept-ranges"))
                        acceptRangesBytes = header.getValue().equalsIgnoreCase("bytes");
                }
            }

        } catch (Exception e){
            GBLog.e( TAG, "ERR"+e.toString());
        }

        if( acceptRangesBytes && contentLenght > 0 ) {

            GBDownloader oldState = null;

            if( fileInfo.exists() ) {
                try {
                    FileReader json = new FileReader(fileInfo);
                    oldState = gson.fromJson(json, GBDownloader.class);
                    json.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            dlFullSize = contentLenght;
            dlCurSize = 0;


            if( oldState != null ) {

                if( oldState.getUrl().equalsIgnoreCase(dlURL) && oldState.getDlFullSize() == dlFullSize) {
                    dlCurSize = oldState.getDlCurSize();
                }
            }


        } else {
            dlFullSize = 0;
            dlCurSize = 0;
        }

        _writeLock.lock();
        try {
            this._dlFullSize = dlFullSize;
            this._dlCurSize = dlCurSize;
            this._dlInflateFullSize = dlInflateFullSize;
            this._dlInflateCurSize = dlInflateCurSize;

            try {
                File parentDir = new File( fileInfo.getParent() );
                boolean result = parentDir.mkdirs();

                FileWriter json = new FileWriter(fileInfo);
                gson.toJson(this, GBDownloader.class, json);
                json.close();

            } catch (java.io.IOException e) {
                e.printStackTrace();
            }

        } finally {
            _writeLock.unlock();
        }
        notifyPropertyChanged( BR.dlInflateFullSize);
        notifyPropertyChanged( BR.dlInflateCurSize);
        notifyPropertyChanged( BR.dlFullSize);
        notifyPropertyChanged( BR.dlCurSize);



        if( dlCurSize == 0 ) {
            if( fileTmp.exists() )
                fileTmp.delete();
        }

        if( dlFullSize > dlCurSize ) {
            // #27 : un SEUL GET pour toute la plage restante (avant : 1 GET par Mo) + gros buffer
            // 256 Ko alloue une seule fois (avant : 256 o realloue a chaque read -> ~12000 read()/s)
            // + ecriture .info et calcul du debit au plus 1x/s (avant : a chaque Mo).
            final int READ_BUFFER = 256*1024;

            long lastTime = System.nanoTime();   // fenetre du debit affiche
            long lastInfoTime = lastTime;         // derniere ecriture du .info (resume)
            long sizeTime = 0;

            try {
                HttpClient client = new DefaultHttpClient();
                HttpGet getMethod = new HttpGet(dlURL);
                getMethod.setHeader("Connection", "keep-alive");
                getMethod.setHeader("Range", "bytes=" + dlCurSize + "-" + (dlFullSize - 1));

                HttpResponse response = client.execute(getMethod);
                GBLog.d(TAG, response.getStatusLine().toString());
                int statusCode = response.getStatusLine().getStatusCode();

                // 206 = Partial Content (Range honore) ; 200 = serveur ignore le Range -> tout depuis 0.
                if( statusCode == 206 || statusCode == 200 ) {
                    if( statusCode == 200 )
                        dlCurSize = 0;

                    HttpEntity entity = response.getEntity();
                    InputStream in = new BufferedInputStream(entity.getContent(), READ_BUFFER);
                    RandomAccessFile out = new RandomAccessFile(fileTmp, "rw");
                    out.seek(dlCurSize);

                    byte[] buffer = new byte[READ_BUFFER];
                    int bytes;
                    while( (bytes = in.read(buffer)) > 0 ) {
                        out.write(buffer, 0, bytes);
                        dlCurSize += bytes;
                        sizeTime += bytes;

                        long now = System.nanoTime();
                        int deltaTime = (int) ((now - lastTime) / 1000000);
                        if( deltaTime >= 1000 ) {
                            _writeLock.lock();
                            try {
                                this._dlSpeed = sizeTime * 1000 / deltaTime;
                                this._dlCurSize = dlCurSize;
                            } finally {
                                _writeLock.unlock();
                            }
                            notifyPropertyChanged(BR.dlSpeed);
                            notifyPropertyChanged(BR.dlCurSize);
                            sizeTime = 0;
                            lastTime = now;
                        }

                        // Point de reprise (.info) rafraichi au plus 1x/s au lieu de chaque Mo.
                        if( (int) ((now - lastInfoTime) / 1000000) >= 1000 ) {
                            _writeLock.lock();
                            try {
                                this._dlCurSize = dlCurSize;
                                FileWriter json = new FileWriter(fileInfo);
                                gson.toJson(this, GBDownloader.class, json);
                                json.close();
                            } finally {
                                _writeLock.unlock();
                            }
                            lastInfoTime = now;
                        }
                    }
                    in.close();
                    out.close();

                    // .info final : position atteinte (pour un resume si l'inflate echoue).
                    _writeLock.lock();
                    try {
                        this._dlCurSize = dlCurSize;
                        FileWriter json = new FileWriter(fileInfo);
                        gson.toJson(this, GBDownloader.class, json);
                        json.close();
                    } finally {
                        _writeLock.unlock();
                    }
                } else {
                    GBLog.e(TAG, String.format("statusCode:%d", statusCode));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        bOK = (dlFullSize == dlCurSize);

        if( bOK && (dlFullSize > 0) ) {

            // UNGZIP
            final int BUFFER_SIZE = 1024*1024; // 1M Buffer
            long lastTime = System.nanoTime();
            long sizeTime = 0;


            bOK = false;

            try {

                boolean bModeRealInflateSize = false;

                if( bModeRealInflateSize ) { // Work Only whit Compressed File < 4Go
                    RandomAccessFile raf = new RandomAccessFile(fileTmp, "r");
                    raf.seek(raf.length() - 4);
                    int b4 = raf.read();
                    int b3 = raf.read();
                    int b2 = raf.read();
                    int b1 = raf.read();
                    dlInflateFullSize = (b1 << 24) | (b2 << 16) + (b3 << 8) + b4;
                    raf.close();

                    GBLog.d(TAG, String.format("dlInflateFullSize=%d", dlInflateFullSize));

                    _writeLock.lock();
                    try {
                        this._dlInflateFullSize = dlInflateFullSize;
                    } finally {
                        _writeLock.unlock();
                    }
                    notifyPropertyChanged( BR.dlInflateFullSize);
                }
                else {
                    dlInflateFullSize = dlFullSize;

                    _writeLock.lock();
                    try {
                        this._dlInflateFullSize = dlInflateFullSize;
                    } finally {
                        _writeLock.unlock();
                    }
                    notifyPropertyChanged( BR.dlInflateFullSize);
                }

                FileInputStream in = new FileInputStream(fileTmp);
                FileOutputStream out = new FileOutputStream(fileOut,false);
                GZIPInputStream inGZ = null;
                CountingOutputStream coStrem = null;
                //ByteArrayOutputStream baos = null;

                if( bModeRealInflateSize ) {
                    inGZ = new GZIPInputStream(in, BUFFER_SIZE);
                } else {
                    //baos = new ByteArrayOutputStream(BUFFER_SIZE);
                    //coStrem = new CountingOutputStream(baos);
                    coStrem = new CountingOutputStream(new FileOutputStream("/dev/null"));
                    inGZ = new GZIPInputStream(new TeeInputStream(in, coStrem, true), BUFFER_SIZE);
                }

                int bytesRead;
                int bytesCompressedRead;
                byte[] data = new byte[BUFFER_SIZE];
                while ((bytesRead = inGZ.read(data)) != -1) {
                    out.write(data, 0, bytesRead );

                    if( bModeRealInflateSize ) {
                        bytesCompressedRead = bytesRead;
                    } else {
                        bytesCompressedRead = coStrem.resetCount();
                        //baos.reset();

                    }
                    dlInflateCurSize += bytesCompressedRead;
                    _writeLock.lock();
                    try {
                        this._dlInflateCurSize = dlInflateCurSize;
                    } finally {
                        _writeLock.unlock();
                    }
                    // See below (Only Notify each 1s)
                    //notifyPropertyChanged( BR.dlInflateCurSize);

                    long now = System.nanoTime();
                    // Change nanoseconds to milliseconds (1 nanosecond = 1000000 milliseconds).
                    int deltaTime = (int) ((now - lastTime)/ 1000000 );
                    sizeTime += bytesCompressedRead;
                    if( deltaTime >= 1000 ) {

                        _writeLock.lock();
                        try {
                            this._dlSpeed = sizeTime * 1000 / deltaTime;
                        } finally {
                            _writeLock.unlock();
                        }
                        notifyPropertyChanged(BR.dlSpeed);
                        notifyPropertyChanged( BR.dlInflateCurSize);

                        sizeTime = 0;
                        lastTime = now;
                    }
                }
                out.close();
                inGZ.close();
                in.close();

                bOK = true;

            } catch (IOException e) {
                e.printStackTrace();
                _fatal = true; // gzip corrompu ou disque plein -> ne pas relancer
            }
        }

        if( bOK ) {
            if( fileTmp.exists() )
                fileTmp.delete();

            if( fileInfo.exists() )
                fileInfo.delete();
        } else if( _fatal ) {
            // Echec definitif : on nettoie .tmp/.info et on n'insiste pas ; l'utilisateur est prevenu
            // et relancera manuellement s'il le souhaite (les interruptions reseau, elles, reprennent).
            if( fileTmp.exists() )
                fileTmp.delete();
            if( fileInfo.exists() )
                fileInfo.delete();
            ViewsController.notifyError("Map download failed (corrupt data or not enough space). Please try again.");
        }

        return bOK;
    }
}
