package com.sailracing.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.sailracing.app.ui.map.MapUiState
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.domain.course.CoursePosition
import com.sailracing.domain.strategy.FavouredSide
import com.sailracing.domain.wind.Tack
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The drawn map of the racing area, wind up: the area that follows the track, one arrow per cell showing
 * the mean wind there (bright when measured in the cell, dim when estimated from the neighbours; amber
 * veered, blue backed), the start line, the windward mark, the track, the fastest line from the boat to
 * the mark, the boat with its two close-hauled headings (the lifted one bold), a north arrow and a scale.
 * No real map: the geometry is what matters on the water.
 */
@Composable
fun CourseMap(state: MapUiState, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier.aspectRatio(1f).clipToBounds().testTag("courseMap")) {
        val side = min(size.width, size.height)
        val left = (size.width - side) / 2
        val top = (size.height - side) / 2
        val view = state.view
        val scale = side / view.sideMeters.toFloat()
        val viewLeftMeters = view.centerAcrossMeters - view.sideMeters / 2
        val viewTopMeters = view.centerUpwindMeters + view.sideMeters / 2
        fun toPx(position: CoursePosition) = Offset(
            left + ((position.acrossMeters - viewLeftMeters) * scale).toFloat(),
            top + ((viewTopMeters - position.upwindMeters) * scale).toFloat(),
        )

        val area = state.area
        val areaTopLeft = toPx(CoursePosition(area.leftMeters, area.topMeters))
        val areaSize = Size((area.widthMeters * scale).toFloat(), (area.heightMeters * scale).toFloat())
        drawRect(RaceColors.Surface, areaTopLeft, areaSize)
        val cellPx = (area.cellMeters * scale).toFloat()
        // Cell grid.
        var x = area.leftMeters
        while (x <= area.rightMeters + 1e-6) {
            val px = toPx(CoursePosition(x, 0.0)).x
            drawLine(RaceColors.Dim.copy(alpha = 0.5f), Offset(px, areaTopLeft.y), Offset(px, areaTopLeft.y + areaSize.height), 1f)
            x += area.cellMeters
        }
        var y = area.bottomMeters
        while (y <= area.topMeters + 1e-6) {
            val py = toPx(CoursePosition(0.0, y)).y
            drawLine(RaceColors.Dim.copy(alpha = 0.5f), Offset(areaTopLeft.x, py), Offset(areaTopLeft.x + areaSize.width, py), 1f)
            y += area.cellMeters
        }
        // Wind arrows, pointing where the wind blows to.
        if (cellPx >= MIN_ARROW_CELL_PX) {
            for (arrow in state.arrows) {
                val centre = toPx(CoursePosition(area.leftMeters + (arrow.column + 0.5) * area.cellMeters, area.bottomMeters + (arrow.row + 0.5) * area.cellMeters))
                val color = when {
                    arrow.shiftDegrees > SHIFT_COLOUR_THRESHOLD -> RaceColors.Estimated
                    arrow.shiftDegrees < -SHIFT_COLOUR_THRESHOLD -> RaceColors.Info
                    else -> RaceColors.White
                }
                drawWindArrow(centre, arrow.shiftDegrees + 180.0, cellPx * 0.7f, if (arrow.measured) color else color.copy(alpha = 0.35f), if (arrow.measured) 3f else 2f)
            }
        }
        // The axis from the start to the mark: the split between the two sides.
        val origin = toPx(CoursePosition(0.0, 0.0))
        val mark = state.mark?.let(::toPx)
        if (mark != null) drawLine(RaceColors.Dim, origin, mark, 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)))
        drawRect(RaceColors.Dim, areaTopLeft, areaSize, style = Stroke(3f))

        drawPolyline(state.track, ::toPx, RaceColors.Info.copy(alpha = 0.8f), 3f)
        drawPolyline(state.route, ::toPx, RaceColors.Early, 5f)

        val pin = state.pinEnd?.let(::toPx)
        val boatEnd = state.boatEnd?.let(::toPx)
        if (pin != null && boatEnd != null) drawLine(RaceColors.White, pin, boatEnd, 6f)
        pin?.let { drawMark(it, "P", textMeasurer, RaceColors.White) }
        boatEnd?.let { drawMark(it, "B", textMeasurer, RaceColors.White) }
        mark?.let { drawMark(it, "M", textMeasurer, if (state.markIsSet) RaceColors.Warning else RaceColors.Muted) }

        val boat = state.boat?.let(::toPx)
        if (boat != null) {
            val arrow = side * 0.12f
            val lifted = state.favouredTack
            drawHeading(boat, state.starboardHeadingDegrees, arrow, RaceColors.Starboard, bold = lifted == Tack.STARBOARD)
            drawHeading(boat, state.portHeadingDegrees, arrow, RaceColors.Port, bold = lifted == Tack.PORT)
            drawBoat(boat, state.boatHeadingDegrees, side * 0.035f)
        }

        // North arrow and side labels in the corners, the favoured side highlighted.
        val label = TextStyle(color = RaceColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        val highlighted = label.copy(color = RaceColors.Early)
        drawText(textMeasurer, "LEFT", Offset(left + 8f, top + 8f), if (state.favouredSide == FavouredSide.LEFT) highlighted else label)
        val rightWidth = textMeasurer.measure("RIGHT", label).size.width
        drawText(textMeasurer, "RIGHT", Offset(left + side - rightWidth - 8f, top + 8f), if (state.favouredSide == FavouredSide.RIGHT) highlighted else label)
        drawNorth(Offset(left + side - side * 0.1f, top + side - side * 0.12f), state.northDegrees, side * 0.05f, textMeasurer)
        // Scale bar: one cell.
        val barY = top + side - 12f
        drawLine(RaceColors.White, Offset(left + 8f, barY), Offset(left + 8f + cellPx, barY), 4f)
    }
}

