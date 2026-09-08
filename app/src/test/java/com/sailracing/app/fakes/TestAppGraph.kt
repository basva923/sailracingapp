package com.sailracing.app.fakes

import com.sailracing.app.audio.CuePlayer
import com.sailracing.app.data.AppSettings
import com.sailracing.app.data.PersistedRace
import com.sailracing.app.data.RaceRepository
import com.sailracing.app.di.AppGraph
import com.sailracing.app.log.SessionLog
import com.sailracing.app.race.RaceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

/** A graph whose coroutines only run when the test advances [scheduler]; sensors are pushed by the test. */
@OptIn(ExperimentalCoroutinesApi::class)
class TestAppGraph(
    initialSettings: AppSettings = AppSettings(),
    initialRace: PersistedRace = PersistedRace(),
) : AppGraph {
    val scheduler = TestCoroutineScheduler()
    val dispatcher = StandardTestDispatcher(scheduler)
    val clock = ManualClock(1_700_000_000_000L)
    val sensors = FakeSensorSource()
    val cues = RecordingCuePlayer()
    val fakeRepository = FakeRepository(initialSettings, initialRace)
    val log = RecordingSessionLog()

    override val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    override val repository: RaceRepository get() = fakeRepository
    override val cuePlayer: CuePlayer get() = cues
    override val sessionLog: SessionLog get() = log
    /** Starts the session without blocking: the engine dispatcher only runs when [scheduler] is advanced. */
    fun startSession() {
        applicationScope.launch { raceSession.start() }
        scheduler.runCurrent()
    }

    override val raceSession: RaceSession = RaceSession(
        repository = fakeRepository,
        sensorSourceFactory = { _, _ -> sensors },
        cuePlayer = cues,
        scope = applicationScope,
        log = log,
        versionName = "test",
        baseClock = clock,
        engineDispatcher = dispatcher,
    )
}
