package com.sailracing.app.ui.wind

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.race.HeadingSource
import com.sailracing.domain.race.RaceSnapshot
import com.sailracing.domain.strategy.TackAdvice
import com.sailracing.domain.text.Formatters
import com.sailracing.domain.text.PlanText
import com.sailracing.domain.timer.RacePhase
import com.sailracing.domain.wind.HistogramBin
import com.sailracing.domain.wind.PointOfSail
import com.sailracing.domain.wind.SailingState
import com.sailracing.domain.wind.Tack
import com.sailracing.domain.wind.TargetHeadings
import com.sailracing.domain.wind.WindMath
import com.sailracing.domain.wind.WindSettings
import kotlin.math.roundToInt

/**
 * Everything the wind screen shows: the tack advice, the compass rose, the wind numbers, the histogram
 * and the speed averages, as display-ready values so the screen only recomposes when something visible changes.
 */
data class WindUiState(
    // Compass rose
    val headingDegrees: Double? = null,
    val windDegrees: Int = 0,
    val meanWindDegrees: Double? = null,
    val estimatedWindDegrees: Double? = null,
    val targets: TargetHeadings = WindMath.targetHeadings(WindSettings()),
    // Wind numbers
    val configuredWind: String = Formatters.PLACEHOLDER,
    val windDirection: Int = 0,
    val tackAngle: Int = WindSettings.DEFAULT_TACK_ANGLE,
    val downwindAngle: Int = WindSettings.DEFAULT_DOWNWIND_ANGLE,
    val meanWind: String = Formatters.PLACEHOLDER,
    val meanIsMeasured: Boolean = false,
    val estimatedWind: String = Formatters.PLACEHOLDER,
    /** The estimated wind relative to the reference: the mean wind, or the set wind while there is no histogram. */
    val shift: String = Formatters.PLACEHOLDER,
    val shiftDegrees: Double? = null,
    val heading: String = Formatters.PLACEHOLDER,
    val headingSource: String = "",
    val speed: String = Formatters.PLACEHOLDER,
    val vmg: String = Formatters.PLACEHOLDER,
    val tack: Tack? = null,
    val tackLabel: String = "",
    /** "Stbd" / "Port", short enough for a third of the screen. */
    val tackShort: String = "—",
    /** "Upwind" / "Downwind", or "Tack" when unknown. */
    val pointOfSailLabel: String = "Tack",
    // Advice
    val advice: TackAdvice = TackAdvice.UNKNOWN,
    val adviceTitle: String = "—",
    val adviceDetail: String = "Waiting for a heading",
    // Charts, all relative to the reference wind (bin 0 in the middle).
    val histogramCaption: String = "Wind direction frequency, ±$HISTOGRAM_HALF_WIDTH° around the set wind",
    val histogram: List<HistogramBin> = emptyList(),
    val estimatedOffset: Double? = null,
    /** Where the set wind sits relative to the measured mean; null while the set wind is the reference. */
    val setOffset: Double? = null,
    val shifts: List<Double> = emptyList(),
    val statistics: String = "No upwind samples yet",
    // Averages
    val averageUpwind: String = Formatters.PLACEHOLDER,
    val averageUpwindVmg: String = Formatters.PLACEHOLDER,
    val averageDownwind: String = Formatters.PLACEHOLDER,
    val averageDownwindVmg: String = Formatters.PLACEHOLDER,
    val raceTime: String = "",
    val canSetFromHeading: Boolean = false,
) {
    companion object {
        const val HISTOGRAM_HALF_WIDTH = 40
        const val HISTORY_SAMPLES = 300

        fun from(snapshot: RaceSnapshot): WindUiState {
            val wind = snapshot.windSettings
            val histogram = snapshot.histogram
            val plan = snapshot.plan
            val reference = snapshot.windReference.directionDegrees
            val referenceName = if (snapshot.windReference.isMeasured) "mean" else "set"
            val mean = histogram.meanDirection()
            val deviation = histogram.standardDeviation()
            val statistics = if (mean == null) {
                "No upwind samples yet"
            } else {
                "Mean ${Formatters.degrees(mean)}" +
                    (deviation?.let { " ±${it.toInt()}°" } ?: "") +
                    " · ${histogram.totalSamples} samples"
            }
            val stats = snapshot.speedStats
            val sailing = snapshot.sailing
            return WindUiState(
                headingDegrees = snapshot.headingDegrees,
                windDegrees = wind.directionDegrees,
                meanWindDegrees = plan.referenceWindDegrees.takeIf { plan.referenceIsMeasured },
                estimatedWindDegrees = snapshot.estimatedWindDegrees,
                targets = snapshot.targetHeadings,
                configuredWind = Formatters.degrees(wind.directionDegrees),
                windDirection = wind.directionDegrees,
                tackAngle = wind.tackAngleDegrees,
                downwindAngle = wind.downwindAngleDegrees,
                meanWind = if (plan.referenceIsMeasured) Formatters.degrees(plan.referenceWindDegrees) else Formatters.PLACEHOLDER,
                meanIsMeasured = plan.referenceIsMeasured,
                estimatedWind = snapshot.estimatedWindDegrees?.let(Formatters::degrees) ?: Formatters.PLACEHOLDER,
                shift = plan.shiftFromReferenceDegrees?.let(Formatters::signedDegrees) ?: Formatters.PLACEHOLDER,
                shiftDegrees = plan.shiftFromReferenceDegrees,
                heading = snapshot.headingDegrees?.let(Formatters::degrees) ?: Formatters.PLACEHOLDER,
                headingSource = when (snapshot.headingSource) {
                    HeadingSource.COURSE_OVER_GROUND -> "GPS course"
                    HeadingSource.COMPASS -> "Compass"
                    null -> ""
                },
                speed = snapshot.speedMps?.let(Formatters::knotsValue) ?: Formatters.PLACEHOLDER,
                vmg = snapshot.vmgMps?.let(Formatters::knotsValue) ?: Formatters.PLACEHOLDER,
                tack = sailing?.tack,
                tackLabel = sailing?.let(::tackLabel) ?: "",
                tackShort = when (sailing?.tack) {
                    Tack.STARBOARD -> "Stbd"
                    Tack.PORT -> "Port"
                    null -> "—"
                },
                pointOfSailLabel = when (sailing?.pointOfSail) {
                    PointOfSail.UPWIND -> "Upwind"
                    PointOfSail.DOWNWIND -> "Downwind"
                    null -> "Tack"
                },
                advice = plan.tackAdvice,
                adviceTitle = PlanText.tackTitle(plan),
                adviceDetail = PlanText.tackDetail(plan),
                histogramCaption = "Wind direction frequency, ±$HISTOGRAM_HALF_WIDTH° around the $referenceName wind",
                histogram = histogram.window(reference.roundToInt(), HISTOGRAM_HALF_WIDTH),
                estimatedOffset = snapshot.shiftDegrees,
                setOffset = if (plan.referenceIsMeasured) Angles.signedDifference(reference, wind.directionDegrees.toDouble()) else null,
                shifts = snapshot.history.last(HISTORY_SAMPLES).map { Angles.signedDifference(reference, it.directionDegrees) },
                statistics = statistics,
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

        fun tackLabel(state: SailingState): String {
            val tack = if (state.tack == Tack.STARBOARD) "Starboard" else "Port"
            val point = if (state.pointOfSail == PointOfSail.UPWIND) "upwind" else "downwind"
            return "$tack $point"
        }
    }
}
