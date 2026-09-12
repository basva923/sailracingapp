package com.sailracing.domain.wind

import com.sailracing.domain.geo.Angles
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * One estimated wind direction, with what it was read off.
 *
 * @property directionDegrees the wind the heading implied: the heading turned by the tack angle (or the
 *   downwind angle) on the side the wind was on.
 * @property upwind true when the boat was close-hauled at the time - within the close-hauled band of the
 *   tack angle - so the sample counts for the wind statistics.
 * @property headingDegrees the heading the sample was read off, and [tack] the side the wind was on, both
 *   judged against the reference wind of the moment; null in a sample that never had them.
 * @property downwind true when the boat was on its downwind angle at the time.
 */
public data class WindSample(
    val timestampMillis: Long,
    val directionDegrees: Double,
    val upwind: Boolean,
    val headingDegrees: Double? = null,
    val tack: Tack? = null,
    val downwind: Boolean = false,
)

/** A bounded, chronological list of wind samples; oldest samples drop off when [capacity] is exceeded. */
public data class WindHistory(val samples: List<WindSample> = emptyList(), val capacity: Int = DEFAULT_CAPACITY) {

    init {
        require(capacity > 0) { "capacity must be positive: $capacity" }
    }

    public val latest: WindSample? get() = samples.lastOrNull()

    public operator fun plus(sample: WindSample): WindHistory {
        val updated = if (samples.size >= capacity) samples.drop(samples.size - capacity + 1) + sample else samples + sample
        return copy(samples = updated)
    }

    public fun last(count: Int): List<WindSample> = samples.takeLast(count)

    /** The samples taken at or after [sinceMillis], oldest first: the history is chronological, so it is the tail. */
    public fun since(sinceMillis: Long): List<WindSample> = samples.takeLastWhile { it.timestampMillis >= sinceMillis }

    /** The most recent [count] samples taken while beating, oldest first: what the wind is doing lately. */
    public fun recentUpwind(count: Int): List<WindSample> = samples.filter { it.upwind }.takeLast(count)

    /**
     * The wind the boat has been measuring over its last [count] close-hauled samples, or null when it has
     * none. Over a handful of samples this is the wind of the moment, steadied against the wave that
     * knocked the boat off course; over hundreds of them it is the trend.
     */
    public fun recentUpwindMeanDegrees(count: Int): Double? = meanDirectionDegrees(recentUpwind(count))

    /**
     * The middle of the wind measured close-hauled since [sinceMillis]: the circular median of the
     * estimates of the close-hauled samples, or null with fewer than [minSamples] of them. The median,
     * not the mean, so that the odd reach that got counted and the tail of a passing shift do not pull
     * it: half the samples were veered from it and half backed, which is what a shift is judged against.
     */
    public fun medianCloseHauledDirectionDegrees(sinceMillis: Long, minSamples: Int): Double? {
        val directions = since(sinceMillis).mapNotNull { sample -> sample.directionDegrees.takeIf { sample.upwind } }
        return if (directions.size < minSamples) null else medianOf(directions)
    }

    /**
     * The heading the boat has been holding close-hauled on [tack] since [sinceMillis]: the circular mean of
     * the headings of its close-hauled samples there, or null with fewer than [minSamples] of them. It is
     * half of what the reference wind is made of - see [WindReference].
     */
    public fun closeHauledHeadingDegrees(tack: Tack, sinceMillis: Long, minSamples: Int): Double? {
        val headings = since(sinceMillis).mapNotNull { sample -> sample.headingDegrees?.takeIf { sample.upwind && sample.tack == tack } }
        return if (headings.size < minSamples) null else meanOf(headings)
    }

    /**
     * The wind of the moment on [tack]: the circular mean of the last [count] estimates taken on it since
     * [sinceMillis] while it was on the angle - close-hauled, or on the downwind angle when [downwind] - or
     * null when there is none. Ten of them is a wave and a wobble of the helm averaged out; the boat that
     * has just tacked has one, and that one is its wind.
     */
    public fun steadyDirectionDegrees(tack: Tack, downwind: Boolean, count: Int, sinceMillis: Long): Double? {
        val recent = since(sinceMillis)
            .filter { it.tack == tack && (if (downwind) it.downwind else it.upwind) }
            .takeLast(count)
        return meanDirectionDegrees(recent)
    }

    /**
     * The heading the boat has been holding lately: the circular mean of the headings of the last [count]
     * samples since [sinceMillis] that lie within [maxSpreadDegrees] of [nearDegrees] (the heading now), so
     * that the samples of the tack before the last one, a right angle away, do not count. Null with none.
     * It is what the wind is set from when the sailor says which tack the boat is on.
     */
    public fun steadyHeadingDegrees(nearDegrees: Double, count: Int, sinceMillis: Long, maxSpreadDegrees: Double): Double? {
        val headings = since(sinceMillis)
            .mapNotNull { it.headingDegrees }
            .filter { abs(Angles.signedDifference(nearDegrees, it)) <= maxSpreadDegrees }
            .takeLast(count)
        return if (headings.isEmpty()) null else meanOf(headings)
    }

    public fun withCapacity(newCapacity: Int): WindHistory = WindHistory(samples.takeLast(newCapacity), newCapacity)

    public companion object {
        public const val DEFAULT_CAPACITY: Int = 3600

        /** The circular mean direction of [samples] in [0, 360), or null when there are none. */
        public fun meanDirectionDegrees(samples: List<WindSample>): Double? =
            if (samples.isEmpty()) null else meanOf(samples.map { it.directionDegrees })

        /**
         * The circular median: the value with as many of the others clockwise of it as anticlockwise,
         * measured from the mean so that a set straddling north sorts as one; the two middle values are
         * averaged when there is no single middle.
         */
        private fun medianOf(degrees: List<Double>): Double {
            val mean = meanOf(degrees)
            val offsets = degrees.map { Angles.signedDifference(mean, it) }.sorted()
            val middle = offsets.size / 2
            val median = if (offsets.size % 2 == 1) offsets[middle] else (offsets[middle - 1] + offsets[middle]) / 2
            return Angles.normalize(mean + median)
        }

        private fun meanOf(degrees: List<Double>): Double {
            var c = 0.0
            var s = 0.0
            for (value in degrees) {
                val radians = Angles.toRadians(value)
                c += cos(radians)
                s += sin(radians)
            }
            return Angles.normalize(Angles.toDegrees(atan2(s, c)))
        }
    }
}
