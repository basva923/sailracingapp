package com.sailracing.app.ui.wind

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sailracing.app.ui.components.ActionButton
import com.sailracing.app.ui.components.AdaptivePanes
import com.sailracing.app.ui.components.Caption
import com.sailracing.app.ui.components.ConfirmDialog
import com.sailracing.app.ui.components.HistogramChart
import com.sailracing.app.ui.components.HistoryChart
import com.sailracing.app.ui.components.LabeledValue
import com.sailracing.app.ui.components.NumberInputDialog
import com.sailracing.app.ui.components.tackColor
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.domain.wind.WindSettings

class WindActions(
    val setWindDirection: (Int) -> Unit,
    val setTackAngle: (Int) -> Unit,
    val setDownwindAngle: (Int) -> Unit,
    val windFromStarboard: () -> Unit,
    val windFromPort: () -> Unit,
    val resetStatistics: () -> Unit,
)

private enum class WindDialog { DIRECTION, TACK_ANGLE, DOWNWIND_ANGLE, RESET }

@Composable
fun WindScreen(state: WindUiState, actions: WindActions, modifier: Modifier = Modifier) {
    var dialog by rememberSaveable { mutableStateOf<WindDialog?>(null) }
    val shiftColor = when {
        state.shiftDegrees == null -> MaterialTheme.colorScheme.onBackground
        state.shiftDegrees > 0 -> RaceColors.Estimated
        state.shiftDegrees < 0 -> RaceColors.Info
        else -> MaterialTheme.colorScheme.onBackground
    }

    AdaptivePanes(
        modifier = modifier,
        first = { _ ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledValue("Wind", state.configuredWind, Modifier.weight(1f), color = RaceColors.Wind, testTag = "configuredWind")
                LabeledValue("Estimated", state.estimatedWind, Modifier.weight(1f), color = RaceColors.Estimated, testTag = "estimatedWind")
                LabeledValue("Shift", state.shift, Modifier.weight(1f), color = shiftColor, testTag = "shift")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledValue("Heading", state.heading, Modifier.weight(1f), testTag = "heading")
                LabeledValue("Speed", state.speed, Modifier.weight(1f), testTag = "speed")
                LabeledValue(state.pointOfSailLabel, state.tackShort, Modifier.weight(1f), color = tackColor(state.tack), testTag = "tack")
            }
            Caption(state.headingSource.ifEmpty { "No heading" }, modifier = Modifier.fillMaxWidth())
            Caption("Wind direction frequency, ±${WindUiState.HISTOGRAM_HALF_WIDTH}° around the set wind")
            HistogramChart(
                bins = state.histogram.ifEmpty { com.sailracing.domain.wind.WindHistogram().window(0, WindUiState.HISTOGRAM_HALF_WIDTH) },
                estimatedOffsetDegrees = state.estimatedOffset,
                modifier = Modifier.fillMaxWidth().height(170.dp),
            )
            Text(
                state.statistics,
                modifier = Modifier.fillMaxWidth().testTag("statistics"),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Caption("Shift over the last ${WindUiState.HISTORY_SAMPLES} samples")
            HistoryChart(shiftsDegrees = state.shifts, modifier = Modifier.fillMaxWidth().height(120.dp))
        },
        second = { _ ->
            Caption("Set the wind from your heading while close-hauled")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    "Starboard tack",
                    onClick = actions.windFromStarboard,
                    modifier = Modifier.weight(1f),
                    containerColor = RaceColors.Starboard,
                    contentColor = RaceColors.Black,
                    enabled = state.canSetFromHeading,
                    testTag = "windFromStarboard",
                )
                ActionButton(
                    "Port tack",
                    onClick = actions.windFromPort,
                    modifier = Modifier.weight(1f),
                    containerColor = RaceColors.Port,
                    contentColor = RaceColors.Black,
                    enabled = state.canSetFromHeading,
                    testTag = "windFromPort",
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Wind ${state.configuredWind}", onClick = { dialog = WindDialog.DIRECTION }, modifier = Modifier.weight(1f), testTag = "editWind")
                ActionButton("Tack ${state.tackAngle}°", onClick = { dialog = WindDialog.TACK_ANGLE }, modifier = Modifier.weight(1f), testTag = "editTackAngle")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Downwind ${state.downwindAngle}°", onClick = { dialog = WindDialog.DOWNWIND_ANGLE }, modifier = Modifier.weight(1f), testTag = "editDownwindAngle")
                ActionButton("Reset stats", onClick = { dialog = WindDialog.RESET }, modifier = Modifier.weight(1f), testTag = "resetWind")
            }
        },
    )

    when (dialog) {
        WindDialog.DIRECTION -> NumberInputDialog(
            title = "Wind direction",
            initialValue = state.windDirection.toDouble(),
            unit = "°",
            range = 0.0..359.0,
            wrap = true,
            onConfirm = { actions.setWindDirection(it.toInt()); dialog = null },
            onDismiss = { dialog = null },
        )
        WindDialog.TACK_ANGLE -> NumberInputDialog(
            title = "Tack angle (wind to close-hauled course)",
            initialValue = state.tackAngle.toDouble(),
            unit = "°",
            range = WindSettings.TACK_ANGLE_RANGE.first.toDouble()..WindSettings.TACK_ANGLE_RANGE.last.toDouble(),
            bigStep = 5.0,
            onConfirm = { actions.setTackAngle(it.toInt()); dialog = null },
            onDismiss = { dialog = null },
        )
        WindDialog.DOWNWIND_ANGLE -> NumberInputDialog(
            title = "Downwind true wind angle",
            initialValue = state.downwindAngle.toDouble(),
            unit = "°",
            range = WindSettings.DOWNWIND_ANGLE_RANGE.first.toDouble()..WindSettings.DOWNWIND_ANGLE_RANGE.last.toDouble(),
            bigStep = 5.0,
            onConfirm = { actions.setDownwindAngle(it.toInt()); dialog = null },
            onDismiss = { dialog = null },
        )
        WindDialog.RESET -> ConfirmDialog(
            title = "Reset wind statistics?",
            text = "The histogram and the shift history will be cleared. The set wind direction is kept.",
            confirmText = "Reset",
            onConfirm = { actions.resetStatistics(); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}
