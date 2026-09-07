package com.sailracing.app.sensors

import org.junit.Test
import kotlin.test.assertEquals

class DeviceHeadingTest {

    private fun heading(vararg rows: Float): Double = DeviceHeading.fromRotationMatrix(rows)

    @Test
    fun uprightPhoneReportsWhereItsBackFaces() {
        // Rotated 90 degrees about the east axis from flat: the screen faces south, the back north.
        assertEquals(0.0, heading(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f), 1e-6)
        // Same, turned to face east with its back.
        assertEquals(90.0, heading(0f, 0f, -1f, -1f, 0f, 0f, 0f, 1f, 0f), 1e-6)
        // Upright and turned to landscape: the back still faces north.
        assertEquals(0.0, heading(0f, -1f, 0f, 0f, 0f, -1f, 1f, 0f, 0f), 1e-6)
        // Leaning back by 60 degrees is still upright enough: the back faces south.
        assertEquals(180.0, heading(0f, 1f, 0f, -0.866f, 0f, 0.5f, 0.5f, 0f, 0.866f), 1e-6)
    }

    @Test
    fun flatPhoneFallsBackToItsTopEdge() {
        // Identity: flat, top pointing north.
        assertEquals(0.0, heading(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), 1e-6)
        // Flat, top pointing east.
        assertEquals(90.0, heading(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f), 1e-6)
        // Propped up by only a few degrees with the top pointing east: still the top edge, not the back.
        assertEquals(90.0, heading(0f, 1f, 0f, -0.995f, 0f, 0.1f, 0.1f, 0f, 0.995f), 1e-6)
        assertEquals(0.3f, DeviceHeading.FLAT_THRESHOLD)
    }
}
