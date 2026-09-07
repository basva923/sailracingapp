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
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.app.ui.theme.SailRacingTheme
import com.sailracing.domain.strategy.TackAdvice
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
        compose.onNodeWithTag("advice").assertTextEquals("—")
        compose.onNodeWithTag("adviceDetail").assertTextEquals("WAITING FOR A HEADING")
        compose.onNodeWithTag("configuredWind").assertTextEquals("---")
        compose.onNodeWithTag("meanWind").assertTextEquals("---")
        compose.onNodeWithTag("estimatedWind").assertTextEquals("---")
        compose.onNodeWithTag("speed").assertTextEquals("---")
        compose.onNodeWithTag("tack").assertTextEquals("—")
        compose.onNodeWithTag("compassRose").assertIsDisplayed()
        compose.onNodeWithTag("statistics").performScrollTo().assertTextEquals("No upwind samples yet")
        compose.onNodeWithTag("histogramChart").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("historyChart").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("windFromStarboard").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("windFromPort").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("tackLabel").performScrollTo().assertTextEquals("—")
    }

    private fun sailing(advice: TackAdvice, title: String, shift: Double) = WindUiState(
        headingDegrees = 340.0, windDegrees = 20, meanWindDegrees = 25.0, estimatedWindDegrees = 25.0,
        configuredWind = "020°", windDirection = 20, meanWind = "025°", meanIsMeasured = true, estimatedWind = "025°",
        shift = "+5°", shiftDegrees = shift, heading = "340°", headingSource = "GPS course", speed = "5.8", vmg = "4.1",
        tack = Tack.STARBOARD, tackLabel = "Starboard upwind", tackShort = "Stbd", pointOfSailLabel = "Upwind",
        advice = advice, adviceTitle = title, adviceDetail = "Starboard is lifted 5° from the mean wind",
        histogram = (-40..40).map { HistogramBin(it, if (it == 5) 3 else 0) }, estimatedOffset = 5.0, setOffset = -5.0,
        shifts = listOf(2.0, 5.0, 4.0), statistics = "Mean 025° ±2° · 3 samples",
        averageUpwind = "5.5 kn", averageUpwindVmg = "3.9 kn", averageDownwind = "6.1 kn", averageDownwindVmg = "5.0 kn",
        raceTime = "Racing +01:12", canSetFromHeading = true,
    )

    @Test
    fun sailingStateAndTackButtons() {
        show(sailing(TackAdvice.HOLD, "HOLD", 5.0))
        compose.onNodeWithTag("raceTime").assertTextEquals("RACING +01:12")
        compose.onNodeWithTag("advice").assertTextEquals("HOLD")
        compose.onNodeWithTag("meanWind").assertTextEquals("025°")
        compose.onNodeWithTag("shift").assertTextEquals("+5°")
        compose.onNodeWithTag("tack").assertTextEquals("Stbd")
        compose.onNodeWithTag("speed").assertTextEquals("5.8")
        compose.onNodeWithTag("vmg").assertTextEquals("4.1")
        compose.onNodeWithTag("avgUpwind").performScrollTo().assertTextEquals("5.5 kn")
        compose.onNodeWithTag("avgDownwindVmg").performScrollTo().assertTextEquals("5.0 kn")
        compose.onNodeWithTag("tackLabel").performScrollTo().assertTextEquals("STARBOARD UPWIND")
        compose.onNodeWithTag("windFromStarboard").performScrollTo().performClick()
        compose.onNodeWithTag("windFromPort").performScrollTo().performClick()
        assertEquals(listOf("starboard", "port"), log)
    }

    @Test
    fun adviceAndShiftColours() {
        show(sailing(TackAdvice.TACK, "TACK", -5.0))
        compose.onNodeWithTag("advice").assertTextEquals("TACK")
        assertEquals(RaceColors.Warning, adviceColor(TackAdvice.TACK))
        assertEquals(RaceColors.Early, adviceColor(TackAdvice.HOLD))
        assertEquals(RaceColors.White, adviceColor(TackAdvice.EITHER))
        assertEquals(RaceColors.Muted, adviceColor(TackAdvice.UNKNOWN))
    }

    @Test
    fun eitherTackAndZeroShift() {
        show(sailing(TackAdvice.EITHER, "EITHER TACK", 0.0))
        compose.onNodeWithTag("advice").assertTextEquals("EITHER TACK")
        compose.onNodeWithTag("shift").assertTextEquals("+5°")
    }

    @Test
    fun editingValuesThroughDialogs() {
        show(WindUiState(windDirection = 355, tackAngle = 45, downwindAngle = 140))
        // Wind direction wraps around the compass: 355 + 10 -> 5.
        compose.onNodeWithTag("editWind").performScrollTo().performClick()
        compose.onNodeWithTag("stepUpBig").performClick()
        compose.onNodeWithTag("numberValue").assertTextEquals("5 °")
        repeat(6) { compose.onNodeWithTag("stepDown").performClick() }
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
        compose.onNodeWithText("Reset the statistics?").assertIsDisplayed()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(listOf("wind10", "tack75", "downwind150", "reset"), log)
    }
}
