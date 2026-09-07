package com.sailracing.domain.race

import com.sailracing.domain.timer.Cue
import com.sailracing.domain.timer.RacePhase
import kotlin.test.Test
import kotlin.test.assertEquals

class RaceEngineTest {

    @Test
    fun `engine applies events and returns effects`() {
        val engine = RaceEngine()
        assertEquals(RaceState(), engine.state)
        assertEquals(emptyList(), engine.dispatch(RaceEvent.StartCountdown(1, nowMillis = 0)))
        assertEquals(listOf(RaceEffect.PlayCue(Cue.MINUTE)), engine.dispatch(RaceEvent.Tick(0)))
        assertEquals(RacePhase.COUNTDOWN, engine.snapshot(30_000).phase)
        assertEquals(RacePhase.RACING, engine.snapshot(60_000).phase)
    }
}
