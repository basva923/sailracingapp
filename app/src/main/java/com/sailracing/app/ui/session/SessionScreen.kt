package com.sailracing.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.sailracing.app.ui.components.ActionButton
import com.sailracing.app.ui.components.AdaptivePanes
import com.sailracing.app.ui.components.Caption
import com.sailracing.app.ui.components.ConfirmDialog
import com.sailracing.app.ui.components.StatusChip
import com.sailracing.app.ui.theme.RaceColors

class SessionActions(
    val start: () -> Unit,
    val end: () -> Unit,
    val clear: () -> Unit,
)

private enum class SessionDialog { END, CLEAR }

/**
 * The central place to start, end and clear a session, with an overview of what the session holds.
 * Starting switches on the GPS, the ticker and the beeps; ending switches them off but keeps the data;
 * clearing forgets the line, the mark, the track and every statistic for the next race.
 */
@Composable
fun SessionScreen(state: SessionUiState, actions: SessionActions, modifier: Modifier = Modifier) {
    var dialog by rememberSaveable { mutableStateOf<SessionDialog?>(null) }
    val titleColor = when (state.status) {
        SessionStatus.RUNNING -> RaceColors.Early
        SessionStatus.ENDED -> RaceColors.Warning
        SessionStatus.NONE -> RaceColors.Muted
    }

    AdaptivePanes(
        modifier = modifier,
        first = { _ ->
            Text(
                state.title,
                modifier = Modifier.fillMaxWidth().testTag("sessionTitle"),
                style = MaterialTheme.typography.displaySmall,
                color = titleColor,
                textAlign = TextAlign.Center,
            )
            Caption(state.detail, modifier = Modifier.fillMaxWidth().testTag("sessionDetail"), color = RaceColors.White, maxLines = 3)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatusChip(state.gpsStatus, state.gpsOk, Modifier.testTag("sessionGps"))
                Caption("Since ${state.since}", modifier = Modifier.testTag("sessionSince"))
            }
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow("Track", state.track, "sessionTrack")
                InfoRow("Wind", state.wind, "sessionWind")
                InfoRow("Start line", state.line, "sessionLine")
                InfoRow("Windward mark", state.mark, "sessionMark")
                InfoRow("Timer", state.timer, "sessionTimer")
            }
        },
        second = { _ ->
            if (state.status == SessionStatus.RUNNING) {
                ActionButton(
                    "End session",
                    onClick = { dialog = SessionDialog.END },
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = RaceColors.Late,
                    contentColor = RaceColors.Black,
                    testTag = "endSession",
                )
            } else {
                ActionButton(
                    "Start session",
                    onClick = actions.start,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = RaceColors.Early,
                    contentColor = RaceColors.Black,
                    testTag = "startSession",
                )
            }
            ActionButton(
                "Clear session",
                onClick = { dialog = SessionDialog.CLEAR },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.hasData,
                testTag = "clearSession",
            )
            Caption("Start: GPS on, wind measured, countdown beeps. End: GPS off, data kept.", maxLines = 3)
            Caption("Clear: forget the line, the mark, the track and the statistics for the next race.", maxLines = 3)
        },
    )

    when (dialog) {
        SessionDialog.END -> ConfirmDialog(
            title = "End the session?",
            text = "GPS tracking and the countdown stop. What was measured is kept until you clear it.",
            confirmText = "End",
            onConfirm = { actions.end(); dialog = null },
            onDismiss = { dialog = null },
        )
        SessionDialog.CLEAR -> ConfirmDialog(
            title = "Clear the session?",
            text = "The start line, the windward mark, the timer, the track and all wind and speed statistics are forgotten. The set wind and the settings are kept.",
            confirmText = "Clear",
            onConfirm = { actions.clear(); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun InfoRow(label: String, value: String, testTag: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Caption(label, modifier = Modifier.weight(0.4f))
        Text(
            value,
            modifier = Modifier.weight(0.6f).testTag(testTag),
            style = MaterialTheme.typography.titleMedium,
            color = RaceColors.White,
            maxLines = 2,
        )
    }
}
