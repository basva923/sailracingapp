package com.sailracing.app.ui.race

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sailracing.app.ui.components.adviceColor
import com.sailracing.app.ui.map.MapArea
import com.sailracing.app.ui.map.MapArrow
import com.sailracing.app.ui.map.MapUiState
import com.sailracing.app.ui.map.MapView
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.app.ui.theme.SailRacingTheme
import com.sailracing.app.ui.wind.WindUiState
import com.sailracing.domain.course.CoursePosition
import com.sailracing.domain.strategy.FavouredSide
import com.sailracing.domain.strategy.TackAdvice
import com.sailracing.domain.wind.HistogramBin
import com.sailracing.domain.wind.Tack
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * The one screen raced by: the map on top, and under it the advice, the numbers, the wind, the mark and
 * the statistics. What used to be two screens is one scroll, so everything is reachable from the map.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RaceScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val log = mutableListOf<String>()
    private val actions = RaceActions(
        setWindDirection = { log += "wind$it" },
        setTackAngle = { log += "tack$it" },
        setDownwindAngle = { log += "downwind$it" },
        windFromStarboard = { log += "starboard" },
        windFromPort = { log += "port" },
        resetStatistics = { log += "reset" },
        markHere = { log += "here" },
        setMarkFromLine = { bearing, distance -> log += "mark$bearing/$distance" },
        clearMark = { log += "clearMark" },
        clearTrack = { log += "clear" },
    )

    private fun show(map: MapUiState = MapUiState(), wind: WindUiState = WindUiState()) {
        compose.setContent { SailRacingTheme { RaceScreen(map = map, wind = wind, actions = actions) } }
    }

    @Test
    fun emptyState() {
        show()
        compose.onNodeWithTag("courseMap").assertIsDisplayed()
        compose.onNodeWithTag("advice").assertTextEquals("—")
        compose.onNodeWithTag("adviceDetail").assertTextEquals("WAITING FOR A HEADING")
        compose.onNodeWithTag("sideTitle").assertTextEquals("SIDES UNKNOWN")
        compose.onNodeWithTag("configuredWind").performScrollTo().assertTextEquals("---")
        compose.onNodeWithTag("meanWind").performScrollTo().assertTextEquals("---")
        compose.onNodeWithTag("estimatedWind").performScrollTo().assertTextEquals("---")
        compose.onNodeWithTag("speed").performScrollTo().assertTextEquals("---")
        compose.onNodeWithTag("tack").performScrollTo().assertTextEquals("—")
        compose.onNodeWithTag("compassRose").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("markText").performScrollTo().assertTextEquals("MARK: TOP OF THE AREA UNTIL YOU SET IT")
        compose.onNodeWithTag("trackText").performScrollTo().assertTextEquals("NO TRACK YET")
        compose.onNodeWithTag("statistics").performScrollTo().assertTextEquals("No upwind samples yet")
        compose.onNodeWithTag("histogramChart").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("historyChart").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("windFromStarboard").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("windFromPort").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("tackLabel").performScrollTo().assertTextEquals("—")
        compose.onNodeWithTag("markHere").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("markByBearing").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("clearMark").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("clearTrack").performScrollTo().assertIsNotEnabled()
        // Nothing has been zoomed, so there is nothing to fit back.
        compose.onNodeWithTag("fitMap").performScrollTo().assertIsNotEnabled()
    }

    private val fullMap = MapUiState(
        area = MapArea(-60.0, -60.0, 240.0, 240.0, 60.0),
        view = MapView(0.0, 60.0, 300.0, 300.0),
        arrows = listOf(MapArrow(0, 0, 4.0, true, 0.9), MapArrow(1, 0, -4.0, true, 0.5), MapArrow(2, 1, 0.5, false, 0.05)),
        track = listOf(CoursePosition(-50.0, -50.0), CoursePosition(0.0, 0.0), CoursePosition(40.0, 120.0)),
        pinEnd = CoursePosition(-50.0, 0.0), boatEnd = CoursePosition(50.0, 0.0),
        boat = CoursePosition(40.0, 120.0), mark = CoursePosition(0.0, 180.0), markIsSet = true,
        boatHeadingDegrees = 45.0, northDegrees = 340.0,
        favouredTack = Tack.PORT, favouredSide = FavouredSide.RIGHT, sideTitle = "GO RIGHT",
        sideDetail = "Right side wind +4° · go towards the shift",
        scaleText = "Area 240 m × 240 m", markText = "Mark set 180 m at 000° from the line",
        raceLine = listOf(CoursePosition(40.0, 120.0), CoursePosition(0.0, 180.0)),
        riskyLine = listOf(CoursePosition(40.0, 120.0), CoursePosition(-40.0, 150.0), CoursePosition(0.0, 180.0)),
        raceLineText = "Race line: 2 tacks · 04:37 to the mark, 05:02 on a bad day",
        riskText = "Flyer: 1 tack · 04:29, 04:05 at best · beats the race line in 6 of 16 winds",
        trackText = "300 points · 5 min", canMarkHere = true, canSetMarkFromLine = true,
    )

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
    fun racing() {
        show(fullMap, sailing(TackAdvice.HOLD, "HOLD", 5.0))
        compose.onNodeWithTag("raceTime").assertTextEquals("RACING +01:12")
        compose.onNodeWithTag("advice").assertTextEquals("HOLD")
        compose.onNodeWithTag("sideTitle").assertTextEquals("GO RIGHT")
        compose.onNodeWithTag("sideDetail").assertTextEquals("RIGHT SIDE WIND +4° · GO TOWARDS THE SHIFT")
        compose.onNodeWithTag("raceLineText").assertTextEquals("RACE LINE: 2 TACKS · 04:37 TO THE MARK, 05:02 ON A BAD DAY")
        compose.onNodeWithTag("riskText")
            .assertTextEquals("FLYER: 1 TACK · 04:29, 04:05 AT BEST · BEATS THE RACE LINE IN 6 OF 16 WINDS")
        compose.onNodeWithTag("meanWind").performScrollTo().assertTextEquals("025°")
        compose.onNodeWithTag("shift").performScrollTo().assertTextEquals("+5°")
        compose.onNodeWithTag("tack").performScrollTo().assertTextEquals("Stbd")
        compose.onNodeWithTag("speed").performScrollTo().assertTextEquals("5.8")
        compose.onNodeWithTag("vmg").performScrollTo().assertTextEquals("4.1")
        compose.onNodeWithTag("avgUpwind").performScrollTo().assertTextEquals("5.5 kn")
        compose.onNodeWithTag("avgDownwindVmg").performScrollTo().assertTextEquals("5.0 kn")
        compose.onNodeWithTag("tackLabel").performScrollTo().assertTextEquals("STARBOARD UPWIND")
        compose.onNodeWithTag("scale").performScrollTo().assertTextEquals("AREA 240 M × 240 M")
        compose.onNodeWithTag("markText").performScrollTo().assertTextEquals("MARK SET 180 M AT 000° FROM THE LINE")
        compose.onNodeWithTag("windFromStarboard").performScrollTo().performClick()
        compose.onNodeWithTag("windFromPort").performScrollTo().performClick()
        assertEquals(listOf("starboard", "port"), log)
    }

    @Test
    fun adviceAndSideColours() {
        show(fullMap.copy(favouredSide = FavouredSide.LEFT, sideTitle = "GO LEFT"), sailing(TackAdvice.TACK, "TACK", -5.0))
        compose.onNodeWithTag("advice").assertTextEquals("TACK")
        compose.onNodeWithTag("sideTitle").assertTextEquals("GO LEFT")
        assertEquals(RaceColors.Warning, adviceColor(TackAdvice.TACK))
        assertEquals(RaceColors.Early, adviceColor(TackAdvice.HOLD))
        assertEquals(RaceColors.White, adviceColor(TackAdvice.EITHER))
        assertEquals(RaceColors.Muted, adviceColor(TackAdvice.UNKNOWN))
    }

    @Test
    fun evenSidesAndEitherTack() {
        show(fullMap.copy(favouredSide = FavouredSide.EVEN, sideTitle = "SIDES EVEN"), sailing(TackAdvice.EITHER, "EITHER TACK", 0.0))
        compose.onNodeWithTag("sideTitle").assertTextEquals("SIDES EVEN")
        compose.onNodeWithTag("advice").assertTextEquals("EITHER TACK")
        compose.onNodeWithTag("shift").performScrollTo().assertTextEquals("+5°")
    }

    /** The phone this is sailed with: 360 x 760 dp, where a row that fits a wide screen can still clip. */
    @Test
    @Config(qualifiers = "w360dp-h760dp-xxhdpi")
    fun everythingIsReachableOnANarrowPhone() {
        show(fullMap, sailing(TackAdvice.HOLD, "HOLD", 5.0))
        compose.onNodeWithTag("courseMap").assertIsDisplayed()
        compose.onNodeWithTag("advice").assertTextEquals("HOLD")
        compose.onNodeWithTag("sideTitle").assertIsDisplayed()
        compose.onNodeWithTag("speed").performScrollTo().assertTextEquals("5.8")
        compose.onNodeWithTag("windFromStarboard").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("markHere").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("historyChart").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingTheWindThroughDialogs() {
        show(wind = WindUiState(windDirection = 355, tackAngle = 45, downwindAngle = 140))
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

    @Test
    fun zoomingTheMapAndFittingItBackAgain() {
        show(fullMap, sailing(TackAdvice.HOLD, "HOLD", 5.0))
        compose.onNodeWithTag("fitMap").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("courseMap").performTouchInput {
            pinch(center + Offset(-40f, 0f), center + Offset(-120f, 0f), center + Offset(40f, 0f), center + Offset(120f, 0f))
        }
        compose.onNodeWithTag("fitMap").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("fitMap").performClick()
        compose.onNodeWithTag("fitMap").assertIsNotEnabled()

        // A double tap on the map fits it back too.
        compose.onNodeWithTag("courseMap").performTouchInput {
            pinch(center + Offset(-40f, 0f), center + Offset(-120f, 0f), center + Offset(40f, 0f), center + Offset(120f, 0f))
        }
        compose.onNodeWithTag("fitMap").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("courseMap").performTouchInput { doubleClick() }
        compose.onNodeWithTag("fitMap").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun settingTheMarkAndClearingTheTrack() {
        show(fullMap, sailing(TackAdvice.HOLD, "HOLD", 5.0))
        compose.onNodeWithTag("markHere").performScrollTo().performClick()

        // Mark by bearing: 0 + 10 = 010°, then 1000 + 500 - 50 = 1450 m.
        compose.onNodeWithTag("markByBearing").performScrollTo().performClick()
        compose.onNodeWithTag("stepUpBig").performClick()
        compose.onNodeWithTag("numberValue").assertTextEquals("10 °")
        compose.onNodeWithTag("confirm").performClick()
        compose.onNodeWithTag("stepUpBig").performClick()
        compose.onNodeWithTag("stepDown").performClick()
        compose.onNodeWithTag("numberValue").assertTextEquals("1450 m")
        compose.onNodeWithTag("confirm").performClick()
        // Cancelling either step sets nothing.
        compose.onNodeWithTag("markByBearing").performScrollTo().performClick()
        compose.onNodeWithTag("cancel").performClick()
        compose.onNodeWithTag("markByBearing").performScrollTo().performClick()
        compose.onNodeWithTag("confirm").performClick()
        compose.onNodeWithTag("cancel").performClick()

        compose.onNodeWithTag("clearMark").performScrollTo().performClick()
        compose.onNodeWithText("Clear the mark?").assertIsDisplayed()
        compose.onNodeWithTag("cancel").performClick()
        compose.onNodeWithTag("clearMark").performScrollTo().performClick()
        compose.onNodeWithTag("confirm").performClick()

        compose.onNodeWithTag("clearTrack").performScrollTo().performClick()
        compose.onNodeWithText("Clear the track?").assertIsDisplayed()
        compose.onNodeWithTag("cancel").performClick()
        compose.onNodeWithTag("clearTrack").performClick()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(listOf("here", "mark10/1450.0", "clearMark", "clear"), log)
    }
}
