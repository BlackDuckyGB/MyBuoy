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

import android.graphics.Canvas;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceHolder;

import java.util.Locale;

public class GameThread extends Thread {
    // #53 : volatile. Ce drapeau est ecrit par le thread UI (surfaceDestroyed -> setRunning(false))
    // et lu par le thread de rendu dans sa boucle : sans volatile, rien ne garantit que l'ordre
    // d'arret lui parvienne, et le join() d'en face peut attendre indefiniment.
    private volatile boolean running;
    private GameSurface gameSurface;
    private SurfaceHolder surfaceHolder;

    // Target frame rate. FPS_LOW_RESOURCE is the one used by low-resource mode (#85): both values
    // sit here, one line each, to be tuned from experience.
    private static final long FPS = 25;
    private static final long FPS_LOW_RESOURCE = 15;
    private double fps = 0;

    private AppManager _am;

    // Use the GPU-backed canvas when available (API 26+); fall back to the
    // software canvas if hardware locking ever fails on a frame.
    private boolean useHardwareCanvas = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);


    public GameThread(GameSurface gameSurface, SurfaceHolder surfaceHolder)  {
        this.gameSurface= gameSurface;
        this.surfaceHolder= surfaceHolder;
        this._am = AppManager.getInstance();
    }

    @Override
    public void run()  {
        long startTime = System.nanoTime();
        long fpsLastime = System.nanoTime();

        while(running)  {
            Canvas canvas= null;
            long tFrame0 = System.nanoTime();
            long tLocked = tFrame0;          
            long tUpdated = tFrame0;         
            long tDrawn = tFrame0;           
            try {
                // Get Canvas from Holder and lock it (hardware-accelerated when possible).
                if (useHardwareCanvas) {
                    try {
                        canvas = this.surfaceHolder.lockHardwareCanvas();
                    } catch (Throwable t) {
                        // GPU canvas not usable here: drop back to software for good.
                        GBLog.w("GAMETHREAD", "lockHardwareCanvas failed, falling back to software: " + t);
                        useHardwareCanvas = false;
                        canvas = this.surfaceHolder.lockCanvas();
                    }
                } else {
                    canvas = this.surfaceHolder.lockCanvas();
                }

                // #53 : lockCanvas() rend null des que la Surface n'est plus valide (destruction en cours
                // au passage en arriere-plan). Le synchronized(canvas) d'origine la deferencait alors,
                // d'ou une NullPointerException tracee en E a chaque mise en arriere-plan. On saute la
                // frame : running est deja a false, la boucle sort au tour suivant.
                if( canvas != null ) {

                    tLocked = System.nanoTime();

                    // Synchronized
                    synchronized (canvas)  {
                        this.gameSurface.update();
                        tUpdated = System.nanoTime();
                        this.gameSurface.draw(canvas);
                        tDrawn = System.nanoTime();  
                    }
                }
            }catch(Exception e)  {
                // With the STACK. Without it, an exception thrown here every ten seconds for an
                // afternoon said only its own name: the frame was lost, the screen kept the
                // previous one, and nothing said where it came from.
                GBLog.e( "GAMETHREAD", "exception in update or draw, frame lost", e);
            }catch(OutOfMemoryError e)  {
                // Heap exhausted (e.g. a chunk too big for a dense city) -> skip this frame
                // instead of dying. OutOfMemoryError is an Error, not an Exception, so the
                // catch above would NOT catch it and the render thread would crash (FATAL).
                // The failed allocation is rolled back; a GC frees it. Stay alive.
                GBLog.e( "GAMETHREAD", "OOM in draw, skip frame: " + e);
            } finally {
                if(canvas!= null)  {
                    // Unlock Canvas.
                    this.surfaceHolder.unlockCanvasAndPost(canvas);

                    // With lockHardwareCanvas the draw() above only RECORDS the
                    // display list; the actual rasterisation happens here, in the post. Both
                    // are measured, otherwise the numbers would blame the wrong step.
                    perfAccumulate(tFrame0, tLocked, tUpdated, tDrawn, System.nanoTime());
                }
            }
            long now = System.nanoTime() ;
            // Interval to redraw game
            // (Change nanoseconds to milliseconds)
            double deltaTime = (now - startTime)/1000000;
            // #85: the rate is read back on every frame -- the Parameters switch must show at once,
            // without restarting the renderer. Division in double: 1000/15 is 66 in integer maths,
            // and that gap would be paid on every frame.
            long fpsCible = ((_am != null) && _am.getLowResourceMode()) ? FPS_LOW_RESOURCE : FPS;
            double fpsTime = 1000 / (double) fpsCible;
            fps = 1000 / deltaTime;
            long waitTime = (long)(fpsTime - deltaTime) - 2; // 1 correction to better speed



            //if(waitTime < 10)  {
            //    waitTime= 10; // Millisecond.
            //}
            if(waitTime < 0)  {
                waitTime= 0; // Millisecond.
            }
            //System.out.print(" Wait Time="+ waitTime);
            //GBLog.d( "GameThread", String.format("WT=%d FPS=%.1f/%d", waitTime, fps, FPS) );

            //waitTime= 0;

            if( waitTime > 0 ) {
                try {
                    // Sleep.
                    this.sleep(waitTime);
                } catch (InterruptedException e) {

                }
            }

            // Recalcule FPS apres le Sleep (Qui devrait etre  = FPS)
            now = System.nanoTime() ;
            deltaTime = (now - startTime)/1000000;
            fps = 1000 / deltaTime;

            deltaTime = (now - fpsLastime)/1000000;
            if( deltaTime > 1000 ) {
                this._am.setFps(fps);
                MemLog.log("GameThread frame");   // heap 1x/s (leak-hunt)
                fpsLastime = System.nanoTime();
            }


            startTime = System.nanoTime();
            //System.out.print(".");
        }
    }

    public void setRunning(boolean running)  {
        this.running= running;
    }

    // ------------------------------------------------------------------ PERF
    // Cost of each step of a frame, averaged over one second. Feeds the "Frame" line of the
    // debug panel, and a log line when Debug mode is on. Born of the land_polygons slowdown:
    // splitting recording from rasterisation is what showed the cost was in HWUI and not here.

    private long _perfLockNs = 0;
    private long _perfUpdateNs = 0;
    private long _perfRecordNs = 0;
    private long _perfPostNs = 0;
    private int  _perfFrames = 0;
    private long _perfLastLogMs = 0;

    private void perfAccumulate(long tFrame0, long tLocked, long tUpdated, long tDrawn, long tPosted) {
        _perfLockNs   += tLocked  - tFrame0;
        _perfUpdateNs += tUpdated - tLocked;
        _perfRecordNs += tDrawn   - tUpdated;
        _perfPostNs   += tPosted  - tDrawn;
        _perfFrames++;

        long nowMs = System.currentTimeMillis();

        if( _perfLastLogMs == 0 ) {
            _perfLastLogMs = nowMs;
            return;
        }

        if( (nowMs - _perfLastLogMs) < 1000 )
            return;

        double n = _perfFrames;
        double lock   = _perfLockNs   / 1000000.0 / n;
        double update = _perfUpdateNs / 1000000.0 / n;
        double record = _perfRecordNs / 1000000.0 / n;
        double post   = _perfPostNs   / 1000000.0 / n;

        // Debug panel, "Frame" line. Fed whatever the log level: the reading costs four
        // timestamps a frame, and it is the only way to see any of this without a cable.
        if( _am != null )
            _am.setFrameTimes(update, record, post, useHardwareCanvas);

        if( GBLog.isDebug() )
            GBLog.d("PERF", String.format(Locale.US,
                    "frame: fps=%.1f n=%d lock=%.1f upd=%.1f rec=%.1f post=%.1f busy=%.1fms hw=%b",
                    fps, _perfFrames, lock, update, record, post,
                    lock + update + record + post, useHardwareCanvas));

        _perfLockNs = 0;
        _perfUpdateNs = 0;
        _perfRecordNs = 0;
        _perfPostNs = 0;
        _perfFrames = 0;
        _perfLastLogMs = nowMs;
    }
}
