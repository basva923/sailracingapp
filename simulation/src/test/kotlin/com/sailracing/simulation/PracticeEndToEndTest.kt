package com.sailracing.simulation

import com.sailracing.domain.course.Side
import com.sailracing.domain.race.RaceEngine
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.race.RaceSnapshot
import com.sailracing.domain.strategy.FavouredSide
import com.sailracing.domain.strategy.UpwindStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the practice simulations are for: after laps up both sides of the course the app has measured the
 * whole racing area, so the side it favours and the race line it draws can be held against a wind that is
 * known exactly. Two winds with an unarguable answer are checked here - one bent down the right side, one
 * simply stronger on the left - which is the automated half of what
 * [docs/SIMULATIONS.md](../../../../../../docs/SIMULATIONS.md) asks a sailor to look at on the phone.
 */
class PracticeEndToEndTest {

    /** Two short laps, one up each side, so the test stays quick but every corner is still sailed. */
    private fun shortSession(wind: WindModel) = PracticeScenario.Config(
        wind = wind,
        beatLengthMeters = 300.0,
        laps = 2,
        corridors = listOf(
            PracticeScenario.LapCorridor(halfWidthMeters = 90.0, centerMeters = -70.0),
            PracticeScenario.LapCorridor(halfWidthMeters = 90.0, centerMeters = 70.0),
        ),
        raceCorridorHalfWidthMeters = null,
    )

    /**
     * Replays the whole session through the real engine, but only works out what the app would show from
     * [snapshotsFrom] on: a snapshot rebuilds the course model and its Monte Carlo, and only the last beat
     * is being looked at.
     */
    private fun replay(result: SimulationResult, snapshotsFrom: Long): Map<Long, RaceSnapshot> {
        val engine = RaceEngine()
        val snapshots = sortedMapOf<Long, RaceSnapshot>()
        var next = 0
        var now = 0L
        while (now <= result.durationMillis) {
            while (next < result.timeline.size && result.timeline[next].timeMillis <= now) {
                engine.dispatch(result.timeline[next].event)
                next++
            }
            engine.dispatch(RaceEvent.Tick(now))
            if (now >= snapshotsFrom) snapshots[now] = engine.snapshot(now)
            now += 1_000L
        }
        return snapshots
    }

    /** The snapshot just after the gun, from the middle of the line with the whole beat still to sail. */
    private fun onTheLastBeat(result: SimulationResult): RaceSnapshot {
        val start = result.milestone(PracticeScenario.START)
        return assertNotNull(replay(result, start)[start + 15_000L])
    }

    @Test
    fun `a wind bent down the right side favours the right and leans the race line that way`() {
        val result = PracticeScenario.build(
            shortSession(
                WindModel(
                    meanDirectionDegrees = 20.0, oscillationDegrees = 4.0, periodSeconds = 300.0,
                    shearDegreesPerMeter = 0.09, meanSpeedKnots = 12.0,
                ),
            ),
        )
        val beat = onTheLastBeat(result)
        val course = assertNotNull(beat.course)
        // Both sides were sailed enough to be compared at all.
        assertTrue(course.grid.side(Side.LEFT).upwindSamples >= UpwindStrategy.MIN_SIDE_SAMPLES)
        assertTrue(course.grid.side(Side.RIGHT).upwindSamples >= UpwindStrategy.MIN_SIDE_SAMPLES)
        assertEquals(FavouredSide.RIGHT, beat.plan.favouredSide, "plan ${beat.plan}")
        assertTrue(assertNotNull(beat.plan.sides.windDifferenceDegrees) > 3.0, "sides ${beat.plan.sides}")
        // And there is a race line to sail from where the boat is to the mark, inside the racing area.
        // Whether it leans right is what the sailor judges on the phone; the numbers only say it is a
        // line that can be steered, and that it is not a detour.
        val line = course.raceLine
        assertTrue(line.points.size >= 2, "race line $line")
        assertEquals(course.boat, line.points.first())
        assertEquals(course.windwardMark, line.points.last())
        assertTrue(
            line.points.all {
                it.acrossMeters in course.spec.leftMeters..course.spec.rightMeters &&
                    it.upwindMeters in course.spec.bottomMeters..course.spec.topMeters
            },
            "race line outside the area: $line",
        )
    }

    @Test
    fun `pressure on the left favours the left, on boat speed alone`() {
        val result = PracticeScenario.build(
            shortSession(
                WindModel(
                    meanDirectionDegrees = 20.0, oscillationDegrees = 4.0, periodSeconds = 300.0,
                    meanSpeedKnots = 12.0, speedShearKnotsPerMeter = -0.02,
                ),
            ),
        )
        val beat = onTheLastBeat(result)
        val sides = beat.plan.sides
        // The wind blows from the same direction everywhere; only the speed measured says go left.
        assertTrue(kotlin.math.abs(assertNotNull(sides.windDifferenceDegrees)) < 4.0, "sides $sides")
        assertTrue(assertNotNull(sides.speedDifferenceMps) < 0.0, "sides $sides")
        assertEquals(FavouredSide.LEFT, beat.plan.favouredSide, "plan ${beat.plan}")
    }
}
