package com.sailracing.app.race

import com.sailracing.app.data.AppSettings
import com.sailracing.app.data.PersistedRace
import com.sailracing.app.data.SimulationSettings
import com.sailracing.app.fakes.FakeRepository
import com.sailracing.app.fakes.FakeSensorSource
import com.sailracing.app.fakes.RecordingCuePlayer
import com.sailracing.app.fakes.RecordingSessionLog
import com.sailracing.app.fakes.SchedulerClock
import com.sailracing.app.sensors.SimulatedSensorSource
import com.sailracing.app.time.ScaledClock
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.race.HeadingSource
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.race.RaceSettings
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.timer.Cue
import com.sailracing.domain.timer.CuePolicy
import com.sailracing.domain.timer.RacePhase
import com.sailracing.domain.timer.TimerState
import com.sailracing.domain.wind.WindSettings
import com.sailracing.simulation.StandardRaceScenario
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RaceSessionTest {

    private fun TestScope.session(
        repository: FakeRepository,
        sensors: FakeSensorSource = FakeSensorSource(),
        cues: RecordingCuePlayer = RecordingCuePlayer(),
        clock: SchedulerClock = SchedulerClock(this),
        log: RecordingSessionLog = RecordingSessionLog(),
    ): RaceSession {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return RaceSession(
            repository = repository,
            sensorSourceFactory = { _, _ -> sensors },
            cuePlayer = cues,
            scope = this,
            log = log,
            versionName = "1.0",
            baseClock = clock,
            engineDispatcher = dispatcher,
        )
    }

    @Test
    fun restoresPersistedRaceAndSettings() = runTest {
        val clock = SchedulerClock(this)
        val wind = WindSettings(200, 40, 150)
        val running = TimerState.Running(clock.nowMillis() + 60_000)
        val repository = FakeRepository(
            initialSettings = AppSettings(race = RaceSettings(compassOffsetDegrees = 180), keepScreenOn = false),
            initialRace = PersistedRace(wind, running),
        )
        val session = session(repository, clock = clock)
        assertEquals(null, session.startedAtMillis.value)
        session.start()
        runCurrent()

        assertTrue(session.isRunning.value)
        assertEquals(clock.nowMillis(), session.startedAtMillis.value)
        assertEquals(wind, session.state.value.wind.settings)
        assertEquals(running, session.state.value.timer)
        assertEquals(180, session.state.value.settings.compassOffsetDegrees)
        assertFalse(session.settings.value.keepScreenOn)
        assertEquals(RacePhase.COUNTDOWN, session.snapshot.value.phase)
        // Restoring must not write anything back.
        assertTrue(repository.savedWinds.isEmpty() && repository.savedTimers.isEmpty())
        // Starting twice is harmless.
        session.start()
        session.stop()
        assertFalse(session.isRunning.value)
        session.stop()
    }

    /**
     * A line and a mark belong to the race that is being sailed, not to the phone: whatever was marked
     * before, every session begins with an empty course.
     */
    @Test
    fun everySessionStartsWithoutALineOrAMark() = runTest {
        val sensors = FakeSensorSource()
        val session = session(FakeRepository(), sensors)
        session.start()
        runCurrent()
        sensors.events.tryEmit(RaceEvent.FixReceived(PositionFix(GeoPoint(51.14, 5.83), session.nowMillis(), 3.0, 315.0, 3.0)))
        runCurrent()
        session.dispatch(RaceEvent.MarkPinEnd)
        session.dispatch(RaceEvent.MarkBoatEnd)
        session.dispatch(RaceEvent.MarkWindwardMark)
        runCurrent()
        assertTrue(session.state.value.startLine.isComplete)
        assertNotNull(session.state.value.windwardMark)

        // Ending and starting again is the next race, not a continuation of the marked one.
        session.stop()
        session.start()
        runCurrent()
        assertEquals(StartLine(), session.state.value.startLine)
        assertNull(session.state.value.windwardMark)
        session.stop()
    }

    @Test
    fun discardsAStaleCountdown() = runTest {
        val clock = SchedulerClock(this)
        val stale = TimerState.Running(clock.nowMillis() - RaceSession.MAX_RESTORED_RACE_AGE_MILLIS - 1)
        val session = session(FakeRepository(initialRace = PersistedRace(timer = stale)), clock = clock)
        session.start()
        runCurrent()
        assertEquals(TimerState.Idle, session.state.value.timer)
        session.stop()
    }

    @Test
    fun everythingOfTheSessionEndsUpInTheLog() = runTest {
        val log = RecordingSessionLog()
        val sensors = FakeSensorSource()
        val session = session(FakeRepository(), sensors, log = log)
        session.start()
        runCurrent()
        // The head of the log says what the session was started with, before anything happened in it.
        assertTrue(log.lines.first().contains(""""type":"session""""), log.lines.first())
        assertTrue(log.lines.first().contains(""""app":"1.0""""), log.lines.first())
        // Restoring the persisted race is the first thing the app was told, so it is logged as well.
        assertTrue(log.ofType("event").any { it.contains("SetWindSettings") })

        sensors.events.tryEmit(RaceEvent.FixReceived(PositionFix(GeoPoint(51.14, 5.83), session.nowMillis(), 3.0, 315.0, 3.0)))
        session.startCountdown(1)
        runCurrent()
        advanceTimeBy(61_000)
        runCurrent()
        session.stop()

        assertTrue(log.ofType("event").any { it.contains(""""event":"FixReceived"""") && it.contains(""""lat":51.14""") })
        assertTrue(log.ofType("event").any { it.contains("StartCountdown") })
        assertTrue(log.ofType("cue").any { it.contains(""""cue":"START"""") })
        // The state is written down about once a second all the way through the minute.
        assertTrue(log.ofType("state").size >= 60, "only ${log.ofType("state").size} state lines")
        assertTrue(log.lines.last().contains(""""type":"end""""), log.lines.last())
    }

    @Test
    fun ticksPlayCuesAndActionsArePersisted() = runTest {
        val repository = FakeRepository()
        val cues = RecordingCuePlayer()
        val sensors = FakeSensorSource()
        val session = session(repository, sensors, cues)
        session.start()
        runCurrent()

        session.startCountdown(1)
        runCurrent()
        assertIs<TimerState.Running>(session.state.value.timer)
        advanceTimeBy(61_000)
        runCurrent()
        assertEquals(RacePhase.RACING, session.snapshot.value.phase)
        assertEquals(Cue.MINUTE, cues.cues.first())
        assertEquals(Cue.START, cues.cues.last())
        assertEquals(1 + 5 + 9 + 1, cues.cues.size)
        assertEquals(listOf(session.state.value.timer), repository.savedTimers)

        sensors.events.tryEmit(RaceEvent.FixReceived(PositionFix(GeoPoint(51.14, 5.83), session.nowMillis(), 3.0, 315.0, 3.0)))
        runCurrent()
        session.dispatch(RaceEvent.MarkPinEnd)
        session.dispatch(RaceEvent.MarkWindwardMark)
        session.dispatch(RaceEvent.SetWindDirection(90))
        runCurrent()
        assertEquals(90, repository.savedWinds.last().directionDegrees)

        session.syncCountdown()
        session.dispatch(RaceEvent.StopTimer)
        runCurrent()
        assertEquals(TimerState.Idle, repository.savedTimers.last())
        session.stop()
    }

    @Test
    fun settingsChangesReachTheEngineAndSwitchSensors() = runTest {
        val repository = FakeRepository()
        var created = 0
        var lastSettings: AppSettings? = null
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = SchedulerClock(this)
        val session = RaceSession(
            repository = repository,
            sensorSourceFactory = { settings, _ -> created++; lastSettings = settings; FakeSensorSource() },
            cuePlayer = RecordingCuePlayer(),
            scope = this,
            baseClock = clock,
            engineDispatcher = dispatcher,
        )
        session.start()
        runCurrent()
        assertEquals(1, created)
        assertEquals(clock, session.clock)

        repository.updateSettings { it.copy(race = it.race.copy(cuePolicy = CuePolicy(enabled = false))) }
        runCurrent()
        assertFalse(session.state.value.settings.cuePolicy.enabled)
        assertEquals(1, created)

        repository.updateSettings { it.copy(simulation = SimulationSettings(enabled = true, speedFactor = 4.0)) }
        runCurrent()
        assertEquals(2, created)
        assertEquals(true, lastSettings?.simulation?.enabled)
        assertIs<ScaledClock>(session.clock)
        val before = session.nowMillis()
        advanceTimeBy(1_000)
        assertEquals(4_000, session.nowMillis() - before)

        // Loading another simulation replays another scenario, from a clean sheet.
        session.dispatch(RaceEvent.SetWindwardMark(GeoPoint(51.14, 5.83)))
        runCurrent()
        assertNotNull(session.state.value.windwardMark)
        repository.updateSettings { it.copy(simulation = it.simulation.copy(scenarioId = "oscillating")) }
        runCurrent()
        assertEquals(3, created)
        assertEquals("oscillating", lastSettings?.simulation?.scenarioId)
        assertNull(session.state.value.windwardMark)

        // Taking the heading from the compass means another sensor to listen to, so the source is rebuilt.
        repository.updateSettings { it.copy(race = it.race.copy(headingSource = HeadingSource.COMPASS)) }
        runCurrent()
        assertEquals(4, created)
        assertEquals(HeadingSource.COMPASS, lastSettings?.race?.headingSource)
        session.stop()
        assertEquals(clock, session.clock)
    }

    @Test
    fun aFullSimulatedRaceRunsThroughTheSession() = runTest {
        val repository = FakeRepository(initialSettings = AppSettings(simulation = SimulationSettings(enabled = true, autoPlayActions = true, speedFactor = 1.0)))
        val cues = RecordingCuePlayer()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = SchedulerClock(this)
        val result = StandardRaceScenario.build()
        val session = RaceSession(
            repository = repository,
            sensorSourceFactory = { settings, sessionClock ->
                SimulatedSensorSource(result, sessionClock, includeActions = settings.simulation.autoPlayActions, loop = false)
            },
            cuePlayer = cues,
            scope = this,
            baseClock = clock,
            engineDispatcher = dispatcher,
        )
        session.start()
        runCurrent()

        advanceTimeBy(result.durationMillis + 2_000)
        runCurrent()

        // Everything the scripted sailor did went through the engine, and what outlives a session was persisted.
        assertTrue(session.state.value.startLine.isComplete)
        assertTrue(repository.savedWinds.isNotEmpty())
        assertEquals(TimerState.Idle, session.state.value.timer)
        assertTrue(repository.savedTimers.any { it is TimerState.Running })
        assertEquals(TimerState.Idle, repository.savedTimers.last())
        assertEquals(Cue.START, cues.cues.last { it == Cue.START })
        assertEquals(1, cues.cues.count { it == Cue.START })
        assertEquals(9, cues.cues.count { it == Cue.SECOND })
        assertTrue(session.state.value.wind.histogram.totalSamples > 200)
        assertNotNull(session.state.value.speedStats.upwind.mean)
        session.stop()
        advanceUntilIdle()
    }
}
