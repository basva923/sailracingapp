package com.sailracing.app.race

import com.sailracing.app.audio.CuePlayer
import com.sailracing.app.data.AppSettings
import com.sailracing.app.data.RaceRepository
import com.sailracing.app.data.SimulationSettings
import com.sailracing.app.sensors.SensorSource
import com.sailracing.app.time.Clock
import com.sailracing.app.time.ScaledClock
import com.sailracing.app.time.SystemClock
import com.sailracing.domain.race.RaceCalculator
import com.sailracing.domain.race.RaceEffect
import com.sailracing.domain.race.RaceEngine
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.race.RaceSnapshot
import com.sailracing.domain.race.RaceState
import com.sailracing.domain.timer.TimerState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The single live race: owns the [RaceEngine], feeds it sensor events and clock ticks, plays cue effects,
 * restores and persists race data, and exposes the state to the UI and the foreground service.
 *
 * All engine access is serialised on one dispatcher so events never interleave.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RaceSession(
    private val repository: RaceRepository,
    private val sensorSourceFactory: (SimulationSettings, Clock) -> SensorSource,
    private val cuePlayer: CuePlayer,
    private val scope: CoroutineScope,
    private val baseClock: Clock = SystemClock,
    private val tickMillis: Long = DEFAULT_TICK_MILLIS,
    engineDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) {
    private val engineContext = engineDispatcher
    private val engine = RaceEngine()

    private val _state = MutableStateFlow(engine.state)
    val state: StateFlow<RaceState> = _state.asStateFlow()

    private val _snapshot = MutableStateFlow(RaceCalculator.snapshot(engine.state, baseClock.nowMillis()))
    val snapshot: StateFlow<RaceSnapshot> = _snapshot.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    @Volatile
    var clock: Clock = baseClock
        private set

    private val jobs = mutableListOf<Job>()
    private var sensorJob: Job? = null

    /** The time the app believes it is: scaled while a simulation runs. */
    fun nowMillis(): Long = clock.nowMillis()

    /** Restores persisted data and starts sensors, ticker and persistence. Safe to call repeatedly. */
    suspend fun start() {
        if (_isRunning.value) return
        _isRunning.value = true

        val initialSettings = repository.settings.first()
        val persisted = repository.persistedRace.first()
        _settings.value = initialSettings
        withContext(engineContext) {
            applyLocked(RaceEvent.UpdateSettings(initialSettings.race))
            applyLocked(RaceEvent.SetStartLine(persisted.startLine))
            applyLocked(RaceEvent.SetWindSettings(persisted.wind))
            applyLocked(RaceEvent.SetTimer(restorableTimer(persisted.timer)))
        }

        jobs += scope.launch { observeSettings() }
        jobs += scope.launch { persistChanges() }
        jobs += scope.launch { tick() }
        startSensors(initialSettings.simulation)
    }

    fun stop() {
        if (!_isRunning.value) return
        sensorJob?.cancel()
        sensorJob = null
        jobs.forEach { it.cancel() }
        jobs.clear()
        clock = baseClock
        _isRunning.value = false
    }

    /** Applies a sailor action from the UI. Time-stamped events get the session clock's time. */
    fun dispatch(event: RaceEvent) {
        scope.launch(engineContext) { applyLocked(event) }
    }

    fun startCountdown(minutes: Int) = dispatch(RaceEvent.StartCountdown(minutes, clock.nowMillis()))

    fun syncCountdown() = dispatch(RaceEvent.SyncCountdown(clock.nowMillis()))

    /** Must run on [engineContext]. */
    private fun applyLocked(event: RaceEvent) {
        val effects = engine.dispatch(event)
        publish()
        effects.forEach { effect ->
            when (effect) {
                is RaceEffect.PlayCue -> cuePlayer.play(effect.cue)
            }
        }
    }

    private fun publish() {
        _state.value = engine.state
        _snapshot.value = RaceCalculator.snapshot(engine.state, clock.nowMillis())
    }

    private suspend fun tick() {
        while (scope.isActive) {
            withContext(engineContext) { applyLocked(RaceEvent.Tick(clock.nowMillis())) }
            val factor = _settings.value.simulation.takeIf { it.enabled }?.speedFactor ?: 1.0
            delay((tickMillis / factor).toLong().coerceAtLeast(MIN_TICK_MILLIS))
        }
    }

    private suspend fun observeSettings() {
        repository.settings.collect { settings ->
            val previous = _settings.value
            _settings.value = settings
            if (settings.race != previous.race) dispatch(RaceEvent.UpdateSettings(settings.race))
            if (settings.simulation != previous.simulation) startSensors(settings.simulation)
        }
    }

    private fun startSensors(simulation: SimulationSettings) {
        sensorJob?.cancel()
        clock = if (simulation.enabled) ScaledClock(baseClock, simulation.speedFactor) else baseClock
        sensorJob = scope.launch {
            sensorSourceFactory(simulation, clock).events().collect { event ->
                withContext(engineContext) { applyLocked(event) }
            }
        }
    }

    private suspend fun persistChanges() = coroutineScope {
        launch { state.map { it.startLine }.distinctUntilChanged().drop(1).collect { repository.saveStartLine(it) } }
        launch { state.map { it.wind.settings }.distinctUntilChanged().drop(1).collect { repository.saveWind(it) } }
        launch { state.map { it.timer }.distinctUntilChanged().drop(1).collect { repository.saveTimer(it) } }
    }

    /** A countdown from a previous launch is only worth restoring if its start is recent. */
    private fun restorableTimer(timer: TimerState): TimerState {
        val running = timer as? TimerState.Running ?: return TimerState.Idle
        val age = baseClock.nowMillis() - running.startAtMillis
        return if (age <= MAX_RESTORED_RACE_AGE_MILLIS) running else TimerState.Idle
    }

    companion object {
        const val DEFAULT_TICK_MILLIS = 250L
        const val MIN_TICK_MILLIS = 20L
        const val MAX_RESTORED_RACE_AGE_MILLIS = 3 * 60 * 60 * 1000L
    }
}
