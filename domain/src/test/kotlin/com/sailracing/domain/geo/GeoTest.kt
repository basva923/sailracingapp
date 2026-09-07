package com.sailracing.domain.geo

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeoTest {

    private val a = GeoPoint(51.143547, 5.833524)
    private val b = GeoPoint(51.139998, 5.839311)
    private val c = GeoPoint(51.140391, 5.833951)

    @Test
    fun `geo point validates its range`() {
        assertFailsWith<IllegalArgumentException> { GeoPoint(91.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { GeoPoint(0.0, 181.0) }
        assertEquals(GeoPoint(-90.0, 180.0), GeoPoint(-90.0, 180.0))
    }

    @Test
    fun `haversine distance is symmetric and matches reference values`() {
        assertEquals(565.0, Geo.distanceMeters(a, b), 0.5)
        assertEquals(565.0, Geo.distanceMeters(b, a), 0.5)
        assertEquals(376.0, Geo.distanceMeters(c, b), 0.5)
        assertEquals(352.0, Geo.distanceMeters(a, c), 0.5)
        assertEquals(0.0, Geo.distanceMeters(a, a), 1e-9)
    }

    @Test
    fun `initial bearing follows the compass`() {
        val origin = GeoPoint(51.14, 5.83)
        assertEquals(0.0, Geo.initialBearingDegrees(origin, GeoPoint(51.15, 5.83)), 0.01)
        assertEquals(90.0, Geo.initialBearingDegrees(origin, GeoPoint(51.14, 5.84)), 0.05)
        assertEquals(180.0, Geo.initialBearingDegrees(origin, GeoPoint(51.13, 5.83)), 0.01)
        assertEquals(270.0, Geo.initialBearingDegrees(origin, GeoPoint(51.14, 5.82)), 0.05)
    }

    @Test
    fun `cross track distance matches reference magnitudes and carries a side sign`() {
        assertEquals(230.0, abs(Geo.crossTrackDistanceMeters(a, b, c)), 0.5)
        assertEquals(230.0, abs(Geo.crossTrackDistanceMeters(b, a, c)), 0.5)
        assertEquals(345.0, abs(Geo.crossTrackDistanceMeters(c, b, a)), 0.5)
        assertEquals(369.0, abs(Geo.crossTrackDistanceMeters(a, c, b)), 0.5)
        assertEquals(345.0, abs(Geo.crossTrackDistanceMeters(b, c, a)), 0.5)
        assertEquals(369.0, abs(Geo.crossTrackDistanceMeters(c, a, b)), 0.5)

        // C lies to the right of A->B (south of a line running south-east) and to the left of B->A.
        assertTrue(Geo.crossTrackDistanceMeters(a, b, c) > 0)
        assertTrue(Geo.crossTrackDistanceMeters(b, a, c) < 0)
    }

    @Test
    fun `along track distance is signed relative to the line start`() {
        val start = GeoPoint(51.14, 5.83)
        val end = Geo.destination(start, 90.0, 100.0)

        val ahead = Geo.destination(Geo.destination(start, 90.0, 30.0), 0.0, 20.0)
        assertEquals(30.0, Geo.alongTrackDistanceMeters(start, end, ahead), 0.1)

        val behind = Geo.destination(Geo.destination(start, 270.0, 25.0), 180.0, 10.0)
        assertEquals(-25.0, Geo.alongTrackDistanceMeters(start, end, behind), 0.1)

        val beyond = Geo.destination(start, 90.0, 140.0)
        assertEquals(140.0, Geo.alongTrackDistanceMeters(start, end, beyond), 0.1)

        assertEquals(0.0, Geo.alongTrackDistanceMeters(start, end, start), 1e-6)
    }

    @Test
    fun `destination and distance are inverses`() {
        val start = GeoPoint(51.14, 5.83)
        for (bearing in listOf(0.0, 45.0, 137.5, 180.0, 270.0, 359.0)) {
            val there = Geo.destination(start, bearing, 250.0)
            assertEquals(250.0, Geo.distanceMeters(start, there), 0.01)
            assertEquals(bearing, Geo.initialBearingDegrees(start, there), 0.01)
        }
    }

    @Test
    fun `destination keeps longitude within range across the antimeridian`() {
        val nearDateLine = GeoPoint(0.0, 179.999)
        val crossed = Geo.destination(nearDateLine, 90.0, 1000.0)
        assertTrue(crossed.longitude < 0.0)
        assertTrue(crossed.longitude > -180.0)
    }
}
