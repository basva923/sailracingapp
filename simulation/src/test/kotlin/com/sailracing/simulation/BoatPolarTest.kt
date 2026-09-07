package com.sailracing.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BoatPolarTest {

    private val polar = BoatPolar(maxSpeedMps = 2.0)

    @Test
    fun `table points are reproduced and interpolated`() {
        assertEquals(0.0, polar.speedMps(0.0), 1e-9)
        assertEquals(1.7, polar.speedMps(45.0), 1e-9)
        assertEquals(1.7, polar.speedMps(-45.0), 1e-9)
        assertEquals(2.0, polar.speedMps(90.0), 1e-9)
        assertEquals(1.8, polar.speedMps(140.0), 1e-9)
        assertEquals(1.2, polar.speedMps(180.0), 1e-9)
        assertEquals(1.2, polar.speedMps(250.0), 1e-9) // clamped to 180
        // halfway between 30 (0.15) and 45 (0.85)
        assertEquals(1.0, polar.speedMps(37.5), 1e-9)
    }

    @Test
    fun `close-hauled is the best upwind vmg`() {
        val vmg45 = polar.speedMps(45.0) * Math.cos(Math.toRadians(45.0))
        val vmg60 = polar.speedMps(60.0) * Math.cos(Math.toRadians(60.0))
        val vmg35 = polar.speedMps(35.0) * Math.cos(Math.toRadians(35.0))
        assertTrue(vmg45 > vmg60)
        assertTrue(vmg45 > vmg35)
    }

    @Test
    fun `max speed must be positive`() {
        assertFailsWith<IllegalArgumentException> { BoatPolar(maxSpeedMps = 0.0) }
    }
}
