package com.sailracing.simulation

import com.sailracing.domain.geo.Geo
import com.sailracing.domain.race.RaceEvent
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StandardRaceScenarioTest {

    @Test
    fun `the scripted race visits every milestone in order`() {
        val result = StandardRaceScenario.build()
        val order = listOf(
            StandardRaceScenario.MARK_PIN, StandardRaceScenario.MARK_BOAT, StandardRaceScenario.WIND_STARBOARD,
            StandardRaceScenario.WIND_PORT, StandardRaceScenario.TO_PRESTART, StandardRaceScenario.COUNTDOWN_SYNC,
            StandardRaceScenario.START, StandardRaceScenario.BEAT, StandardRaceScenario.RUN, StandardRaceScenario.FINISH,
        )
        assertEquals(order, result.milestones.keys.toList())
        val times = order.map(result::milestone)
        assertEquals(times, times.sorted())
        assertTrue(result.durationMillis < 40 * 60_000L, "race took ${result.durationMillis / 1000} s")

        val actions = result.actions().map { it.event }
        assertTrue(RaceEvent.MarkPinEnd in actions)
        assertTrue(RaceEvent.MarkBoatEnd in actions)
        assertTrue(RaceEvent.SetWindFromStarboardTack in actions)
        assertTrue(RaceEvent.SetWindFromPortTack in actions)
        assertTrue(RaceEvent.SetWindwardMarkFromLine(20, 500.0) in actions)
        assertTrue(actions.any { it is RaceEvent.StartCountdown })
        assertTrue(actions.any { it is RaceEvent.SyncCountdown })
        assertEquals(RaceEvent.StopTimer, actions.last())
    }

    @Test
    fun `the boat crosses the line at the gun and rounds the marks`() {
        val result = StandardRaceScenario.build()
        val gun = StandardRaceScenario.gunMillis(result)
        val crossing = assertNotNull(result.firstCourseSideCrossing(result.milestone(StandardRaceScenario.COUNTDOWN_SYNC)))
        assertTrue(abs(crossing - gun) <= 5_000, "crossed ${(crossing - gun) / 1000.0} s from the gun")
        assertEquals(result.milestone(StandardRaceScenario.START), crossing)

        val course = result.course
        fun positionAt(millis: Long) = result.truth.first { it.timeMillis == millis }.boat.position
        assertTrue(Geo.distanceMeters(positionAt(result.milestone(StandardRaceScenario.BEAT)), course.windwardMark) <= 15.0)
        assertTrue(Geo.distanceMeters(positionAt(result.milestone(StandardRaceScenario.RUN)), course.leewardMark) <= 15.0)
    }

    @Test
    fun `defaults describe the same course, boat and legs as the default configuration`() {
        val config = StandardRaceScenario.Config()
        assertEquals(StandardRaceScenario.course(config), StandardRaceScenario.course())
        assertEquals(StandardRaceScenario.initialState(config), StandardRaceScenario.initialState())
        assertEquals(StandardRaceScenario.legs(config).map { it.name }, StandardRaceScenario.legs().map { it.name })
        assertEquals(10, StandardRaceScenario.legs().size)
        val course = StandardRaceScenario.course()
        val initial = StandardRaceScenario.initialState()
        assertEquals(60.0, Geo.distanceMeters(initial.position, course.pinEnd), 0.01)
        assertEquals(110.0, initial.headingDegrees, 1e-9)
    }

    @Test
    fun `configuration is honoured`() {
        val config = StandardRaceScenario.Config(
            wind = WindModel(meanDirectionDegrees = 200.0),
            noise = NoiseModel.typicalGps(seed = 3),
            lineLengthMeters = 80.0,
            beatLengthMeters = 300.0,
        )
        val course = StandardRaceScenario.course(config)
        assertEquals(80.0, Geo.distanceMeters(course.pinEnd, course.boatEnd), 0.01)
        val result = StandardRaceScenario.build(config)
        assertEquals(course, result.course)
        assertTrue(result.fixes().any { assertNotNull(it.accuracyMeters) > 3.0 })
        assertEquals(200.0, result.windAt(0))
        // By default the wind is veered on the right of the course, and the ground truth follows the boat.
        val sheared = StandardRaceScenario.build()
        val start = sheared.milestone(StandardRaceScenario.START)
        val beat = sheared.truth.filter { it.timeMillis in start..sheared.milestone(StandardRaceScenario.BEAT) }
        val rightmost = beat.maxBy { sheared.course.acrossMeters(it.boat.position) }
        val leftmost = beat.minBy { sheared.course.acrossMeters(it.boat.position) }
        val expectedDifference = StandardRaceScenario.DEFAULT_SHEAR_DEGREES_PER_METER *
            (sheared.course.acrossMeters(rightmost.boat.position) - sheared.course.acrossMeters(leftmost.boat.position))
        assertTrue(expectedDifference > 3.0, "the corridor should span enough for a visible shear: $expectedDifference")
        assertEquals(
            sheared.wind.directionAt(rightmost.timeMillis / 1000.0) + expectedDifference,
            sheared.wind.directionAt(leftmost.timeMillis / 1000.0) + (rightmost.windDirectionDegrees - leftmost.windDirectionDegrees),
            0.01,
        )
    }
}
