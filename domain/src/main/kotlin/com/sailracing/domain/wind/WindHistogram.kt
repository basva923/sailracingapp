package com.sailracing.domain.wind

import com.sailracing.domain.geo.Angles
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** One bin of a histogram window: [offsetDegrees] relative to the window centre and its sample [count]. */
public data class HistogramBin(val offsetDegrees: Int, val count: Int)

/**
 * Immutable histogram of wind directions with one-degree bins.
 * Every mutation returns a new instance, so it can live inside an immutable race state.
 */
public class WindHistogram private constructor(private val bins: IntArray) {

    public constructor() : this(IntArray(BIN_COUNT))

    public val totalSamples: Int get() = bins.sum()

    public val isEmpty: Boolean get() = bins.all { it == 0 }

    public fun count(degrees: Int): Int = bins[Angles.normalize(degrees)]

    public operator fun plus(degrees: Double): WindHistogram {
        val copy = bins.copyOf()
        copy[Angles.normalize(degrees.roundToInt())]++
        return WindHistogram(copy)
    }

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

    public companion object {
        public const val BIN_COUNT: Int = 360
        private const val RESULTANT_EPSILON: Double = 1e-9

        public fun fromCounts(counts: List<Int>): WindHistogram {
            require(counts.size == BIN_COUNT) { "expected $BIN_COUNT counts, got ${counts.size}" }
            return WindHistogram(counts.toIntArray())
        }
    }
}
