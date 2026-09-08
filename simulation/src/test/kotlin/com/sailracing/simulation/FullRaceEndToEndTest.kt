package com.sailracing.simulation

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.geo.Geo
import com.sailracing.domain.race.ApproachSpeed
import com.sailracing.domain.race.HeadingSource
import com.sailracing.domain.race.RaceEffect
import com.sailracing.domain.race.RaceEngine
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.race.RaceSettings
import com.sailracing.domain.race.RaceSnapshot
import com.sailracing.domain.race.RaceState
import com.sailracing.domain.course.CourseModel
import com.sailracing.domain.course.RaceLineFinder
import com.sailracing.domain.course.Side
import com.sailracing.domain.course.TrackGrid
import com.sailracing.domain.startline.LineSide
import com.sailracing.domain.strategy.FavouredSide
import com.sailracing.domain.strategy.TackAdvice
import com.sailracing.domain.strategy.UpwindStrategy
import com.sailracing.domain.timer.Cue
import com.sailracing.domain.timer.RacePhase
import com.sailracing.domain.wind.PointOfSail
import com.sailracing.domain.wind.Tack
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives the real race engine through the complete scripted race and checks every feature of the app
 * against the simulator's ground truth: line marking, wind setting from either tack, the countdown with
 * its sync and cues, distance and time to the line, time to kill, the line side, wind statistics,
 * speed statistics, tack/point-of-sail detection and the shift indicator.
 */
class FullRaceEndToEndTest {

    private class PlayedCue(val timeMillis: Long, val cue: Cue)

    /** Replays [result] through an engine, calling [observe] with the snapshot after every simulated second. */
    private fun replay(result: SimulationResult, engine: RaceEngine, observe: (RaceSnapshot) -> Unit = {}): List<PlayedCue> {
        val cues = mutableListOf<PlayedCue>()
        var next = 0
        var now = 0L
        while (now <= result.durationMillis) {
            while (next < result.timeline.size && result.timeline[next].timeMillis <= now) {
                engine.dispatch(result.timeline[next].event)
                next++
            }
            engine.dispatch(RaceEvent.Tick(now)).forEach { cues += PlayedCue(now, (it as RaceEffect.PlayCue).cue) }
            if (now % 1000 == 0L) observe(engine.snapshot(now))
            now += TICK_MILLIS
        }
        return cues
    }

