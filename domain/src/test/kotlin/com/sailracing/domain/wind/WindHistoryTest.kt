package com.sailracing.domain.wind

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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

    private fun beating(t: Long, heading: Double, tack: Tack, upwind: Boolean = true, downwind: Boolean = false) =
        WindSample(t * 1_000L, if (tack == Tack.STARBOARD) heading + 45 else heading - 45, upwind, headingDegrees = heading, tack = tack, downwind = downwind)

    @Test
    fun `the samples since a moment are the tail`() {
        var history = WindHistory()
        for (t in 1L..5L) history += sample(t)
        assertEquals(listOf(sample(3), sample(4), sample(5)), history.since(3L))
        assertEquals(history.samples, history.since(0L))
        assertEquals(emptyList(), history.since(6L))
    }

    @Test
    fun `the close-hauled heading on a tack is the mean of its close-hauled samples`() {
        var history = WindHistory()
        for (t in 1L..30L) history += beating(t, if (t % 2 == 0L) 310.0 else 320.0, Tack.STARBOARD)
        for (t in 31L..40L) history += beating(t, 45.0, Tack.PORT)
        history += beating(41L, 280.0, Tack.STARBOARD, upwind = false)
        assertEquals(315.0, assertNotNull(history.closeHauledHeadingDegrees(Tack.STARBOARD, 0L, 30)), 1e-9)
        assertNull(history.closeHauledHeadingDegrees(Tack.STARBOARD, 2_000L, 30))
        assertNull(history.closeHauledHeadingDegrees(Tack.PORT, 0L, 30))
        assertEquals(45.0, assertNotNull(history.closeHauledHeadingDegrees(Tack.PORT, 0L, 10)), 1e-9)
        // A sample without a heading says nothing about one.
        assertNull((WindHistory() + WindSample(0L, 0.0, upwind = true)).closeHauledHeadingDegrees(Tack.STARBOARD, 0L, 1))
    }

    @Test
    fun `the median close-hauled direction`() {
        var history = WindHistory()
        for (t in 1L..9L) history += beating(t, 300.0 + t, Tack.STARBOARD)
        // Nine estimates from 346 to 354: the median is the fifth, 350.
        assertEquals(350.0, assertNotNull(history.medianCloseHauledDirectionDegrees(0L, 9)), 1e-9)
        assertNull(history.medianCloseHauledDirectionDegrees(0L, 10))
        assertNull(history.medianCloseHauledDirectionDegrees(10_000L, 1))
        // Eight of them: the two middle ones averaged.
        assertEquals(350.5, assertNotNull(history.medianCloseHauledDirectionDegrees(2_000L, 1)), 1e-9)
        // A reach does not count, however far off it is.
        history += beating(10L, 270.0, Tack.STARBOARD, upwind = false)
        assertEquals(350.0, assertNotNull(history.medianCloseHauledDirectionDegrees(0L, 1)), 1e-9)
    }

    @Test
    fun `the steady direction is the last few on-angle samples on the tack`() {
        var history = WindHistory()
        for (t in 1L..20L) history += beating(t, 315.0, Tack.STARBOARD)
        history += beating(21L, 325.0, Tack.STARBOARD)
        history += beating(22L, 300.0, Tack.STARBOARD, upwind = false)
        history += beating(23L, 45.0, Tack.PORT)
        // Ten samples: nine at 315 (wind 0) and one at 325 (wind 10), the reach in between not counting.
        assertEquals(1.0, assertNotNull(history.steadyDirectionDegrees(Tack.STARBOARD, downwind = false, count = 10, sinceMillis = 0L)), 0.01)
        assertEquals(10.0, assertNotNull(history.steadyDirectionDegrees(Tack.STARBOARD, downwind = false, count = 10, sinceMillis = 21_000L)), 1e-9)
        assertNull(history.steadyDirectionDegrees(Tack.STARBOARD, downwind = false, count = 10, sinceMillis = 22_000L))
        assertEquals(0.0, assertNotNull(history.steadyDirectionDegrees(Tack.PORT, downwind = false, count = 10, sinceMillis = 0L)), 1e-9)
        // Downwind samples are their own kind.
        assertNull(history.steadyDirectionDegrees(Tack.PORT, downwind = true, count = 10, sinceMillis = 0L))
        history += WindSample(24_000L, 5.0, upwind = false, headingDegrees = 145.0, tack = Tack.PORT, downwind = true)
        assertEquals(5.0, assertNotNull(history.steadyDirectionDegrees(Tack.PORT, downwind = true, count = 10, sinceMillis = 0L)), 1e-9)
    }

    @Test
    fun `the steady heading ignores the other tack and anything too old`() {
        var history = WindHistory()
        for (t in 1L..5L) history += beating(t, 45.0, Tack.PORT)
        for (t in 6L..15L) history += beating(t, if (t % 2 == 0L) 310.0 else 320.0, Tack.STARBOARD)
        assertEquals(315.0, assertNotNull(history.steadyHeadingDegrees(318.0, count = 10, sinceMillis = 0L, maxSpreadDegrees = 45.0)), 1e-9)
        assertEquals(320.0, assertNotNull(history.steadyHeadingDegrees(318.0, count = 1, sinceMillis = 0L, maxSpreadDegrees = 45.0)), 1e-9)
        assertEquals(45.0, assertNotNull(history.steadyHeadingDegrees(50.0, count = 10, sinceMillis = 0L, maxSpreadDegrees = 45.0)), 1e-9)
        assertNull(history.steadyHeadingDegrees(50.0, count = 10, sinceMillis = 6_000L, maxSpreadDegrees = 45.0))
        assertNull(history.steadyHeadingDegrees(180.0, count = 10, sinceMillis = 0L, maxSpreadDegrees = 45.0))
        assertNull((WindHistory() + sample(1)).steadyHeadingDegrees(0.0, count = 10, sinceMillis = 0L, maxSpreadDegrees = 45.0))
    }
}
