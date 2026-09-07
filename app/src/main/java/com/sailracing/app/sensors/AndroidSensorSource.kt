package com.sailracing.app.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.race.RaceEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge

/**
 * Real sensors: GPS position at [gpsIntervalMillis] and the rotation-vector compass at a low rate.
 *
 * The plain [LocationManager] is used rather than Play Services so the app works on every device, including
 * de-Googled ones, and has no extra dependency. GPS at 1 Hz is the dominant battery cost and is unavoidable
 * for an accurate time to the line; the compass is sampled slowly and only matters while stationary.
 */
class AndroidSensorSource(
    private val context: Context,
    private val gpsIntervalMillis: Long = 1_000L,
) : SensorSource {

    override fun events(): Flow<RaceEvent> = merge(locations(), compass())

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun locations(): Flow<RaceEvent> {
        if (!hasLocationPermission()) return emptyFlow()
        return callbackFlow {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = LocationListener { location -> trySend(RaceEvent.FixReceived(location.toFix())) }
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, gpsIntervalMillis, 0f, listener, Looper.getMainLooper())
            awaitClose { manager.removeUpdates(listener) }
        }
    }

    private fun compass(): Flow<RaceEvent> = callbackFlow {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val rotation = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                val azimuth = Math.toDegrees(orientation[0].toDouble())
                trySend(RaceEvent.CompassUpdated(azimuth))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, COMPASS_SAMPLING_MICROS, COMPASS_BATCH_MICROS)
        awaitClose { manager.unregisterListener(listener) }
    }

    private companion object {
        const val COMPASS_SAMPLING_MICROS = 500_000
        const val COMPASS_BATCH_MICROS = 1_000_000
    }
}

/** Converts an Android [Location] to the domain's [PositionFix]; missing speed/bearing become null. */
fun Location.toFix(): PositionFix = PositionFix(
    point = GeoPoint(latitude.coerceIn(-90.0, 90.0), longitude.coerceIn(-180.0, 180.0)),
    timestampMillis = time,
    speedMps = if (hasSpeed()) speed.toDouble() else null,
    courseDegrees = if (hasBearing()) bearing.toDouble() else null,
    accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else null,
)

/** Whether the device can run the location listener at all (used to decide the foreground service type). */
fun isAtLeastQ(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
