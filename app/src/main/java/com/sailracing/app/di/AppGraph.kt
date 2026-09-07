package com.sailracing.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.sailracing.app.SailRacingApplication
import com.sailracing.app.audio.AndroidCuePlayer
import com.sailracing.app.audio.CuePlayer
import com.sailracing.app.data.DataStoreRaceRepository
import com.sailracing.app.data.RaceRepository
import com.sailracing.app.race.RaceSession
import com.sailracing.app.sensors.AndroidSensorSource
import com.sailracing.app.sensors.SensorSource
import com.sailracing.app.sensors.SimulatedSensorSource
import com.sailracing.app.time.SystemClock
import com.sailracing.simulation.StandardRaceScenario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency graph: one place that wires the app together. Kept as an interface so tests
 * and previews can substitute fakes without a DI framework.
 */
interface AppGraph {
    val applicationScope: CoroutineScope
    val repository: RaceRepository
    val cuePlayer: CuePlayer
    val raceSession: RaceSession
}

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "sail_racing")

class DefaultAppGraph(context: Context) : AppGraph {
    private val appContext = context.applicationContext

    override val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val repository: RaceRepository = DataStoreRaceRepository(appContext.preferencesDataStore)

    override val cuePlayer: CuePlayer = AndroidCuePlayer(appContext, applicationScope) { raceSession.settings.value.vibrate }

    private val simulation by lazy { StandardRaceScenario.build() }

    override val raceSession: RaceSession = RaceSession(
        repository = repository,
        sensorSourceFactory = { simulationSettings, clock ->
            if (simulationSettings.enabled) {
                SimulatedSensorSource(simulation, clock, includeActions = simulationSettings.autoPlayActions)
            } else {
                AndroidSensorSource(appContext)
            } as SensorSource
        },
        cuePlayer = cuePlayer,
        scope = applicationScope,
        baseClock = SystemClock,
    )
}

/** The app-wide graph, reachable from any context. */
val Context.appGraph: AppGraph
    get() = (applicationContext as SailRacingApplication).graph
