package com.sailracing.domain.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CueScheduleTest {

    private val policy = CuePolicy()

    @Test
    fun `default policy follows the design`() {
        assertEquals(Cue.MINUTE, CueSchedule.cueAt(300, policy))
        assertNull(CueSchedule.cueAt(299, policy))
        assertEquals(Cue.MINUTE, CueSchedule.cueAt(240, policy))
        assertNull(CueSchedule.cueAt(130, policy)) // outside the ten-second window
        assertEquals(Cue.MINUTE, CueSchedule.cueAt(120, policy))
        assertEquals(Cue.TEN_SECONDS, CueSchedule.cueAt(110, policy))
        assertNull(CueSchedule.cueAt(105, policy))
        assertEquals(Cue.MINUTE, CueSchedule.cueAt(60, policy))
        assertEquals(Cue.TEN_SECONDS, CueSchedule.cueAt(50, policy))
        assertNull(CueSchedule.cueAt(11, policy))
        assertEquals(Cue.TEN_SECONDS, CueSchedule.cueAt(10, policy))
        for (second in 9L downTo 1L) assertEquals(Cue.SECOND, CueSchedule.cueAt(second, policy), "second $second")
        assertEquals(Cue.START, CueSchedule.cueAt(0, policy))
        assertNull(CueSchedule.cueAt(-1, policy))
    }

    @Test
    fun `disabled policy is silent`() {
        val silent = CuePolicy(enabled = false)
        assertNull(CueSchedule.cueAt(0, silent))
        assertNull(CueSchedule.cueAt(60, silent))
    }

    @Test
    fun `windows are configurable`() {
        val custom = CuePolicy(tenSecondWindowSeconds = 60, secondWindowSeconds = 5)
        assertNull(CueSchedule.cueAt(110, custom))
        assertEquals(Cue.TEN_SECONDS, CueSchedule.cueAt(50, custom))
        assertNull(CueSchedule.cueAt(6, custom))
        assertEquals(Cue.SECOND, CueSchedule.cueAt(5, custom))
    }
}
