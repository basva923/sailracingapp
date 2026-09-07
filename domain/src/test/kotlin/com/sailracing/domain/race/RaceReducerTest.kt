package com.sailracing.domain.race

import com.sailracing.domain.course.Track
import com.sailracing.domain.geo.Geo
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
import com.sailracing.domain.wind.WindHistory
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
        assertEquals(1L, state.lastSampleSecond)

        val later = state.apply(RaceEvent.FixReceived(fix(2_000)))
        assertEquals(2, later.wind.histogram.totalSamples)
    }

    @Test
    fun `no sample without heading, speed or way`() {
        val noHeading = RaceState().apply(RaceEvent.FixReceived(fix(1_000, courseDegrees = null)))
        assertTrue(noHeading.wind.histogram.isEmpty)
        // The position is still worth remembering for the map.
        assertEquals(1L, noHeading.lastSampleSecond)
        assertEquals(1, noHeading.track.points.size)
        assertNull(noHeading.track.latest?.headingDegrees)
        assertNull(noHeading.track.latest?.upwindWindDegrees)

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
    fun `the track records every fix second, with the wind where it was sampled close-hauled`() {
        val state = RaceState().apply(
            RaceEvent.FixReceived(fix(1_000)),
            RaceEvent.FixReceived(fix(1_500)),
            RaceEvent.FixReceived(fix(2_000, courseDegrees = 280.0)),
            RaceEvent.FixReceived(fix(3_000, speedMps = 0.1)),
            RaceEvent.FixReceived(fix(4_000, courseDegrees = 220.0)),
        )
        val points = state.track.points
        assertEquals(listOf(1_000L, 2_000L, 3_000L, 4_000L), points.map { it.timestampMillis })
        assertEquals(0.0, assertNotNull(points[0].upwindWindDegrees), 1e-9)
        assertEquals(315.0, points[0].headingDegrees)
        assertEquals(3.0, points[0].speedMps)
        assertNull(points[1].upwindWindDegrees) // reaching
        assertNull(points[2].upwindWindDegrees) // drifting
        assertNull(points[3].upwindWindDegrees) // running
        assertEquals(origin, points[3].point)

        val cleared = state.apply(RaceEvent.ClearTrack)
        assertTrue(cleared.track.isEmpty)
        assertEquals(Track.DEFAULT_CAPACITY, cleared.track.capacity)
        assertEquals(state.wind.histogram, cleared.wind.histogram)
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

    @Test
    fun `the windward mark can be set here, from the line, directly, and cleared`() {
        assertNull(RaceState().apply(RaceEvent.MarkWindwardMark).windwardMark)
        assertNull(RaceState().apply(RaceEvent.SetWindwardMarkFromLine(0, 500.0)).windwardMark)

        val here = RaceState().apply(RaceEvent.FixReceived(fix(1_000, point = origin)))
        assertEquals(origin, here.apply(RaceEvent.MarkWindwardMark).windwardMark)
        // Without a line the bearing and distance count from the boat.
        val fromBoat = assertNotNull(here.apply(RaceEvent.SetWindwardMarkFromLine(0, 500.0)).windwardMark)
        assertEquals(0.0, Geo.distanceMeters(north(500.0), fromBoat), 0.01)
        // With a line they count from its middle.
        val fromLine = assertNotNull(
            here.apply(RaceEvent.SetStartLine(StartLine(origin, east(100.0))), RaceEvent.SetWindwardMarkFromLine(0, 500.0)).windwardMark,
        )
        assertEquals(0.0, Geo.distanceMeters(north(500.0, from = east(50.0)), fromLine), 0.01)

        assertEquals(north(5.0), here.apply(RaceEvent.SetWindwardMark(north(5.0))).windwardMark)
        assertNull(fromLine.let { here.apply(RaceEvent.SetWindwardMark(it), RaceEvent.SetWindwardMark(null)).windwardMark })
    }

    @Test
    fun `clearing the session keeps the settings, the set wind and the sensors`() {
        val settings = RaceSettings(windHistoryCapacity = 7, compassOffsetDegrees = 180)
        val state = RaceState(settings = settings).apply(
            RaceEvent.SetWindDirection(90),
            RaceEvent.CompassUpdated(10.0),
            RaceEvent.FixReceived(fix(1_000, courseDegrees = 45.0)),
            RaceEvent.MarkPinEnd,
            RaceEvent.MarkWindwardMark,
            RaceEvent.StartCountdown(5, nowMillis = 1_000),
        )
        assertEquals(1, state.wind.histogram.totalSamples)
        val cleared = state.apply(RaceEvent.ClearSession)
        assertEquals(StartLine(), cleared.startLine)
        assertNull(cleared.windwardMark)
        assertEquals(TimerState.Idle, cleared.timer)
        assertTrue(cleared.track.isEmpty)
        assertEquals(Track.DEFAULT_CAPACITY, cleared.track.capacity)
        assertEquals(WindHistogram(), cleared.wind.histogram)
        assertEquals(WindHistory(capacity = 7), cleared.wind.history)
        assertEquals(SpeedStats(), cleared.speedStats)
        assertNull(cleared.lastCuedSecond)
        assertNull(cleared.lastSampleSecond)
        assertEquals(settings, cleared.settings)
        assertEquals(90, cleared.wind.settings.directionDegrees)
        assertEquals(state.navigation, cleared.navigation)
    }

    @Test
    fun `sampling judges the tack against the measured mean, not the roughly set wind`() {
        // Thirty close-hauled samples on starboard at 340: the wind is really 25, not the 0 that was set.
        var state = RaceState()
        for (t in 1L..30L) state = state.apply(RaceEvent.FixReceived(fix(t * 1_000, courseDegrees = 340.0)))
        assertEquals(25.0, state.wind.reference.directionDegrees, 1e-9)
        assertTrue(state.wind.reference.isMeasured)
        // Heading 80 is a reach against the set wind (80 off) but close-hauled on port against the real one (55 off).
        val port = state.apply(RaceEvent.FixReceived(fix(60_000, courseDegrees = 80.0)))
        assertEquals(31, port.wind.histogram.totalSamples)
        assertEquals(1, port.wind.histogram.count(35))
        assertEquals(35.0, assertNotNull(port.track.latest?.upwindWindDegrees), 1e-9)
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
