package com.sailracing.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.race.ApproachSpeed
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.timer.CuePolicy
import com.sailracing.domain.timer.TimerState
import com.sailracing.domain.wind.WindSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DataStoreRaceRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun repository(scope: CoroutineScope, file: File = File(folder.root, "test.preferences_pb")): DataStoreRaceRepository =
        DataStoreRaceRepository(PreferenceDataStoreFactory.create(scope = scope) { file })

    @Test
    fun defaultsWhenNothingStored() = runTest(UnconfinedTestDispatcher()) {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val repository = repository(scope)
        assertEquals(AppSettings(), repository.settings.first())
        assertEquals(PersistedRace(), repository.persistedRace.first())
        scope.cancel()
    }

    @Test
    fun settingsRoundTrip() = runTest(UnconfinedTestDispatcher()) {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val repository = repository(scope)
        val custom = AppSettings(
            race = AppSettings().race.copy(
                approachSpeed = ApproachSpeed.Manual(2.5),
                minSailingSpeedMps = 0.7,
                courseMinSpeedMps = 0.9,
                fixMaxAgeMillis = 9_000,
                compassOffsetDegrees = 180,
                cuePolicy = CuePolicy(enabled = false, tenSecondWindowSeconds = 60, secondWindowSeconds = 5),
                upwindMaxTwaDegrees = 70,
                downwindMinTwaDegrees = 130,
                maxSamplingTurnRateDegreesPerSecond = 5.0,
                windHistoryCapacity = 100,
            ),
            keepScreenOn = false,
            vibrate = false,
            simulation = SimulationSettings(enabled = true, autoPlayActions = false, speedFactor = 5.0),
        )
        repository.updateSettings { custom }
        assertEquals(custom, repository.settings.first())

        val fallback = custom.copy(race = custom.race.copy(approachSpeed = ApproachSpeed.AverageUpwindVmg(1.1)))
        repository.updateSettings { fallback }
        assertEquals(fallback, repository.settings.first())

        scope.cancel()
    }

    @Test
    fun raceDataRoundTrip() = runTest(UnconfinedTestDispatcher()) {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val repository = repository(scope)
        val line = StartLine(GeoPoint(51.14, 5.83), GeoPoint(51.141, 5.831))
        repository.saveStartLine(line)
        repository.saveWind(WindSettings(200, 50, 150))
        repository.saveTimer(TimerState.Running(123_456))
        val stored = repository.persistedRace.first()
        assertEquals(line, stored.startLine)
        assertEquals(WindSettings(200, 50, 150), stored.wind)
        assertEquals(TimerState.Running(123_456), stored.timer)

        repository.saveStartLine(StartLine(pinEnd = GeoPoint(1.0, 2.0)))
        repository.saveTimer(TimerState.Idle)
        val cleared = repository.persistedRace.first()
        assertEquals(StartLine(pinEnd = GeoPoint(1.0, 2.0)), cleared.startLine)
        assertNull(cleared.startLine.boatEnd)
        assertEquals(TimerState.Idle, cleared.timer)
        scope.cancel()
    }

    @Test
    fun corruptValuesFallBackToDefaults() = runTest(UnconfinedTestDispatcher()) {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val file = File(folder.root, "test.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        store.edit { prefs ->
            prefs[intPreferencesKey("wind.direction")] = 999
            prefs[doublePreferencesKey("line.pin.lat")] = 95.0
            prefs[doublePreferencesKey("line.pin.lon")] = 5.0
            prefs[doublePreferencesKey("settings.approach.speed")] = -1.0
            prefs[doublePreferencesKey("settings.simulation.speed")] = 0.0
        }
        val repository = DataStoreRaceRepository(store)
        val race = repository.persistedRace.first()
        assertEquals(WindSettings(), race.wind)
        assertNull(race.startLine.pinEnd)
        val settings = repository.settings.first()
        assertIs<ApproachSpeed.AverageUpwindVmg>(settings.race.approachSpeed)
        assertEquals(1.5, (settings.race.approachSpeed as ApproachSpeed.AverageUpwindVmg).fallbackMps)
        assertEquals(1.0, settings.simulation.speedFactor)
        scope.cancel()
    }
}
