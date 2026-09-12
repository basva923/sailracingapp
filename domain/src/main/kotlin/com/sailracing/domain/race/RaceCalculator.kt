package com.sailracing.domain.race

import com.sailracing.domain.course.CourseFrame
import com.sailracing.domain.course.CourseInputs
import com.sailracing.domain.course.CourseModel
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.startline.StartLineCalculator
import com.sailracing.domain.strategy.UpwindStrategy
import com.sailracing.domain.timer.CountdownTimer
import com.sailracing.domain.wind.WindHistory
import com.sailracing.domain.wind.WindMath
import kotlin.math.roundToInt

/**
 * Derives the displayable [RaceSnapshot] from a [RaceState] at a given moment. Pure: the same state and
 * time always give the same snapshot; [previous] only lets the course model be reused when nothing it
 * depends on has changed (the clock ticks several times a second, the track grows once a second).
 */
public object RaceCalculator {

    public fun snapshot(state: RaceState, nowMillis: Long, previous: RaceSnapshot? = null): RaceSnapshot {
        val fix = state.navigation.lastFix
        val fixAge = fix?.let { nowMillis - it.timestampMillis }
        val fixIsFresh = fixAge != null && fixAge <= state.settings.fixMaxAgeMillis
        val remaining = CountdownTimer.remainingMillis(state.timer, nowMillis)
        val windSettings = state.wind.settings
        val reference = state.wind.reference(nowMillis)
        val referenceDegrees = reference.directionDegrees
        val tackAngle = reference.tackAngleDegrees
        val heading = state.navigation.headingDegrees

        val line = fix?.let { StartLineCalculator.solve(state.startLine, it.point, referenceDegrees) }
        val (approachSpeed, measured) = approachSpeed(state)
        val timeToLine = line?.let { StartLineCalculator.timeToLineSeconds(it.distanceMeters, approachSpeed) }
        val timeToKill = if (remaining != null && timeToLine != null) {
            StartLineCalculator.timeToKillSeconds(remaining / 1000.0, timeToLine)
        } else {
            null
        }

        val sailing = heading?.let { WindMath.sailingState(it, referenceDegrees) }
        val estimatedWind = heading?.let { WindMath.estimatedWindDirection(it, windSettings, referenceDegrees, tackAngle) }
        val target = sailing?.let { WindMath.targetHeading(windSettings, it, referenceDegrees, tackAngle) }
        val speed = fix?.speedMps

        // A heading only says something about the wind while the boat holds an angle to it: close-hauled,
        // or on its downwind angle. Reaching, it says nothing.
        val beating = sailing != null && WindMath.isCloseHauled(sailing, tackAngle, state.settings.closeHauledBandDegrees)
        val running = sailing != null && WindMath.isOnDownwindAngle(sailing, state.settings.downwindMinTwaDegrees)
        val onAngle = beating || running
        // The wind of the moment is the last few samples on this tack, not the last fix alone - one fix is
        // a wave and a wobble of the helm. The boat that has just settled on a tack has one sample, and
        // until it has that one its heading now stands in.
        val steadyWind = if (onAngle) {
            state.wind.history.steadyDirectionDegrees(sailing!!.tack, running, STEADY_SAMPLES, nowMillis - STEADY_WINDOW_MILLIS) ?: estimatedWind
        } else {
            null
        }

        // The race line is planned from the tack the boat is on and the wind it is measuring right now, so
        // both only count while it is actually beating: a reaching boat's heading says nothing about the wind.
        val course = courseOrigin(state.startLine, state.track.points.firstOrNull()?.point ?: fix?.point)?.let { origin ->
            val inputs = CourseInputs(
                frame = CourseFrame(origin, referenceDegrees),
                line = state.startLine,
                windwardMark = state.windwardMark,
                boat = fix?.point,
                tackAngleDegrees = tackAngle.roundToInt(),
                currentTack = if (beating) sailing!!.tack else null,
                currentWindDegrees = if (beating) currentWindDegrees(state.wind.history) else null,
                settings = state.settings.course,
            )
            previous?.course?.takeIf { it.isFor(state.track, inputs) } ?: CourseModel.build(state.track, inputs, previous?.course)
        }
        val plan = UpwindStrategy.plan(
            windReference = reference,
            histogram = state.wind.histogram,
            history = state.wind.history,
            sailing = sailing,
            estimatedWindDegrees = steadyWind,
            grid = course?.grid,
            onAngle = onAngle,
        )

        return RaceSnapshot(
            nowMillis = nowMillis,
            phase = CountdownTimer.phase(state.timer, nowMillis),
            remainingMillis = remaining,
            remainingWholeSeconds = remaining?.let(CountdownTimer::remainingWholeSeconds),
            startLine = state.startLine,
            startLineLengthMeters = state.startLine.lengthMeters(),
            line = line,
            approachSpeedMps = approachSpeed,
            approachSpeedIsMeasured = measured,
            timeToLineSeconds = timeToLine,
            timeToKillSeconds = timeToKill,
            position = fix?.point,
            fixAgeMillis = fixAge,
            fixIsFresh = fixIsFresh,
            accuracyMeters = fix?.accuracyMeters,
            speedMps = speed,
            headingDegrees = heading,
            headingSource = state.navigation.headingSource,
            windSettings = windSettings,
            windReference = reference,
            sailing = sailing,
            estimatedWindDegrees = estimatedWind,
            shiftDegrees = estimatedWind?.let { WindMath.shiftDegrees(referenceDegrees, it) },
            steadyWindDegrees = steadyWind,
            steadyShiftDegrees = steadyWind?.let { WindMath.shiftDegrees(referenceDegrees, it) },
            targetHeadings = WindMath.targetHeadings(windSettings, referenceDegrees, tackAngle),
            targetHeadingDegrees = target,
            headingErrorDegrees = if (heading != null && target != null) WindMath.headingErrorDegrees(heading, target) else null,
            vmgMps = if (speed != null && sailing != null) WindMath.velocityMadeGood(speed, sailing) else null,
            speedStats = state.speedStats,
            histogram = state.wind.histogram,
            history = state.wind.history,
            course = course,
            trackPointCount = state.track.points.size,
            plan = plan,
        )
    }

