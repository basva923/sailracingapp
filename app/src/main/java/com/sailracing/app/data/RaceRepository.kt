package com.sailracing.app.data

import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.timer.TimerState
import com.sailracing.domain.wind.WindSettings
import kotlinx.coroutines.flow.Flow

/** Persistence for settings and race data. */
interface RaceRepository {
    val settings: Flow<AppSettings>
    val persistedRace: Flow<PersistedRace>

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings)
    suspend fun saveStartLine(line: StartLine)
    suspend fun saveWindwardMark(mark: GeoPoint?)
    suspend fun saveWind(wind: WindSettings)
    suspend fun saveTimer(timer: TimerState)
}
