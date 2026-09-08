package com.sailracing.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sailracing.app.ui.map.MapArea
import com.sailracing.app.ui.map.MapCamera
import com.sailracing.app.ui.map.MapCameraState
import com.sailracing.app.ui.map.MapUiState
import com.sailracing.app.ui.map.MapView
import com.sailracing.app.ui.theme.SailRacingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The fingers on the map. A pinch has to zoom by everything the fingers spread and a drag has to carry the
 * water with them, which only works if the map keeps working from the camera as it is now - not from the
 * one it was given when the gesture started.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class CourseMapTest {

    @get:Rule
    val compose = createComposeRule()

    /** A square area on a square map: 300 dp (900 px at this density) showing 200 m of water, 4.5 px a metre. */
    private val state = MapUiState(
        area = MapArea(-100.0, -100.0, 200.0, 200.0, 50.0),
        view = MapView(0.0, 0.0, 200.0, 200.0),
    )

    private val camera = MapCameraState()

    private fun show() {
        compose.setContent {
            SailRacingTheme {
                CourseMap(state = state, modifier = Modifier.size(300.dp), camera = camera)
            }
        }
    }

    @Test
    fun aPinchZoomsInByWhatTheFingersSpread() {
        show()
        // Fingers 100 px apart spread to 500 px: five times in, less the spread eaten by the touch slop
        // before the gesture takes (about 1.35 of it), so a good deal more than three.
        compose.onNodeWithTag("courseMap").performTouchInput {
            pinch(center + Offset(-50f, 0f), center + Offset(-250f, 0f), center + Offset(50f, 0f), center + Offset(250f, 0f))
        }
        val zoom = camera.camera.zoom
        assertTrue(zoom in 3f..5f, "a five-times pinch should zoom most of five times in, not $zoom")
    }

    @Test
    fun aDragCarriesTheWaterWithItAndADoubleTapFitsItBack() {
        show()
        // 90 px up the screen is 20 m of water, less the touch slop: the middle of the map moves downwind.
        compose.onNodeWithTag("courseMap").performTouchInput {
            swipe(center, center - Offset(0f, 90f))
        }
        assertTrue(camera.camera.upwindMeters in -20.0..-8.0, "the drag should have moved the map: ${camera.camera}")
        assertEquals(0.0, camera.camera.acrossMeters, 0.001)

        compose.onNodeWithTag("courseMap").performTouchInput { doubleClick() }
        assertEquals(MapCamera.FITTED, camera.camera)
    }
}
