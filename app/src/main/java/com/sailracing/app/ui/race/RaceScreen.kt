package com.sailracing.app.ui.race

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.sailracing.app.ui.components.LabeledValue
import com.sailracing.app.ui.components.tackColor
import com.sailracing.app.ui.theme.RaceColors

class RaceActions(
    val windFromStarboard: () -> Unit,
    val windFromPort: () -> Unit,
)

@Composable
fun RaceScreen(state: RaceUiState, actions: RaceActions, modifier: Modifier = Modifier) {
    val steerColor = when (state.steer) {
        Steer.ON_TARGET -> RaceColors.Early
        Steer.HEAD_UP, Steer.BEAR_AWAY -> RaceColors.Warning
        Steer.NONE -> RaceColors.Muted
    }
    AdaptivePanes(
        modifier = modifier,
        first = {
            if (state.raceTime.isNotEmpty()) Caption(state.raceTime, modifier = Modifier.fillMaxWidth().testTag("raceTime"))
            CompassRose(
                headingDegrees = state.headingDegrees,
                windDegrees = state.windDegrees.toDouble(),
                estimatedWindDegrees = state.estimatedWindDegrees,
                targets = state.targets,
                modifier = Modifier.fillMaxWidth(0.8f).align(Alignment.CenterHorizontally).padding(4.dp),
            )
            Text(
                state.steerText.ifEmpty { "Waiting for heading" },
                modifier = Modifier.fillMaxWidth().testTag("steer"),
                style = MaterialTheme.typography.headlineMedium,
                color = steerColor,
                textAlign = TextAlign.Center,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledValue("Heading", state.heading, Modifier.weight(1f), testTag = "heading")
                LabeledValue("Target", state.target, Modifier.weight(1f), color = tackColor(state.tack), testTag = "target")
                LabeledValue("Shift", state.shift, Modifier.weight(1f), testTag = "shift")
            }
        },
        second = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigValue("Speed kn", state.speed, Modifier.weight(1f), maxFontSize = 96.sp, testTag = "speed")
                BigValue("VMG kn", state.vmg, Modifier.weight(1f), maxFontSize = 96.sp, testTag = "vmg")
            }
            Caption(state.tackLabel.ifEmpty { "—" }, modifier = Modifier.fillMaxWidth().testTag("tack"), color = tackColor(state.tack))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledValue("Avg upwind", state.averageUpwind, Modifier.weight(1f), testTag = "avgUpwind")
                LabeledValue("Avg VMG", state.averageUpwindVmg, Modifier.weight(1f), testTag = "avgUpwindVmg")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledValue("Avg downwind", state.averageDownwind, Modifier.weight(1f), testTag = "avgDownwind")
                LabeledValue("Avg VMG", state.averageDownwindVmg, Modifier.weight(1f), testTag = "avgDownwindVmg")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    "Wind ← stbd",
                    onClick = actions.windFromStarboard,
                    modifier = Modifier.weight(1f),
                    containerColor = RaceColors.Starboard,
                    contentColor = RaceColors.Black,
                    enabled = state.canSetFromHeading,
                    testTag = "windFromStarboard",
                )
                ActionButton(
                    "Wind ← port",
                    onClick = actions.windFromPort,
                    modifier = Modifier.weight(1f),
                    containerColor = RaceColors.Port,
                    contentColor = RaceColors.Black,
                    enabled = state.canSetFromHeading,
                    testTag = "windFromPort",
                )
            }
        },
    )
}
