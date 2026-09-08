package com.sailracing.app.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.sailracing.domain.course.CoursePosition
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How the map is looked at: fitted to whatever room it has, then pinched and dragged. All of it is plain
 * arithmetic on metres and pixels, so it is checked here rather than through a gesture on a screen.
 */
class MapCameraTest {

    /** A 400 x 800 m piece of water, and a screen 300 x 300 pixels to draw it on. */
    private val view = MapView(centerAcrossMeters = 100.0, centerUpwindMeters = 200.0, widthMeters = 400.0, heightMeters = 800.0)
    private val size = Size(300f, 300f)

    @Test
    fun theAreaIsScaledToFitAndCentred() {
        val projection = MapProjection.of(view, MapCamera.FITTED, size)
        // The taller side decides the scale, so the whole area fits with room to spare across.
        assertEquals(300f / 800f, projection.pixelsPerMeter, 1e-6f)
        assertEquals(Offset(150f, 150f), projection.toPx(CoursePosition(100.0, 200.0)))
        // Up the course is up the screen.
        assertTrue(projection.toPx(CoursePosition(100.0, 600.0)).y < 150f)
        assertEquals(Offset(150f, 0f), projection.toPx(CoursePosition(100.0, 600.0)))
        val there = CoursePosition(-25.0, 375.0)
        val back = projection.toCourse(projection.toPx(there))
        assertEquals(there.acrossMeters, back.acrossMeters, 1e-6)
        assertEquals(there.upwindMeters, back.upwindMeters, 1e-6)
        assertTrue(MapCamera.FITTED.isFitted)
    }

    @Test
    fun withoutRoomToDrawInTheProjectionStillWorks() {
        val projection = MapProjection.of(view, MapCamera.FITTED, Size.Zero)
        assertEquals(1f, projection.pixelsPerMeter)
        assertEquals(CoursePosition(100.0, 200.0), projection.toCourse(Offset.Zero))
    }

    @Test
    fun pinchingZoomsAroundTheFingersAndKeepsTheWaterUnderThem() {
        val centroid = Offset(100f, 60f)
        val held = MapProjection.of(view, MapCamera.FITTED, size).toCourse(centroid)
        val zoomed = MapCamera.FITTED.transformedBy(view, size, centroid, panPixels = Offset.Zero, zoomFactor = 2f)
        assertEquals(2f, zoomed.zoom)
        assertFalse(zoomed.isFitted)
        val stillThere = MapProjection.of(view, zoomed, size).toCourse(centroid)
        assertEquals(held.acrossMeters, stillThere.acrossMeters, 1e-4)
        assertEquals(held.upwindMeters, stillThere.upwindMeters, 1e-4)
        // Twice the scale, so half the water on the screen.
        assertEquals(2f * 300f / 800f, MapProjection.of(view, zoomed, size).pixelsPerMeter, 1e-6f)
    }

    @Test
    fun draggingMovesTheWaterWithTheFinger() {
        val camera = MapCamera(zoom = 4f)
        val centroid = Offset(150f, 150f)
        val under = MapProjection.of(view, camera, size).toCourse(centroid - Offset(30f, 0f))
        val dragged = camera.transformedBy(view, size, centroid, panPixels = Offset(30f, 0f), zoomFactor = 1f)
        assertEquals(4f, dragged.zoom)
        val moved = MapProjection.of(view, dragged, size).toCourse(centroid)
        assertEquals(under.acrossMeters, moved.acrossMeters, 1e-4)
        assertEquals(under.upwindMeters, moved.upwindMeters, 1e-4)
    }

    @Test
    fun theRacingAreaCanNeverBePushedOffTheScreen() {
        val far = MapCamera(zoom = 3f, acrossMeters = 10_000.0, upwindMeters = -10_000.0).clampedTo(view)
        assertEquals(200.0, far.acrossMeters)
        assertEquals(-400.0, far.upwindMeters)
        // Zoom is bounded at both ends, whatever the fingers do.
        assertEquals(MapCamera.MIN_ZOOM, MapCamera.FITTED.transformedBy(view, size, Offset(1f, 1f), Offset.Zero, 0.1f).zoom)
        assertEquals(MapCamera.MAX_ZOOM, MapCamera(zoom = 8f).transformedBy(view, size, Offset(1f, 1f), Offset.Zero, 4f).zoom)
        assertTrue(MapCamera(zoom = 0.5f).clampedTo(view).isFitted)
    }

    @Test
    fun theViewSurvivesARotation() {
        val camera = MapCamera(zoom = 2.5f, acrossMeters = 12.0, upwindMeters = -34.0)
        val state = MapCameraState(camera)
        val saved = with(MapCameraState.Saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(state) }
        assertEquals(camera, MapCameraState.Saver.restore(saved as List<Any>)?.camera)
    }

    @Test
    fun theFingersMoveTheCameraTheyLeftBehind() {
        // What a gesture does, step by step: each little movement builds on the one before it.
        val state = MapCameraState()
        assertTrue(state.isFitted)
        repeat(4) { state.transform(view, size, Offset(150f, 150f), Offset.Zero, 1.5f) }
        assertEquals(1.5f * 1.5f * 1.5f * 1.5f, state.camera.zoom, 1e-4f)
        assertFalse(state.isFitted)
        state.fit()
        assertEquals(MapCamera.FITTED, state.camera)
    }
}
