package com.sailracing.app.log

import com.sailracing.app.data.AppSettings
import com.sailracing.app.data.SimulationSettings
import com.sailracing.domain.course.GridSettings
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.race.ApproachSpeed
import com.sailracing.domain.race.RaceEngine
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.race.RaceSettings
import com.sailracing.domain.race.RaceSnapshot
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.timer.Cue
import com.sailracing.domain.timer.TimerState
import com.sailracing.domain.wind.WindSettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What ends up in the log: every event that reached the engine, and the app's own state about once a
 * second. Together they are the session - the first to replay it, the second to read it.
 */
class SessionRecorderTest {

    private val engine = RaceEngine()
    private val recorder = SessionRecorder()
    private val here = GeoPoint(51.14, 5.83)

    private fun snapshot(atMillis: Long): RaceSnapshot = engine.snapshot(atMillis)

    private fun types(event: RaceEvent, atMillis: Long): List<String> =
        recorder.record(event, snapshot(atMillis)).map { it.type }

    @Test
    fun theHeadOfTheLogSaysWhatTheSessionWasStartedWith() {
        val settings = AppSettings(
            race = RaceSettings(
                approachSpeed = ApproachSpeed.Manual(2.5),
                course = RaceSettings().course.copy(grid = GridSettings(40.0)),
            ),
            simulation = SimulationSettings(enabled = true, speedFactor = 20.0),
        )
        val line = recorder.start(1_000L, "1.2.3", settings).toJsonLine()
        assertTrue(line.startsWith("""{"t":1000,"type":"session","app":"1.2.3","""), line)
        assertTrue(line.contains(""""simulation":true"""), line)
        assertTrue(line.contains(""""simulationSpeed":20.0"""), line)
        assertTrue(line.contains(""""approachSpeedMps":2.5"""), line)
        assertTrue(line.contains(""""approachSpeedManual":true"""), line)
        assertTrue(line.contains(""""cellSizeMeters":40.0"""), line)
        assertTrue(line.contains(""""monteCarloRuns":1000"""), line)
        assertTrue(line.contains(""""windColumns":3"""), line)
        // A racing area that chooses its own squares simply has no size to state.
        assertTrue(!recorder.start(0L, "", AppSettings()).toJsonLine().contains("cellSizeMeters"))
    }

