package com.sailracing.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sailracing.app.ui.theme.SailRacingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

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
}
