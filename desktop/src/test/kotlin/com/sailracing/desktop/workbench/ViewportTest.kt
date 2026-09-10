package com.sailracing.desktop.workbench

import com.sailracing.domain.geo.PlanePosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ViewportTest {

    private val view = Viewport(PlanePosition(100.0, 200.0), metersPerPixel = 2.0, widthPixels = 400, heightPixels = 300)

    @Test
    fun `north is up and east is right`() {
        // The middle of the canvas is the middle of the water.
        assertEquals(PixelPoint(200.0, 150.0), view.toPixels(PlanePosition(100.0, 200.0)))
        // 100 m east is 50 pixels to the right; 100 m north is 50 pixels up, which is a smaller y.
        assertEquals(PixelPoint(250.0, 150.0), view.toPixels(PlanePosition(200.0, 200.0)))
        assertEquals(PixelPoint(200.0, 100.0), view.toPixels(PlanePosition(100.0, 300.0)))
        assertEquals(800.0, view.widthMeters)
        assertEquals(600.0, view.heightMeters)
    }

    @Test
    fun `a pixel and a position mean the same thing both ways round`() {
        for (pixel in listOf(PixelPoint(0.0, 0.0), PixelPoint(399.0, 299.0), PixelPoint(123.0, 45.0))) {
            val back = view.toPixels(view.toPlane(pixel.x, pixel.y))
            assertEquals(pixel.x, back.x, 1e-9)
            assertEquals(pixel.y, back.y, 1e-9)
        }
    }

    @Test
    fun `dragging moves the water with the hand`() {
        val dragged = view.pannedBy(dxPixels = 20.0, dyPixels = -10.0)
        // The hand went right and up, so the water under it did too: the middle moved the other way.
        assertEquals(60.0, dragged.center.eastMeters, 1e-9)
        assertEquals(180.0, dragged.center.northMeters, 1e-9)
        assertEquals(view.metersPerPixel, dragged.metersPerPixel)
    }

    @Test
    fun `zooming holds the metre that is under the mouse`() {
        val mouse = PixelPoint(310.0, 60.0)
        val under = view.toPlane(mouse.x, mouse.y)
        val closer = view.zoomedBy(2.0, mouse.x, mouse.y)
        assertEquals(1.0, closer.metersPerPixel, 1e-9)
        val still = closer.toPixels(under)
        assertEquals(mouse.x, still.x, 1e-9)
        assertEquals(mouse.y, still.y, 1e-9)
        // And out again is where it started.
        val back = closer.zoomedBy(0.5, mouse.x, mouse.y)
        assertEquals(view.metersPerPixel, back.metersPerPixel, 1e-9)
        assertEquals(view.center.eastMeters, back.center.eastMeters, 1e-9)
    }

    @Test
    fun `zoom stops at a close-up of the boat and at a whole bay`() {
        var zoomed = view
        repeat(60) { zoomed = zoomed.zoomedBy(2.0, 200.0, 150.0) }
        assertEquals(Viewport.MIN_METERS_PER_PIXEL, zoomed.metersPerPixel)
        repeat(60) { zoomed = zoomed.zoomedBy(0.5, 200.0, 150.0) }
        assertEquals(Viewport.MAX_METERS_PER_PIXEL, zoomed.metersPerPixel)
        assertFailsWith<IllegalArgumentException> { view.zoomedBy(0.0, 0.0, 0.0) }
    }

    @Test
    fun `the window can be any size, and never no size at all`() {
        val resized = view.resized(800, 200)
        assertEquals(800, resized.widthPixels)
        assertEquals(200, resized.heightPixels)
        assertEquals(view.center, resized.center)
        assertEquals(1, view.resized(0, -5).widthPixels)
        assertFailsWith<IllegalArgumentException> { Viewport(metersPerPixel = 0.0) }
        assertFailsWith<IllegalArgumentException> { Viewport(widthPixels = 0) }
    }

    @Test
    fun `fitting holds everything drawn, with room around it`() {
        val fitted = Viewport.fitting(
            listOf(PlanePosition(-200.0, 0.0), PlanePosition(200.0, 800.0)),
            widthPixels = 400,
            heightPixels = 400,
        )
        assertEquals(PlanePosition(0.0, 400.0), fitted.center)
        // The tall side decides: 800 m and its margin over 400 pixels.
        assertEquals(800.0 * 1.24 / 400, fitted.metersPerPixel, 1e-9)
        val corners = listOf(PlanePosition(-200.0, 0.0), PlanePosition(200.0, 800.0)).map(fitted::toPixels)
        assertTrue(corners.all { it.x in 0.0..400.0 && it.y in 0.0..400.0 }, "$corners")
    }

    @Test
    fun `nothing drawn opens on a course-sized square`() {
        val empty = Viewport.fitting(emptyList(), 600, 600)
        assertEquals(PlanePosition(), empty.center)
        assertEquals(Viewport.EMPTY_VIEW_METERS, empty.widthMeters, 1e-9)
        assertEquals(1, Viewport.fitting(emptyList(), 0, 0).widthPixels)
        // One mark on its own is not a course either: the canvas keeps some water around it.
        val single = Viewport.fitting(listOf(PlanePosition(50.0, 50.0)), 600, 600)
        assertEquals(PlanePosition(50.0, 50.0), single.center)
        assertEquals(Viewport.EMPTY_VIEW_METERS * Viewport.MARGIN, single.widthMeters, 1e-9)
    }

    @Test
    fun `the scale bar is a round number of metres`() {
        assertEquals(100.0, Viewport.scaleBarMeters(199.0))
        assertEquals(200.0, Viewport.scaleBarMeters(200.0))
        assertEquals(500.0, Viewport.scaleBarMeters(999.0))
        assertEquals(1000.0, Viewport.scaleBarMeters(1000.0))
        assertEquals(1.0, Viewport.scaleBarMeters(1.5))
        assertEquals(0.5, Viewport.scaleBarMeters(0.9))
        assertFailsWith<IllegalArgumentException> { Viewport.scaleBarMeters(0.0) }
    }
}
