package com.sailracing.app.ui.race

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.sailracing.app.ui.components.GlanceCell
import com.sailracing.app.ui.components.GlanceLayout
import com.sailracing.app.ui.components.HistogramChart
import com.sailracing.app.ui.components.HistoryChart
import com.sailracing.app.ui.components.LabeledValue
import com.sailracing.app.ui.components.NumberInputDialog
import com.sailracing.app.ui.components.PaneSize
import com.sailracing.app.ui.components.SmallButton
import com.sailracing.app.ui.components.adviceColor
import com.sailracing.app.ui.components.fontSizeFitting
import com.sailracing.app.ui.components.shiftColor
import com.sailracing.app.ui.components.speedDeltaColor
import com.sailracing.app.ui.components.tackColor
import com.sailracing.app.ui.map.MapCameraState
import com.sailracing.app.ui.map.MapUiState
import com.sailracing.app.ui.map.rememberMapCameraState
import com.sailracing.app.ui.theme.RaceColors
import com.sailracing.app.ui.wind.WindUiState
import com.sailracing.domain.strategy.FavouredSide
import com.sailracing.domain.wind.WindHistogram
import com.sailracing.domain.wind.WindSettings
import kotlin.math.abs

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
 * The screen to race by, built for a helm who looks at it for a second between the telltales and the
 * water. Nothing on it scrolls. On top - or on the left, with the phone on its side - the map with the
 * race line to the windward mark, and what that line costs written under it. Under those the glance panel:
 * whether to tack and which side pays, in big coloured words with the reason under each; then the speed
 * against the average of the day, green when the boat is going better than it has been, and the shift
 * from the mean wind with a strip of the histogram showing where the wind sits now. A bar at the bottom
 * carries the race time and *More*: that swaps the map for everything else - the rose, the numbers, the
 * wind and the mark buttons, the full histogram and the statistics - while the glance panel stays put,
 * and *Map* brings the map back.
 */
@Composable
fun RaceScreen(map: MapUiState, wind: WindUiState, actions: RaceActions, modifier: Modifier = Modifier) {
    var dialog by rememberSaveable { mutableStateOf<RaceDialog?>(null) }
    var bearing by rememberSaveable { mutableIntStateOf(0) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val camera = rememberMapCameraState()

    GlanceLayout(
        modifier = modifier,
        main = { pane ->
            if (showDetails) {
                Details(map = map, wind = wind, pane = pane, onDialog = { dialog = it }, actions = actions)
            } else {
                MapWithOverlays(map = map, camera = camera)
            }
        },
        pinned = {
            GlancePanel(map, wind)
            RaceBar(raceTime = wind.raceTime, showDetails = showDetails, onToggle = { showDetails = !showDetails })
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

/**
 * The map, with the two things worth adding to it: what the race line costs, on a line of its own under
 * the water so it never covers the boat or the line (the start is at the bottom of the map), and a *Fit*
 * button in the corner while the map is zoomed or moved, so the whole area is one tap away.
 */
@Composable
private fun MapWithOverlays(map: MapUiState, camera: MapCameraState) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            CourseMap(state = map, modifier = Modifier.fillMaxSize(), camera = camera)
            if (!camera.isFitted) {
                SmallButton("Fit", onClick = camera::fit, modifier = Modifier.align(Alignment.TopEnd).padding(top = 28.dp), testTag = "fitMap")
            }
        }
        Caption(
            map.raceLineGlance.ifEmpty { "No race line yet" },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("raceLineGlance"),
            color = if (map.raceLineGlance.isEmpty()) RaceColors.Muted else RaceColors.Early,
            maxLines = 1,
        )
    }
}

/**
 * What the helm reads at a glance: two rows of two cells that share the panel's height. The words
 * first - tack or hold, and which side to go to, with the reason under each - then the numbers: the
 * speed against the average, and the shift with the histogram it sits in.
 */
@Composable
private fun ColumnScope.GlancePanel(map: MapUiState, wind: WindUiState) {
    val sideColor = when (map.favouredSide) {
        FavouredSide.LEFT, FavouredSide.RIGHT -> RaceColors.Early
        FavouredSide.EVEN -> RaceColors.White
        FavouredSide.UNKNOWN -> RaceColors.Muted
    }
    GlanceRow(Modifier.weight(WORDS_WEIGHT)) {
        // Both reasons are given two lines whether they need them or not, so the two words above them
        // are always the same size on the same line.
        GlanceCell(wind.adviceTitle, Modifier.weight(1f), color = adviceColor(wind.advice), maxFontSize = WORD_FONT, testTag = "advice") {
            Caption(wind.adviceGlance, modifier = Modifier.fillMaxWidth().testTag("adviceGlance"), color = RaceColors.White, minLines = 2)
        }
        GlanceCell(map.sideTitle, Modifier.weight(1f), color = sideColor, maxFontSize = WORD_FONT, testTag = "sideTitle") {
            Caption(map.sideGlance, modifier = Modifier.fillMaxWidth().testTag("sideGlance"), color = RaceColors.White, minLines = 2)
        }
    }
    GlanceRow(Modifier.weight(NUMBERS_WEIGHT)) {
        GlanceCell(wind.speed, Modifier.weight(1f), title = "Speed kn", testTag = "speed") {
            Caption(
                wind.speedDelta,
                modifier = Modifier.fillMaxWidth().testTag("speedDelta"),
                color = speedDeltaColor(wind.speedDeltaMps),
                maxLines = 1,
            )
        }
        GlanceCell(wind.shift, Modifier.weight(1f), title = wind.shiftTitle, color = shiftColor(wind.shiftDegrees), testTag = "shift") {
            // The middle of the histogram only: the wind swings a few degrees, and at this size the
            // whole window would squeeze that into a smudge. A wind beyond it is marked on the edge.
            // The marker is the shift the number above shows - none while the boat is off the angle,
            // when a heading says nothing about the wind - not the raw estimate the full chart draws.
            val strip = wind.histogram.filter { abs(it.offsetDegrees) <= GLANCE_HALF_WIDTH }
            HistogramChart(
                bins = strip.ifEmpty { WindHistogram().window(0, GLANCE_HALF_WIDTH) },
                estimatedOffsetDegrees = wind.shiftDegrees,
                modifier = Modifier.fillMaxWidth().height(STRIP_HEIGHT),
                emptyText = "No samples yet",
                testTag = "shiftStrip",
            )
        }
    }
}

@Composable
private fun GlanceRow(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

/** The bar under the glance panel: the race time on the left, and on the right the way to everything else. */
@Composable
private fun RaceBar(raceTime: String, showDetails: Boolean, onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(BAR_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        Text(
            raceTime.uppercase(),
            modifier = Modifier.weight(1f).testTag("raceTime"),
            style = MaterialTheme.typography.labelLarge,
            color = RaceColors.White,
            maxLines = 1,
        )
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().clickable(onClick = onToggle).testTag("toggleDetails"),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                if (showDetails) "▲ MAP" else "MORE ▼",
                style = MaterialTheme.typography.labelLarge,
                color = RaceColors.Info,
                textAlign = TextAlign.End,
            )
        }
    }
}

