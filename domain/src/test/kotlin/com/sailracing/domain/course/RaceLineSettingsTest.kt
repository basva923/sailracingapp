package com.sailracing.domain.course

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The dials of the Monte Carlo. The defaults are a belief about a dinghy course in a shifty breeze, so they
 * are pinned down here: changing one is a decision, not a typo.
 */
class RaceLineSettingsTest {

    @Test
    fun `the defaults are the ones the race line is documented with`() {
        val settings = RaceLineSettings()
        assertEquals(16, settings.runs)
        assertEquals(0.8, settings.currentWindWeight)
        assertEquals(200.0, settings.currentWindRangeMeters)
        assertEquals(0.6, settings.speedSpreadFactor)
        assertEquals(0.5, settings.minBoatSpeedMps)
        assertEquals(0.2, settings.riskFraction)
        assertEquals(5.0, settings.recomputeShiftDegrees)
    }

    @Test
    fun `nonsense settings are refused`() {
        fun message(block: () -> RaceLineSettings): String =
            assertFailsWith<IllegalArgumentException> { block() }.message.orEmpty()

        assertEquals("a Monte Carlo needs at least one run: 0", message { RaceLineSettings(runs = 0) })
        assertEquals("the current wind's weight is a fraction: 1.5", message { RaceLineSettings(currentWindWeight = 1.5) })
        assertTrue(message { RaceLineSettings(currentWindRangeMeters = 0.0) }.startsWith("ranges must be positive"))
        assertEquals("the slowest a boat may go must be positive: 0.0", message { RaceLineSettings(minBoatSpeedMps = 0.0) })
        assertEquals("the risk fraction is one tail: 0.5", message { RaceLineSettings(riskFraction = 0.5) })
        assertEquals("the risk fraction is one tail: 0.0", message { RaceLineSettings(riskFraction = 0.0) })
        assertEquals("a wind cannot move less than nothing: -1.0", message { RaceLineSettings(recomputeShiftDegrees = -1.0) })
    }
}
