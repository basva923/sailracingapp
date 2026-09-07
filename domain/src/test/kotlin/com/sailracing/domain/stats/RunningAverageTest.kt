package com.sailracing.domain.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RunningAverageTest {

    @Test
    fun `empty average has no mean`() {
        assertNull(RunningAverage().mean)
    }

    @Test
    fun `accumulates count sum and max`() {
        val average = RunningAverage() + 2.0 + 4.0 + 3.0
        assertEquals(3, average.count)
        assertEquals(9.0, average.sum)
        assertEquals(4.0, average.max)
        assertEquals(3.0, average.mean)
    }

    @Test
    fun `first value becomes the max even when negative`() {
        assertEquals(-1.0, (RunningAverage() + -1.0).max)
    }

    @Test
    fun `speed stats start empty`() {
        val stats = SpeedStats()
        assertNull(stats.upwind.mean)
        assertNull(stats.upwindVmg.mean)
        assertNull(stats.downwind.mean)
        assertNull(stats.downwindVmg.mean)
    }
}
