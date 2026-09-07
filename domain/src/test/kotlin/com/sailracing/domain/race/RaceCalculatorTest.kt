package com.sailracing.domain.race

import com.sailracing.domain.geo.Geo
import com.sailracing.domain.race.TestFixtures.east
import com.sailracing.domain.race.TestFixtures.fix
import com.sailracing.domain.race.TestFixtures.origin
import com.sailracing.domain.startline.LineSide
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.stats.RunningAverage
import com.sailracing.domain.stats.SpeedStats
import com.sailracing.domain.timer.RacePhase
import com.sailracing.domain.timer.TimerState
import com.sailracing.domain.wind.PointOfSail
import com.sailracing.domain.wind.Tack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
