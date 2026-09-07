package com.sailracing.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sailracing.app.ui.theme.RaceColors

/**
 * A line box is taller than its font size; this is the ratio the bold sans face here needs, used to
 * turn a height budget into a font size.
 */
private const val LineBoxFactor = 1.25f

/**
 * The largest font size that still fits [available] height, capped at [max]. [BigValue] scales its
 * number to the width it is given, which on a short pane (landscape) would run off the bottom.
 */
@Composable
fun fontSizeFitting(available: Dp, max: TextUnit): TextUnit {
    val fits = with(LocalDensity.current) { (available / LineBoxFactor).toSp() }
    return if (fits < max) fits else max
}

/** A small muted caption above a big value; the value scales to fill the available width. */
@Composable
fun BigValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
    maxFontSize: TextUnit = 96.sp,
    minFontSize: TextUnit = 24.sp,
    testTag: String? = null,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Caption(label)
        BasicText(
            text = value,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
            style = TextStyle(color = color, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = maxFontSize, stepSize = 2.sp),
        )
    }
}

/**
 * A caption in the muted colour, upper case, used as a label everywhere. Captions carry real
 * information ("±40° around the set wind"), so a caption too wide for a narrow screen wraps rather
 * than being silently cut off in the middle of a word.
 */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = RaceColors.Muted, maxLines: Int = 2) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A medium-sized labelled value for secondary information. */
@Composable
fun LabeledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
    testTag: String? = null,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Caption(label)
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
        )
    }
}
