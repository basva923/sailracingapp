package com.sailracing.app.ui.race

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.sp
import com.sailracing.app.ui.components.ActionButton
import com.sailracing.app.ui.components.BigValue
import com.sailracing.app.ui.components.Caption
import com.sailracing.app.ui.components.CompassRose
import com.sailracing.app.ui.components.ConfirmDialog
import com.sailracing.app.ui.components.CourseMap
import com.sailracing.app.ui.components.HistogramChart
import com.sailracing.app.ui.components.HistoryChart
import com.sailracing.app.ui.components.LabeledValue
import com.sailracing.app.ui.components.MapAndPanel
import com.sailracing.app.ui.components.NumberInputDialog
import com.sailracing.app.ui.components.PaneSize
import com.sailracing.app.ui.components.adviceColor
import com.sailracing.app.ui.components.fontSizeFitting
import com.sailracing.app.ui.components.shiftColor
import com.sailracing.app.ui.components.tackColor
import com.sailracing.app.ui.map.MapCameraState
import com.sailracing.app.ui.map.MapUiState
import com.sailracing.app.ui.map.rememberMapCameraState
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.app.ui.wind.WindUiState
import com.sailracing.domain.strategy.FavouredSide
import com.sailracing.domain.wind.WindHistogram
import com.sailracing.domain.wind.WindSettings

/** Everything the racing screen can ask of the race: the wind, the statistics, the mark and the track. */
class RaceActions(
    val setWindDirection: (Int) -> Unit,
    val setTackAngle: (Int) -> Unit,
    val setDownwindAngle: (Int) -> Unit,
    val windFromStarboard: () -> Unit,
    val windFromPort: () -> Unit,
    val resetStatistics: () -> Unit,
    val markHere: () -> Unit,
    val setMarkFromLine: (bearingDegrees: Int, distanceMeters: Double) -> Unit,
    val clearMark: () -> Unit,
    val clearTrack: () -> Unit,
)

private enum class RaceDialog {
    WIND_DIRECTION,
    TACK_ANGLE,
    DOWNWIND_ANGLE,
    RESET_STATISTICS,
    MARK_BEARING,
    MARK_DISTANCE,
    CLEAR_MARK,
    CLEAR_TRACK,
}

/**
 * The screen to race by: the map of the way to the windward mark, as big as the screen allows, and
 * underneath it - or beside it, with the phone on its side - everything else, in the order it matters
 * while beating: whether to tack, which side pays, the numbers, the wind, the mark and the statistics.
 * Beating, the sailor only looks at the top of it.
 */
