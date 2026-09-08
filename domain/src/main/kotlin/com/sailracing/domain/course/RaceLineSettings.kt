package com.sailracing.domain.course

/**
 * The dials of the race line's Monte Carlo: how far a wind measured at the boat is believed to reach, how
 * much of the drawn spread a whole board feels, and how many winds are tried. What the wind over the
 * course is believed to be like in the first place belongs to [WindFieldSettings]: this is only what the
 * search makes of it.
 *
 * The defaults are for a dinghy course of a few hundred metres in a shifty coastal or inland breeze.
 * Every one of them is a belief about the water, not a fact, which is why they are all in one place.
 *
 * @property runs how many winds the Monte Carlo tries. Every candidate line is timed through all of them,
 *   so the lines are compared over the same winds and a difference between two lines is a real difference
 *   and not the noise of two separate draws.
 * @property currentWindWeight how much the wind measured right now, from the boat's own tack angle,
 *   overrules the mean measured in the square the boat is in: 0 ignores it, 1 believes only it. At 0.8 the
 *   wind of the moment carries the square the boat is in and the search leaves on the shift it is sailing.
 * @property currentWindRangeMeters how far that belief reaches: the weight falls off as
 *   `exp(-distance / range)`, so it is a third of [currentWindWeight] one range away and almost nothing
 *   three ranges away. A header you are sailing in now says a lot about the water fifty metres ahead and
 *   little about the far corner of the course.
 * @property speedSpreadFactor how much of the speed histogram's spread a whole board across a square
 *   feels. The histogram is of speeds a second apart; a board averages a handful of them, so the wave that
 *   stopped the boat for one sample is not the wave it crosses the square on.
 * @property minBoatSpeedMps the slowest a drawn speed may be, so that a line never costs eternity.
 * @property riskFraction which end of the spread counts as luck. At 0.2 a line's good day is the time it
 *   beats one run in five and its bad day the time it is beaten by one run in five: the fast line is the
 *   one with the best good day, the safe line the one with the best bad day.
 * @property recomputeShiftDegrees how far the wind of the moment has to move before the whole plan is
 *   worked out again. The plan is otherwise only redone when the boat sails into another square, because a
 *   line that is re-searched every second flickers between equally quick alternatives and a sailor cannot
 *   steer to a line that moves; the wind wandering a couple of degrees is not worth that.
 * @property seed the seed of the drawn winds, so the same course always gives the same advice.
 */
public data class RaceLineSettings(
    val runs: Int = 16,
    val currentWindWeight: Double = 0.8,
    val currentWindRangeMeters: Double = 200.0,
    val speedSpreadFactor: Double = 0.6,
    val minBoatSpeedMps: Double = 0.5,
    val riskFraction: Double = 0.2,
    val recomputeShiftDegrees: Double = 5.0,
    val seed: Long = 20260908L,
) {
    init {
        require(runs > 0) { "a Monte Carlo needs at least one run: $runs" }
        require(currentWindWeight in 0.0..1.0) { "the current wind's weight is a fraction: $currentWindWeight" }
        require(currentWindRangeMeters > 0.0) { "ranges must be positive: $currentWindRangeMeters" }
        require(minBoatSpeedMps > 0.0) { "the slowest a boat may go must be positive: $minBoatSpeedMps" }
        require(riskFraction > 0.0 && riskFraction < 0.5) { "the risk fraction is one tail: $riskFraction" }
        require(recomputeShiftDegrees >= 0.0) { "a wind cannot move less than nothing: $recomputeShiftDegrees" }
    }
}
