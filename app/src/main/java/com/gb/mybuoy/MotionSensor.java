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

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;

// What the phone itself feels, from the accelerometer (every phone, no permission) and the
// gyroscope when there is one. Three states, sent to AppManager on every change:
//  - STILL:    acceleration and rotation almost flat over the last STILL_WINDOW_MS (phone laid
//              down, or held without moving);
//  - STEPS:    regular peaks at a walking cadence (a walker);
//  - AGITATED: it moves, but with no step rhythm (swell, roll, a car, a phone in the hand).
// Callbacks run on a background thread of their own, started by AppManager.startAllThreadsOnce()
// with the other background threads and alive with the process (never stopped; re-checked with
// isAlive() on every start, like ChunkCacheThread). Only the sensor subscription follows the
// screen (start in onResume, stop in onPause).
public class MotionSensor implements SensorEventListener {

    private static final String TAG = "MotionSensor";

    public static final int STATE_UNKNOWN  = 0;
    public static final int STATE_STILL    = 1;
    public static final int STATE_STEPS    = 2;
    public static final int STATE_AGITATED = 3;

    private static final int SAMPLING_US = 20_000;   // 50 Hz

    // Still: over the window, std-dev of |acc| and max |gyro| below these. Measured on the A26:
    // laid down 0.005-0.1 / 0.002-0.01, held in the hand at rest 0.05-0.15 / 0.02-0.14, moving
    // >= 0.4 / >= 1.0. A hand at rest counts as still; a hysteresis keeps it from flickering.
    private static final long   STILL_WINDOW_MS     = 500;
    private static final double STILL_ACC_STD       = 0.20;   // m/s2, to become still
    private static final double STILL_ACC_STD_EXIT  = 0.35;   // m/s2, to leave it
    private static final double STILL_GYRO_MAX      = 0.15;   // rad/s, to become still
    private static final double STILL_GYRO_MAX_EXIT = 0.50;   // rad/s, to leave it (a hand settling
                                                              // after a stop reaches 0.30-0.37)

    // Reference taken when the state becomes still: a very slow movement stays under the window
    // thresholds above, so the still state is also left when the phone has drifted from where it
    // was (absolute measures, no drift with time). After such a break a whole quiet window is
    // needed again before still is validated, with a new reference.
    private static final double GRAVITY_ALPHA      = 0.1;   // low-pass on the acc vector (gravity direction)
    private static final double TILT_ENTRY_DEG     = 2.0;   // max tilt drift across the window to become still
    private static final double TILT_REF_DEG       = 4.0;   // tilt away from the reference -> not still
    private static final double HEADING_REF_DEG    = 5.0;   // cap vue away from the reference -> not still
    private static final int    HEADING_CHECK_EVERY = 5;    // accel samples (~10 Hz)

    // Steps: peaks of the low-passed |acc| above the slow mean.
    private static final double STEP_LP_ALPHA     = 0.25;   // low-pass on |acc| (~2-3 Hz at 50 Hz)
    private static final double STEP_MEAN_ALPHA   = 0.02;   // slow mean (gravity + offset)
    private static final double STEP_PEAK         = 1.2;    // m/s2 above the mean to arm a peak
    private static final double STEP_RESET        = 0.3;    // back under this to re-arm
    private static final long   STEP_MIN_MS       = 300;    // fastest cadence
    private static final long   STEP_MAX_MS       = 1300;   // slowest cadence
    private static final int    STEP_CONFIRM      = 2;      // regular steps in a row to call it walking

    private static final long   STATS_LOG_MS      = 2000;

    private static HandlerThread _thread = null;   // guarded by the class

    private final SensorManager _sensorManager;
    private final Sensor _accel;
    private final Sensor _gyro;
    private final AppManager _am;
    private boolean _started = false;   // main thread only
    private Handler _handler = null;    // main thread only

    // --- Sensor thread only ---------------------------------------------------------------
    private static final int RING = 64;   // > STILL_WINDOW_MS at 50 Hz
    private final double[] _accMag = new double[RING];
    private final double[] _gravX = new double[RING];   // low-passed acc vector, same slots as _accMag
    private final double[] _gravY = new double[RING];
    private final double[] _gravZ = new double[RING];
    private double _gx = 0.0, _gy = 0.0, _gz = 0.0;
    private boolean _gravInit = false;
    private double _refX = 0.0, _refY = 0.0, _refZ = 0.0;   // gravity direction when still began
    private double _refHeading = Double.NaN;                 // cap vue when still began (NaN: none)
    private long _breakMs = 0;                               // last time the reference was broken
    private int _headingTick = 0;
    private final long[] _accTime = new long[RING];
    private int _accHead = 0;
    private int _accCount = 0;
    private double _gyroMag = 0.0;
    private long _gyroTime = 0;
    private double _gyroMaxWin = 0.0;
    private long _gyroMaxTime = 0;

