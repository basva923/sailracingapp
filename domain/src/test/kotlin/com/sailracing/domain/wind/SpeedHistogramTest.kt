package com.sailracing.domain.wind

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeedHistogramTest {

    @Test
    fun `an empty histogram knows nothing`() {
        val empty = SpeedHistogram()
        assertTrue(empty.isEmpty)
        assertEquals(0.0, empty.totalWeight)
        assertNull(empty.meanMps)
        assertNull(empty.spreadMps)
        assertEquals("SpeedHistogram(samples=0.0, mean=null)", empty.toString())
        val failure = assertFailsWith<IllegalArgumentException> { empty.quantile(0.5) }
        assertEquals("an empty speed histogram has no quantiles", failure.message)
    }

    @Test
    fun `samples land in their bin and the mean is the middle of the bins`() {
        val histogram = SpeedHistogram.of(3.0, 3.1, 3.2, 4.0)
        assertFalse(histogram.isEmpty)
        assertEquals(4.0, histogram.totalWeight)
        // Three samples in the bin from 3.00 to 3.25 and one in the bin from 4.00 to 4.25.
        assertEquals(3.0, histogram.weightAt(3.2))
        assertEquals(1.0, histogram.weightAt(4.1))
        assertEquals(0.0, histogram.weightAt(3.5))
        assertEquals((3 * 3.125 + 4.125) / 4, assertNotNull(histogram.meanMps), 1e-9)
    }

    @Test
    fun `speeds outside the bins are kept in the ones at the ends`() {
        assertEquals(0, SpeedHistogram.binOf(0.0))
        assertEquals(0, SpeedHistogram.binOf(-3.0))
        assertEquals(12, SpeedHistogram.binOf(3.0))
        assertEquals(SpeedHistogram.BIN_COUNT - 1, SpeedHistogram.binOf(12.0))
        assertEquals(SpeedHistogram.BIN_COUNT - 1, SpeedHistogram.binOf(100.0))
        assertEquals(1.0, SpeedHistogram.of(100.0).weightAt(11.9))
    }

    @Test
    fun `the quantile walks the distribution and interpolates inside a bin`() {
        // Four samples in four different bins: 1.0, 2.0, 3.0, 4.0 m/s.
        val histogram = SpeedHistogram.of(1.0, 2.0, 3.0, 4.0)
        assertEquals(1.0, histogram.quantile(0.0), 1e-9, "the bottom of the slowest bin")
        assertEquals(1.125, histogram.quantile(0.125), 1e-9, "half way through the first bin")
        assertEquals(1.25, histogram.quantile(0.25), 1e-9, "the whole of the first bin is the slowest quarter")
        assertEquals(2.125, histogram.quantile(0.375), 1e-9, "half way through the second bin")
        assertEquals(2.25, histogram.quantile(0.5), 1e-9)
        assertEquals(4.25, histogram.quantile(1.0), 1e-9, "the top of the last bin")
        // Out of range fractions are held at the ends rather than throwing.
        assertEquals(histogram.quantile(0.0), histogram.quantile(-1.0), 1e-9)
        assertEquals(histogram.quantile(1.0), histogram.quantile(2.0), 1e-9)
    }

    @Test
    fun `the fastest a blended histogram can be is the top of its last bin`() {
        // Weights blended from other squares do not add up to their own sum exactly: 0.1 + 0.2 is a hair
        // over 0.3, so walking the whole distribution can leave a crumb behind at the top of it.
        val weights = DoubleArray(SpeedHistogram.BIN_COUNT)
        weights[SpeedHistogram.binOf(1.0)] = 0.1
        weights[SpeedHistogram.binOf(2.0)] = 0.2
        val blended = SpeedHistogram.fromWeights(weights)
        assertEquals(2.25, blended.quantile(1.0), 1e-9, "the top of the fastest bin with anything in it")
        assertTrue(blended.quantile(0.999) <= 2.25)
    }

    @Test
    fun `drawing at uniform fractions reproduces the histogram it was drawn from`() {
        // A puffy corner: two thirds of the time 2 m/s, one third 4 m/s. Drawn back out, the two speeds
        // have to come out in that proportion - the shape of the distribution, not just its mean of 2.7.
        val puffy = (1..60).fold(SpeedHistogram()) { histogram, i -> histogram + if (i % 3 == 0) 4.0 else 2.0 }
        val random = Random(3)
        val drawn = List(6_000) { puffy.quantile(random.nextDouble()) }
        assertEquals(2.0 / 3.0, drawn.count { it < 3.0 } / 6_000.0, 0.02)
        assertEquals(assertNotNull(puffy.meanMps), drawn.average(), 0.05)
    }

    @Test
    fun `the spread is the standard deviation of the samples`() {
        assertEquals(0.0, assertNotNull(SpeedHistogram.of(3.0, 3.0, 3.0).spreadMps), 1e-9)
        // 2 and 4 m/s, half of the time each: a mean of 3 and a deviation of 1.
        val split = SpeedHistogram.of(2.0, 4.0, 2.0, 4.0)
        assertEquals(3.125, assertNotNull(split.meanMps), 1e-9)
        assertEquals(1.0, assertNotNull(split.spreadMps), 1e-9)
    }

    @Test
    fun `histograms add up`() {
        val here = SpeedHistogram.of(2.0, 2.0)
        val there = SpeedHistogram.of(4.0, 4.0)
        val both = here + there
        assertEquals(4.0, both.totalWeight)
        assertEquals(2.0, both.weightAt(2.0))
        assertEquals(3.125, assertNotNull(both.meanMps), 1e-9)
    }

    @Test
    fun `bins can be handed over raw, and only in the right number`() {
        val weights = DoubleArray(SpeedHistogram.BIN_COUNT)
        weights[SpeedHistogram.binOf(3.0)] = 2.0
        val histogram = SpeedHistogram.fromWeights(weights)
        assertEquals(SpeedHistogram.of(3.0, 3.0), histogram)
        // The array is copied in: writing to it afterwards does not change the histogram.
        weights[0] = 5.0
        assertEquals(SpeedHistogram.of(3.0, 3.0), histogram)
        val failure = assertFailsWith<IllegalArgumentException> { SpeedHistogram.fromWeights(DoubleArray(3)) }
        assertEquals("expected ${SpeedHistogram.BIN_COUNT} bins, got 3", failure.message)
    }

    @Test
    fun `equality`() {
        assertEquals(SpeedHistogram.of(3.0), SpeedHistogram.of(3.0))
        assertEquals(SpeedHistogram.of(3.0).hashCode(), SpeedHistogram.of(3.0).hashCode())
        assertNotEquals(SpeedHistogram.of(3.0), SpeedHistogram.of(4.0))
        assertFalse(SpeedHistogram().equals("histogram"))
    }
}
