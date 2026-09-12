package com.sailracing.app.ui.wind

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
import com.sailracing.app.ui.theme.SailRacingTheme
import com.sailracing.domain.strategy.TackAdvice
import com.sailracing.domain.wind.HistogramBin
import com.sailracing.domain.wind.Tack
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The wind in full: the numbers, the rose, the charts, and the buttons that set the wind and the angles. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

    private fun show(wind: WindUiState = WindUiState()) {
        compose.setContent { SailRacingTheme { WindScreen(wind = wind, actions = actions) } }
    }

    private val sailing = WindUiState(
        headingDegrees = 340.0, windDegrees = 20, meanWindDegrees = 25.0, estimatedWindDegrees = 25.0,
        configuredWind = "020°", windDirection = 20, meanWind = "025°", meanIsMeasured = true, estimatedWind = "025°",
        windNow = "025°", windNowGlance = "Wind: stbd 340° + 45°", windNowIsLive = true,
        tackAngleMeasured = "Measured tack angle 48° · set 45°",
        shift = "+5°", shiftDegrees = 5.0, shiftTitle = "Shift vs mean · + lift", heading = "340°", headingSource = "GPS course",
        speed = "5.8", speedDelta = "+0.3 vs avg 5.5", speedDeltaMps = 0.15, vmg = "4.1",
        tack = Tack.STARBOARD, tackLabel = "Starboard upwind", tackShort = "Stbd", pointOfSailLabel = "Upwind",
        advice = TackAdvice.HOLD, adviceTitle = "STAY", adviceDetail = "Starboard is lifted 5° from the mean wind", adviceGlance = "Stbd lifted 5°",
        histogram = (-40..40).map { HistogramBin(it, if (it == 5) 3 else 0) }, estimatedOffset = 5.0, setOffset = -5.0,
        shifts = listOf(2.0, 5.0, 4.0), statistics = "Mean 025° ±2° · 3 samples",
        averageUpwind = "5.5 kn", averageUpwindVmg = "3.9 kn", averageDownwind = "6.1 kn", averageDownwindVmg = "5.0 kn",
        raceTime = "Racing +01:12", canSetFromHeading = true,
    )

    @Test
    fun emptyState() {
        show()
        compose.onNodeWithTag("adviceTitle").assertIsDisplayed().assertTextEquals("—")
        compose.onNodeWithTag("adviceDetail").assertTextEquals("WAITING FOR A HEADING")
        compose.onNodeWithTag("configuredWind").assertTextEquals("---")
        compose.onNodeWithTag("meanWind").assertTextEquals("---")
        compose.onNodeWithTag("windNow").assertTextEquals("---")
        compose.onNodeWithTag("windNowGlance").assertTextEquals("SET WIND · NO HEADING")
        compose.onNodeWithTag("vmg").performScrollTo().assertTextEquals("---")
        compose.onNodeWithTag("heading").performScrollTo().assertTextEquals("---")
        compose.onNodeWithTag("compassRose").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("tackLabel").performScrollTo().assertTextEquals("—")
        compose.onNodeWithTag("avgUpwind").performScrollTo().assertTextEquals("---")
        compose.onNodeWithTag("windFromStarboard").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("windFromPort").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("tackAngleMeasured").performScrollTo()
            .assertTextEquals("TACK ANGLE NOT MEASURED YET: SAIL CLOSE-HAULED ON BOTH TACKS")
        compose.onNodeWithTag("statistics").performScrollTo().assertTextEquals("No upwind samples yet")
        compose.onNodeWithTag("histogramChart").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("historyChart").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sailing() {
        show(sailing)
        compose.onNodeWithTag("adviceTitle").assertTextEquals("STAY")
        compose.onNodeWithTag("adviceDetail").assertTextEquals("STARBOARD IS LIFTED 5° FROM THE MEAN WIND")
        compose.onNodeWithTag("configuredWind").assertTextEquals("020°")
        compose.onNodeWithTag("meanWind").assertTextEquals("025°")
        compose.onNodeWithTag("windNow").assertTextEquals("025°")
        compose.onNodeWithTag("windNowGlance").assertTextEquals("WIND: STBD 340° + 45°")
        compose.onNodeWithTag("vmg").performScrollTo().assertTextEquals("4.1")
        compose.onNodeWithTag("heading").performScrollTo().assertTextEquals("340°")
        compose.onNodeWithTag("tackLabel").performScrollTo().assertTextEquals("STARBOARD UPWIND")
        compose.onNodeWithTag("avgUpwind").performScrollTo().assertTextEquals("5.5 kn")
        compose.onNodeWithTag("avgUpwindVmg").performScrollTo().assertTextEquals("3.9 kn")
        compose.onNodeWithTag("avgDownwind").performScrollTo().assertTextEquals("6.1 kn")
        compose.onNodeWithTag("avgDownwindVmg").performScrollTo().assertTextEquals("5.0 kn")
        compose.onNodeWithTag("tackAngleMeasured").performScrollTo().assertTextEquals("MEASURED TACK ANGLE 48° · SET 45°")
        compose.onNodeWithTag("statistics").performScrollTo().assertTextEquals("Mean 025° ±2° · 3 samples")
        compose.onNodeWithTag("windFromStarboard").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("windFromPort").performScrollTo().performClick()
        assertEquals(listOf("starboard", "port"), log)
    }

    @Test
    fun settingTheWindThroughDialogs() {
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
        compose.onNodeWithTag("cancel").performClick()
        compose.onNodeWithTag("resetWind").performScrollTo().performClick()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(listOf("wind10", "tack75", "downwind150", "reset"), log)
    }

    /** On its side the numbers are on the left and the buttons and charts on the right, each pane scrolling. */
    @Test
    @Config(qualifiers = "w760dp-h360dp-land-xxhdpi")
    fun onItsSideThePanesSitSideBySide() {
        show(sailing)
        val advice = compose.onNodeWithTag("adviceTitle").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val starboard = compose.onNodeWithTag("windFromStarboard").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(advice.right <= starboard.left, "the numbers are left of the buttons: $advice, $starboard")
        compose.onNodeWithTag("historyChart").performScrollTo().assertIsDisplayed()
    }
}
