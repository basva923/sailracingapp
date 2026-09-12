package com.sailracing.app.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sailracing.app.ui.theme.SailRacingTheme
import com.sailracing.domain.course.CoursePosition
import com.sailracing.domain.strategy.FavouredSide
import com.sailracing.domain.wind.Tack
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The course: the map on top, and under it the side, the lines, the mark and the track with their buttons. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val log = mutableListOf<String>()
    private val actions = MapActions(
        markHere = { log += "here" },
        setMarkFromLine = { bearing, distance -> log += "mark$bearing/$distance" },
        clearMark = { log += "clearMark" },
        clearTrack = { log += "clear" },
    )

    private fun show(map: MapUiState = MapUiState()) {
        compose.setContent { SailRacingTheme { MapScreen(map = map, actions = actions) } }
    }

    private val fullMap = MapUiState(
        area = MapArea(-60.0, -60.0, 240.0, 240.0, 60.0),
        view = MapView(0.0, 60.0, 300.0, 300.0),
        windArea = MapArea(-60.0, -60.0, 240.0, 240.0, 80.0),
        arrows = listOf(
            MapArrow(-20.0, -20.0, 80.0, 4.0, true, 0.9),
            MapArrow(60.0, -20.0, 80.0, -4.0, true, 0.5),
            MapArrow(140.0, 60.0, 80.0, 0.5, false, 0.05),
        ),
        track = listOf(CoursePosition(-50.0, -50.0), CoursePosition(0.0, 0.0), CoursePosition(40.0, 120.0)),
        pinEnd = CoursePosition(-50.0, 0.0), boatEnd = CoursePosition(50.0, 0.0),
        boat = CoursePosition(40.0, 120.0), mark = CoursePosition(0.0, 180.0), markIsSet = true,
        boatHeadingDegrees = 45.0, northDegrees = 340.0,
        favouredTack = Tack.PORT, favouredSide = FavouredSide.RIGHT, sideTitle = "GO RIGHT",
        sideDetail = "Right side wind +4°, speed +0.3 kn · go towards the shift",
        scaleText = "Area 240 m × 240 m", markText = "Mark set 180 m at 000° from the line",
        raceLine = listOf(CoursePosition(40.0, 120.0), CoursePosition(0.0, 180.0)),
        riskyLine = listOf(CoursePosition(40.0, 120.0), CoursePosition(-40.0, 150.0), CoursePosition(0.0, 180.0)),
        raceLineText = "Race line: 2 tacks · 04:37 to the mark, 05:02 on a bad day",
        riskText = "Flyer: 1 tack · 04:29, 04:05 at best · beats the race line in 6 of 16 winds",
        raceLineGlance = "2 tacks · 04:37 to the mark",
        trackText = "300 points · 5 min", canMarkHere = true, canSetMarkFromLine = true,
    )

    @Test
    fun emptyState() {
        show()
        compose.onNodeWithTag("courseMap").assertIsDisplayed()
        compose.onNodeWithTag("raceLineGlance").assertIsDisplayed().assertTextEquals("NO RACE LINE YET")
        // Nothing has been zoomed, so there is nothing to fit back.
        compose.onNodeWithTag("fitMap").assertDoesNotExist()
        compose.onNodeWithTag("sideTitle").assertIsDisplayed().assertTextEquals("SIDES UNKNOWN")
        compose.onNodeWithTag("markText").performScrollTo().assertTextEquals("MARK: TOP OF THE AREA UNTIL YOU SET IT")
        compose.onNodeWithTag("markHere").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("markByBearing").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("clearMark").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("clearTrack").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("trackText").performScrollTo().assertTextEquals("NO TRACK YET")
        // The map stays where it is while the details scroll.
        compose.onNodeWithTag("courseMap").assertIsDisplayed()
    }

    @Test
    fun racing() {
        show(fullMap)
        compose.onNodeWithTag("raceLineGlance").assertIsDisplayed().assertTextEquals("2 TACKS · 04:37 TO THE MARK")
        compose.onNodeWithTag("sideTitle").assertTextEquals("GO RIGHT")
        compose.onNodeWithTag("sideDetail").assertTextEquals("RIGHT SIDE WIND +4°, SPEED +0.3 KN · GO TOWARDS THE SHIFT")
        compose.onNodeWithTag("raceLineText").performScrollTo().assertTextEquals("RACE LINE: 2 TACKS · 04:37 TO THE MARK, 05:02 ON A BAD DAY")
        compose.onNodeWithTag("riskText").performScrollTo()
            .assertTextEquals("FLYER: 1 TACK · 04:29, 04:05 AT BEST · BEATS THE RACE LINE IN 6 OF 16 WINDS")
        compose.onNodeWithTag("scale").performScrollTo().assertTextEquals("AREA 240 M × 240 M")
        compose.onNodeWithTag("markText").performScrollTo().assertTextEquals("MARK SET 180 M AT 000° FROM THE LINE")
        compose.onNodeWithTag("trackText").performScrollTo().assertTextEquals("300 POINTS · 5 MIN")
        compose.onNodeWithTag("markHere").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("clearMark").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("clearTrack").performScrollTo().assertIsEnabled()
    }

    @Test
    fun theLeftSide() {
        show(fullMap.copy(favouredSide = FavouredSide.LEFT, sideTitle = "GO LEFT"))
        compose.onNodeWithTag("sideTitle").assertTextEquals("GO LEFT")
    }

    @Test
    fun evenSides() {
        show(fullMap.copy(favouredSide = FavouredSide.EVEN, sideTitle = "SIDES EVEN"))
        compose.onNodeWithTag("sideTitle").assertTextEquals("SIDES EVEN")
    }

    @Test
    fun zoomingTheMapAndFittingItBackAgain() {
        show(fullMap)
        compose.onNodeWithTag("fitMap").assertDoesNotExist()
        compose.onNodeWithTag("courseMap").performTouchInput {
            pinch(center + Offset(-40f, 0f), center + Offset(-120f, 0f), center + Offset(40f, 0f), center + Offset(120f, 0f))
        }
        // Zoomed, the map grows a button to fit it back; fitted, the button is gone again.
        compose.onNodeWithTag("fitMap").assertIsDisplayed().performClick()
        compose.onNodeWithTag("fitMap").assertDoesNotExist()

        // A double tap on the map fits it back too.
        compose.onNodeWithTag("courseMap").performTouchInput {
            pinch(center + Offset(-40f, 0f), center + Offset(-120f, 0f), center + Offset(40f, 0f), center + Offset(120f, 0f))
        }
        compose.onNodeWithTag("fitMap").assertIsDisplayed()
        compose.onNodeWithTag("courseMap").performTouchInput { doubleClick() }
        compose.onNodeWithTag("fitMap").assertDoesNotExist()
    }

    @Test
    fun settingTheMarkAndClearingTheTrack() {
        show(fullMap)
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

    /** The phone this is sailed with, upright: the map on top, the details under it. */
    @Test
    @Config(qualifiers = "w360dp-h760dp-xxhdpi")
    fun uprightTheMapIsAboveTheDetails() {
        show(fullMap)
        val map = compose.onNodeWithTag("courseMap").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val side = compose.onNodeWithTag("sideTitle").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(map.bottom <= side.top, "the map should be above the details: $map, $side")
        compose.onNodeWithTag("markHere").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("courseMap").assertIsDisplayed()
    }

    /** The same phone on its side: the map on the left, the details on the right. */
    @Test
    @Config(qualifiers = "w760dp-h360dp-land-xxhdpi")
    fun onItsSideTheMapIsBesideTheDetails() {
        show(fullMap)
        val map = compose.onNodeWithTag("courseMap").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val side = compose.onNodeWithTag("sideTitle").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(map.right <= side.left, "the map should be to the left of the details: $map, $side")
        compose.onNodeWithTag("clearTrack").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("courseMap").assertIsDisplayed()
    }
}
