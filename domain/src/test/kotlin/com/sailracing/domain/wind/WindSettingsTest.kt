package com.sailracing.domain.wind

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WindSettingsTest {

    @Test
    fun `defaults are sensible`() {
        val settings = WindSettings()
        assertEquals(0, settings.directionDegrees)
        assertEquals(45, settings.tackAngleDegrees)
        assertEquals(140, settings.downwindAngleDegrees)
        assertEquals(20..80, WindSettings.TACK_ANGLE_RANGE)
        assertEquals(100..179, WindSettings.DOWNWIND_ANGLE_RANGE)
    }

    @Test
    fun `constructor validates ranges`() {
        assertFailsWith<IllegalArgumentException> { WindSettings(directionDegrees = 360) }
        assertFailsWith<IllegalArgumentException> { WindSettings(directionDegrees = -1) }
        assertFailsWith<IllegalArgumentException> { WindSettings(tackAngleDegrees = 10) }
        assertFailsWith<IllegalArgumentException> { WindSettings(downwindAngleDegrees = 90) }
    }

    @Test
    fun `with-methods normalise and clamp`() {
        assertEquals(350, WindSettings().withDirection(-10).directionDegrees)
        assertEquals(10, WindSettings().withDirection(370).directionDegrees)
        assertEquals(20, WindSettings().withTackAngle(5).tackAngleDegrees)
        assertEquals(80, WindSettings().withTackAngle(95).tackAngleDegrees)
        assertEquals(55, WindSettings().withTackAngle(55).tackAngleDegrees)
        assertEquals(100, WindSettings().withDownwindAngle(50).downwindAngleDegrees)
        assertEquals(179, WindSettings().withDownwindAngle(200).downwindAngleDegrees)
    }
}
