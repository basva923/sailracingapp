package com.sailracing.app.fakes

import com.sailracing.app.audio.CuePlayer
import com.sailracing.app.data.AppSettings
import com.sailracing.app.data.PersistedRace
import com.sailracing.app.data.RaceRepository
import com.sailracing.app.log.SessionLog
import com.sailracing.app.log.SessionRecorder
import com.sailracing.app.sensors.SensorSource
import com.sailracing.app.time.Clock
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.race.RaceSnapshot
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.timer.Cue
import com.sailracing.domain.timer.TimerState
import com.sailracing.domain.wind.WindSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope

/** In-memory repository that records every save. */
class FakeRepository(
    initialSettings: AppSettings = AppSettings(),
    initialRace: PersistedRace = PersistedRace(),
) : RaceRepository {
    override val settings = MutableStateFlow(initialSettings)
    override val persistedRace = MutableStateFlow(initialRace)
    val savedLines = mutableListOf<StartLine>()
    val savedMarks = mutableListOf<GeoPoint?>()
    val savedWinds = mutableListOf<WindSettings>()
    val savedTimers = mutableListOf<TimerState>()

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = settings.update(transform)

    override suspend fun saveStartLine(line: StartLine) {
        savedLines += line
        persistedRace.update { it.copy(startLine = line) }
    }

    override suspend fun saveWindwardMark(mark: GeoPoint?) {
        savedMarks += mark
        persistedRace.update { it.copy(windwardMark = mark) }
    }

    override suspend fun saveWind(wind: WindSettings) {
        savedWinds += wind
        persistedRace.update { it.copy(wind = wind) }
    }

    override suspend fun saveTimer(timer: TimerState) {
        savedTimers += timer
        persistedRace.update { it.copy(timer = timer) }
    }
}

/** A session log that keeps its lines in memory, so a test can read what would have been written. */
class RecordingSessionLog : SessionLog {
    private val recorder = SessionRecorder()
    val lines = mutableListOf<String>()
    var deleted = 0
        private set

    override val location: String = "/tmp/sessions"

    override fun start(nowMillis: Long, versionName: String, settings: AppSettings) {
        lines += recorder.start(nowMillis, versionName, settings).toJsonLine()
    }

    override fun record(event: RaceEvent, snapshot: RaceSnapshot) {
        recorder.record(event, snapshot).forEach { lines += it.toJsonLine() }
    }

    override fun cue(cue: Cue, nowMillis: Long) {
        lines += recorder.cue(cue, nowMillis).toJsonLine()
    }

    override fun end(nowMillis: Long) {
        lines += recorder.end(nowMillis).toJsonLine()
    }

    override suspend fun summary(): String = "${lines.size} lines"

    override suspend fun deleteAll() {
        deleted++
        lines.clear()
    }

    /** Every line of a kind, e.g. every "state" line. */
    fun ofType(type: String): List<String> = lines.filter { it.contains("\"type\":\"$type\"") }
}

/** A sensor source the test pushes events into. */
class FakeSensorSource : SensorSource {
    val events = MutableSharedFlow<RaceEvent>(extraBufferCapacity = 64)
    override fun events(): Flow<RaceEvent> = events
}

class RecordingCuePlayer : CuePlayer {
    val cues = mutableListOf<Cue>()
    override fun play(cue: Cue) {
        cues += cue
    }
}

/** A clock whose time is set by the test. */
class ManualClock(var now: Long = 0L) : Clock {
    override fun nowMillis(): Long = now
}

/** A clock that follows the virtual time of a coroutine test scheduler, offset to a plausible epoch. */
class SchedulerClock(private val scope: TestScope, private val epochMillis: Long = 1_700_000_000_000L) : Clock {
    override fun nowMillis(): Long = epochMillis + scope.testScheduler.currentTime
}
