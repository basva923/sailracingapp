package com.sailracing.simulation

import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.startline.LineSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScenarioRunnerTest {

    private val center = GeoPoint(51.14, 5.83)
    private val course = RaceCourse.square(center, windDirectionDegrees = 0.0)
    private val wind = WindModel(0.0)
    private val below = Geo.destination(center, 180.0, 100.0)
    private val initial = BoatState(0.0, below, headingDegrees = 90.0, speedMps = 3.0)

    @Test
    fun `runs legs in order and records fixes, actions, truth and milestones`() {
        val legs = listOf(
            Leg("hold", helm = { HoldHeading(90.0) }, until = Termination.After(5.0),
                onStart = listOf { now -> RaceEvent.StartCountdown(1, now) }, onEnd = listOf { RaceEvent.StopTimer }),
            Leg("cross", helm = { HoldHeading(45.0) }, until = Termination.OnCourseSide),
        )
        val result = ScenarioRunner(course, wind).run(initial, legs)

        assertEquals(RaceEvent.StartCountdown(1, 0), result.timeline[1].event)
        assertEquals(5_000L, result.milestone("hold"))
        assertEquals(listOf(RaceEvent.StartCountdown(1, 0), RaceEvent.StopTimer), result.actions().map { it.event })
        assertEquals(5_000L, result.actions()[1].timeMillis)
        val fixes = result.fixes()
        assertEquals(result.truth.size, fixes.size)
        assertEquals(0L, fixes.first().timestampMillis)
        assertEquals(result.durationMillis, fixes.last().timestampMillis)
        assertEquals(3.0, assertNotNull(fixes.first().accuracyMeters))
        assertEquals(0.0, result.windAt(1234))
        val crossing = assertNotNull(result.firstCourseSideCrossing(0))
        assertEquals(result.milestone("cross"), crossing)
        assertNull(result.firstCourseSideCrossing(result.durationMillis + 1))
        assertEquals(course, result.course)
        assertEquals(wind, result.wind)
        assertEquals(BoatPolar(), result.polar)
        assertFailsWith<IllegalStateException> { result.milestone("nope") }
    }

    @Test
    fun `terminations`() {
        val boat = BoatState(12.0, below, 0.0, 1.0)
        assertTrue(Termination.After(2.0).isMet(boat, 10.0, course, 0.0))
        assertTrue(!Termination.After(3.0).isMet(boat, 10.0, course, 0.0))
        assertTrue(Termination.AtTime(12.0).isMet(boat, 0.0, course, 0.0))
        assertTrue(!Termination.AtTime(13.0).isMet(boat, 0.0, course, 0.0))
        assertTrue(Termination.Within(below, 1.0).isMet(boat, 0.0, course, 0.0))
        assertTrue(!Termination.Within(center, 1.0).isMet(boat, 0.0, course, 0.0))
        assertTrue(!Termination.OnCourseSide.isMet(boat, 0.0, course, 0.0))
        val above = BoatState(0.0, Geo.destination(center, 0.0, 10.0), 0.0, 1.0)
        assertTrue(Termination.OnCourseSide.isMet(above, 0.0, course, 0.0))
        // Judged by the given wind: with a south wind, "below" is the course side.
        assertTrue(Termination.OnCourseSide.isMet(boat, 0.0, course, 180.0))
        assertEquals(LineSide.COURSE, com.sailracing.domain.startline.StartLineCalculator.solve(course.line, above.position, 0.0)?.side)
    }

    @Test
    fun `noise perturbs fixes deterministically`() {
        val legs = listOf(Leg("hold", helm = { HoldHeading(90.0) }, until = Termination.After(20.0)))
        val a = ScenarioRunner(course, wind, noise = NoiseModel.typicalGps(1)).run(initial, legs)
        val b = ScenarioRunner(course, wind, noise = NoiseModel.typicalGps(1)).run(initial, legs)
        assertEquals(a.fixes(), b.fixes())
        val clean = ScenarioRunner(course, wind).run(initial, legs)
        val noisy = a.fixes()
        val errors = noisy.zip(clean.fixes()).map { (n, c) -> Geo.distanceMeters(n.point, c.point) }
        assertTrue(errors.any { it > 0.1 })
        assertTrue(errors.all { it < 10.0 })
        assertEquals(4.5, assertNotNull(noisy.first().accuracyMeters))
        assertTrue(noisy.all { assertNotNull(it.speedMps) >= 0.0 })
    }

    @Test
    fun `refuses empty scenarios and non-terminating legs`() {
        assertFailsWith<IllegalArgumentException> { ScenarioRunner(course, wind).run(initial, emptyList()) }
        val forever = listOf(Leg("forever", helm = { HoldHeading(90.0) }, until = Termination.Within(course.windwardMark, 1.0)))
        assertFailsWith<IllegalStateException> { ScenarioRunner(course, wind, maxDurationSeconds = 30.0).run(initial, forever) }
    }
}
