package com.sailracing.app.ui.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.sailracing.app.ui.components.fontSizeFitting
import com.sailracing.app.ui.components.ConfirmDialog
import com.sailracing.app.ui.components.StatusChip
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.domain.timer.RacePhase

/** All the sailor's actions on the start screen. */
class StartActions(
    val markPin: () -> Unit,
    val clearPin: () -> Unit,
    val markBoat: () -> Unit,
    val clearBoat: () -> Unit,
    val startCountdown: (minutes: Int) -> Unit,
    val sync: () -> Unit,
    val stop: () -> Unit,
)

private enum class LineEnd { PIN, BOAT }

@Composable
fun StartScreen(state: StartUiState, actions: StartActions, modifier: Modifier = Modifier) {
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    var lineEndDialog by rememberSaveable { mutableStateOf<LineEnd?>(null) }

    AdaptivePanes(
        modifier = modifier,
        first = { pane ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatusChip(state.gpsStatus, state.gpsOk, Modifier.testTag("gpsStatus"))
                Caption(state.approachSpeed, modifier = Modifier.weight(1f, fill = false))
            }
            BigValue(
                label = state.clockLabel,
                value = state.clock,
                maxFontSize = fontSizeFitting(pane.height * 0.28f, 140.sp),
                testTag = "clock",
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val secondaryMax = fontSizeFitting(pane.height * 0.20f, 72.sp)
                BigValue(
                    label = "Time to kill",
                    value = state.timeToKill,
                    modifier = Modifier.weight(1f),
                    color = when (state.urgency) {
                        Urgency.EARLY -> RaceColors.Early
                        Urgency.LATE -> RaceColors.Late
                        Urgency.NEUTRAL -> MaterialTheme.colorScheme.onBackground
                    },
                    maxFontSize = secondaryMax,
                    testTag = "timeToKill",
                )
                BigValue(
                    label = "To line",
                    value = state.distanceToLine,
                    modifier = Modifier.weight(1f),
                    color = if (state.overLine) RaceColors.Late else MaterialTheme.colorScheme.onBackground,
                    maxFontSize = secondaryMax,
                    testTag = "distanceToLine",
                )
            }
            if (state.overEarly) {
                Text(
                    "OVER THE LINE",
                    modifier = Modifier.fillMaxWidth().testTag("overEarly"),
                    color = RaceColors.Late,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
            }
        },
        second = { _ ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionButton(
                    text = if (state.pinSet) "Pin ✓" else "Pin",
                    onClick = { if (state.pinSet) lineEndDialog = LineEnd.PIN else actions.markPin() },
                    modifier = Modifier.weight(1f),
                    containerColor = if (state.pinSet) RaceColors.Early else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (state.pinSet) RaceColors.Black else MaterialTheme.colorScheme.onSurface,
                    enabled = state.canMark || state.pinSet,
                    testTag = "pinButton",
                )
                Caption(state.lineLength, modifier = Modifier.weight(1f).testTag("lineLength"))
                ActionButton(
                    text = if (state.boatSet) "Boat ✓" else "Boat",
                    onClick = { if (state.boatSet) lineEndDialog = LineEnd.BOAT else actions.markBoat() },
                    modifier = Modifier.weight(1f),
                    containerColor = if (state.boatSet) RaceColors.Early else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (state.boatSet) RaceColors.Black else MaterialTheme.colorScheme.onSurface,
                    enabled = state.canMark || state.boatSet,
                    testTag = "boatButton",
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (minutes in listOf(5, 4, 1)) {
                    ActionButton(
                        text = "$minutes min",
                        onClick = { actions.startCountdown(minutes) },
                        modifier = Modifier.weight(1f),
                        containerColor = RaceColors.Warning,
                        contentColor = RaceColors.Black,
                        testTag = "start${minutes}",
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    text = "Sync",
                    onClick = actions.sync,
                    modifier = Modifier.weight(2f),
                    containerColor = RaceColors.Info,
                    contentColor = RaceColors.Black,
                    enabled = state.phase == RacePhase.COUNTDOWN,
                    testTag = "sync",
                )
                ActionButton(
                    text = "Stop",
                    onClick = { confirmStop = true },
                    modifier = Modifier.weight(1f),
                    containerColor = RaceColors.Late,
                    contentColor = RaceColors.Black,
                    enabled = state.phase != RacePhase.SETUP,
                    testTag = "stop",
                )
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Caption("Pin / Boat: mark the line where you are")
                Caption("Sync: round to the nearest minute")
            }
        },
    )

    if (confirmStop) {
        ConfirmDialog(
            title = "Stop the timer?",
            text = "The countdown or race time will be cleared.",
            confirmText = "Stop",
            onConfirm = { confirmStop = false; actions.stop() },
            onDismiss = { confirmStop = false },
        )
    }
    lineEndDialog?.let { end ->
        val name = if (end == LineEnd.PIN) "pin end" else "boat end"
        ConfirmDialog(
            title = "Re-mark the $name?",
            text = "Move the $name to your current position, or clear it with Cancel and the button afterwards.",
            confirmText = "Re-mark here",
            onConfirm = {
                lineEndDialog = null
                if (end == LineEnd.PIN) actions.markPin() else actions.markBoat()
            },
            onDismiss = {
                lineEndDialog = null
                if (end == LineEnd.PIN) actions.clearPin() else actions.clearBoat()
            },
        )
    }
}
