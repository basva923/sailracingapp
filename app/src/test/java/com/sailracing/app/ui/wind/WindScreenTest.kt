package com.sailracing.app.ui.wind

import androidx.compose.ui.test.assertIsDisplayed
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
import com.sailracing.domain.wind.HistogramBin
import com.sailracing.domain.wind.Tack
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class WindScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val log = mutableListOf<String>()
    private val actions = WindActions(
        setWindDirection = { log += "wind$it" },
        setTackAngle = { log += "tack$it" },
        setDownwindAngle = { log += "downwind$it" },
        windFromStarboard = { log += "starboard" },
        windFromPort = { log += "port" },
        resetStatistics = { log += "reset" },
    )

    private fun show(state: WindUiState) {
        compose.setContent { SailRacingTheme { WindScreen(state = state, actions = actions) } }
    }

    @Test
    fun emptyState() {
        show(WindUiState())
        compose.onNodeWithTag("configuredWind").assertTextEquals("---")
        compose.onNodeWithTag("estimatedWind").assertTextEquals("---")
        compose.onNodeWithTag("tack").assertTextEquals("—")
        compose.onNodeWithTag("statistics").assertTextEquals("No upwind samples yet")
        compose.onNodeWithTag("histogramChart").assertIsDisplayed()
        compose.onNodeWithTag("historyChart").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("windFromStarboard").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("windFromPort").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun sailingStateAndTackButtons() {
        show(
            WindUiState(
                configuredWind = "020°", windDirection = 20, estimatedWind = "025°", shift = "+5°", shiftDegrees = 5.0,
                heading = "340°", headingSource = "GPS course", speed = "5.8 kn", tack = Tack.STARBOARD, tackLabel = "Starboard upwind",
                tackShort = "Stbd", pointOfSailLabel = "Upwind",
                histogram = (-40..40).map { HistogramBin(it, if (it == 5) 3 else 0) }, estimatedOffset = 5.0,
                shifts = listOf(2.0, 5.0, 4.0), statistics = "Mean 025° ±2° · 3 samples", canSetFromHeading = true,
            ),
        )
        compose.onNodeWithTag("shift").assertTextEquals("+5°")
        compose.onNodeWithTag("tack").assertTextEquals("Stbd")
        compose.onNodeWithTag("windFromStarboard").performScrollTo().performClick()
        compose.onNodeWithTag("windFromPort").performScrollTo().performClick()
        assertEquals(listOf("starboard", "port"), log)
    }

    @Test
    fun backedShiftUsesTheOtherColourPath() {
        show(WindUiState(shift = "-5°", shiftDegrees = -5.0))
        compose.onNodeWithTag("shift").assertTextEquals("-5°")
    }

    @Test
    fun zeroShift() {
        show(WindUiState(shift = "0°", shiftDegrees = 0.0))
        compose.onNodeWithTag("shift").assertTextEquals("0°")
    }

    @Test
    fun editingValuesThroughDialogs() {
        show(WindUiState(windDirection = 355, tackAngle = 45, downwindAngle = 140))
        // Wind direction wraps around the compass: 355 + 10 -> 5.
        compose.onNodeWithTag("editWind").performScrollTo().performClick()
        compose.onNodeWithTag("stepUpBig").performClick()
        compose.onNodeWithTag("numberValue").assertTextEquals("5 °")
        compose.onNodeWithTag("stepDown").performClick()
        compose.onNodeWithTag("stepDown").performClick()
        compose.onNodeWithTag("stepDown").performClick()
        compose.onNodeWithTag("stepDown").performClick()
        compose.onNodeWithTag("stepDown").performClick()
        compose.onNodeWithTag("stepDown").performClick()
        compose.onNodeWithTag("numberValue").assertTextEquals("359 °")
        compose.onNodeWithTag("stepUp").performClick()
        compose.onNodeWithTag("stepUpBig").performClick()
        compose.onNodeWithTag("confirm").performClick()

        // Tack angle clamps to its range: 45 + 5 * 8 = 85 -> 80.
        compose.onNodeWithTag("editTackAngle").performScrollTo().performClick()
        repeat(8) { compose.onNodeWithTag("stepUpBig").performClick() }
        compose.onNodeWithTag("numberValue").assertTextEquals("80 °")
        compose.onNodeWithTag("stepDownBig").performClick()
        compose.onNodeWithTag("confirm").performClick()

        compose.onNodeWithTag("editDownwindAngle").performScrollTo().performClick()
        compose.onNodeWithTag("stepUpBig").performClick()
        compose.onNodeWithTag("stepUpBig").performClick()
        compose.onNodeWithTag("confirm").performClick()

        compose.onNodeWithTag("editWind").performScrollTo().performClick()
        compose.onNodeWithTag("stepDownBig").performClick()
        compose.onNodeWithTag("cancel").performClick()

        compose.onNodeWithTag("resetWind").performScrollTo().performClick()
        compose.onNodeWithText("Reset wind statistics?").assertIsDisplayed()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(listOf("wind10", "tack75", "downwind150", "reset"), log)
    }
}
