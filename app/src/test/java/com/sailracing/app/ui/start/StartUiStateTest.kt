package com.sailracing.app.ui.start

import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.race.ApproachSpeed
import com.sailracing.domain.race.RaceEngine
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.race.RaceSettings
import com.sailracing.domain.race.RaceState
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.timer.RacePhase
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartUiStateTest {

    private val pin = GeoPoint(51.14, 5.83)
    private val boat = Geo.destination(pin, 90.0, 100.0)
    private val center = Geo.destination(pin, 90.0, 50.0)

    @Test
    fun idleWithoutGps() {
        val state = StartUiState.from(RaceEngine().snapshot(nowMillis = 0))
        assertEquals(RacePhase.SETUP, state.phase)
        assertEquals("Time", state.clockLabel)
        assertEquals("---", state.timeToKill)
        assertEquals(Urgency.NEUTRAL, state.urgency)
        assertEquals("---", state.distanceToLine)
        assertFalse(state.canMark)
        assertEquals("No GPS", state.gpsStatus)
        assertFalse(state.gpsOk)
        assertEquals("Mark both ends", state.lineLength)
        assertTrue(state.approachSpeed.contains("default"))
        assertFalse(state.overEarly)
    }

    @Test
    fun countdownEarlyAndLate() {
        val engine = RaceEngine(RaceState(startLine = StartLine(pin, boat), settings = RaceSettings(approachSpeed = ApproachSpeed.Manual(2.0))))
        engine.dispatch(RaceEvent.StartCountdown(1, nowMillis = 0))
        val below = Geo.destination(center, 180.0, 40.0) // 20 s to the line at 2 m/s
        engine.dispatch(RaceEvent.FixReceived(PositionFix(below, 10_000, 2.5, 320.0, 3.0)))

        val early = StartUiState.from(engine.snapshot(10_000))
        assertEquals(RacePhase.COUNTDOWN, early.phase)
        assertEquals("Time to start", early.clockLabel)
        assertEquals("00:50", early.clock)
        assertEquals("+30 s", early.timeToKill)
        assertEquals(Urgency.EARLY, early.urgency)
        assertEquals("40 m", early.distanceToLine)
        assertFalse(early.overLine)
        assertTrue(early.pinSet && early.boatSet)
        assertEquals("Line 100 m", early.lineLength)
        assertTrue(early.canMark)
        assertEquals("GPS ±3 m", early.gpsStatus)
        assertTrue(early.gpsOk)
        assertFalse(early.overEarly)

        val late = StartUiState.from(engine.snapshot(50_000))
        assertEquals("-10 s", late.timeToKill)
        assertEquals(Urgency.LATE, late.urgency)
        assertEquals("GPS stale", late.gpsStatus)
        assertFalse(late.gpsOk)
    }

    @Test
    fun overTheLineDuringTheCountdown() {
        val engine = RaceEngine(RaceState(startLine = StartLine(pin, boat)))
        engine.dispatch(RaceEvent.StartCountdown(1, nowMillis = 0))
        val above = Geo.destination(center, 0.0, 10.0)
        engine.dispatch(RaceEvent.FixReceived(PositionFix(above, 1_000, 2.5, 320.0, null)))
        val state = StartUiState.from(engine.snapshot(1_000))
        assertTrue(state.overEarly)
        assertTrue(state.overLine)
        assertEquals("-10 m", state.distanceToLine)
        assertEquals("GPS", state.gpsStatus)

        // Not a concern once racing: the course side is where the boat belongs.
        val racing = StartUiState.from(engine.snapshot(61_000))
        assertEquals(RacePhase.RACING, racing.phase)
        assertEquals("Race time", racing.clockLabel)
        assertEquals("+00:01", racing.clock)
        assertEquals("---", racing.timeToKill)
        assertFalse(racing.overEarly)
        assertFalse(racing.overLine)
        assertEquals("10 m", racing.distanceToLine)

        // Before the countdown it is still the wrong side of the line, only without the warning.
        val setup = StartUiState.from(RaceEngine(RaceState(startLine = StartLine(pin, boat))).also {
            it.dispatch(RaceEvent.FixReceived(PositionFix(above, 1_000, 2.5, 320.0, null)))
        }.snapshot(1_000))
        assertTrue(setup.overLine)
        assertFalse(setup.overEarly)
        assertEquals("-10 m", setup.distanceToLine)
    }
}
