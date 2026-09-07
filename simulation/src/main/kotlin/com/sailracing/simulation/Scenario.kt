package com.sailracing.simulation

import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.startline.LineSide
import com.sailracing.domain.startline.StartLineCalculator

/** Builds an event that needs to know the simulated time at which it happens. */
public typealias EventFactory = (nowMillis: Long) -> RaceEvent

/** When a [Leg] ends. Times are in simulated seconds. */
public sealed interface Termination {
    public fun isMet(boat: BoatState, legStartSeconds: Double, course: RaceCourse, windDirectionDegrees: Double): Boolean

    /** After a duration counted from the start of the leg. */
    public data class After(val seconds: Double) : Termination {
        override fun isMet(boat: BoatState, legStartSeconds: Double, course: RaceCourse, windDirectionDegrees: Double): Boolean =
            boat.timeSeconds - legStartSeconds >= seconds
    }

    /** At an absolute simulated time. */
    public data class AtTime(val seconds: Double) : Termination {
        override fun isMet(boat: BoatState, legStartSeconds: Double, course: RaceCourse, windDirectionDegrees: Double): Boolean =
            boat.timeSeconds >= seconds
    }

    /** Once the boat is within a radius of a point. */
    public data class Within(val point: GeoPoint, val meters: Double) : Termination {
        override fun isMet(boat: BoatState, legStartSeconds: Double, course: RaceCourse, windDirectionDegrees: Double): Boolean =
            Geo.distanceMeters(boat.position, point) <= meters
    }

    /** Once the boat is on the course side of the start line (has crossed it), judged by the true wind. */
    public data object OnCourseSide : Termination {
        override fun isMet(boat: BoatState, legStartSeconds: Double, course: RaceCourse, windDirectionDegrees: Double): Boolean =
            StartLineCalculator.solve(course.line, boat.position, windDirectionDegrees)?.side == LineSide.COURSE
    }
}

/**
 * One scripted stretch of sailing.
 *
 * @property helm creates the helm for this leg, given the leg's start time in simulated seconds.
 * @property onStart the sailor's actions when the leg starts (after the fix at that instant).
 * @property onEnd the sailor's actions when the leg ends.
 */
public data class Leg(
    val name: String,
    val helm: (legStartSeconds: Double) -> Helm,
    val until: Termination,
    val onStart: List<EventFactory> = emptyList(),
    val onEnd: List<EventFactory> = emptyList(),
)

/** An event on the simulated timeline. */
public data class TimedEvent(val timeMillis: Long, val event: RaceEvent)

/** Ground truth at one simulated second, for assertions that the app derived the right thing. */
public data class TruthSample(val timeMillis: Long, val windDirectionDegrees: Double, val boat: BoatState)

/** The output of running a scenario. */
public data class SimulationResult(
    val course: RaceCourse,
    val wind: WindModel,
    val polar: BoatPolar,
    val timeline: List<TimedEvent>,
    val truth: List<TruthSample>,
    val milestones: Map<String, Long>,
) {
    public val durationMillis: Long get() = timeline.last().timeMillis

    public fun fixes(): List<PositionFix> = timeline.mapNotNull { (it.event as? RaceEvent.FixReceived)?.fix }

    public fun actions(): List<TimedEvent> = timeline.filter { it.event !is RaceEvent.FixReceived }

    public fun milestone(name: String): Long = milestones[name] ?: error("no milestone named '$name' in ${milestones.keys}")

    /** The true wind at a simulated time (nearest truth sample). */
    public fun windAt(timeMillis: Long): Double = truth.minBy { kotlin.math.abs(it.timeMillis - timeMillis) }.windDirectionDegrees

    /** First simulated time at or after [afterMillis] at which the boat is on the course side of the line. */
    public fun firstCourseSideCrossing(afterMillis: Long): Long? = truth.firstOrNull { sample ->
        sample.timeMillis >= afterMillis &&
            StartLineCalculator.solve(course.line, sample.boat.position, sample.windDirectionDegrees)?.side == LineSide.COURSE
    }?.timeMillis
}
