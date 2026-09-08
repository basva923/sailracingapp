package com.sailracing.simulation

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import kotlin.math.exp

/** The simulated boat at one instant. */
public data class BoatState(
    val timeSeconds: Double,
    val position: GeoPoint,
    val headingDegrees: Double,
    val speedMps: Double,
)

/**
 * How the boat responds to the helm.
 *
 * @property turnRateDegreesPerSecond maximum rate of turn.
 * @property accelerationTimeConstantSeconds how quickly speed converges to the polar speed.
 */
public data class SailingModel(
    val polar: BoatPolar = BoatPolar(),
    val turnRateDegreesPerSecond: Double = 25.0,
    val accelerationTimeConstantSeconds: Double = 3.0,
)

public object BoatDynamics {

    /**
     * Advances [state] by [dtSeconds], turning towards [targetHeadingDegrees] in [windSpeedKnots] of wind
     * from [windDirectionDegrees].
     */
    public fun step(
        state: BoatState,
        targetHeadingDegrees: Double,
        windDirectionDegrees: Double,
        model: SailingModel,
        dtSeconds: Double,
        windSpeedKnots: Double = model.polar.referenceWindKnots,
    ): BoatState {
        val maxTurn = model.turnRateDegreesPerSecond * dtSeconds
        val turn = Angles.signedDifference(state.headingDegrees, targetHeadingDegrees).coerceIn(-maxTurn, maxTurn)
        val heading = Angles.normalize(state.headingDegrees + turn)

        val twa = Angles.signedDifference(heading, windDirectionDegrees)
        val targetSpeed = model.polar.speedMps(twa, windSpeedKnots)
        val blend = 1.0 - exp(-dtSeconds / model.accelerationTimeConstantSeconds)
        val speed = state.speedMps + (targetSpeed - state.speedMps) * blend

        val position = Geo.destination(state.position, heading, speed * dtSeconds)
        return BoatState(state.timeSeconds + dtSeconds, position, heading, speed)
    }
}
