package com.sailracing.domain.course

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * What the wind over a racing area is believed to be like before anything is measured. The defaults decide
 * how quickly a square borrows its neighbours' wind and how uncertain an unsailed corner stays, so they are
 * pinned down here: changing one is a decision, not a typo.
 */
class WindFieldSettingsTest {

    @Test
    fun `the defaults are the ones the wind field is documented with`() {
        val settings = WindFieldSettings()
        assertEquals(3, settings.minCellSamples)
        assertEquals(150.0, settings.correlationLengthMeters)
        assertEquals(3.0, settings.neighbourhoodRadii)
        assertEquals(12.0, settings.spatialSpreadDegrees)
        assertEquals(8.0, settings.sampleSpreadDegrees)
        assertEquals(3.0, settings.minSpreadDegrees)
        assertEquals(30.0, settings.maxSpreadDegrees)
        assertEquals(0.005, settings.minWeightFraction)
    }

    @Test
    fun `nonsense settings are refused`() {
        fun message(block: () -> WindFieldSettings): String =
            assertFailsWith<IllegalArgumentException> { block() }.message.orEmpty()

        assertEquals("a square is measured by at least one sample: 0", message { WindFieldSettings(minCellSamples = 0) })
        assertTrue(message { WindFieldSettings(correlationLengthMeters = 0.0) }.startsWith("the wind hangs together"))
        assertTrue(message { WindFieldSettings(neighbourhoodRadii = 0.0) }.startsWith("a square listens"))
        assertTrue(message { WindFieldSettings(spatialSpreadDegrees = 0.0) }.startsWith("the spreads believed in"))
        assertTrue(message { WindFieldSettings(sampleSpreadDegrees = -1.0) }.startsWith("the spreads believed in"))
        assertTrue(message { WindFieldSettings(minSpreadDegrees = -1.0) }.startsWith("spreads must be a range"))
        assertTrue(message { WindFieldSettings(minSpreadDegrees = 40.0) }.startsWith("spreads must be a range"))
        assertEquals("a share of the weight is a fraction: 2.0", message { WindFieldSettings(minWeightFraction = 2.0) })
    }

    @Test
    fun `the course settings hold the three of them together`() {
        val settings = CourseSettings()
        assertEquals(GridSettings(), settings.grid)
        assertEquals(WindFieldSettings(), settings.windField)
        assertEquals(RaceLineSettings(), settings.raceLine)
    }
}
