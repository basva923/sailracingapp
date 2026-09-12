package com.sailracing.app.ui.start

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config
import com.sailracing.app.ui.theme.SailRacingTheme
import com.sailracing.domain.timer.RacePhase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class StartScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val log = mutableListOf<String>()
    private val actions = StartActions(
        markPin = { log += "markPin" },
        clearPin = { log += "clearPin" },
        markBoat = { log += "markBoat" },
        clearBoat = { log += "clearBoat" },
        startCountdown = { log += "start$it" },
        sync = { log += "sync" },
        stop = { log += "stop" },
    )

    private fun show(state: StartUiState) {
        compose.setContent { SailRacingTheme { StartScreen(state = state, actions = actions) } }
    }

    @Test
    fun idleStateShowsPlaceholdersAndDisablesTimerControls() {
        show(StartUiState(clock = "13:07"))
        compose.onNodeWithTag("clock").assertTextEquals("13:07")
        compose.onNodeWithTag("timeToKill").assertTextEquals("---")
        compose.onNodeWithTag("distanceToLine").assertTextEquals("---")
        compose.onNodeWithTag("gpsStatus").assertTextEquals("No GPS")
        compose.onNodeWithTag("pinButton").assertIsNotEnabled()
        compose.onNodeWithTag("boatButton").assertIsNotEnabled()
        compose.onNodeWithTag("sync").assertIsNotEnabled()
        compose.onNodeWithTag("stop").assertIsNotEnabled()
        compose.onNodeWithTag("start5").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("start4").performScrollTo().performClick()
        compose.onNodeWithTag("start1").performScrollTo().performClick()
        assertEquals(listOf("start5", "start4", "start1"), log)
    }

    @Test
    fun markingTheLine() {
        show(StartUiState(canMark = true, gpsStatus = "GPS ±3 m", gpsOk = true))
        compose.onNodeWithTag("pinButton").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("boatButton").performScrollTo().performClick()
        assertEquals(listOf("markPin", "markBoat"), log)
    }

    @Test
    fun aSetEndAsksBeforeMovingOrClearing() {
        show(StartUiState(canMark = true, pinSet = true, boatSet = true, lineLength = "Line 100 m"))
        compose.onNodeWithTag("lineLength").assertTextEquals("LINE 100 M")
        compose.onNodeWithTag("pinButton").performScrollTo().performClick()
        compose.onNodeWithText("Re-mark the pin end?").assertIsDisplayed()
        compose.onNodeWithTag("confirm").performClick()
        compose.onNodeWithTag("boatButton").performScrollTo().performClick()
        compose.onNodeWithTag("cancel").performClick()
        assertEquals(listOf("markPin", "clearBoat"), log)
    }

    @Test
    fun countdownStateAndStopConfirmation() {
        show(
            StartUiState(
                phase = RacePhase.COUNTDOWN,
                clockLabel = "Time to start",
                clock = "04:37",
                timeToKill = "+12 s",
                urgency = Urgency.EARLY,
                distanceToLine = "-85 m",
                overLine = true,
                overEarly = true,
            ),
        )
        compose.onNodeWithTag("clock").assertTextEquals("04:37")
        compose.onNodeWithTag("timeToKill").assertTextEquals("+12 s")
        compose.onNodeWithTag("overEarly").assertIsDisplayed()
        compose.onNodeWithTag("sync").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("stop").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Stop the timer?").assertIsDisplayed()
        compose.onNodeWithTag("cancel").performClick()
        compose.onNodeWithTag("stop").performScrollTo().performClick()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(listOf("sync", "stop"), log)
    }

    @Test
    fun lateStateIsShown() {
        show(StartUiState(phase = RacePhase.COUNTDOWN, timeToKill = "-4 s", urgency = Urgency.LATE))
        compose.onNodeWithTag("timeToKill").assertTextEquals("-4 s")
    }
}
