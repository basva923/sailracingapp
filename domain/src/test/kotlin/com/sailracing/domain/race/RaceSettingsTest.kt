package com.sailracing.domain.race

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RaceSettingsTest {

    @Test
    fun `approach speeds must be positive`() {
        assertFailsWith<IllegalArgumentException> { ApproachSpeed.Manual(0.0) }
        assertFailsWith<IllegalArgumentException> { ApproachSpeed.AverageUpwindVmg(-1.0) }
        assertEquals(1.5, ApproachSpeed.AverageUpwindVmg().fallbackMps)
        assertEquals(2.0, ApproachSpeed.Manual(2.0).speedMps)
    }

    @Test
    fun `defaults`() {
        val settings = RaceSettings()
        assertEquals(ApproachSpeed.AverageUpwindVmg(), settings.approachSpeed)
        assertEquals(0, settings.compassOffsetDegrees)
        assertEquals(RaceSettings.DEFAULT_UPWIND_MAX_TWA, settings.upwindMaxTwaDegrees)
        assertEquals(60, settings.upwindMaxTwaDegrees)
        assertEquals(RaceSettings.DEFAULT_DOWNWIND_MIN_TWA, settings.downwindMinTwaDegrees)
        assertEquals(120, settings.downwindMinTwaDegrees)
        assertEquals(RaceSettings.DEFAULT_MAX_SAMPLING_TURN_RATE, settings.maxSamplingTurnRateDegreesPerSecond)
    }
}
