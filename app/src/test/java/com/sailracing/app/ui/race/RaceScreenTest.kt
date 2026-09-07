package com.sailracing.app.ui.race

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config
import com.sailracing.app.ui.theme.SailRacingTheme
import com.sailracing.domain.wind.Tack
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class RaceScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val log = mutableListOf<String>()
    private val actions = RaceActions(windFromStarboard = { log += "starboard" }, windFromPort = { log += "port" })

    private fun show(state: RaceUiState) {
        compose.setContent { SailRacingTheme { RaceScreen(state = state, actions = actions) } }
    }

    @Test
    fun emptyState() {
        show(RaceUiState())
        compose.onNodeWithTag("steer").assertTextEquals("Waiting for heading")
        compose.onNodeWithTag("speed").assertTextEquals("---")
        compose.onNodeWithTag("compassRose").assertIsDisplayed()
        compose.onNodeWithTag("windFromStarboard").assertIsNotEnabled()
    }

    private fun sailing(steer: Steer) = RaceUiState(
        headingDegrees = 317.0, windDegrees = 0, estimatedWindDegrees = 2.0, heading = "317°", target = "315°",
        speed = "5.8", vmg = "4.1", steer = steer, steerText = "On target", shift = "+2°", tack = Tack.STARBOARD,
        tackLabel = "Starboard upwind", averageUpwind = "5.5 kn", averageUpwindVmg = "3.9 kn",
        averageDownwind = "6.1 kn", averageDownwindVmg = "5.0 kn", raceTime = "Racing +01:12", canSetFromHeading = true,
    )

    @Test
    fun sailingStateAndButtons() {
        show(sailing(Steer.ON_TARGET))
        compose.onNodeWithTag("raceTime").assertTextEquals("RACING +01:12")
        compose.onNodeWithTag("heading").assertTextEquals("317°")
        compose.onNodeWithTag("avgUpwind").performScrollTo().assertTextEquals("5.5 kn")
        compose.onNodeWithTag("windFromStarboard").performScrollTo().performClick()
        compose.onNodeWithTag("windFromPort").performScrollTo().performClick()
        assertEquals(listOf("starboard", "port"), log)
    }

    @Test
    fun steeringHintsUseTheWarningColour() {
        show(sailing(Steer.HEAD_UP))
        compose.onNodeWithTag("steer").assertTextEquals("On target")
    }

    @Test
    fun bearAwayHint() {
        show(sailing(Steer.BEAR_AWAY))
        compose.onNodeWithTag("steer").assertIsDisplayed()
    }
}
