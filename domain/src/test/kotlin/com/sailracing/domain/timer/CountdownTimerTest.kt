package com.sailracing.domain.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CountdownTimerTest {

    @Test
    fun `start schedules the gun minutes ahead`() {
        val running = CountdownTimer.start(nowMillis = 1_000, minutes = 5)
        assertEquals(301_000, running.startAtMillis)
        assertEquals(300_000, CountdownTimer.remainingMillis(running, 1_000))
        assertEquals(-4_000, CountdownTimer.remainingMillis(running, 305_000))
        assertFailsWith<IllegalArgumentException> { CountdownTimer.start(0, 0) }
    }

    @Test
    fun `remaining is null while idle`() {
        assertNull(CountdownTimer.remainingMillis(TimerState.Idle, 42))
    }

    @Test
    fun `sync rounds the remaining time to the nearest minute`() {
        val running = TimerState.Running(startAtMillis = 300_000)
        // 4:53 left -> 5:00
        assertEquals(TimerState.Running(307_000), CountdownTimer.syncToNearestMinute(running, 7_000))
        // 4:20 left -> 4:00
        assertEquals(TimerState.Running(280_000), CountdownTimer.syncToNearestMinute(running, 40_000))
        // 0:29.999 left -> start now
        val synced = CountdownTimer.syncToNearestMinute(running, 270_001)
        assertEquals(TimerState.Running(270_001), synced)
        assertEquals(RacePhase.RACING, CountdownTimer.phase(synced, 270_001))
    }

    @Test
    fun `sync leaves idle and started timers alone`() {
        assertEquals(TimerState.Idle, CountdownTimer.syncToNearestMinute(TimerState.Idle, 5))
        val started = TimerState.Running(100)
        assertEquals(started, CountdownTimer.syncToNearestMinute(started, 100))
        assertEquals(started, CountdownTimer.syncToNearestMinute(started, 5_000))
    }

    @Test
    fun `phase follows the timer`() {
        assertEquals(RacePhase.SETUP, CountdownTimer.phase(TimerState.Idle, 0))
        val running = TimerState.Running(10_000)
        assertEquals(RacePhase.COUNTDOWN, CountdownTimer.phase(running, 9_999))
        assertEquals(RacePhase.RACING, CountdownTimer.phase(running, 10_000))
        assertEquals(RacePhase.RACING, CountdownTimer.phase(running, 20_000))
    }

    @Test
    fun `whole seconds round up before the start and down after`() {
        assertEquals(0, CountdownTimer.remainingWholeSeconds(0))
        assertEquals(1, CountdownTimer.remainingWholeSeconds(1))
        assertEquals(1, CountdownTimer.remainingWholeSeconds(999))
        assertEquals(1, CountdownTimer.remainingWholeSeconds(1_000))
        assertEquals(2, CountdownTimer.remainingWholeSeconds(1_001))
        assertEquals(300, CountdownTimer.remainingWholeSeconds(300_000))
        assertEquals(0, CountdownTimer.remainingWholeSeconds(-1))
        assertEquals(0, CountdownTimer.remainingWholeSeconds(-999))
        assertEquals(-1, CountdownTimer.remainingWholeSeconds(-1_000))
        assertEquals(-1, CountdownTimer.remainingWholeSeconds(-1_500))
        assertEquals(-2, CountdownTimer.remainingWholeSeconds(-2_000))
    }
}
