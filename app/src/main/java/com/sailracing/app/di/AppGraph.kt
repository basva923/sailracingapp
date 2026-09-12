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
import com.sailracing.app.log.FileSessionLog
import com.sailracing.app.log.SessionLog
import com.sailracing.app.log.SessionLogFiles
import com.sailracing.app.race.RaceSession
import com.sailracing.app.sensors.AndroidSensorSource
import com.sailracing.app.sensors.SensorSource
import com.sailracing.app.sensors.SimulatedSensorSource
import com.sailracing.app.time.SystemClock
import com.sailracing.simulation.SimulationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Manual dependency graph: one place that wires the app together. Kept as an interface so tests
 * and previews can substitute fakes without a DI framework.
 */
interface AppGraph {
    val applicationScope: CoroutineScope
    val repository: RaceRepository
    val cuePlayer: CuePlayer
    val sessionLog: SessionLog
    val raceSession: RaceSession
}

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "sail_racing")

class DefaultAppGraph(context: Context) : AppGraph {
    private val appContext = context.applicationContext

    override val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val repository: RaceRepository = DataStoreRaceRepository(appContext.preferencesDataStore)

    override val cuePlayer: CuePlayer = AndroidCuePlayer(appContext, applicationScope) { raceSession.settings.value.vibrate }

    /**
     * The sessions folder the phone hands out for this app's own files: on a device that is
     * `Android/data/<package>/files/sessions`, which comes off over USB with `adb pull` and shows up in a
     * file manager, so a log can actually be got at. Without external storage the private folder does.
     */
    override val sessionLog: SessionLog = FileSessionLog(
        files = SessionLogFiles(File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "sessions")),
        scope = applicationScope,
        enabled = { raceSession.settings.value.logSessions },
    )

    private val versionName: String =
        runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty() }.getOrDefault("")

    /**
     * The simulations that have been run, by their id. Running one takes a moment (a whole afternoon of
     * sailing, second by second), so a simulation the sailor switches back to is not run again.
     */
    private val simulations = ConcurrentHashMap<String, SimulationResult>()

    override val raceSession: RaceSession = RaceSession(
        repository = repository,
        sensorSourceFactory = { settings, clock ->
            val simulation = settings.simulation
            if (simulation.enabled) {
                val scenario = simulation.scenario
                val result = simulations.computeIfAbsent(scenario.id) { scenario.build() }
                SimulatedSensorSource(result, clock, includeActions = simulation.autoPlayActions)
            } else {
                AndroidSensorSource(appContext, settings.race.headingSource)
            } as SensorSource
        },
        cuePlayer = cuePlayer,
        scope = applicationScope,
        log = sessionLog,
        versionName = versionName,
        baseClock = SystemClock,
    )
}

/** The app-wide graph, reachable from any context. */
val Context.appGraph: AppGraph
    get() = (applicationContext as SailRacingApplication).graph
