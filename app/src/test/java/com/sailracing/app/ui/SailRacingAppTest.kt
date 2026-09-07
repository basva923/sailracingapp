package com.sailracing.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config
import com.sailracing.app.fakes.TestAppGraph
import com.sailracing.app.ui.theme.SailRacingTheme
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.timer.RacePhase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class SailRacingAppTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun navigatesBetweenScreensAndDrivesTheSession() {
        val graph = TestAppGraph()
        val viewModel = RaceViewModel(graph.raceSession, graph.repository)
        graph.startSession()
        compose.setContent { SailRacingTheme { SailRacingApp(viewModel = viewModel, versionName = "test") } }

        compose.onNodeWithTag("clock").assertIsDisplayed()
        compose.onNodeWithTag("start5").performScrollTo().performClick()
        graph.scheduler.runCurrent()
        assertEquals(RacePhase.COUNTDOWN, graph.raceSession.snapshot.value.phase)

        graph.sensors.events.tryEmit(RaceEvent.FixReceived(PositionFix(GeoPoint(51.14, 5.83), graph.clock.now, 3.0, 315.0, 3.0)))
        graph.scheduler.runCurrent()
        compose.waitForIdle()

        compose.onNodeWithTag("nav_WIND").performClick()
        compose.onNodeWithTag("configuredWind").assertIsDisplayed()
        compose.onNodeWithTag("windFromStarboard").performScrollTo().performClick()
        graph.scheduler.runCurrent()
        assertEquals(0, graph.raceSession.state.value.wind.settings.directionDegrees)

        compose.onNodeWithTag("nav_RACE").performClick()
        compose.onNodeWithTag("compassRose").assertIsDisplayed()
        compose.onNodeWithTag("windFromPort").performScrollTo().performClick()
        graph.scheduler.runCurrent()
        assertEquals(270, graph.raceSession.state.value.wind.settings.directionDegrees)

        compose.onNodeWithTag("nav_SETTINGS").performClick()
        compose.onNodeWithTag("beeps").performScrollTo().performClick()
        graph.scheduler.runCurrent()
        compose.waitForIdle()
        assertEquals(false, graph.fakeRepository.settings.value.race.cuePolicy.enabled)

        compose.onNodeWithTag("nav_START").performClick()
        compose.onNodeWithTag("clock").assertIsDisplayed()
        assertNotNull(viewModel.nowMillis())
        viewModel.endSession()
        assertTrue(!graph.raceSession.isRunning.value)
    }

    @Test
    fun viewModelForwardsEveryAction() {
        val graph = TestAppGraph()
        val viewModel = RaceViewModel(graph.raceSession, graph.repository)
        viewModel.ensureRunning()
        graph.scheduler.runCurrent()
        compose.waitForIdle()
        graph.scheduler.runCurrent()
        assertTrue(graph.raceSession.isRunning.value)

        graph.sensors.events.tryEmit(RaceEvent.FixReceived(PositionFix(GeoPoint(51.14, 5.83), graph.clock.now, 3.0, 315.0, 3.0)))
        graph.scheduler.runCurrent()
        viewModel.markPinEnd()
        viewModel.markBoatEnd()
        viewModel.setWindDirection(100)
        viewModel.setTackAngle(40)
        viewModel.setDownwindAngle(150)
        viewModel.startCountdown(4)
        viewModel.syncCountdown()
        graph.scheduler.runCurrent()
        val state = graph.raceSession.state.value
        assertTrue(state.startLine.isComplete)
        assertEquals(100, state.wind.settings.directionDegrees)
        assertEquals(40, state.wind.settings.tackAngleDegrees)
        assertEquals(150, state.wind.settings.downwindAngleDegrees)
        assertEquals(RacePhase.COUNTDOWN, graph.raceSession.snapshot.value.phase)

        viewModel.clearPinEnd()
        viewModel.clearBoatEnd()
        viewModel.setWindFromStarboardTack()
        viewModel.setWindFromPortTack()
        viewModel.resetWindStatistics()
        viewModel.resetSpeedStatistics()
        viewModel.stopTimer()
        viewModel.updateSettings { it.copy(vibrate = false) }
        graph.scheduler.runCurrent()
        compose.waitForIdle()
        graph.scheduler.runCurrent()
        val after = graph.raceSession.state.value
        assertTrue(!after.startLine.isComplete)
        assertEquals(RacePhase.SETUP, graph.raceSession.snapshot.value.phase)
        assertEquals(false, graph.fakeRepository.settings.value.vibrate)
        assertEquals(false, viewModel.settings.value.vibrate)
        val factory = RaceViewModel.Factory(graph)
        assertNotNull(factory.create(RaceViewModel::class.java))
    }
}
