package com.sailracing.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.sailracing.domain.course.CoursePosition
import kotlin.math.min

/**
 * How the racing area is being looked at. The map is drawn to fit the area to whatever room it is given;
 * this is what the sailor's fingers have done on top of that.
 *
 * @property zoom 1 shows the whole racing area, more zooms in on a point of it.
 * @property acrossMeters how far the middle of the screen has been moved to the right of the middle of the
 *   area, in course-frame metres; @property upwindMeters the same upwind. Both are clamped so that the
 *   racing area can never be pushed off the screen entirely.
 */
@Immutable
data class MapCamera(
    val zoom: Float = MIN_ZOOM,
    val acrossMeters: Double = 0.0,
    val upwindMeters: Double = 0.0,
) {
    /** True when the map is simply the racing area fitted to the screen: nothing to reset. */
    val isFitted: Boolean get() = zoom <= MIN_ZOOM && acrossMeters == 0.0 && upwindMeters == 0.0

    /** The same camera, with the middle of the screen kept inside the racing area. */
    fun clampedTo(view: MapView): MapCamera = copy(
        zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM),
        acrossMeters = acrossMeters.coerceIn(-view.widthMeters / 2, view.widthMeters / 2),
        upwindMeters = upwindMeters.coerceIn(-view.heightMeters / 2, view.heightMeters / 2),
    )

    /**
     * The camera after one pinch-and-drag: [panPixels] is how far the fingers moved and [zoomFactor] how
     * much they spread, around [centroidPixels]. The water under the fingers stays under the fingers.
     */
    fun transformedBy(
        view: MapView,
        size: Size,
        centroidPixels: Offset,
        panPixels: Offset,
        zoomFactor: Float,
    ): MapCamera {
        val zoomed = copy(zoom = (zoom * zoomFactor).coerceIn(MIN_ZOOM, MAX_ZOOM))
        val held = MapProjection.of(view, this, size).toCourse(centroidPixels - panPixels)
        val moved = MapProjection.of(view, zoomed, size).toCourse(centroidPixels)
        return zoomed.copy(
            acrossMeters = acrossMeters + held.acrossMeters - moved.acrossMeters,
            upwindMeters = upwindMeters + held.upwindMeters - moved.upwindMeters,
        ).clampedTo(view)
    }

    companion object {
        /** The whole racing area, fitted to the screen: where the map starts and what a double tap returns to. */
        val FITTED: MapCamera = MapCamera()

        const val MIN_ZOOM: Float = 1f

        /** Zoomed all the way in, a 600 m course is about 50 m across the screen: a boat length or two. */
        const val MAX_ZOOM: Float = 12f
    }
}

/**
 * The camera while fingers are on it, kept by whoever shows the map.
 *
 * A pinch is one long-running coroutine: it is handed a stream of little movements, and each of them has
 * to build on what the ones before it did. Passing the camera in as a value cannot do that - the gesture
 * would keep transforming the camera the pinch started from, of which only the last flick would survive -
 * so the map is given this holder instead and reads it as it is now, gesture and all. The map reads it
 * while drawing too, so a pinch redraws without recomposing anything.
 */
@Stable
class MapCameraState(camera: MapCamera = MapCamera.FITTED) {
    var camera: MapCamera by mutableStateOf(camera)
        private set

    /** True when the map is simply the racing area fitted to the screen: nothing to fit back. */
    val isFitted: Boolean get() = camera.isFitted

    /** Back to the whole racing area: the double tap and the *Fit the map* button. */
    fun fit() {
        camera = MapCamera.FITTED
    }

    /** One step of a pinch-and-drag on a map of [size] pixels showing [view]. */
    fun transform(view: MapView, size: Size, centroidPixels: Offset, panPixels: Offset, zoomFactor: Float) {
        camera = camera.transformedBy(view, size, centroidPixels, panPixels, zoomFactor)
    }

    companion object {
        /** Keeps the view over a rotation or a trip through the background. */
        val Saver: Saver<MapCameraState, Any> = listSaver(
            save = { listOf(it.camera.zoom, it.camera.acrossMeters, it.camera.upwindMeters) },
            restore = { MapCameraState(MapCamera(it[0] as Float, it[1] as Double, it[2] as Double)) },
        )
    }
}

/** The camera of a map, kept over recompositions, rotations and trips through the background. */
@Composable
fun rememberMapCameraState(): MapCameraState = rememberSaveable(saver = MapCameraState.Saver) { MapCameraState() }

/**
 * Course-frame metres onto the canvas and back: the racing area (with its margin) scaled to fit the room
 * the map was given, keeping both axes at the same scale, then zoomed and panned by the [MapCamera].
 */
class MapProjection(
    private val size: Size,
    private val centerAcrossMeters: Double,
    private val centerUpwindMeters: Double,
    /** How many pixels one metre of water is: the scale bar, and how big an arrow may be drawn. */
    val pixelsPerMeter: Float,
) {
    fun toPx(position: CoursePosition): Offset = Offset(
        size.width / 2 + ((position.acrossMeters - centerAcrossMeters) * pixelsPerMeter).toFloat(),
        size.height / 2 - ((position.upwindMeters - centerUpwindMeters) * pixelsPerMeter).toFloat(),
    )

    fun toCourse(offset: Offset): CoursePosition = CoursePosition(
        centerAcrossMeters + (offset.x - size.width / 2) / pixelsPerMeter,
        centerUpwindMeters - (offset.y - size.height / 2) / pixelsPerMeter,
    )

    companion object {
        fun of(view: MapView, camera: MapCamera, size: Size): MapProjection {
            // Before the map has been measured there is no room to fit anything into: one pixel per metre
            // keeps the projection invertible until there is.
            val room = min(size.width / view.widthMeters.toFloat(), size.height / view.heightMeters.toFloat())
            val fitted = if (room.isFinite() && room > 0f) room else 1f
            return MapProjection(
                size = size,
                centerAcrossMeters = view.centerAcrossMeters + camera.acrossMeters,
                centerUpwindMeters = view.centerUpwindMeters + camera.upwindMeters,
                pixelsPerMeter = fitted * camera.zoom,
            )
        }
    }
}
