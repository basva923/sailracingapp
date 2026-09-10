package com.sailracing.app.ui.wind

import com.sailracing.app.ui.format.Formatters
import com.sailracing.app.ui.plan.PlanText
import com.sailracing.domain.geo.Angles
import com.sailracing.domain.race.HeadingSource
import com.sailracing.domain.race.RaceSnapshot
import com.sailracing.domain.strategy.TackAdvice
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
    /** What the shift is measured from: "Shift vs mean" once there is a histogram, "Shift vs set wind" before. */
    val shiftTitle: String = "Shift vs set wind",
    val heading: String = Formatters.PLACEHOLDER,
    val headingSource: String = "",
    val speed: String = Formatters.PLACEHOLDER,
    /**
     * The speed against the average on this point of sail, "+0.3 vs avg 5.5", judged only while the boat
     * is on the angle the average was taken on; otherwise the average alone, or why there is none.
     */
    val speedDelta: String = "",
    /** The same difference in m/s, for the colour: null when the speed is not being judged. */
    val speedDeltaMps: Double? = null,
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
    /** The reason in a few words, for the glance panel: "Stbd lifted 5°". */
    val adviceGlance: String = "No heading",
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
            val speed = snapshot.speedMps
            val average = when (sailing?.pointOfSail) {
                PointOfSail.UPWIND -> stats.upwind.mean
                PointOfSail.DOWNWIND -> stats.downwind.mean
                null -> null
            }
            // The average is of the boat on its close-hauled or downwind angle, so the speed is only
            // judged against it while the boat is there - the same test the tack advice needs.
            val onAngle = plan.tackAdvice != TackAdvice.UNKNOWN
            val speedDelta = when {
                speed == null -> ""
                average == null -> "No average yet"
                !onAngle -> "Avg ${Formatters.knots(average)}"
                else -> "${Formatters.signedKnotsValue(speed - average)} vs avg ${Formatters.knotsValue(average)}"
            }
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
                shiftTitle = if (plan.referenceIsMeasured) "Shift vs mean" else "Shift vs set wind",
                heading = snapshot.headingDegrees?.let(Formatters::degrees) ?: Formatters.PLACEHOLDER,
                headingSource = when (snapshot.headingSource) {
                    HeadingSource.COURSE_OVER_GROUND -> "GPS course"
                    HeadingSource.COMPASS -> "Compass"
                    null -> ""
                },
                speed = speed?.let(Formatters::knotsValue) ?: Formatters.PLACEHOLDER,
                speedDelta = speedDelta,
                speedDeltaMps = if (speed != null && average != null && onAngle) speed - average else null,
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
                adviceGlance = PlanText.tackGlance(plan),
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
