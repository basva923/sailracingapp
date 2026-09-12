package com.sailracing.app.ui.race

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sailracing.app.ui.components.adviceColor
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.app.ui.theme.SailRacingTheme
import com.sailracing.app.ui.wind.WindUiState
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

/**
 * The one screen raced by: the word, the speed, the heading and the shift, all on the screen without a
 * scroll, and nothing else - the map and the wind have screens of their own.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RaceScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(wind: WindUiState = WindUiState()) {
        compose.setContent { SailRacingTheme { RaceScreen(wind = wind) } }
    }

    private fun sailing(advice: TackAdvice, title: String, shift: Double) = WindUiState(
        headingDegrees = 340.0, windDegrees = 20, meanWindDegrees = 25.0, estimatedWindDegrees = 25.0,
        configuredWind = "020°", windDirection = 20, meanWind = "025°", meanIsMeasured = true, estimatedWind = "025°",
        windNow = "025°", windNowGlance = "Wind: stbd 340° + 45°", windNowIsLive = true,
        shift = "+5°", shiftDegrees = shift, shiftTitle = "Shift vs mean · + lift", heading = "340°", headingSource = "GPS course",
        headingGlance = "Stbd upwind · GPS",
        histogramFromTheBoat = (-40..40).map { HistogramBin(it, if (it == 5) 3 else 0) },
        speed = "5.8", speedDelta = "+0.3 vs avg 5.5", speedDeltaMps = 0.15, vmg = "4.1",
        tack = Tack.STARBOARD, tackLabel = "Starboard upwind", tackShort = "Stbd", pointOfSailLabel = "Upwind",
        advice = advice, adviceTitle = title, adviceDetail = "Starboard is lifted 5° from the mean wind", adviceGlance = "Stbd lifted 5°",
        histogram = (-40..40).map { HistogramBin(it, if (it == 5) 3 else 0) }, estimatedOffset = 5.0, setOffset = -5.0,
        shifts = listOf(2.0, 5.0, 4.0), statistics = "Mean 025° ±2° · 3 samples",
        averageUpwind = "5.5 kn", averageUpwindVmg = "3.9 kn", averageDownwind = "6.1 kn", averageDownwindVmg = "5.0 kn",
        raceTime = "Racing +01:12", canSetFromHeading = true,
    )

    private fun assertEverythingIsOnTheScreen() {
        compose.onNodeWithTag("advice").assertIsDisplayed()
        compose.onNodeWithTag("adviceGlance").assertIsDisplayed()
        compose.onNodeWithTag("speed").assertIsDisplayed()
        compose.onNodeWithTag("speedDelta").assertIsDisplayed()
        compose.onNodeWithTag("heading").assertIsDisplayed()
        compose.onNodeWithTag("headingGlance").assertIsDisplayed()
        compose.onNodeWithTag("shift").assertIsDisplayed()
        compose.onNodeWithTag("shiftStrip").assertIsDisplayed()
        compose.onNodeWithTag("raceTime").assertExists()
        // Nothing of the other screens is here.
        compose.onNodeWithTag("courseMap").assertDoesNotExist()
        compose.onNodeWithTag("configuredWind").assertDoesNotExist()
        compose.onNodeWithTag("windFromStarboard").assertDoesNotExist()
    }

    @Test
    fun emptyState() {
        show()
        assertEverythingIsOnTheScreen()
        compose.onNodeWithTag("advice").assertTextEquals("—")
        compose.onNodeWithTag("adviceGlance").assertTextEquals("NO HEADING")
        compose.onNodeWithTag("speed").assertTextEquals("---")
        compose.onNodeWithTag("speedDelta").assertTextEquals("")
        compose.onNodeWithTag("heading").assertTextEquals("---")
        compose.onNodeWithTag("headingGlance").assertTextEquals("NO HEADING")
        compose.onNodeWithTag("shift").assertTextEquals("---")
        compose.onNodeWithTag("raceTime").assertTextEquals("")
    }

    @Test
    fun racing() {
        show(sailing(TackAdvice.HOLD, "STAY", 5.0))
        assertEverythingIsOnTheScreen()
        compose.onNodeWithTag("raceTime").assertIsDisplayed().assertTextEquals("RACING +01:12")
        compose.onNodeWithTag("advice").assertTextEquals("STAY")
        compose.onNodeWithTag("adviceGlance").assertTextEquals("STBD LIFTED 5°")
        compose.onNodeWithTag("speed").assertTextEquals("5.8")
        compose.onNodeWithTag("speedDelta").assertTextEquals("+0.3 VS AVG 5.5")
        compose.onNodeWithTag("heading").assertTextEquals("340°")
        compose.onNodeWithTag("headingGlance").assertTextEquals("STBD UPWIND · GPS")
        compose.onNodeWithTag("shift").assertTextEquals("+5°")
    }

    @Test
    fun adviceColoursAndLongWords() {
        show(sailing(TackAdvice.TACK, "TACK", -5.0))
        compose.onNodeWithTag("advice").assertTextEquals("TACK")
        assertEquals(RaceColors.Warning, adviceColor(TackAdvice.TACK))
        assertEquals(RaceColors.Early, adviceColor(TackAdvice.HOLD))
        assertEquals(RaceColors.White, adviceColor(TackAdvice.EITHER))
        assertEquals(RaceColors.Muted, adviceColor(TackAdvice.UNKNOWN))
    }

    @Test
    fun eitherTackStillFitsItsCell() {
        show(sailing(TackAdvice.EITHER, "EITHER TACK", 0.0).copy(adviceGlance = "At the mean ±6°"))
        compose.onNodeWithTag("advice").assertIsDisplayed().assertTextEquals("EITHER TACK")
        compose.onNodeWithTag("adviceGlance").assertTextEquals("AT THE MEAN ±6°")
        compose.onNodeWithTag("shift").assertIsDisplayed()
    }

    /** The phone this is sailed with: 360 x 760 dp, where a row that fits a wide screen can still clip. */
    @Test
    @Config(qualifiers = "w360dp-h760dp-xxhdpi")
    fun everythingIsOnANarrowPhone() {
        show(sailing(TackAdvice.HOLD, "STAY", 5.0))
        assertEverythingIsOnTheScreen()
        val advice = compose.onNodeWithTag("advice").fetchSemanticsNode().boundsInRoot
        val speed = compose.onNodeWithTag("speed").fetchSemanticsNode().boundsInRoot
        val heading = compose.onNodeWithTag("heading").fetchSemanticsNode().boundsInRoot
        val shift = compose.onNodeWithTag("shift").fetchSemanticsNode().boundsInRoot
        // Upright: the word on top, then each number on a line of its own, the shift at the bottom.
        assertTrue(advice.bottom <= speed.top, "the word is above the speed: $advice, $speed")
        assertTrue(speed.bottom <= heading.top, "the heading is under the speed: $speed, $heading")
        assertTrue(heading.bottom <= shift.top, "the shift is under the heading: $heading, $shift")
        // Each number has the whole width to grow into.
        assertTrue(speed.width > 300f, "the speed spans the screen: $speed")
    }

    /** The same phone on its side: the word and the shift on the left, the numbers on the right. */
    @Test
    @Config(qualifiers = "w760dp-h360dp-land-xxhdpi")
    fun onItsSideTheWordAndTheShiftAreBesideTheNumbers() {
        show(sailing(TackAdvice.HOLD, "STAY", 5.0))
        assertEverythingIsOnTheScreen()
        val advice = compose.onNodeWithTag("advice").fetchSemanticsNode().boundsInRoot
        val speed = compose.onNodeWithTag("speed").fetchSemanticsNode().boundsInRoot
        val heading = compose.onNodeWithTag("heading").fetchSemanticsNode().boundsInRoot
        val shift = compose.onNodeWithTag("shift").fetchSemanticsNode().boundsInRoot
        assertTrue(advice.right <= speed.left, "the word is left of the speed: $advice, $speed")
        assertTrue(advice.bottom <= shift.top, "the shift is under the word: $advice, $shift")
        assertTrue(speed.bottom <= heading.top, "the heading is under the speed: $speed, $heading")
    }
}
