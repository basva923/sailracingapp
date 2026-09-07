package com.sailracing.domain.course

import com.sailracing.domain.geo.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackTest {

    private fun point(t: Long) = TrackPoint(t, GeoPoint(51.0, 5.0 + t * 1e-5), speedMps = 3.0, headingDegrees = 10.0)

    @Test
    fun `capacity must be positive`() {
        assertFailsWith<IllegalArgumentException> { Track(capacity = 0) }
        assertEquals(4 * 3600, Track().capacity)
    }

    @Test
    fun `keeps the newest points`() {
        var track = Track(capacity = 3)
        assertTrue(track.isEmpty)
        assertNull(track.latest)
        for (t in 1L..5L) track += point(t)
        assertEquals(listOf(point(3), point(4), point(5)), track.points)
        assertEquals(point(5), track.latest)
        assertTrue(!track.isEmpty)
        assertNull(point(1).upwindWindDegrees)
    }
}
