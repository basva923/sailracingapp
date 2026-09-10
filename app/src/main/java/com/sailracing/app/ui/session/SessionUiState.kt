package com.sailracing.app.ui.session

import com.sailracing.domain.race.RaceSnapshot
import com.sailracing.domain.text.Formatters
import com.sailracing.domain.timer.RacePhase

/** The three states the Session screen distinguishes. */
enum class SessionStatus { NONE, RUNNING, ENDED }

/**
 * Everything the Session screen shows: whether a session runs, what has been gathered so far, and what
 * starting, ending and clearing would do. Display-ready text, so the screen only recomposes on visible change.
 */
data class SessionUiState(
    val status: SessionStatus = SessionStatus.NONE,
    val hasData: Boolean = false,
    val title: String = "NO SESSION",
    val detail: String = "Start a session to switch on the GPS and begin measuring the wind",
    val gpsStatus: String = "GPS off",
    val gpsOk: Boolean = false,
    val since: String = Formatters.PLACEHOLDER,
    val track: String = "No track yet",
    val wind: String = "No upwind samples yet",
    val line: String = "Not set",
    val mark: String = "Top of the area",
    val timer: String = "Idle",
    val simulating: Boolean = false,
) {
    companion object {
        fun from(snapshot: RaceSnapshot, running: Boolean, startedAtMillis: Long?, simulating: Boolean): SessionUiState {
            val line = snapshot.startLine
            val markIsSet = snapshot.course?.windwardMarkIsSet == true
            val hasData = snapshot.trackPointCount > 0 || snapshot.histogram.totalSamples > 0 || snapshot.history.samples.isNotEmpty() ||
                line.pinEnd != null || line.boatEnd != null || markIsSet || snapshot.phase != RacePhase.SETUP ||
                snapshot.speedStats.upwind.count > 0 || snapshot.speedStats.downwind.count > 0
            val status = when {
                running -> SessionStatus.RUNNING
                hasData -> SessionStatus.ENDED
                else -> SessionStatus.NONE
            }
            val since = startedAtMillis?.let(Formatters::timeOfDay) ?: Formatters.PLACEHOLDER
            val simulation = if (simulating) " · simulating a race" else ""
            val (title, detail) = when (status) {
                SessionStatus.RUNNING -> "SESSION RUNNING" to "GPS, wind statistics and countdown are on since $since$simulation"
                SessionStatus.ENDED -> "SESSION ENDED" to "GPS is off, the data is kept. Start again to continue, or clear it for the next race$simulation"
                SessionStatus.NONE -> "NO SESSION" to "Start a session to switch on the GPS and begin measuring the wind$simulation"
            }
            val accuracy = snapshot.accuracyMeters
            val gps = when {
                !running -> "GPS off" to false
                snapshot.position == null -> "No GPS" to false
                !snapshot.fixIsFresh -> "GPS stale" to false
                accuracy != null -> "GPS ±${Formatters.meters(accuracy)}" to true
                else -> "GPS" to true
            }
            val reference = snapshot.windReference
            val samples = snapshot.histogram.totalSamples
            return SessionUiState(
                status = status,
                hasData = hasData,
                title = title,
                detail = detail,
                gpsStatus = gps.first,
                gpsOk = gps.second,
                since = since,
                track = if (snapshot.trackPointCount == 0) "No track yet" else "${snapshot.trackPointCount} points · ${snapshot.trackPointCount / 60} min",
                wind = when {
                    samples == 0 -> "No upwind samples yet"
                    reference.isMeasured -> "$samples upwind samples · mean ${Formatters.degrees(reference.directionDegrees)}"
                    else -> "$samples upwind samples"
                },
                line = when {
                    line.isComplete -> "Set · ${Formatters.meters(line.lengthMeters() ?: 0.0)}"
                    line.pinEnd != null -> "Pin end only"
                    line.boatEnd != null -> "Boat end only"
                    else -> "Not set"
                },
                mark = if (markIsSet) "Set" else "Top of the area",
                timer = when (snapshot.phase) {
                    RacePhase.SETUP -> "Idle"
                    RacePhase.COUNTDOWN -> "Start in ${Formatters.countdown(snapshot.remainingMillis ?: 0)}"
                    RacePhase.RACING -> "Racing ${Formatters.countdown(snapshot.remainingMillis ?: 0)}"
                },
                simulating = simulating,
            )
        }
    }
}
