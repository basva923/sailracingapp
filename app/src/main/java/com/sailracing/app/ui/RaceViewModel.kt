package com.sailracing.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sailracing.app.data.AppSettings
import com.sailracing.app.data.RaceRepository
import com.sailracing.app.di.AppGraph
import com.sailracing.app.race.RaceSession
import com.sailracing.app.ui.map.MapUiState
import com.sailracing.app.ui.session.SessionUiState
import com.sailracing.app.ui.start.StartUiState
import com.sailracing.app.ui.wind.WindUiState
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.race.RaceSnapshot
import com.sailracing.domain.timer.RacePhase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Thin adapter between the UI and the app-wide [RaceSession]; every action is one event. */
class RaceViewModel(private val session: RaceSession, private val repository: RaceRepository) : ViewModel() {

    val snapshot: StateFlow<RaceSnapshot> = session.snapshot
    val settings: StateFlow<AppSettings> = session.settings
    val isRunning: StateFlow<Boolean> = session.isRunning

    // Display states are derived here, once, so screens only recompose when visible text changes
    // (a StateFlow drops equal values) even though snapshots arrive several times a second.
    val startUiState: StateFlow<StartUiState> = derived(StartUiState::from)
    val windUiState: StateFlow<WindUiState> = derived(WindUiState::from)
    val mapUiState: StateFlow<MapUiState> = derived(MapUiState::from)
    val phase: StateFlow<RacePhase> = derived { it.phase }
    val sessionUiState: StateFlow<SessionUiState> = combine(session.snapshot, session.isRunning, session.startedAtMillis, session.settings) {
        snapshot, running, startedAt, settings -> SessionUiState.from(snapshot, running, startedAt, settings.simulation.enabled)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        SessionUiState.from(session.snapshot.value, session.isRunning.value, session.startedAtMillis.value, session.settings.value.simulation.enabled),
    )

    private fun <T> derived(transform: (RaceSnapshot) -> T): StateFlow<T> =
        session.snapshot.map(transform).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), transform(session.snapshot.value))

    fun nowMillis(): Long = session.nowMillis()

    // Session: started and ended by the sailor; clearing forgets everything measured and marked.
    fun startSession() {
        viewModelScope.launch { session.start() }
    }

    fun endSession() = session.stop()
    fun clearSession() = session.dispatch(RaceEvent.ClearSession)

    // Start line
    fun markPinEnd() = session.dispatch(RaceEvent.MarkPinEnd)
    fun markBoatEnd() = session.dispatch(RaceEvent.MarkBoatEnd)
    fun clearPinEnd() = session.dispatch(RaceEvent.ClearPinEnd)
    fun clearBoatEnd() = session.dispatch(RaceEvent.ClearBoatEnd)

    // Countdown. A countdown needs the ticker for its beeps, so it starts the session if the sailor forgot to.
    fun startCountdown(minutes: Int) {
        viewModelScope.launch {
            session.start()
            session.startCountdown(minutes)
        }
    }
    fun syncCountdown() = session.syncCountdown()
    fun stopTimer() = session.dispatch(RaceEvent.StopTimer)

    // Wind
    fun setWindDirection(degrees: Int) = session.dispatch(RaceEvent.SetWindDirection(degrees))
    fun setTackAngle(degrees: Int) = session.dispatch(RaceEvent.SetTackAngle(degrees))
    fun setDownwindAngle(degrees: Int) = session.dispatch(RaceEvent.SetDownwindAngle(degrees))
    fun setWindFromPortTack() = session.dispatch(RaceEvent.SetWindFromPortTack)
    fun setWindFromStarboardTack() = session.dispatch(RaceEvent.SetWindFromStarboardTack)
    fun resetStatistics() {
        session.dispatch(RaceEvent.ResetWindStatistics)
        session.dispatch(RaceEvent.ResetSpeedStatistics)
    }

    // Map
    fun clearTrack() = session.dispatch(RaceEvent.ClearTrack)
    fun markWindwardMark() = session.dispatch(RaceEvent.MarkWindwardMark)
    fun setWindwardMarkFromLine(bearingDegrees: Int, distanceMeters: Double) =
        session.dispatch(RaceEvent.SetWindwardMarkFromLine(bearingDegrees, distanceMeters))
    fun clearWindwardMark() = session.dispatch(RaceEvent.SetWindwardMark(null))

    // Settings
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.updateSettings(transform) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }

    class Factory(private val graph: AppGraph) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = RaceViewModel(graph.raceSession, graph.repository) as T
    }
}
