package com.sailracing.domain.wind

import com.sailracing.domain.geo.Angles
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** One bin of a histogram window: [offsetDegrees] relative to the window centre and its sample [count]. */
public data class HistogramBin(val offsetDegrees: Int, val count: Int)

/**
 * Immutable histogram of wind directions with one-degree bins.
 * Every mutation returns a new instance, so it can live inside an immutable race state.
 */
public class WindHistogram private constructor(private val bins: IntArray) {

    public constructor() : this(IntArray(BIN_COUNT))

    /** Counted once, when the histogram is made: a drawn wind asks for it far more often than a sample changes it. */
    public val totalSamples: Int = bins.sum()

    public val isEmpty: Boolean get() = totalSamples == 0

    public fun count(degrees: Int): Int = bins[Angles.normalize(degrees)]

    public operator fun plus(degrees: Double): WindHistogram {
        val copy = bins.copyOf()
        copy[Angles.normalize(degrees.roundToInt())]++
        return WindHistogram(copy)
    }

    /** The two histograms laid on top of each other: everything measured in either place. */
    public operator fun plus(other: WindHistogram): WindHistogram =
        WindHistogram(IntArray(BIN_COUNT) { bins[it] + other.bins[it] })

    /** Bins from [centerDegrees] - [halfWidthDegrees] to + [halfWidthDegrees], inclusive. */
    public fun window(centerDegrees: Int, halfWidthDegrees: Int): List<HistogramBin> =
        (-halfWidthDegrees..halfWidthDegrees).map { offset -> HistogramBin(offset, count(centerDegrees + offset)) }

    /** The most frequent direction, or null when empty. Ties resolve to the lowest degree. */
    public fun mode(): Int? {
        if (isEmpty) return null
        var best = 0
        for (i in 1 until BIN_COUNT) if (bins[i] > bins[best]) best = i
        return best
    }

    /** Circular mean direction in [0, 360), or null when empty. */
    public fun meanDirection(): Double? {
        val resultant = resultant() ?: return null
        return Angles.normalize(Angles.toDegrees(atan2(resultant.second, resultant.first)))
    }

    /**
     * How much of one direction the samples are, from 0 (spread evenly around the compass, no direction at
     * all) to 1 (every sample the same): the length of the mean resultant. It is what weighs a histogram's
     * mean against another's - a wind that wandered all day pulls a blend less far than a steady one.
     */
    public val concentration: Double
        get() {
            val (c, s) = resultant() ?: return 0.0
            return sqrt(c * c + s * s) / totalSamples
        }

    /**
     * This histogram made ready to draw directions from, or null when nothing was measured. Built once and
     * drawn from as often as the simulation needs; see [Draws].
     */
    public fun draws(): Draws? {
        if (isEmpty) return null
        val degrees = ArrayList<Int>()
        val cumulative = ArrayList<Int>()
        var running = 0
        for (degree in 0 until BIN_COUNT) {
            if (bins[degree] == 0) continue
            running += bins[degree]
            degrees += degree
            cumulative += running
        }
        return Draws(degrees.toIntArray(), cumulative.toIntArray())
    }

    /** Circular standard deviation in degrees, or null when empty. */
    public fun standardDeviation(): Double? {
        val (c, s) = resultant() ?: return null
        val n = totalSamples.toDouble()
        val r = sqrt(c * c + s * s) / n
        // Perfectly opposing samples leave a resultant of (numerically almost) zero: no meaningful deviation.
        if (r < RESULTANT_EPSILON) return null
        return Angles.toDegrees(sqrt(-2.0 * ln(r)))
    }

    public fun toCounts(): List<Int> = bins.toList()

    override fun equals(other: Any?): Boolean = other is WindHistogram && bins.contentEquals(other.bins)

    override fun hashCode(): Int = bins.contentHashCode()

    override fun toString(): String = "WindHistogram(samples=$totalSamples, mode=${mode()})"

    private fun resultant(): Pair<Double, Double>? {
        if (isEmpty) return null
        var c = 0.0
        var s = 0.0
        for (degree in 0 until BIN_COUNT) {
            val count = bins[degree]
            if (count == 0) continue
            val radians = Angles.toRadians(degree.toDouble())
            c += count * cos(radians)
            s += count * sin(radians)
        }
        return c to s
    }

    /**
     * A histogram ready to be drawn from: the directions it holds and the running total of their counts.
     * One draw is then a random number and a search through the directions that were actually measured,
     * rather than a walk around the whole compass - which is what makes drawing a thousand winds cheap.
     *
     * A drawn direction is not the bin's whole degree but anywhere inside it, so that a histogram of
     * one-degree bins draws a continuous wind rather than a staircase.
     */
    public class Draws internal constructor(private val degrees: IntArray, private val cumulative: IntArray) {

        /** How many samples the histogram it was built from holds. */
        public val samples: Int get() = cumulative[cumulative.size - 1]

        /** One direction in [0, 360), drawn from exactly the distribution the histogram holds. */
        public fun next(random: Random): Double {
            val target = random.nextInt(samples)
            var low = 0
            var high = degrees.size - 1
            while (low < high) {
                val middle = (low + high) / 2
                if (target < cumulative[middle]) high = middle else low = middle + 1
            }
            return Angles.normalize(degrees[low] + random.nextDouble() - 0.5)
        }
    }

    public companion object {
        public const val BIN_COUNT: Int = 360
        private const val RESULTANT_EPSILON: Double = 1e-9

        public fun fromCounts(counts: List<Int>): WindHistogram {
            require(counts.size == BIN_COUNT) { "expected $BIN_COUNT counts, got ${counts.size}" }
            return WindHistogram(counts.toIntArray())
        }

        /** A histogram straight from its bin counts: how one is built up in a loop without copying it per sample. */
        public fun fromCounts(counts: IntArray): WindHistogram {
            require(counts.size == BIN_COUNT) { "expected $BIN_COUNT counts, got ${counts.size}" }
            return WindHistogram(counts.copyOf())
        }
    }
}
