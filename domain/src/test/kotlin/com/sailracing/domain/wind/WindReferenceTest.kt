package com.sailracing.domain.wind

import kotlin.test.Test
import kotlin.test.assertEquals

class WindReferenceTest {

    @Test
    fun `the set wind is the reference until the histogram has enough samples`() {
        val settings = WindSettings(directionDegrees = 20)
        var histogram = WindHistogram()
        assertEquals(WindReference(20.0, isMeasured = false), WindReference.of(settings, histogram))
        repeat(WindReference.MIN_HISTOGRAM_SAMPLES - 1) { histogram += 25.0 }
        assertEquals(WindReference(20.0, isMeasured = false), WindReference.of(settings, histogram))
        histogram += 25.0
        val measured = WindReference.of(settings, histogram)
        assertEquals(25.0, measured.directionDegrees, 1e-9)
        assertEquals(true, measured.isMeasured)
    }
}
