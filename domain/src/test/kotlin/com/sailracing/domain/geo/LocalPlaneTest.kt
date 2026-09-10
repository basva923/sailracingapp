package com.sailracing.domain.geo

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalPlaneTest {

    private val origin = GeoPoint(51.14, 5.83)
    private val plane = LocalPlane(origin)

    @Test
    fun `the plane is metres east and north of its origin`() {
        assertEquals(PlanePosition(0.0, 0.0), plane.toPlane(origin))
        val northEast = plane.toPlane(Geo.destination(Geo.destination(origin, 0.0, 300.0), 90.0, 100.0))
        assertEquals(100.0, northEast.eastMeters, 0.5)
        assertEquals(300.0, northEast.northMeters, 0.5)
        val southWest = plane.toPlane(Geo.destination(origin, 225.0, 141.42))
        assertEquals(-100.0, southWest.eastMeters, 0.5)
        assertEquals(-100.0, southWest.northMeters, 0.5)
    }

    @Test
    fun `positions convert back to the same place`() {
        for (position in listOf(PlanePosition(), PlanePosition(-350.0, 1200.0), PlanePosition(800.0, -400.0))) {
            val back = plane.toPlane(plane.toGeo(position))
            assertEquals(position.eastMeters, back.eastMeters, 1e-6)
            assertEquals(position.northMeters, back.northMeters, 1e-6)
        }
        // A plane is only ever the water around its own origin.
        assertEquals(origin, plane.origin)
    }

    @Test
    fun `longitude differences wrap across the antimeridian and latitude is clamped at the pole`() {
        val antimeridian = LocalPlane(GeoPoint(0.0, 179.999))
        val east = antimeridian.toPlane(GeoPoint(0.0, -179.999)).eastMeters
        assertEquals(222.4, east, 1.0)
        assertEquals(-179.999, antimeridian.toGeo(PlanePosition(east, 0.0)).longitude, 1e-6)
        assertEquals(90.0, plane.toGeo(PlanePosition(0.0, 1e9)).latitude)
    }

    @Test
    fun `positions add, subtract, measure and interpolate`() {
        val a = PlanePosition(10.0, 20.0)
        val b = PlanePosition(13.0, 24.0)
        assertEquals(PlanePosition(23.0, 44.0), a + b)
        assertEquals(PlanePosition(3.0, 4.0), b - a)
        assertEquals(5.0, a.distanceTo(b))
        assertEquals(0.0, a.distanceTo(a))
        assertEquals(PlanePosition(11.5, 22.0), a.towards(b, 0.5))
        assertEquals(b, a.towards(b, 1.0))
        assertEquals(PlanePosition(16.0, 28.0), a.towards(b, 2.0))
    }

    @Test
    fun `bearings are compass degrees from north`() {
        val here = PlanePosition(100.0, 100.0)
        assertEquals(0.0, here.bearingTo(PlanePosition(100.0, 200.0)))
        assertEquals(90.0, here.bearingTo(PlanePosition(200.0, 100.0)))
        assertEquals(180.0, here.bearingTo(PlanePosition(100.0, 0.0)))
        assertEquals(270.0, here.bearingTo(PlanePosition(0.0, 100.0)))
        assertEquals(45.0, here.bearingTo(PlanePosition(200.0, 200.0)))
        assertEquals(315.0, here.bearingTo(PlanePosition(0.0, 200.0)))
        // Nowhere to go: north, arbitrarily but never a NaN.
        assertEquals(0.0, here.bearingTo(here))
    }
}
