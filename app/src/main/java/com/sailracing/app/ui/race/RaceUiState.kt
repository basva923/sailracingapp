package com.sailracing.app.ui.race

import com.sailracing.app.ui.format.Formatters
import com.sailracing.app.ui.wind.WindUiState
import com.sailracing.domain.race.RaceSnapshot
import com.sailracing.domain.timer.RacePhase
import com.sailracing.domain.wind.PointOfSail
import com.sailracing.domain.wind.Tack
import com.sailracing.domain.wind.TargetHeadings
import com.sailracing.domain.wind.WindMath
import com.sailracing.domain.wind.WindSettings
import kotlin.math.abs
import kotlin.math.roundToInt

/** The steering hint. */
enum class Steer { ON_TARGET, HEAD_UP, BEAR_AWAY, NONE }

data class RaceUiState(
    val headingDegrees: Double? = null,
    val windDegrees: Int = 0,
    val estimatedWindDegrees: Double? = null,
    val targets: TargetHeadings = WindMath.targetHeadings(WindSettings()),
    val heading: String = Formatters.PLACEHOLDER,
    val target: String = Formatters.PLACEHOLDER,
    val speed: String = Formatters.PLACEHOLDER,
    val vmg: String = Formatters.PLACEHOLDER,
    val steer: Steer = Steer.NONE,
    val steerText: String = "",
    val shift: String = Formatters.PLACEHOLDER,
    val tack: Tack? = null,
    val tackLabel: String = "",
    val averageUpwind: String = Formatters.PLACEHOLDER,
    val averageUpwindVmg: String = Formatters.PLACEHOLDER,
    val averageDownwind: String = Formatters.PLACEHOLDER,
    val averageDownwindVmg: String = Formatters.PLACEHOLDER,
    val raceTime: String = "",
    val canSetFromHeading: Boolean = false,
) {
    companion object {
        /** Within this many degrees of the target the boat is "on target". */
        const val ON_TARGET_TOLERANCE_DEGREES = 3

        fun from(snapshot: RaceSnapshot): RaceUiState {
            val error = snapshot.headingErrorDegrees
            val sailing = snapshot.sailing
            val (steer, text) = when {
                error == null || sailing == null -> Steer.NONE to ""
                abs(error).roundToInt() <= ON_TARGET_TOLERANCE_DEGREES -> Steer.ON_TARGET to "On target"
                else -> {
                    // Upwind on starboard the target is left of the wind: pointing right of it means bear away... unless
                    // we generalise: positive error = heading right of target. Which way to turn is simply "left".
                    val amount = "${abs(error).roundToInt()}°"
                    val turnLeft = error > 0
                    val headUp = when (sailing.pointOfSail) {
                        // Upwind: heading up means turning towards the wind.
                        PointOfSail.UPWIND -> (sailing.tack == Tack.STARBOARD) != turnLeft
                        // Downwind: heading up means turning towards the wind as well; the wind is now behind.
                        PointOfSail.DOWNWIND -> (sailing.tack == Tack.STARBOARD) != turnLeft
                    }
                    if (headUp) Steer.HEAD_UP to "Head up $amount" else Steer.BEAR_AWAY to "Bear away $amount"
                }
            }
            val stats = snapshot.speedStats
            return RaceUiState(
                headingDegrees = snapshot.headingDegrees,
                windDegrees = snapshot.windSettings.directionDegrees,
                estimatedWindDegrees = snapshot.estimatedWindDegrees,
                targets = snapshot.targetHeadings,
                heading = snapshot.headingDegrees?.let(Formatters::degrees) ?: Formatters.PLACEHOLDER,
                target = snapshot.targetHeadingDegrees?.let(Formatters::degrees) ?: Formatters.PLACEHOLDER,
                speed = snapshot.speedMps?.let(Formatters::knotsValue) ?: Formatters.PLACEHOLDER,
                vmg = snapshot.vmgMps?.let(Formatters::knotsValue) ?: Formatters.PLACEHOLDER,
                steer = steer,
                steerText = text,
                shift = snapshot.shiftDegrees?.let(Formatters::signedDegrees) ?: Formatters.PLACEHOLDER,
                tack = sailing?.tack,
                tackLabel = sailing?.let(WindUiState::tackLabel) ?: "",
                averageUpwind = stats.upwind.mean?.let(Formatters::knots) ?: Formatters.PLACEHOLDER,
                averageUpwindVmg = stats.upwindVmg.mean?.let(Formatters::knots) ?: Formatters.PLACEHOLDER,
                averageDownwind = stats.downwind.mean?.let(Formatters::knots) ?: Formatters.PLACEHOLDER,
                averageDownwindVmg = stats.downwindVmg.mean?.let(Formatters::knots) ?: Formatters.PLACEHOLDER,
                raceTime = when (snapshot.phase) {
                    RacePhase.SETUP -> ""
                    RacePhase.COUNTDOWN -> "Start in ${Formatters.countdown(snapshot.remainingMillis ?: 0)}"
                    RacePhase.RACING -> "Racing ${Formatters.countdown(snapshot.remainingMillis ?: 0)}"
                },
                canSetFromHeading = snapshot.headingDegrees != null,
            )
        }
    }
}
