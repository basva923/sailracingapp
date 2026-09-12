package com.sailracing.app.ui.wind

import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.race.HeadingSource
import com.sailracing.domain.race.RaceEngine
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.race.RaceSettings
import com.sailracing.domain.race.RaceState
import com.sailracing.domain.strategy.TackAdvice
import com.sailracing.domain.wind.PointOfSail
import com.sailracing.domain.wind.SailingState
import com.sailracing.domain.wind.Tack
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindUiStateTest {

    private fun engine(vararg headings: Double, wind: Int = 20): RaceEngine {
        val engine = RaceEngine()
        engine.dispatch(RaceEvent.SetWindDirection(wind))
        headings.forEachIndexed { i, heading ->
            engine.dispatch(RaceEvent.FixReceived(PositionFix(GeoPoint(51.14, 5.83), (i + 1) * 1_000L, 3.0, heading, 3.0)))
        }
        return engine
    }

    /** A sailor who took the heading from the compass, so a reading counts without a fix. */
    private fun compassEngine(): RaceEngine =
        RaceEngine(RaceState(settings = RaceSettings(headingSource = HeadingSource.COMPASS)))

    @Test
    fun emptyState() {
        val state = WindUiState.from(RaceEngine().snapshot(0))
        assertEquals("000°", state.configuredWind)
        assertEquals("---", state.meanWind)
        assertFalse(state.meanIsMeasured)
        assertNull(state.meanWindDegrees)
        assertEquals("---", state.estimatedWind)
        assertEquals("000°", state.windNow)
        assertEquals("Set wind · no heading", state.windNowGlance)
        assertFalse(state.windNowIsLive)
        assertEquals("---", state.shift)
        assertNull(state.shiftDegrees)
        assertEquals("---", state.heading)
        assertEquals("", state.headingSource)
        assertEquals("No heading", state.headingGlance)
        assertEquals("Tack angle not measured yet: sail close-hauled on both tacks", state.tackAngleMeasured)
        assertEquals("---", state.speed)
        assertEquals("", state.speedDelta)
        assertNull(state.speedDeltaMps)
        assertEquals("---", state.vmg)
        assertEquals("Shift vs set wind · + lift", state.shiftTitle)
        assertEquals(81, state.histogramFromTheBoat.size)
        assertEquals("No heading", state.adviceGlance)
        assertEquals("", state.tackLabel)
        assertEquals("—", state.tackShort)
        assertEquals("Tack", state.pointOfSailLabel)
        assertNull(state.tack)
        assertEquals(TackAdvice.UNKNOWN, state.advice)
        assertEquals("—", state.adviceTitle)
        assertEquals("Waiting for a heading", state.adviceDetail)
        assertEquals(81, state.histogram.size)
        assertEquals("Wind direction frequency, ±40° around the set wind", state.histogramCaption)
        assertNull(state.setOffset)
        assertTrue(state.shifts.isEmpty())
        assertEquals("No upwind samples yet", state.statistics)
        assertEquals("---", state.averageUpwind)
        assertEquals("", state.raceTime)
        assertFalse(state.canSetFromHeading)
        assertEquals(315.0, state.targets.starboardUpwind)
    }

    @Test
    fun sailingStateAgainstTheSetWind() {
        val state = WindUiState.from(engine(340.0, 340.0).snapshot(2_000))
        assertEquals("020°", state.configuredWind)
        assertEquals(20, state.windDirection)
        assertEquals("025°", state.estimatedWind)
        // The wind read off the boat: close-hauled on starboard at 340, 45 degrees off the wind.
        assertEquals("025°", state.windNow)
        assertEquals("Wind: stbd 340°\u00A0+\u00A045°", state.windNowGlance)
        assertTrue(state.windNowIsLive)
        // Two samples are not a histogram yet: the shift is judged against the set wind.
        assertEquals("---", state.meanWind)
        assertEquals("+5°", state.shift)
        assertEquals(5.0, assertNotNull(state.shiftDegrees), 1e-9)
        assertEquals(5.0, assertNotNull(state.estimatedOffset), 1e-9)
        assertEquals("340°", state.heading)
        assertEquals("GPS course", state.headingSource)
        assertEquals("Stbd upwind · GPS", state.headingGlance)
        assertEquals("5.8", state.speed)
        // Two close-hauled samples at the same speed: the boat is exactly on its average.
        assertEquals("0.0 vs avg 5.8", state.speedDelta)
        assertEquals(0.0, assertNotNull(state.speedDeltaMps), 1e-9)
        assertEquals("4.5", state.vmg)
        assertEquals("Shift vs set wind · + lift", state.shiftTitle)
        // On starboard upwind a veer is a lift, so the histogram from the boat is the histogram.
        assertEquals(state.histogram, state.histogramFromTheBoat)
        assertEquals("Stbd lifted 5°", state.adviceGlance)
        assertEquals(Tack.STARBOARD, state.tack)
        assertEquals("Starboard upwind", state.tackLabel)
        assertEquals("Stbd", state.tackShort)
        assertEquals("Upwind", state.pointOfSailLabel)
        assertEquals(TackAdvice.HOLD, state.advice)
        assertEquals("STAY", state.adviceTitle)
        assertEquals("Starboard is lifted 5° from the set wind", state.adviceDetail)
        assertEquals(2, state.histogram.first { it.offsetDegrees == 5 }.count)
        assertNull(state.setOffset)
        assertEquals(listOf(5.0, 5.0), state.shifts)
        assertEquals("Mean 025° ±0° · 2 samples", state.statistics)
        assertEquals("5.8 kn", state.averageUpwind)
        assertEquals("---", state.averageDownwind)
        assertTrue(state.canSetFromHeading)
    }

    @Test
    fun withAMeasuredWindTheMeanIsTheReference() {
        // Thirty samples on starboard at 340 (wind 25): the wind is read off the boat.
        val engine = engine(*DoubleArray(30) { 340.0 })
        val measured = WindUiState.from(engine.snapshot(30_000))
        assertTrue(measured.meanIsMeasured)
        assertEquals("025°", measured.meanWind)
        assertEquals(25.0, assertNotNull(measured.meanWindDegrees), 1e-6)
        // The set wind (20) sits 5 degrees backed from the mean, which is now the centre of everything.
        assertEquals(-5.0, assertNotNull(measured.setOffset), 1e-6)
        assertEquals("Wind direction frequency, ±40° around the mean wind", measured.histogramCaption)
        assertEquals(30, measured.histogram.first { it.offsetDegrees == 0 }.count)
        assertEquals(340.0, measured.targets.starboardUpwind, 1e-6)
        assertEquals(TackAdvice.EITHER, measured.advice)
        assertTrue(measured.shifts.all { kotlin.math.abs(it) < 1e-6 })

        // Then the boat is headed to 330 (wind 15) and holds it: the tack itself is not sampled, the
        // twelve seconds after it are. The median of the histogram stays at 25 - twelve headed samples
        // against thirty - the shift shown is the last ten seconds against it, and the advice is to tack.
        engine.dispatch(RaceEvent.FixReceived(PositionFix(GeoPoint(51.14, 5.83), 31_000L, 3.0, 330.0, 3.0)))
        for (t in 32L..43L) engine.dispatch(RaceEvent.FixReceived(PositionFix(GeoPoint(51.14, 5.83), t * 1_000L, 3.0, 330.0, 3.0)))
        val state = WindUiState.from(engine.snapshot(43_000))
        assertEquals("025°", state.meanWind)
        assertEquals(25.0, assertNotNull(state.meanWindDegrees), 1e-6)
        assertEquals(-5.0, assertNotNull(state.setOffset), 1e-6)
        assertEquals(30, state.histogram.first { it.offsetDegrees == 0 }.count)
        assertEquals(12, state.histogram.first { it.offsetDegrees == -10 }.count)
        assertEquals("015°", state.estimatedWind)
        assertEquals("015°", state.windNow)
        assertEquals("Wind: stbd 330°\u00A0+\u00A045°", state.windNowGlance)
        assertTrue(state.windNowIsLive)
        assertEquals("-10°", state.shift)
        assertEquals(-10.0, assertNotNull(state.shiftDegrees), 1e-6)
        assertEquals(TackAdvice.TACK, state.advice)
        assertEquals("TACK", state.adviceTitle)
        assertEquals("Headed 10° from the mean wind: port is lifted", state.adviceDetail)
        assertEquals("Headed 10° · port lifted", state.adviceGlance)
        assertEquals("Shift vs mean · + lift", state.shiftTitle)
        assertEquals(-10.0, assertNotNull(state.estimatedOffset), 1e-6)
    }

    @Test
    fun theTackAngleIsMeasuredOffBothTacks() {
        // Close-hauled on starboard at 312 and on port at 48: the boat tacks through 96 degrees.
        val engine = engine(*DoubleArray(30) { 312.0 }, wind = 0)
        engine.dispatch(RaceEvent.FixReceived(PositionFix(GeoPoint(51.14, 5.83), 31_000L, 3.0, 48.0, 3.0)))
        for (t in 32L..61L) engine.dispatch(RaceEvent.FixReceived(PositionFix(GeoPoint(51.14, 5.83), t * 1_000L, 3.0, 48.0, 3.0)))
        val state = WindUiState.from(engine.snapshot(62_000))
        assertEquals("Measured tack angle 48° · set 45°", state.tackAngleMeasured)
        assertEquals("000°", state.meanWind)
        assertEquals("Port upwind · GPS", state.headingGlance)
        // On port the estimates sit at 3 (48 - 45): a veer of 3, which on port is a header of 3.
        assertEquals("-3°", state.shift)
        assertEquals(-3.0, assertNotNull(state.shiftDegrees), 1e-9)
        // The full chart on the wind screen keeps the wind's own sign: a veer of 3.
        assertEquals(3.0, assertNotNull(state.estimatedOffset), 1e-9)
        assertEquals(3.0, assertNotNull(state.shifts.last()), 1e-9)
        assertEquals(TackAdvice.TACK, state.advice)
        // And the histogram from the boat is mirrored: the veered samples at +3 sit at -3, headed.
        assertEquals(30, state.histogram.first { it.offsetDegrees == 3 }.count)
        assertEquals(30, state.histogramFromTheBoat.first { it.offsetDegrees == -3 }.count)
        assertEquals(30, state.histogramFromTheBoat.first { it.offsetDegrees == 3 }.count)
        assertEquals((-40..40).toList(), state.histogramFromTheBoat.map { it.offsetDegrees })
    }

    @Test
    fun downwindTheLiftIsTheOtherWayRound() {
        // Wind 0, running on starboard at 220 (wind over the starboard quarter) with the wind veered to 5:
        // a veer downwind favours the port gybe, so on starboard it is a header.
        val engine = compassEngine()
        engine.dispatch(RaceEvent.SetWindDirection(0))
        engine.dispatch(RaceEvent.CompassUpdated(225.0))
        val starboardRun = WindUiState.from(engine.snapshot(0))
        assertEquals("-5°", starboardRun.shift)
        assertEquals(TackAdvice.TACK, starboardRun.advice)
        assertEquals("GYBE", starboardRun.adviceTitle)
        val port = compassEngine()
        port.dispatch(RaceEvent.SetWindDirection(0))
        port.dispatch(RaceEvent.CompassUpdated(145.0))
        val portRun = WindUiState.from(port.snapshot(0))
        assertEquals("+5°", portRun.shift)
        assertEquals(TackAdvice.HOLD, portRun.advice)
    }

    @Test
    fun theSpeedIsJudgedAgainstTheAverageOnlyOnTheAngle() {
        // A boat drifting at 0.2 m/s is not sampled: there is nothing to compare its speed with yet.
        val drifting = RaceEngine()
        drifting.dispatch(RaceEvent.SetWindDirection(20))
        drifting.dispatch(RaceEvent.FixReceived(PositionFix(GeoPoint(51.14, 5.83), 1_000L, 0.2, 340.0, 3.0)))
        val slow = WindUiState.from(drifting.snapshot(1_000))
        assertEquals("0.4", slow.speed)
        assertEquals("No average yet", slow.speedDelta)
        assertNull(slow.speedDeltaMps)

        // Close-hauled at 3 m/s, then a faster fix on the same tack: faster than the average of the two.
        val engine = engine(340.0)
        engine.dispatch(RaceEvent.FixReceived(PositionFix(GeoPoint(51.14, 5.83), 2_000L, 3.4, 340.0, 3.0)))
        val faster = WindUiState.from(engine.snapshot(2_000))
        assertEquals("6.6", faster.speed)
        assertEquals("+0.4 vs avg 6.2", faster.speedDelta)
        assertEquals(0.2, assertNotNull(faster.speedDeltaMps), 1e-9)

        // Bearing away to a reach the average is shown but the speed is not judged against it:
        // the average is of close-hauled sailing, and a reaching boat is faster whatever the wind does.
        engine.dispatch(RaceEvent.FixReceived(PositionFix(GeoPoint(51.14, 5.83), 3_000L, 4.0, 300.0, 3.0)))
        val reaching = WindUiState.from(engine.snapshot(3_000))
        assertEquals(TackAdvice.UNKNOWN, reaching.advice)
        assertEquals("Sail close-hauled", reaching.adviceGlance)
        assertEquals("Avg 6.2 kn", reaching.speedDelta)
        assertNull(reaching.speedDeltaMps)
        assertEquals("020°", reaching.windNow)
        assertEquals("Set wind · sail close-hauled", reaching.windNowGlance)
        assertFalse(reaching.windNowIsLive)
    }

    @Test
    fun compassHeadingRaceTimeAndLabels() {
        val engine = compassEngine()
        engine.dispatch(RaceEvent.CompassUpdated(200.0))
        val state = WindUiState.from(engine.snapshot(0))
        assertEquals("Compass", state.headingSource)
        assertEquals("Stbd downwind · Compass", state.headingGlance)
        assertEquals("Starboard downwind", state.tackLabel)
        assertEquals("Downwind", state.pointOfSailLabel)
        assertEquals("---", state.speed)
        assertEquals("340°", state.windNow)
        assertEquals("Wind: stbd 200°\u00A0+\u00A0140°", state.windNowGlance)
        assertTrue(state.windNowIsLive)
        val port = compassEngine().also { it.dispatch(RaceEvent.CompassUpdated(45.0)) }
        assertEquals("Port", WindUiState.from(port.snapshot(0)).tackShort)
        assertEquals("Wind: port 045°\u00A0-\u00A045°", WindUiState.from(port.snapshot(0)).windNowGlance)
        assertEquals("000°", WindUiState.from(port.snapshot(0)).windNow)
        assertEquals("Port downwind", WindUiState.tackLabel(SailingState(Tack.PORT, PointOfSail.DOWNWIND, -160.0)))
        assertEquals("Starboard upwind", WindUiState.tackLabel(SailingState(Tack.STARBOARD, PointOfSail.UPWIND, 45.0)))

        val racing = engine(315.0, wind = 0)
        racing.dispatch(RaceEvent.StartCountdown(1, nowMillis = 1_000))
        assertEquals("Start in 00:50", WindUiState.from(racing.snapshot(11_000)).raceTime)
        assertEquals("Racing +00:05", WindUiState.from(racing.snapshot(66_000)).raceTime)
        val downwind = WindUiState.from(engine(220.0, wind = 0).snapshot(1_000))
        assertEquals("5.8 kn", downwind.averageDownwind)
        assertEquals("---", downwind.averageUpwindVmg)
        assertTrue(downwind.averageDownwindVmg.endsWith("kn"))
    }
}
