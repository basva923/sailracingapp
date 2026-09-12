package com.sailracing.app.ui.session

import com.sailracing.app.ui.format.Formatters
import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.race.RaceEngine
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.timer.TimerState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionUiStateTest {

    private val pin = GeoPoint(51.14, 5.83)
    private val boatEnd = Geo.destination(pin, 90.0, 100.0)

    private fun sailing(vararg fixes: Int, accuracy: Double? = 3.0): RaceEngine {
        val engine = RaceEngine()
        engine.dispatch(RaceEvent.SetWindDirection(20))
        for (t in fixes) engine.dispatch(RaceEvent.FixReceived(PositionFix(pin, t * 1_000L, 3.0, 340.0, accuracy)))
        return engine
    }

    @Test
    fun noSession() {
        val state = SessionUiState.from(RaceEngine().snapshot(0), running = false, startedAtMillis = null, simulating = false)
        assertEquals(SessionStatus.NONE, state.status)
        assertFalse(state.hasData)
        assertEquals("NO SESSION", state.title)
        assertEquals("Start a session to switch on the GPS and begin measuring the wind", state.detail)
        assertEquals("GPS off", state.gpsStatus)
        assertFalse(state.gpsOk)
        assertEquals("---", state.since)
        assertEquals("No track yet", state.track)
        assertEquals("No upwind samples yet", state.wind)
        assertEquals("Not set", state.line)
        assertEquals("Top of the area", state.mark)
        assertEquals("Idle", state.timer)
        assertFalse(state.simulating)
        assertEquals(SessionUiState(), state)
    }

    @Test
    fun aRunningSessionWithData() {
        val engine = sailing(1, 2)
        engine.dispatch(RaceEvent.SetStartLine(StartLine(pin, boatEnd)))
        engine.dispatch(RaceEvent.StartCountdown(5, nowMillis = 1_000))
        val startedAt = 1_700_000_000_000L
        val state = SessionUiState.from(engine.snapshot(2_000), running = true, startedAtMillis = startedAt, simulating = true)
        assertEquals(SessionStatus.RUNNING, state.status)
        assertTrue(state.hasData)
        assertEquals("SESSION RUNNING", state.title)
        val since = Formatters.timeOfDay(startedAt)
        assertEquals("GPS, wind statistics and countdown are on since $since · simulating a race", state.detail)
        assertEquals(since, state.since)
        assertEquals("GPS ±3 m", state.gpsStatus)
        assertTrue(state.gpsOk)
        assertEquals("2 points · 0 min", state.track)
        assertEquals("2 upwind samples", state.wind)
        assertEquals("Set · 100 m", state.line)
        assertEquals("Top of the area", state.mark)
        assertEquals("Start in 04:59", state.timer)
        assertTrue(state.simulating)

        val ended = SessionUiState.from(engine.snapshot(2_000), running = false, startedAtMillis = startedAt, simulating = false)
        assertEquals(SessionStatus.ENDED, ended.status)
        assertEquals("SESSION ENDED", ended.title)
        assertEquals(
            "GPS is off, the track and the statistics are kept. Starting again is the next race: it begins without a line or a mark",
            ended.detail,
        )
        assertEquals("GPS off", ended.gpsStatus)
    }

    @Test
    fun gpsQualityMeasuredWindMarkAndPartialLine() {
        val engine = sailing(*IntArray(30) { it + 1 })
        engine.dispatch(RaceEvent.MarkWindwardMark)
        engine.dispatch(RaceEvent.SetStartLine(StartLine(pinEnd = pin)))
        val fresh = SessionUiState.from(engine.snapshot(30_000), running = true, startedAtMillis = 0L, simulating = false)
        assertEquals("30 upwind samples · mean 025°", fresh.wind)
        assertEquals("Set", fresh.mark)
        assertEquals("Pin end only", fresh.line)
        assertEquals("Idle", fresh.timer)

        val stale = SessionUiState.from(engine.snapshot(60_000), running = true, startedAtMillis = 0L, simulating = false)
        assertEquals("GPS stale", stale.gpsStatus)
        assertFalse(stale.gpsOk)

        engine.dispatch(RaceEvent.SetStartLine(StartLine(boatEnd = boatEnd)))
        engine.dispatch(RaceEvent.SetTimer(TimerState.Running(25_000)))
        val racing = SessionUiState.from(engine.snapshot(30_000), running = true, startedAtMillis = 0L, simulating = false)
        assertEquals("Boat end only", racing.line)
        assertEquals("Racing +00:05", racing.timer)

        val noAccuracy = sailing(1, accuracy = null)
        assertEquals("GPS", SessionUiState.from(noAccuracy.snapshot(1_000), running = true, startedAtMillis = 0L, simulating = false).gpsStatus)
        val noFix = RaceEngine().also { it.dispatch(RaceEvent.SetStartLine(StartLine(pinEnd = pin))) }
        val waiting = SessionUiState.from(noFix.snapshot(0), running = true, startedAtMillis = 0L, simulating = false)
        assertEquals("No GPS", waiting.gpsStatus)
        assertTrue(waiting.hasData)
    }
}
