package com.sailracing.app.time

import com.sailracing.app.fakes.ManualClock
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClockTest {

    @Test
    fun systemClockTracksWallTime() {
        val before = System.currentTimeMillis()
        val now = SystemClock.nowMillis()
        assertTrue(now >= before)
        assertTrue(now <= System.currentTimeMillis())
    }

    @Test
    fun scaledClockRunsFaster() {
        val base = ManualClock(1_000)
        val scaled = ScaledClock(base, speedFactor = 10.0)
        assertEquals(1_000, scaled.nowMillis())
        base.now = 1_100
        assertEquals(2_000, scaled.nowMillis())
        assertFailsWith<IllegalArgumentException> { ScaledClock(base, 0.0) }
    }
}
