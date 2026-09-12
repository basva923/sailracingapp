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
import com.sailracing.domain.race.HeadingSource
import com.sailracing.domain.race.RaceEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge

/**
 * Real sensors: GPS position at [gpsIntervalMillis], and the rotation-vector compass when the sailor takes
 * the heading from it ([headingSource]).
 *
 * The plain [LocationManager] is used rather than Play Services so the app works on every device, including
 * de-Googled ones, and has no extra dependency. GPS at 1 Hz is the dominant battery cost and is unavoidable
 * for an accurate time to the line. The compass is registered only when it is the chosen source, and then
 * unbatched at [COMPASS_SAMPLING_MICROS], because answering sooner than the GPS course is the whole point
 * of choosing it. Its heading is read for a phone standing upright on the mast, see [DeviceHeading].
 */
class AndroidSensorSource(
    private val context: Context,
    private val headingSource: HeadingSource = HeadingSource.COURSE_OVER_GROUND,
    private val gpsIntervalMillis: Long = 1_000L,
) : SensorSource {

    override fun events(): Flow<RaceEvent> =
        if (headingSource == HeadingSource.COMPASS) merge(locations(), compass()) else locations()

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
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                trySend(RaceEvent.CompassUpdated(DeviceHeading.fromRotationMatrix(rotation)))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, COMPASS_SAMPLING_MICROS, NO_BATCHING)
        awaitClose { manager.unregisterListener(listener) }
    }

    private companion object {
        /** 5 Hz: a boat does not swing faster than that, and every reading costs a pass through the engine. */
        const val COMPASS_SAMPLING_MICROS = 200_000
        const val NO_BATCHING = 0
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
