package com.sailracing.app.sensors

import android.Manifest
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sailracing.domain.race.RaceEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidSensorSourceTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun locationToFixMapsAllFields() {
        val location = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 51.14
            longitude = 5.83
            time = 1_234
            speed = 3f
            bearing = 315f
            accuracy = 4f
        }
        val fix = location.toFix()
        assertEquals(51.14, fix.point.latitude)
        assertEquals(5.83, fix.point.longitude)
        assertEquals(1_234, fix.timestampMillis)
        assertEquals(3.0, fix.speedMps!!, 1e-6)
        assertEquals(315.0, fix.courseDegrees!!, 1e-6)
        assertEquals(4.0, fix.accuracyMeters!!, 1e-6)

        val bare = Location(LocationManager.GPS_PROVIDER).apply { latitude = 1.0; longitude = 2.0 }.toFix()
        assertNull(bare.speedMps)
        assertNull(bare.courseDegrees)
        assertNull(bare.accuracyMeters)
        assertTrue(isAtLeastQ())
    }

    @Test
    fun withoutPermissionOnlyTheCompassIsUsed() = runTest(UnconfinedTestDispatcher()) {
        val source = AndroidSensorSource(app)
        val collected = mutableListOf<RaceEvent>()
        val job = launch { source.events().toList(collected) }
        assertTrue(collected.isEmpty())
        job.cancel()
    }

    @Test
    fun gpsFixesAndCompassHeadingsBecomeEvents() = runTest(UnconfinedTestDispatcher()) {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val locationManager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        shadowOf(locationManager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val sensorManager = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotation = ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR)
        shadowOf(sensorManager).addSensor(rotation)

        val source = AndroidSensorSource(app, gpsIntervalMillis = 500)
        val collected = mutableListOf<RaceEvent>()
        val job = launch { source.events().toList(collected) }

        val location = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 51.14
            longitude = 5.83
            time = 42
            speed = 2f
            bearing = 90f
        }
        shadowOf(locationManager).simulateLocation(location)
        ShadowLooper.idleMainLooper()
        val event = ShadowSensorManager.createSensorEvent(4, Sensor.TYPE_ROTATION_VECTOR)
        // Identity rotation: phone flat, pointing north.
        event.values[0] = 0f
        event.values[1] = 0f
        event.values[2] = 0f
        event.values[3] = 1f
        shadowOf(sensorManager).sendSensorEventToListeners(event)
        ShadowLooper.idleMainLooper()

        val fix = collected.filterIsInstance<RaceEvent.FixReceived>().firstOrNull()
        assertIs<RaceEvent.FixReceived>(fix)
        assertEquals(42, fix.fix.timestampMillis)
        val compass = collected.filterIsInstance<RaceEvent.CompassUpdated>().firstOrNull()
        assertIs<RaceEvent.CompassUpdated>(compass)
        assertEquals(0.0, compass.headingDegrees, 1e-6)

        job.cancel()
        assertTrue(shadowOf(locationManager).getRequestLocationUpdateListeners().isEmpty())
    }

    @Test
    fun withoutARotationSensorTheCompassFlowCompletes() = runTest(UnconfinedTestDispatcher()) {
        val source = AndroidSensorSource(app)
        val result = withTimeoutOrNull(1_000) { source.events().toList() }
        assertEquals(emptyList(), result)
        assertNull(withTimeoutOrNull(100) { source.events().firstOrNull() })
    }
}
