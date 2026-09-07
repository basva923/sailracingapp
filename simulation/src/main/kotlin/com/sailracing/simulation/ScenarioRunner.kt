package com.sailracing.simulation

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.geo.Geo
import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.race.RaceEvent

/**
 * Runs a list of [Leg]s through [BoatDynamics] at 1 Hz and records the GPS fixes, the sailor's actions,
 * the ground truth and named milestones (the time each leg ended).
 */
public class ScenarioRunner(
    private val course: RaceCourse,
    private val wind: WindModel,
    private val model: SailingModel = SailingModel(),
    private val noise: NoiseModel = NoiseModel.none(),
    private val maxDurationSeconds: Double = 3 * 3600.0,
) {
    public fun run(initial: BoatState, legs: List<Leg>): SimulationResult {
        require(legs.isNotEmpty()) { "a scenario needs at least one leg" }
        val timeline = mutableListOf<TimedEvent>()
        val truth = mutableListOf<TruthSample>()
        val milestones = linkedMapOf<String, Long>()

        var boat = initial
        fun windAtBoat() = wind.directionAt(boat.timeSeconds, course.acrossMeters(boat.position))
        fun record() {
            val millis = (boat.timeSeconds * 1000).toLong()
            timeline += TimedEvent(millis, RaceEvent.FixReceived(fix(boat, millis)))
            truth += TruthSample(millis, windAtBoat(), boat)
        }
        record()

        for (leg in legs) {
            val legStart = boat.timeSeconds
            val startMillis = (legStart * 1000).toLong()
            leg.onStart.forEach { timeline += TimedEvent(startMillis, it(startMillis)) }
            val helm = leg.helm(legStart)

            while (!leg.until.isMet(boat, legStart, course, windAtBoat())) {
                check(boat.timeSeconds < maxDurationSeconds) { "leg '${leg.name}' did not terminate within $maxDurationSeconds s" }
                val windNow = windAtBoat()
                val target = helm.steer(HelmContext(boat, windNow, course, model.polar))
                boat = BoatDynamics.step(boat, target, windNow, model, STEP_SECONDS)
                record()
            }

            val endMillis = (boat.timeSeconds * 1000).toLong()
            leg.onEnd.forEach { timeline += TimedEvent(endMillis, it(endMillis)) }
            milestones[leg.name] = endMillis
        }
        return SimulationResult(course, wind, model.polar, timeline, truth, milestones)
    }

    private fun fix(boat: BoatState, millis: Long): PositionFix {
        if (noise.isSilent) {
            return PositionFix(boat.position, millis, boat.speedMps, boat.headingDegrees, accuracyMeters = 3.0)
        }
        val displaced = Geo.destination(boat.position, noise.bearing(), kotlin.math.abs(noise.gaussian(noise.positionSigmaMeters)))
        return PositionFix(
            point = displaced,
            timestampMillis = millis,
            speedMps = (boat.speedMps + noise.gaussian(noise.speedSigmaMps)).coerceAtLeast(0.0),
            courseDegrees = Angles.normalize(boat.headingDegrees + noise.gaussian(noise.courseSigmaDegrees)),
            accuracyMeters = 3.0 + noise.positionSigmaMeters,
        )
    }

    private companion object {
        const val STEP_SECONDS = 1.0
    }
}
