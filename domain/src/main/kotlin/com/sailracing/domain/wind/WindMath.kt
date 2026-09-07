package com.sailracing.domain.wind

import com.sailracing.domain.geo.Angles
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Wind estimation from the boat's heading.
 *
 * Convention: on starboard tack the wind is to the right of the bow, so heading = wind - angle;
 * on port tack heading = wind + angle. This holds for upwind (tack angle) and downwind (downwind angle).
 */
public object WindMath {

    /** Wind direction implied by sailing close-hauled on port tack at [headingDegrees]. */
    public fun windFromPortTack(headingDegrees: Double, tackAngleDegrees: Int): Int =
        Angles.normalize(headingDegrees.roundToInt() - tackAngleDegrees)

    /** Wind direction implied by sailing close-hauled on starboard tack at [headingDegrees]. */
    public fun windFromStarboardTack(headingDegrees: Double, tackAngleDegrees: Int): Int =
        Angles.normalize(headingDegrees.roundToInt() + tackAngleDegrees)

    /** Tack and point of sail for a boat heading [headingDegrees] in wind from [windDirectionDegrees]. */
    public fun sailingState(headingDegrees: Double, windDirectionDegrees: Double): SailingState {
        val twa = Angles.signedDifference(headingDegrees, windDirectionDegrees)
        val tack = if (twa >= 0.0) Tack.STARBOARD else Tack.PORT
        val pointOfSail = if (abs(twa) <= 90.0) PointOfSail.UPWIND else PointOfSail.DOWNWIND
        return SailingState(tack, pointOfSail, twa)
    }

    /**
     * The wind direction the boat is actually sailing to, assuming it holds the optimal angle for its
     * current tack and point of sail (both judged against [referenceDegrees], the measured mean wind when
     * there is one and the set wind otherwise).
     */
    public fun estimatedWindDirection(
        headingDegrees: Double,
        settings: WindSettings,
        referenceDegrees: Double = settings.directionDegrees.toDouble(),
    ): Double {
        val state = sailingState(headingDegrees, referenceDegrees)
        val angle = when (state.pointOfSail) {
            PointOfSail.UPWIND -> settings.tackAngleDegrees
            PointOfSail.DOWNWIND -> settings.downwindAngleDegrees
        }
        val estimate = when (state.tack) {
            Tack.STARBOARD -> headingDegrees + angle
            Tack.PORT -> headingDegrees - angle
        }
        return Angles.normalize(estimate)
    }

    /** Signed wind shift from the reference to the estimated direction: positive = veer (clockwise). */
    public fun shiftDegrees(referenceDegrees: Double, estimatedDegrees: Double): Double =
        Angles.signedDifference(referenceDegrees, estimatedDegrees)

    /** The optimal headings around the wind [referenceDegrees] (the set wind by default). */
    public fun targetHeadings(settings: WindSettings, referenceDegrees: Double = settings.directionDegrees.toDouble()): TargetHeadings {
        val wind = referenceDegrees
        return TargetHeadings(
            starboardUpwind = Angles.normalize(wind - settings.tackAngleDegrees),
            portUpwind = Angles.normalize(wind + settings.tackAngleDegrees),
            starboardDownwind = Angles.normalize(wind - settings.downwindAngleDegrees),
            portDownwind = Angles.normalize(wind + settings.downwindAngleDegrees),
        )
    }

    /** The optimal heading for the boat's current tack and point of sail. */
    public fun targetHeading(
        settings: WindSettings,
        state: SailingState,
        referenceDegrees: Double = settings.directionDegrees.toDouble(),
    ): Double {
        val targets = targetHeadings(settings, referenceDegrees)
        return when (state.pointOfSail) {
            PointOfSail.UPWIND -> if (state.tack == Tack.STARBOARD) targets.starboardUpwind else targets.portUpwind
            PointOfSail.DOWNWIND -> if (state.tack == Tack.STARBOARD) targets.starboardDownwind else targets.portDownwind
        }
    }

    /** How far the boat points right (positive) or left (negative) of [targetDegrees]. */
    public fun headingErrorDegrees(headingDegrees: Double, targetDegrees: Double): Double =
        Angles.signedDifference(targetDegrees, headingDegrees)

    /** Velocity made good towards the wind (upwind) or away from it (downwind); always non-negative when on course. */
    public fun velocityMadeGood(speedMps: Double, state: SailingState): Double {
        val component = speedMps * cos(Angles.toRadians(state.trueWindAngleDegrees))
        return if (state.pointOfSail == PointOfSail.UPWIND) component else -component
    }
}
