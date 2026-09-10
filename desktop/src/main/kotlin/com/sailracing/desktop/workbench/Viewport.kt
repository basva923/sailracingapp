package com.sailracing.desktop.workbench

import com.sailracing.domain.geo.PlanePosition
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** A point on the canvas, in pixels: x to the right, y down, as every screen counts them. */
data class PixelPoint(val x: Double, val y: Double)

/**
 * The piece of water the canvas shows: which metre is in the middle of it, and how many metres a pixel is
 * worth. North is up and east is right - the chart the course is drawn on, not the wind-up map the phone
 * shows - so setting another wind turns the strategy's answer over the drawing and leaves the drawing
 * itself alone.
 *
 * It is a value and not a widget: every gesture is a viewport in and a viewport out, which is what makes
 * a pan, a zoom and a fit something a test can check rather than something to squint at.
 */
data class Viewport(
    val center: PlanePosition = PlanePosition(),
    val metersPerPixel: Double = 1.0,
    val widthPixels: Int = 1,
    val heightPixels: Int = 1,
) {
    init {
        require(metersPerPixel > 0.0) { "a pixel must be worth some water: $metersPerPixel" }
        require(widthPixels > 0 && heightPixels > 0) { "a canvas needs pixels: $widthPixels x $heightPixels" }
    }

    /** How much water is on the canvas, in metres. */
    val widthMeters: Double get() = widthPixels * metersPerPixel
    val heightMeters: Double get() = heightPixels * metersPerPixel

    fun toPixels(position: PlanePosition): PixelPoint = PixelPoint(
        x = widthPixels / 2.0 + (position.eastMeters - center.eastMeters) / metersPerPixel,
        y = heightPixels / 2.0 - (position.northMeters - center.northMeters) / metersPerPixel,
    )

    fun toPlane(x: Double, y: Double): PlanePosition = PlanePosition(
        eastMeters = center.eastMeters + (x - widthPixels / 2.0) * metersPerPixel,
        northMeters = center.northMeters - (y - heightPixels / 2.0) * metersPerPixel,
    )

    /** The same water in a window of another size: what is in the middle stays in the middle. */
    fun resized(widthPixels: Int, heightPixels: Int): Viewport =
        copy(widthPixels = max(1, widthPixels), heightPixels = max(1, heightPixels))

    /** Dragged by a mouse: the water follows the hand, so the canvas moves the other way. */
    fun pannedBy(dxPixels: Double, dyPixels: Double): Viewport = copy(
        center = PlanePosition(
            eastMeters = center.eastMeters - dxPixels * metersPerPixel,
            northMeters = center.northMeters + dyPixels * metersPerPixel,
        ),
    )

    /**
     * Zoomed about a pixel - the one under the wheel - so the metre the mouse is on stays under the mouse.
     * A [factor] above 1 zooms in; the scale stops at [MIN_METERS_PER_PIXEL] and [MAX_METERS_PER_PIXEL],
     * which are a close-up of the boat's own length and a whole bay.
     */
    fun zoomedBy(factor: Double, aboutX: Double, aboutY: Double): Viewport {
        require(factor > 0.0) { "a zoom is a ratio: $factor" }
        val scale = (metersPerPixel / factor).coerceIn(MIN_METERS_PER_PIXEL, MAX_METERS_PER_PIXEL)
        val held = toPlane(aboutX, aboutY)
        val kept = scale / metersPerPixel
        return copy(
            center = PlanePosition(
                eastMeters = held.eastMeters + (center.eastMeters - held.eastMeters) * kept,
                northMeters = held.northMeters + (center.northMeters - held.northMeters) * kept,
            ),
            metersPerPixel = scale,
        )
    }

    companion object {
        /** A pixel is never worth less than this: closer than a boat length is a close-up of nothing. */
        const val MIN_METERS_PER_PIXEL: Double = 0.02

        /** Nor more than this: a canvas of twenty kilometres is a chart, not a race course. */
        const val MAX_METERS_PER_PIXEL: Double = 20.0

        /** The water a canvas opens on when there is nothing drawn on it yet: a course-sized square. */
        const val EMPTY_VIEW_METERS: Double = 600.0

        /** The room left around everything drawn, as a fraction of it. */
        const val MARGIN: Double = 0.12

        /**
         * The view that holds all of [positions] with a margin around it, in a canvas of this size.
         * Nothing drawn, or everything drawn on one spot, opens on [EMPTY_VIEW_METERS] of water.
         */
        fun fitting(positions: List<PlanePosition>, widthPixels: Int, heightPixels: Int): Viewport {
            val width = max(1, widthPixels)
            val height = max(1, heightPixels)
            if (positions.isEmpty()) {
                return Viewport(PlanePosition(), EMPTY_VIEW_METERS / max(width, height), width, height)
            }
            val left = positions.minOf { it.eastMeters }
            val right = positions.maxOf { it.eastMeters }
            val bottom = positions.minOf { it.northMeters }
            val top = positions.maxOf { it.northMeters }
            val across = max((right - left) * (1 + 2 * MARGIN), EMPTY_VIEW_METERS * MARGIN)
            val up = max((top - bottom) * (1 + 2 * MARGIN), EMPTY_VIEW_METERS * MARGIN)
            val scale = max(across / width, up / height).coerceIn(MIN_METERS_PER_PIXEL, MAX_METERS_PER_PIXEL)
            return Viewport(PlanePosition((left + right) / 2, (bottom + top) / 2), scale, width, height)
        }

        /**
         * A round number of metres for the scale bar, no bigger than [maxMeters]: one, two or five times
         * a power of ten, which is how a chart's scale has always been marked.
         */
        fun scaleBarMeters(maxMeters: Double): Double {
            require(maxMeters > 0.0) { "a scale bar is some length: $maxMeters" }
            val decade = 10.0.pow(floor(log10(maxMeters)))
            return when {
                maxMeters >= 5 * decade -> 5 * decade
                maxMeters >= 2 * decade -> 2 * decade
                else -> decade
            }
        }
    }
}
