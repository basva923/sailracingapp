package com.sailracing.domain.race

import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.timer.Cue
import com.sailracing.domain.timer.TimerState
import com.sailracing.domain.wind.WindSettings

/** Everything that can happen to a race session: sensor input, the clock, and the sailor's actions. */
public sealed interface RaceEvent {

    // Sensors and clock
    public data class FixReceived(val fix: PositionFix) : RaceEvent
    public data class CompassUpdated(val headingDegrees: Double) : RaceEvent
    public data class Tick(val nowMillis: Long) : RaceEvent

    // Start line
    public data object MarkPinEnd : RaceEvent
    public data object MarkBoatEnd : RaceEvent
    public data object ClearPinEnd : RaceEvent
    public data object ClearBoatEnd : RaceEvent
    public data class SetStartLine(val line: StartLine) : RaceEvent

    // Countdown
    public data class StartCountdown(val minutes: Int, val nowMillis: Long) : RaceEvent
    public data class SyncCountdown(val nowMillis: Long) : RaceEvent
    public data object StopTimer : RaceEvent
    public data class SetTimer(val timer: TimerState) : RaceEvent

    // Wind
    public data class SetWindDirection(val degrees: Int) : RaceEvent
    public data class SetTackAngle(val degrees: Int) : RaceEvent
    public data class SetDownwindAngle(val degrees: Int) : RaceEvent
    public data object SetWindFromPortTack : RaceEvent
    public data object SetWindFromStarboardTack : RaceEvent
    public data class SetWindSettings(val settings: WindSettings) : RaceEvent
    public data object ResetWindStatistics : RaceEvent
    public data object ResetSpeedStatistics : RaceEvent

    // Configuration
    public data class UpdateSettings(val settings: RaceSettings) : RaceEvent
}

/** Side effects the reducer asks the outside world to perform. */
public sealed interface RaceEffect {
    public data class PlayCue(val cue: Cue) : RaceEffect
}

/** The result of reducing one event. */
public data class Transition(val state: RaceState, val effects: List<RaceEffect> = emptyList())
