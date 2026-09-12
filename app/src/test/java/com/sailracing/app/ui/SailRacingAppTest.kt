package com.sailracing.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.sailracing.app.fakes.TestAppGraph
import com.sailracing.app.ui.session.SessionStatus
import com.sailracing.app.ui.theme.SailRacingTheme
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.timer.RacePhase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
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

        // The app opens on the session screen.
        compose.onNodeWithTag("sessionTitle").assertTextEquals("SESSION RUNNING")
        compose.onNodeWithTag("nav_START").performClick()
        compose.onNodeWithTag("clock").assertIsDisplayed()
        compose.onNodeWithTag("start5").performScrollTo().performClick()
        graph.scheduler.runCurrent()
        assertEquals(RacePhase.COUNTDOWN, graph.raceSession.snapshot.value.phase)

        graph.sensors.events.tryEmit(RaceEvent.FixReceived(PositionFix(GeoPoint(51.14, 5.83), graph.clock.now, 3.0, 315.0, 3.0)))
        graph.scheduler.runCurrent()
        compose.waitForIdle()

        // The race screen: the big numbers and nothing to press.
        compose.onNodeWithTag("nav_RACE").performClick()
        compose.onNodeWithTag("advice").assertIsDisplayed()
        compose.onNodeWithTag("speed").assertIsDisplayed()
        compose.onNodeWithTag("courseMap").assertDoesNotExist()

        // The wind is set on the wind screen.
        compose.onNodeWithTag("nav_WIND").performClick()
        compose.onNodeWithTag("configuredWind").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("windFromStarboard").performScrollTo().performClick()
        graph.scheduler.runCurrent()
        assertEquals(0, graph.raceSession.state.value.wind.settings.directionDegrees)

        compose.onNodeWithTag("compassRose").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("windFromPort").performScrollTo().performClick()
        graph.scheduler.runCurrent()
        assertEquals(270, graph.raceSession.state.value.wind.settings.directionDegrees)

        // The mark and the track on the map screen.
        compose.onNodeWithTag("nav_MAP").performClick()
        compose.onNodeWithTag("courseMap").assertIsDisplayed()
        compose.onNodeWithTag("markHere").performScrollTo().performClick()
        graph.scheduler.runCurrent()
        assertEquals(GeoPoint(51.14, 5.83), graph.raceSession.state.value.windwardMark)
        compose.onNodeWithTag("clearTrack").performScrollTo().performClick()
        compose.onNodeWithTag("confirm").performClick()
        graph.scheduler.runCurrent()
        assertTrue(graph.raceSession.state.value.track.isEmpty)

        compose.onNodeWithTag("nav_SETTINGS").performClick()
        compose.onNodeWithTag("beeps").performScrollTo().performClick()
        graph.scheduler.runCurrent()
        compose.waitForIdle()
        assertEquals(false, graph.fakeRepository.settings.value.race.cuePolicy.enabled)

        // The session screen ends, restarts and clears the session.
        compose.onNodeWithTag("nav_SESSION").performClick()
        compose.onNodeWithTag("endSession").performScrollTo().performClick()
        compose.onNodeWithTag("confirm").performClick()
        compose.waitForIdle()
        assertFalse(graph.raceSession.isRunning.value)
        compose.onNodeWithTag("sessionTitle").assertTextEquals("SESSION ENDED")
        compose.onNodeWithTag("startSession").performScrollTo().performClick()
        graph.scheduler.runCurrent()
        compose.waitForIdle()
        assertTrue(graph.raceSession.isRunning.value)
        compose.onNodeWithTag("clearSession").performScrollTo().performClick()
        compose.onNodeWithTag("confirm").performClick()
        graph.scheduler.runCurrent()
        compose.waitForIdle()
        assertNull(graph.raceSession.state.value.windwardMark)
        assertTrue(graph.raceSession.state.value.wind.histogram.isEmpty)
        assertEquals(RacePhase.SETUP, graph.raceSession.snapshot.value.phase)

        compose.onNodeWithTag("nav_START").performClick()
        compose.onNodeWithTag("clock").assertIsDisplayed()
        assertNotNull(viewModel.nowMillis())
        viewModel.endSession()
        assertTrue(!graph.raceSession.isRunning.value)
    }

    @Test
    fun aCountdownStartsTheSessionWhenTheSailorForgot() {
        val graph = TestAppGraph()
        val viewModel = RaceViewModel(graph.raceSession, graph.repository)
        compose.setContent { SailRacingTheme { SailRacingApp(viewModel = viewModel, versionName = "test") } }
        compose.onNodeWithTag("sessionTitle").assertTextEquals("NO SESSION")
        compose.onNodeWithTag("nav_START").performClick()
        compose.onNodeWithTag("start4").performScrollTo().performClick()
        graph.scheduler.runCurrent()
        compose.waitForIdle()
        graph.scheduler.runCurrent()
        assertTrue(graph.raceSession.isRunning.value)
        assertEquals(RacePhase.COUNTDOWN, graph.raceSession.snapshot.value.phase)
        compose.onNodeWithTag("nav_SESSION").performClick()
        compose.onNodeWithTag("sessionTitle").assertTextEquals("SESSION RUNNING")
        assertEquals(SessionStatus.RUNNING, viewModel.sessionUiState.value.status)
        viewModel.endSession()
    }

    @Test
    fun viewModelForwardsEveryAction() {
        val graph = TestAppGraph()
        val viewModel = RaceViewModel(graph.raceSession, graph.repository)
        viewModel.startSession()
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
        viewModel.markWindwardMark()
        viewModel.startCountdown(4)
        viewModel.syncCountdown()
        graph.scheduler.runCurrent()
        compose.waitForIdle()
        graph.scheduler.runCurrent()
        val state = graph.raceSession.state.value
        assertTrue(state.startLine.isComplete)
        assertEquals(GeoPoint(51.14, 5.83), state.windwardMark)
        assertEquals(100, state.wind.settings.directionDegrees)
        assertEquals(40, state.wind.settings.tackAngleDegrees)
        assertEquals(150, state.wind.settings.downwindAngleDegrees)
        assertEquals(RacePhase.COUNTDOWN, graph.raceSession.snapshot.value.phase)

        viewModel.clearPinEnd()
        viewModel.clearBoatEnd()
        viewModel.setWindFromStarboardTack()
        viewModel.setWindFromPortTack()
        viewModel.resetStatistics()
        viewModel.stopTimer()
        viewModel.updateSettings { it.copy(vibrate = false) }
        viewModel.clearTrack()
        viewModel.setWindwardMarkFromLine(0, 500.0)
        graph.scheduler.runCurrent()
        assertNotNull(graph.raceSession.state.value.windwardMark)
        viewModel.clearWindwardMark()
        graph.scheduler.runCurrent()
        compose.waitForIdle()
        graph.scheduler.runCurrent()
        val after = graph.raceSession.state.value
        assertTrue(!after.startLine.isComplete)
        assertTrue(after.track.isEmpty)
        assertNull(after.windwardMark)
        assertTrue(after.wind.histogram.isEmpty)
        assertEquals(0, after.speedStats.upwind.count)
        assertEquals(RacePhase.SETUP, graph.raceSession.snapshot.value.phase)
        assertEquals(false, graph.fakeRepository.settings.value.vibrate)
        assertEquals(false, viewModel.settings.value.vibrate)
        assertEquals(RacePhase.SETUP, viewModel.phase.value)
        viewModel.clearSession()
        graph.scheduler.runCurrent()
        // Clearing forgets the line but keeps the set wind.
        assertNull(graph.raceSession.state.value.startLine.pinEnd)
        assertEquals(after.wind.settings, graph.raceSession.state.value.wind.settings)
        assertEquals(275, graph.raceSession.state.value.wind.settings.directionDegrees)
        val factory = RaceViewModel.Factory(graph)
        assertNotNull(factory.create(RaceViewModel::class.java))
    }

    @Test
    fun movesToTheRaceScreenAtTheGun() {
        val graph = TestAppGraph()
        val viewModel = RaceViewModel(graph.raceSession, graph.repository)
        graph.startSession()
        compose.setContent { SailRacingTheme { SailRacingApp(viewModel = viewModel, versionName = "test", initialScreen = Screen.START) } }

        compose.onNodeWithTag("start1").performScrollTo().performClick()
        graph.scheduler.runCurrent()
        compose.waitForIdle()
        compose.onNodeWithTag("clock").assertIsDisplayed()

        // The gun: the next tick sees the race running and the app shows the race screen.
        graph.clock.now += 61_000
        graph.scheduler.advanceTimeBy(300)
        graph.scheduler.runCurrent()
        compose.waitForIdle()
        assertEquals(RacePhase.RACING, graph.raceSession.snapshot.value.phase)
        compose.onNodeWithTag("advice").assertIsDisplayed()

        // It only happens at the gun: the sailor can go back to the start screen and stay there.
        compose.onNodeWithTag("nav_START").performClick()
        compose.onNodeWithTag("clock").assertIsDisplayed()
        graph.clock.now += 1_000
        graph.scheduler.advanceTimeBy(300)
        graph.scheduler.runCurrent()
        compose.waitForIdle()
        compose.onNodeWithTag("clock").assertIsDisplayed()
    }
}
