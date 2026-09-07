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
import androidx.compose.ui.unit.dp
import com.sailracing.app.ui.theme.RaceColors

/**
 * Two stacked panes in portrait, side by side in landscape (phone mounted sideways on the boat).
 * Both panes scroll if the screen is too small.
 */
@Composable
fun AdaptivePanes(
    modifier: Modifier = Modifier,
    first: @Composable ColumnScope.() -> Unit,
    second: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = first,
                )
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = second,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                first()
                second()
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
