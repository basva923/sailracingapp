package com.sailracing.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WindModelTest {

    @Test
    fun `steady wind never changes`() {
        val wind = WindModel(meanDirectionDegrees = 20.0)
        assertEquals(20.0, wind.directionAt(0.0))
        assertEquals(20.0, wind.directionAt(1234.5))
    }

    @Test
    fun `oscillating wind swings either side of the mean and wraps`() {
        val wind = WindModel(meanDirectionDegrees = 2.0, oscillationDegrees = 8.0, periodSeconds = 240.0)
        assertEquals(2.0, wind.directionAt(0.0), 1e-9)
        assertEquals(10.0, wind.directionAt(60.0), 1e-9)
        assertEquals(2.0, wind.directionAt(120.0), 1e-9)
        assertEquals(354.0, wind.directionAt(180.0), 1e-9)
    }

    @Test
    fun `shear veers the wind to the right of the axis`() {
        val wind = WindModel(meanDirectionDegrees = 0.0, shearDegreesPerMeter = 0.02)
        assertEquals(0.0, wind.directionAt(5.0))
        assertEquals(2.0, wind.directionAt(5.0, acrossMeters = 100.0), 1e-9)
        assertEquals(358.0, wind.directionAt(5.0, acrossMeters = -100.0), 1e-9)
        assertEquals(0.0, WindModel(0.0).directionAt(5.0, acrossMeters = 100.0))
    }

    @Test
    fun `a persistent shift keeps turning the wind one way`() {
        val veering = WindModel(meanDirectionDegrees = 10.0, veerDegreesPerHour = 20.0)
        assertEquals(10.0, veering.directionAt(0.0), 1e-9)
        assertEquals(20.0, veering.directionAt(1800.0), 1e-9)
        assertEquals(30.0, veering.directionAt(3600.0), 1e-9)
        assertEquals(350.0, WindModel(10.0, veerDegreesPerHour = -20.0).directionAt(3600.0), 1e-9)
    }

    @Test
    fun `the strength puffs, builds and differs from one side to the other`() {
        val steady = WindModel(meanDirectionDegrees = 0.0, meanSpeedKnots = 12.0)
        assertEquals(12.0, steady.speedKnotsAt(0.0))
        assertEquals(12.0, steady.speedKnotsAt(1234.5, acrossMeters = 100.0))

        val gusty = WindModel(0.0, meanSpeedKnots = 14.0, gustKnots = 6.0, gustPeriodSeconds = 240.0)
        assertEquals(14.0, gusty.speedKnotsAt(0.0), 1e-9)
        assertEquals(20.0, gusty.speedKnotsAt(60.0), 1e-9)
        assertEquals(8.0, gusty.speedKnotsAt(180.0), 1e-9)

        val sided = WindModel(0.0, meanSpeedKnots = 12.0, speedShearKnotsPerMeter = -0.015)
        assertEquals(15.0, sided.speedKnotsAt(0.0, acrossMeters = -200.0), 1e-9)
        assertEquals(9.0, sided.speedKnotsAt(0.0, acrossMeters = 200.0), 1e-9)

        val building = WindModel(0.0, meanSpeedKnots = 7.0, buildKnotsPerHour = 9.0)
        assertEquals(11.5, building.speedKnotsAt(1800.0), 1e-9)
        // A dying breeze never goes quite calm, so the boat keeps sailing.
        assertEquals(WindModel.MIN_SPEED_KNOTS, WindModel(0.0, meanSpeedKnots = 6.0, buildKnotsPerHour = -20.0).speedKnotsAt(3600.0))
    }

    @Test
    fun `periods and the strength must be positive`() {
        assertFailsWith<IllegalArgumentException> { WindModel(0.0, periodSeconds = 0.0) }
        assertFailsWith<IllegalArgumentException> { WindModel(0.0, gustPeriodSeconds = 0.0) }
        assertFailsWith<IllegalArgumentException> { WindModel(0.0, meanSpeedKnots = 0.0) }
    }
}
