package com.sailracing.domain.wind

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

/**
 * How fast the boat has been sailing close-hauled somewhere, as a histogram of [BIN_WIDTH_MPS] wide bins.
 *
 * A mean speed would say a square is quick; the histogram says *how* it is quick, which is what a race
 * line needs: two squares with the same mean can be a steady breeze and a corner that is half puff, half
 * hole, and only one of them is worth the risk. Counts are weights rather than integers so that the
 * histogram of a square nobody sailed through can be blended from the squares around it.
 *
 * Bins are counted from zero, so bin *i* holds the speeds in `[i * BIN_WIDTH_MPS, (i + 1) * BIN_WIDTH_MPS)`;
 * anything faster than the last bin counts in the last bin, which no dinghy will ever reach.
 */
public class SpeedHistogram private constructor(private val bins: DoubleArray) {

    public constructor() : this(DoubleArray(BIN_COUNT))

    public val totalWeight: Double get() = bins.sum()

    public val isEmpty: Boolean get() = bins.all { it == 0.0 }

    /** The weight in the bin holding [speedMps]. */
    public fun weightAt(speedMps: Double): Double = bins[binOf(speedMps)]

    /** The same histogram with one more sample of [speedMps] in it. */
    public operator fun plus(speedMps: Double): SpeedHistogram {
        val copy = bins.copyOf()
        copy[binOf(speedMps)] += 1.0
        return SpeedHistogram(copy)
    }

    /** The two histograms laid on top of each other: everything measured in either place. */
    public operator fun plus(other: SpeedHistogram): SpeedHistogram =
        SpeedHistogram(DoubleArray(BIN_COUNT) { bins[it] + other.bins[it] })

    /** The mean speed, or null when nothing was measured. */
    public val meanMps: Double?
        get() {
            val total = totalWeight
            if (total == 0.0) return null
            var sum = 0.0
            for (bin in 0 until BIN_COUNT) sum += bins[bin] * middleOf(bin)
            return sum / total
        }

    /** The standard deviation of the speed, or null when nothing was measured. */
    public val spreadMps: Double?
        get() {
            val mean = meanMps ?: return null
            var sum = 0.0
            for (bin in 0 until BIN_COUNT) {
                val difference = middleOf(bin) - mean
                sum += bins[bin] * difference * difference
            }
            return sqrt(sum / totalWeight)
        }

    /**
     * The speed at [fraction] of the way through the distribution: the median at 0.5, the slowest tenth of
     * the time at 0.1. Drawing a uniform [fraction] and reading this off is a draw from the histogram
     * itself, whatever shape it has - a hole and a puff come out as a hole and a puff, not as their mean.
     * Inside the bin it lands in the speed is interpolated, so the result is not stepped.
     */
    public fun quantile(fraction: Double): Double {
        val total = totalWeight
        require(total > 0.0) { "an empty speed histogram has no quantiles" }
        var remaining = fraction.coerceIn(0.0, 1.0) * total
        var last = 0
        for (bin in 0 until BIN_COUNT) {
            val weight = bins[bin]
            if (weight <= 0.0) continue
            if (remaining <= weight) return (bin + min(remaining / weight, 1.0)) * BIN_WIDTH_MPS
            remaining -= weight
            last = bin
        }
        // Weights that were blended from the squares around do not add up to their own total to the last
        // bit, so the walk can come out of the top bin with a crumb of it left over: that is the top.
        return (last + 1) * BIN_WIDTH_MPS
    }


    override fun equals(other: Any?): Boolean = other is SpeedHistogram && bins.contentEquals(other.bins)

    override fun hashCode(): Int = bins.contentHashCode()

    override fun toString(): String = "SpeedHistogram(samples=$totalWeight, mean=$meanMps)"

    private fun middleOf(bin: Int): Double = (bin + 0.5) * BIN_WIDTH_MPS

    public companion object {
        /** Bin width in metres per second: fine enough to tell a lull from a puff, coarse enough to fill up. */
        public const val BIN_WIDTH_MPS: Double = 0.25

        /** Bins from zero to 12 m/s (23 knots), which no boat this app is for sails close-hauled. */
        public const val BIN_COUNT: Int = 48

        /** The bin a speed falls in; anything beyond the last bin counts in it. */
        public fun binOf(speedMps: Double): Int =
            floor(speedMps / BIN_WIDTH_MPS).toInt().coerceIn(0, BIN_COUNT - 1)

        /** A histogram of these speeds: one sample each. */
        public fun of(vararg speedsMps: Double): SpeedHistogram =
            speedsMps.fold(SpeedHistogram()) { histogram, speed -> histogram + speed }

        /** A histogram straight from its bin weights: how one is built up in a loop without copying it per sample. */
        public fun fromWeights(weights: DoubleArray): SpeedHistogram {
            require(weights.size == BIN_COUNT) { "expected $BIN_COUNT bins, got ${weights.size}" }
            return SpeedHistogram(weights.copyOf())
        }
    }
}
