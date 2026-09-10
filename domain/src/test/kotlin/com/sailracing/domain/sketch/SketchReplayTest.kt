package com.sailracing.domain.sketch

import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.geo.LocalPlane
import com.sailracing.domain.geo.PlanePosition
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.race.RaceSettings
import com.sailracing.domain.wind.WindSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SketchReplayTest {

    private val course = CourseSketch(
        wind = WindSettings(directionDegrees = 0, tackAngleDegrees = 45),
        pinEnd = PlanePosition(-50.0, 0.0),
        boatEnd = PlanePosition(50.0, 0.0),
        windwardMark = PlanePosition(0.0, 400.0),
    )

    private fun CourseSketch.sailing(vararg points: PlanePosition, speedMps: Double = 3.0) =
        copy(track = points.map { SketchPoint(it, speedMps) })

    @Test
    fun `the course is set up before the boat leaves the beach`() {
        val events = SketchReplay.events(course.sailing(PlanePosition(0.0, 0.0), PlanePosition(0.0, 30.0)))
        assertEquals(RaceEvent.UpdateSettings(RaceSettings()), events[0])
        assertEquals(RaceEvent.SetWindSettings(course.wind), events[1])
        assertEquals(RaceEvent.SetStartLine(course.startLine), events[2])
        assertEquals(RaceEvent.SetWindwardMark(course.windwardMarkGeo), events[3])
        assertTrue(events.drop(4).all { it is RaceEvent.FixReceived })
    }

    @Test
    fun `a leg is sailed at one fix a second, and the last one is where the drawing stops`() {
        val fixes = SketchReplay.fixes(course.sailing(PlanePosition(0.0, 0.0), PlanePosition(0.0, 30.0)))
        // 30 m at 3 m/s: ten whole seconds of it, and then the boat is at the end of the leg.
        assertEquals(11, fixes.size)
        assertEquals((0L..10L).map { it * 1000 }, fixes.map { it.timestampMillis })
        assertTrue(fixes.all { it.speedMps == 3.0 && it.courseDegrees == 0.0 })
        assertTrue(fixes.all { it.accuracyMeters == SketchReplay.ACCURACY_METERS })
        val plane = course.plane
        assertEquals(listOf(0.0, 3.0, 6.0, 9.0, 12.0, 15.0, 18.0, 21.0, 24.0, 27.0, 30.0), fixes.map { north(plane, it.point) })
    }

    @Test
    fun `a corner is a tack, and the boat keeps its pace across it`() {
        // Ten metres north, then east: the first leg ends between two fixes, so the second starts mid-stride.
        val fixes = SketchReplay.fixes(
            course.sailing(PlanePosition(0.0, 0.0), PlanePosition(0.0, 10.0), PlanePosition(30.0, 10.0)),
        )
        val headings = fixes.map { it.courseDegrees }
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0), headings.take(4))
        assertTrue(headings.drop(4).all { it == 90.0 })
        val plane = course.plane
        // 12 m sailed of the first 10 m leg, so the second leg starts 2 m along it.
        assertEquals(listOf(2.0, 5.0, 8.0, 11.0, 14.0, 17.0, 20.0, 23.0, 26.0, 29.0), fixes.drop(4).dropLast(1).map { east(plane, it.point) })
        assertEquals(30.0, east(plane, fixes.last().point), 1e-6)
    }

    @Test
    fun `a track drawn at the tack angle is a boat close-hauled`() {
        // Wind from the north, tacking through 90: up and to the left is starboard, up and to the right is port.
        val fixes = SketchReplay.fixes(
            course.sailing(PlanePosition(0.0, 0.0), PlanePosition(-70.0, 70.0), PlanePosition(0.0, 140.0)),
        )
        assertEquals(315.0, fixes.first().courseDegrees!!, 1e-9)
        assertEquals(45.0, fixes.last().courseDegrees!!, 1e-9)
    }

    @Test
    fun `a drawing that is not a sail produces no fixes`() {
        assertTrue(SketchReplay.fixes(course).isEmpty())
        assertTrue(SketchReplay.fixes(course.sailing(PlanePosition(0.0, 0.0))).isEmpty())
        // Two points in the same spot: a boat that never left it.
        assertTrue(SketchReplay.fixes(course.sailing(PlanePosition(4.0, 4.0), PlanePosition(4.0, 4.0))).isEmpty())
        assertEquals(4, SketchReplay.events(course).size)
    }

    @Test
    fun `the clock can start anywhere`() {
        val fixes = SketchReplay.fixes(course.sailing(PlanePosition(0.0, 0.0), PlanePosition(0.0, 6.0)), startTimeMillis = 1_700_000_000_000)
        assertEquals(listOf(1_700_000_000_000, 1_700_000_001_000, 1_700_000_002_000), fixes.map { it.timestampMillis })
    }

    private fun north(plane: LocalPlane, point: GeoPoint) = Math.round(plane.toPlane(point).northMeters * 1e6) / 1e6

    private fun east(plane: LocalPlane, point: GeoPoint) = Math.round(plane.toPlane(point).eastMeters * 1e6) / 1e6
}
