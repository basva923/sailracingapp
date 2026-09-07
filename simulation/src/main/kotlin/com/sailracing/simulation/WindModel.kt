package com.sailracing.simulation

import com.sailracing.domain.geo.Angles
import kotlin.math.PI
import kotlin.math.sin

/**
 * True wind that oscillates sinusoidally around a mean direction, as real wind does over a beat, and may
 * differ from one side of the course to the other, as it does near a shore.
 *
 * @property meanDirectionDegrees direction the wind blows from, on the course axis.
 * @property oscillationDegrees amplitude of the oscillation (peak shift to either side).
 * @property periodSeconds duration of one full oscillation.
 * @property shearDegreesPerMeter how much the wind veers per metre to the right of the course axis
 *   (looking upwind); negative when it backs to the right.
 */
public data class WindModel(
    val meanDirectionDegrees: Double,
    val oscillationDegrees: Double = 0.0,
    val periodSeconds: Double = 240.0,
    val shearDegreesPerMeter: Double = 0.0,
) {
    init {
        require(periodSeconds > 0.0) { "period must be positive: $periodSeconds" }
    }

    /** The wind on the course axis. */
    public fun directionAt(timeSeconds: Double): Double = directionAt(timeSeconds, acrossMeters = 0.0)

    /** The wind [acrossMeters] to the right of the course axis. */
    public fun directionAt(timeSeconds: Double, acrossMeters: Double): Double = Angles.normalize(
        meanDirectionDegrees + oscillationDegrees * sin(2 * PI * timeSeconds / periodSeconds) + shearDegreesPerMeter * acrossMeters,
    )
}
