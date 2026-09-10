package com.sailracing.app.ui.wind

import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.race.RaceEngine
import com.sailracing.domain.race.RaceEvent
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

    @Test
    fun emptyState() {
        val state = WindUiState.from(RaceEngine().snapshot(0))
        assertEquals("000°", state.configuredWind)
        assertEquals("---", state.meanWind)
        assertFalse(state.meanIsMeasured)
        assertNull(state.meanWindDegrees)
        assertEquals("---", state.estimatedWind)
        assertEquals("---", state.shift)
        assertNull(state.shiftDegrees)
        assertEquals("---", state.heading)
        assertEquals("", state.headingSource)
        assertEquals("---", state.speed)
        assertEquals("", state.speedDelta)
        assertNull(state.speedDeltaMps)
        assertEquals("---", state.vmg)
        assertEquals("Shift vs set wind", state.shiftTitle)
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
        // Two samples are not a histogram yet: the shift is judged against the set wind.
        assertEquals("---", state.meanWind)
        assertEquals("+5°", state.shift)
        assertEquals(5.0, state.shiftDegrees)
        assertEquals(5.0, state.estimatedOffset)
        assertEquals("340°", state.heading)
        assertEquals("GPS course", state.headingSource)
        assertEquals("5.8", state.speed)
        // Two close-hauled samples at the same speed: the boat is exactly on its average.
        assertEquals("0.0 vs avg 5.8", state.speedDelta)
        assertEquals(0.0, assertNotNull(state.speedDeltaMps), 1e-9)
        assertEquals("4.5", state.vmg)
        assertEquals("Shift vs set wind", state.shiftTitle)
        assertEquals("Stbd lifted 5°", state.adviceGlance)
        assertEquals(Tack.STARBOARD, state.tack)
        assertEquals("Starboard upwind", state.tackLabel)
        assertEquals("Stbd", state.tackShort)
        assertEquals("Upwind", state.pointOfSailLabel)
        assertEquals(TackAdvice.HOLD, state.advice)
        assertEquals("HOLD", state.adviceTitle)
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
    fun withAHistogramTheMeanIsTheReference() {
        // Thirty samples on starboard at 340 (wind 25), then the boat is headed to 330 (wind 15): tack.
        val engine = engine(*DoubleArray(30) { 340.0 })
        engine.dispatch(RaceEvent.FixReceived(PositionFix(GeoPoint(51.14, 5.83), 31_000L, 3.0, 330.0, 3.0)))
        val state = WindUiState.from(engine.snapshot(31_000))
        assertTrue(state.meanIsMeasured)
        assertEquals("025°", state.meanWind)
        assertEquals(25.0, assertNotNull(state.meanWindDegrees), 1e-6)
        // The set wind (20) sits 5 degrees backed from the mean, which is now the centre of everything.
        assertEquals(-5.0, assertNotNull(state.setOffset), 1e-6)
        assertEquals("Wind direction frequency, ±40° around the mean wind", state.histogramCaption)
        assertEquals(30, state.histogram.first { it.offsetDegrees == 0 }.count)
        assertEquals(340.0, state.targets.starboardUpwind, 1e-6)
        assertEquals("015°", state.estimatedWind)
        assertEquals("-10°", state.shift)
        assertEquals(TackAdvice.TACK, state.advice)
        assertEquals("TACK", state.adviceTitle)
        assertEquals("Headed 10° from the mean wind: port is lifted", state.adviceDetail)
        assertEquals("Headed 10° · port lifted", state.adviceGlance)
        assertEquals("Shift vs mean", state.shiftTitle)
        assertEquals(-10.0, assertNotNull(state.estimatedOffset), 1e-6)
        assertTrue(state.shifts.all { kotlin.math.abs(it) < 1e-6 })
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
    }

    @Test
    fun compassHeadingRaceTimeAndLabels() {
        val engine = RaceEngine()
        engine.dispatch(RaceEvent.CompassUpdated(200.0))
        val state = WindUiState.from(engine.snapshot(0))
        assertEquals("Compass", state.headingSource)
        assertEquals("Starboard downwind", state.tackLabel)
        assertEquals("Downwind", state.pointOfSailLabel)
        assertEquals("---", state.speed)
        val port = RaceEngine().also { it.dispatch(RaceEvent.CompassUpdated(45.0)) }
        assertEquals("Port", WindUiState.from(port.snapshot(0)).tackShort)
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
