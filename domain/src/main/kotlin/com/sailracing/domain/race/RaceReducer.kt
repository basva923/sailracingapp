package com.sailracing.domain.race

import com.sailracing.domain.course.Track
import com.sailracing.domain.course.TrackPoint
import com.sailracing.domain.geo.Angles
import com.sailracing.domain.geo.Geo
import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.stats.SpeedStats
import com.sailracing.domain.timer.CountdownTimer
import com.sailracing.domain.timer.CueSchedule
import com.sailracing.domain.timer.TimerState
import com.sailracing.domain.wind.WindHistory
import com.sailracing.domain.wind.WindMath
import com.sailracing.domain.wind.WindReference
import com.sailracing.domain.wind.WindSample
import com.sailracing.domain.wind.WindSettings
import kotlin.math.abs

/** Pure state machine of the race. `reduce(state, event)` never touches the outside world. */
public object RaceReducer {

    public fun reduce(state: RaceState, event: RaceEvent): Transition = when (event) {
        is RaceEvent.FixReceived -> Transition(onFix(state, event.fix))
        is RaceEvent.CompassUpdated -> Transition(onCompass(state, event.headingDegrees))
        is RaceEvent.Tick -> onTick(state, event.nowMillis)

        RaceEvent.MarkPinEnd -> Transition(state.navigation.lastFix?.let { fix ->
            state.copy(startLine = state.startLine.copy(pinEnd = fix.point))
        } ?: state)
        RaceEvent.MarkBoatEnd -> Transition(state.navigation.lastFix?.let { fix ->
            state.copy(startLine = state.startLine.copy(boatEnd = fix.point))
        } ?: state)
        RaceEvent.ClearPinEnd -> Transition(state.copy(startLine = state.startLine.copy(pinEnd = null)))
        RaceEvent.ClearBoatEnd -> Transition(state.copy(startLine = state.startLine.copy(boatEnd = null)))
        is RaceEvent.SetStartLine -> Transition(state.copy(startLine = event.line))

        RaceEvent.MarkWindwardMark -> Transition(state.navigation.lastFix?.let { fix -> state.copy(windwardMark = fix.point) } ?: state)
        is RaceEvent.SetWindwardMarkFromLine -> Transition(
            (state.startLine.middle() ?: state.navigation.lastFix?.point)?.let { from ->
                state.copy(windwardMark = Geo.destination(from, event.bearingDegrees.toDouble(), event.distanceMeters))
            } ?: state,
        )
        is RaceEvent.SetWindwardMark -> Transition(state.copy(windwardMark = event.point))

        is RaceEvent.StartCountdown -> Transition(
            state.copy(timer = CountdownTimer.start(event.nowMillis, event.minutes), lastCuedSecond = null),
        )
        is RaceEvent.SyncCountdown -> Transition(
            state.copy(timer = CountdownTimer.syncToNearestMinute(state.timer, event.nowMillis), lastCuedSecond = null),
        )
        RaceEvent.StopTimer -> Transition(state.copy(timer = TimerState.Idle, lastCuedSecond = null))
        is RaceEvent.SetTimer -> Transition(state.copy(timer = event.timer, lastCuedSecond = null))

        is RaceEvent.SetWindDirection -> Transition(withWindSettings(state, state.wind.settings.withDirection(event.degrees)))
        is RaceEvent.SetTackAngle -> Transition(withWindSettings(state, state.wind.settings.withTackAngle(event.degrees)))
        is RaceEvent.SetDownwindAngle ->
            Transition(withWindSettings(state, state.wind.settings.withDownwindAngle(event.degrees)))
        RaceEvent.SetWindFromPortTack -> Transition(state.navigation.headingDegrees?.let { heading ->
            val direction = WindMath.windFromPortTack(steadyHeading(state, heading), state.wind.settings.tackAngleDegrees)
            withWindSettings(state, state.wind.settings.withDirection(direction))
        } ?: state)
        RaceEvent.SetWindFromStarboardTack -> Transition(state.navigation.headingDegrees?.let { heading ->
            val direction = WindMath.windFromStarboardTack(steadyHeading(state, heading), state.wind.settings.tackAngleDegrees)
            withWindSettings(state, state.wind.settings.withDirection(direction))
        } ?: state)
        is RaceEvent.SetWindSettings -> Transition(withWindSettings(state, event.settings))
        // The statistics go; what the boat is - the tack angle read off its own tacks - stays.
        RaceEvent.ResetWindStatistics -> Transition(
            state.copy(
                wind = WindState(
                    settings = state.wind.settings,
                    history = state.wind.history.copy(samples = emptyList()),
                    measuredTackAngleDegrees = state.wind.measuredTackAngleDegrees,
                ),
            ),
        )
        RaceEvent.ResetSpeedStatistics -> Transition(state.copy(speedStats = SpeedStats()))
        RaceEvent.ClearTrack -> Transition(state.copy(track = Track(capacity = state.track.capacity)))
        RaceEvent.ClearSession -> Transition(
            RaceState(
                wind = WindState(
                    settings = state.wind.settings,
                    history = WindHistory(capacity = state.settings.windHistoryCapacity),
                    measuredTackAngleDegrees = state.wind.measuredTackAngleDegrees,
                ),
                navigation = state.navigation,
                settings = state.settings,
                track = Track(capacity = state.track.capacity),
            ),
        )

        is RaceEvent.UpdateSettings -> Transition(onSettings(state, event.settings))
    }

