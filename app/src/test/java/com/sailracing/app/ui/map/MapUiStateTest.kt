package com.sailracing.app.ui.map

import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.race.RaceEngine
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.strategy.FavouredSide
import com.sailracing.domain.strategy.TackAdvice
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapUiStateTest {

    private val pin = GeoPoint(51.14, 5.83)
    private val boatEnd = Geo.destination(pin, 90.0, 100.0)
    private val center = Geo.destination(pin, 90.0, 50.0)

    @Test
    fun emptyState() {
        val state = MapUiState.from(RaceEngine().snapshot(0))
        assertFalse(state.hasFrame)
        assertEquals(MapArea(-50.0, -50.0, 100.0, 100.0, 10.0), state.area)
        assertEquals(MapView(0.0, 0.0, 200.0), state.view)
        assertTrue(state.arrows.isEmpty())
        assertTrue(state.track.isEmpty())
        assertTrue(state.route.isEmpty())
        assertNull(state.pinEnd)
        assertNull(state.boatEnd)
        assertNull(state.boat)
        assertNull(state.mark)
        assertFalse(state.markIsSet)
        assertNull(state.boatHeadingDegrees)
        assertEquals(0.0, state.northDegrees)
        assertEquals(315.0, state.starboardHeadingDegrees)
        assertEquals(45.0, state.portHeadingDegrees)
        assertNull(state.favouredTack)
        assertEquals(FavouredSide.UNKNOWN, state.favouredSide)
        assertEquals("SIDES UNKNOWN", state.sideTitle)
        assertEquals("Sail upwind on both sides to compare them (0 left, 0 right samples)", state.sideDetail)
        assertEquals(TackAdvice.UNKNOWN, state.advice)
        assertEquals("—", state.tackTitle)
        assertEquals("Waiting for a heading", state.tackDetail)
        assertEquals("", state.scaleText)
        assertEquals("Mark: top of the area until you set it", state.markText)
        assertEquals("No track yet", state.trackText)
        assertFalse(state.canMarkHere)
        assertFalse(state.canSetMarkFromLine)
    }

    @Test
    fun theViewShowsTheWholeAreaWithAMarginAndIsNeverTiny() {
        val beat = MapUiState.view(MapArea(-60.0, -60.0, 240.0, 720.0, 60.0))
        assertEquals(60.0, beat.centerAcrossMeters, 1e-9)
        assertEquals(300.0, beat.centerUpwindMeters, 1e-9)
        assertEquals(828.0, beat.sideMeters, 1e-9)
        assertEquals(MapView(5.0, 5.0, 200.0), MapUiState.view(MapArea(0.0, 0.0, 10.0, 10.0, 10.0)))
        assertEquals(100.0, MapArea(-50.0, -50.0, 150.0, 200.0, 10.0).rightMeters)
        assertEquals(150.0, MapArea(-50.0, -50.0, 150.0, 200.0, 10.0).topMeters)
    }

    @Test
    fun aRaceInProgressFillsTheMap() {
        val engine = RaceEngine()
        engine.dispatch(RaceEvent.SetStartLine(StartLine(pin, boatEnd)))
        engine.dispatch(RaceEvent.SetWindDirection(0))
        // Close-hauled on starboard in a north wind (heading 315), 230 m upwind and 30 m right (east) of the line centre.
        val spot = Geo.destination(Geo.destination(center, 0.0, 230.0), 90.0, 30.0)
        for (i in 1..4) engine.dispatch(RaceEvent.FixReceived(PositionFix(spot, i * 1_000L, 3.0, 315.0, 3.0)))
        val state = MapUiState.from(engine.snapshot(4_000))

        assertTrue(state.hasFrame)
        assertEquals(-50.0, assertNotNull(state.pinEnd).acrossMeters, 0.5)
        assertEquals(0.0, state.pinEnd!!.upwindMeters, 0.5)
        assertEquals(50.0, assertNotNull(state.boatEnd).acrossMeters, 0.5)
        val boat = assertNotNull(state.boat)
        assertEquals(30.0, boat.acrossMeters, 0.5)
        assertEquals(230.0, boat.upwindMeters, 0.5)
        assertEquals(315.0, assertNotNull(state.boatHeadingDegrees), 1e-9)
        assertEquals(0.0, state.northDegrees, 1e-9)
        assertEquals(315.0, state.starboardHeadingDegrees, 1e-9)
        assertEquals(45.0, state.portHeadingDegrees, 1e-9)
        assertEquals(4, state.track.size)

        // The area follows the line and the boat: 100 m wide, 230 m tall, so 20 m cells.
        val area = state.area
        assertEquals(20.0, area.cellMeters)
        assertEquals(-60.0, area.leftMeters)
        assertEquals(60.0, area.rightMeters)
        assertEquals(240.0, area.topMeters)
        assertEquals(MapUiState.view(area), state.view)
        assertTrue(state.scaleText.startsWith("Area 120 m ×") && state.scaleText.endsWith("20 m cells"), state.scaleText)
        // One arrow per cell; only the cell the boat sat in is measured, at the mean wind.
        assertEquals((area.widthMeters / 20).toInt() * (area.heightMeters / 20).toInt(), state.arrows.size)
        val measured = state.arrows.single { it.measured }
        assertEquals(4, measured.column)
        assertEquals(0.0, measured.shiftDegrees, 1e-9)
        // No mark set: the top of the area, and a line from the boat up to it.
        assertFalse(state.markIsSet)
        val mark = assertNotNull(state.mark)
        assertEquals(0.0, mark.acrossMeters, 1e-9)
        assertEquals(240.0, mark.upwindMeters, 1e-9)
        assertEquals(boat, state.route.first())
        assertEquals(mark, state.route.last())
        assertTrue(state.route.size >= 2)
        assertEquals("Mark: top of the area until you set it", state.markText)
        assertTrue(state.canMarkHere)
        assertTrue(state.canSetMarkFromLine)

        assertEquals("4 points · 0 min", state.trackText)
        assertEquals(TackAdvice.EITHER, state.advice)
        assertEquals("EITHER TACK", state.tackTitle)
    }

    @Test
    fun theFrameTurnsWithTheWind() {
        val engine = RaceEngine()
        engine.dispatch(RaceEvent.SetStartLine(StartLine(pin, boatEnd)))
        engine.dispatch(RaceEvent.SetWindDirection(90))
        engine.dispatch(RaceEvent.CompassUpdated(45.0))
        val state = MapUiState.from(engine.snapshot(0))
        // In an east wind the line lies along the wind axis and north is to the left.
        assertEquals(-50.0, assertNotNull(state.pinEnd).upwindMeters, 0.5)
        assertEquals(0.0, state.pinEnd!!.acrossMeters, 0.5)
        assertEquals(270.0, state.northDegrees, 1e-9)
        assertEquals(315.0, assertNotNull(state.boatHeadingDegrees), 1e-9)
        assertNull(state.boat)
        assertTrue(state.route.isEmpty())
        assertFalse(state.canMarkHere)
        assertTrue(state.canSetMarkFromLine)
    }

    @Test
    fun aSetMarkIsPartOfTheAreaAndDescribedFromTheLine() {
        val engine = RaceEngine()
        engine.dispatch(RaceEvent.SetStartLine(StartLine(pin, boatEnd)))
        engine.dispatch(RaceEvent.SetWindDirection(0))
        engine.dispatch(RaceEvent.FixReceived(PositionFix(center, 1_000L, 3.0, 315.0, 3.0)))
        engine.dispatch(RaceEvent.SetWindwardMarkFromLine(10, 500.0))
        val state = MapUiState.from(engine.snapshot(1_000))
        assertTrue(state.markIsSet)
        val mark = assertNotNull(state.mark)
        assertEquals(500.0 * Math.sin(Math.toRadians(10.0)), mark.acrossMeters, 0.5)
        assertEquals(500.0 * Math.cos(Math.toRadians(10.0)), mark.upwindMeters, 0.5)
        assertTrue(state.area.topMeters >= mark.upwindMeters)
        assertEquals("Mark set 500 m at 010° from the line", state.markText)
        assertEquals(mark, state.route.last())

        engine.dispatch(RaceEvent.SetWindwardMark(null))
        assertFalse(MapUiState.from(engine.snapshot(1_000)).markIsSet)
    }

    @Test
    fun arrowsAreMeasuredOnlyWithEnoughSamplesAndEstimatedElsewhere() {
        val engine = RaceEngine()
        engine.dispatch(RaceEvent.SetWindDirection(0))
        val here = GeoPoint(51.14, 5.83)
        val there = Geo.destination(here, 0.0, 300.0)
        engine.dispatch(RaceEvent.FixReceived(PositionFix(here, 1_000L, 3.0, 320.0, 3.0)))
        engine.dispatch(RaceEvent.FixReceived(PositionFix(here, 2_000L, 3.0, 320.0, 3.0)))
        engine.dispatch(RaceEvent.FixReceived(PositionFix(there, 3_000L, 3.0, 320.0, 3.0)))
        val state = MapUiState.from(engine.snapshot(3_000))
        assertTrue(state.arrows.isNotEmpty())
        assertTrue(state.arrows.none { it.measured })
        assertTrue(state.arrows.all { it.shiftDegrees == 0.0 })

        engine.dispatch(RaceEvent.FixReceived(PositionFix(here, 4_000L, 3.0, 320.0, 3.0)))
        val measured = MapUiState.from(engine.snapshot(4_000))
        assertEquals(1, measured.arrows.count { it.measured })
        // One measured cell: every estimated cell takes its wind.
        assertTrue(measured.arrows.all { abs(it.shiftDegrees - 5.0) < 1e-9 }, "arrows ${measured.arrows}")
    }
}