    @Test
    fun `a complete race exercises every feature correctly`() {
        val result = StandardRaceScenario.build()
        val engine = RaceEngine(RaceState(settings = RaceSettings(approachSpeed = ApproachSpeed.AverageUpwindVmg(fallbackMps = 1.5))))
        val gun = StandardRaceScenario.gunMillis(result)
        val snapshots = sortedMapOf<Long, RaceSnapshot>()

        val cues = replay(result, engine) { snapshots[it.nowMillis] = it }
        fun at(millis: Long): RaceSnapshot = assertNotNull(snapshots[millis], "no snapshot at $millis")

        // --- Start line ---------------------------------------------------------------------------------
        val afterPin = at(result.milestone(StandardRaceScenario.MARK_PIN))
        assertNotNull(afterPin.startLine.pinEnd)
        assertNull(afterPin.startLine.boatEnd)
        assertTrue(Geo.distanceMeters(afterPin.startLine.pinEnd!!, result.course.pinEnd) <= 4.0)

        val afterBoat = at(result.milestone(StandardRaceScenario.MARK_BOAT))
        assertTrue(afterBoat.startLine.isComplete)
        assertEquals(100.0, assertNotNull(afterBoat.startLineLengthMeters), 8.0)

        // --- Wind from the boat's heading on each tack -----------------------------------------------------
        val starboardMillis = result.milestone(StandardRaceScenario.WIND_STARBOARD)
        val afterStarboard = at(starboardMillis)
        assertEquals(45, afterStarboard.windSettings.tackAngleDegrees)
        assertEquals(140, afterStarboard.windSettings.downwindAngleDegrees)
        assertWithin(1.5, result.windAt(starboardMillis), afterStarboard.windSettings.directionDegrees.toDouble(), "wind from starboard tack")
        val beforeStarboard = at(starboardMillis - 1_000)
        assertWithin(0.0, Angles.normalize(20 + 15).toDouble(), beforeStarboard.windSettings.directionDegrees.toDouble(), "deliberately wrong wind")

        val portMillis = result.milestone(StandardRaceScenario.WIND_PORT)
        assertWithin(1.5, result.windAt(portMillis), at(portMillis).windSettings.directionDegrees.toDouble(), "wind from port tack")
        assertEquals(PointOfSail.UPWIND, assertNotNull(at(portMillis - 5_000).sailing).pointOfSail)
        assertEquals(com.sailracing.domain.wind.Tack.PORT, assertNotNull(at(portMillis - 5_000).sailing).tack)
        assertEquals(com.sailracing.domain.wind.Tack.STARBOARD, assertNotNull(at(starboardMillis - 5_000).sailing).tack)

        // --- Countdown: started late, then synced ---------------------------------------------------------
        val syncMillis = result.milestone(StandardRaceScenario.COUNTDOWN_SYNC)
        val startPress = syncMillis - (StandardRaceScenario.LATE_PRESS_SECONDS * 1000).toLong()
        assertEquals(RacePhase.SETUP, at(startPress - 1_000).phase)
        assertEquals(RacePhase.COUNTDOWN, at(startPress).phase)
        assertEquals(300_000L, at(startPress).remainingMillis)
        assertEquals(298_000L, at(syncMillis - 1_000).remainingMillis)
        assertEquals(300_000L, at(syncMillis).remainingMillis)
        assertEquals(gun, syncMillis + assertNotNull(at(syncMillis).remainingMillis))

        // --- Cues -----------------------------------------------------------------------------------------
        val expectedCues = listOf(300L to Cue.MINUTE) +
            listOf(Cue.MINUTE, Cue.MINUTE, Cue.MINUTE).mapIndexed { i, cue -> (240L - 60 * i) to cue } +
            listOf(110L, 100L, 90L, 80L, 70L).map { it to Cue.TEN_SECONDS } +
            listOf(60L to Cue.MINUTE) +
            listOf(50L, 40L, 30L, 20L, 10L).map { it to Cue.TEN_SECONDS } +
            (9L downTo 1L).map { it to Cue.SECOND } +
            listOf(0L to Cue.START)
        val cuesAfterSync = cues.filter { it.timeMillis >= syncMillis }
        assertEquals(expectedCues.map { it.second }, cuesAfterSync.map { it.cue })
        cuesAfterSync.zip(expectedCues).forEach { (played, expected) ->
            val idealTime = gun - expected.first * 1000
            assertTrue(played.timeMillis in idealTime..(idealTime + TICK_MILLIS), "cue ${expected.second} at ${played.timeMillis} vs $idealTime")
        }
        // The confirmation cue at the moment of pressing start (the sync's own cue is the first one above).
        assertEquals(listOf(Cue.MINUTE), cues.filter { it.timeMillis in startPress until syncMillis }.map { it.cue })

        // --- Approaching the line ------------------------------------------------------------------------------
        val crossing = assertNotNull(result.firstCourseSideCrossing(syncMillis))
        assertTrue(abs(crossing - gun) <= 5_000, "crossed ${(crossing - gun) / 1000.0} s from the gun")

        val earlyLook = at(gun - 90_000)
        assertEquals(LineSide.PRE_START, assertNotNull(earlyLook.line).side)
        assertTrue(earlyLook.approachSpeedIsMeasured, "upwind VMG should be measured after the wind legs")
        assertWithin(0.25, result.polar.speedMps(45.0) * cos(Angles.toRadians(45.0)), earlyLook.approachSpeedMps, "measured VMG")
        assertTrue(assertNotNull(earlyLook.timeToKillSeconds) > 0, "early: positive time to kill")
        assertTrue(assertNotNull(earlyLook.line).distanceMeters > 50.0)

        val atCrossing = at(crossing)
        assertEquals(LineSide.COURSE, assertNotNull(atCrossing.line).side)
        assertTrue(assertNotNull(atCrossing.line).distanceMeters < 5.0)
        assertTrue(abs(assertNotNull(atCrossing.timeToKillSeconds)) <= 6.0, "time to kill at the line ${atCrossing.timeToKillSeconds}")
        assertTrue(assertNotNull(atCrossing.line).isBetweenEnds, "crossed between the ends at ${atCrossing.line?.alongFraction}")
        assertEquals(RacePhase.RACING, at(gun + 1_000).phase)
        assertEquals(RacePhase.COUNTDOWN, at(gun - 1_000).phase)

        // Time to kill decreases towards zero as the boat approaches on its final run-in.
        val runIn = (crossing - 20_000..crossing step 5_000).map { assertNotNull(at(it).timeToKillSeconds) }
        assertTrue(runIn.zipWithNext().all { (a, b) -> b <= a + 0.5 }, "time to kill should fall: $runIn")

        // --- The beat: wind statistics and shift -------------------------------------------------------------
        val beatEnd = result.milestone(StandardRaceScenario.BEAT)
        val beat = at(beatEnd)
        assertTrue(Geo.distanceMeters(assertNotNull(beat.position), result.course.windwardMark) <= 15.0)
        assertTrue(beat.histogram.totalSamples >= 200, "histogram samples ${beat.histogram.totalSamples}")
        assertWithin(4.0, result.wind.meanDirectionDegrees, assertNotNull(beat.histogram.meanDirection()), "histogram mean")
        val deviation = assertNotNull(beat.histogram.standardDeviation())
        assertTrue(deviation in 2.0..12.0, "histogram deviation $deviation")
        assertTrue(beat.history.samples.size >= 200)

        var comparedShifts = 0
        for (t in (gun + 30_000)..beatEnd step 1_000) {
            val s = at(t)
            val heading = s.headingDegrees ?: continue
            val shift = s.shiftDegrees ?: continue
            // Only when the boat really is close-hauled to the true wind: mid-tack, or when pointing at the
            // mark from the layline, the heading says nothing about the wind.
            val trueWind = result.windAt(t)
            val trueTwa = abs(Angles.signedDifference(heading, trueWind))
            if (abs(trueTwa - result.polar.upwindAngleDegrees) > 2.0) continue
            // The shift is measured from the reference wind: the histogram's centre once it is measured.
            val trueShift = Angles.signedDifference(s.windReference.directionDegrees, trueWind)
            assertWithin(3.0, trueShift, shift, "shift at $t")
            assertEquals(HeadingSource.COURSE_OVER_GROUND, s.headingSource)
            assertTrue(s.fixIsFresh)
            comparedShifts++
        }
        assertTrue(comparedShifts > 100, "compared $comparedShifts shifts")

        // --- Tack advice: hold the lifted tack, tack when headed, judged against the histogram centre -----------
        var agreed = 0
        var judged = 0
        for (t in (gun + 30_000)..beatEnd step 1_000) {
            val s = at(t)
            val plan = s.plan
            if (!plan.referenceIsMeasured) continue
            val heading = s.headingDegrees ?: continue
            val trueWind = result.windAt(t)
            if (abs(abs(Angles.signedDifference(heading, trueWind)) - result.polar.upwindAngleDegrees) > 2.0) continue
            val trueShift = Angles.signedDifference(plan.referenceWindDegrees, trueWind)
            if (abs(trueShift) < UpwindStrategy.SHIFT_DEAD_BAND_DEGREES + 2.0) continue
            val liftedTack = if (trueShift > 0) Tack.STARBOARD else Tack.PORT
            val expected = if (liftedTack == assertNotNull(s.sailing).tack) TackAdvice.HOLD else TackAdvice.TACK
            judged++
            if (plan.tackAdvice == expected) agreed++
        }
        assertTrue(judged > 60, "judged $judged tack advices")
        assertTrue(agreed >= judged * 0.9, "tack advice agreed $agreed of $judged times")

        // --- The map: the beat covers both sides of the course and the veered right side is favoured ---------------
        val course = assertNotNull(beat.course)
        val grid = course.grid
        assertTrue(grid.side(Side.LEFT).upwindSamples >= UpwindStrategy.MIN_SIDE_SAMPLES, "left samples ${grid.side(Side.LEFT)}")
        assertTrue(grid.side(Side.RIGHT).upwindSamples >= UpwindStrategy.MIN_SIDE_SAMPLES, "right samples ${grid.side(Side.RIGHT)}")
        assertEquals(beat.trackPointCount, grid.totalVisits, "the area follows the track, so every point is in a cell")
        val frame = course.frame
        assertEquals(0.0, Geo.distanceMeters(frame.origin, result.course.lineCenter), 5.0)
        assertWithin(4.0, result.wind.meanDirectionDegrees, frame.windDirectionDegrees, "the map is oriented on the measured mean wind")
        assertTrue(course.trackPositions.size in 2..CourseModel.MAX_TRACK_POINTS + 1)
        val markInFrame = frame.toCourse(result.course.windwardMark)
        assertEquals(0.0, markInFrame.acrossMeters, 40.0)
        assertEquals(500.0, markInFrame.upwindMeters, 40.0)
        // The mark the sailor entered from the line is the real one, and it is the axis of the sides.
        assertTrue(course.windwardMarkIsSet)
        assertTrue(Geo.distanceMeters(assertNotNull(course.inputs.windwardMark), result.course.windwardMark) <= 10.0)
        assertEquals(course.windwardMark, grid.axisTarget)
        // The area is the track's bounding box in 10 m multiples, with the mark and the line inside it.
        assertEquals(0.0, course.spec.cellSizeMeters % 10.0, 1e-9)
        assertTrue(course.spec.columns <= 13 && course.spec.rows <= 13, "cells ${course.spec}")
        assertNotNull(course.spec.cellOf(course.windwardMark))
        assertNotNull(course.spec.cellOf(assertNotNull(course.pinEnd)))
        assertTrue(course.spec.heightMeters >= 500.0 && course.spec.heightMeters < 700.0, "area ${course.spec}")
        // The wind field shows the shear: measured cells on the right are veered relative to those on the left.
        val measured = course.windField.cells.filter { it.measured }
        assertTrue(measured.size >= 6, "measured cells ${measured.size}")
        val rightShift = measured.filter { course.spec.center(it.cell).acrossMeters > 40.0 }.map { it.shiftDegrees }.average()
        val leftShift = measured.filter { course.spec.center(it.cell).acrossMeters < -40.0 }.map { it.shiftDegrees }.average()
        assertTrue(rightShift > leftShift + 2.0, "right $rightShift vs left $leftShift")
        assertTrue(course.windField.cells.none { it.measured } || course.windField.cells.all { abs(it.shiftDegrees) < 30.0 })
        // Halfway up the beat the app draws a line from the boat to the mark that stays inside the area.
        val midBeat = at(gun + (beatEnd - gun) / 2 / 1000 * 1000)
        val midCourse = assertNotNull(midBeat.course)
        val raceLine = midCourse.raceLine
        assertTrue(raceLine.points.size >= 2, "race line $raceLine")
        assertEquals(midCourse.boat, raceLine.points.first())
        assertEquals(midCourse.windwardMark, raceLine.points.last())
        assertTrue(raceLine.points.all { it.acrossMeters in midCourse.spec.leftMeters..midCourse.spec.rightMeters && it.upwindMeters in midCourse.spec.bottomMeters..midCourse.spec.topMeters })
        // What is left of the beat: never quicker than sailing straight at the mark, and not a wild detour either.
        val boat = assertNotNull(midCourse.boat)
        val toGo = hypot(midCourse.windwardMark.acrossMeters - boat.acrossMeters, midCourse.windwardMark.upwindMeters - boat.upwindMeters)
        assertTrue(raceLine.seconds > toGo / RaceLineFinder.BOAT_SPEED_MPS, "race line $raceLine over $toGo m")
        assertTrue(raceLine.seconds < 3 * toGo / RaceLineFinder.BOAT_SPEED_MPS, "race line $raceLine over $toGo m")
        val sides = beat.plan.sides
        val windDifference = assertNotNull(sides.windDifferenceDegrees)
        val expectedDifference = result.wind.shearDegreesPerMeter *
            (averageAcross(result, grid, Side.RIGHT) - averageAcross(result, grid, Side.LEFT))
        assertEquals(expectedDifference, windDifference, 2.5)
        assertEquals(FavouredSide.RIGHT, beat.plan.favouredSide, "plan ${beat.plan}")

        // --- Speeds --------------------------------------------------------------------------------------------
        val runEnd = result.milestone(StandardRaceScenario.RUN)
        val run = at(runEnd)
        assertTrue(Geo.distanceMeters(assertNotNull(run.position), result.course.leewardMark) <= 15.0)
        assertWithin(0.2, result.polar.speedMps(45.0), assertNotNull(run.speedStats.upwind.mean), "average upwind speed")
        assertWithin(0.4, result.polar.speedMps(140.0), assertNotNull(run.speedStats.downwind.mean), "average downwind speed")
        assertTrue(assertNotNull(run.speedStats.downwindVmg.mean) > 1.5)
        assertEquals(PointOfSail.DOWNWIND, assertNotNull(at(runEnd - 30_000).sailing).pointOfSail)
        assertTrue(assertNotNull(at(runEnd - 30_000).vmgMps) > 1.5)

        // --- Finish --------------------------------------------------------------------------------------------
        val finish = at(result.milestone(StandardRaceScenario.FINISH))
        assertEquals(RacePhase.SETUP, finish.phase)
        assertNull(finish.remainingMillis)
        assertEquals(LineSide.COURSE, assertNotNull(finish.line).side)
        assertEquals(RacePhase.RACING, at(result.milestone(StandardRaceScenario.FINISH) - 1_000).phase)
    }

