package com.sailracing.domain.course

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * How the wind over a racing area is worked out from what has been measured of it. The defaults decide how
 * coarsely the area is cut and how much of a drawn wind is the moment, the day and chance, so they are
 * pinned down here: changing one is a decision, not a typo.
 */
class WindFieldSettingsTest {

    @Test
    fun `the defaults are the ones the wind field is documented with`() {
        val settings = WindFieldSettings()
        assertEquals(3, settings.columns)
        assertEquals(0.35, settings.currentWindFraction)
        assertEquals(0.05, settings.randomWindFraction)
        assertEquals(3, settings.minBlockSamples)
        assertEquals(0.6, settings.speedSpreadFactor)
        assertEquals(0.5, settings.minBoatSpeedMps)
    }

    @Test
    fun `what the moment and chance leave over is what the histograms are worth`() {
        assertEquals(0.6, WindFieldSettings().measuredFraction, 1e-9)
        assertEquals(1.0, WindFieldSettings(currentWindFraction = 0.0, randomWindFraction = 0.0).measuredFraction, 1e-9)
        assertEquals(0.0, WindFieldSettings(currentWindFraction = 0.5, randomWindFraction = 0.5).measuredFraction, 1e-9)
    }

    @Test
    fun `nonsense settings are refused`() {
        fun message(block: () -> WindFieldSettings): String =
            assertFailsWith<IllegalArgumentException> { block() }.message.orEmpty()

        assertEquals("an area is at least one big square across: 0", message { WindFieldSettings(columns = 0) })
        assertTrue(message { WindFieldSettings(currentWindFraction = 1.5) }.startsWith("the wind of the moment's share"))
        assertTrue(message { WindFieldSettings(randomWindFraction = -0.1) }.startsWith("chance's share"))
        assertTrue(
            message { WindFieldSettings(currentWindFraction = 0.8, randomWindFraction = 0.3) }
                .startsWith("the wind of the moment and chance cannot be worth more"),
        )
        assertEquals("a big square is measured by at least one sample: 0", message { WindFieldSettings(minBlockSamples = 0) })
        assertEquals("a share of the speed spread is not negative: -1.0", message { WindFieldSettings(speedSpreadFactor = -1.0) })
        assertEquals("the slowest a boat may go must be positive: 0.0", message { WindFieldSettings(minBoatSpeedMps = 0.0) })
    }

    @Test
    fun `the course settings hold the three of them together`() {
        val settings = CourseSettings()
        assertEquals(GridSettings(), settings.grid)
        assertEquals(WindFieldSettings(), settings.windField)
        assertEquals(RaceLineSettings(), settings.raceLine)
    }
}