    private fun withWindSettings(state: RaceState, settings: WindSettings): RaceState =
        state.copy(wind = state.wind.copy(settings = settings))

    /**
     * The heading the wind is set from when the sailor presses a tack button: the boat's heading over the
     * last twenty seconds on this tack, not the wave it is on right now. The heading of the moment when
     * there is nothing steadier - the boat has only just settled, or has not been sampled at all.
     */
    private fun steadyHeading(state: RaceState, headingDegrees: Double): Double {
        val latest = state.wind.history.latest ?: return headingDegrees
        return state.wind.history.steadyHeadingDegrees(
            nearDegrees = headingDegrees,
            count = STEADY_HEADING_SAMPLES,
            sinceMillis = latest.timestampMillis - STEADY_HEADING_WINDOW_MILLIS,
            maxSpreadDegrees = STEADY_HEADING_SPREAD_DEGREES,
        ) ?: headingDegrees
    }

    private fun onCompass(state: RaceState, rawHeadingDegrees: Double): RaceState {
        val corrected = Angles.normalize(rawHeadingDegrees + state.settings.compassOffsetDegrees)
        return state.copy(navigation = HeadingSelector.select(state.navigation.lastFix, corrected, state.settings))
    }

    private fun onSettings(state: RaceState, settings: RaceSettings): RaceState {
        // The compass offset may have changed: undo the old offset and re-apply the new one.
        val rawCompass = state.navigation.compassHeadingDegrees?.let { Angles.normalize(it - state.settings.compassOffsetDegrees) }
        val corrected = rawCompass?.let { Angles.normalize(it + settings.compassOffsetDegrees) }
        return state.copy(
            settings = settings,
            navigation = HeadingSelector.select(state.navigation.lastFix, corrected, settings),
            wind = state.wind.copy(history = state.wind.history.withCapacity(settings.windHistoryCapacity)),
        )
    }

    private fun onFix(state: RaceState, fix: PositionFix): RaceState {
        val navigation = HeadingSelector.select(fix, state.navigation.compassHeadingDegrees, state.settings)
        val turning = isTurningFast(state, navigation, fix)
        return sample(state.copy(navigation = navigation), fix, turning)
    }

    /** True while the heading changes faster than the sampling limit, i.e. mid-tack or mid-gybe. */
    private fun isTurningFast(state: RaceState, navigation: NavigationState, fix: PositionFix): Boolean {
        val previousHeading = state.navigation.headingDegrees ?: return false
        val previousTime = state.navigation.lastFix?.timestampMillis ?: return false
        val heading = navigation.headingDegrees ?: return false
        val dtSeconds = (fix.timestampMillis - previousTime) / 1000.0
        if (dtSeconds <= 0.0) return false
        val rate = abs(Angles.signedDifference(previousHeading, heading)) / dtSeconds
        return rate > state.settings.maxSamplingTurnRateDegreesPerSecond
    }

