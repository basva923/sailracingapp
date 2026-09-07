package com.sailracing.domain.wind

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WindHistoryTest {

    private fun sample(t: Long) = WindSample(t, t.toDouble(), upwind = true)

    @Test
    fun `capacity must be positive`() {
        assertFailsWith<IllegalArgumentException> { WindHistory(capacity = 0) }
    }

    @Test
    fun `keeps the newest samples`() {
        var history = WindHistory(capacity = 3)
        assertNull(history.latest)
        for (t in 1L..5L) history += sample(t)
        assertEquals(listOf(sample(3), sample(4), sample(5)), history.samples)
        assertEquals(sample(5), history.latest)
        assertEquals(listOf(sample(4), sample(5)), history.last(2))
        assertEquals(listOf(sample(3), sample(4), sample(5)), history.last(10))
    }

    @Test
    fun `capacity can shrink and grow`() {
        var history = WindHistory(capacity = 5)
        for (t in 1L..5L) history += sample(t)
        val smaller = history.withCapacity(2)
        assertEquals(listOf(sample(4), sample(5)), smaller.samples)
        assertEquals(2, smaller.capacity)
        val larger = smaller.withCapacity(10)
        assertEquals(2, larger.samples.size)
        assertEquals(10, larger.capacity)
    }
}
