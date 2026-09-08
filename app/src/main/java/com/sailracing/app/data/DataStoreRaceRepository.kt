package com.sailracing.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sailracing.domain.course.GridSettings
import com.sailracing.domain.course.GridSpec
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.race.ApproachSpeed
import com.sailracing.domain.race.RaceSettings
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.timer.CuePolicy
import com.sailracing.domain.timer.TimerState
import com.sailracing.domain.wind.WindSettings
import com.sailracing.simulation.SimulationCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What is stored for a square size the sailor left to the racing area itself. */
private const val AUTOMATIC_CELL_SIZE = 0.0

/** Stores settings and race data in a Preferences DataStore. */
class DataStoreRaceRepository(private val dataStore: DataStore<Preferences>) : RaceRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { it.toSettings() }.distinctUntilChanged()

    override val persistedRace: Flow<PersistedRace> = dataStore.data.map { it.toPersistedRace() }.distinctUntilChanged()

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    override suspend fun saveStartLine(line: StartLine) {
        dataStore.edit { prefs ->
            prefs.writePoint(Keys.PIN_LAT, Keys.PIN_LON, line.pinEnd)
            prefs.writePoint(Keys.BOAT_LAT, Keys.BOAT_LON, line.boatEnd)
        }
    }

    override suspend fun saveWindwardMark(mark: GeoPoint?) {
        dataStore.edit { prefs -> prefs.writePoint(Keys.MARK_LAT, Keys.MARK_LON, mark) }
    }

    override suspend fun saveWind(wind: WindSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.WIND_DIRECTION] = wind.directionDegrees
            prefs[Keys.TACK_ANGLE] = wind.tackAngleDegrees
            prefs[Keys.DOWNWIND_ANGLE] = wind.downwindAngleDegrees
        }
    }

    override suspend fun saveTimer(timer: TimerState) {
        dataStore.edit { prefs ->
            when (timer) {
                TimerState.Idle -> prefs.remove(Keys.TIMER_START_AT)
                is TimerState.Running -> prefs[Keys.TIMER_START_AT] = timer.startAtMillis
            }
        }
    }

    private fun MutablePreferences.writePoint(latKey: Preferences.Key<Double>, lonKey: Preferences.Key<Double>, point: GeoPoint?) {
        if (point == null) {
            remove(latKey)
            remove(lonKey)
        } else {
            this[latKey] = point.latitude
            this[lonKey] = point.longitude
        }
    }

    private fun MutablePreferences.write(settings: AppSettings) {
        val race = settings.race
        when (val approach = race.approachSpeed) {
            is ApproachSpeed.Manual -> {
                this[Keys.APPROACH_MANUAL] = true
                this[Keys.APPROACH_SPEED] = approach.speedMps
            }
            is ApproachSpeed.AverageUpwindVmg -> {
                this[Keys.APPROACH_MANUAL] = false
                this[Keys.APPROACH_SPEED] = approach.fallbackMps
            }
        }
        this[Keys.MIN_SAILING_SPEED] = race.minSailingSpeedMps
        this[Keys.COURSE_MIN_SPEED] = race.courseMinSpeedMps
        this[Keys.FIX_MAX_AGE] = race.fixMaxAgeMillis
        this[Keys.COMPASS_OFFSET] = race.compassOffsetDegrees
        this[Keys.CUES_ENABLED] = race.cuePolicy.enabled
        this[Keys.CUE_TEN_SECOND_WINDOW] = race.cuePolicy.tenSecondWindowSeconds
        this[Keys.CUE_SECOND_WINDOW] = race.cuePolicy.secondWindowSeconds
        this[Keys.UPWIND_MAX_TWA] = race.upwindMaxTwaDegrees
        this[Keys.DOWNWIND_MIN_TWA] = race.downwindMinTwaDegrees
        this[Keys.MAX_SAMPLING_TURN_RATE] = race.maxSamplingTurnRateDegreesPerSecond
        this[Keys.HISTORY_CAPACITY] = race.windHistoryCapacity
        // 0 is "let the racing area choose": DataStore has no null, and no square is ever 0 m.
        this[Keys.CELL_SIZE] = race.course.grid.cellSizeMeters ?: AUTOMATIC_CELL_SIZE
        this[Keys.KEEP_SCREEN_ON] = settings.keepScreenOn
        this[Keys.VIBRATE] = settings.vibrate
        this[Keys.LOG_SESSIONS] = settings.logSessions
        this[Keys.SIM_ENABLED] = settings.simulation.enabled
        this[Keys.SIM_AUTOPLAY] = settings.simulation.autoPlayActions
        this[Keys.SIM_SPEED] = settings.simulation.speedFactor
        this[Keys.SIM_SCENARIO] = settings.simulation.scenarioId
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = RaceSettings()
        val defaultCues = CuePolicy()
        val manual = this[Keys.APPROACH_MANUAL] ?: false
        val approachSpeed = this[Keys.APPROACH_SPEED]?.takeIf { it > 0.0 } ?: ApproachSpeed.AverageUpwindVmg.DEFAULT_FALLBACK_MPS
        val race = RaceSettings(
            approachSpeed = if (manual) ApproachSpeed.Manual(approachSpeed) else ApproachSpeed.AverageUpwindVmg(approachSpeed),
            minSailingSpeedMps = this[Keys.MIN_SAILING_SPEED] ?: defaults.minSailingSpeedMps,
            courseMinSpeedMps = this[Keys.COURSE_MIN_SPEED] ?: defaults.courseMinSpeedMps,
            fixMaxAgeMillis = this[Keys.FIX_MAX_AGE] ?: defaults.fixMaxAgeMillis,
            compassOffsetDegrees = this[Keys.COMPASS_OFFSET] ?: defaults.compassOffsetDegrees,
            cuePolicy = CuePolicy(
                enabled = this[Keys.CUES_ENABLED] ?: defaultCues.enabled,
                tenSecondWindowSeconds = this[Keys.CUE_TEN_SECOND_WINDOW] ?: defaultCues.tenSecondWindowSeconds,
                secondWindowSeconds = this[Keys.CUE_SECOND_WINDOW] ?: defaultCues.secondWindowSeconds,
            ),
            upwindMaxTwaDegrees = this[Keys.UPWIND_MAX_TWA] ?: defaults.upwindMaxTwaDegrees,
            downwindMinTwaDegrees = this[Keys.DOWNWIND_MIN_TWA] ?: defaults.downwindMinTwaDegrees,
            maxSamplingTurnRateDegreesPerSecond = this[Keys.MAX_SAMPLING_TURN_RATE] ?: defaults.maxSamplingTurnRateDegreesPerSecond,
            windHistoryCapacity = this[Keys.HISTORY_CAPACITY] ?: defaults.windHistoryCapacity,
            course = defaults.course.copy(
                grid = GridSettings(
                    cellSizeMeters = this[Keys.CELL_SIZE]?.takeIf { it >= GridSpec.BASE_CELL_METERS },
                ),
            ),
        )
        return AppSettings(
            race = race,
            keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: true,
            vibrate = this[Keys.VIBRATE] ?: true,
            logSessions = this[Keys.LOG_SESSIONS] ?: true,
            simulation = SimulationSettings(
                enabled = this[Keys.SIM_ENABLED] ?: false,
                autoPlayActions = this[Keys.SIM_AUTOPLAY] ?: true,
                speedFactor = this[Keys.SIM_SPEED]?.takeIf { it > 0.0 } ?: 1.0,
                // A simulation dropped from the catalog falls back to the default rather than to nothing.
                scenarioId = SimulationCatalog.byId(this[Keys.SIM_SCENARIO]).id,
            ),
        )
    }

    private fun Preferences.toPersistedRace(): PersistedRace {
        val defaults = WindSettings()
        val wind = runCatching {
            WindSettings(
                directionDegrees = this[Keys.WIND_DIRECTION] ?: defaults.directionDegrees,
                tackAngleDegrees = this[Keys.TACK_ANGLE] ?: defaults.tackAngleDegrees,
                downwindAngleDegrees = this[Keys.DOWNWIND_ANGLE] ?: defaults.downwindAngleDegrees,
            )
        }.getOrDefault(defaults)
        return PersistedRace(
            startLine = StartLine(readPoint(Keys.PIN_LAT, Keys.PIN_LON), readPoint(Keys.BOAT_LAT, Keys.BOAT_LON)),
            windwardMark = readPoint(Keys.MARK_LAT, Keys.MARK_LON),
            wind = wind,
            timer = this[Keys.TIMER_START_AT]?.let { TimerState.Running(it) } ?: TimerState.Idle,
        )
    }

    private fun Preferences.readPoint(latKey: Preferences.Key<Double>, lonKey: Preferences.Key<Double>): GeoPoint? {
        val lat = this[latKey] ?: return null
        val lon = this[lonKey] ?: return null
        return runCatching { GeoPoint(lat, lon) }.getOrNull()
    }

    private object Keys {
        val PIN_LAT = doublePreferencesKey("line.pin.lat")
        val PIN_LON = doublePreferencesKey("line.pin.lon")
        val BOAT_LAT = doublePreferencesKey("line.boat.lat")
        val BOAT_LON = doublePreferencesKey("line.boat.lon")
        val MARK_LAT = doublePreferencesKey("mark.windward.lat")
        val MARK_LON = doublePreferencesKey("mark.windward.lon")
        val WIND_DIRECTION = intPreferencesKey("wind.direction")
        val TACK_ANGLE = intPreferencesKey("wind.tackAngle")
        val DOWNWIND_ANGLE = intPreferencesKey("wind.downwindAngle")
        val TIMER_START_AT = longPreferencesKey("timer.startAt")

        val APPROACH_MANUAL = booleanPreferencesKey("settings.approach.manual")
        val APPROACH_SPEED = doublePreferencesKey("settings.approach.speed")
        val MIN_SAILING_SPEED = doublePreferencesKey("settings.minSailingSpeed")
        val COURSE_MIN_SPEED = doublePreferencesKey("settings.courseMinSpeed")
        val FIX_MAX_AGE = longPreferencesKey("settings.fixMaxAge")
        val COMPASS_OFFSET = intPreferencesKey("settings.compassOffset")
        val CUES_ENABLED = booleanPreferencesKey("settings.cues.enabled")
        val CUE_TEN_SECOND_WINDOW = intPreferencesKey("settings.cues.tenSecondWindow")
        val CUE_SECOND_WINDOW = intPreferencesKey("settings.cues.secondWindow")
        val UPWIND_MAX_TWA = intPreferencesKey("settings.upwindMaxTwa")
        val DOWNWIND_MIN_TWA = intPreferencesKey("settings.downwindMinTwa")
        val MAX_SAMPLING_TURN_RATE = doublePreferencesKey("settings.maxSamplingTurnRate")
        val HISTORY_CAPACITY = intPreferencesKey("settings.historyCapacity")
        val CELL_SIZE = doublePreferencesKey("settings.course.cellSize")
        val KEEP_SCREEN_ON = booleanPreferencesKey("settings.keepScreenOn")
        val VIBRATE = booleanPreferencesKey("settings.vibrate")
        val LOG_SESSIONS = booleanPreferencesKey("settings.log.sessions")
        val SIM_ENABLED = booleanPreferencesKey("settings.simulation.enabled")
        val SIM_AUTOPLAY = booleanPreferencesKey("settings.simulation.autoPlay")
        val SIM_SPEED = doublePreferencesKey("settings.simulation.speed")
        val SIM_SCENARIO = stringPreferencesKey("settings.simulation.scenario")
    }
}
