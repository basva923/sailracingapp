package com.sailracing.domain.course

import com.sailracing.domain.geo.GeoPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteFinderTest {

    private val frame = CourseFrame(GeoPoint(51.14, 5.83), windDirectionDegrees = 0.0)
    private val spec = GridSpec.covering(listOf(CoursePosition(-100.0, 0.0), CoursePosition(100.0, 600.0)))
    private val mark = CoursePosition(0.0, 600.0)

    private fun column(column: Int, wind: Double): List<TrackPoint> = (0 until spec.rows).flatMap { row ->
        val centre = spec.center(GridCell(column, row))
        List(3) { TrackPoint(0L, frame.toGeo(centre), speedMps = 3.0, headingDegrees = 0.0, upwindWindDegrees = wind) }
    }

    private val even = WindField.build(TrackGrid.build(Track(), frame, spec, mark), referenceDegrees = 0.0)

    @Test
    fun `speed made good along a course`() {
        assertEquals(cos(Math.toRadians(45.0)), RouteFinder.speedFactor(0.0, 45, 140), 1e-9)
        assertEquals(cos(Math.toRadians(45.0)) / cos(Math.toRadians(30.0)), RouteFinder.speedFactor(-30.0, 45, 140), 1e-9)
        assertEquals(1.0, RouteFinder.speedFactor(45.0, 45, 140))
        assertEquals(1.0, RouteFinder.speedFactor(90.0, 45, 140))
        assertEquals(1.0, RouteFinder.speedFactor(140.0, 45, 140))
        assertEquals(cos(Math.toRadians(40.0)) / cos(Math.toRadians(20.0)), RouteFinder.speedFactor(160.0, 45, 140), 1e-9)
        assertEquals(cos(Math.toRadians(40.0)), RouteFinder.speedFactor(180.0, 45, 140), 1e-9)
    }

    @Test
    fun `in an even wind the line is straight, upwind and downwind`() {
        assertEquals(GridSpec(-100.0, 0.0, 4, 12, 50.0), spec)
        val up = RouteFinder.route(even, CoursePosition(0.0, 0.0), mark, 45, 140)
        assertEquals(CoursePosition(0.0, 0.0), up.first())
        assertEquals(mark, up.last())
        assertTrue(up.size > 2)
        assertTrue(up.all { abs(it.acrossMeters) <= 13.0 }, "route $up")
        assertTrue(up.zipWithNext().all { (a, b) -> b.upwindMeters >= a.upwindMeters }, "route $up")

        val down = RouteFinder.route(even, mark, CoursePosition(0.0, 0.0), 45, 140)
        assertEquals(mark, down.first())
        assertEquals(CoursePosition(0.0, 0.0), down.last())
        assertTrue(down.all { abs(it.acrossMeters) <= 13.0 }, "route $down")
    }

    @Test
    fun `the line bends into the side where the wind is shifted`() {
        val veeredRight = WindField.build(TrackGrid.build(Track(column(0, 0.0) + column(3, 15.0)), frame, spec, mark), referenceDegrees = 0.0)
        val route = RouteFinder.route(veeredRight, CoursePosition(0.0, 0.0), mark, 45, 140)
        assertEquals(CoursePosition(0.0, 0.0), route.first())
        assertEquals(mark, route.last())
        val middle = route.subList(route.size / 4, route.size * 3 / 4)
        assertTrue(middle.map { it.acrossMeters }.average() > 25.0, "route $route")
        assertTrue(route.all { it.acrossMeters in spec.leftMeters..spec.rightMeters && it.upwindMeters in spec.bottomMeters..spec.topMeters })

        val backedLeft = WindField.build(TrackGrid.build(Track(column(0, 345.0) + column(3, 0.0)), frame, spec, mark), referenceDegrees = 0.0)
        val left = RouteFinder.route(backedLeft, CoursePosition(0.0, 0.0), mark, 45, 140)
        assertTrue(left.subList(left.size / 4, left.size * 3 / 4).map { it.acrossMeters }.average() < -25.0, "route $left")
    }

    @Test
    fun `short and degenerate routes`() {
        val from = CoursePosition(0.0, 0.0)
        assertEquals(listOf(from, CoursePosition(5.0, 5.0)), RouteFinder.route(even, from, CoursePosition(5.0, 5.0), 45, 140))
        // Adjacent search cells: nothing to smooth.
        assertEquals(listOf(from, CoursePosition(0.0, 30.0)), RouteFinder.route(even, from, CoursePosition(0.0, 30.0), 45, 140))
        // Positions outside the area are taken from its nearest cells; the ends stay exact.
        val outside = RouteFinder.route(even, CoursePosition(-500.0, -500.0), CoursePosition(500.0, 5000.0), 45, 140)
        assertEquals(CoursePosition(-500.0, -500.0), outside.first())
        assertEquals(CoursePosition(500.0, 5000.0), outside.last())
    }
}
