package com.sailracing.app.ui.wind

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.sailracing.app.ui.components.AdaptivePanes
import com.sailracing.app.ui.components.BigValue
import com.sailracing.app.ui.components.Caption
import com.sailracing.app.ui.components.CompassRose
import com.sailracing.app.ui.components.ConfirmDialog
import com.sailracing.app.ui.components.HistogramChart
import com.sailracing.app.ui.components.HistoryChart
import com.sailracing.app.ui.components.LabeledValue
import com.sailracing.app.ui.components.NumberInputDialog
import com.sailracing.app.ui.components.PaneSize
import com.sailracing.app.ui.components.adviceColor
import com.sailracing.app.ui.components.fontSizeFitting
import com.sailracing.app.ui.components.tackColor
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.domain.wind.WindHistogram
import com.sailracing.domain.wind.WindSettings

/** Everything the wind screen can ask of the race: the set wind, the angles and the statistics. */
class WindActions(
    val setWindDirection: (Int) -> Unit,
    val setTackAngle: (Int) -> Unit,
    val setDownwindAngle: (Int) -> Unit,
    val windFromStarboard: () -> Unit,
    val windFromPort: () -> Unit,
    val resetStatistics: () -> Unit,
)

private enum class WindDialog { WIND_DIRECTION, TACK_ANGLE, DOWNWIND_ANGLE, RESET_STATISTICS }

/**
 * The wind in full, for between the beats: the advice with its reason and the wind the boat is
 * measuring, the rose with the VMG and the heading, the set, mean and measured winds, the averages, the
 * buttons that set the wind - from the heading on either tack, or by hand - and the angles, and the
 * statistics: the histogram the shifts are judged against and the shifts themselves. Two panes, stacked
 * upright and side by side with the phone on its side, both of them scrolling.
 */
