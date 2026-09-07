package com.sailracing.simulation

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoiseModelTest {

    @Test
    fun `silent model produces zeros`() {
        val none = NoiseModel.none()
        assertTrue(none.isSilent)
        assertEquals(0.0, none.gaussian(0.0))
    }

    @Test
    fun `gaussian noise is seeded and roughly the right size`() {
        val a = NoiseModel.typicalGps(seed = 7)
        val b = NoiseModel.typicalGps(seed = 7)
        assertFalse(a.isSilent)
        val samples = List(2000) { a.gaussian(2.0) }
        val other = List(2000) { b.gaussian(2.0) }
        assertEquals(samples, other)
        val mean = samples.average()
        val sigma = Math.sqrt(samples.sumOf { (it - mean) * (it - mean) } / samples.size)
        assertTrue(abs(mean) < 0.2, "mean $mean")
        assertTrue(abs(sigma - 2.0) < 0.2, "sigma $sigma")
        val bearing = a.bearing()
        assertTrue(bearing >= 0.0 && bearing < 360.0)
    }
}