@Composable
fun RaceScreen(map: MapUiState, wind: WindUiState, actions: RaceActions, modifier: Modifier = Modifier) {
    var dialog by rememberSaveable { mutableStateOf<RaceDialog?>(null) }
    var bearing by rememberSaveable { mutableIntStateOf(0) }
    val camera = rememberMapCameraState()

    MapAndPanel(
        modifier = modifier,
        // A tall beat is worth a tall map; a square racing area would only get bands of nothing from one.
        mapShape = map.view.shape,
        map = {
            CourseMap(state = map, modifier = Modifier.fillMaxSize(), camera = camera)
        },
        panel = { pane ->
            Advice(map, wind)
            Numbers(wind, pane)
            WindButtons(wind, onDialog = { dialog = it }, actions = actions)
            MarkAndMap(map = map, camera = camera, onDialog = { dialog = it }, actions = actions)
            Statistics(wind)
        },
    )

    when (dialog) {
        RaceDialog.WIND_DIRECTION -> NumberInputDialog(
            title = "Wind direction",
            initialValue = wind.windDirection.toDouble(),
            unit = "°",
            range = 0.0..359.0,
            wrap = true,
            onConfirm = { actions.setWindDirection(it.toInt()); dialog = null },
            onDismiss = { dialog = null },
        )
        RaceDialog.TACK_ANGLE -> NumberInputDialog(
            title = "Tack angle (wind to close-hauled course)",
            initialValue = wind.tackAngle.toDouble(),
            unit = "°",
            range = WindSettings.TACK_ANGLE_RANGE.first.toDouble()..WindSettings.TACK_ANGLE_RANGE.last.toDouble(),
            bigStep = 5.0,
            onConfirm = { actions.setTackAngle(it.toInt()); dialog = null },
            onDismiss = { dialog = null },
        )
        RaceDialog.DOWNWIND_ANGLE -> NumberInputDialog(
            title = "Downwind true wind angle",
            initialValue = wind.downwindAngle.toDouble(),
            unit = "°",
            range = WindSettings.DOWNWIND_ANGLE_RANGE.first.toDouble()..WindSettings.DOWNWIND_ANGLE_RANGE.last.toDouble(),
            bigStep = 5.0,
            onConfirm = { actions.setDownwindAngle(it.toInt()); dialog = null },
            onDismiss = { dialog = null },
        )
        RaceDialog.RESET_STATISTICS -> ConfirmDialog(
            title = "Reset the statistics?",
            text = "The wind histogram, the shift history and the speed averages will be cleared. The set wind direction is kept.",
            confirmText = "Reset",
            onConfirm = { actions.resetStatistics(); dialog = null },
            onDismiss = { dialog = null },
        )
        RaceDialog.MARK_BEARING -> NumberInputDialog(
            title = "Mark bearing from the line",
            initialValue = bearing.toDouble(),
            unit = "°",
            range = 0.0..359.0,
            wrap = true,
            onConfirm = { bearing = it.toInt(); dialog = RaceDialog.MARK_DISTANCE },
            onDismiss = { dialog = null },
        )
        RaceDialog.MARK_DISTANCE -> NumberInputDialog(
            title = "Mark distance from the line",
            initialValue = DEFAULT_MARK_DISTANCE_METERS,
            unit = "m",
            range = 50.0..5000.0,
            smallStep = 50.0,
            bigStep = 500.0,
            onConfirm = { actions.setMarkFromLine(bearing, it); dialog = null },
            onDismiss = { dialog = null },
        )
        RaceDialog.CLEAR_MARK -> ConfirmDialog(
            title = "Clear the mark?",
            text = "The top of the racing area will be used as the windward mark again.",
            confirmText = "Clear",
            onConfirm = { actions.clearMark(); dialog = null },
            onDismiss = { dialog = null },
        )
        RaceDialog.CLEAR_TRACK -> ConfirmDialog(
            title = "Clear the track?",
            text = "The track and the wind arrows on the map will be cleared. Wind statistics are kept.",
            confirmText = "Clear",
            onConfirm = { actions.clearTrack(); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

/** What to do about it: tack or hold, which side pays, and what the drawn lines are worth. */
@Composable
private fun ColumnScope.Advice(map: MapUiState, wind: WindUiState) {
    val sideColor = when (map.favouredSide) {
        FavouredSide.LEFT, FavouredSide.RIGHT -> RaceColors.Early
        FavouredSide.EVEN -> RaceColors.White
        FavouredSide.UNKNOWN -> RaceColors.Muted
    }
    if (wind.raceTime.isNotEmpty()) Caption(wind.raceTime, modifier = Modifier.fillMaxWidth().testTag("raceTime"))
    Text(
        wind.adviceTitle,
        modifier = Modifier.fillMaxWidth().testTag("advice"),
        style = MaterialTheme.typography.displaySmall,
        color = adviceColor(wind.advice),
        textAlign = TextAlign.Center,
    )
    Caption(wind.adviceDetail, modifier = Modifier.fillMaxWidth().testTag("adviceDetail"), color = RaceColors.White)
    Text(
        map.sideTitle,
        modifier = Modifier.fillMaxWidth().testTag("sideTitle"),
        style = MaterialTheme.typography.headlineMedium,
        color = sideColor,
        textAlign = TextAlign.Center,
    )
    Caption(map.sideDetail, modifier = Modifier.fillMaxWidth().testTag("sideDetail"), color = RaceColors.White, maxLines = 3)
    Caption(map.raceLineText, modifier = Modifier.fillMaxWidth().testTag("raceLineText"), color = RaceColors.White, maxLines = 2)
    Caption(map.riskText, modifier = Modifier.fillMaxWidth().testTag("riskText"), color = RaceColors.Warning, maxLines = 3)
}

/** The numbers behind the advice: what the boat is doing and what the wind is doing. */
@Composable
private fun ColumnScope.Numbers(wind: WindUiState, pane: PaneSize) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        // The rose shares the row with the speeds; both are sized so the row fits a short landscape pane.
        val roseSize = minOf(pane.width * 0.42f, pane.height * 0.32f)
        val speedFont = fontSizeFitting(pane.height * 0.12f, 56.sp)
        CompassRose(
            headingDegrees = wind.headingDegrees,
            windDegrees = wind.windDegrees.toDouble(),
            estimatedWindDegrees = wind.estimatedWindDegrees,
            targets = wind.targets,
            meanWindDegrees = wind.meanWindDegrees,
            modifier = Modifier.size(roseSize),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BigValue("Speed kn", wind.speed, maxFontSize = speedFont, testTag = "speed")
            BigValue("VMG kn", wind.vmg, maxFontSize = speedFont, testTag = "vmg")
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledValue("Set wind", wind.configuredWind, Modifier.weight(1f), color = RaceColors.Wind, testTag = "configuredWind")
        LabeledValue("Mean", wind.meanWind, Modifier.weight(1f), testTag = "meanWind")
        LabeledValue("Now", wind.estimatedWind, Modifier.weight(1f), color = RaceColors.Estimated, testTag = "estimatedWind")
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledValue("Heading", wind.heading, Modifier.weight(1f), testTag = "heading")
        LabeledValue("Shift", wind.shift, Modifier.weight(1f), color = shiftColor(wind.shiftDegrees), testTag = "shift")
        LabeledValue(wind.pointOfSailLabel, wind.tackShort, Modifier.weight(1f), color = tackColor(wind.tack), testTag = "tack")
    }
    Caption(wind.headingSource.ifEmpty { "No heading" }, modifier = Modifier.fillMaxWidth())
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledValue("Avg upwind", wind.averageUpwind, Modifier.weight(1f), testTag = "avgUpwind")
        LabeledValue("Avg VMG", wind.averageUpwindVmg, Modifier.weight(1f), testTag = "avgUpwindVmg")
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledValue("Avg downwind", wind.averageDownwind, Modifier.weight(1f), testTag = "avgDownwind")
        LabeledValue("Avg VMG", wind.averageDownwindVmg, Modifier.weight(1f), testTag = "avgDownwindVmg")
    }
}

/** Setting the wind: from the boat's own heading on either tack, or by hand. */
@Composable
private fun ColumnScope.WindButtons(wind: WindUiState, onDialog: (RaceDialog) -> Unit, actions: RaceActions) {
    Caption("Set the wind from your heading while close-hauled")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton(
            "Starboard tack",
            onClick = actions.windFromStarboard,
            modifier = Modifier.weight(1f),
            containerColor = RaceColors.Starboard,
            contentColor = RaceColors.Black,
            enabled = wind.canSetFromHeading,
            testTag = "windFromStarboard",
        )
        ActionButton(
            "Port tack",
            onClick = actions.windFromPort,
            modifier = Modifier.weight(1f),
            containerColor = RaceColors.Port,
            contentColor = RaceColors.Black,
            enabled = wind.canSetFromHeading,
            testTag = "windFromPort",
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton("Wind ${wind.configuredWind}", onClick = { onDialog(RaceDialog.WIND_DIRECTION) }, modifier = Modifier.weight(1f), testTag = "editWind")
        ActionButton("Tack ${wind.tackAngle}°", onClick = { onDialog(RaceDialog.TACK_ANGLE) }, modifier = Modifier.weight(1f), testTag = "editTackAngle")
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton("Downwind ${wind.downwindAngle}°", onClick = { onDialog(RaceDialog.DOWNWIND_ANGLE) }, modifier = Modifier.weight(1f), testTag = "editDownwindAngle")
        ActionButton("Reset stats", onClick = { onDialog(RaceDialog.RESET_STATISTICS) }, modifier = Modifier.weight(1f), testTag = "resetWind")
    }
    Caption(wind.tackLabel.ifEmpty { "—" }, modifier = Modifier.fillMaxWidth().testTag("tackLabel"), color = tackColor(wind.tack))
}

/** The windward mark, the track, and what the map above is showing. */
@Composable
private fun ColumnScope.MarkAndMap(
    map: MapUiState,
    camera: MapCameraState,
    onDialog: (RaceDialog) -> Unit,
    actions: RaceActions,
) {
    Caption(map.markText, modifier = Modifier.fillMaxWidth().testTag("markText"), color = RaceColors.White, maxLines = 3)
    Caption("Set the windward mark at the boat, or by bearing and distance from the line", maxLines = 3)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton("Mark here", onClick = actions.markHere, modifier = Modifier.weight(1f), enabled = map.canMarkHere, testTag = "markHere")
        ActionButton(
            "By bearing",
            onClick = { onDialog(RaceDialog.MARK_BEARING) },
            modifier = Modifier.weight(1f),
            enabled = map.canSetMarkFromLine,
            testTag = "markByBearing",
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton("Clear mark", onClick = { onDialog(RaceDialog.CLEAR_MARK) }, modifier = Modifier.weight(1f), enabled = map.markIsSet, testTag = "clearMark")
        ActionButton(
            "Clear track",
            onClick = { onDialog(RaceDialog.CLEAR_TRACK) },
            modifier = Modifier.weight(1f),
            enabled = map.track.isNotEmpty(),
            testTag = "clearTrack",
        )
    }
    ActionButton("Fit the map", onClick = camera::fit, modifier = Modifier.fillMaxWidth(), enabled = !camera.isFitted, testTag = "fitMap")
    Caption(map.scaleText, modifier = Modifier.fillMaxWidth().testTag("scale"))
    Caption(map.trackText, modifier = Modifier.fillMaxWidth().testTag("trackText"))
    Caption("Pinch the map to zoom, drag to move, double tap to fit the racing area again", maxLines = 2)
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

/** What the wind has been doing: the histogram it is judged against and the shifts it went through. */
@Composable
private fun ColumnScope.Statistics(wind: WindUiState) {
    Caption(wind.histogramCaption)
    HistogramChart(
        bins = wind.histogram.ifEmpty { WindHistogram().window(0, WindUiState.HISTOGRAM_HALF_WIDTH) },
        estimatedOffsetDegrees = wind.estimatedOffset,
        setOffsetDegrees = wind.setOffset,
        modifier = Modifier.fillMaxWidth().height(150.dp),
    )
    Text(
        wind.statistics,
        modifier = Modifier.fillMaxWidth().testTag("statistics"),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Caption("Shift over the last ${WindUiState.HISTORY_SAMPLES} samples")
    HistoryChart(shiftsDegrees = wind.shifts, modifier = Modifier.fillMaxWidth().height(100.dp))
}

private const val DEFAULT_MARK_DISTANCE_METERS = 1000.0
