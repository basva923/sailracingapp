package com.sailracing.simulation

import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RaceCourseTest {

    private val center = GeoPoint(51.14, 5.83)

    @Test
    fun `square course is laid out relative to the wind`() {
        val course = RaceCourse.square(center, windDirectionDegrees = 20.0, lineLengthMeters = 100.0, beatLengthMeters = 500.0, leewardOffsetMeters = 80.0)
        assertEquals(100.0, Geo.distanceMeters(course.pinEnd, course.boatEnd), 0.01)
        assertEquals(290.0, Geo.initialBearingDegrees(center, course.pinEnd), 0.01)
        assertEquals(110.0, Geo.initialBearingDegrees(center, course.boatEnd), 0.01)
        assertEquals(500.0, Geo.distanceMeters(center, course.windwardMark), 0.01)
        assertEquals(20.0, Geo.initialBearingDegrees(center, course.windwardMark), 0.01)
        assertEquals(80.0, Geo.distanceMeters(center, course.leewardMark), 0.01)
        assertEquals(200.0, Geo.initialBearingDegrees(center, course.leewardMark), 0.01)
        assertTrue(course.line.isComplete)
        assertEquals(0.0, Geo.distanceMeters(center, course.lineCenter), 0.01)
        // Across the axis: positive to the right looking upwind (bearing 110 from the centre).
        assertEquals(50.0, course.acrossMeters(course.boatEnd), 0.01)
        assertEquals(-50.0, course.acrossMeters(course.pinEnd), 0.01)
        assertEquals(0.0, course.acrossMeters(Geo.destination(center, 20.0, 250.0)), 0.01)
    }

    @Test
    fun `dimensions must be positive`() {
        assertFailsWith<IllegalArgumentException> { RaceCourse.square(center, 0.0, lineLengthMeters = 0.0) }
    }
}
