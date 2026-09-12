package com.sailracing.app.data

import com.sailracing.domain.course.GridSettings
import com.sailracing.domain.race.RaceSettings
import com.sailracing.domain.timer.TimerState
import com.sailracing.domain.wind.WindSettings
import com.sailracing.simulation.SimulationCatalog
import com.sailracing.simulation.SimulationScenario

/** Settings for the built-in race simulation. */
data class SimulationSettings(
    val enabled: Boolean = false,
    /** Replay the scripted sailor actions as well as the GPS track, for a hands-free demo. */
    val autoPlayActions: Boolean = true,
    /** Run the simulated clock this many times faster than real time. */
    val speedFactor: Double = 1.0,
    /** Which simulation of [SimulationCatalog] is replayed. */
    val scenarioId: String = SimulationCatalog.default.id,
) {
    /** The simulation itself, falling back to the default when the stored one is not on offer any more. */
    val scenario: SimulationScenario get() = SimulationCatalog.byId(scenarioId)
}

/** Everything the sailor can configure, persisted across launches. */
data class AppSettings(
    val race: RaceSettings = RaceSettings(),
    val keepScreenOn: Boolean = true,
    val vibrate: Boolean = true,
    /** Write everything of the session to a file, to go through afterwards. */
    val logSessions: Boolean = true,
    val simulation: SimulationSettings = SimulationSettings(),
)

/** The same settings with the size of the racing area's squares changed. */
fun AppSettings.withGrid(transform: (GridSettings) -> GridSettings): AppSettings =
    copy(race = race.copy(course = race.course.copy(grid = transform(race.course.grid))))

/** The square size a sailor who switches off "automatic" starts from. */
const val DEFAULT_CELL_SIZE_METERS: Double = 50.0

/**
 * Race data that survives an app restart: the wind and a running countdown.
 *
 * The start line and the windward mark are deliberately not among them. They are laid afresh for every
 * race, so a line kept from the last outing would have the app timing the start to a line miles away.
 * Every session therefore begins with an empty course, see [com.sailracing.app.race.RaceSession.start].
 */
data class PersistedRace(
    val wind: WindSettings = WindSettings(),
    val timer: TimerState = TimerState.Idle,
)