@Composable
fun WindScreen(wind: WindUiState, actions: WindActions, modifier: Modifier = Modifier) {
    var dialog by rememberSaveable { mutableStateOf<WindDialog?>(null) }

    AdaptivePanes(
        modifier = modifier,
        first = { pane ->
            Advice(wind)
            Numbers(wind, pane)
        },
        second = { _ ->
            WindButtons(wind, onDialog = { dialog = it }, actions = actions)
            Statistics(wind)
        },
    )

    when (dialog) {
        WindDialog.WIND_DIRECTION -> NumberInputDialog(
            title = "Wind direction",
            initialValue = wind.windDirection.toDouble(),
            unit = "°",
            range = 0.0..359.0,
            wrap = true,
            onConfirm = { actions.setWindDirection(it.toInt()); dialog = null },
            onDismiss = { dialog = null },
        )
        WindDialog.TACK_ANGLE -> NumberInputDialog(
            title = "Tack angle (wind to close-hauled course)",
            initialValue = wind.tackAngle.toDouble(),
            unit = "°",
            range = WindSettings.TACK_ANGLE_RANGE.first.toDouble()..WindSettings.TACK_ANGLE_RANGE.last.toDouble(),
            bigStep = 5.0,
            onConfirm = { actions.setTackAngle(it.toInt()); dialog = null },
            onDismiss = { dialog = null },
        )
        WindDialog.DOWNWIND_ANGLE -> NumberInputDialog(
            title = "Downwind true wind angle",
            initialValue = wind.downwindAngle.toDouble(),
            unit = "°",
            range = WindSettings.DOWNWIND_ANGLE_RANGE.first.toDouble()..WindSettings.DOWNWIND_ANGLE_RANGE.last.toDouble(),
            bigStep = 5.0,
            onConfirm = { actions.setDownwindAngle(it.toInt()); dialog = null },
            onDismiss = { dialog = null },
        )
        WindDialog.RESET_STATISTICS -> ConfirmDialog(
            title = "Reset the statistics?",
            text = "The wind histogram, the shift history and the speed averages will be cleared. The set wind direction is kept.",
            confirmText = "Reset",
            onConfirm = { actions.resetStatistics(); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

/** The advice as the race screen shows it, with the whole of its reason, and the wind the boat is measuring. */
@Composable
private fun ColumnScope.Advice(wind: WindUiState) {
    Text(
        wind.adviceTitle,
        modifier = Modifier.fillMaxWidth().testTag("adviceTitle"),
        style = MaterialTheme.typography.headlineMedium,
        color = adviceColor(wind.advice),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
    Caption(wind.adviceDetail, modifier = Modifier.fillMaxWidth().testTag("adviceDetail"), color = RaceColors.White)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledValue("Set wind", wind.configuredWind, Modifier.weight(1f), color = RaceColors.Wind, testTag = "configuredWind")
        LabeledValue("Mean", wind.meanWind, Modifier.weight(1f), testTag = "meanWind")
        LabeledValue("Now", wind.windNow, Modifier.weight(1f), color = if (wind.windNowIsLive) RaceColors.Estimated else RaceColors.Muted, testTag = "windNow")
    }
    Caption(wind.windNowGlance, modifier = Modifier.fillMaxWidth().testTag("windNowGlance"))
}

/** The numbers behind the advice: the rose, and what the boat is doing against the wind. */
@Composable
private fun ColumnScope.Numbers(wind: WindUiState, pane: PaneSize) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        // The rose shares the row with the VMG; both are sized so the row fits a short landscape pane.
        val roseSize = minOf(pane.width * 0.42f, pane.height * 0.4f)
        val vmgFont = fontSizeFitting(pane.height * 0.15f, 56.sp)
        CompassRose(
            headingDegrees = wind.headingDegrees,
            windDegrees = wind.windDegrees.toDouble(),
            estimatedWindDegrees = wind.estimatedWindDegrees,
            targets = wind.targets,
            meanWindDegrees = wind.meanWindDegrees,
            modifier = Modifier.size(roseSize),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BigValue("VMG kn", wind.vmg, maxFontSize = vmgFont, testTag = "vmg")
            LabeledValue("Heading", wind.heading, Modifier.fillMaxWidth(), testTag = "heading")
            Caption(wind.headingSource.ifEmpty { "No heading" }, modifier = Modifier.fillMaxWidth())
        }
    }
    Caption(wind.tackLabel.ifEmpty { "—" }, modifier = Modifier.fillMaxWidth().testTag("tackLabel"), color = tackColor(wind.tack))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledValue("Avg upwind", wind.averageUpwind, Modifier.weight(1f), testTag = "avgUpwind")
        LabeledValue("Avg VMG", wind.averageUpwindVmg, Modifier.weight(1f), testTag = "avgUpwindVmg")
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledValue("Avg downwind", wind.averageDownwind, Modifier.weight(1f), testTag = "avgDownwind")
        LabeledValue("Avg VMG", wind.averageDownwindVmg, Modifier.weight(1f), testTag = "avgDownwindVmg")
    }
}

/** Setting the wind: from the boat's own heading on either tack, or by hand; and the angles it sails. */
@Composable
private fun ColumnScope.WindButtons(wind: WindUiState, onDialog: (WindDialog) -> Unit, actions: WindActions) {
    Caption("Set the wind from your heading while close-hauled", maxLines = 2)
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
        ActionButton("Wind ${wind.configuredWind}", onClick = { onDialog(WindDialog.WIND_DIRECTION) }, modifier = Modifier.weight(1f), testTag = "editWind")
        ActionButton("Tack ${wind.tackAngle}°", onClick = { onDialog(WindDialog.TACK_ANGLE) }, modifier = Modifier.weight(1f), testTag = "editTackAngle")
    }
    Caption(wind.tackAngleMeasured, modifier = Modifier.fillMaxWidth().testTag("tackAngleMeasured"), maxLines = 2)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton("Downwind ${wind.downwindAngle}°", onClick = { onDialog(WindDialog.DOWNWIND_ANGLE) }, modifier = Modifier.weight(1f), testTag = "editDownwindAngle")
        ActionButton("Reset stats", onClick = { onDialog(WindDialog.RESET_STATISTICS) }, modifier = Modifier.weight(1f), testTag = "resetWind")
    }
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
