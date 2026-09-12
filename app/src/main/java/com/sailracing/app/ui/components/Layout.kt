package com.sailracing.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import com.sailracing.domain.strategy.UpwindStrategy

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
 * The racing screen's shape: what is looked at while beating, and nothing that scrolls.
 *
 * [main] is the map and [pinned] the panel that stays on the screen whatever [main] does, sized to the
 * room it has rather than to what is written in it. Upright, the panel takes the bottom of the screen
 * ([pinnedFraction] of it, at most [MAX_PINNED_HEIGHT]) and the map the rest; with the phone on its side
 * the map takes the left half and the panel the right, the whole height. Nothing here scrolls of itself,
 * so the helm never finds the map left half way down a list.
 */
@Composable
fun GlanceLayout(
    modifier: Modifier = Modifier,
    pinnedFraction: Float = PINNED_FRACTION,
    main: @Composable BoxScope.(PaneSize) -> Unit,
    pinned: @Composable ColumnScope.(PaneSize) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight
        if (width > height) {
            val pane = PaneSize(width / 2 - PanePadding * 2, height - PanePadding * 2)
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(PanePadding)) { main(pane) }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(PanePadding),
                    verticalArrangement = Arrangement.spacedBy(PanePadding / 2),
                ) { pinned(pane) }
            }
        } else {
            val pinnedHeight = (height * pinnedFraction).coerceAtMost(MAX_PINNED_HEIGHT)
            val mainPane = PaneSize(width - PanePadding * 2, height - pinnedHeight - PanePadding * 2)
            val pinnedPane = PaneSize(width - PanePadding * 2, pinnedHeight - PanePadding * 2)
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(PanePadding)) { main(mainPane) }
                Column(
                    modifier = Modifier.fillMaxWidth().height(pinnedHeight)
                        .padding(start = PanePadding, end = PanePadding, bottom = PanePadding),
                    verticalArrangement = Arrangement.spacedBy(PanePadding / 2),
                ) { pinned(pinnedPane) }
            }
        }
    }
}

/** How much of an upright screen the glance panel takes: enough for two rows of big words and numbers. */
private const val PINNED_FRACTION = 0.36f

/** And no more than this on a tall screen: the rest is better spent on the map. */
private val MAX_PINNED_HEIGHT = 300.dp

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

/**
 * The colour of the speed against its average: green when the boat is going faster than it has been,
 * red when slower, white within a tenth of a knot - or when it is not being judged at all.
 */
fun speedDeltaColor(deltaMps: Double?): Color = when {
    deltaMps == null -> RaceColors.Muted
    deltaMps > SPEED_DELTA_BAND_MPS -> RaceColors.Early
    deltaMps < -SPEED_DELTA_BAND_MPS -> RaceColors.Late
    else -> RaceColors.White
}

/** A tenth of a knot either way is the same speed. */
private const val SPEED_DELTA_BAND_MPS = 0.05

/**
 * The colour of the shift as the boat feels it: green for a lift, red for a header, white within the dead
 * band the advice ignores - or when there is no shift to speak of.
 */
@Composable
fun shiftColor(liftDegrees: Double?): Color = when {
    liftDegrees == null -> MaterialTheme.colorScheme.onBackground
    liftDegrees >= UpwindStrategy.SHIFT_DEAD_BAND_DEGREES -> RaceColors.Early
    liftDegrees <= -UpwindStrategy.SHIFT_DEAD_BAND_DEGREES -> RaceColors.Late
    else -> MaterialTheme.colorScheme.onBackground
}

/** The colour that represents a tack. */
fun tackColor(tack: com.sailracing.domain.wind.Tack?): Color = when (tack) {
    com.sailracing.domain.wind.Tack.STARBOARD -> RaceColors.Starboard
    com.sailracing.domain.wind.Tack.PORT -> RaceColors.Port
    null -> RaceColors.Muted
}
