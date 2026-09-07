package com.sailracing.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.domain.wind.TargetHeadings
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Heading-up compass rose: the boat always points up, the rose turns underneath it.
 * Shows the configured wind (blue arrow from the edge), the mean wind of the histogram (white), the
 * estimated wind (amber), the two close-hauled target headings (green/red ticks for starboard/port)
 * and the downwind targets (dimmer).
 */
@Composable
fun CompassRose(
    headingDegrees: Double?,
    windDegrees: Double,
    estimatedWindDegrees: Double?,
    targets: TargetHeadings,
    modifier: Modifier = Modifier,
    meanWindDegrees: Double? = null,
) {
    Canvas(modifier = modifier.aspectRatio(1f).testTag("compassRose")) {
        val heading = headingDegrees ?: 0.0
        val center = Offset(size.width / 2, size.height / 2)
        val radius = min(size.width, size.height) / 2 * 0.92f
        val rotation = (-heading).toFloat()

        // Rose
        drawCircle(RaceColors.Dim, radius, center, style = Stroke(3f))
        rotate(rotation, center) {
            for (degree in 0 until 360 step 10) {
                val major = degree % 90 == 0
                val length = if (major) radius * 0.16f else if (degree % 30 == 0) radius * 0.1f else radius * 0.05f
                val angle = Math.toRadians(degree.toDouble() - 90)
                val outer = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
                val inner = Offset(center.x + (radius - length) * cos(angle).toFloat(), center.y + (radius - length) * sin(angle).toFloat())
                drawLine(if (major) RaceColors.White else RaceColors.Muted, inner, outer, if (major) 5f else 2f)
            }
            // Targets
            drawTick(center, radius, targets.starboardUpwind, RaceColors.Starboard, 0.3f, 8f)
            drawTick(center, radius, targets.portUpwind, RaceColors.Port, 0.3f, 8f)
            drawTick(center, radius, targets.starboardDownwind, RaceColors.Starboard.copy(alpha = 0.5f), 0.2f, 5f)
            drawTick(center, radius, targets.portDownwind, RaceColors.Port.copy(alpha = 0.5f), 0.2f, 5f)
            // Wind arrows pointing inwards from the direction the wind comes from
            drawWindArrow(center, radius, windDegrees, RaceColors.Wind, 0.42f)
            if (meanWindDegrees != null) drawWindArrow(center, radius, meanWindDegrees, RaceColors.White, 0.36f)
            if (estimatedWindDegrees != null) drawWindArrow(center, radius, estimatedWindDegrees, RaceColors.Estimated, 0.3f)
        }

        // Boat (fixed, pointing up)
        val boat = Path().apply {
            moveTo(center.x, center.y - radius * 0.28f)
            lineTo(center.x + radius * 0.11f, center.y + radius * 0.2f)
            lineTo(center.x - radius * 0.11f, center.y + radius * 0.2f)
            close()
        }
        drawPath(boat, if (headingDegrees != null) RaceColors.White else RaceColors.Dim)
        // Lubber line
        drawLine(RaceColors.White, Offset(center.x, center.y - radius), Offset(center.x, center.y - radius * 0.8f), 6f)
    }
}

private fun DrawScope.drawTick(center: Offset, radius: Float, degrees: Double, color: Color, lengthFraction: Float, width: Float) {
    val angle = Math.toRadians(degrees - 90)
    val outer = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
    val inner = Offset(
        center.x + radius * (1 - lengthFraction) * cos(angle).toFloat(),
        center.y + radius * (1 - lengthFraction) * sin(angle).toFloat(),
    )
    drawLine(color, inner, outer, width)
}

private fun DrawScope.drawWindArrow(center: Offset, radius: Float, degrees: Double, color: Color, lengthFraction: Float) {
    val angle = Math.toRadians(degrees - 90)
    val tail = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
    val head = Offset(
        center.x + radius * (1 - lengthFraction) * cos(angle).toFloat(),
        center.y + radius * (1 - lengthFraction) * sin(angle).toFloat(),
    )
    drawLine(color, tail, head, 8f)
    val headLength = radius * 0.08f
    val direction = angle + Math.PI
    for (side in listOf(-1, 1)) {
        val wing = direction + side * Math.toRadians(150.0)
        drawLine(color, head, Offset(head.x + headLength * cos(wing).toFloat(), head.y + headLength * sin(wing).toFloat()), 8f)
    }
}