    /** The mean across-axis position of the close-hauled samples on one side, from the grid's cell centres. */
    private fun averageAcross(result: SimulationResult, grid: TrackGrid, side: Side): Double {
        val cells = grid.visited.filter { (cell, stats) -> grid.spec.side(cell, grid.axisTarget) == side && stats.upwindSamples > 0 }
        val samples = cells.sumOf { it.second.upwindSamples }
        return cells.sumOf { (cell, stats) -> grid.spec.center(cell).acrossMeters * stats.upwindSamples } / samples
    }

    @Test
    fun `an even wind favours neither side`() {
        val config = StandardRaceScenario.Config(wind = StandardRaceScenario.Config().wind.copy(shearDegreesPerMeter = 0.0))
        val result = StandardRaceScenario.build(config)
        val engine = RaceEngine()
        var beat: RaceSnapshot? = null
        replay(result, engine) { if (it.nowMillis == result.milestone(StandardRaceScenario.BEAT)) beat = it }
        val plan = assertNotNull(beat).plan
        assertNotNull(plan.sides.windDifferenceDegrees)
        assertTrue(abs(assertNotNull(plan.sideScoreDegrees)) < UpwindStrategy.SIDE_DEAD_BAND_DEGREES, "plan $plan")
        assertEquals(FavouredSide.EVEN, plan.favouredSide)
    }

