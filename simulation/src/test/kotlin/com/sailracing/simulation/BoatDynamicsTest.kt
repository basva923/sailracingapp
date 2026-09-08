package com.sailracing.simulation

import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoatDynamicsTest {

    private val model = SailingModel(BoatPolar(maxSpeedMps = 3.0), turnRateDegreesPerSecond = 25.0, accelerationTimeConstantSeconds = 3.0)
    private val origin = GeoPoint(51.14, 5.83)

    @Test
    fun `turns are rate limited`() {
        val boat = BoatState(0.0, origin, headingDegrees = 0.0, speedMps = 3.0)
        val stepped = BoatDynamics.step(boat, targetHeadingDegrees = 90.0, windDirectionDegrees = 0.0, model, dtSeconds = 1.0)
        assertEquals(25.0, stepped.headingDegrees, 1e-9)
        assertEquals(1.0, stepped.timeSeconds)
        val left = BoatDynamics.step(boat, targetHeadingDegrees = 300.0, windDirectionDegrees = 0.0, model, dtSeconds = 1.0)
        assertEquals(335.0, left.headingDegrees, 1e-9)
        val small = BoatDynamics.step(boat, targetHeadingDegrees = 10.0, windDirectionDegrees = 0.0, model, dtSeconds = 1.0)
        assertEquals(10.0, small.headingDegrees, 1e-9)
    }

    @Test
    fun `speed converges to the polar speed and the boat moves`() {
        var boat = BoatState(0.0, origin, headingDegrees = 90.0, speedMps = 0.0)
        repeat(30) { boat = BoatDynamics.step(boat, 90.0, 0.0, model, 1.0) }
        assertEquals(3.0, boat.speedMps, 1e-3)
        assertEquals(90.0, Geo.initialBearingDegrees(origin, boat.position), 0.1)
        assertTrue(Geo.distanceMeters(origin, boat.position) > 70.0)
    }

    @Test
    fun `less wind is less speed, and the reference wind is what the polar means`() {
        fun settledSpeed(windSpeedKnots: Double): Double {
            var boat = BoatState(0.0, origin, headingDegrees = 90.0, speedMps = 0.0)
            repeat(30) { boat = BoatDynamics.step(boat, 90.0, 0.0, model, 1.0, windSpeedKnots) }
            return boat.speedMps
        }
        assertEquals(3.0, settledSpeed(WindModel.DEFAULT_SPEED_KNOTS), 1e-3)
        assertEquals(1.5, settledSpeed(3.0), 1e-3)
        assertTrue(settledSpeed(20.0) > 3.0)
    }

    @Test
    fun `head to wind the boat stalls`() {
        var boat = BoatState(0.0, origin, headingDegrees = 0.0, speedMps = 3.0)
        repeat(30) { boat = BoatDynamics.step(boat, 0.0, 0.0, model, 1.0) }
        assertEquals(0.0, boat.speedMps, 1e-3)
    }
}
