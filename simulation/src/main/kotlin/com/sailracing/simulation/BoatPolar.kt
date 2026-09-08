package com.sailracing.simulation

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Boat speed as a fraction of [maxSpeedMps], by true wind angle. A simple dinghy-like polar:
 * stopped head to wind, fastest on a beam reach, slow dead downwind.
 *
 * The table is the boat in [referenceWindKnots] of breeze; in more or less wind the whole polar is
 * scaled by [windSpeedFactor], so a hole is sailed through slowly and a puff pays.
 *
 * @property upwindAngleDegrees the true wind angle this boat sails close-hauled.
 * @property downwindAngleDegrees the true wind angle this boat sails on its downwind course.
 * @property referenceWindKnots the wind strength [maxSpeedMps] is the boat's speed in.
 */
public data class BoatPolar(
    val maxSpeedMps: Double = 3.0,
    val upwindAngleDegrees: Double = 45.0,
    val downwindAngleDegrees: Double = 140.0,
    val referenceWindKnots: Double = WindModel.DEFAULT_SPEED_KNOTS,
) {
    init {
        require(maxSpeedMps > 0.0) { "max speed must be positive: $maxSpeedMps" }
        require(referenceWindKnots > 0.0) { "reference wind must be positive: $referenceWindKnots" }
    }

    /** Boat speed in m/s at the given true wind angle (sign is ignored), in [windSpeedKnots] of breeze. */
    public fun speedMps(trueWindAngleDegrees: Double, windSpeedKnots: Double): Double =
        speedMps(trueWindAngleDegrees) * windSpeedFactor(windSpeedKnots)

    /**
     * How much of the reference-wind polar the boat sails in [windSpeedKnots]: the square root of the
     * wind ratio, as a displacement hull roughly follows, and no more than [MAX_WIND_SPEED_FACTOR] however
     * hard it blows - a boat that is already at hull speed does not keep gaining with the gusts.
     */
    public fun windSpeedFactor(windSpeedKnots: Double): Double =
        sqrt((windSpeedKnots / referenceWindKnots).coerceAtLeast(0.0)).coerceAtMost(MAX_WIND_SPEED_FACTOR)

    /** Boat speed in m/s at the given true wind angle (sign is ignored), in the reference wind. */
    public fun speedMps(trueWindAngleDegrees: Double): Double {
        val twa = abs(trueWindAngleDegrees).coerceIn(0.0, 180.0)
        var index = 0
        while (index < TABLE.size - 2 && twa > TABLE[index + 1].first) index++
        val (a, fa) = TABLE[index]
        val (b, fb) = TABLE[index + 1]
        val fraction = fa + (fb - fa) * (twa - a) / (b - a)
        return maxSpeedMps * fraction
    }

    public companion object {
        /** The most the polar is stretched by wind alone, whatever the gust. */
        public const val MAX_WIND_SPEED_FACTOR: Double = 1.4
    }
}

/** (true wind angle, fraction of max speed) */
private val TABLE = listOf(
    0.0 to 0.0,
    30.0 to 0.15,
    45.0 to 0.85,
    60.0 to 0.95,
    90.0 to 1.0,
    120.0 to 0.95,
    140.0 to 0.9,
    160.0 to 0.75,
    180.0 to 0.6,
)
