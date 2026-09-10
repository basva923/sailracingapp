package com.sailracing.domain.course

/**
 * The dials of the race line's Monte Carlo: how many winds are tried, how many of them are worth searching
 * a beat in, and which end of the spread counts as luck. What the wind over the course is believed to be
 * in the first place belongs to [WindFieldSettings]: this is only what the search makes of it.
 *
 * The defaults are for a dinghy course of a few hundred metres in a shifty coastal or inland breeze.
 *
 * @property runs how many winds the Monte Carlo draws. Every candidate line is timed through all of them,
 *   so the lines are compared over the same winds and a difference between two lines is a real difference
 *   and not the noise of two separate draws. A drawn wind is a handful of dice per big square, so a
 *   thousand of them cost almost nothing; it is searching a beat that is dear, which is what [searchRuns]
 *   is for.
 * @property searchRuns how many of those winds get a beat searched in them, to collect the lines worth
 *   judging. Every search is one candidate - the right answer to *some* wind the course might have - and
 *   they saturate quickly: past a dozen or two the searches keep finding lines that are already in the
 *   list. The line through the mean wind is always a candidate as well.
 *
 *   It is also the one dear part of a plan - a searched beat costs a hundred drawn winds - so this is the
 *   dial to turn when the phone is thinking too long, and [runs] is the one to turn up for a steadier
 *   answer to "how bad is its bad day".
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
    val runs: Int = 1000,
    val searchRuns: Int = 16,
    val riskFraction: Double = 0.2,
    val recomputeShiftDegrees: Double = 5.0,
    val seed: Long = 20260908L,
) {
    init {
        require(runs > 0) { "a Monte Carlo needs at least one run: $runs" }
        require(searchRuns > 0) { "a plan needs at least one searched beat: $searchRuns" }
        require(riskFraction > 0.0 && riskFraction < 0.5) { "the risk fraction is one tail: $riskFraction" }
        require(recomputeShiftDegrees >= 0.0) { "a wind cannot move less than nothing: $recomputeShiftDegrees" }
    }

    /** How many winds are actually searched: there is no searching a wind that was never drawn. */
    public val searched: Int get() = minOf(searchRuns, runs)
}
