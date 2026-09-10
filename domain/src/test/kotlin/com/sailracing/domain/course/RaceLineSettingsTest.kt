package com.sailracing.domain.course

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The dials of the Monte Carlo. The defaults are a belief about a dinghy course in a shifty breeze, so they
 * are pinned down here: changing one is a decision, not a typo.
 */
class RaceLineSettingsTest {

    @Test
    fun `the defaults are the ones the race line is documented with`() {
        val settings = RaceLineSettings()
        assertEquals(1000, settings.runs)
        assertEquals(16, settings.searchRuns)
        assertEquals(0.2, settings.riskFraction)
        assertEquals(5.0, settings.recomputeShiftDegrees)
        assertEquals(16, settings.searched)
    }

    @Test
    fun `there is no searching a wind that was never drawn`() {
        assertEquals(4, RaceLineSettings(runs = 4, searchRuns = 32).searched)
        assertEquals(1, RaceLineSettings(runs = 1).searched)
    }

    @Test
    fun `nonsense settings are refused`() {
        fun message(block: () -> RaceLineSettings): String =
            assertFailsWith<IllegalArgumentException> { block() }.message.orEmpty()

        assertEquals("a Monte Carlo needs at least one run: 0", message { RaceLineSettings(runs = 0) })
        assertEquals("a plan needs at least one searched beat: 0", message { RaceLineSettings(searchRuns = 0) })
        assertEquals("the risk fraction is one tail: 0.5", message { RaceLineSettings(riskFraction = 0.5) })
        assertEquals("the risk fraction is one tail: 0.0", message { RaceLineSettings(riskFraction = 0.0) })
        assertEquals("a wind cannot move less than nothing: -1.0", message { RaceLineSettings(recomputeShiftDegrees = -1.0) })
    }
}
