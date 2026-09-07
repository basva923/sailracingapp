package com.sailracing.domain.race

import com.sailracing.domain.startline.StartLineCalculator
import com.sailracing.domain.timer.CountdownTimer
import com.sailracing.domain.wind.WindMath

/** Derives the displayable [RaceSnapshot] from a [RaceState] at a given moment. Pure and allocation-light. */
public object RaceCalculator {

    public fun snapshot(state: RaceState, nowMillis: Long): RaceSnapshot {
        val fix = state.navigation.lastFix
        val fixAge = fix?.let { nowMillis - it.timestampMillis }
        val fixIsFresh = fixAge != null && fixAge <= state.settings.fixMaxAgeMillis
        val remaining = CountdownTimer.remainingMillis(state.timer, nowMillis)
        val windSettings = state.wind.settings
        val heading = state.navigation.headingDegrees

        val line = fix?.let { StartLineCalculator.solve(state.startLine, it.point, windSettings.directionDegrees.toDouble()) }
        val (approachSpeed, measured) = approachSpeed(state)
        val timeToLine = line?.let { StartLineCalculator.timeToLineSeconds(it.distanceMeters, approachSpeed) }
        val timeToKill = if (remaining != null && timeToLine != null) {
            StartLineCalculator.timeToKillSeconds(remaining / 1000.0, timeToLine)
        } else {
            null
        }

        val sailing = heading?.let { WindMath.sailingState(it, windSettings.directionDegrees.toDouble()) }
        val estimatedWind = heading?.let { WindMath.estimatedWindDirection(it, windSettings) }
        val target = sailing?.let { WindMath.targetHeading(windSettings, it) }
        val speed = fix?.speedMps

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
            sailing = sailing,
            estimatedWindDegrees = estimatedWind,
            shiftDegrees = estimatedWind?.let { WindMath.shiftDegrees(windSettings.directionDegrees.toDouble(), it) },
            targetHeadings = WindMath.targetHeadings(windSettings),
            targetHeadingDegrees = target,
            headingErrorDegrees = if (heading != null && target != null) WindMath.headingErrorDegrees(heading, target) else null,
            vmgMps = if (speed != null && sailing != null) WindMath.velocityMadeGood(speed, sailing) else null,
            speedStats = state.speedStats,
            histogram = state.wind.histogram,
            history = state.wind.history,
        )
    }

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
