package com.sailracing.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.domain.wind.HistogramBin
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Bar chart of how often each wind direction (relative to the reference wind, bin 0 in the middle) was
 * observed while sailing upwind. The reference (the histogram's weighted centre once measured) is the
 * white line in the middle, the set wind a blue line where it sits relative to it, the current estimate
 * amber. A marker beyond the window is drawn on its edge: a wind off the chart is still a wind to the right
 * or the left of everything measured.
 */
@Composable
fun HistogramChart(
    bins: List<HistogramBin>,
    estimatedOffsetDegrees: Double?,
    modifier: Modifier = Modifier,
    setOffsetDegrees: Double? = null,
    emptyText: String = "Sail upwind to build the wind histogram",
    testTag: String = "histogramChart",
) {
    val maxCount = bins.maxOfOrNull { it.count } ?: 0
    Box(modifier = modifier.testTag(testTag)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val chart = chartArea(size)
            drawAxis(chart, bins.first().offsetDegrees, bins.last().offsetDegrees)
            if (maxCount > 0) {
                val barWidth = chart.width / bins.size
                bins.forEachIndexed { index, bin ->
                    val height = chart.height * bin.count / maxCount
                    drawRect(
                        color = RaceColors.Wind.copy(alpha = 0.75f),
                        topLeft = Offset(chart.left + index * barWidth, chart.top + chart.height - height),
                        size = Size(barWidth * 0.8f, height),
                    )
                }
            }
            drawMarker(chart, bins, 0.0, RaceColors.White)
            if (setOffsetDegrees != null) drawMarker(chart, bins, setOffsetDegrees, RaceColors.Wind)
            if (estimatedOffsetDegrees != null) drawMarker(chart, bins, estimatedOffsetDegrees, RaceColors.Estimated)
        }
        if (maxCount == 0) {
            Text(
                emptyText,
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyMedium,
                color = RaceColors.Muted,
            )
        }
    }
}

/** Line chart of the wind shift (estimated minus reference) over the last samples; left is oldest. */
@Composable
fun HistoryChart(
    shiftsDegrees: List<Double>,
    modifier: Modifier = Modifier,
    rangeDegrees: Double = 40.0,
    emptyText: String = "Wind shift history appears while sailing",
) {
    Box(modifier = modifier.testTag("historyChart")) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val chart = chartArea(size)
            // Zero line and +/- range guides
            drawLine(RaceColors.Dim, Offset(chart.left, chart.top), Offset(chart.left, chart.top + chart.height), 2f)
            drawLine(RaceColors.Wind, Offset(chart.left, chart.centerY), Offset(chart.left + chart.width, chart.centerY), 2f)
            drawLine(RaceColors.Dim, Offset(chart.left, chart.top), Offset(chart.left + chart.width, chart.top), 1f)
            drawLine(RaceColors.Dim, Offset(chart.left, chart.top + chart.height), Offset(chart.left + chart.width, chart.top + chart.height), 1f)
            if (shiftsDegrees.size >= 2) {
                val path = Path()
                val step = chart.width / (shiftsDegrees.size - 1)
                shiftsDegrees.forEachIndexed { index, shift ->
                    val clamped = shift.coerceIn(-rangeDegrees, rangeDegrees)
                    val y = chart.centerY - (clamped / rangeDegrees).toFloat() * chart.height / 2
                    val x = chart.left + index * step
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, RaceColors.Estimated, style = Stroke(width = 4f))
            }
        }
        if (shiftsDegrees.size < 2) {
            Text(emptyText, modifier = Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodyMedium, color = RaceColors.Muted)
        }
    }
}

private data class ChartArea(val left: Float, val top: Float, val width: Float, val height: Float) {
    val centerY: Float get() = top + height / 2
}

private fun chartArea(size: Size): ChartArea {
    val padding = 8.dp.value * 2
    return ChartArea(padding, padding, size.width - 2 * padding, size.height - 2 * padding)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAxis(chart: ChartArea, first: Int, last: Int) {
    drawLine(RaceColors.Dim, Offset(chart.left, chart.top + chart.height), Offset(chart.left + chart.width, chart.top + chart.height), 2f)
    // Tick every 10 degrees
    val span = (last - first).coerceAtLeast(1)
    var tick = ((first / 10.0).roundToInt()) * 10
    while (tick <= last) {
        val x = chart.left + chart.width * (tick - first) / span
        drawLine(RaceColors.Dim, Offset(x, chart.top + chart.height), Offset(x, chart.top + chart.height + 6f), 2f)
        tick += 10
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMarker(chart: ChartArea, bins: List<HistogramBin>, offset: Double, color: Color) {
    val first = bins.first().offsetDegrees
    val last = bins.last().offsetDegrees
    val clamped = offset.coerceIn(first.toDouble(), last.toDouble())
    val span = (last - first).coerceAtLeast(1)
    val x = chart.left + chart.width * ((clamped - first) / span).toFloat() + chart.width / bins.size / 2
    drawLine(color, Offset(x, chart.top), Offset(x, chart.top + chart.height), 3f)
}

/** Shift values from history samples, for the chart. */
fun shiftsFrom(samples: List<Double>, referenceDegrees: Double): List<Double> =
    samples.map { com.sailracing.domain.geo.Angles.signedDifference(referenceDegrees, it) }.also { list ->
        check(list.all { abs(it) <= 180.0 })
    }
