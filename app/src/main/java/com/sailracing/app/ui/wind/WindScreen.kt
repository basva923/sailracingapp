package com.sailracing.app.ui.wind

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
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
import com.sailracing.app.ui.components.fontSizeFitting
import com.sailracing.app.ui.components.tackColor
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.domain.strategy.TackAdvice
import com.sailracing.domain.wind.WindHistogram
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

/**
 * The racing screen: whether to hold or tack, judged against the weighted centre of the wind histogram,
 * with the compass rose, the wind numbers and the statistics; the second pane sets the wind. The set wind
 * is only a rough seed: once the histogram has enough samples its mean is the reference everywhere.
 * The helm steers to the sails, so there is no steering hint here.
 */
@Composable
fun WindScreen(state: WindUiState, actions: WindActions, modifier: Modifier = Modifier) {
    var dialog by rememberSaveable { mutableStateOf<WindDialog?>(null) }

    AdaptivePanes(
        modifier = modifier,
        first = { pane ->
            if (state.raceTime.isNotEmpty()) Caption(state.raceTime, modifier = Modifier.fillMaxWidth().testTag("raceTime"))
            Text(
                state.adviceTitle,
                modifier = Modifier.fillMaxWidth().testTag("advice"),
                style = MaterialTheme.typography.displaySmall,
                color = adviceColor(state.advice),
                textAlign = TextAlign.Center,
            )
            Caption(state.adviceDetail, modifier = Modifier.fillMaxWidth().testTag("adviceDetail"), color = RaceColors.White)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // The rose shares the row with the speeds; both are sized so the row fits a short landscape pane.
                val roseSize = minOf(pane.width * 0.48f, pane.height * 0.5f)
                val speedFont = fontSizeFitting(pane.height * 0.17f, 64.sp)
                CompassRose(
                    headingDegrees = state.headingDegrees,
                    windDegrees = state.windDegrees.toDouble(),
                    estimatedWindDegrees = state.estimatedWindDegrees,
                    targets = state.targets,
                    meanWindDegrees = state.meanWindDegrees,
                    modifier = Modifier.size(roseSize),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigValue("Speed kn", state.speed, maxFontSize = speedFont, testTag = "speed")
                    BigValue("VMG kn", state.vmg, maxFontSize = speedFont, testTag = "vmg")
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledValue("Set wind", state.configuredWind, Modifier.weight(1f), color = RaceColors.Wind, testTag = "configuredWind")
                LabeledValue("Mean", state.meanWind, Modifier.weight(1f), testTag = "meanWind")
                LabeledValue("Now", state.estimatedWind, Modifier.weight(1f), color = RaceColors.Estimated, testTag = "estimatedWind")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledValue("Heading", state.heading, Modifier.weight(1f), testTag = "heading")
                LabeledValue("Shift", state.shift, Modifier.weight(1f), color = shiftColor(state.shiftDegrees), testTag = "shift")
                LabeledValue(state.pointOfSailLabel, state.tackShort, Modifier.weight(1f), color = tackColor(state.tack), testTag = "tack")
            }
            Caption(state.headingSource.ifEmpty { "No heading" }, modifier = Modifier.fillMaxWidth())
            Caption(state.histogramCaption)
            HistogramChart(
                bins = state.histogram.ifEmpty { WindHistogram().window(0, WindUiState.HISTOGRAM_HALF_WIDTH) },
                estimatedOffsetDegrees = state.estimatedOffset,
                setOffsetDegrees = state.setOffset,
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            Text(
                state.statistics,
                modifier = Modifier.fillMaxWidth().testTag("statistics"),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Caption("Shift over the last ${WindUiState.HISTORY_SAMPLES} samples")
            HistoryChart(shiftsDegrees = state.shifts, modifier = Modifier.fillMaxWidth().height(100.dp))
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
            Caption(state.tackLabel.ifEmpty { "—" }, modifier = Modifier.fillMaxWidth().testTag("tackLabel"), color = tackColor(state.tack))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledValue("Avg upwind", state.averageUpwind, Modifier.weight(1f), testTag = "avgUpwind")
                LabeledValue("Avg VMG", state.averageUpwindVmg, Modifier.weight(1f), testTag = "avgUpwindVmg")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledValue("Avg downwind", state.averageDownwind, Modifier.weight(1f), testTag = "avgDownwind")
                LabeledValue("Avg VMG", state.averageDownwindVmg, Modifier.weight(1f), testTag = "avgDownwindVmg")
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
            title = "Reset the statistics?",
            text = "The wind histogram, the shift history and the speed averages will be cleared. The set wind direction is kept.",
            confirmText = "Reset",
            onConfirm = { actions.resetStatistics(); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}
