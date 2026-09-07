package com.sailracing.app.ui.wind

import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.race.RaceEngine
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.wind.PointOfSail
import com.sailracing.domain.wind.SailingState
import com.sailracing.domain.wind.Tack
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindUiStateTest {

    @Test
    fun emptyState() {
        val state = WindUiState.from(RaceEngine().snapshot(0))
        assertEquals("000°", state.configuredWind)
        assertEquals("---", state.estimatedWind)
        assertEquals("---", state.shift)
        assertNull(state.shiftDegrees)
        assertEquals("---", state.heading)
        assertEquals("", state.headingSource)
        assertEquals("", state.tackLabel)
        assertEquals("—", state.tackShort)
        assertEquals("Tack", state.pointOfSailLabel)
        assertNull(state.tack)
        assertEquals(81, state.histogram.size)
        assertTrue(state.shifts.isEmpty())
        assertEquals("No upwind samples yet", state.statistics)
        assertFalse(state.canSetFromHeading)
    }

    @Test
    fun sailingState() {
        val engine = RaceEngine()
        engine.dispatch(RaceEvent.SetWindDirection(20))
        engine.dispatch(RaceEvent.FixReceived(PositionFix(GeoPoint(51.14, 5.83), 1_000, 3.0, 340.0, 3.0)))
        engine.dispatch(RaceEvent.FixReceived(PositionFix(GeoPoint(51.14, 5.83), 2_000, 3.0, 340.0, 3.0)))
        val state = WindUiState.from(engine.snapshot(2_000))
        assertEquals("020°", state.configuredWind)
        assertEquals(20, state.windDirection)
        assertEquals("025°", state.estimatedWind)
        assertEquals("+5°", state.shift)
        assertEquals(5.0, state.shiftDegrees)
        assertEquals("340°", state.heading)
        assertEquals("GPS course", state.headingSource)
        assertEquals("5.8 kn", state.speed)
        assertEquals(Tack.STARBOARD, state.tack)
        assertEquals("Starboard upwind", state.tackLabel)
        assertEquals("Stbd", state.tackShort)
        assertEquals("Upwind", state.pointOfSailLabel)
        assertEquals(2, state.histogram.first { it.offsetDegrees == 5 }.count)
        assertEquals(listOf(5.0, 5.0), state.shifts)
        assertEquals("Mean 025° ±0° · 2 samples", state.statistics)
        assertTrue(state.canSetFromHeading)
        assertEquals(5.0, state.estimatedOffset)
    }

    @Test
    fun compassHeadingAndLabels() {
        val engine = RaceEngine()
        engine.dispatch(RaceEvent.CompassUpdated(200.0))
        val state = WindUiState.from(engine.snapshot(0))
        assertEquals("Compass", state.headingSource)
        assertEquals("Starboard downwind", state.tackLabel)
        assertEquals("Downwind", state.pointOfSailLabel)
        val port = RaceEngine().also { it.dispatch(RaceEvent.CompassUpdated(45.0)) }
        assertEquals("Port", WindUiState.from(port.snapshot(0)).tackShort)
        assertEquals("Port downwind", WindUiState.tackLabel(SailingState(Tack.PORT, PointOfSail.DOWNWIND, -160.0)))
        assertEquals("Starboard upwind", WindUiState.tackLabel(SailingState(Tack.STARBOARD, PointOfSail.UPWIND, 45.0)))
    }
}
