package com.sailracing.app.ui.map

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
import com.sailracing.domain.course.CoursePosition
import com.sailracing.domain.strategy.FavouredSide
import com.sailracing.domain.strategy.TackAdvice
import com.sailracing.domain.wind.Tack
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val log = mutableListOf<String>()
    private val actions = MapActions(
        clearTrack = { log += "clear" },
        markHere = { log += "here" },
        setMarkFromLine = { bearing, distance -> log += "mark$bearing/$distance" },
        clearMark = { log += "clearMark" },
    )

    private fun show(state: MapUiState) {
        compose.setContent { SailRacingTheme { MapScreen(state = state, actions = actions) } }
    }

    @Test
    fun emptyState() {
        show(MapUiState())
        compose.onNodeWithTag("courseMap").assertIsDisplayed()
        compose.onNodeWithTag("sideTitle").assertTextEquals("SIDES UNKNOWN")
        compose.onNodeWithTag("tackTitle").performScrollTo().assertTextEquals("—")
        compose.onNodeWithTag("markText").performScrollTo().assertTextEquals("MARK: TOP OF THE AREA UNTIL YOU SET IT")
        compose.onNodeWithTag("trackText").performScrollTo().assertTextEquals("NO TRACK YET")
        compose.onNodeWithTag("markHere").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("markByBearing").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("clearMark").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("clearTrack").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aFullMapAndTheActions() {
        show(
            MapUiState(
                hasFrame = true,
                area = MapArea(-60.0, -60.0, 240.0, 240.0, 60.0),
                view = MapView(0.0, 60.0, 300.0),
                arrows = listOf(MapArrow(0, 0, 4.0, true), MapArrow(1, 0, -4.0, true), MapArrow(2, 1, 0.5, false)),
                track = listOf(CoursePosition(-50.0, -50.0), CoursePosition(0.0, 0.0), CoursePosition(40.0, 120.0)),
                route = listOf(CoursePosition(40.0, 120.0), CoursePosition(0.0, 180.0)),
                pinEnd = CoursePosition(-50.0, 0.0), boatEnd = CoursePosition(50.0, 0.0),
                boat = CoursePosition(40.0, 120.0), mark = CoursePosition(0.0, 180.0), markIsSet = true,
                boatHeadingDegrees = 45.0, northDegrees = 340.0,
                favouredTack = Tack.PORT, favouredSide = FavouredSide.RIGHT, sideTitle = "GO RIGHT",
                sideDetail = "Right side wind +4° · go towards the shift", advice = TackAdvice.HOLD, tackTitle = "HOLD",
                tackDetail = "Port is lifted 4° from the mean wind", scaleText = "Area 240 m × 240 m", markText = "Mark set 180 m at 000° from the line",
                trackText = "300 points · 5 min", canMarkHere = true, canSetMarkFromLine = true,
            ),
        )
        compose.onNodeWithTag("sideTitle").assertTextEquals("GO RIGHT")
        compose.onNodeWithTag("sideDetail").assertTextEquals("RIGHT SIDE WIND +4° · GO TOWARDS THE SHIFT")
        compose.onNodeWithTag("tackTitle").performScrollTo().assertTextEquals("HOLD")
        compose.onNodeWithTag("scale").performScrollTo().assertTextEquals("AREA 240 M × 240 M")
        compose.onNodeWithTag("markText").performScrollTo().assertTextEquals("MARK SET 180 M AT 000° FROM THE LINE")

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

    @Test
    fun leftSide() {
        show(MapUiState(favouredSide = FavouredSide.LEFT, sideTitle = "GO LEFT", boat = CoursePosition(0.0, 0.0), favouredTack = Tack.STARBOARD))
        compose.onNodeWithTag("sideTitle").assertTextEquals("GO LEFT")
    }

    @Test
    fun evenSides() {
        show(MapUiState(favouredSide = FavouredSide.EVEN, sideTitle = "SIDES EVEN", boat = CoursePosition(0.0, 0.0)))
        compose.onNodeWithTag("sideTitle").assertTextEquals("SIDES EVEN")
    }
}