/** Arrows shifted less than this from the mean are drawn neutral. */
private const val SHIFT_COLOUR_THRESHOLD = 1.5

/** Cells smaller than this on screen get no arrow: it would be an unreadable smudge. */
private const val MIN_ARROW_CELL_PX = 14f

private fun DrawScope.drawPolyline(positions: List<CoursePosition>, toPx: (CoursePosition) -> Offset, color: Color, width: Float) {
    if (positions.size < 2) return
    val path = Path()
    positions.forEachIndexed { index, position ->
        val point = toPx(position)
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    drawPath(path, color, style = Stroke(width = width))
}

private fun DrawScope.drawMark(at: Offset, text: String, textMeasurer: TextMeasurer, color: Color) {
    drawCircle(RaceColors.Black, 11f, at)
    drawCircle(color, 11f, at, style = Stroke(3f))
    val style = TextStyle(color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    val measured = textMeasurer.measure(text, style)
    drawText(measured, topLeft = Offset(at.x - measured.size.width / 2, at.y - measured.size.height / 2))
}

private fun DrawScope.drawHeading(from: Offset, degrees: Double, length: Float, color: Color, bold: Boolean) {
    val angle = Math.toRadians(degrees - 90)
    val to = Offset(from.x + length * cos(angle).toFloat(), from.y + length * sin(angle).toFloat())
    drawLine(if (bold) color else color.copy(alpha = 0.45f), from, to, if (bold) 7f else 3f)
}

/** An arrow centred on [at], pointing in the frame direction [degrees] (0 = up), with a head at the tip. */
private fun DrawScope.drawWindArrow(at: Offset, degrees: Double, length: Float, color: Color, width: Float) {
    val angle = Math.toRadians(degrees - 90)
    val dx = cos(angle).toFloat()
    val dy = sin(angle).toFloat()
    val tail = Offset(at.x - dx * length / 2, at.y - dy * length / 2)
    val tip = Offset(at.x + dx * length / 2, at.y + dy * length / 2)
    drawLine(color, tail, tip, width)
    val head = length * 0.35f
    for (wing in listOf(-1, 1)) {
        val direction = angle + Math.PI + wing * Math.toRadians(28.0)
        drawLine(color, tip, Offset(tip.x + head * cos(direction).toFloat(), tip.y + head * sin(direction).toFloat()), width)
    }
}

private fun DrawScope.drawBoat(at: Offset, headingDegrees: Double?, size: Float) {
    val angle = Math.toRadians((headingDegrees ?: 0.0) - 90)
    fun corner(forward: Float, sideways: Float): Offset {
        val x = forward * cos(angle) - sideways * sin(angle)
        val y = forward * sin(angle) + sideways * cos(angle)
        return Offset(at.x + x.toFloat(), at.y + y.toFloat())
    }
    val hull = Path().apply {
        val bow = corner(size, 0f)
        moveTo(bow.x, bow.y)
        val right = corner(-size * 0.8f, size * 0.55f)
        lineTo(right.x, right.y)
        val leftCorner = corner(-size * 0.8f, -size * 0.55f)
        lineTo(leftCorner.x, leftCorner.y)
        close()
    }
    drawPath(hull, if (headingDegrees != null) RaceColors.White else RaceColors.Muted)
    drawPath(hull, RaceColors.Black, style = Stroke(2f))
}

private fun DrawScope.drawNorth(at: Offset, degrees: Double, length: Float, textMeasurer: TextMeasurer) {
    val angle = Math.toRadians(degrees - 90)
    val tip = Offset(at.x + length * cos(angle).toFloat(), at.y + length * sin(angle).toFloat())
    val tail = Offset(at.x - length * cos(angle).toFloat(), at.y - length * sin(angle).toFloat())
    drawLine(RaceColors.Muted, tail, tip, 3f)
    for (wing in listOf(-1, 1)) {
        val direction = angle + Math.PI + wing * Math.toRadians(30.0)
        drawLine(RaceColors.Muted, tip, Offset(tip.x + length * 0.4f * cos(direction).toFloat(), tip.y + length * 0.4f * sin(direction).toFloat()), 3f)
    }
    val style = TextStyle(color = RaceColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val measured = textMeasurer.measure("N", style)
    val labelAt = Offset(tip.x + length * 0.6f * cos(angle).toFloat(), tip.y + length * 0.6f * sin(angle).toFloat())
    drawText(measured, topLeft = Offset(labelAt.x - measured.size.width / 2, labelAt.y - measured.size.height / 2))
}
