package com.sailracing.app.race

import com.sailracing.app.data.AppSettings
import com.sailracing.app.data.SimulationSettings
import com.sailracing.app.fakes.FakeRepository
import com.sailracing.app.fakes.RecordingCuePlayer
import com.sailracing.app.fakes.SchedulerClock
import com.sailracing.app.sensors.SimulatedSensorSource
import com.sailracing.domain.timer.RacePhase
import com.sailracing.simulation.StandardRaceScenario
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Changing the simulation speed mid-race restarts the replay; the scripted actions must still all apply. */
@OptIn(ExperimentalCoroutinesApi::class)
class RaceSessionRestartTest {

    @Test
    fun speedChangeRestartsTheReplayWithActions() = runTest {
        val repository = FakeRepository(initialSettings = AppSettings(simulation = SimulationSettings(enabled = true, autoPlayActions = true, speedFactor = 1.0)))
        val result = StandardRaceScenario.build()
        val clock = SchedulerClock(this)
        var created = 0
        val session = RaceSession(
            repository = repository,
            sensorSourceFactory = { settings, sessionClock ->
                created++
                SimulatedSensorSource(result, sessionClock, includeActions = settings.simulation.autoPlayActions, loop = true)
            },
            cuePlayer = RecordingCuePlayer(),
            scope = this,
            baseClock = clock,
            engineDispatcher = StandardTestDispatcher(testScheduler),
        )
        session.start()
        runCurrent()
        advanceTimeBy(25_000)
        runCurrent()
        assertEquals(1, created)
        assertTrue(session.state.value.startLine.pinEnd != null)

        repository.updateSettings { it.copy(simulation = it.simulation.copy(speedFactor = 6.0)) }
        runCurrent()
        assertEquals(2, created)

        // 6x: the whole pre-start (245 s) plus a minute of countdown takes about a minute of real time.
        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(session.state.value.startLine.isComplete, "boat end should be marked after the restart")
        assertEquals(RacePhase.COUNTDOWN, session.snapshot.value.phase)
        assertTrue(session.state.value.wind.settings.directionDegrees != 0)
        session.stop()
    }
}
