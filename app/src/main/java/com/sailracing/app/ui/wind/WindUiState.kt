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
    /**
     * The wind of the moment: the direction the boat is sailing to, read off its heading over the last
     * few samples and the angle it is holding, while it is holding one - and the reference wind, muted,
     * when it is not, since a heading off the angle says nothing about the wind.
     */
    val windNow: String = Formatters.PLACEHOLDER,
    /** Where [windNow] came from: "Wind: stbd 340° + 45°" off the boat, or "Set wind · no heading". */
    val windNowGlance: String = "Set wind · no heading",
    /** "Measured tack angle 48° · set 45°" once both tacks have been sailed, else why not. */
    val tackAngleMeasured: String = "Tack angle not measured yet: sail close-hauled on both tacks",
    /** Whether [windNow] is the boat's own estimate rather than the reference wind standing in for it. */
    val windNowIsLive: Boolean = false,
    /**
     * The shift as the boat feels it: positive when the wind has gone the boat's way - a lift, closer to
     * the wind - and negative when it is headed, on either tack, upwind or down. It is the steadied wind
     * against the reference (the mean wind, or the set wind while there is none), turned round on the tack
     * a veer heads.
     */
    val shift: String = Formatters.PLACEHOLDER,
    val shiftDegrees: Double? = null,
    /** What the shift is measured from: "Shift vs mean" once measured, "Shift vs set wind" before; + is a lift. */
    val shiftTitle: String = "Shift vs set wind · + lift",
    /**
     * The middle of the histogram seen from the boat: veered to the right on the tack a veer lifts, and
     * mirrored on the other, so that a lift is always to the right of the centre like the shift is +.
     */
    val histogramFromTheBoat: List<HistogramBin> = emptyList(),
    val heading: String = Formatters.PLACEHOLDER,
    val headingSource: String = "",
    /** Under the heading on the race screen: the tack and where the heading comes from, "Stbd upwind · GPS". */
    val headingGlance: String = "No heading",
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
            // A veer lifts starboard upwind and port downwind; on the other tack it is a header.
            val veerLifts = sailing != null && (sailing.pointOfSail == PointOfSail.UPWIND) == (sailing.tack == Tack.STARBOARD)
            val lift = plan.shiftFromReferenceDegrees?.let { if (veerLifts) it else -it }
            val window = histogram.window(reference.roundToInt(), HISTOGRAM_HALF_WIDTH)
            val heading = snapshot.headingDegrees
            val headingSource = when (snapshot.headingSource) {
                HeadingSource.COURSE_OVER_GROUND -> "GPS course"
                HeadingSource.COMPASS -> "Compass"
                null -> ""
            }
            val tackShort = when (sailing?.tack) {
                Tack.STARBOARD -> "Stbd"
                Tack.PORT -> "Port"
                null -> "—"
            }
            // The wind the boat itself is measuring: its heading over the last few samples turned by the
            // angle it sails at, on the side the wind is on. Only while it is on that angle; off it the
            // reference wind stands in, in the muted colour, so the screen still answers "where is the
            // wind" on a reach or a start.
            val liveWind = snapshot.steadyWindDegrees?.takeIf { onAngle }
            val referenceWord = referenceName.replaceFirstChar { it.uppercase() }
            val windNowGlance = when {
                heading == null || sailing == null -> "$referenceWord wind · no heading"
                liveWind == null -> "$referenceWord wind · sail close-hauled"
                else -> {
                    val angle = if (sailing.pointOfSail == PointOfSail.UPWIND) wind.tackAngleDegrees else wind.downwindAngleDegrees
                    val sign = if (sailing.tack == Tack.STARBOARD) "+" else "-"
                    // Non-breaking spaces around the sign: half a panel is narrow enough to wrap this
                    // caption, and it should break after the tack, not in the middle of the sum.
                    "Wind: ${tackShort.lowercase()} ${Formatters.degrees(heading)}\u00A0$sign\u00A0$angle°"
                }
            }
            return WindUiState(
                headingDegrees = heading,
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
                windNow = Formatters.degrees(liveWind ?: reference),
                windNowGlance = windNowGlance,
                windNowIsLive = liveWind != null,
                shift = lift?.let(Formatters::signedDegrees) ?: Formatters.PLACEHOLDER,
                shiftDegrees = lift,
                shiftTitle = if (plan.referenceIsMeasured) "Shift vs mean · + lift" else "Shift vs set wind · + lift",
                histogramFromTheBoat = if (veerLifts) window else window.map { HistogramBin(-it.offsetDegrees, it.count) }.reversed(),
                heading = heading?.let(Formatters::degrees) ?: Formatters.PLACEHOLDER,
                headingSource = headingSource,
                headingGlance = when {
                    heading == null -> "No heading"
                    sailing == null -> headingSource
                    else -> "${tackLabel(sailing).replace("Starboard", "Stbd")} · ${headingSource.removeSuffix(" course")}"
                },
                tackAngleMeasured = snapshot.windReference.measuredTackAngleDegrees?.let {
                    "Measured tack angle ${it.roundToInt()}° · set ${wind.tackAngleDegrees}°"
                } ?: "Tack angle not measured yet: sail close-hauled on both tacks",
                speed = speed?.let(Formatters::knotsValue) ?: Formatters.PLACEHOLDER,
                speedDelta = speedDelta,
                speedDeltaMps = if (speed != null && average != null && onAngle) speed - average else null,
                vmg = snapshot.vmgMps?.let(Formatters::knotsValue) ?: Formatters.PLACEHOLDER,
                tack = sailing?.tack,
                tackLabel = sailing?.let(::tackLabel) ?: "",
                tackShort = tackShort,
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
                histogram = window,
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
                canSetFromHeading = heading != null,
            )
        }

        fun tackLabel(state: SailingState): String {
            val tack = if (state.tack == Tack.STARBOARD) "Starboard" else "Port"
            val point = if (state.pointOfSail == PointOfSail.UPWIND) "upwind" else "downwind"
            return "$tack $point"
        }
    }
}
