package com.sailracing.domain.course

import com.sailracing.domain.wind.Tack
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * How one line came out over all the winds the Monte Carlo tried.
 *
 * @property meanSeconds what it takes on an average day.
 * @property goodSeconds what it takes when the wind is kind: the time it beats in [RaceLineSettings.riskFraction]
 *   of the runs, one in five by default.
 * @property badSeconds what it takes when the wind is not: the time it is beaten by in as many runs.
 * @property spreadSeconds the standard deviation over the runs.
 */
public data class LineRisk(
    val meanSeconds: Double = 0.0,
    val goodSeconds: Double = 0.0,
    val badSeconds: Double = 0.0,
    val spreadSeconds: Double = 0.0,
) {
    /** How much of a gamble the line is: the seconds between its good day and its bad day. */
    public val riskSeconds: Double get() = badSeconds - goodSeconds

    public companion object {
        public val NONE: LineRisk = LineRisk()
    }
}

/**
 * The two lines up the beat: the one to sail and the one to gamble on.
 *
 * @property safe the line whose bad day is the least bad. It is the line the map draws: it gives away a
 *   little on a good day to lose less on a bad one, which over a series is how places are kept. Where
 *   nothing betters the line through the mean wind by a tack, that line is the safe one: a smaller
 *   difference than that is the luck of the draw, and a race line has to stay still enough to steer to.
 * @property fast the line with the best good day, when that is worth at least a tack; the safe line itself
 *   when it is not. Where the two differ it is the flyer: a side of the course that pays more when the wind
 *   does what it did in a fifth of the simulations.
 * @property winFraction how often the fast line actually beat the safe one, over the same winds: 0.5 is a
 *   coin toss, 0.2 is a line that only comes good when everything falls right.
 * @property sampled the best line of every simulation a beat was searched in - the spread of opinion
 *   itself. Where they all lie on top of each other the beat has one answer; where they fan out, it does
 *   not. There are [RaceLineSettings.searchRuns] of them, not one per run: every one of them is timed
 *   through all [runs] winds, but searching a thousand beats to draw a thousand lines that agree would be
 *   a thousand times the work for the same fan.
 * @property runs how many winds every candidate was timed through.
 */
public data class RaceLinePlan(
    val safe: RaceLine = RaceLine.NONE,
    val safeRisk: LineRisk = LineRisk.NONE,
    val fast: RaceLine = RaceLine.NONE,
    val fastRisk: LineRisk = LineRisk.NONE,
    val winFraction: Double = 0.0,
    val runs: Int = 0,
    val sampled: List<RaceLine> = emptyList(),
) {
    public val isEmpty: Boolean get() = safe.isEmpty

    /** True when the safest line is the fastest one as well: there is nothing to gamble on here. */
    public val agree: Boolean get() = safe.points == fast.points

    /** The whole plan pinned to where the boat is now; see [RaceLine.anchoredAt]. */
    public fun anchoredAt(boat: CoursePosition): RaceLinePlan = if (isEmpty) {
        this
    } else {
        copy(safe = safe.anchoredAt(boat), fast = fast.anchoredAt(boat), sampled = sampled.map { it.anchoredAt(boat) })
    }

    public companion object {
        /** No plan: no boat position, so nothing to sail from. */
        public val NONE: RaceLinePlan = RaceLinePlan()
    }
}

/**
 * The race line as a bet rather than a calculation: the fastest way up the beat is only as certain as the
 * wind it was worked out in, so this searches many winds instead of one and reports what they agree on.
 *
 * ## What it does
 *
 * 1. [RaceLineSettings.runs] winds are drawn over the racing area by [WindSampler] - each of them a whole
 *    field of directions and boat speeds, drawn per big square from the histogram measured there, from the
 *    histogram of the whole course, from the wind the boat is measuring right now, or from nothing at all.
 * 2. [RaceLineFinder] beats to the mark through the first [RaceLineSettings.searchRuns] of them, from the
 *    tack the boat is on. That gives one candidate line per searched wind, plus the line through the mean
 *    wind: every candidate is the right answer to *some* wind the course might have, which is what keeps
 *    the list sensible. Searching every drawn wind would only find the same lines again.
 * 3. Every candidate is then sailed through *every* drawn wind ([RaceLineFinder.secondsToSail]). Because
 *    all the candidates are timed over the same thousand winds, the difference between two of them is a
 *    real difference and not the noise of two separate draws.
 * 4. The line whose slow runs are least slow is the **safe** line; the line whose quick runs are quickest
 *    is the **fast** one. Both have to better the line through the mean wind - the answer to the wind as it
 *    stands - by more than a tack costs, or that plain line stays: a difference smaller than a tack is the
 *    noise of the draw, not a side of the course. In a settled wind all three are one line and there is
 *    nothing to choose.
 *
 * ## Why the times are what they are
 *
 * A line that overstands its layline in one wind is sailing away from the mark in another, and a line that
 * hunts a puff in one is sitting in a hole in another; both come out of the timing without any rule about
 * corners or laylines being written. The times reported are the mean over the runs, with the good and bad
 * day around them: the map shows the mean, and the difference between the two lines is the size of the bet.
 */
