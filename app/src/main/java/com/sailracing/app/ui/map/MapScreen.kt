package com.sailracing.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.sailracing.app.ui.components.Caption
import com.sailracing.app.ui.components.ConfirmDialog
import com.sailracing.app.ui.components.CourseMap
import com.sailracing.app.ui.components.GlanceLayout
import com.sailracing.app.ui.components.NumberInputDialog
import com.sailracing.app.ui.components.SmallButton
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.domain.strategy.FavouredSide

/** Everything the map screen can ask of the race: the windward mark and the track. */
class MapActions(
    val markHere: () -> Unit,
    val setMarkFromLine: (bearingDegrees: Int, distanceMeters: Double) -> Unit,
    val clearMark: () -> Unit,
    val clearTrack: () -> Unit,
)

private enum class MapDialog { MARK_BEARING, MARK_DISTANCE, CLEAR_MARK, CLEAR_TRACK }

/**
 * The course: the map with the race line to the windward mark on top (the left half with the phone on
 * its side), with what the line costs written under it and a *Fit* button while it is zoomed, and under
 * it, scrolling, which side of the course pays and why, the race line and the flyer in full, the mark and
 * the track with their buttons, and what the drawn things mean.
 */
@Composable
fun MapScreen(map: MapUiState, actions: MapActions, modifier: Modifier = Modifier) {
    var dialog by rememberSaveable { mutableStateOf<MapDialog?>(null) }
    var bearing by rememberSaveable { mutableIntStateOf(0) }
    val camera = rememberMapCameraState()

    GlanceLayout(
        modifier = modifier,
        pinnedFraction = DETAILS_FRACTION,
        main = { MapWithOverlays(map = map, camera = camera) },
        pinned = {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("mapDetails"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Side(map)
                MarkAndTrack(map = map, onDialog = { dialog = it }, actions = actions)
            }
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

/**
 * The map, with the two things worth adding to it: what the race line costs, on a line of its own under
 * the water so it never covers the boat or the line (the start is at the bottom of the map), and a *Fit*
 * button in the corner while the map is zoomed or moved, so the whole area is one tap away.
 */
@Composable
private fun MapWithOverlays(map: MapUiState, camera: MapCameraState) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            CourseMap(state = map, modifier = Modifier.fillMaxSize(), camera = camera)
            if (!camera.isFitted) {
                SmallButton("Fit", onClick = camera::fit, modifier = Modifier.align(Alignment.TopEnd).padding(top = 28.dp), testTag = "fitMap")
            }
        }
        Caption(
            map.raceLineGlance.ifEmpty { "No race line yet" },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("raceLineGlance"),
            color = if (map.raceLineGlance.isEmpty()) RaceColors.Muted else RaceColors.Early,
            maxLines = 1,
        )
    }
}

/** Which side pays and why, and what the drawn lines are worth. */
@Composable
private fun ColumnScope.Side(map: MapUiState) {
    Text(
        map.sideTitle,
        modifier = Modifier.fillMaxWidth().testTag("sideTitle"),
        style = MaterialTheme.typography.titleMedium,
        color = when (map.favouredSide) {
            FavouredSide.LEFT, FavouredSide.RIGHT -> RaceColors.Early
            FavouredSide.EVEN -> RaceColors.White
            FavouredSide.UNKNOWN -> RaceColors.Muted
        },
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
    Caption(map.sideDetail, modifier = Modifier.fillMaxWidth().testTag("sideDetail"), color = RaceColors.White, maxLines = 3)
    Caption(map.raceLineText, modifier = Modifier.fillMaxWidth().testTag("raceLineText"), color = RaceColors.White, maxLines = 2)
    Caption(map.riskText, modifier = Modifier.fillMaxWidth().testTag("riskText"), color = RaceColors.Warning, maxLines = 3)
}

/** The windward mark, the track, and what the map is showing. */
@Composable
private fun ColumnScope.MarkAndTrack(map: MapUiState, onDialog: (MapDialog) -> Unit, actions: MapActions) {
    Caption(map.markText, modifier = Modifier.fillMaxWidth().testTag("markText"), color = RaceColors.White, maxLines = 3)
    Caption("Set the windward mark at the boat, or by bearing and distance from the line", maxLines = 3)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton("Mark here", onClick = actions.markHere, modifier = Modifier.weight(1f), enabled = map.canMarkHere, testTag = "markHere")
        ActionButton(
            "By bearing",
            onClick = { onDialog(MapDialog.MARK_BEARING) },
            modifier = Modifier.weight(1f),
            enabled = map.canSetMarkFromLine,
            testTag = "markByBearing",
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton("Clear mark", onClick = { onDialog(MapDialog.CLEAR_MARK) }, modifier = Modifier.weight(1f), enabled = map.markIsSet, testTag = "clearMark")
        ActionButton(
            "Clear track",
            onClick = { onDialog(MapDialog.CLEAR_TRACK) },
            modifier = Modifier.weight(1f),
            enabled = map.track.isNotEmpty(),
            testTag = "clearTrack",
        )
    }
    Caption(map.scaleText, modifier = Modifier.fillMaxWidth().testTag("scale"))
    Caption(map.trackText, modifier = Modifier.fillMaxWidth().testTag("trackText"))
    Caption("Pinch the map to zoom, drag to move, double tap or the Fit button to fit the racing area again", maxLines = 3)
    Caption(
        "Arrows: the wind in each square · solid where it was measured there, faint where it is mostly the " +
            "day's wind · amber veered, blue backed",
        maxLines = 4,
    )
    Caption(
        "Green line: the race line to sail, board by board, the safest of many simulated winds · " +
            "dashed amber: the flyer, quicker when the wind is kind · bold heading from the boat: the lifted tack",
        maxLines = 4,
    )
}

/** Upright, the map keeps most of the screen: what is under it scrolls. */
private const val DETAILS_FRACTION = 0.32f

private const val DEFAULT_MARK_DISTANCE_METERS = 1000.0