/**
 * Everything that is not read at a glance, in the map's place and in the order it is wanted: the whole
 * of the advice, the rose and the numbers, the wind, the mark and the map's legend, the statistics.
 */
@Composable
private fun BoxScope.Details(map: MapUiState, wind: WindUiState, pane: PaneSize, onDialog: (RaceDialog) -> Unit, actions: RaceActions) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("details"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Advice(map, wind)
        Numbers(wind, pane)
        WindButtons(wind, onDialog = onDialog, actions = actions)
        MarkAndMap(map = map, onDialog = onDialog, actions = actions)
        Statistics(wind)
    }
}

/** The advice in full: why to tack or hold, why that side, and what the drawn lines are worth. */
@Composable
private fun ColumnScope.Advice(map: MapUiState, wind: WindUiState) {
    Caption(wind.adviceDetail, modifier = Modifier.fillMaxWidth().testTag("adviceDetail"), color = RaceColors.White)
    Caption(map.sideDetail, modifier = Modifier.fillMaxWidth().testTag("sideDetail"), color = RaceColors.White, maxLines = 3)
    Caption(map.raceLineText, modifier = Modifier.fillMaxWidth().testTag("raceLineText"), color = RaceColors.White, maxLines = 2)
    Caption(map.riskText, modifier = Modifier.fillMaxWidth().testTag("riskText"), color = RaceColors.Warning, maxLines = 3)
}

/** The numbers behind the advice: the rose, and what the boat and the wind are doing. */
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledValue("Set wind", wind.configuredWind, Modifier.weight(1f), color = RaceColors.Wind, testTag = "configuredWind")
        LabeledValue("Mean", wind.meanWind, Modifier.weight(1f), testTag = "meanWind")
        LabeledValue("Now", wind.estimatedWind, Modifier.weight(1f), color = RaceColors.Estimated, testTag = "estimatedWind")
    }
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
    Caption(wind.tackLabel.ifEmpty { "—" }, modifier = Modifier.fillMaxWidth().testTag("tackLabel"), color = tackColor(wind.tack))
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
}

/** The windward mark, the track, and what the map is showing. */
@Composable
private fun ColumnScope.MarkAndMap(map: MapUiState, onDialog: (RaceDialog) -> Unit, actions: RaceActions) {
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
    Caption(map.scaleText, modifier = Modifier.fillMaxWidth().testTag("scale"))
    Caption(map.trackText, modifier = Modifier.fillMaxWidth().testTag("trackText"))
    Caption("Pinch the map to zoom, drag to move, double tap or the Fit button to fit the racing area again", maxLines = 3)
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

/** The words row gets a little less of the panel than the numbers, which carry a caption and a chart too. */
private const val WORDS_WEIGHT = 1f
private const val NUMBERS_WEIGHT = 1.25f

/** The advice words are read from the back of the boat: as big as the cell allows, up to this. */
private val WORD_FONT = 40.sp

/** The glance histogram shows this much either side of the reference wind. */
private const val GLANCE_HALF_WIDTH = 20

private val STRIP_HEIGHT = 36.dp
private val BAR_HEIGHT = 40.dp

private const val DEFAULT_MARK_DISTANCE_METERS = 1000.0