    @Test
    fun `noisy gps still yields a usable picture`() {
        val result = StandardRaceScenario.build(StandardRaceScenario.Config(noise = NoiseModel.typicalGps(seed = 42)))
        val engine = RaceEngine()
        val gun = StandardRaceScenario.gunMillis(result)
        var atGun: RaceSnapshot? = null
        var beat: RaceSnapshot? = null
        replay(result, engine) { snapshot ->
            if (snapshot.nowMillis == gun) atGun = snapshot
            if (snapshot.nowMillis == result.milestone(StandardRaceScenario.BEAT)) beat = snapshot
        }
        assertTrue(assertNotNull(assertNotNull(atGun).line).distanceMeters < 20.0)
        assertWithin(5.0, result.wind.meanDirectionDegrees, assertNotNull(assertNotNull(beat).histogram.meanDirection()), "noisy histogram mean")
        assertEquals(100.0, assertNotNull(assertNotNull(beat).startLineLengthMeters), 12.0)
    }

    private fun assertWithin(tolerance: Double, expectedDegrees: Double, actualDegrees: Double, what: String) {
        val difference = abs(Angles.signedDifference(expectedDegrees, actualDegrees))
        assertTrue(difference <= tolerance, "$what: expected $expectedDegrees but was $actualDegrees (off by $difference)")
    }

    private companion object {
        const val TICK_MILLIS = 250L
    }
}
