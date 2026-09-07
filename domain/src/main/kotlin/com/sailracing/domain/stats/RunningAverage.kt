package com.sailracing.domain.stats

import kotlin.math.max

/** Count, sum and maximum of a stream of values; immutable. */
public data class RunningAverage(val count: Int = 0, val sum: Double = 0.0, val max: Double = 0.0) {

    public val mean: Double? get() = if (count == 0) null else sum / count

    public operator fun plus(value: Double): RunningAverage =
        RunningAverage(count + 1, sum + value, if (count == 0) value else max(max, value))
}

/** Boat-speed statistics split by point of sail. Speeds in metres per second. */
public data class SpeedStats(
    val upwind: RunningAverage = RunningAverage(),
    val upwindVmg: RunningAverage = RunningAverage(),
    val downwind: RunningAverage = RunningAverage(),
    val downwindVmg: RunningAverage = RunningAverage(),
)
