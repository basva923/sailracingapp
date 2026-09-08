package com.sailracing.domain.race

import com.sailracing.domain.geo.Geo
import com.sailracing.domain.race.TestFixtures.east
import com.sailracing.domain.race.TestFixtures.fix
import com.sailracing.domain.race.TestFixtures.origin
import com.sailracing.domain.startline.LineSide
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.stats.RunningAverage
import com.sailracing.domain.stats.SpeedStats
import com.sailracing.domain.strategy.FavouredSide
import com.sailracing.domain.strategy.TackAdvice
import com.sailracing.domain.timer.RacePhase
import com.sailracing.domain.timer.TimerState
import com.sailracing.domain.wind.PointOfSail
import com.sailracing.domain.wind.Tack
import com.sailracing.domain.wind.WindReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RaceCalculatorTest {

    private val line = StartLine(origin, east(100.0))
    private val belowCenter = Geo.destination(Geo.destination(origin, 90.0, 50.0), 180.0, 60.0)

    @Test
    fun `empty state yields an empty snapshot`() {
        val snapshot = RaceCalculator.snapshot(RaceState(), nowMillis = 5_000)
        assertEquals(5_000, snapshot.nowMillis)
        assertEquals(RacePhase.SETUP, snapshot.phase)
        assertNull(snapshot.remainingMillis)
        assertNull(snapshot.remainingWholeSeconds)
        assertNull(snapshot.startLineLengthMeters)
        assertNull(snapshot.line)
        assertNull(snapshot.timeToLineSeconds)
        assertNull(snapshot.timeToKillSeconds)
        assertNull(snapshot.position)
        assertNull(snapshot.fixAgeMillis)
        assertFalse(snapshot.fixIsFresh)
        assertNull(snapshot.accuracyMeters)
        assertNull(snapshot.speedMps)
        assertNull(snapshot.headingDegrees)
        assertNull(snapshot.headingSource)
        assertNull(snapshot.sailing)
        assertNull(snapshot.estimatedWindDegrees)
        assertNull(snapshot.shiftDegrees)
        assertNull(snapshot.targetHeadingDegrees)
        assertNull(snapshot.headingErrorDegrees)
        assertNull(snapshot.vmgMps)
        assertEquals(1.5, snapshot.approachSpeedMps)
        assertFalse(snapshot.approachSpeedIsMeasured)
        assertEquals(315.0, snapshot.targetHeadings.starboardUpwind)
        assertEquals(WindReference(0.0, isMeasured = false), snapshot.windReference)
        assertNull(snapshot.course)
        assertEquals(0, snapshot.trackPointCount)
        assertEquals(TackAdvice.UNKNOWN, snapshot.plan.tackAdvice)
        assertEquals(FavouredSide.UNKNOWN, snapshot.plan.favouredSide)
    }

    @Test
    fun `full snapshot during the countdown`() {
        val engine = RaceEngine(RaceState(startLine = line, settings = RaceSettings(approachSpeed = ApproachSpeed.Manual(2.0))))
        engine.dispatch(RaceEvent.StartCountdown(1, nowMillis = 0))
        engine.dispatch(RaceEvent.FixReceived(fix(10_000, point = belowCenter, speedMps = 3.0, courseDegrees = 320.0)))
        val snapshot = engine.snapshot(nowMillis = 11_000)

        assertEquals(RacePhase.COUNTDOWN, snapshot.phase)
        assertEquals(49_000, snapshot.remainingMillis)
        assertEquals(49, snapshot.remainingWholeSeconds)
        assertEquals(100.0, assertNotNull(snapshot.startLineLengthMeters), 0.01)
        val solution = assertNotNull(snapshot.line)
        assertEquals(60.0, solution.distanceMeters, 0.1)
        assertEquals(LineSide.PRE_START, solution.side)
        assertEquals(2.0, snapshot.approachSpeedMps)
        assertFalse(snapshot.approachSpeedIsMeasured)
        assertEquals(30.0, assertNotNull(snapshot.timeToLineSeconds), 0.05)
        assertEquals(19.0, assertNotNull(snapshot.timeToKillSeconds), 0.05)

        assertEquals(belowCenter, snapshot.position)
        assertEquals(1_000, snapshot.fixAgeMillis)
        assertTrue(snapshot.fixIsFresh)
        assertEquals(4.0, snapshot.accuracyMeters)
        assertEquals(3.0, snapshot.speedMps)
        assertEquals(320.0, snapshot.headingDegrees)
        assertEquals(HeadingSource.COURSE_OVER_GROUND, snapshot.headingSource)

        val sailing = assertNotNull(snapshot.sailing)
        assertEquals(Tack.STARBOARD, sailing.tack)
        assertEquals(PointOfSail.UPWIND, sailing.pointOfSail)
        assertEquals(5.0, assertNotNull(snapshot.estimatedWindDegrees))
        assertEquals(5.0, assertNotNull(snapshot.shiftDegrees))
        assertEquals(315.0, snapshot.targetHeadingDegrees)
        assertEquals(5.0, assertNotNull(snapshot.headingErrorDegrees))
        assertEquals(3.0 * Math.cos(Math.toRadians(40.0)), assertNotNull(snapshot.vmgMps), 1e-9)
        assertEquals(1, snapshot.speedStats.upwind.count)
        assertEquals(1, snapshot.histogram.totalSamples)
        assertEquals(1, snapshot.history.samples.size)

        // The map is centred on the middle of the line, wind up, and its area follows the line and the boat.
        val course = assertNotNull(snapshot.course)
        assertEquals(0.0, course.frame.windDirectionDegrees)
        assertEquals(0.0, Geo.distanceMeters(course.frame.origin, Geo.destination(origin, 90.0, 50.0)), 0.01)
        val boat = assertNotNull(course.trackPositions.singleOrNull())
        assertEquals(0.0, boat.acrossMeters, 0.5)
        assertEquals(-60.0, boat.upwindMeters, 0.5)
        assertEquals(boat, course.boat)
        assertEquals(1, snapshot.trackPointCount)
        assertEquals(10.0, course.spec.cellSizeMeters)
        assertEquals(1, course.grid.totalVisits)
        assertEquals(1, course.grid.stats(assertNotNull(course.spec.cellOf(boat))).upwindSamples)
        assertFalse(course.windwardMarkIsSet)
        assertEquals(course.spec.topCenter, course.windwardMark)
        assertEquals(boat, course.raceLine.points.first())
        assertEquals(course.windwardMark, course.raceLine.points.last())
        assertEquals(TackAdvice.HOLD, snapshot.plan.tackAdvice)
        assertFalse(snapshot.plan.referenceIsMeasured)
    }

    @Test
    fun `the map is centred on what is known`() {
        assertNull(RaceCalculator.courseOrigin(StartLine(), fallback = null))
        assertEquals(origin, RaceCalculator.courseOrigin(StartLine(), fallback = origin))
        assertEquals(origin, RaceCalculator.courseOrigin(StartLine(pinEnd = origin), fallback = east(5.0)))
        assertEquals(east(10.0), RaceCalculator.courseOrigin(StartLine(boatEnd = east(10.0)), fallback = east(5.0)))

        // Without a line the frame follows the boat, and later the track's first point.
        val state = RaceState()
            .let { RaceReducer.reduce(it, RaceEvent.FixReceived(fix(1_000, point = east(30.0)))).state }
            .let { RaceReducer.reduce(it, RaceEvent.FixReceived(fix(2_000, point = east(60.0)))).state }
        val snapshot = RaceCalculator.snapshot(state, nowMillis = 2_000)
        val course = assertNotNull(snapshot.course)
        assertEquals(east(30.0), course.frame.origin)
        assertEquals(2, course.trackPositions.size)
        assertEquals(30.0, course.trackPositions.last().acrossMeters, 0.5)
        assertEquals(2, snapshot.trackPointCount)
    }

    @Test
    fun `the course model is reused across ticks and rebuilt when its inputs change`() {
        val state = RaceReducer.reduce(RaceState(startLine = line), RaceEvent.FixReceived(fix(1_000, point = belowCenter))).state
        val first = RaceCalculator.snapshot(state, nowMillis = 1_000)
        val tick = RaceCalculator.snapshot(state, nowMillis = 1_250, previous = first)
        assertSame(assertNotNull(first.course), tick.course)

        val moved = RaceReducer.reduce(state, RaceEvent.FixReceived(fix(2_000, point = belowCenter))).state
        val afterFix = RaceCalculator.snapshot(moved, nowMillis = 2_000, previous = tick)
        assertNotSame(first.course, afterFix.course)
        assertEquals(2, assertNotNull(afterFix.course).grid.totalVisits)

        val marked = RaceReducer.reduce(moved, RaceEvent.SetWindwardMark(TestFixtures.north(400.0))).state
        val afterMark = RaceCalculator.snapshot(marked, nowMillis = 2_000, previous = afterFix)
        assertNotSame(afterFix.course, afterMark.course)
        assertTrue(assertNotNull(afterMark.course).windwardMarkIsSet)
        assertEquals(400.0, afterMark.course!!.windwardMark.upwindMeters, 0.5)
    }

    @Test
    fun `once measured, the mean wind orients everything instead of the set wind`() {
        var state = RaceReducer.reduce(RaceState(startLine = line), RaceEvent.SetWindDirection(20)).state
        for (t in 1L..30L) state = RaceReducer.reduce(state, RaceEvent.FixReceived(fix(t * 1_000, point = belowCenter, courseDegrees = 340.0))).state
        val snapshot = RaceCalculator.snapshot(state, nowMillis = 30_000)
        assertEquals(25.0, snapshot.windReference.directionDegrees, 1e-9)
        assertTrue(snapshot.windReference.isMeasured)
        assertEquals(20, snapshot.windSettings.directionDegrees)
        assertEquals(340.0, snapshot.targetHeadings.starboardUpwind, 1e-9)
        assertEquals(340.0, assertNotNull(snapshot.targetHeadingDegrees), 1e-9)
        assertEquals(0.0, assertNotNull(snapshot.shiftDegrees), 1e-9)
        assertEquals(25.0, assertNotNull(snapshot.course).frame.windDirectionDegrees, 1e-9)
        assertEquals(TackAdvice.EITHER, snapshot.plan.tackAdvice)
    }

    @Test
    fun `stale fix and racing phase`() {
        val state = RaceState(timer = TimerState.Running(10_000), navigation = NavigationState(lastFix = fix(1_000)))
        val snapshot = RaceCalculator.snapshot(state, nowMillis = 12_000)
        assertEquals(RacePhase.RACING, snapshot.phase)
        assertEquals(-2_000, snapshot.remainingMillis)
        assertEquals(-2, snapshot.remainingWholeSeconds)
        assertEquals(11_000, snapshot.fixAgeMillis)
        assertFalse(snapshot.fixIsFresh)
        assertNull(snapshot.line)
        assertNull(snapshot.timeToKillSeconds)
    }

    @Test
    fun `measured approach speed needs enough samples`() {
        val fallback = RaceState(settings = RaceSettings(approachSpeed = ApproachSpeed.AverageUpwindVmg(fallbackMps = 1.2)))
        assertEquals(1.2 to false, RaceCalculator.approachSpeed(fallback))

        val few = fallback.copy(speedStats = SpeedStats(upwindVmg = RunningAverage(count = 5, sum = 10.0, max = 2.0)))
        assertEquals(1.2 to false, RaceCalculator.approachSpeed(few))

        val enough = fallback.copy(speedStats = SpeedStats(upwindVmg = RunningAverage(count = 30, sum = 60.0, max = 2.0)))
        assertEquals(2.0 to true, RaceCalculator.approachSpeed(enough))

        val zero = fallback.copy(speedStats = SpeedStats(upwindVmg = RunningAverage(count = 30, sum = 0.0, max = 0.0)))
        assertEquals(1.2 to false, RaceCalculator.approachSpeed(zero))

        val manual = RaceState(settings = RaceSettings(approachSpeed = ApproachSpeed.Manual(2.5)))
        assertEquals(2.5 to false, RaceCalculator.approachSpeed(manual))
    }
}
