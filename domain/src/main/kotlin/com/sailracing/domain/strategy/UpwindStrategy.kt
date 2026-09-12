package com.sailracing.domain.strategy

import com.sailracing.domain.course.Side
import com.sailracing.domain.course.TrackGrid
import com.sailracing.domain.geo.Angles
import com.sailracing.domain.wind.PointOfSail
import com.sailracing.domain.wind.SailingState
import com.sailracing.domain.wind.Tack
import com.sailracing.domain.wind.WindHistogram
import com.sailracing.domain.wind.WindHistory
import com.sailracing.domain.wind.WindReference
import kotlin.math.abs

/** What to do with the tack you are on. */
public enum class TackAdvice {
    /** You are on the lifted tack: stay on it. */
    HOLD,

    /** You are headed: the other tack is lifted. */
    TACK,

    /** The wind sits at its mean: neither tack is favoured, sail for position. */
    EITHER,

    /** No usable heading, or not sailing close-hauled or on the downwind angle. */
    UNKNOWN,
}

/** Which half of the course to go to. */
public enum class FavouredSide { LEFT, RIGHT, EVEN, UNKNOWN }

/** The wind and speed observed in the right half of the course compared with the left half. */
public data class SideComparison(
    val leftSamples: Int = 0,
    val rightSamples: Int = 0,
    /** Positive when the wind on the right is veered (clockwise) relative to the wind on the left. */
    val windDifferenceDegrees: Double? = null,
    /** Positive when the boat was faster close-hauled on the right. */
    val speedDifferenceMps: Double? = null,
)

/**
 * The upwind game plan.
 *
 * @property referenceWindDegrees the wind the shifts are judged against: the weighted centre of the histogram,
 *   or the set wind while there are too few samples ([referenceIsMeasured]); see [WindReference].
 * @property oscillationDegrees the spread of the histogram: how far the wind typically swings either side.
 * @property shiftFromReferenceDegrees the estimated wind now minus the reference: positive = veered.
 * @property favouredTack the tack that is lifted now, null when the shift is within the dead band.
 * @property trendDegrees the recent wind minus the reference: a persistent shift in progress.
 * @property sideScoreDegrees the sum of the evidence for the right side (positive) or the left (negative),
 *   in degrees of wind shift equivalent; speed differences are converted at [UpwindStrategy.PERCENT_SPEED_PER_DEGREE].
 */
public data class UpwindPlan(
    val referenceWindDegrees: Double,
    val referenceIsMeasured: Boolean,
    val oscillationDegrees: Double? = null,
    val shiftFromReferenceDegrees: Double? = null,
    val pointOfSail: PointOfSail? = null,
    val currentTack: Tack? = null,
    val favouredTack: Tack? = null,
    val tackAdvice: TackAdvice = TackAdvice.UNKNOWN,
    val sides: SideComparison = SideComparison(),
    val trendDegrees: Double? = null,
    val sideScoreDegrees: Double? = null,
    val favouredSide: FavouredSide = FavouredSide.UNKNOWN,
)

/**
 * Decides when to tack and which side of the course pays, from what has been measured.
 *
 * The wind is assumed to keep oscillating over the histogram, so the weighted centre of the histogram is the
 * wind to beat against: when the wind is veered from it the starboard tack is lifted (and the port gybe
 * downwind), when it is backed the port tack is. A boat on the headed tack should tack.
 *
 * Which side of the course pays follows from the map: sail towards the side where the wind is shifted to
 * (a boat that goes into the header first tacks onto a longer lift) and where the boat was faster (more
 * pressure), and towards a shift that is still building (the recent wind against the longer-term centre).
 */
public object UpwindStrategy {

    /** Shifts smaller than this are noise: neither tack is favoured. */
    public const val SHIFT_DEAD_BAND_DEGREES: Double = 3.0

    /** A side needs this many close-hauled samples before its wind and speed count. */
    public const val MIN_SIDE_SAMPLES: Int = 20

    /** A side score smaller than this means the sides are even. */
    public const val SIDE_DEAD_BAND_DEGREES: Double = 4.0

    /** The recent wind is the mean of the last so many upwind samples, and needs at least the minimum. */
    public const val TREND_SAMPLES: Int = 600
    public const val MIN_TREND_SAMPLES: Int = 300