public object RaceLinePlanner {

    /**
     * The plan from [from] (the boat) to [to] (the windward mark) over the wind measured in [field].
     *
     * @param currentTack the tack the boat is on now: leaving on the other one costs a tack.
     * @param currentShiftDegrees the wind the boat is measuring right now, off the reference wind, from its
     *   own heading and tack angle; null when it is not close-hauled and has nothing to say about the wind.
     */
    public fun plan(
        field: WindField,
        from: CoursePosition,
        to: CoursePosition,
        tackAngleDegrees: Int,
        currentTack: Tack? = null,
        currentShiftDegrees: Double? = null,
        settings: RaceLineSettings = RaceLineSettings(),
        tackLossSeconds: Double = RaceLineFinder.TACK_LOSS_SECONDS,
    ): RaceLinePlan {
        val sampler = WindSampler.of(field, currentShiftDegrees)
        // One stream of dice for the whole Monte Carlo, from the settings' seed: the same course always
        // draws the same thousand winds, and a run is not repeatable one wind at a time.
        val random = Random(settings.seed)
        val winds = List(settings.runs) { sampler.draw(random) }
        fun beat(conditions: SailingConditions) =
            RaceLineFinder.find(conditions, from, to, tackAngleDegrees, currentTack, tackLossSeconds)

        // The mean wind first, so that a beat everyone agrees on is drawn exactly as it was before there
        // was a Monte Carlo, and a tie between candidates falls to it.
        val candidates = LinkedHashMap<List<Long>, RaceLine>()
        val mean = beat(sampler.mean)
        candidates[signature(mean)] = mean
        // A wind drawn wild enough to leave no beating to the mark at all sends the search back with the
        // straight course to it ([RaceLine.direct]). That is not a line anyone sails up a beat, and timing
        // it would flatter it, so it is left out - unless the leg is not a beat in the first place, which
        // is what the mean wind says.
        val sampled = winds.take(settings.searched).map(::beat).filter { mean.direct || !it.direct }
        for (line in sampled) candidates.putIfAbsent(signature(line), line)

        val scored = candidates.values.map { line ->
            Scored(line, winds.map { RaceLineFinder.secondsToSail(it, line, tackAngleDegrees, currentTack, tackLossSeconds) }, settings)
        }
        // The line through the mean wind is the answer to the wind as it stands, and it is the first
        // candidate; another line has to better its bad day by more than a tack to take its place. Half a
        // dozen lines up an even beat are within a second of one another and which of them has the best bad
        // day is the luck of a thousand draws, not a side of the course - and a race line that jumps from
        // one of them to the next as the dice fall cannot be steered to.
        val plain = scored.first()
        val steadiest = scored.minBy { it.risk.badSeconds }
        val safe = if (plain.risk.badSeconds - steadiest.risk.badSeconds < tackLossSeconds) plain else steadiest
        val bold = scored.minBy { it.risk.goodSeconds }
        // In a settled wind a dozen lines are within a second of one another and the chance of the draw
        // decides which of them has the best good day. A flyer has to promise more than a tack costs before
        // it is worth drawing beside the race line, or the map would offer a gamble on nothing at all.
        val fast = if (safe.risk.goodSeconds - bold.risk.goodSeconds < tackLossSeconds) safe else bold
        return RaceLinePlan(
            safe = safe.line.copy(seconds = safe.risk.meanSeconds),
            safeRisk = safe.risk,
            fast = fast.line.copy(seconds = fast.risk.meanSeconds),
            fastRisk = fast.risk,
            winFraction = fast.times.indices.count { fast.times[it] < safe.times[it] } / fast.times.size.toDouble(),
            runs = settings.runs,
            sampled = sampled,
        )
    }

    /** The value [fraction] of the way through [sorted], between the two runs it falls between. */
    public fun percentile(sorted: List<Double>, fraction: Double): Double {
        val position = fraction.coerceIn(0.0, 1.0) * (sorted.size - 1)
        val below = floor(position).toInt()
        val above = ceil(position).toInt()
        return sorted[below] + (sorted[above] - sorted[below]) * (position - below)
    }

    /** One candidate line and what it took in every drawn wind, in the order they were drawn. */
    private class Scored(val line: RaceLine, val times: List<Double>, settings: RaceLineSettings) {
        val risk: LineRisk = run {
            val mean = times.average()
            val sorted = times.sorted()
            LineRisk(
                meanSeconds = mean,
                goodSeconds = percentile(sorted, settings.riskFraction),
                badSeconds = percentile(sorted, 1.0 - settings.riskFraction),
                spreadSeconds = sqrt(times.sumOf { (it - mean) * (it - mean) } / times.size),
            )
        }
    }

    /** Two lines that are the same track to within half a metre are one candidate, and timed once. */
    private fun signature(line: RaceLine): List<Long> =
        line.points.flatMap { listOf((it.acrossMeters * 2.0).roundToLong(), (it.upwindMeters * 2.0).roundToLong()) }
}