    /**
     * Records at most one track point per second of fix time, with a wind and speed sample when the boat is
     * actually sailing steadily (moving, and not in the middle of a tack or gybe). Which tack and point of
     * sail the boat is on is judged against the reference wind (read off the boat's own tacks once there
     * is one), so a roughly set wind does not keep one tack out of the statistics; a sample only counts
     * as close-hauled within the close-hauled band of the tack angle, so a reach does not get in either.
     */
    private fun sample(state: RaceState, fix: PositionFix, turning: Boolean): RaceState {
        val second = fix.timestampMillis / 1000
        if (second == state.lastSampleSecond) return state
        val heading = state.navigation.headingDegrees
        val speed = fix.speedMps
        val point = TrackPoint(fix.timestampMillis, fix.point, speed, heading)
        val recorded = state.copy(track = state.track + point, lastSampleSecond = second)
        if (heading == null || speed == null) return recorded
        if (speed < state.settings.minSailingSpeedMps || turning) return recorded

        val windSettings = state.wind.settings
        val reference = state.wind.reference(fix.timestampMillis)
        val sailing = WindMath.sailingState(heading, reference.directionDegrees)
        val estimate = WindMath.estimatedWindDirection(heading, windSettings, reference.directionDegrees, reference.tackAngleDegrees)
        val sailingUpwind = WindMath.isCloseHauled(sailing, reference.tackAngleDegrees, state.settings.closeHauledBandDegrees)
        val sailingDownwind = WindMath.isOnDownwindAngle(sailing, state.settings.downwindMinTwaDegrees)
        val vmg = WindMath.velocityMadeGood(speed, sailing)

        val histogram = if (sailingUpwind) state.wind.histogram + estimate else state.wind.histogram
        val capacity = state.settings.windHistoryCapacity
        val sized = if (state.wind.history.capacity == capacity) state.wind.history else state.wind.history.withCapacity(capacity)
        val history = sized + WindSample(fix.timestampMillis, estimate, sailingUpwind, heading, sailing.tack, sailingDownwind)
        val stats = when {
            sailingUpwind -> state.speedStats.copy(
                upwind = state.speedStats.upwind + speed,
                upwindVmg = state.speedStats.upwindVmg + vmg,
            )
            sailingDownwind -> state.speedStats.copy(
                downwind = state.speedStats.downwind + speed,
                downwindVmg = state.speedStats.downwindVmg + vmg,
            )
            else -> state.speedStats
        }
        val tracked = if (sailingUpwind) state.track + point.copy(upwindWindDegrees = estimate) else recorded.track
        // The tack angle read off the boat's two tacks, this sample included, is kept for the board on
        // which only one of them is sailed.
        val measuredTackAngle = WindReference.measuredTackAngleDegrees(windSettings, history, fix.timestampMillis) ?: state.wind.measuredTackAngleDegrees
        return state.copy(
            wind = state.wind.copy(histogram = histogram, history = history, measuredTackAngleDegrees = measuredTackAngle),
            speedStats = stats,
            track = tracked,
            lastSampleSecond = second,
        )
    }

    /** The wind set from a tack is read off this many samples, at most this old, and none from the other tack. */
    public const val STEADY_HEADING_SAMPLES: Int = 10
    public const val STEADY_HEADING_WINDOW_MILLIS: Long = 20_000L
    public const val STEADY_HEADING_SPREAD_DEGREES: Double = 45.0

    private fun onTick(state: RaceState, nowMillis: Long): Transition {
        val remaining = CountdownTimer.remainingMillis(state.timer, nowMillis) ?: return Transition(state)
        val second = CountdownTimer.remainingWholeSeconds(remaining)
        if (second == state.lastCuedSecond) return Transition(state)
        val cue = CueSchedule.cueAt(second, state.settings.cuePolicy)
        val effects = if (cue != null) listOf(RaceEffect.PlayCue(cue)) else emptyList()
        return Transition(state.copy(lastCuedSecond = second), effects)
    }
}
