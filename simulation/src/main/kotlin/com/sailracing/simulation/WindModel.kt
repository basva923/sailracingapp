package com.sailracing.simulation

import com.sailracing.domain.geo.Angles
import kotlin.math.PI
import kotlin.math.sin

/**
 * True wind that oscillates sinusoidally around a mean direction, as real wind does over a beat.
 *
 * @property meanDirectionDegrees direction the wind blows from.
 * @property oscillationDegrees amplitude of the oscillation (peak shift to either side).
 * @property periodSeconds duration of one full oscillation.
 */
public data class WindModel(
    val meanDirectionDegrees: Double,
    val oscillationDegrees: Double = 0.0,
    val periodSeconds: Double = 240.0,
) {
    init {
        require(periodSeconds > 0.0) { "period must be positive: $periodSeconds" }
    }

    public fun directionAt(timeSeconds: Double): Double =
        Angles.normalize(meanDirectionDegrees + oscillationDegrees * sin(2 * PI * timeSeconds / periodSeconds))
}
