package com.sailracing.app.ui.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sailracing.app.ui.theme.SailRacingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class SessionScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val log = mutableListOf<String>()
    private val actions = SessionActions(start = { log += "start" }, end = { log += "end" }, clear = { log += "clear" })

    private fun show(state: SessionUiState) {
        compose.setContent { SailRacingTheme { SessionScreen(state = state, actions = actions) } }
    }

    @Test
    fun noSessionOffersOnlyStart() {
        show(SessionUiState())
        compose.onNodeWithTag("sessionTitle").assertTextEquals("NO SESSION")
        compose.onNodeWithTag("sessionGps").assertTextEquals("GPS off")
        compose.onNodeWithTag("sessionDetail").assertTextEquals("START A SESSION TO SWITCH ON THE GPS AND BEGIN MEASURING THE WIND")
        compose.onNodeWithTag("sessionSince").assertTextEquals("SINCE ---")
        compose.onNodeWithTag("sessionTrack").assertTextEquals("No track yet")
        compose.onNodeWithTag("clearSession").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("startSession").performScrollTo().performClick()
        assertEquals(listOf("start"), log)
    }

    @Test
    fun aRunningSessionCanBeEndedAndCleared() {
        show(
            SessionUiState(
                status = SessionStatus.RUNNING, hasData = true, title = "SESSION RUNNING", detail = "GPS on since 10:32",
                gpsStatus = "GPS ±3 m", gpsOk = true, since = "10:32", track = "300 points · 5 min",
                wind = "120 upwind samples · mean 023°", line = "Set · 100 m", mark = "Set", timer = "Start in 04:12",
            ),
        )
        compose.onNodeWithTag("sessionTitle").assertTextEquals("SESSION RUNNING")
        compose.onNodeWithTag("sessionDetail").assertTextEquals("GPS ON SINCE 10:32")
        compose.onNodeWithTag("sessionWind").assertTextEquals("120 upwind samples · mean 023°")
        compose.onNodeWithTag("sessionLine").assertTextEquals("Set · 100 m")
        compose.onNodeWithTag("sessionMark").assertTextEquals("Set")
        compose.onNodeWithTag("sessionTimer").assertTextEquals("Start in 04:12")

        compose.onNodeWithTag("endSession").performScrollTo().performClick()
        compose.onNodeWithText("End the session?").assertIsDisplayed()
        compose.onNodeWithTag("cancel").performClick()
        compose.onNodeWithTag("endSession").performScrollTo().performClick()
        compose.onNodeWithTag("confirm").performClick()

        compose.onNodeWithTag("clearSession").performScrollTo().performClick()
        compose.onNodeWithText("Clear the session?").assertIsDisplayed()
        compose.onNodeWithTag("cancel").performClick()
        compose.onNodeWithTag("clearSession").performScrollTo().performClick()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(listOf("end", "clear"), log)
    }

    @Test
    fun anEndedSessionOffersStartAndClear() {
        show(SessionUiState(status = SessionStatus.ENDED, hasData = true, title = "SESSION ENDED"))
        compose.onNodeWithTag("sessionTitle").assertTextEquals("SESSION ENDED")
        compose.onNodeWithTag("startSession").performScrollTo().performClick()
        compose.onNodeWithTag("clearSession").performScrollTo().performClick()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(listOf("start", "clear"), log)
    }
}
