package com.sailracing.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sailracing.app.ui.map.MapArea
import com.sailracing.app.ui.map.MapArrow
import com.sailracing.app.ui.map.MapUiState
import com.sailracing.app.ui.map.MapView
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.domain.course.CoursePosition
import com.sailracing.domain.strategy.FavouredSide
import com.sailracing.app.ui.theme.SailRacingTheme
import com.sailracing.domain.wind.HistogramBin
import com.sailracing.domain.wind.Tack
import com.sailracing.domain.wind.TargetHeadings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Native graphics, and an explicit draw of the window into a bitmap, so the Canvas drawing code really runs. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComponentsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Robolectric never draws a frame by itself; drawing the decor view runs every Canvas lambda. */
    private fun drawWindow(): Bitmap {
        compose.waitForIdle()
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
        }
        return bitmap
    }

    @Test
    fun chartsRenderWithAndWithoutData() {
        compose.setContent {
            SailRacingTheme {
                Column {
                    HistogramChart(bins = (-40..40).map { HistogramBin(it, 0) }, estimatedOffsetDegrees = null, modifier = Modifier.size(300.dp, 150.dp))
                    HistogramChart(bins = (-40..40).map { HistogramBin(it, it + 41) }, estimatedOffsetDegrees = 12.0, setOffsetDegrees = 3.0, modifier = Modifier.size(300.dp, 150.dp))
                    HistogramChart(bins = (-5..5).map { HistogramBin(it, 1) }, estimatedOffsetDegrees = 30.0, modifier = Modifier.size(300.dp, 150.dp))
                    HistoryChart(shiftsDegrees = emptyList(), modifier = Modifier.size(300.dp, 100.dp))
                    HistoryChart(shiftsDegrees = listOf(-50.0, 3.0, 60.0), modifier = Modifier.size(300.dp, 100.dp))
                }
            }
        }
        compose.onNodeWithText("Sail upwind to build the wind histogram").assertIsDisplayed()
        compose.onNodeWithText("Wind shift history appears while sailing").assertIsDisplayed()
        drawWindow()
    }

    @Test
    fun compassRoseRendersWithAndWithoutHeading() {
        val targets = TargetHeadings(315.0, 45.0, 220.0, 140.0)
        compose.setContent {
            SailRacingTheme {
                Column {
                    CompassRose(headingDegrees = null, windDegrees = 0.0, estimatedWindDegrees = null, targets = targets, modifier = Modifier.size(200.dp))
                    CompassRose(headingDegrees = 320.0, windDegrees = 0.0, estimatedWindDegrees = 5.0, targets = targets, modifier = Modifier.size(200.dp), meanWindDegrees = 2.0)
                }
            }
        }
        drawWindow()
    }

    @Test
    fun courseMapRendersEveryLayer() {
        val full = MapUiState(
            hasFrame = true,
            area = MapArea(-60.0, -60.0, 240.0, 240.0, 60.0),
            view = MapView(0.0, 60.0, 300.0),
            arrows = listOf(MapArrow(0, 0, 4.0, true), MapArrow(1, 0, -4.0, true), MapArrow(2, 1, 0.5, false), MapArrow(3, 3, -0.5, true)),
            track = listOf(CoursePosition(-50.0, -50.0), CoursePosition(0.0, 0.0), CoursePosition(40.0, 120.0)),
            route = listOf(CoursePosition(40.0, 120.0), CoursePosition(20.0, 150.0), CoursePosition(0.0, 180.0)),
            pinEnd = CoursePosition(-50.0, 0.0), boatEnd = CoursePosition(50.0, 0.0),
            boat = CoursePosition(40.0, 120.0), mark = CoursePosition(0.0, 180.0), markIsSet = true,
            boatHeadingDegrees = 45.0, northDegrees = 340.0,
            favouredTack = Tack.PORT, favouredSide = FavouredSide.RIGHT,
        )
        compose.setContent {
            SailRacingTheme {
                Column {
                    CourseMap(MapUiState(), modifier = Modifier.size(200.dp))
                    CourseMap(full, modifier = Modifier.size(200.dp))
                    CourseMap(
                        full.copy(favouredSide = FavouredSide.LEFT, favouredTack = Tack.STARBOARD, boatHeadingDegrees = null, pinEnd = null, track = emptyList(), route = emptyList(), markIsSet = false),
                        modifier = Modifier.size(200.dp),
                    )
                    // Zoomed far out the cells are too small for arrows.
                    CourseMap(full.copy(view = MapView(0.0, 0.0, 20_000.0)), modifier = Modifier.size(200.dp))
                }
            }
        }
        val image = drawWindow()
        assertTrue(image.width > 1 && image.height > 1)
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
