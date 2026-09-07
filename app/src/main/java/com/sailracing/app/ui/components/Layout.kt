package com.sailracing.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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

/** The colour that represents a tack. */
fun tackColor(tack: com.sailracing.domain.wind.Tack?): Color = when (tack) {
    com.sailracing.domain.wind.Tack.STARBOARD -> RaceColors.Starboard
    com.sailracing.domain.wind.Tack.PORT -> RaceColors.Port
    null -> RaceColors.Muted
}
