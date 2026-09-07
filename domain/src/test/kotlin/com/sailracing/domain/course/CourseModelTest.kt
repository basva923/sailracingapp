package com.sailracing.domain.course

import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.startline.StartLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CourseModelTest {

    private val origin = GeoPoint(51.14, 5.83)
    private val frame = CourseFrame(origin, windDirectionDegrees = 0.0)
    private val line = StartLine(frame.toGeo(CoursePosition(-45.0, 5.0)), frame.toGeo(CoursePosition(45.0, 5.0)))
    private val boat = frame.toGeo(CoursePosition(25.0, 85.0))

    private fun point(across: Double, upwind: Double, wind: Double? = null, seconds: Long = 0L) =
        TrackPoint(seconds * 1000, frame.toGeo(CoursePosition(across, upwind)), speedMps = 3.0, headingDegrees = 0.0, upwindWindDegrees = wind)

    private val track = Track(listOf(point(5.0, -25.0), point(15.0, 45.0, wind = 5.0), point(25.0, 85.0, wind = 5.0)))
    private val inputs = CourseInputs(frame, line, windwardMark = null, boat = boat, tackAngleDegrees = 45, downwindAngleDegrees = 140)

    @Test
    fun `without a set mark the top of the area is the mark`() {
        val course = CourseModel.build(track, inputs)
        assertEquals(frame, course.frame)
        assertEquals(inputs, course.inputs)
        assertEquals(GridSpec(-50.0, -30.0, 10, 12, 10.0), course.spec)
        assertFalse(course.windwardMarkIsSet)
        assertEquals(CoursePosition(0.0, 90.0), course.windwardMark)
        assertEquals(course.windwardMark, course.grid.axisTarget)
        assertEquals(3, course.trackPositions.size)
        assertEquals(3, course.grid.totalVisits)
        assertEquals(-45.0, assertNotNull(course.pinEnd).acrossMeters, 0.01)
        assertEquals(45.0, assertNotNull(course.boatEnd).acrossMeters, 0.01)
        val boat = assertNotNull(course.boat)
        assertEquals(25.0, boat.acrossMeters, 0.01)
        assertEquals(85.0, boat.upwindMeters, 0.01)
        assertEquals(boat, course.route.first())
        assertEquals(course.windwardMark, course.route.last())
        assertEquals(0, course.windField.measuredCount)
        assertTrue(course.toString().startsWith("CourseModel(spec="))
    }

    @Test
    fun `a set mark is part of the area and the axis`() {
        val mark = frame.toGeo(CoursePosition(-40.0, 495.0))
        val course = CourseModel.build(track, inputs.copy(windwardMark = mark))
        assertTrue(course.windwardMarkIsSet)
        assertEquals(-40.0, course.windwardMark.acrossMeters, 0.01)
        assertEquals(495.0, course.windwardMark.upwindMeters, 0.01)
        assertEquals(50.0, course.spec.cellSizeMeters)
        assertEquals(500.0, course.spec.topMeters)
        assertEquals(course.windwardMark, course.route.last())
    }

    @Test
    fun `without a boat there is no route, and an empty track still has an area`() {
        val course = CourseModel.build(Track(), inputs.copy(boat = null, line = StartLine()))
        assertTrue(course.route.isEmpty())
        assertNull(course.boat)
        assertNull(course.pinEnd)
        assertNull(course.boatEnd)
        assertTrue(course.trackPositions.isEmpty())
        assertEquals(GridSpec(0.0, 0.0, 1, 1, 10.0), course.spec)
    }

    @Test
    fun `a model is reused only for the same track instance and inputs`() {
        val course = CourseModel.build(track, inputs)
        assertTrue(course.isFor(track, inputs))
        assertFalse(course.isFor(track.copy(), inputs))
        assertFalse(course.isFor(track, inputs.copy(tackAngleDegrees = 40)))
        assertFalse(course.isFor(track, inputs.copy(boat = origin)))
    }

    @Test
    fun `a long track is thinned for drawing but keeps its last point`() {
        // 1202 points thin to every third one (401 points) plus the last, which is not on the stride.
        val count = CourseModel.MAX_TRACK_POINTS * 2 + 2
        var long = Track(capacity = count)
        for (i in 0 until count) long += TrackPoint(i * 1000L, Geo.destination(origin, 0.0, i.toDouble()))
        val positions = CourseModel.build(long, inputs.copy(boat = null, line = StartLine())).trackPositions
        assertEquals(402, positions.size)
        assertEquals(0.0, positions.first().upwindMeters, 1e-6)
        assertEquals((count - 1).toDouble(), positions.last().upwindMeters, 0.01)
        assertEquals(3.0, positions[1].upwindMeters, 0.01)

        val exact = Track(capacity = 10).let { t -> (0 until 10).fold(t) { acc, i -> acc + TrackPoint(i * 1000L, Geo.destination(origin, 0.0, i.toDouble())) } }
        assertEquals(10, CourseModel.build(exact, inputs).trackPositions.size)
    }
}