    /** A 1 degree lift is worth about 1.6 % of upwind VMG at a 45 degree tack angle. */
    public const val PERCENT_SPEED_PER_DEGREE: Double = 1.6

    /**
     * @param estimatedWindDegrees the wind the boat is measuring now, steadied over its last few samples.
     * @param onAngle whether the boat is close-hauled or on its downwind angle, so that the estimate means
     *   something; off the angle there is no shift and no advice.
     */
    public fun plan(
        windReference: WindReference,
        histogram: WindHistogram,
        history: WindHistory,
        sailing: SailingState?,
        estimatedWindDegrees: Double?,
        grid: TrackGrid?,
        onAngle: Boolean,
    ): UpwindPlan {
        val measured = windReference.isMeasured
        val reference = windReference.directionDegrees

        val shift = if (onAngle && sailing != null && estimatedWindDegrees != null) Angles.signedDifference(reference, estimatedWindDegrees) else null
        val favouredTack = shift?.takeIf { abs(it) >= SHIFT_DEAD_BAND_DEGREES }?.let { s ->
            val veered = s > 0.0
            // Upwind the lifted tack has the wind shifted towards its side; downwind it is the other way round.
            if (veered == (sailing!!.pointOfSail == PointOfSail.UPWIND)) Tack.STARBOARD else Tack.PORT
        }
        val advice = when {
            shift == null -> TackAdvice.UNKNOWN
            favouredTack == null -> TackAdvice.EITHER
            favouredTack == sailing!!.tack -> TackAdvice.HOLD
            else -> TackAdvice.TACK
        }

        val sides = compareSides(grid)
        val trend = if (measured) trend(history, reference) else null
        val speedTerm = sides.speedDifferenceMps?.let { difference ->
            val left = grid!!.side(Side.LEFT).meanSpeedMps!!
            val right = grid.side(Side.RIGHT).meanSpeedMps!!
            val average = (left + right) / 2
            if (average > 0.0) difference / average * 100.0 / PERCENT_SPEED_PER_DEGREE else 0.0
        }
        val evidence = listOfNotNull(sides.windDifferenceDegrees, speedTerm, trend)
        val score = if (evidence.isEmpty()) null else evidence.sum()
        val favouredSide = when {
            score == null -> FavouredSide.UNKNOWN
            score > SIDE_DEAD_BAND_DEGREES -> FavouredSide.RIGHT
            score < -SIDE_DEAD_BAND_DEGREES -> FavouredSide.LEFT
            else -> FavouredSide.EVEN
        }

        return UpwindPlan(
            referenceWindDegrees = reference,
            referenceIsMeasured = measured,
            oscillationDegrees = if (measured) histogram.standardDeviation() else null,
            shiftFromReferenceDegrees = shift,
            pointOfSail = sailing?.pointOfSail,
            currentTack = sailing?.tack,
            favouredTack = favouredTack,
            tackAdvice = advice,
            sides = sides,
            trendDegrees = trend,
            sideScoreDegrees = score,
            favouredSide = favouredSide,
        )
    }

    private fun compareSides(grid: TrackGrid?): SideComparison {
        if (grid == null) return SideComparison()
        val left = grid.side(Side.LEFT)
        val right = grid.side(Side.RIGHT)
        val enough = left.upwindSamples >= MIN_SIDE_SAMPLES && right.upwindSamples >= MIN_SIDE_SAMPLES
        return SideComparison(
            leftSamples = left.upwindSamples,
            rightSamples = right.upwindSamples,
            windDifferenceDegrees = if (enough) Angles.signedDifference(left.meanWindDegrees!!, right.meanWindDegrees!!) else null,
            speedDifferenceMps = if (enough) right.meanSpeedMps!! - left.meanSpeedMps!! else null,
        )
    }

    /** The circular mean of the recent upwind samples relative to [referenceDegrees], or null with too few. */
    private fun trend(history: WindHistory, referenceDegrees: Double): Double? {
        val recent = history.recentUpwind(TREND_SAMPLES)
        if (recent.size < MIN_TREND_SAMPLES) return null
        return Angles.signedDifference(referenceDegrees, WindHistory.meanDirectionDegrees(recent)!!)
    }
}
