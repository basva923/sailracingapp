package com.sailracing.app.data

import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.race.RaceSettings
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.timer.TimerState
import com.sailracing.domain.wind.WindSettings

/** Settings for the built-in race simulation. */
data class SimulationSettings(
    val enabled: Boolean = false,
    /** Replay the scripted sailor actions as well as the GPS track, for a hands-free demo. */
    val autoPlayActions: Boolean = true,
    /** Run the simulated clock this many times faster than real time. */
    val speedFactor: Double = 1.0,
)

/** Everything the sailor can configure, persisted across launches. */
data class AppSettings(
    val race: RaceSettings = RaceSettings(),
    val keepScreenOn: Boolean = true,
    val vibrate: Boolean = true,
    val simulation: SimulationSettings = SimulationSettings(),
)

/** Race data that survives an app restart: the line, the windward mark, the wind and a running countdown. */
data class PersistedRace(
    val startLine: StartLine = StartLine(),
    val windwardMark: GeoPoint? = null,
    val wind: WindSettings = WindSettings(),
    val timer: TimerState = TimerState.Idle,
)
