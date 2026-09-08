package com.sailracing.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong
import com.sailracing.app.ui.theme.RaceColors

/** A large, glove-friendly button with big text. */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 64.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = RaceColors.Surface,
            disabledContentColor = RaceColors.Dim,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** A yes/no confirmation for actions that are hard to undo during a race (stopping the timer, resets). */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        textContentColor = RaceColors.White,
        title = { Text(title, style = MaterialTheme.typography.headlineMedium) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm")) {
                Text(confirmText, style = MaterialTheme.typography.titleLarge, color = RaceColors.Late)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel")) {
                Text("Cancel", style = MaterialTheme.typography.titleLarge)
            }
        },
    )
}

/**
 * Numeric entry by big +/- buttons instead of a keyboard: usable with gloves and spray on the screen.
 * The value is clamped to [range], or wraps around it when [wrap] is set (for compass directions).
 */
@Composable
fun NumberInputDialog(
    title: String,
    initialValue: Double,
    unit: String,
    range: ClosedFloatingPointRange<Double>,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
    smallStep: Double = 1.0,
    bigStep: Double = 10.0,
    wrap: Boolean = false,
    decimals: Int = 0,
) {
    var value by rememberSaveable { mutableDoubleStateOf(roundTo(initialValue.coerceIn(range), decimals)) }
    fun step(delta: Double) {
        val next = roundTo(value + delta, decimals)
        value = when {
            !wrap -> next.coerceIn(range)
            next > range.endInclusive -> range.start + (next - range.endInclusive - smallStep)
            next < range.start -> range.endInclusive - (range.start - next - smallStep)
            else -> next
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        textContentColor = RaceColors.White,
        title = { Text(title, style = MaterialTheme.typography.headlineMedium) },
        text = {
            androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(
                    text = "${formatNumber(value, decimals)} $unit",
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(vertical = 12.dp).testTag("numberValue"),
                )
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                ) {
                    StepButton("-${formatNumber(bigStep, decimals)}", "stepDownBig") { step(-bigStep) }
                    StepButton("-${formatNumber(smallStep, decimals)}", "stepDown") { step(-smallStep) }
                    StepButton("+${formatNumber(smallStep, decimals)}", "stepUp") { step(smallStep) }
                    StepButton("+${formatNumber(bigStep, decimals)}", "stepUpBig") { step(bigStep) }
                }
                Text(
                    "${formatNumber(range.start, decimals)} to ${formatNumber(range.endInclusive, decimals)} $unit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RaceColors.Muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, modifier = Modifier.testTag("confirm")) {
                Text("OK", style = MaterialTheme.typography.titleLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel")) {
                Text("Cancel", style = MaterialTheme.typography.titleLarge)
            }
        },
    )
}

/** One of the choices offered by a [ChoiceDialog]. */
data class Choice(val id: String, val title: String, val detail: String = "")

/**
 * Pick one of a list, for a setting with more to it than a switch or a number: which simulation to sail.
 * The list scrolls, and tapping a line chooses it and closes the dialog.
 */
@Composable
fun ChoiceDialog(
    title: String,
    choices: List<Choice>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        textContentColor = RaceColors.White,
        title = { Text(title, style = MaterialTheme.typography.headlineMedium) },
        text = {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).testTag("choices"),
            ) {
                choices.forEach { choice ->
                    val chosen = choice.id == selectedId
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(choice.id) }
                            .padding(vertical = 8.dp)
                            .testTag("choice_" + choice.id),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        // The whole row is the target - a gloved finger should not have to find the button.
                        RadioButton(selected = chosen, onClick = null)
                        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                choice.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (chosen) RaceColors.Info else RaceColors.White,
                            )
                            if (choice.detail.isNotBlank()) {
                                Text(choice.detail, style = MaterialTheme.typography.bodyMedium, color = RaceColors.Muted)
                            }
                        }
                    }
                    HorizontalDivider(color = RaceColors.Dim)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel")) {
                Text("Close", style = MaterialTheme.typography.titleLarge)
            }
        },
    )
}

@Composable
private fun StepButton(label: String, tag: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 56.dp).testTag(tag),
        shape = MaterialTheme.shapes.medium,
        // Not surfaceContainerHigh: that is the dialog's own container colour, which would leave these
        // looking like bare text rather than the big gloved-finger targets they are.
        colors = ButtonDefaults.buttonColors(containerColor = RaceColors.Dim, contentColor = RaceColors.White),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

/** Rounds to a number of decimals so stepping by 0.1 never accumulates floating-point noise. */
fun roundTo(value: Double, decimals: Int): Double {
    var factor = 1.0
    repeat(decimals) { factor *= 10.0 }
    return kotlin.math.round(value * factor) / factor
}

/** Formats with a fixed number of decimals, e.g. 5.0 -> "5" or "5.0". */
fun formatNumber(value: Double, decimals: Int): String =
    if (decimals == 0) value.roundToLong().toString() else String.format(java.util.Locale.ROOT, "%.${decimals}f", value)
