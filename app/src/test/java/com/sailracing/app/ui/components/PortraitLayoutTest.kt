package com.sailracing.app.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sailracing.app.ui.theme.SailRacingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/** The phone upright on the mast: the map on top, big, with everything else scrolling under it. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class PortraitLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private var shape by mutableFloatStateOf(1f)

    private fun show() {
        compose.setContent {
            SailRacingTheme {
                MapAndPanel(
                    mapShape = shape,
                    map = { Text("the map", modifier = Modifier.fillMaxSize().testTag("map")) },
                    panel = { Text("the numbers", modifier = Modifier.testTag("panel")) },
                )
            }
        }
    }

    /** The map as drawn, after setting how many times taller than wide the water on it is. */
    private fun mapFor(mapShape: Float) = run {
        compose.runOnUiThread { shape = mapShape }
        compose.waitForIdle()
        compose.onNodeWithTag("map").fetchSemanticsNode().boundsInRoot
    }

    @Test
    fun theMapTakesTheTopOfTheScreenAndThePanelTheRest() {
        show()
        val map = compose.onNodeWithTag("map").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("panel").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(map.bottom <= panel.top, "the map should be above the panel: $map, $panel")
    }

    @Test
    fun theMapIsGivenTheHeightTheRacingAreaCanUse() {
        show()
        val screen = compose.onRoot().fetchSemanticsNode().size.height.toFloat()

        // A square racing area only needs a square map; the rest of the screen is better spent on the panel.
        val square = mapFor(1f)
        assertTrue(square.height in square.width * 0.95f..square.width * 1.05f, "square area, square map: $square")

        // A beat three times as tall as it is wide is drawn far bigger in a map that is given the height.
        val beat = mapFor(3f)
        assertTrue(beat.height > square.height * 1.4f, "a tall area should get a taller map: $beat vs $square")
        assertTrue(beat.height < screen * 0.72f, "but never the whole screen: $beat of $screen")

        // A racing area wider than it is tall cannot use a tall map, and still gets one worth steering by.
        val wide = mapFor(0.2f)
        assertTrue(wide.height > screen * 0.4f, "the map is never squeezed away: $wide of $screen")
        assertTrue(wide.height < square.height, "but a wide area gets less of the screen than a square one: $wide")
    }
}
