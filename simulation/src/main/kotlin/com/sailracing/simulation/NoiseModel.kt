package com.sailracing.simulation

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** Deterministic (seeded) Gaussian noise applied to simulated GPS fixes. */
public class NoiseModel(
    seed: Long,
    public val positionSigmaMeters: Double = 0.0,
    public val speedSigmaMps: Double = 0.0,
    public val courseSigmaDegrees: Double = 0.0,
) {
    private val random = Random(seed)

    public val isSilent: Boolean get() = positionSigmaMeters == 0.0 && speedSigmaMps == 0.0 && courseSigmaDegrees == 0.0

    /** A standard normal deviate, scaled by [sigma]. */
    public fun gaussian(sigma: Double): Double {
        if (sigma == 0.0) return 0.0
        val u1 = 1.0 - random.nextDouble()
        val u2 = random.nextDouble()
        return sigma * sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    /** A uniformly random bearing for displacing positions. */
    public fun bearing(): Double = random.nextDouble() * 360.0

    public companion object {
        public fun none(): NoiseModel = NoiseModel(seed = 0)

        /** Typical consumer GPS on a moving boat. */
        public fun typicalGps(seed: Long): NoiseModel =
            NoiseModel(seed, positionSigmaMeters = 1.5, speedSigmaMps = 0.1, courseSigmaDegrees = 2.0)
    }
}
