package com.sailracing.domain.stats

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The normal distribution, as much of it as the race line's Monte Carlo needs: a draw from it and its
 * cumulative distribution. Kotlin's [Random] only makes uniform numbers, so both are built here rather
 * than taken from the platform, which keeps the domain module pure Kotlin.
 */
public object Gaussian {

    /**
     * One draw from the standard normal distribution (mean 0, standard deviation 1), by the Box-Muller
     * transform: two uniforms in, one normal out. The second normal it could make is thrown away, so that
     * a draw needs no state and the same seed always gives the same sequence.
     */
    public fun sample(random: Random): Double {
        // 1 - nextDouble() lands in (0, 1]: the log of it is finite, where the log of a 0 would not be.
        val radius = sqrt(-2.0 * ln(1.0 - random.nextDouble()))
        return radius * cos(2.0 * PI * random.nextDouble())
    }

    /**
     * The probability that a standard normal draw is at most [z], to about seven decimals
     * (Zelen and Severo's rational approximation). It turns a normal draw into a uniform one, which is how
     * a correlated field of normals becomes a correlated field of samples from any other distribution.
     */
    public fun cdf(z: Double): Double {
        val x = abs(z)
        val t = 1.0 / (1.0 + P * x)
        val density = exp(-x * x / 2.0) / sqrt(2.0 * PI)
        val tail = density * t * (B1 + t * (B2 + t * (B3 + t * (B4 + t * B5))))
        return if (z >= 0.0) 1.0 - tail else tail
    }

    private const val P: Double = 0.2316419
    private const val B1: Double = 0.319381530
    private const val B2: Double = -0.356563782
    private const val B3: Double = 1.781477937
    private const val B4: Double = -1.821255978
    private const val B5: Double = 1.330274429
}
