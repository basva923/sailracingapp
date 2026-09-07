package com.sailracing.domain.wind

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindHistogramTest {

    @Test
    fun `empty histogram`() {
        val empty = WindHistogram()
        assertTrue(empty.isEmpty)
        assertEquals(0, empty.totalSamples)
        assertNull(empty.mode())
        assertNull(empty.meanDirection())
        assertNull(empty.standardDeviation())
        assertEquals(360, empty.toCounts().size)
    }

    @Test
    fun `samples land in one-degree bins with wrap-around`() {
        val histogram = WindHistogram() + 359.6 + 0.2 + 1.0 + 1.0 + 361.0
        assertFalse(histogram.isEmpty)
        assertEquals(5, histogram.totalSamples)
        assertEquals(2, histogram.count(0))
        assertEquals(2, histogram.count(360))
        assertEquals(3, histogram.count(1))
        assertEquals(0, histogram.count(2))
        assertEquals(1, histogram.mode())
        assertEquals(
            listOf(HistogramBin(-2, 0), HistogramBin(-1, 0), HistogramBin(0, 2), HistogramBin(1, 3), HistogramBin(2, 0)),
            histogram.window(0, 2),
        )
    }

    @Test
    fun `mode ties resolve to the lowest degree`() {
        assertEquals(10, (WindHistogram() + 10.0 + 20.0).mode())
    }

    @Test
    fun `circular mean and deviation`() {
        val straddling = WindHistogram() + 350.0 + 10.0
        assertEquals(0.0, assertNotNull(straddling.meanDirection()), 1e-9)
        assertEquals(10.0, assertNotNull(straddling.standardDeviation()), 0.1)

        val steady = WindHistogram() + 90.0 + 90.0 + 90.0
        assertEquals(90.0, assertNotNull(steady.meanDirection()), 1e-9)
        assertEquals(0.0, assertNotNull(steady.standardDeviation()), 1e-6)

        val opposite = WindHistogram() + 0.0 + 180.0
        assertNull(opposite.standardDeviation())
    }

    @Test
    fun `round trips through counts`() {
        val histogram = WindHistogram() + 5.0 + 6.0
        val copy = WindHistogram.fromCounts(histogram.toCounts())
        assertEquals(histogram, copy)
        assertEquals(histogram.hashCode(), copy.hashCode())
        assertNotEquals(histogram, WindHistogram())
        assertNotEquals<Any>(histogram, "not a histogram")
        assertEquals("WindHistogram(samples=2, mode=5)", histogram.toString())
        assertFailsWith<IllegalArgumentException> { WindHistogram.fromCounts(listOf(1, 2, 3)) }
    }

    @Test
    fun `adding does not mutate the original`() {
        val original = WindHistogram()
        original + 1.0
        assertTrue(original.isEmpty)
    }
}
