package com.sailracing.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.domain.strategy.TackAdvice

/**
 * The room a pane offers its content, after padding. Content that would otherwise size itself from the
 * width alone (big auto-sizing numbers, the compass rose) uses [height] to stay inside the screen.
 * In portrait the two panes stack in one scroll and therefore share [height]; in landscape each pane
 * gets it in full.
 */
data class PaneSize(val width: Dp, val height: Dp)

private val PanePadding = 12.dp

/**
 * Two stacked panes in portrait, side by side in landscape (phone mounted sideways on the boat).
 * Both panes scroll if the screen is too small.
 */
@Composable
fun AdaptivePanes(
    modifier: Modifier = Modifier,
    first: @Composable ColumnScope.(PaneSize) -> Unit,
    second: @Composable ColumnScope.(PaneSize) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val height = maxHeight - PanePadding * 2
        if (landscape) {
            val pane = PaneSize(width = maxWidth / 2 - PanePadding * 2, height = height)
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(PanePadding),
                    verticalArrangement = Arrangement.spacedBy(PanePadding),
                ) { first(pane) }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(PanePadding),
                    verticalArrangement = Arrangement.spacedBy(PanePadding),
                ) { second(pane) }
            }
        } else {
            val pane = PaneSize(width = maxWidth - PanePadding * 2, height = height)
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PanePadding),
                verticalArrangement = Arrangement.spacedBy(PanePadding),
            ) {
                first(pane)
                second(pane)
            }
        }
    }
}

/**
 * The racing screen's shape: the map first, then everything else under it (or beside it, with the phone
 * on its side). The map keeps its room whatever the panel does - it is what the sailor looks at while
 * beating - and the panel scrolls under it for the numbers, the wind and the settings.
 *
 * Upright, the map is given the height it can use rather than a fixed slice of the screen: a beat three
 * times as tall as it is wide is drawn three times bigger in a tall pane, while a square racing area
 * would only get black bands from one, and the panel can have that room instead.
 *
 * @param mapShape how many times taller than wide the water the map draws is.
 */
@Composable
fun MapAndPanel(
    modifier: Modifier = Modifier,
    mapShape: Float = 1f,
    map: @Composable () -> Unit,
    panel: @Composable ColumnScope.(PaneSize) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight
        if (width > height) {
            val pane = PaneSize(width / 2 - PanePadding * 2, height - PanePadding * 2)
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(PanePadding)) { map() }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(PanePadding),
                    verticalArrangement = Arrangement.spacedBy(PanePadding),
                ) { panel(pane) }
            }
        } else {
            val mapHeight = (width - PanePadding * 2) * mapShape + PanePadding * 2
            val pane = PaneSize(width - PanePadding * 2, height - PanePadding * 2)
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .height(mapHeight.coerceIn(height * MIN_PORTRAIT_MAP_FRACTION, height * MAX_PORTRAIT_MAP_FRACTION))
                        .padding(PanePadding),
                ) { map() }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(PanePadding),
                    verticalArrangement = Arrangement.spacedBy(PanePadding),
                ) { panel(pane) }
            }
        }
    }
}

/** The least of an upright screen the map gets, however wide the racing area is: still enough to steer to. */
private const val MIN_PORTRAIT_MAP_FRACTION = 0.45f

/** And the most, however tall it is: what is left is the advice and the side, which are read at a glance. */
private const val MAX_PORTRAIT_MAP_FRACTION = 0.72f

/** A small status pill, e.g. GPS quality. */
@Composable
fun StatusChip(text: String, ok: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = if (ok) RaceColors.Early else RaceColors.Warning,
    )
}

/** The colour of a tack advice: green to hold, amber to act, white when it makes no difference. */
fun adviceColor(advice: TackAdvice): Color = when (advice) {
    TackAdvice.HOLD -> RaceColors.Early
    TackAdvice.TACK -> RaceColors.Warning
    TackAdvice.EITHER -> RaceColors.White
    TackAdvice.UNKNOWN -> RaceColors.Muted
}

/** The colour of a shift relative to the reference wind: amber veered, blue backed. */
@Composable
fun shiftColor(shiftDegrees: Double?): Color = when {
    shiftDegrees == null -> MaterialTheme.colorScheme.onBackground
    shiftDegrees > 0 -> RaceColors.Estimated
    shiftDegrees < 0 -> RaceColors.Info
    else -> MaterialTheme.colorScheme.onBackground
}

/** The colour that represents a tack. */
fun tackColor(tack: com.sailracing.domain.wind.Tack?): Color = when (tack) {
    com.sailracing.domain.wind.Tack.STARBOARD -> RaceColors.Starboard
    com.sailracing.domain.wind.Tack.PORT -> RaceColors.Port
    null -> RaceColors.Muted
}
