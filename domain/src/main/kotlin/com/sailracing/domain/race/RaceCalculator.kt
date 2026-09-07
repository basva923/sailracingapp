package com.sailracing.domain.race

import com.sailracing.domain.course.CourseFrame
import com.sailracing.domain.course.CourseInputs
import com.sailracing.domain.course.CourseModel
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.startline.StartLineCalculator
import com.sailracing.domain.strategy.UpwindStrategy
import com.sailracing.domain.timer.CountdownTimer
import com.sailracing.domain.wind.WindMath

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
        val reference = state.wind.reference
        val referenceDegrees = reference.directionDegrees
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
        val estimatedWind = heading?.let { WindMath.estimatedWindDirection(it, windSettings, referenceDegrees) }
        val target = sailing?.let { WindMath.targetHeading(windSettings, it, referenceDegrees) }
        val speed = fix?.speedMps

        val course = courseOrigin(state.startLine, state.track.points.firstOrNull()?.point ?: fix?.point)?.let { origin ->
            val inputs = CourseInputs(
                frame = CourseFrame(origin, referenceDegrees),
                line = state.startLine,
                windwardMark = state.windwardMark,
                boat = fix?.point,
                tackAngleDegrees = windSettings.tackAngleDegrees,
                downwindAngleDegrees = windSettings.downwindAngleDegrees,
            )
            previous?.course?.takeIf { it.isFor(state.track, inputs) } ?: CourseModel.build(state.track, inputs)
        }
        val plan = UpwindStrategy.plan(
            windReference = reference,
            histogram = state.wind.histogram,
            history = state.wind.history,
            sailing = sailing,
            estimatedWindDegrees = estimatedWind,
            grid = course?.grid,
            upwindMaxTwaDegrees = state.settings.upwindMaxTwaDegrees,
            downwindMinTwaDegrees = state.settings.downwindMinTwaDegrees,
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
            targetHeadings = WindMath.targetHeadings(windSettings, referenceDegrees),
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
}
