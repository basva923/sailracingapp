package com.sailracing.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sailracing.app.data.AppSettings
import com.sailracing.app.ui.components.ActionButton
import com.sailracing.app.ui.components.ConfirmDialog
import com.sailracing.app.ui.components.NumberInputDialog
import com.sailracing.app.ui.format.Formatters
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.domain.race.ApproachSpeed
import com.sailracing.domain.timer.CuePolicy
import java.util.Locale

class SettingsActions(
    val update: ((AppSettings) -> AppSettings) -> Unit,
    val resetWindStatistics: () -> Unit,
    val resetSpeedStatistics: () -> Unit,
    val endSession: () -> Unit,
)

private enum class SettingsDialog { APPROACH_SPEED, TEN_SECOND_WINDOW, SECOND_WINDOW, UPWIND_MAX_TWA, SIM_SPEED, RESET_STATS, END_SESSION }

@Composable
fun SettingsScreen(settings: AppSettings, actions: SettingsActions, modifier: Modifier = Modifier, versionName: String = "") {
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    val race = settings.race
    val approach = race.approachSpeed
    val approachManual = approach is ApproachSpeed.Manual
    val approachSpeedMps = when (approach) {
        is ApproachSpeed.Manual -> approach.speedMps
        is ApproachSpeed.AverageUpwindVmg -> approach.fallbackMps
    }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp).testTag("settingsList")) {
        SectionHeader("Start")
            SwitchRow(
                title = "Manual approach speed",
                subtitle = if (approachManual) "Time to line uses the speed below" else "Time to line uses the measured upwind VMG, falling back to the speed below",
                checked = approachManual,
                onCheckedChange = { manual ->
                    actions.update { current ->
                        val speed = when (val a = current.race.approachSpeed) {
                            is ApproachSpeed.Manual -> a.speedMps
                            is ApproachSpeed.AverageUpwindVmg -> a.fallbackMps
                        }
                        current.copy(race = current.race.copy(approachSpeed = if (manual) ApproachSpeed.Manual(speed) else ApproachSpeed.AverageUpwindVmg(speed)))
                    }
                },
                testTag = "manualApproach",
            )
            ValueRow(
                title = if (approachManual) "Approach speed" else "Fallback approach speed",
                value = Formatters.knots(approachSpeedMps),
                onClick = { dialog = SettingsDialog.APPROACH_SPEED },
                testTag = "approachSpeed",
            )

        SectionHeader("Signals")
            SwitchRow(
                title = "Beeps",
                subtitle = "Every minute, every 10 s in the last minutes, every second in the last 10 s, and at the start",
                checked = race.cuePolicy.enabled,
                onCheckedChange = { enabled -> actions.update { it.copy(race = it.race.copy(cuePolicy = it.race.cuePolicy.copy(enabled = enabled))) } },
                testTag = "beeps",
            )
            ValueRow(
                title = "10-second beeps in the last",
                value = "${race.cuePolicy.tenSecondWindowSeconds} s",
                onClick = { dialog = SettingsDialog.TEN_SECOND_WINDOW },
                testTag = "tenSecondWindow",
            )
            ValueRow(
                title = "Beep every second in the last",
                value = "${race.cuePolicy.secondWindowSeconds} s",
                onClick = { dialog = SettingsDialog.SECOND_WINDOW },
                testTag = "secondWindow",
            )
            SwitchRow(
                title = "Vibrate with beeps",
                subtitle = "Useful when the phone is in a pocket",
                checked = settings.vibrate,
                onCheckedChange = { vibrate -> actions.update { it.copy(vibrate = vibrate) } },
                testTag = "vibrate",
            )

        SectionHeader("Wind and heading")
            ValueRow(
                title = "Count as upwind up to",
                value = "${race.upwindMaxTwaDegrees}° off the wind",
                onClick = { dialog = SettingsDialog.UPWIND_MAX_TWA },
                testTag = "upwindMaxTwa",
            )
            SwitchRow(
                title = "Phone mounted backwards",
                subtitle = "Adds 180° to the compass heading. The GPS course is used whenever the boat moves.",
                checked = race.compassOffsetDegrees == 180,
                onCheckedChange = { reversed -> actions.update { it.copy(race = it.race.copy(compassOffsetDegrees = if (reversed) 180 else 0)) } },
                testTag = "phoneReversed",
            )

        SectionHeader("Display")
            SwitchRow(
                title = "Keep the screen on",
                subtitle = "Pure black backgrounds keep AMOLED power use low",
                checked = settings.keepScreenOn,
                onCheckedChange = { on -> actions.update { it.copy(keepScreenOn = on) } },
                testTag = "keepScreenOn",
            )

        SectionHeader("Simulation")
            SwitchRow(
                title = "Simulate a race",
                subtitle = "Replaces the GPS with a scripted boat sailing a full race around a course",
                checked = settings.simulation.enabled,
                onCheckedChange = { enabled -> actions.update { it.copy(simulation = it.simulation.copy(enabled = enabled)) } },
                testTag = "simulationEnabled",
            )
            SwitchRow(
                title = "Press the buttons automatically",
                subtitle = "The scripted sailor marks the line, sets the wind and starts the countdown",
                checked = settings.simulation.autoPlayActions,
                onCheckedChange = { auto -> actions.update { it.copy(simulation = it.simulation.copy(autoPlayActions = auto)) } },
                testTag = "simulationAutoPlay",
            )
            ValueRow(
                title = "Simulation speed",
                value = String.format(Locale.ROOT, "%.0f×", settings.simulation.speedFactor),
                onClick = { dialog = SettingsDialog.SIM_SPEED },
                testTag = "simulationSpeed",
            )

        SectionHeader("Session")
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Reset statistics", onClick = { dialog = SettingsDialog.RESET_STATS }, modifier = Modifier.weight(1f), testTag = "resetStatistics")
                ActionButton(
                    "End session",
                    onClick = { dialog = SettingsDialog.END_SESSION },
                    modifier = Modifier.weight(1f),
                    containerColor = RaceColors.Late,
                    contentColor = RaceColors.Black,
                    testTag = "endSession",
                )
            }
            Text(
                "Ending the session switches off the GPS and the countdown. Sail Racing $versionName".trim(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = RaceColors.Muted,
            )
    }

    when (dialog) {
        SettingsDialog.APPROACH_SPEED -> NumberInputDialog(
            title = "Approach speed",
            initialValue = approachSpeedMps * Formatters.METERS_PER_SECOND_TO_KNOTS,
            unit = "kn",
            range = 0.5..30.0,
            smallStep = 0.1,
            bigStep = 1.0,
            decimals = 1,
            onConfirm = { knots ->
                val mps = Formatters.knotsToMetersPerSecond(knots)
                actions.update { it.copy(race = it.race.copy(approachSpeed = if (approachManual) ApproachSpeed.Manual(mps) else ApproachSpeed.AverageUpwindVmg(mps))) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.TEN_SECOND_WINDOW -> NumberInputDialog(
            title = "10-second beeps in the last",
            initialValue = race.cuePolicy.tenSecondWindowSeconds.toDouble(),
            unit = "s",
            range = 0.0..600.0,
            smallStep = 10.0,
            bigStep = 60.0,
            onConfirm = { seconds ->
                actions.update { it.copy(race = it.race.copy(cuePolicy = it.race.cuePolicy.copy(tenSecondWindowSeconds = seconds.toInt()))) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.SECOND_WINDOW -> NumberInputDialog(
            title = "Beep every second in the last",
            initialValue = race.cuePolicy.secondWindowSeconds.toDouble(),
            unit = "s",
            range = 0.0..60.0,
            smallStep = 1.0,
            bigStep = 5.0,
            onConfirm = { seconds ->
                actions.update { it.copy(race = it.race.copy(cuePolicy = it.race.cuePolicy.copy(secondWindowSeconds = seconds.toInt()))) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.UPWIND_MAX_TWA -> NumberInputDialog(
            title = "Count as upwind up to",
            initialValue = race.upwindMaxTwaDegrees.toDouble(),
            unit = "° off the wind",
            range = 30.0..90.0,
            smallStep = 1.0,
            bigStep = 5.0,
            onConfirm = { degrees ->
                actions.update { it.copy(race = it.race.copy(upwindMaxTwaDegrees = degrees.toInt())) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.SIM_SPEED -> NumberInputDialog(
            title = "Simulation speed factor",
            initialValue = settings.simulation.speedFactor,
            unit = "× real time",
            range = 1.0..50.0,
            smallStep = 1.0,
            bigStep = 5.0,
            onConfirm = { factor ->
                actions.update { it.copy(simulation = it.simulation.copy(speedFactor = factor)) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.RESET_STATS -> ConfirmDialog(
            title = "Reset all statistics?",
            text = "Wind histogram, shift history and speed averages will be cleared.",
            confirmText = "Reset",
            onConfirm = { actions.resetWindStatistics(); actions.resetSpeedStatistics(); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.END_SESSION -> ConfirmDialog(
            title = "End the session?",
            text = "GPS tracking and the countdown stop. Reopen the app to start again.",
            confirmText = "End session",
            onConfirm = { actions.endSession(); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = RaceColors.Info,
    )
    HorizontalDivider(color = RaceColors.Dim)
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, testTag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = RaceColors.Muted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(testTag))
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit, testTag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleLarge, color = RaceColors.Info)
    }
}

/** Convenience for tests and previews: the default cue policy text. */
fun CuePolicy.describe(): String = if (enabled) "on, 10 s window $tenSecondWindowSeconds s" else "off"
