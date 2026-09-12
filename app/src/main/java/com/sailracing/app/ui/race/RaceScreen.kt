package com.sailracing.app.ui.race

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sailracing.app.ui.components.Caption
import com.sailracing.app.ui.components.GlanceCell
import com.sailracing.app.ui.components.HistogramChart
import com.sailracing.app.ui.components.adviceColor
import com.sailracing.app.ui.components.shiftColor
import com.sailracing.app.ui.components.speedDeltaColor
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.app.ui.wind.WindUiState
import com.sailracing.domain.wind.WindHistogram
import kotlin.math.abs

/**
 * The screen raced by, built for a helm who looks at it for a second between the telltales and the water,
 * from the back of the boat, in sunlight. Nothing on it scrolls and nothing on it is pressed. Four things,
 * each on a line of its own and as big as its share of the screen allows: whether to *TACK* or *STAY*,
 * with the reason under it; the speed against the day's average on this point of sail; the heading, with
 * the tack it is on; and the shift as the boat feels it - + and green for a lift, - and red for a header -
 * over a strip of the histogram showing where the wind sits now, seen from the boat too. A bar under
 * them carries the race time. With the phone on its side the word and the shift take the left half, the
 * speed and the heading the right.
 *
 * Everything else has a tab of its own: the map and the mark on *Map*, the wind numbers, the rose, the
 * charts and the wind buttons on *Wind*.
 */
@Composable
fun RaceScreen(wind: WindUiState, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(PADDING)) {
        if (maxWidth > maxHeight) Landscape(wind) else Portrait(wind)
    }
}

@Composable
private fun Portrait(wind: WindUiState) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GAP)) {
        AdviceCell(wind, Modifier.weight(ADVICE_WEIGHT))
        SpeedCell(wind, Modifier.weight(NUMBER_WEIGHT))
        HeadingCell(wind, Modifier.weight(NUMBER_WEIGHT))
        ShiftCell(wind, Modifier.weight(SHIFT_WEIGHT))
        RaceBar(wind.raceTime)
    }
}

@Composable
private fun Landscape(wind: WindUiState) {
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(GAP)) {
            AdviceCell(wind, Modifier.weight(ADVICE_WEIGHT))
            ShiftCell(wind, Modifier.weight(SHIFT_WEIGHT))
            RaceBar(wind.raceTime)
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(GAP)) {
            SpeedCell(wind, Modifier.weight(1f))
            HeadingCell(wind, Modifier.weight(1f))
        }
    }
}

/** TACK or STAY, green to stay and amber to act, with the reason in a line under it. */
@Composable
private fun AdviceCell(wind: WindUiState, modifier: Modifier) {
    GlanceCell(wind.adviceTitle, modifier, color = adviceColor(wind.advice), maxFontSize = BIG_FONT, testTag = "advice") {
        Caption(wind.adviceGlance, modifier = Modifier.fillMaxWidth().testTag("adviceGlance"), color = RaceColors.White, maxLines = 1)
    }
}

/** The speed, with how it compares with the average on this point of sail: green faster, red slower. */
@Composable
private fun SpeedCell(wind: WindUiState, modifier: Modifier) {
    GlanceCell(wind.speed, modifier, title = "Speed kn", maxFontSize = BIG_FONT, testTag = "speed") {
        Caption(wind.speedDelta, modifier = Modifier.fillMaxWidth().testTag("speedDelta"), color = speedDeltaColor(wind.speedDeltaMps), maxLines = 1)
    }
}

/** The heading, with the tack the boat is on and where the heading comes from. */
@Composable
private fun HeadingCell(wind: WindUiState, modifier: Modifier) {
    GlanceCell(wind.heading, modifier, title = "Heading", maxFontSize = BIG_FONT, testTag = "heading") {
        Caption(wind.headingGlance, modifier = Modifier.fillMaxWidth().testTag("headingGlance"), maxLines = 1)
    }
}

/**
 * The shift as the boat feels it, green for a lift and red for a header, over the middle of the histogram
 * seen from the boat - a lift to the right: the wind swings a few degrees, and at this size the whole
 * window would squeeze that into a smudge. A wind beyond it is marked on the edge. The marker is the
 * shift the number shows - none while the boat is off the angle, when a heading says nothing about the wind.
 */
@Composable
private fun ShiftCell(wind: WindUiState, modifier: Modifier) {
    GlanceCell(wind.shift, modifier, title = wind.shiftTitle, color = shiftColor(wind.shiftDegrees), maxFontSize = BIG_FONT, testTag = "shift") {
        val strip = wind.histogramFromTheBoat.filter { abs(it.offsetDegrees) <= GLANCE_HALF_WIDTH }
        HistogramChart(
            bins = strip.ifEmpty { WindHistogram().window(0, GLANCE_HALF_WIDTH) },
            estimatedOffsetDegrees = wind.shiftDegrees,
            modifier = Modifier.fillMaxWidth().height(STRIP_HEIGHT),
            emptyText = "No samples yet",
            testTag = "shiftStrip",
        )
    }
}

/** The bar under the cells: the race time, and nothing else to read. */
@Composable
private fun RaceBar(raceTime: String) {
    Row(modifier = Modifier.fillMaxWidth().height(BAR_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        Text(
            raceTime.uppercase(),
            modifier = Modifier.weight(1f).testTag("raceTime"),
            style = MaterialTheme.typography.labelLarge,
            color = RaceColors.White,
            maxLines = 1,
        )
    }
}

/** The word gets a little more of the screen than a number; the shift carries a chart under it too. */
private const val ADVICE_WEIGHT = 1.1f
private const val NUMBER_WEIGHT = 1f
private const val SHIFT_WEIGHT = 1.15f

/** Every value is as big as its cell allows; this is only where the growing stops on a tablet. */
private val BIG_FONT = 160.sp

/** The glance histogram shows this much either side of the reference wind. */
private const val GLANCE_HALF_WIDTH = 20

private val PADDING = 12.dp
private val GAP = 8.dp
private val STRIP_HEIGHT = 44.dp
private val BAR_HEIGHT = 28.dp
