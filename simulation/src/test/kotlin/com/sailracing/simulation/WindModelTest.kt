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
    fun `period must be positive`() {
        assertFailsWith<IllegalArgumentException> { WindModel(0.0, periodSeconds = 0.0) }
    }
}
