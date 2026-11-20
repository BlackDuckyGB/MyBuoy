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
import android.os.SystemClock;
import android.view.Surface;

// Device heading: the direction the phone points to, from the fused rotation-vector sensor
// (gyro + accel + magnetometer), magnetic north. AppManager converts it to true.
//
// Which axis "points": held flat or tilted, it is the top of the screen; held upright, the top of
// the screen points to the sky and its horizontal projection vanishes, so we take the back-camera
// axis instead (the camera itself stays off). Tilting from flat to upright keeps the same heading
// across the switch: the top of the screen hands over to the back of the phone. A hysteresis
// around 45 degrees keeps it from flickering between the two.
public class DeviceHeadingSensor implements SensorEventListener {

    private static final String TAG = "DeviceHeading";

    private static final int SAMPLING_US = 100_000;   // 100 ms

    // |vertical component of the screen normal|: 1 = flat, 0 = upright.
    private static final float TO_CAMERA_BELOW = 0.60f;   // ~53 degrees from flat
    private static final float TO_SCREEN_ABOVE = 0.80f;   // ~37 degrees from flat

    // Jitter filter: a real turn always goes one way; back-and-forth steps are sensor noise.
    private static final double STEP_NOISE_DEG = 0.1;   // smaller step = no direction
    private static final double DEADBAND_DEG   = 1.0;   // below this offset the phone is still
    private static final int    TURN_RUN       = 2;     // same-direction steps to confirm a turn
    private static final double TURN_JUMP_DEG  = 3.0;   // offset that means a turn at once

    // Once the filter has decided the phone is turning, the output CLOSES on the raw value
    // instead of jumping to it: the time below is what it takes to cover half the remaining
    // gap, whatever rate the sensor delivers at. A heading then reads as a sweep and not as a
    // series of steps, on the main map as much as on the sky disc.
    //
    // The price is a lag of about one and a half times this for as long as a turn lasts: four
    // degrees behind a turn at thirty degrees a second, which is already a brisk sweep of the
    // horizon by hand. Standing still there is no lag at all, the output being frozen.
    private static final double TURN_HALFLIFE_MS = 100.0;

    // The gap at which a turn is considered over and the output settled.
    private static final double SETTLED_DEG = 0.1;

    private final SensorManager _sensorManager;
    private final Sensor _sensor;
    private final AppManager _am;

    private final float[] _rotation = new float[9];

    private int _displayRotation = Surface.ROTATION_0;   // main thread only
    private boolean _useCamera = false;
    private boolean _started = false;

    private boolean _hasFiltered = false;
    private double _prevRaw = 0.0;
    private double _filtered = 0.0;
    private int _lastDir = 0;
    private int _run = 0;
    private boolean _turning = false;   // last state, for the debug log only
    private boolean _following = false; // closing on the raw value after a turn was called
    private long _filteredMs = 0;       // when the output last moved

