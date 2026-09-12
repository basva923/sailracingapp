package com.sailracing.app.ui.start

import com.sailracing.app.ui.format.Formatters
import com.sailracing.domain.race.RaceSnapshot
import com.sailracing.domain.startline.LineSide
import com.sailracing.domain.timer.RacePhase

/** How to colour the time to kill. */
enum class Urgency { NEUTRAL, EARLY, LATE }

/** Everything the start screen shows, as display-ready text so recomposition only happens when text changes. */
data class StartUiState(
    val phase: RacePhase = RacePhase.SETUP,
    val clockLabel: String = "Time",
    val clock: String = Formatters.PLACEHOLDER,
    val timeToKill: String = Formatters.PLACEHOLDER,
    val urgency: Urgency = Urgency.NEUTRAL,
    /** Negative once the boat is over the line before the start, when it counts against it. */
    val distanceToLine: String = Formatters.PLACEHOLDER,
    /** Whether the boat is on the course side of the line before the gun: the distance is then read in red. */
    val overLine: Boolean = false,
    /** [overLine] during the countdown itself, when it is worth a warning of its own. */
    val overEarly: Boolean = false,
    val pinSet: Boolean = false,
    val boatSet: Boolean = false,
    val lineLength: String = "Mark both ends",
    val canMark: Boolean = false,
    val gpsStatus: String = "No GPS",
    val gpsOk: Boolean = false,
    val approachSpeed: String = "",
) {
    companion object {
        fun from(snapshot: RaceSnapshot): StartUiState {
            val remaining = snapshot.remainingMillis
            val (label, clock) = when (snapshot.phase) {
                RacePhase.SETUP -> "Time" to Formatters.timeOfDay(snapshot.nowMillis)
                RacePhase.COUNTDOWN -> "Time to start" to Formatters.countdown(remaining ?: 0)
                RacePhase.RACING -> "Race time" to Formatters.countdown(remaining ?: 0)
            }
            val ttk = snapshot.timeToKillSeconds?.takeIf { snapshot.phase == RacePhase.COUNTDOWN }
            val urgency = when {
                ttk == null -> Urgency.NEUTRAL
                ttk >= 0.0 -> Urgency.EARLY
                else -> Urgency.LATE
            }
            val line = snapshot.line
            val accuracy = snapshot.accuracyMeters
            val gps = when {
                snapshot.position == null -> "No GPS" to false
                !snapshot.fixIsFresh -> "GPS stale" to false
                accuracy != null -> "GPS ±${Formatters.meters(accuracy)}" to true
                else -> "GPS" to true
            }
            // Kept short: it shares one line with the GPS status on a narrow screen.
            val approach = "Approach ${Formatters.knots(snapshot.approachSpeedMps)} " +
                if (snapshot.approachSpeedIsMeasured) "(VMG)" else "(default)"
            // Over the line before the gun the distance is what has to be sailed back: negative, in red.
            val overLine = snapshot.phase != RacePhase.RACING && line?.side == LineSide.COURSE
            return StartUiState(
                phase = snapshot.phase,
                clockLabel = label,
                clock = clock,
                timeToKill = ttk?.let(Formatters::signedSeconds) ?: Formatters.PLACEHOLDER,
                urgency = urgency,
                distanceToLine = line?.let { (if (overLine) "-" else "") + Formatters.meters(it.distanceMeters) } ?: Formatters.PLACEHOLDER,
                overLine = overLine,
                overEarly = snapshot.phase == RacePhase.COUNTDOWN && line?.side == LineSide.COURSE,
                pinSet = snapshot.startLine.pinEnd != null,
                boatSet = snapshot.startLine.boatEnd != null,
                lineLength = snapshot.startLineLengthMeters?.let { "Line ${Formatters.meters(it)}" } ?: "Mark both ends",
                canMark = snapshot.position != null,
                gpsStatus = gps.first,
                gpsOk = gps.second,
                approachSpeed = approach,
            )
        }
    }
}
