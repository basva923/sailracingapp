package com.sailracing.app.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
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

/** The phone mounted sideways: both panes side by side. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w914dp-h411dp-land-xxhdpi")
class LandscapeLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun panesSitSideBySideInLandscape() {
        compose.setContent {
            SailRacingTheme {
                AdaptivePanes(
                    first = { Text("left pane", modifier = Modifier.testTag("first")) },
                    second = { Text("right pane", modifier = Modifier.testTag("second")) },
                )
            }
        }
        compose.onNodeWithTag("first").assertIsDisplayed()
        compose.onNodeWithTag("second").assertIsDisplayed()
    }

    @Test
    fun theMapSitsBesideTheGlancePanelInLandscape() {
        compose.setContent {
            SailRacingTheme {
                GlanceLayout(
                    main = { Text("the map", modifier = Modifier.fillMaxSize().testTag("main")) },
                    pinned = { Text("the glance", modifier = Modifier.fillMaxSize().testTag("pinned")) },
                )
            }
        }
        val main = compose.onNodeWithTag("main").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val pinned = compose.onNodeWithTag("pinned").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val screen = compose.onRoot().fetchSemanticsNode().size.height.toFloat()
        assertTrue(main.right <= pinned.left, "the map should be to the left of the panel: $main, $pinned")
        // On its side the panel has the whole height, like the map.
        assertTrue(pinned.height > screen * 0.85f, "the panel fills the height: $pinned of $screen")
        assertTrue(main.height > screen * 0.85f, "and so does the map: $main of $screen")
    }
}
