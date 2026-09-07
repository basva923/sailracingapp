package com.sailracing.simulation

import kotlin.math.abs

/**
 * Boat speed as a fraction of [maxSpeedMps], by true wind angle. A simple dinghy-like polar:
 * stopped head to wind, fastest on a beam reach, slow dead downwind.
 *
 * @property upwindAngleDegrees the true wind angle this boat sails close-hauled.
 * @property downwindAngleDegrees the true wind angle this boat sails on its downwind course.
 */
public data class BoatPolar(
    val maxSpeedMps: Double = 3.0,
    val upwindAngleDegrees: Double = 45.0,
    val downwindAngleDegrees: Double = 140.0,
) {
    init {
        require(maxSpeedMps > 0.0) { "max speed must be positive: $maxSpeedMps" }
    }

    /** Boat speed in m/s at the given true wind angle (sign is ignored). */
    public fun speedMps(trueWindAngleDegrees: Double): Double {
        val twa = abs(trueWindAngleDegrees).coerceIn(0.0, 180.0)
        var index = 0
        while (index < TABLE.size - 2 && twa > TABLE[index + 1].first) index++
        val (a, fa) = TABLE[index]
        val (b, fb) = TABLE[index + 1]
        val fraction = fa + (fb - fa) * (twa - a) / (b - a)
        return maxSpeedMps * fraction
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
