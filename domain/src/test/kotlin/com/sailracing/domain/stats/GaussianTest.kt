package com.sailracing.domain.stats

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GaussianTest {

    @Test
    fun `draws have the mean and the spread of a standard normal`() {
        val random = Random(4)
        val draws = List(20_000) { Gaussian.sample(random) }
        val mean = draws.average()
        val deviation = sqrt(draws.sumOf { (it - mean) * (it - mean) } / draws.size)
        assertEquals(0.0, mean, 0.03, "the draws are not centred")
        assertEquals(1.0, deviation, 0.03, "the draws are not a standard normal")
        // Two thirds within one, and the tails are there but rare.
        assertEquals(0.683, draws.count { abs(it) <= 1.0 } / draws.size.toDouble(), 0.02)
        assertEquals(0.954, draws.count { abs(it) <= 2.0 } / draws.size.toDouble(), 0.01)
        assertTrue(draws.any { abs(it) > 3.0 }, "twenty thousand draws and never a three sigma one")
    }

    @Test
    fun `the same seed always draws the same numbers`() {
        val first = List(20) { Gaussian.sample(Random(7)) }
        val second = List(20) { Gaussian.sample(Random(7)) }
        assertEquals(first, second)
    }

    @Test
    fun `the distribution matches its own cumulative distribution`() {
        val random = Random(11)
        val draws = List(20_000) { Gaussian.sample(random) }
        for (z in listOf(-2.0, -1.0, -0.5, 0.0, 0.5, 1.0, 2.0)) {
            assertEquals(Gaussian.cdf(z), draws.count { it <= z } / draws.size.toDouble(), 0.02, "at $z")
        }
    }

    @Test
    fun `the cumulative distribution has the values it should`() {
        // The approximation is good to about seven decimals, which is where these tolerances come from.
        assertEquals(0.5, Gaussian.cdf(0.0), 1e-7)
        assertEquals(0.8413447, Gaussian.cdf(1.0), 1e-6)
        assertEquals(0.1586553, Gaussian.cdf(-1.0), 1e-6)
        assertEquals(0.9772499, Gaussian.cdf(2.0), 1e-6)
        assertEquals(0.9986501, Gaussian.cdf(3.0), 1e-6)
        // Symmetric, rising, and never outside the unit interval however far out it is asked.
        for (z in -50..50) {
            val value = Gaussian.cdf(z / 10.0)
            assertEquals(1.0, value + Gaussian.cdf(-z / 10.0), 1e-7, "not symmetric at $z")
            assertTrue(value in 0.0..1.0, "outside the unit interval at $z: $value")
            assertTrue(value >= Gaussian.cdf((z - 1) / 10.0), "not rising at $z")
        }
        assertEquals(0.0, Gaussian.cdf(-40.0), 1e-12)
        assertEquals(1.0, Gaussian.cdf(40.0), 1e-12)
    }
}