    /**
     * The origin of the map's frame: the middle of the start line, one marked end while the other is
     * missing, and otherwise where the track started (or the boat is), so the map is useful before the line is set.
     */
    public fun courseOrigin(line: StartLine, fallback: GeoPoint?): GeoPoint? = line.middle() ?: fallback

    /** The speed used for time-to-line, and whether it is a measured value. */
    public fun approachSpeed(state: RaceState): Pair<Double, Boolean> = when (val setting = state.settings.approachSpeed) {
        is ApproachSpeed.Manual -> setting.speedMps to false
        is ApproachSpeed.AverageUpwindVmg -> {
            val measured = state.speedStats.upwindVmg
            val mean = measured.mean
            if (mean != null && mean > 0.0 && measured.count >= MIN_VMG_SAMPLES) mean to true else setting.fallbackMps to false
        }
    }

    /** Fewer samples than this and the measured VMG is too noisy to trust for the start. */
    public const val MIN_VMG_SAMPLES: Int = 30

    /**
     * The wind the race line starts from: the mean of the last [CURRENT_WIND_SAMPLES] close-hauled
     * estimates, or null when the boat has not been beating. One sample is a wave and a wobble of the
     * helm; half a minute of them is the shift the boat is actually in.
     */
    public fun currentWindDegrees(history: WindHistory, samples: Int = CURRENT_WIND_SAMPLES): Double? =
        history.recentUpwindMeanDegrees(samples)

    /** How many close-hauled samples the wind of the moment is averaged over: about half a minute of them. */
    public const val CURRENT_WIND_SAMPLES: Int = 30

    /** The shift shown and the advice given are read off this many samples on the current tack, at most this old. */
    public const val STEADY_SAMPLES: Int = 10
    public const val STEADY_WINDOW_MILLIS: Long = 30_000L
}