    @Test
    fun everyEventIsWrittenDownWithWhatItCarried() {
        fun line(event: RaceEvent): String =
            recorder.record(event, snapshot(0L)).first { it.type == "event" }.toJsonLine()

        val fix = line(RaceEvent.FixReceived(PositionFix(here, 900L, 3.0, 320.0, 4.0)))
        assertTrue(fix.contains(""""event":"FixReceived""""), fix)
        assertTrue(fix.contains(""""lat":51.14""") && fix.contains(""""lon":5.83"""), fix)
        assertTrue(fix.contains(""""speedMps":3.0""") && fix.contains(""""courseDegrees":320.0"""), fix)
        assertTrue(fix.contains(""""accuracyMeters":4.0""") && fix.contains(""""fixMillis":900"""), fix)

        assertTrue(line(RaceEvent.CompassUpdated(12.5)).contains(""""headingDegrees":12.5"""))
        assertTrue(line(RaceEvent.SetStartLine(StartLine(here, here))).contains(""""pinLat":51.14"""))
        assertTrue(line(RaceEvent.SetStartLine(StartLine(here, here))).contains(""""boatLon":5.83"""))
        assertTrue(line(RaceEvent.SetWindwardMark(here)).contains(""""lat":51.14"""))
        assertTrue(line(RaceEvent.SetWindwardMarkFromLine(30, 900.0)).contains(""""distanceMeters":900.0"""))
        assertTrue(line(RaceEvent.StartCountdown(5, 1_000L)).contains(""""minutes":5"""))
        assertTrue(line(RaceEvent.SyncCountdown(2_000L)).contains(""""atMillis":2000"""))
        assertTrue(line(RaceEvent.SetTimer(TimerState.Running(3_000L))).contains(""""startAtMillis":3000"""))
        assertTrue(line(RaceEvent.SetWindDirection(180)).contains(""""degrees":180"""))
        assertTrue(line(RaceEvent.SetTackAngle(43)).contains(""""degrees":43"""))
        assertTrue(line(RaceEvent.SetDownwindAngle(150)).contains(""""degrees":150"""))
        assertTrue(line(RaceEvent.SetWindSettings(WindSettings(200, 44, 145))).contains(""""directionDegrees":200"""))
        assertTrue(line(RaceEvent.UpdateSettings(RaceSettings(closeHauledBandDegrees = 25))).contains(""""closeHauledBandDegrees":25"""))

        // What was cleared or unset says so by leaving the value out, and is still a line of its own.
        assertTrue(line(RaceEvent.SetWindwardMark(null)).endsWith(""""event":"SetWindwardMark"}"""))
        assertTrue(line(RaceEvent.SetTimer(TimerState.Idle)).endsWith(""""event":"SetTimer"}"""))
        assertTrue(line(RaceEvent.MarkPinEnd).endsWith(""""event":"MarkPinEnd"}"""))
        assertTrue(line(RaceEvent.ClearSession).endsWith(""""event":"ClearSession"}"""))
        assertTrue(line(RaceEvent.SetWindFromPortTack).endsWith(""""event":"SetWindFromPortTack"}"""))
    }

    /**
     * The names are spelled out, not read off the classes: a release build shortens those to a letter,
     * which is what made a day's log unreadable once.
     */
    @Test
    fun everyEventHasItsNameSpelledOut() {
        val named = mapOf(
            RaceEvent.FixReceived(PositionFix(here, 0L, 1.0, 0.0, 1.0)) to "FixReceived",
            RaceEvent.CompassUpdated(1.0) to "CompassUpdated",
            RaceEvent.Tick(0L) to "Tick",
            RaceEvent.MarkPinEnd to "MarkPinEnd",
            RaceEvent.MarkBoatEnd to "MarkBoatEnd",
            RaceEvent.ClearPinEnd to "ClearPinEnd",
            RaceEvent.ClearBoatEnd to "ClearBoatEnd",
            RaceEvent.SetStartLine(StartLine()) to "SetStartLine",
            RaceEvent.MarkWindwardMark to "MarkWindwardMark",
            RaceEvent.SetWindwardMarkFromLine(0, 1.0) to "SetWindwardMarkFromLine",
            RaceEvent.SetWindwardMark(null) to "SetWindwardMark",
            RaceEvent.StartCountdown(5, 0L) to "StartCountdown",
            RaceEvent.SyncCountdown(0L) to "SyncCountdown",
            RaceEvent.StopTimer to "StopTimer",
            RaceEvent.SetTimer(TimerState.Idle) to "SetTimer",
            RaceEvent.SetWindDirection(0) to "SetWindDirection",
            RaceEvent.SetTackAngle(45) to "SetTackAngle",
            RaceEvent.SetDownwindAngle(140) to "SetDownwindAngle",
            RaceEvent.SetWindFromPortTack to "SetWindFromPortTack",
            RaceEvent.SetWindFromStarboardTack to "SetWindFromStarboardTack",
            RaceEvent.SetWindSettings(WindSettings()) to "SetWindSettings",
            RaceEvent.ResetWindStatistics to "ResetWindStatistics",
            RaceEvent.ResetSpeedStatistics to "ResetSpeedStatistics",
            RaceEvent.ClearTrack to "ClearTrack",
            RaceEvent.ClearSession to "ClearSession",
            RaceEvent.UpdateSettings(RaceSettings()) to "UpdateSettings",
        )
        for ((event, name) in named) assertEquals(name, with(SessionRecorder) { event.logName() })
    }

    @Test
    fun theStateIsWrittenAboutOnceASecondAndATickIsNoEvent() {
        // The first event of a session always leaves a state line behind it.
        assertEquals(listOf("event", "state"), types(RaceEvent.CompassUpdated(10.0), 0L))
        // Within the second, only the event itself.
        assertEquals(listOf("event"), types(RaceEvent.CompassUpdated(11.0), 500L))
        assertEquals(listOf("event", "state"), types(RaceEvent.CompassUpdated(12.0), 1_000L))
        // A tick is not an event, but it does keep the state coming while nothing else happens.
        assertTrue(types(RaceEvent.Tick(1_500L), 1_500L).isEmpty())
        assertEquals(listOf("state"), types(RaceEvent.Tick(2_100L), 2_100L))
        // A clock that jumped backwards (a simulation started again) does not stop the log.
        assertEquals(listOf("state"), types(RaceEvent.Tick(5L), 5L))
    }

    @Test
    fun theStateLineSaysWhatTheAppWasShowing() {
        engine.dispatch(RaceEvent.SetWindDirection(0))
        engine.dispatch(RaceEvent.FixReceived(PositionFix(here, 1_000L, 3.0, 320.0, 3.0)))
        engine.dispatch(RaceEvent.MarkPinEnd)
        engine.dispatch(RaceEvent.StartCountdown(5, 1_000L))
        val line = recorder.record(RaceEvent.Tick(2_000L), snapshot(2_000L)).single().toJsonLine()
        assertTrue(line.contains(""""type":"state""""), line)
        assertTrue(line.contains(""""phase":"COUNTDOWN""""), line)
        assertTrue(line.contains(""""lat":51.14"""), line)
        assertTrue(line.contains(""""speedMps":3.0"""), line)
        assertTrue(line.contains(""""referenceWindDegrees":0.0"""), line)
        assertTrue(line.contains(""""referenceMeasured":false"""), line)
        assertTrue(line.contains(""""shiftDegrees":5.0"""), line)
        assertTrue(line.contains(""""steadyShiftDegrees":"""), line)
        assertTrue(!line.contains("measuredTackAngleDegrees"), line)
        assertTrue(line.contains(""""trackPoints":1"""), line)
        assertTrue(line.contains(""""cells":"""), line)
        assertTrue(line.contains(""""measuredBlocks":0"""), line)
        assertTrue(line.contains(""""windBlocks":"""), line)
        assertTrue(line.contains(""""advice":"""), line)
        assertTrue(line.contains(""""favouredSide":"""), line)
    }

    @Test
    fun cuesAndTheEndOfTheSession() {
        assertEquals("""{"t":10,"type":"cue","cue":"START"}""", recorder.cue(Cue.START, 10L).toJsonLine())
        assertEquals("""{"t":20,"type":"end"}""", recorder.end(20L).toJsonLine())
    }
}
