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
     * there is one and the set wind otherwise). Upwind that angle is [tackAngleDegrees]: the one measured
     * off the boat's own tacks when there is one, the set one until then.
     */
    public fun estimatedWindDirection(
        headingDegrees: Double,
        settings: WindSettings,
        referenceDegrees: Double = settings.directionDegrees.toDouble(),
        tackAngleDegrees: Double = settings.tackAngleDegrees.toDouble(),
    ): Double {
        val state = sailingState(headingDegrees, referenceDegrees)
        val angle = when (state.pointOfSail) {
            PointOfSail.UPWIND -> tackAngleDegrees
            PointOfSail.DOWNWIND -> settings.downwindAngleDegrees.toDouble()
        }
        val estimate = when (state.tack) {
            Tack.STARBOARD -> headingDegrees + angle
            Tack.PORT -> headingDegrees - angle
        }
        return Angles.normalize(estimate)
    }

    /**
     * Whether a boat sailing [sailing] counts as close-hauled: upwind, and pointing no more than
     * [bandDegrees] below [tackAngleDegrees] off the wind (and never beyond a beam reach). Pointing higher
     * than the tack angle is never held against it - a boat cannot pinch far and keep its speed, so a
     * heading well above the angle is a lift, not a reach.
     */
    public fun isCloseHauled(sailing: SailingState, tackAngleDegrees: Double, bandDegrees: Int): Boolean =
        sailing.pointOfSail == PointOfSail.UPWIND &&
            abs(sailing.trueWindAngleDegrees) <= minOf(tackAngleDegrees + bandDegrees, MAX_CLOSE_HAULED_TWA_DEGREES)

    /** Whether a boat sailing [sailing] is on its downwind angle: at least [minTwaDegrees] off the wind. */
    public fun isOnDownwindAngle(sailing: SailingState, minTwaDegrees: Int): Boolean =
        sailing.pointOfSail == PointOfSail.DOWNWIND && abs(sailing.trueWindAngleDegrees) >= minTwaDegrees

    /** Beyond a beam reach nothing is close-hauled, whatever the band. */
    public const val MAX_CLOSE_HAULED_TWA_DEGREES: Double = 90.0

    /** Signed wind shift from the reference to the estimated direction: positive = veer (clockwise). */
    public fun shiftDegrees(referenceDegrees: Double, estimatedDegrees: Double): Double =
        Angles.signedDifference(referenceDegrees, estimatedDegrees)

    /** The optimal headings around the wind [referenceDegrees] (the set wind by default), at [tackAngleDegrees] upwind. */
    public fun targetHeadings(
        settings: WindSettings,
        referenceDegrees: Double = settings.directionDegrees.toDouble(),
        tackAngleDegrees: Double = settings.tackAngleDegrees.toDouble(),
    ): TargetHeadings {
        val wind = referenceDegrees
        return TargetHeadings(
            starboardUpwind = Angles.normalize(wind - tackAngleDegrees),
            portUpwind = Angles.normalize(wind + tackAngleDegrees),
            starboardDownwind = Angles.normalize(wind - settings.downwindAngleDegrees),
            portDownwind = Angles.normalize(wind + settings.downwindAngleDegrees),
        )
    }

    /** The optimal heading for the boat's current tack and point of sail. */
    public fun targetHeading(
        settings: WindSettings,
        state: SailingState,
        referenceDegrees: Double = settings.directionDegrees.toDouble(),
        tackAngleDegrees: Double = settings.tackAngleDegrees.toDouble(),
    ): Double {
        val targets = targetHeadings(settings, referenceDegrees, tackAngleDegrees)
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
