package com.sailracing.domain.course

import com.sailracing.domain.wind.Tack
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The Monte Carlo over the race line: what the safe line and the flyer are, and what makes them differ.
 *
 * The winds are drawn with a fixed seed, so every one of these runs the same way every time. What is
 * asserted is never a particular line but the property the plan promises: that a settled wind leaves
 * nothing to gamble on, that an uncertain side of the course is where the flyer goes and the safe line
 * does not, and that a line is timed over the same winds as every other.
 */
class RaceLinePlannerTest {

    /** 600 x 600 m of 50 m squares, the boat just above the start, the mark at the top middle. */
    private val spec = GridSpec(-300.0, 0.0, 12, 12, 50.0)
    private val boat = CoursePosition(0.0, 5.0)
    private val mark = CoursePosition(0.0, 595.0)

    /** Where the line goes: the mean of how far right its points are, weighted by nothing but their number. */
    private fun sideOf(line: RaceLine): Double = line.points.map { it.acrossMeters }.average()

    private fun plan(
        field: WindField,
        currentTack: Tack? = null,
        currentShiftDegrees: Double? = null,
        settings: RaceLineSettings = RaceLineSettings(),
    ): RaceLinePlan = RaceLinePlanner.plan(field, boat, mark, 45, currentTack, currentShiftDegrees, settings)

    @Test
    fun `a settled wind leaves nothing to gamble on`() {
        // Every square measured over and over at the same wind and the same speed: the spread of every
        // square is the floor, and every simulation comes out with the same beat.
        val settled = CourseFixtures.even(spec, samplesPerCell = 60)
        val plan = plan(settled)

        assertTrue(plan.agree, "a settled wind produced a flyer: ${plan.fast}")
        assertEquals(plan.safe.points, plan.fast.points)
        assertFalse(plan.isEmpty)
        assertEquals(RaceLineSettings().runs, plan.runs)
        assertEquals(plan.runs, plan.sampled.size)
        // The bad day is barely worse than the good one, and the mean sits between them.
        assertTrue(plan.safeRisk.riskSeconds < 30.0, "a settled wind is not a gamble: ${plan.safeRisk}")
        assertTrue(plan.safeRisk.goodSeconds <= plan.safeRisk.meanSeconds)
        assertTrue(plan.safeRisk.meanSeconds <= plan.safeRisk.badSeconds)
        assertEquals(plan.safe.seconds, plan.safeRisk.meanSeconds, 1e-9, "the drawn line's time is its mean over the runs")
        // A beat of 600 m at 3 m/s takes at least 600 * sqrt(2) / 3 = 283 s, and this one is close to it.
        assertTrue(plan.safeRisk.meanSeconds in 280.0..320.0, "${plan.safeRisk}")
    }

    @Test
    fun `the flyer goes to the side nobody can be sure of, and the race line does not`() {
        // Both sides average the same wind. The right half swings 20 degrees either way from sample to
        // sample, the left half barely moves: the same mean, a very different bet.
        val shifty = CourseFixtures.field(spec, samplesPerCell = 60) { cell, index, _ ->
            val swing = if (cell.column >= 6) 20.0 else 2.0
            CourseFixtures.Sample(if (index % 2 == 0) swing else -swing, 3.0)
        }
        val plan = plan(shifty)

        assertFalse(plan.agree, "the two sides are not the same bet, so the lines should differ")
        assertTrue(sideOf(plan.fast) > sideOf(plan.safe) + 20.0, "the flyer should go right: ${sideOf(plan.fast)} against ${sideOf(plan.safe)}")
        assertTrue(sideOf(plan.fast) > 0.0, "the flyer should go into the uncertain half: ${sideOf(plan.fast)}")
        // The gamble is a gamble: it wins some of the winds and loses others.
        assertTrue(plan.winFraction > 0.0 && plan.winFraction < 1.0, "the flyer neither won nor lost anything: ${plan.winFraction}")
        assertTrue(plan.fastRisk.spreadSeconds > plan.safeRisk.spreadSeconds, "the flyer should be the more variable line")
        assertTrue(plan.fastRisk.goodSeconds < plan.safeRisk.goodSeconds, "the flyer should have the better good day")
        assertTrue(plan.safeRisk.badSeconds < plan.fastRisk.badSeconds, "the race line should have the better bad day")
    }

