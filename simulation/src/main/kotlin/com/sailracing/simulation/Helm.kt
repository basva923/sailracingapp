package com.sailracing.simulation

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.wind.Tack
import com.sailracing.domain.wind.WindMath
import kotlin.math.abs

/** Everything a helm strategy may look at when choosing a heading. */
public data class HelmContext(
    val boat: BoatState,
    val windDirectionDegrees: Double,
    val course: RaceCourse,
    val polar: BoatPolar,
)

/** A steering strategy: returns the heading to aim for at this instant. Helms may keep state between calls. */
public abstract class Helm {
    public abstract fun steer(context: HelmContext): Double
}

/** Sail straight at a point. Only sensible when the point is not in the no-go zone. */
public class GoTo(private val target: GeoPoint) : Helm() {
    override fun steer(context: HelmContext): Double = Geo.initialBearingDegrees(context.boat.position, target)
}

/** Hold a fixed compass heading. */
public class HoldHeading(private val headingDegrees: Double) : Helm() {
    override fun steer(context: HelmContext): Double = Angles.normalize(headingDegrees)
}

/** Hold a fixed angle to the (current, shifting) wind: +90 is a beam reach with the wind over starboard. */
public class HoldWindAngle(private val offsetDegrees: Double) : Helm() {
    override fun steer(context: HelmContext): Double = Angles.normalize(context.windDirectionDegrees + offsetDegrees)
}

/** Sail close-hauled on the given tack, following the wind as it shifts, like a sailor steering to the telltales. */
public class CloseHauled(private val tack: Tack) : Helm() {
    override fun steer(context: HelmContext): Double =
        closeHauledHeading(context.windDirectionDegrees, context.polar.upwindAngleDegrees, tack)
}

/**
 * Beat upwind to a mark: sail close-hauled on the current tack until the mark can be laid,
 * then point straight at it (which is how the boat changes tack on the layline).
 *
 * @property corridorHalfWidthMeters when set, the helm stays within this distance of the course axis and
 *   tacks at the edge of the corridor, zigzagging up the middle like a boat that plays the shifts,
 *   instead of sailing out to a layline in one board.
 */
public class BeatTo(
    private val mark: GeoPoint,
    initialTack: Tack,
    private val corridorHalfWidthMeters: Double? = null,
) : Helm() {
    public var tack: Tack = initialTack
        private set

    override fun steer(context: HelmContext): Double {
        val wind = context.windDirectionDegrees
        val bearing = Geo.initialBearingDegrees(context.boat.position, mark)
        val markFromWindAxis = Angles.signedDifference(wind, bearing)
        if (abs(markFromWindAxis) >= context.polar.upwindAngleDegrees) {
            // The mark is on or beyond a layline: fetch it directly.
            tack = if (markFromWindAxis > 0) Tack.PORT else Tack.STARBOARD
            return bearing
        }
        tack = WindMath.sailingState(context.boat.headingDegrees, wind).tack
        val limit = corridorHalfWidthMeters
        if (limit != null) {
            // Starboard tack carries the boat to the left of the axis, port to the right.
            val across = context.course.acrossMeters(context.boat.position)
            val leavingCorridor = if (tack == Tack.STARBOARD) across <= -limit else across >= limit
            if (leavingCorridor) tack = tack.opposite()
        }
        return closeHauledHeading(wind, context.polar.upwindAngleDegrees, tack)
    }
}

/**
 * Run downwind to a mark: sail the polar's downwind angle on the current gybe until the mark can be reached
 * without sailing lower, then point straight at it.
 */
public class RunTo(private val mark: GeoPoint, initialTack: Tack) : Helm() {
    public var tack: Tack = initialTack
        private set

    override fun steer(context: HelmContext): Double {
        val wind = context.windDirectionDegrees
        val bearing = Geo.initialBearingDegrees(context.boat.position, mark)
        val downwindAxis = Angles.normalize(wind + 180.0)
        val markFromAxis = Angles.signedDifference(downwindAxis, bearing)
        val gybeAngle = 180.0 - context.polar.downwindAngleDegrees
        if (abs(markFromAxis) >= gybeAngle) {
            // Sailing straight at the mark keeps the true wind angle at or above the polar's downwind angle.
            tack = if (markFromAxis > 0) Tack.STARBOARD else Tack.PORT
            return bearing
        }
        tack = WindMath.sailingState(context.boat.headingDegrees, wind).tack
        return downwindHeading(wind, context.polar.downwindAngleDegrees, tack)
    }
}

