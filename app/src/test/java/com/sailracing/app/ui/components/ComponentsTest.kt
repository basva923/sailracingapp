package com.sailracing.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.app.ui.theme.SailRacingTheme
import com.sailracing.domain.wind.HistogramBin
import com.sailracing.domain.wind.Tack
import com.sailracing.domain.wind.TargetHeadings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** Native graphics so the Canvas drawing code really runs. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun chartsRenderWithAndWithoutData() {
        compose.setContent {
            SailRacingTheme {
                Column {
                    HistogramChart(bins = (-40..40).map { HistogramBin(it, 0) }, estimatedOffsetDegrees = null, modifier = Modifier.size(300.dp, 150.dp))
                    HistogramChart(bins = (-40..40).map { HistogramBin(it, it + 41) }, estimatedOffsetDegrees = 12.0, modifier = Modifier.size(300.dp, 150.dp))
                    HistogramChart(bins = (-5..5).map { HistogramBin(it, 1) }, estimatedOffsetDegrees = 30.0, modifier = Modifier.size(300.dp, 150.dp))
                    HistoryChart(shiftsDegrees = emptyList(), modifier = Modifier.size(300.dp, 100.dp))
                    HistoryChart(shiftsDegrees = listOf(-50.0, 3.0, 60.0), modifier = Modifier.size(300.dp, 100.dp))
                }
            }
        }
        compose.onNodeWithText("Sail upwind to build the wind histogram").assertIsDisplayed()
        compose.onNodeWithText("Wind shift history appears while sailing").assertIsDisplayed()
    }

    @Test
    fun compassRoseRendersWithAndWithoutHeading() {
        val targets = TargetHeadings(315.0, 45.0, 220.0, 140.0)
        compose.setContent {
            SailRacingTheme {
                Column {
                    CompassRose(headingDegrees = null, windDegrees = 0.0, estimatedWindDegrees = null, targets = targets, modifier = Modifier.size(200.dp))
                    CompassRose(headingDegrees = 320.0, windDegrees = 0.0, estimatedWindDegrees = 5.0, targets = targets, modifier = Modifier.size(200.dp))
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun valuesWithoutTestTags() {
        compose.setContent {
            SailRacingTheme {
                Column {
                    BigValue(label = "Speed", value = "5.8")
                    LabeledValue(label = "Heading", value = "317°")
                    Caption("plain caption")
                    StatusChip("GPS", ok = true)
                }
            }
        }
        compose.onNodeWithText("5.8").assertIsDisplayed()
        compose.onNodeWithText("317°").assertIsDisplayed()
        compose.onNodeWithText("PLAIN CAPTION").assertIsDisplayed()
    }

    @Test
    fun helpers() {
        assertEquals(listOf(5.0, -5.0), shiftsFrom(listOf(5.0, 355.0), 0.0))
        assertEquals(RaceColors.Starboard, tackColor(Tack.STARBOARD))
        assertEquals(RaceColors.Port, tackColor(Tack.PORT))
        assertEquals(RaceColors.Muted, tackColor(null))
        assertEquals("5", formatNumber(5.0, 0))
        assertEquals("5.0", formatNumber(5.0, 1))
        assertEquals(2.9, roundTo(2.9157, 1))
        assertEquals(3.0, roundTo(2.96, 0))
    }
}
