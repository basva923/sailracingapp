package com.sailracing.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sailracing.app.ui.map.MapActions
import com.sailracing.app.ui.map.MapScreen
import com.sailracing.app.ui.session.SessionActions
import com.sailracing.app.ui.session.SessionScreen
import com.sailracing.app.ui.settings.SettingsActions
import com.sailracing.app.ui.settings.SettingsScreen
import com.sailracing.app.ui.start.StartActions
import com.sailracing.app.ui.start.StartScreen
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.app.ui.wind.WindActions
import com.sailracing.app.ui.wind.WindScreen
import com.sailracing.domain.timer.RacePhase

/** The app's top-level destinations. */
enum class Screen(val label: String, val icon: ImageVector) {
    SESSION("Session", Icons.Filled.Sailing),
    START("Start", Icons.Filled.Timer),
    WIND("Wind", Icons.Filled.Air),
    MAP("Map", Icons.Filled.Map),
    SETTINGS("Settings", Icons.Filled.Settings),
}

/**
 * Root of the UI: bottom navigation between the five screens, all fed by one view model. It opens on the
 * Session screen, where a session is started. At the starting gun the app moves to the wind screen by
 * itself: the start is over, the beat begins.
 */
@Composable
fun SailRacingApp(viewModel: RaceViewModel, versionName: String = "", initialScreen: Screen = Screen.SESSION) {
    var screen by rememberSaveable { mutableStateOf(initialScreen) }
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    var previousPhase by rememberSaveable { mutableStateOf(phase) }
    LaunchedEffect(phase) {
        if (previousPhase == RacePhase.COUNTDOWN && phase == RacePhase.RACING) screen = Screen.WIND
        previousPhase = phase
    }

    Scaffold(
        containerColor = RaceColors.Black,
        bottomBar = {
            NavigationBar(containerColor = RaceColors.Black, tonalElevation = NavigationBarDefaults.Elevation) {
                Screen.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = screen == destination,
                        onClick = { screen = destination },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        modifier = Modifier.testTag("nav_${destination.name}"),
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RaceColors.Black,
                            selectedTextColor = RaceColors.White,
                            indicatorColor = RaceColors.Info,
                            unselectedIconColor = RaceColors.Muted,
                            unselectedTextColor = RaceColors.Muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.fillMaxSize().padding(padding)
        when (screen) {
            Screen.SESSION -> {
                val state by viewModel.sessionUiState.collectAsStateWithLifecycle()
                SessionScreen(
                    state = state,
                    actions = remember(viewModel) {
                        SessionActions(
                            start = viewModel::startSession,
                            end = viewModel::endSession,
                            clear = viewModel::clearSession,
                        )
                    },
                    modifier = content,
                )
            }
            Screen.START -> {
                val state by viewModel.startUiState.collectAsStateWithLifecycle()
                StartScreen(
                    state = state,
                    actions = remember(viewModel) {
                        StartActions(
                            markPin = viewModel::markPinEnd,
                            clearPin = viewModel::clearPinEnd,
                            markBoat = viewModel::markBoatEnd,
                            clearBoat = viewModel::clearBoatEnd,
                            startCountdown = viewModel::startCountdown,
                            sync = viewModel::syncCountdown,
                            stop = viewModel::stopTimer,
                        )
                    },
                    modifier = content,
                )
            }
            Screen.WIND -> {
                val state by viewModel.windUiState.collectAsStateWithLifecycle()
                WindScreen(
                    state = state,
                    actions = remember(viewModel) {
                        WindActions(
                            setWindDirection = viewModel::setWindDirection,
                            setTackAngle = viewModel::setTackAngle,
                            setDownwindAngle = viewModel::setDownwindAngle,
                            windFromStarboard = viewModel::setWindFromStarboardTack,
                            windFromPort = viewModel::setWindFromPortTack,
                            resetStatistics = viewModel::resetStatistics,
                        )
                    },
                    modifier = content,
                )
            }
            Screen.MAP -> {
                val state by viewModel.mapUiState.collectAsStateWithLifecycle()
                MapScreen(
                    state = state,
                    actions = remember(viewModel) {
                        MapActions(
                            clearTrack = viewModel::clearTrack,
                            markHere = viewModel::markWindwardMark,
                            setMarkFromLine = viewModel::setWindwardMarkFromLine,
                            clearMark = viewModel::clearWindwardMark,
                        )
                    },
                    modifier = content,
                )
            }
            Screen.SETTINGS -> {
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                SettingsScreen(
                    settings = settings,
                    actions = remember(viewModel) { SettingsActions(update = viewModel::updateSettings) },
                    modifier = content,
                    versionName = versionName,
                )
            }
        }
    }
}
