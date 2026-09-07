package com.sailracing.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sailracing.app.ui.components.ActionButton
import com.sailracing.app.ui.components.AdaptivePanes
import com.sailracing.app.ui.components.Caption
import com.sailracing.app.ui.components.ConfirmDialog
import com.sailracing.app.ui.components.CourseMap
import com.sailracing.app.ui.components.NumberInputDialog
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.app.ui.wind.adviceColor
import com.sailracing.domain.strategy.FavouredSide

class MapActions(
    val clearTrack: () -> Unit,
    val markHere: () -> Unit,
    val setMarkFromLine: (bearingDegrees: Int, distanceMeters: Double) -> Unit,
    val clearMark: () -> Unit,
)

private enum class MapDialog { MARK_BEARING, MARK_DISTANCE, CLEAR_MARK, CLEAR_TRACK }

/**
 * The map of the racing area with the side advice: where to go upwind, and why. The second pane sets the
 * windward mark: at the boat's position, or as the committee posts it, a bearing and distance from the line.
 */
@Composable
fun MapScreen(state: MapUiState, actions: MapActions, modifier: Modifier = Modifier) {
    var dialog by rememberSaveable { mutableStateOf<MapDialog?>(null) }
    var bearing by rememberSaveable { mutableIntStateOf(0) }
    val sideColor = when (state.favouredSide) {
        FavouredSide.LEFT, FavouredSide.RIGHT -> RaceColors.Early
        FavouredSide.EVEN -> RaceColors.White
        FavouredSide.UNKNOWN -> RaceColors.Muted
    }

    AdaptivePanes(
        modifier = modifier,
        first = { pane ->
            val mapSize = minOf(pane.width, pane.height * 0.7f)
            CourseMap(state, modifier = Modifier.size(mapSize).align(Alignment.CenterHorizontally))
            Text(
                state.sideTitle,
                modifier = Modifier.fillMaxWidth().testTag("sideTitle"),
                style = MaterialTheme.typography.displaySmall,
                color = sideColor,
                textAlign = TextAlign.Center,
            )
            Caption(state.sideDetail, modifier = Modifier.fillMaxWidth().testTag("sideDetail"), color = RaceColors.White, maxLines = 3)
        },
        second = { _ ->
            Text(
                state.tackTitle,
                modifier = Modifier.fillMaxWidth().testTag("tackTitle"),
                style = MaterialTheme.typography.headlineMedium,
                color = adviceColor(state.advice),
                textAlign = TextAlign.Center,
            )
            Caption(state.tackDetail, modifier = Modifier.fillMaxWidth().testTag("tackDetail"))
            Caption(state.markText, modifier = Modifier.fillMaxWidth().testTag("markText"), color = RaceColors.White, maxLines = 3)
            Caption("Set the windward mark at the boat, or by bearing and distance from the line", maxLines = 3)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Mark here", onClick = actions.markHere, modifier = Modifier.weight(1f), enabled = state.canMarkHere, testTag = "markHere")
                ActionButton(
                    "By bearing",
                    onClick = { dialog = MapDialog.MARK_BEARING },
                    modifier = Modifier.weight(1f),
                    enabled = state.canSetMarkFromLine,
                    testTag = "markByBearing",
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Clear mark", onClick = { dialog = MapDialog.CLEAR_MARK }, modifier = Modifier.weight(1f), enabled = state.markIsSet, testTag = "clearMark")
                ActionButton(
                    "Clear track",
                    onClick = { dialog = MapDialog.CLEAR_TRACK },
                    modifier = Modifier.weight(1f),
                    enabled = state.track.isNotEmpty(),
                    testTag = "clearTrack",
                )
            }
            Caption(state.scaleText, modifier = Modifier.fillMaxWidth().testTag("scale"))
            Caption(state.trackText, modifier = Modifier.fillMaxWidth().testTag("trackText"))
            Caption("Arrows: the mean wind in each cell · bright = measured there, dim = estimated from the neighbours · amber veered, blue backed", maxLines = 4)
            Caption("Green line: the fastest course to the mark through that wind · bold heading from the boat: the lifted tack", maxLines = 3)
        },
    )

    when (dialog) {
        MapDialog.MARK_BEARING -> NumberInputDialog(
            title = "Mark bearing from the line",
            initialValue = bearing.toDouble(),
            unit = "°",
            range = 0.0..359.0,
            wrap = true,
            onConfirm = { bearing = it.toInt(); dialog = MapDialog.MARK_DISTANCE },
            onDismiss = { dialog = null },
        )
        MapDialog.MARK_DISTANCE -> NumberInputDialog(
            title = "Mark distance from the line",
            initialValue = DEFAULT_MARK_DISTANCE_METERS,
            unit = "m",
            range = 50.0..5000.0,
            smallStep = 50.0,
            bigStep = 500.0,
            onConfirm = { actions.setMarkFromLine(bearing, it); dialog = null },
            onDismiss = { dialog = null },
        )
        MapDialog.CLEAR_MARK -> ConfirmDialog(
            title = "Clear the mark?",
            text = "The top of the racing area will be used as the windward mark again.",
            confirmText = "Clear",
            onConfirm = { actions.clearMark(); dialog = null },
            onDismiss = { dialog = null },
        )
        MapDialog.CLEAR_TRACK -> ConfirmDialog(
            title = "Clear the track?",
            text = "The track and the wind arrows on the map will be cleared. Wind statistics are kept.",
            confirmText = "Clear",
            onConfirm = { actions.clearTrack(); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

private const val DEFAULT_MARK_DISTANCE_METERS = 1000.0
