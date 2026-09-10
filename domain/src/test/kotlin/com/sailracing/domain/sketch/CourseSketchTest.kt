package com.sailracing.domain.sketch

import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.geo.PlanePosition
import com.sailracing.domain.wind.WindSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CourseSketchTest {

    @Test
    fun `an empty sketch is a patch of open water with nothing drawn on it`() {
        val sketch = CourseSketch()
        assertEquals("Untitled course", sketch.name)
        assertEquals(CourseSketch.DEFAULT_ORIGIN, sketch.origin)
        assertEquals(GeoPoint(52.0, 5.0), CourseSketch.DEFAULT_ORIGIN)
        assertEquals(WindSettings(), sketch.wind)
        assertNull(sketch.startLine.pinEnd)
        assertNull(sketch.startLine.boatEnd)
        assertNull(sketch.windwardMarkGeo)
        assertNull(sketch.boat)
        assertTrue(sketch.track.isEmpty())
        assertEquals(sketch.origin, sketch.plane.origin)
    }

    @Test
    fun `the marks are drawn in metres and read back as coordinates`() {
        val sketch = CourseSketch(
            pinEnd = PlanePosition(-50.0, 0.0),
            boatEnd = PlanePosition(50.0, 0.0),
            windwardMark = PlanePosition(0.0, 400.0),
        )
        val line = sketch.startLine
        assertEquals(100.0, Geo.distanceMeters(assertNotNull(line.pinEnd), assertNotNull(line.boatEnd)), 0.5)
        assertEquals(90.0, Geo.initialBearingDegrees(line.pinEnd!!, line.boatEnd!!), 0.5)
        val mark = assertNotNull(sketch.windwardMarkGeo)
        assertEquals(400.0, Geo.distanceMeters(sketch.origin, mark), 0.5)
        assertEquals(0.0, Geo.initialBearingDegrees(sketch.origin, mark), 0.5)
    }

    @Test
    fun `the boat is at the end of the track it has sailed`() {
        val sailed = CourseSketch()
            .plusPoint(PlanePosition(0.0, 0.0))
            .plusPoint(PlanePosition(-70.0, 70.0), speedMps = 2.5)
        assertEquals(PlanePosition(-70.0, 70.0), sailed.boat)
        assertEquals(listOf(CourseSketch.DEFAULT_BOAT_SPEED_MPS, 2.5), sailed.track.map { it.speedMps })
        // Clearing the track leaves the course itself alone: the same water, unsailed.
        val cleared = sailed.copy(windwardMark = PlanePosition(0.0, 400.0)).withoutTrack()
        assertTrue(cleared.track.isEmpty())
        assertNull(cleared.boat)
        assertEquals(PlanePosition(0.0, 400.0), cleared.windwardMark)
    }

    @Test
    fun `the course dials are turned without reaching through the settings`() {
        val finer = CourseSketch().withCourseSettings { copy(grid = grid.copy(cellSizeMeters = 20.0)) }
        assertEquals(20.0, finer.settings.course.grid.cellSizeMeters)
        val harder = finer.withCourseSettings { copy(raceLine = raceLine.copy(runs = 32)) }
        assertEquals(32, harder.settings.course.raceLine.runs)
        assertEquals(20.0, harder.settings.course.grid.cellSizeMeters)
    }

    @Test
    fun `a boat that does not move leaves no track`() {
        assertFailsWith<IllegalArgumentException> { SketchPoint(PlanePosition(), speedMps = 0.0) }
        assertFailsWith<IllegalArgumentException> { SketchPoint(PlanePosition(), speedMps = -1.0) }
    }
}