    @Test
    fun `more wind on one side is worth going for, even with the wind straight up the course`() {
        // One wind everywhere, but the left half of the course is measured a knot and a half faster.
        val pressure = CourseFixtures.field(spec, samplesPerCell = 60) { cell, _, _ ->
            CourseFixtures.Sample(0.0, if (cell.column < 6) 3.6 else 2.8)
        }
        val plan = plan(pressure)
        assertTrue(sideOf(plan.safe) < -50.0, "the race line should go into the pressure on the left: ${sideOf(plan.safe)}")
        assertTrue(plan.safe.points.any { it.acrossMeters < -200.0 }, "it hardly went left at all: ${plan.safe}")

        // The same course with the pressure on the right: the line mirrors.
        val mirrored = CourseFixtures.field(spec, samplesPerCell = 60) { cell, _, _ ->
            CourseFixtures.Sample(0.0, if (cell.column < 6) 2.8 else 3.6)
        }
        assertTrue(sideOf(plan(mirrored).safe) > 50.0, "the line did not follow the pressure to the right")
    }

    @Test
    fun `a puffy side is a gamble even when it averages the same speed as the steady one`() {
        // The right half is half puff, half hole; the left is steady. Both average 3 m/s exactly.
        val puffy = CourseFixtures.field(spec, samplesPerCell = 60) { cell, index, _ ->
            val speed = if (cell.column < 6) 3.0 else if (index % 2 == 0) 4.2 else 1.8
            CourseFixtures.Sample(0.0, speed)
        }
        for (column in 0 until 12) {
            assertEquals(3.0, puffy.speedMps(GridCell(column, 6)), 0.01, "the fixture means both halves to average 3 m/s")
        }
        val plan = plan(puffy)
        assertFalse(plan.agree, "the same mean speed with a very different spread is still a bet")
        assertTrue(sideOf(plan.fast) > sideOf(plan.safe), "the flyer should hunt the puffs: ${sideOf(plan.fast)} against ${sideOf(plan.safe)}")
        assertTrue(plan.fastRisk.spreadSeconds > plan.safeRisk.spreadSeconds)
    }

    @Test
    fun `the wind the boat is measuring now bends the line, and its tack costs to leave`() {
        val even = CourseFixtures.even(spec, samplesPerCell = 60)
        val straight = plan(even)
        // Beating in a wind veered 25 degrees on top of everything measured: the whole beat leans right.
        val veered = plan(even, currentShiftDegrees = 25.0)
        val backed = plan(even, currentShiftDegrees = -25.0)
        // A veer lifts starboard, and starboard points up and to the left: the beat leans that way, and the
        // boat comes back to the mark on a short port tack.
        assertTrue(sideOf(veered.safe) < sideOf(straight.safe), "a veer did not send the boat out on starboard")
        assertTrue(sideOf(backed.safe) > sideOf(straight.safe), "a backing wind did not send the boat out on port")
        assertEquals(-sideOf(veered.safe), sideOf(backed.safe), 25.0, "the two shifts should mirror each other")

        // On port tack the line may start on port for nothing; starting on starboard costs a tack.
        val onPort = plan(even, currentTack = Tack.PORT)
        val onStarboard = plan(even, currentTack = Tack.STARBOARD)
        assertNotEquals(onPort.safe.points, onStarboard.safe.points, "the tack the boat is on made no difference at all")
        for (tacked in listOf(onPort, onStarboard)) {
            assertTrue(
                tacked.safeRisk.meanSeconds >= straight.safeRisk.meanSeconds - 1e-9,
                "starting on a tack cannot be quicker than being free to pick one",
            )
        }
    }

    @Test
    fun `every candidate is timed over the same winds, and the mean wind is always one of them`() {
        val shifty = CourseFixtures.field(spec, samplesPerCell = 60) { cell, index, random ->
            CourseFixtures.Sample((cell.column - 5.5) * 2.0 + random.nextDouble(-12.0, 12.0) * (index % 2), 3.0)
        }
        val settings = RaceLineSettings(runs = 12)
        val plan = plan(shifty, settings = settings)
        assertEquals(12, plan.runs)
        assertEquals(12, plan.sampled.size)

        // The line through the mean wind is a candidate, so it can never be beaten by a line that is not.
        val mean = RaceLineFinder.find(WindSampler.mean(shifty, boat, null, settings), boat, mark, 45)
        val meanTimes = List(12) { run ->
            RaceLineFinder.secondsToSail(WindSampler.sample(shifty, boat, null, settings, kotlin.random.Random(settings.seed + run)), mean, 45)
        }
        assertTrue(
            RaceLinePlanner.percentile(meanTimes.sorted(), 0.8) >= plan.safeRisk.badSeconds - 1e-9,
            "the safe line is beaten on its own measure by the line through the mean wind",
        )
        // And the safe line's own times are what the plan says they are.
        val safeTimes = List(12) { run ->
            RaceLineFinder.secondsToSail(WindSampler.sample(shifty, boat, null, settings, kotlin.random.Random(settings.seed + run)), plan.safe, 45)
        }
        assertEquals(safeTimes.average(), plan.safeRisk.meanSeconds, 1e-9)
        assertEquals(RaceLinePlanner.percentile(safeTimes.sorted(), 0.2), plan.safeRisk.goodSeconds, 1e-9)
        assertEquals(RaceLinePlanner.percentile(safeTimes.sorted(), 0.8), plan.safeRisk.badSeconds, 1e-9)
    }

