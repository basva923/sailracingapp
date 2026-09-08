package com.sailracing.simulation

import com.sailracing.domain.geo.Geo
import com.sailracing.domain.race.RaceEvent
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PracticeScenarioTest {

    private val config = PracticeScenario.Config(
        wind = WindModel(meanDirectionDegrees = 20.0, oscillationDegrees = 8.0, periodSeconds = 300.0, meanSpeedKnots = 12.0),
    )
    private val result = PracticeScenario.build(config)

    @Test
    fun `five laps are sailed before the start`() {
        val laps = (1..PracticeScenario.DEFAULT_LAPS).flatMap { listOf(PracticeScenario.beatLeg(it), PracticeScenario.runLeg(it)) }
        val order = listOf(
            PracticeScenario.MARK_PIN, PracticeScenario.MARK_BOAT,
            PracticeScenario.WIND_STARBOARD, PracticeScenario.WIND_PORT,
        ) + laps + listOf(
            PracticeScenario.TO_PRESTART, PracticeScenario.COUNTDOWN_SYNC,
            PracticeScenario.START, PracticeScenario.RACE_BEAT,
        )
        assertEquals(order, result.milestones.keys.toList())
        val times = order.map(result::milestone)
        assertEquals(times, times.sorted())
        // Every lap really rounds both marks.
        fun positionAt(millis: Long) = result.truth.first { it.timeMillis == millis }.boat.position
        for (lap in 1..PracticeScenario.DEFAULT_LAPS) {
            assertTrue(Geo.distanceMeters(positionAt(result.milestone(PracticeScenario.beatLeg(lap))), result.course.windwardMark) <= 15.0)
            assertTrue(Geo.distanceMeters(positionAt(result.milestone(PracticeScenario.runLeg(lap))), result.course.leewardMark) <= 15.0)
        }
        assertTrue(result.durationMillis < 90 * 60_000L, "the session took ${result.durationMillis / 60_000} min")
    }

    @Test
    fun `the laps sail up the middle first and then out to either corner`() {
        fun acrossDuring(leg: String, previous: String): List<Double> = result.truth
            .filter { it.timeMillis in result.milestone(previous)..result.milestone(leg) }
            .map { result.course.acrossMeters(it.boat.position) }

        val first = acrossDuring(PracticeScenario.beatLeg(1), PracticeScenario.WIND_PORT)
        val third = acrossDuring(PracticeScenario.beatLeg(3), PracticeScenario.runLeg(2))
        val fourth = acrossDuring(PracticeScenario.beatLeg(4), PracticeScenario.runLeg(3))
        assertTrue(first.max() < 100.0 && first.min() > -100.0, "the first lap keeps to the middle: ${first.min()}..${first.max()}")
        assertTrue(third.min() < -150.0, "the third lap reaches the left corner: ${third.min()}")
        assertTrue(fourth.max() > 150.0, "the fourth lap reaches the right corner: ${fourth.max()}")
    }

    @Test
    fun `the sailor marks the line, sets the wind and the mark, and starts`() {
        val actions = result.actions().map { it.event }
        assertTrue(RaceEvent.MarkPinEnd in actions)
        assertTrue(RaceEvent.MarkBoatEnd in actions)
        assertTrue(RaceEvent.SetWindDirection(35) in actions)
        assertTrue(RaceEvent.SetTackAngle(45) in actions)
        assertTrue(RaceEvent.SetDownwindAngle(140) in actions)
        assertTrue(RaceEvent.SetWindFromStarboardTack in actions)
        assertTrue(RaceEvent.SetWindFromPortTack in actions)
        assertTrue(RaceEvent.SetWindwardMarkFromLine(20, 400.0) in actions)
        assertEquals(RaceEvent.StopTimer, actions.last())
        // The mark is entered before the first lap beats to it.
        val markSet = result.actions().first { it.event is RaceEvent.SetWindwardMarkFromLine }.timeMillis
        assertTrue(markSet <= result.milestone(PracticeScenario.beatLeg(1)))
    }

    @Test
    fun `the boat crosses the line at the gun and beats to the mark`() {
        val gun = PracticeScenario.gunMillis(result, config)
        val crossing = assertNotNull(result.firstCourseSideCrossing(result.milestone(PracticeScenario.COUNTDOWN_SYNC)))
        // The sailor times the run to the line on the wind as it is, so a shift in the last minute is
        // seconds early or late at the gun - as it is on the water.
        assertTrue(abs(crossing - gun) <= 15_000, "crossed ${(crossing - gun) / 1000.0} s from the gun")
        assertEquals(result.milestone(PracticeScenario.START), crossing)
        val finish = result.truth.first { it.timeMillis == result.milestone(PracticeScenario.RACE_BEAT) }
        assertTrue(Geo.distanceMeters(finish.boat.position, result.course.windwardMark) <= 15.0)
    }

    @Test
    fun `the course, the boat and the laps follow the configuration`() {
        val two = config.copy(laps = 2, lineLengthMeters = 80.0, beatLengthMeters = 300.0, corridors = listOf(PracticeScenario.LapCorridor(60.0)))
        val course = PracticeScenario.course(two)
        assertEquals(80.0, Geo.distanceMeters(course.pinEnd, course.boatEnd), 0.01)
        assertEquals(300.0, Geo.distanceMeters(course.lineCenter, course.windwardMark), 0.01)
        assertEquals(PracticeScenario.LapCorridor(60.0), two.corridorFor(1))
        assertEquals(PracticeScenario.LapCorridor(60.0), two.corridorFor(2))
        assertEquals(PracticeScenario.LapCorridor(100.0, -100.0), config.corridorFor(3))
        assertEquals(PracticeScenario.LapCorridor(80.0), config.corridorFor(6))

        val short = PracticeScenario.build(two)
        assertTrue(short.milestones.containsKey(PracticeScenario.runLeg(2)))
        assertTrue(!short.milestones.containsKey(PracticeScenario.beatLeg(3)))
        assertEquals(2 * 2 + 8, PracticeScenario.legs(two).size)
        // The boat starts on a reach at the pin end, at the speed the breeze gives it.
        val initial = PracticeScenario.initialState(two)
        assertEquals(60.0, Geo.distanceMeters(initial.position, course.pinEnd), 0.01)
        assertEquals(110.0, initial.headingDegrees, 1e-9)
        assertEquals(3.0, initial.speedMps, 1e-9)
    }

    @Test
    fun `a session needs a lap and a corridor`() {
        assertFailsWith<IllegalArgumentException> { config.copy(laps = 0) }
        assertFailsWith<IllegalArgumentException> { config.copy(corridors = emptyList()) }
    }

    @Test
    fun `noise reaches the fixes`() {
        val noisy = PracticeScenario.build(config.copy(laps = 1, noise = NoiseModel.typicalGps(seed = 7)))
        assertTrue(noisy.fixes().any { assertNotNull(it.accuracyMeters) > 3.0 })
    }
}
