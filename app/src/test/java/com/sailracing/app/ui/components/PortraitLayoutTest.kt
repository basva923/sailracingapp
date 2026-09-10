package com.sailracing.app.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sailracing.app.ui.theme.SailRacingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/** The phone upright on the mast: the map on top, big, and the glance panel pinned under it. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class PortraitLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private var mainPane: PaneSize? = null
    private var pinnedPane: PaneSize? = null

    private fun show() {
        compose.setContent {
            SailRacingTheme {
                GlanceLayout(
                    main = { pane -> mainPane = pane; Text("the map", modifier = Modifier.fillMaxSize().testTag("main")) },
                    pinned = { pane -> pinnedPane = pane; Text("the glance", modifier = Modifier.fillMaxSize().testTag("pinned")) },
                )
            }
        }
    }

    @Test
    fun theMapTakesTheTopOfTheScreenAndTheGlancePanelTheBottom() {
        show()
        val main = compose.onNodeWithTag("main").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val pinned = compose.onNodeWithTag("pinned").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val screen = compose.onRoot().fetchSemanticsNode().size.height.toFloat()
        assertTrue(main.bottom <= pinned.top, "the map should be above the panel: $main, $pinned")
        // The map keeps most of the screen; the panel has what two rows of big words need and no more.
        assertTrue(main.height > screen * 0.5f, "the map should keep most of the screen: $main of $screen")
        assertTrue(pinned.height in screen * 0.3f..screen * 0.4f, "the panel takes its share of the screen: $pinned of $screen")
        // Both are told the room they have, after the padding around them.
        assertTrue(mainPane!!.width < 360.dp && mainPane!!.height < pinnedPane!!.height + main.height.toDp(), "$mainPane")
        assertTrue(pinnedPane!!.height < pinned.height.toDp() + 1.dp, "$pinnedPane vs $pinned")
    }

    /** A taller phone gives the panel no more than it needs: the rest is map. */
    @Test
    @Config(qualifiers = "w411dp-h914dp-xxhdpi")
    fun onATallScreenThePanelStopsGrowing() {
        show()
        val pinned = compose.onNodeWithTag("pinned").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val main = compose.onNodeWithTag("main").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(pinned.height.toDp() <= 300.dp, "the panel is capped: ${pinned.height.toDp()}")
        assertTrue(main.height > pinned.height * 1.8f, "the map gets the rest: $main vs $pinned")
    }

    private fun Float.toDp(): Dp = (this / compose.density.density).dp
}