/**
 * Pre-start helm. It shuttles on a reach between two launch points below the line, one on each side of the
 * centre, holds head-to-wind at a launch point when there is less than a round trip left, and hardens up
 * close-hauled towards the line centre when the time to kill reaches zero, so the boat crosses at the gun.
 *
 * @property standoffMeters distance of the launch points below the line and to either side of its centre.
 * @property accelerationAllowanceSeconds extra time budgeted for bearing away and accelerating from a standstill.
 */
public class TimedStart(
    private val gunTimeSeconds: Double,
    private val standoffMeters: Double = 60.0,
    private val accelerationAllowanceSeconds: Double = 5.0,
) : Helm() {

    public enum class Phase { SHUTTLE, HOLD, GO }

    public var phase: Phase = Phase.SHUTTLE
        private set
    private var target: GeoPoint? = null
    private var goTack: Tack = Tack.PORT

    override fun steer(context: HelmContext): Double {
        val course = context.course
        val boat = context.boat
        val wind = context.windDirectionDegrees
        val polar = context.polar
        val remaining = gunTimeSeconds - boat.timeSeconds

        val center = course.lineCenter
        val downwind = Geo.initialBearingDegrees(course.windwardMark, center)
        val below = Geo.destination(center, downwind, standoffMeters)
        val left = Geo.destination(below, Geo.initialBearingDegrees(course.boatEnd, course.pinEnd), standoffMeters)
        val right = Geo.destination(below, Geo.initialBearingDegrees(course.pinEnd, course.boatEnd), standoffMeters)
        val closeHauledSpeed = polar.speedMps(polar.upwindAngleDegrees)
        fun neededFrom(point: GeoPoint) = Geo.distanceMeters(point, center) / closeHauledSpeed + accelerationAllowanceSeconds
        val roundTrip = 2 * Geo.distanceMeters(left, right) / polar.maxSpeedMps + ROUND_TRIP_MARGIN_SECONDS

        when (phase) {
            Phase.SHUTTLE -> {
                val current = target ?: left.also { target = it }
                if (Geo.distanceMeters(boat.position, current) <= ARRIVAL_RADIUS_METERS) {
                    if (remaining <= neededFrom(current) + roundTrip) {
                        phase = Phase.HOLD
                        goTack = if (current == left) Tack.PORT else Tack.STARBOARD
                    } else {
                        target = if (current == left) right else left
                    }
                }
                return if (phase == Phase.HOLD) wind else Geo.initialBearingDegrees(boat.position, target!!)
            }
            Phase.HOLD -> {
                if (remaining > neededFrom(boat.position)) return wind
                phase = Phase.GO
                return closeHauledHeading(wind, polar.upwindAngleDegrees, goTack)
            }
            Phase.GO -> return closeHauledHeading(wind, polar.upwindAngleDegrees, goTack)
        }
    }

    private companion object {
        const val ARRIVAL_RADIUS_METERS = 6.0
        const val ROUND_TRIP_MARGIN_SECONDS = 10.0
    }
}

internal fun Tack.opposite(): Tack = if (this == Tack.PORT) Tack.STARBOARD else Tack.PORT

/** Starboard: wind to the right, heading left of the wind. */
internal fun closeHauledHeading(windDegrees: Double, angle: Double, tack: Tack): Double = when (tack) {
    Tack.STARBOARD -> Angles.normalize(windDegrees - angle)
    Tack.PORT -> Angles.normalize(windDegrees + angle)
}

internal fun downwindHeading(windDegrees: Double, angle: Double, tack: Tack): Double = when (tack) {
    Tack.STARBOARD -> Angles.normalize(windDegrees - angle)
    Tack.PORT -> Angles.normalize(windDegrees + angle)
}