    // No lock here (constructor rule): only system lookups.
    public DeviceHeadingSensor(Context context, AppManager am) {
        _sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        _sensor = (_sensorManager != null) ? _sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) : null;
        _am = am;
    }

    public void start(int displayRotation) {
        _displayRotation = displayRotation;
        if (_sensor == null)
            GBLog.d(TAG, "start: no rotation-vector sensor, cap vue = cap T");
        if (_started || (_sensor == null))
            return;
        _started = _sensorManager.registerListener(this, _sensor, SAMPLING_US);
        GBLog.d(TAG, "start: registered=" + _started + " rotation=" + displayRotation);
    }

    public void stop() {
        if (!_started)
            return;
        _sensorManager.unregisterListener(this);
        _started = false;
        GBLog.d(TAG, "stop");
    }

    public void setDisplayRotation(int displayRotation) {
        _displayRotation = displayRotation;
        GBLog.d(TAG, "display rotation=" + displayRotation);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        SensorManager.getRotationMatrixFromVector(_rotation, event.values);

        // Column j of the matrix = device axis j in world coordinates (East, North, Up).
        float tilt = Math.abs(_rotation[8]);
        if (_useCamera && (tilt > TO_SCREEN_ABOVE)) {
            _useCamera = false;
            GBLog.d(TAG, "axis -> screen top (tilt " + tilt + ")");
        } else if (!_useCamera && (tilt < TO_CAMERA_BELOW)) {
            _useCamera = true;
            GBLog.d(TAG, "axis -> back camera (tilt " + tilt + ")");
        }

        float east, north;
        if (_useCamera) {
            east = -_rotation[2];    // back camera = device -Z
            north = -_rotation[5];
        } else {
            east = screenTopEast();
            north = screenTopNorth();
        }

        double azimuth = Math.toDegrees(Math.atan2(east, north));
        if (azimuth < 0)
            azimuth += 360.0;

        _am.setDeviceHeadingMagnetic(filter(azimuth));
    }

    // Still: the output is frozen while the raw value stays within DEADBAND_DEG of it. Turning
    // (offset past the deadband AND two steps in a row towards it, or an offset past
    // TURN_JUMP_DEG): the output follows the raw value at once. A reversed or null step freezes it.
    private double filter(double raw) {
        if (!_hasFiltered) {
            _hasFiltered = true;
            _prevRaw = raw;
            _filtered = raw;
            _filteredMs = SystemClock.uptimeMillis();
            return raw;
        }

        double step = wrap180(raw - _prevRaw);
        _prevRaw = raw;

        int dir = (Math.abs(step) < STEP_NOISE_DEG) ? 0 : ((step > 0) ? 1 : -1);
        if ((dir != 0) && (dir == _lastDir))
            _run++;
        else
            _run = (dir != 0) ? 1 : 0;
        _lastDir = dir;

        double offset = wrap180(raw - _filtered);
        int offsetDir = (offset > 0) ? 1 : -1;
        boolean confirmed = (_run >= TURN_RUN) && (_lastDir == offsetDir);
        boolean turning = (Math.abs(offset) > TURN_JUMP_DEG)
                || ((Math.abs(offset) >= DEADBAND_DEG) && confirmed);

        if (turning != _turning) {
            _turning = turning;
            GBLog.d(TAG, (turning ? "turning" : "still") + " raw=" + Math.round(raw * 10) / 10.0
                    + " out=" + Math.round(_filtered * 10) / 10.0 + " offset=" + Math.round(offset * 10) / 10.0);
        }

        long now = SystemClock.uptimeMillis();
        long elapsed = now - _filteredMs;
        _filteredMs = now;

        // The filter says WHEN to follow; the easing always finishes what it started, so a turn
        // called and then stopped leaves the output on the bearing and not just short of it.
        if (turning)
            _following = true;

        if (_following) {
            double beta = 1.0 - Math.pow(0.5, elapsed / TURN_HALFLIFE_MS);
            _filtered = wrap360(_filtered + (beta * offset));

            if (Math.abs(offset) < SETTLED_DEG)
                _following = false;
        }

        return _filtered;
    }

    // Back into [0, 360).
    private static double wrap360(double d) {
        d %= 360.0;
        return (d < 0) ? (d + 360.0) : d;
    }

    // Signed angle difference in (-180, 180].
    private static double wrap180(double d) {
        d %= 360.0;
        if (d > 180.0)
            d -= 360.0;
        else if (d <= -180.0)
            d += 360.0;
        return d;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // Top of the screen in device axes depends on the display rotation:
    // 0 -> +Y, 90 -> -X, 180 -> -Y, 270 -> +X.
    private float screenTopEast() {
        switch (_displayRotation) {
            case Surface.ROTATION_90:  return -_rotation[0];
            case Surface.ROTATION_180: return -_rotation[1];
            case Surface.ROTATION_270: return _rotation[0];
            default:                   return _rotation[1];
        }
    }

    private float screenTopNorth() {
        switch (_displayRotation) {
            case Surface.ROTATION_90:  return -_rotation[3];
            case Surface.ROTATION_180: return -_rotation[4];
            case Surface.ROTATION_270: return _rotation[3];
            default:                   return _rotation[4];
        }
    }
}
