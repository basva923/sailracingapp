package com.sailracing.domain.course

import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals

class CourseFrameTest {

    private val origin = GeoPoint(51.14, 5.83)

    @Test
    fun `north wind frame keeps east as across and north as upwind`() {
        val frame = CourseFrame(origin, windDirectionDegrees = 0.0)
        val position = frame.toCourse(Geo.destination(Geo.destination(origin, 0.0, 300.0), 90.0, 100.0))
        assertEquals(100.0, position.acrossMeters, 0.5)
        assertEquals(300.0, position.upwindMeters, 0.5)
        assertEquals(CoursePosition(0.0, 0.0), frame.toCourse(origin))
        assertEquals(0.0, frame.toCourseBearing(0.0))
        assertEquals(90.0, frame.toCourseBearing(90.0))
    }

    @Test
    fun `the frame turns with the wind so that upwind is always up`() {
        val frame = CourseFrame(origin, windDirectionDegrees = 90.0)
        val upwind = frame.toCourse(Geo.destination(origin, 90.0, 200.0))
        assertEquals(0.0, upwind.acrossMeters, 0.5)
        assertEquals(200.0, upwind.upwindMeters, 0.5)
        // Looking east, south is to the right.
        val right = frame.toCourse(Geo.destination(origin, 180.0, 50.0))
        assertEquals(50.0, right.acrossMeters, 0.5)
        assertEquals(0.0, right.upwindMeters, 0.5)
        assertEquals(270.0, frame.toCourseBearing(0.0))
        assertEquals(45.0, frame.toCourseBearing(135.0))
    }

    @Test
    fun `positions convert back to the same place`() {
        val frame = CourseFrame(origin, windDirectionDegrees = 213.0)
        for (position in listOf(CoursePosition(0.0, 0.0), CoursePosition(-350.0, 1200.0), CoursePosition(800.0, -400.0))) {
            val geo = frame.toGeo(position)
            val back = frame.toCourse(geo)
            assertEquals(position.acrossMeters, back.acrossMeters, 1e-6)
            assertEquals(position.upwindMeters, back.upwindMeters, 1e-6)
            assertEquals(Math.hypot(position.acrossMeters, position.upwindMeters), Geo.distanceMeters(origin, geo), 0.5)
        }
    }

    @Test
    fun `longitude differences wrap across the antimeridian`() {
        val frame = CourseFrame(GeoPoint(0.0, 179.999), windDirectionDegrees = 0.0)
        val across = frame.toCourse(GeoPoint(0.0, -179.999)).acrossMeters
        assertEquals(222.4, across, 1.0)
        val back = frame.toGeo(CoursePosition(across, 0.0))
        assertEquals(-179.999, back.longitude, 1e-6)
        assertEquals(90.0, frame.toGeo(CoursePosition(0.0, 1e9)).latitude)
    }
}