    private double _lp = 0.0;
    private double _mean = SensorManager.GRAVITY_EARTH;
    private boolean _armed = true;
    private long _lastStepMs = 0;
    private int _regularSteps = 0;

    private int _state = STATE_UNKNOWN;
    private long _lastStatsMs = 0;

    // No lock here (constructor rule): only system lookups, the thread is started in start().
    public MotionSensor(Context context, AppManager am) {
        _sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        _accel = (_sensorManager != null) ? _sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
        _gyro = (_sensorManager != null) ? _sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) : null;
        _am = am;
    }

    public void start() {
        if (_started || (_accel == null)) {
            if (_accel == null)
                GBLog.d(TAG, "start: no accelerometer");
            return;
        }

        Looper looper = ensureThreadLooper();
        if ((_handler == null) || (_handler.getLooper() != looper))
            _handler = new Handler(looper);

        _started = _sensorManager.registerListener(this, _accel, SAMPLING_US, _handler);
        if (_gyro != null)
            _sensorManager.registerListener(this, _gyro, SAMPLING_US, _handler);

        GBLog.d(TAG, "start: accel=" + _started + " gyro=" + (_gyro != null));
    }

    public void stop() {
        if (!_started)
            return;

        _sensorManager.unregisterListener(this);
        _started = false;
        GBLog.d(TAG, "stop");
        // Posted behind any event still queued on the sensor thread: the state is unknown again,
        // nothing is measured off-screen, and the next start begins from scratch.
        _handler.post(_reset);
    }

    private final Runnable _reset = new Runnable() {
        @Override
        public void run() {
            _accCount = 0;
            _gravInit = false;
            _breakMs = 0;
            _gyroTime = 0;
            _gyroMaxWin = 0.0;
            _regularSteps = 0;
            _armed = true;
            _state = STATE_UNKNOWN;
            _am.setMotionSensorState(STATE_UNKNOWN);
        }
    };

    /**
     * Started by AppManager.startAllThreadsOnce() alongside the other background threads.
     */
    public static void startThread() {
        ensureThreadLooper();
    }

    // (Re)starts the sensor thread if it is not alive, and returns its looper.
    private static synchronized Looper ensureThreadLooper() {
        if ((_thread == null) || !_thread.isAlive()) {
            _thread = new HandlerThread("MotionSensor");
            // Daemon: it never holds the process alive.
            _thread.setDaemon(true);
            _thread.start();
            GBLog.d(TAG, "sensor thread started");
        }
        return _thread.getLooper();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long now = SystemClock.elapsedRealtime();
        float x = event.values[0], y = event.values[1], z = event.values[2];
        double mag = Math.sqrt(x * x + y * y + z * z);

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            onGyro(mag, now);
            return;
        }

        onAccel(x, y, z, mag, now);
        classify(now);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void onGyro(double mag, long now) {
        _gyroMag = mag;
        _gyroTime = now;
        // Max over the still window: restarts when the current max has aged out.
        if ((mag >= _gyroMaxWin) || ((now - _gyroMaxTime) > STILL_WINDOW_MS)) {
            _gyroMaxWin = mag;
            _gyroMaxTime = now;
        }
    }

    private void onAccel(float x, float y, float z, double mag, long now) {
        if (!_gravInit) {
            _gx = x;
            _gy = y;
            _gz = z;
            _gravInit = true;
        } else {
            _gx += GRAVITY_ALPHA * (x - _gx);
            _gy += GRAVITY_ALPHA * (y - _gy);
            _gz += GRAVITY_ALPHA * (z - _gz);
        }

        _accMag[_accHead] = mag;
        _gravX[_accHead] = _gx;
        _gravY[_accHead] = _gy;
        _gravZ[_accHead] = _gz;
        _accTime[_accHead] = now;
        _accHead = (_accHead + 1) % RING;
        if (_accCount < RING)
            _accCount++;

        // Steps: a peak of the low-passed magnitude above the slow mean, then back under it.
        _lp += STEP_LP_ALPHA * (mag - _lp);
        _mean += STEP_MEAN_ALPHA * (mag - _mean);
        double d = _lp - _mean;

        if (_armed && (d > STEP_PEAK)) {
            _armed = false;
            long dt = now - _lastStepMs;
            if ((dt >= STEP_MIN_MS) && (dt <= STEP_MAX_MS))
                _regularSteps++;
            else if (dt > STEP_MAX_MS)
                _regularSteps = 1;
            // dt < STEP_MIN_MS: a bounce of the same step, ignored but still timestamped
            _lastStepMs = now;
        } else if (!_armed && (d < STEP_RESET)) {
            _armed = true;
        }
    }

    private void classify(long now) {
        // Std-dev of |acc| over the still window.
        int n = 0;
        int oldest = -1;
        double sum = 0.0, sum2 = 0.0;
        for (int i = 0; i < _accCount; i++) {
            int k = (_accHead - 1 - i + RING) % RING;
            if ((now - _accTime[k]) > STILL_WINDOW_MS)
                break;
            sum += _accMag[k];
            sum2 += _accMag[k] * _accMag[k];
            oldest = k;
            n++;
        }
        if (n < 10)
            return;   // not enough samples yet
        double meanW = sum / n;
        double std = Math.sqrt(Math.max(0.0, sum2 / n - meanW * meanW));
        double gyroMax = ((now - _gyroMaxTime) > STILL_WINDOW_MS) ? _gyroMag : _gyroMaxWin;
        double gyroLimit = (_state == STATE_STILL) ? STILL_GYRO_MAX_EXIT : STILL_GYRO_MAX;
        boolean gyroQuiet = (_gyro == null) || ((now - _gyroTime) > STILL_WINDOW_MS) || (gyroMax < gyroLimit);

        boolean walking = (_regularSteps >= STEP_CONFIRM) && ((now - _lastStepMs) <= STEP_MAX_MS);
        if (!walking && ((now - _lastStepMs) > STEP_MAX_MS))
            _regularSteps = 0;

        int state;
        double accLimit = (_state == STATE_STILL) ? STILL_ACC_STD_EXIT : STILL_ACC_STD;
        boolean quiet = (std < accLimit) && gyroQuiet;

        if (quiet && (_state == STATE_STILL)) {
            // Still already: compare with the reference taken when it began.
            String broken = brokenReference();
            if (broken != null) {
                quiet = false;
                _breakMs = now;
                GBLog.d(TAG, "still reference broken: " + broken);
            }
        } else if (quiet) {
            // Becoming still: a whole quiet window since the last break, and no slow tilt across it.
            int newest = (_accHead - 1 + RING) % RING;
            double drift = angleDeg(_gravX[oldest], _gravY[oldest], _gravZ[oldest],
                    _gravX[newest], _gravY[newest], _gravZ[newest]);
            if (((now - _breakMs) < STILL_WINDOW_MS) || (drift > TILT_ENTRY_DEG))
                quiet = false;
        }

        if (quiet)
            state = STATE_STILL;
        else if (walking)
            state = STATE_STEPS;
        else
            state = STATE_AGITATED;

        if ((state == STATE_STILL) && (_state != STATE_STILL)) {
            _refX = _gx;
            _refY = _gy;
            _refZ = _gz;
            _refHeading = _am.getDeviceHeadingMagnetic();
            _headingTick = 0;
        }

        if ((now - _lastStatsMs) >= STATS_LOG_MS) {
            _lastStatsMs = now;
            GBLog.d(TAG, "stats accStd=" + round3(std) + " gyroMax=" + round3(gyroMax)
                    + " steps=" + _regularSteps + " state=" + name(state));
        }

        if (state != _state) {
            GBLog.d(TAG, name(_state) + " -> " + name(state) + " (accStd=" + round3(std)
                    + " gyroMax=" + round3(gyroMax) + " steps=" + _regularSteps + ")");
            _state = state;
            _am.setMotionSensorState(state);
        }
    }

    // Why the phone is no longer where it was when still began, or null if it still is.
    private String brokenReference() {
        double tilt = angleDeg(_refX, _refY, _refZ, _gx, _gy, _gz);
        if (tilt > TILT_REF_DEG)
            return "tilt " + round3(tilt) + " deg";

        if (++_headingTick >= HEADING_CHECK_EVERY) {
            _headingTick = 0;
            double heading = _am.getDeviceHeadingMagnetic();
            if (!Double.isNaN(_refHeading) && !Double.isNaN(heading)) {
                double d = Math.abs(((heading - _refHeading) % 360.0 + 540.0) % 360.0 - 180.0);
                if (d > HEADING_REF_DEG)
                    return "heading " + round3(d) + " deg";
            } else if (Double.isNaN(_refHeading)) {
                _refHeading = heading;   // first measure arrived after still began
            }
        }
        return null;
    }

    private static double angleDeg(double ax, double ay, double az, double bx, double by, double bz) {
        double na = Math.sqrt(ax * ax + ay * ay + az * az);
        double nb = Math.sqrt(bx * bx + by * by + bz * bz);
        if ((na < 1e-6) || (nb < 1e-6))
            return 0.0;
        double c = (ax * bx + ay * by + az * bz) / (na * nb);
        if (c > 1.0)
            c = 1.0;
        else if (c < -1.0)
            c = -1.0;
        return Math.toDegrees(Math.acos(c));
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    public static String name(int state) {
        switch (state) {
            case STATE_STILL:    return "still";
            case STATE_STEPS:    return "steps";
            case STATE_AGITATED: return "agitated";
            default:             return "unknown";
        }
    }
}
