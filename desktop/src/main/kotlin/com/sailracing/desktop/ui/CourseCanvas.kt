package com.sailracing.desktop.ui

import com.sailracing.domain.course.CoursePosition
import com.sailracing.domain.course.RaceLine
import com.sailracing.domain.geo.Angles
import com.sailracing.domain.geo.PlanePosition
import com.sailracing.domain.text.Formatters
import com.sailracing.desktop.workbench.PixelPoint
import com.sailracing.desktop.workbench.SketchTool
import com.sailracing.desktop.workbench.Viewport
import com.sailracing.desktop.workbench.Workbench
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.geom.GeneralPath
import java.awt.geom.Line2D
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The chart: the course as it is drawn, and the strategy's answer laid over it. North is up.
 *
 * The left button draws with the tool in hand, the right button drags the water about and the wheel
 * zooms on the mouse. Nothing here decides anything about a course - it hands every gesture to the
 * [Workbench] and draws whatever comes back.
 */
class CourseCanvas(
    private var workbench: Workbench,
    private val onChanged: (Workbench) -> Unit,
    private val onGestureFinished: () -> Unit,
) : JPanel() {

    private var viewport = Viewport(widthPixels = 900, heightPixels = 700)
    private var panFrom: PixelPoint? = null

    init {
        background = Palette.WATER
        preferredSize = Dimension(900, 700)
        val mouse = object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(event)) {
                    panFrom = PixelPoint(event.x.toDouble(), event.y.toDouble())
                } else {
                    update(workbench.touch(at(event)))
                }
            }

            override fun mouseDragged(event: MouseEvent) {
                val from = panFrom
                if (from != null) {
                    viewport = viewport.pannedBy(event.x - from.x, event.y - from.y)
                    panFrom = PixelPoint(event.x.toDouble(), event.y.toDouble())
                    repaint()
                } else {
                    update(workbench.drag(at(event)))
                }
            }

            override fun mouseReleased(event: MouseEvent) {
                if (panFrom != null) {
                    panFrom = null
                } else {
                    onGestureFinished()
                }
            }

            override fun mouseWheelMoved(event: MouseWheelEvent) {
                viewport = viewport.zoomedBy(ZOOM_PER_NOTCH.pow(-event.preciseWheelRotation), event.x.toDouble(), event.y.toDouble())
                repaint()
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
        addMouseWheelListener(mouse)
    }

    /** Shows [workbench], whatever changed it. */
    fun show(workbench: Workbench) {
        this.workbench = workbench
        repaint()
    }

    /** Fits the water around everything drawn, or opens on a course-sized square when nothing is. */
    fun fit() {
        viewport = Viewport.fitting(workbench.drawn(), width, height)
        repaint()
    }

    private fun update(updated: Workbench) {
        workbench = updated
        onChanged(updated)
        repaint()
    }

    private fun at(event: MouseEvent): PlanePosition = viewport.toPlane(event.x.toDouble(), event.y.toDouble())

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        viewport = viewport.resized(width, height)
        val canvas = graphics as Graphics2D
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        canvas.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

        drawRacingArea(canvas)
        drawTrack(canvas)
        drawStartLine(canvas)
        drawRoutes(canvas)
        drawMarks(canvas)
        drawCompass(canvas)
        drawScaleBar(canvas)
        drawHint(canvas)
    }

    private fun drawRacingArea(canvas: Graphics2D) {
        val analysis = workbench.analysis ?: return
        val course = analysis.course ?: return
        val spec = course.spec
        canvas.stroke = BasicStroke(1f)
        canvas.color = Palette.AREA
        // The squares are the wind's, not the chart's, so they lie at an angle on a north-up canvas.
        for (column in 0..spec.columns) {
            val across = spec.leftMeters + column * spec.cellSizeMeters
            line(
                canvas,
                analysis.onPlane(CoursePosition(across, spec.bottomMeters)),
                analysis.onPlane(CoursePosition(across, spec.topMeters)),
            )
        }
        for (row in 0..spec.rows) {
            val upwind = spec.bottomMeters + row * spec.cellSizeMeters
            line(
                canvas,
                analysis.onPlane(CoursePosition(spec.leftMeters, upwind)),
                analysis.onPlane(CoursePosition(spec.rightMeters, upwind)),
            )
        }
        val arrowMeters = spec.cellSizeMeters * 0.6
        for (cell in course.windField.cells) {
            val center = analysis.onPlane(spec.center(cell.cell))
            // The wind of the square, on the compass: the reference wind turned by the square's own shift.
            val from = Angles.normalize(course.frame.windDirectionDegrees + cell.shiftDegrees)
            val solidity = (0.25 + 0.75 * cell.confidence).coerceIn(0.0, 1.0)
            val base = if (cell.measured) Palette.ARROW_MEASURED else Palette.ARROW
            canvas.color = Color(base.red, base.green, base.blue, (solidity * 255).roundToInt())
            arrow(canvas, center, Angles.normalize(from + 180.0), arrowMeters)
        }
    }

    private fun drawTrack(canvas: Graphics2D) {
        val track = workbench.sketch.track
        if (track.size < 2) return
        canvas.color = Palette.TRACK
        canvas.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val path = GeneralPath()
        for ((index, point) in track.withIndex()) {
            val pixel = viewport.toPixels(point.position)
            if (index == 0) path.moveTo(pixel.x, pixel.y) else path.lineTo(pixel.x, pixel.y)
        }
        canvas.draw(path)
    }

    private fun drawStartLine(canvas: Graphics2D) {
        val sketch = workbench.sketch
        val pin = sketch.pinEnd
        val committee = sketch.boatEnd
        canvas.color = Palette.LINE
        canvas.stroke = BasicStroke(2f)
        if (pin != null && committee != null) line(canvas, pin, committee)
        pin?.let { dot(canvas, it, 6.0, Palette.LINE, "Pin") }
        committee?.let { dot(canvas, it, 6.0, Palette.LINE, "Boat") }
    }

    private fun drawMarks(canvas: Graphics2D) {
        val analysis = workbench.analysis
        val drawn = workbench.sketch.windwardMark
        val mark = drawn ?: analysis?.course?.let { analysis.onPlane(it.windwardMark) }
        mark?.let { dot(canvas, it, 7.0, Palette.MARK, if (drawn != null) "Mark" else "Mark (assumed)") }
        workbench.sketch.boat?.let { dot(canvas, it, 5.0, Palette.BOAT, "Boat") }
    }

    private fun drawRoutes(canvas: Graphics2D) {
        val analysis = workbench.analysis ?: return
        analysis.flyer?.let { route(canvas, analysis.onPlane(it), Palette.FLYER, dashed = true) }
        route(canvas, analysis.onPlane(analysis.raceLine), Palette.RACE_LINE, dashed = false)
    }

    private fun route(canvas: Graphics2D, points: List<PlanePosition>, color: Color, dashed: Boolean) {
        if (points.size < 2) return
        canvas.color = color
        canvas.stroke = if (dashed) {
            BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, floatArrayOf(9f, 7f), 0f)
        } else {
            BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        }
        val path = GeneralPath()
        for ((index, point) in points.withIndex()) {
            val pixel = viewport.toPixels(point)
            if (index == 0) path.moveTo(pixel.x, pixel.y) else path.lineTo(pixel.x, pixel.y)
        }
        canvas.draw(path)
    }

    /** The wind, north and the two close-hauled headings, in the corner where a compass rose belongs. */
    private fun drawCompass(canvas: Graphics2D) {
        val wind = workbench.sketch.wind
        val reference = workbench.analysis?.reference?.directionDegrees ?: wind.directionDegrees.toDouble()
        val center = PixelPoint(64.0, 64.0)
        canvas.color = Palette.FAINT_TEXT
        canvas.stroke = BasicStroke(1f)
        canvas.drawOval((center.x - 40).toInt(), (center.y - 40).toInt(), 80, 80)
        canvas.drawString("N", (center.x - 4).toInt(), (center.y - 44).toInt())
        canvas.color = Palette.WIND
        canvas.stroke = BasicStroke(3f)
        pixelArrow(canvas, center, Angles.normalize(reference + 180.0), 38.0)
        canvas.color = Palette.FAINT_TEXT
        canvas.stroke = BasicStroke(1f)
        for (heading in listOf(reference - wind.tackAngleDegrees, reference + wind.tackAngleDegrees)) {
            pixelArrow(canvas, center, Angles.normalize(heading), 30.0)
        }
        canvas.color = Palette.TEXT
        canvas.drawString("Wind ${Formatters.degrees(reference)}", 8, 124)
    }

    private fun drawScaleBar(canvas: Graphics2D) {
        val meters = Viewport.scaleBarMeters(viewport.widthMeters / 4)
        val pixels = meters / viewport.metersPerPixel
        val bottom = height - 24.0
        canvas.color = Palette.TEXT
        canvas.stroke = BasicStroke(2f)
        canvas.draw(Line2D.Double(16.0, bottom, 16.0 + pixels, bottom))
        canvas.draw(Line2D.Double(16.0, bottom - 4, 16.0, bottom + 4))
        canvas.draw(Line2D.Double(16.0 + pixels, bottom - 4, 16.0 + pixels, bottom + 4))
        canvas.drawString(Formatters.meters(meters), 16, (bottom - 8).toInt())
    }

    private fun drawHint(canvas: Graphics2D) {
        canvas.color = Palette.FAINT_TEXT
        canvas.drawString(workbench.tool.hint, 16, height - 6)
        val planning = if (workbench.needsPlanning) "Not planned yet — release the mouse or press Plan route" else ""
        canvas.drawString(planning, 16, 20)
    }

    private fun line(canvas: Graphics2D, from: PlanePosition, to: PlanePosition) {
        val a = viewport.toPixels(from)
        val b = viewport.toPixels(to)
        canvas.draw(Line2D.Double(a.x, a.y, b.x, b.y))
    }

    private fun dot(canvas: Graphics2D, position: PlanePosition, radius: Double, color: Color, label: String) {
        val pixel = viewport.toPixels(position)
        canvas.color = color
        canvas.fillOval((pixel.x - radius).toInt(), (pixel.y - radius).toInt(), (radius * 2).toInt(), (radius * 2).toInt())
        canvas.drawString(label, (pixel.x + radius + 3).toInt(), (pixel.y - radius).toInt())
    }

    /** An arrow [meters] long pointing at a compass [bearingDegrees], centred on a position on the water. */
    private fun arrow(canvas: Graphics2D, center: PlanePosition, bearingDegrees: Double, meters: Double) {
        val half = meters / 2
        val east = sin(Angles.toRadians(bearingDegrees))
        val north = cos(Angles.toRadians(bearingDegrees))
        val tail = PlanePosition(center.eastMeters - east * half, center.northMeters - north * half)
        val head = PlanePosition(center.eastMeters + east * half, center.northMeters + north * half)
        val a = viewport.toPixels(tail)
        val b = viewport.toPixels(head)
        canvas.stroke = BasicStroke(2f)
        canvas.draw(Line2D.Double(a.x, a.y, b.x, b.y))
        head(canvas, a, b, min(8.0, max(3.0, half / viewport.metersPerPixel / 2)))
    }

    private fun pixelArrow(canvas: Graphics2D, from: PixelPoint, bearingDegrees: Double, pixels: Double) {
        val to = PixelPoint(
            from.x + sin(Angles.toRadians(bearingDegrees)) * pixels,
            from.y - cos(Angles.toRadians(bearingDegrees)) * pixels,
        )
        canvas.draw(Line2D.Double(from.x, from.y, to.x, to.y))
        head(canvas, from, to, 7.0)
    }

    /** The two barbs of an arrow head at [to], pointing back the way it came. */
    private fun head(canvas: Graphics2D, from: PixelPoint, to: PixelPoint, size: Double) {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val length = kotlin.math.hypot(dx, dy)
        if (length < 1e-6) return
        val ux = dx / length
        val uy = dy / length
        for (side in listOf(-1.0, 1.0)) {
            canvas.draw(
                Line2D.Double(
                    to.x,
                    to.y,
                    to.x - (ux * 0.85 - uy * 0.5 * side) * size,
                    to.y - (uy * 0.85 + ux * 0.5 * side) * size,
                ),
            )
        }
    }

    private companion object {
        /** How much closer one notch of the wheel brings the water. */
        const val ZOOM_PER_NOTCH = 1.15
    }
}
