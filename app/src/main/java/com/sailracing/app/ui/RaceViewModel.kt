package com.sailracing.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sailracing.app.data.AppSettings
import com.sailracing.app.data.RaceRepository
import com.sailracing.app.di.AppGraph
import com.sailracing.app.race.RaceSession
import com.sailracing.app.ui.race.RaceUiState
import com.sailracing.app.ui.start.StartUiState
import com.sailracing.app.ui.wind.WindUiState
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.race.RaceSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    val raceUiState: StateFlow<RaceUiState> = derived(RaceUiState::from)

    private fun <T> derived(transform: (RaceSnapshot) -> T): StateFlow<T> =
        session.snapshot.map(transform).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), transform(session.snapshot.value))

    fun nowMillis(): Long = session.nowMillis()

    fun ensureRunning() {
        viewModelScope.launch { session.start() }
    }

    fun endSession() = session.stop()

    // Start line
    fun markPinEnd() = session.dispatch(RaceEvent.MarkPinEnd)
    fun markBoatEnd() = session.dispatch(RaceEvent.MarkBoatEnd)
    fun clearPinEnd() = session.dispatch(RaceEvent.ClearPinEnd)
    fun clearBoatEnd() = session.dispatch(RaceEvent.ClearBoatEnd)

    // Countdown
    fun startCountdown(minutes: Int) = session.startCountdown(minutes)
    fun syncCountdown() = session.syncCountdown()
    fun stopTimer() = session.dispatch(RaceEvent.StopTimer)

    // Wind
    fun setWindDirection(degrees: Int) = session.dispatch(RaceEvent.SetWindDirection(degrees))
    fun setTackAngle(degrees: Int) = session.dispatch(RaceEvent.SetTackAngle(degrees))
    fun setDownwindAngle(degrees: Int) = session.dispatch(RaceEvent.SetDownwindAngle(degrees))
    fun setWindFromPortTack() = session.dispatch(RaceEvent.SetWindFromPortTack)
    fun setWindFromStarboardTack() = session.dispatch(RaceEvent.SetWindFromStarboardTack)
    fun resetWindStatistics() = session.dispatch(RaceEvent.ResetWindStatistics)
    fun resetSpeedStatistics() = session.dispatch(RaceEvent.ResetSpeedStatistics)

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