    @Test
    fun `the same course always gives the same plan`() {
        val field = CourseFixtures.field(spec, samplesPerCell = 40) { cell, index, random ->
            CourseFixtures.Sample((cell.row - 5.5) * 1.5 + random.nextDouble(-8.0, 8.0) * (index % 2), 2.6 + cell.column * 0.05)
        }
        assertEquals(plan(field), plan(field))
        // Another seed is another set of winds, and may well be another plan; it is still a plan.
        val other = plan(field, settings = RaceLineSettings(seed = 99L))
        assertFalse(other.isEmpty)
        assertEquals(mark, other.safe.points.last())
    }

    @Test
    fun `a leg that is not a beat has one answer and no gamble`() {
        val even = CourseFixtures.even(spec, samplesPerCell = 20)
        val downwind = RaceLinePlanner.plan(even, from = mark, to = boat, tackAngleDegrees = 45)
        assertTrue(downwind.agree, "running to a mark downwind is not a bet")
        assertEquals(listOf(mark, boat), downwind.safe.points)
        assertTrue(downwind.safeRisk.meanSeconds > 0.0)
    }

    @Test
    fun `the whole plan can be pinned to the boat`() {
        val shifty = CourseFixtures.field(spec, samplesPerCell = 60) { cell, index, _ ->
            CourseFixtures.Sample(if (index % 2 == 0) 18.0 else -18.0, if (cell.column < 6) 3.2 else 2.8)
        }
        val plan = plan(shifty)
        val moved = CoursePosition(12.0, 30.0)
        val pinned = plan.anchoredAt(moved)
        assertEquals(moved, pinned.safe.points.first())
        assertEquals(moved, pinned.fast.points.first())
        assertTrue(pinned.sampled.all { it.points.first() == moved })
        assertEquals(plan.safe.points.drop(1), pinned.safe.points.drop(1), "pinning changed the boards")
        assertEquals(plan.safeRisk, pinned.safeRisk)
        assertEquals(RaceLinePlan.NONE, RaceLinePlan.NONE.anchoredAt(moved), "there is nothing to pin without a plan")
        assertTrue(RaceLinePlan.NONE.isEmpty)
        assertEquals(LineRisk.NONE, RaceLinePlan.NONE.safeRisk)
    }

    @Test
    fun `a risk is the seconds between a good day and a bad one`() {
        assertEquals(0.0, LineRisk.NONE.riskSeconds)
        assertEquals(30.0, LineRisk(meanSeconds = 100.0, goodSeconds = 90.0, badSeconds = 120.0, spreadSeconds = 12.0).riskSeconds)
    }

    @Test
    fun `a percentile is the value that far through the runs`() {
        val runs = listOf(10.0, 20.0, 30.0, 40.0, 50.0)
        assertEquals(10.0, RaceLinePlanner.percentile(runs, 0.0))
        assertEquals(30.0, RaceLinePlanner.percentile(runs, 0.5))
        assertEquals(50.0, RaceLinePlanner.percentile(runs, 1.0))
        assertEquals(25.0, RaceLinePlanner.percentile(runs, 0.375), 1e-9, "between two runs it is interpolated")
        assertEquals(10.0, RaceLinePlanner.percentile(runs, -1.0), "out of range fractions are held at the ends")
        assertEquals(50.0, RaceLinePlanner.percentile(runs, 2.0))
        assertEquals(7.0, RaceLinePlanner.percentile(listOf(7.0), 0.3), "one run is every percentile of itself")
    }

    @Test
    fun `one run is a plan too`() {
        val field = CourseFixtures.even(spec, samplesPerCell = 20)
        val single = plan(field, settings = RaceLineSettings(runs = 1))
        assertEquals(1, single.runs)
        assertEquals(1, single.sampled.size)
        assertEquals(single.safeRisk.goodSeconds, single.safeRisk.badSeconds, 1e-9, "one run has no spread to speak of")
        assertEquals(0.0, single.safeRisk.spreadSeconds, 1e-9)
        assertTrue(abs(single.winFraction) < 1e-9 || abs(single.winFraction - 1.0) < 1e-9)
    }
}
