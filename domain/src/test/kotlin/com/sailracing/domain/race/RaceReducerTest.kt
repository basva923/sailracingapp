package com.sailracing.domain.race

import com.sailracing.domain.race.TestFixtures.east
import com.sailracing.domain.race.TestFixtures.fix
import com.sailracing.domain.race.TestFixtures.north
import com.sailracing.domain.race.TestFixtures.origin
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.stats.SpeedStats
import com.sailracing.domain.timer.Cue
import com.sailracing.domain.timer.CuePolicy
import com.sailracing.domain.timer.TimerState
import com.sailracing.domain.wind.WindHistogram
import com.sailracing.domain.wind.WindSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RaceReducerTest {

    private fun RaceState.apply(vararg events: RaceEvent): RaceState =
        events.fold(this) { state, event -> RaceReducer.reduce(state, event).state }

    // --- sensors ---------------------------------------------------------------------------------

    @Test
    fun `a fix updates navigation and samples the wind once per second`() {
        val state = RaceState().apply(RaceEvent.FixReceived(fix(1_000)), RaceEvent.FixReceived(fix(1_500)))
        assertEquals(315.0, state.navigation.headingDegrees)
        assertEquals(HeadingSource.COURSE_OVER_GROUND, state.navigation.headingSource)
        assertEquals(1, state.wind.histogram.totalSamples)
        assertEquals(1, state.wind.histogram.count(0))
        assertEquals(1, state.wind.history.samples.size)
        assertEquals(1, state.speedStats.upwind.count)
        assertEquals(3.0, state.speedStats.upwind.mean)
        assertEquals(1L, state.lastWindSampleSecond)

        val later = state.apply(RaceEvent.FixReceived(fix(2_000)))
        assertEquals(2, later.wind.histogram.totalSamples)
    }

    @Test
    fun `no sample without heading, speed or way`() {
        val noHeading = RaceState().apply(RaceEvent.FixReceived(fix(1_000, courseDegrees = null)))
        assertTrue(noHeading.wind.histogram.isEmpty)
        assertNull(noHeading.lastWindSampleSecond)

        val noSpeed = RaceState().apply(RaceEvent.CompassUpdated(315.0), RaceEvent.FixReceived(fix(1_000, speedMps = null)))
        assertEquals(315.0, noSpeed.navigation.headingDegrees)
        assertTrue(noSpeed.wind.histogram.isEmpty)

        val drifting = RaceState().apply(RaceEvent.CompassUpdated(315.0), RaceEvent.FixReceived(fix(1_000, speedMps = 0.1)))
        assertTrue(drifting.wind.histogram.isEmpty)
        assertEquals(0, drifting.speedStats.upwind.count)
    }

    @Test
    fun `reaching is sampled in history but counts for no statistics`() {
        val reaching = RaceState(settings = RaceSettings(upwindMaxTwaDegrees = 60, downwindMinTwaDegrees = 120))
            .apply(RaceEvent.FixReceived(fix(1_000, courseDegrees = 280.0)))
        assertTrue(reaching.wind.histogram.isEmpty)
        assertEquals(1, reaching.wind.history.samples.size)
        assertEquals(false, reaching.wind.history.latest?.upwind)
        assertEquals(0, reaching.speedStats.upwind.count)
        assertEquals(0, reaching.speedStats.downwind.count)

        val wide = RaceState(settings = RaceSettings(upwindMaxTwaDegrees = 90))
            .apply(RaceEvent.FixReceived(fix(1_000, courseDegrees = 280.0)))
        assertEquals(1, wide.wind.histogram.totalSamples)
        assertEquals(1, wide.speedStats.upwind.count)
    }

    @Test
    fun `no sample while tacking`() {
        val steady = RaceState().apply(RaceEvent.FixReceived(fix(1_000, courseDegrees = 315.0)))
        assertEquals(1, steady.wind.histogram.totalSamples)
        // 90 degrees in one second is a tack, not a wind shift.
        val tacking = steady.apply(RaceEvent.FixReceived(fix(2_000, courseDegrees = 45.0)))
        assertEquals(1, tacking.wind.histogram.totalSamples)
        assertEquals(45.0, tacking.navigation.headingDegrees)
        // Once settled on the new tack, sampling resumes.
        val settled = tacking.apply(RaceEvent.FixReceived(fix(3_000, courseDegrees = 46.0)))
        assertEquals(2, settled.wind.histogram.totalSamples)
        // Slow turns (90 degrees over 20 s) are fine; a fix with the same timestamp is not a turn.
        val slow = steady.apply(RaceEvent.FixReceived(fix(21_000, courseDegrees = 45.0)))
        assertEquals(2, slow.wind.histogram.totalSamples)
        val sameTime = steady.apply(RaceEvent.FixReceived(fix(1_000, courseDegrees = 45.0)))
        assertEquals(1, sameTime.wind.histogram.totalSamples)
        // Without a previous heading there is nothing to compare against.
        val fromCompass = RaceState().apply(RaceEvent.FixReceived(fix(1_000, courseDegrees = null)), RaceEvent.FixReceived(fix(2_000, courseDegrees = 45.0)))
        assertEquals(1, fromCompass.wind.histogram.totalSamples)
        val noPreviousFix = RaceState().apply(RaceEvent.CompassUpdated(300.0), RaceEvent.FixReceived(fix(2_000, courseDegrees = 45.0)))
        assertEquals(1, noPreviousFix.wind.histogram.totalSamples)
        val lostHeading = steady.apply(RaceEvent.FixReceived(fix(2_000, courseDegrees = null)))
        assertEquals(1, lostHeading.wind.histogram.totalSamples)
    }

    @Test
    fun `downwind samples feed the downwind statistics`() {
        val running = RaceState().apply(RaceEvent.FixReceived(fix(1_000, courseDegrees = 220.0, speedMps = 4.0)))
        assertTrue(running.wind.histogram.isEmpty)
        assertEquals(1, running.speedStats.downwind.count)
        assertEquals(4.0, running.speedStats.downwind.mean)
        assertEquals(0, running.speedStats.upwind.count)
        assertNotNull(running.speedStats.downwindVmg.mean)
        assertEquals(0.0, assertNotNull(running.wind.history.latest).directionDegrees, 1e-9)
    }

    @Test
    fun `compass applies the mounting offset`() {
        val state = RaceState(settings = RaceSettings(compassOffsetDegrees = 180)).apply(RaceEvent.CompassUpdated(10.0))
        assertEquals(190.0, state.navigation.compassHeadingDegrees)
        assertEquals(190.0, state.navigation.headingDegrees)
        assertEquals(HeadingSource.COMPASS, state.navigation.headingSource)
    }

    @Test
    fun `changing the offset re-applies it to the current compass reading`() {
        val state = RaceState().apply(RaceEvent.CompassUpdated(100.0))
        assertEquals(100.0, state.navigation.compassHeadingDegrees)
        val reversed = state.apply(RaceEvent.UpdateSettings(RaceSettings(compassOffsetDegrees = 180)))
        assertEquals(280.0, reversed.navigation.compassHeadingDegrees)
        val back = reversed.apply(RaceEvent.UpdateSettings(RaceSettings()))
        assertEquals(100.0, back.navigation.compassHeadingDegrees)
        val noCompass = RaceState().apply(RaceEvent.UpdateSettings(RaceSettings(compassOffsetDegrees = 90)))
        assertNull(noCompass.navigation.compassHeadingDegrees)
    }

    @Test
    fun `updating settings resizes the history`() {
        var state = RaceState()
        for (t in 1L..5L) state = state.apply(RaceEvent.FixReceived(fix(t * 1_000)))
        assertEquals(5, state.wind.history.samples.size)
        state = state.apply(RaceEvent.UpdateSettings(RaceSettings(windHistoryCapacity = 2)))
        assertEquals(2, state.wind.history.samples.size)
        assertEquals(2, state.wind.history.capacity)
    }

    // --- start line --------------------------------------------------------------------------------

    @Test
    fun `marking the ends uses the last fix and clearing removes them`() {
        val noFix = RaceState().apply(RaceEvent.MarkPinEnd, RaceEvent.MarkBoatEnd)
        assertEquals(StartLine(), noFix.startLine)

        val pinFix = fix(1_000, point = origin)
        val boatFix = fix(2_000, point = east(100.0))
        val state = RaceState().apply(
            RaceEvent.FixReceived(pinFix), RaceEvent.MarkPinEnd,
            RaceEvent.FixReceived(boatFix), RaceEvent.MarkBoatEnd,
        )
        assertEquals(StartLine(origin, east(100.0)), state.startLine)
        assertEquals(StartLine(boatEnd = east(100.0)), state.apply(RaceEvent.ClearPinEnd).startLine)
        assertEquals(StartLine(pinEnd = origin), state.apply(RaceEvent.ClearBoatEnd).startLine)

        val restored = RaceState().apply(RaceEvent.SetStartLine(StartLine(north(5.0), north(10.0))))
        assertEquals(StartLine(north(5.0), north(10.0)), restored.startLine)
    }

    // --- countdown ---------------------------------------------------------------------------------

    @Test
    fun `countdown lifecycle`() {
        val started = RaceState().apply(RaceEvent.StartCountdown(5, nowMillis = 1_000))
        assertEquals(TimerState.Running(301_000), started.timer)

        val synced = started.apply(RaceEvent.SyncCountdown(nowMillis = 8_000))
        assertEquals(TimerState.Running(308_000), synced.timer)

        val stopped = synced.apply(RaceEvent.StopTimer)
        assertEquals(TimerState.Idle, stopped.timer)

        val restored = stopped.apply(RaceEvent.SetTimer(TimerState.Running(99_000)))
        assertEquals(TimerState.Running(99_000), restored.timer)
    }

    @Test
    fun `ticks emit each cue exactly once`() {
        var state = RaceState().apply(RaceEvent.StartCountdown(5, nowMillis = 0))
        val cues = mutableListOf<Cue>()
        var now = 0L
        while (now <= 301_000) {
            val transition = RaceReducer.reduce(state, RaceEvent.Tick(now))
            state = transition.state
            cues += transition.effects.map { (it as RaceEffect.PlayCue).cue }
            now += 250
        }
        val expected = listOf(Cue.MINUTE, Cue.MINUTE, Cue.MINUTE, Cue.MINUTE) +
            listOf(Cue.TEN_SECONDS, Cue.TEN_SECONDS, Cue.TEN_SECONDS, Cue.TEN_SECONDS, Cue.TEN_SECONDS) +
            listOf(Cue.MINUTE) +
            listOf(Cue.TEN_SECONDS, Cue.TEN_SECONDS, Cue.TEN_SECONDS, Cue.TEN_SECONDS, Cue.TEN_SECONDS) +
            List(9) { Cue.SECOND } +
            listOf(Cue.START)
        assertEquals(expected, cues)
        assertEquals(-1L, state.lastCuedSecond)
    }

    @Test
    fun `ticks are silent while idle or when nothing changed`() {
        val idle = RaceReducer.reduce(RaceState(), RaceEvent.Tick(5_000))
        assertTrue(idle.effects.isEmpty())
        assertEquals(RaceState(), idle.state)

        val running = RaceState().apply(RaceEvent.StartCountdown(1, nowMillis = 0))
        val first = RaceReducer.reduce(running, RaceEvent.Tick(0))
        assertEquals(listOf(RaceEffect.PlayCue(Cue.MINUTE)), first.effects)
        val again = RaceReducer.reduce(first.state, RaceEvent.Tick(100))
        assertTrue(again.effects.isEmpty())
        val quiet = RaceReducer.reduce(again.state, RaceEvent.Tick(1_000))
        assertTrue(quiet.effects.isEmpty())
        assertEquals(59L, quiet.state.lastCuedSecond)
    }

    @Test
    fun `cues respect the policy`() {
        val silent = RaceState(settings = RaceSettings(cuePolicy = CuePolicy(enabled = false)))
            .apply(RaceEvent.StartCountdown(1, nowMillis = 0))
        assertTrue(RaceReducer.reduce(silent, RaceEvent.Tick(0)).effects.isEmpty())
    }

    // --- wind --------------------------------------------------------------------------------------

    @Test
    fun `wind settings can be set directly`() {
        val state = RaceState().apply(
            RaceEvent.SetWindDirection(370),
            RaceEvent.SetTackAngle(40),
            RaceEvent.SetDownwindAngle(150),
        )
        assertEquals(WindSettings(10, 40, 150), state.wind.settings)
        val replaced = state.apply(RaceEvent.SetWindSettings(WindSettings(200, 50, 160)))
        assertEquals(WindSettings(200, 50, 160), replaced.wind.settings)
    }

    @Test
    fun `wind from the current tack needs a heading`() {
        assertEquals(WindSettings(), RaceState().apply(RaceEvent.SetWindFromPortTack).wind.settings)
        assertEquals(WindSettings(), RaceState().apply(RaceEvent.SetWindFromStarboardTack).wind.settings)

        val heading = RaceState().apply(RaceEvent.CompassUpdated(100.0))
        assertEquals(55, heading.apply(RaceEvent.SetWindFromPortTack).wind.settings.directionDegrees)
        assertEquals(145, heading.apply(RaceEvent.SetWindFromStarboardTack).wind.settings.directionDegrees)
    }

    @Test
    fun `statistics can be reset independently`() {
        val state = RaceState(settings = RaceSettings(windHistoryCapacity = 7))
            .apply(RaceEvent.SetWindDirection(90), RaceEvent.FixReceived(fix(1_000, courseDegrees = 45.0)))
        assertEquals(1, state.wind.histogram.totalSamples)
        assertEquals(1, state.speedStats.upwind.count)

        val windReset = state.apply(RaceEvent.ResetWindStatistics)
        assertEquals(WindHistogram(), windReset.wind.histogram)
        assertTrue(windReset.wind.history.samples.isEmpty())
        assertEquals(7, windReset.wind.history.capacity)
        assertEquals(90, windReset.wind.settings.directionDegrees)
        assertEquals(1, windReset.speedStats.upwind.count)

        val speedReset = state.apply(RaceEvent.ResetSpeedStatistics)
        assertEquals(SpeedStats(), speedReset.speedStats)
        assertEquals(1, speedReset.wind.histogram.totalSamples)
    }
}
