package com.sailracing.domain.startline

import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StartLineCalculatorTest {

    // A 100 m line running west -> east; the wind blows from the north, so the course is north of the line.
    private val pin = GeoPoint(51.14, 5.83)
    private val boat = Geo.destination(pin, 90.0, 100.0)
    private val line = StartLine(pin, boat)
    private val center = Geo.destination(pin, 90.0, 50.0)
    private val northWind = 0.0

    @Test
    fun `incomplete line has no solution`() {
        assertNull(StartLineCalculator.solve(StartLine(pinEnd = pin), center, northWind))
        assertNull(StartLineCalculator.solve(StartLine(boatEnd = boat), center, northWind))
    }

    @Test
    fun `position below the line is on the pre-start side`() {
        val position = Geo.destination(center, 180.0, 50.0)
        val solution = assertNotNull(StartLineCalculator.solve(line, position, northWind))
        assertEquals(50.0, solution.distanceMeters, 0.1)
        assertEquals(50.0, solution.perpendicularDistanceMeters, 0.1)
        assertEquals(0.5, solution.alongFraction, 0.01)
        assertEquals(LineSide.PRE_START, solution.side)
        assertTrue(solution.isBetweenEnds)
    }

    @Test
    fun `position above the line is on the course side`() {
        val position = Geo.destination(center, 0.0, 20.0)
        val solution = assertNotNull(StartLineCalculator.solve(line, position, northWind))
        assertEquals(20.0, solution.distanceMeters, 0.1)
        assertEquals(LineSide.COURSE, solution.side)
    }

    @Test
    fun `side follows the wind direction`() {
        val below = Geo.destination(center, 180.0, 20.0)
        val southWind = 180.0
        assertEquals(LineSide.COURSE, assertNotNull(StartLineCalculator.solve(line, below, southWind)).side)
        // Reversing the ends must not change the answer.
        val reversed = StartLine(boat, pin)
        assertEquals(LineSide.PRE_START, assertNotNull(StartLineCalculator.solve(reversed, below, northWind)).side)
        assertEquals(LineSide.COURSE, assertNotNull(StartLineCalculator.solve(reversed, below, southWind)).side)
    }

    @Test
    fun `side is unknown without a wind direction`() {
        val below = Geo.destination(center, 180.0, 20.0)
        assertEquals(LineSide.UNKNOWN, assertNotNull(StartLineCalculator.solve(line, below, null)).side)
    }

    @Test
    fun `beyond the pin end the distance is measured to the pin`() {
        val position = Geo.destination(Geo.destination(pin, 270.0, 30.0), 180.0, 40.0)
        val solution = assertNotNull(StartLineCalculator.solve(line, position, northWind))
        assertEquals(50.0, solution.distanceMeters, 0.1)
        assertEquals(40.0, solution.perpendicularDistanceMeters, 0.1)
        assertTrue(solution.alongFraction < 0.0)
        assertFalse(solution.isBetweenEnds)
    }

    @Test
    fun `beyond the boat end the distance is measured to the boat`() {
        val position = Geo.destination(Geo.destination(boat, 90.0, 30.0), 180.0, 40.0)
        val solution = assertNotNull(StartLineCalculator.solve(line, position, northWind))
        assertEquals(50.0, solution.distanceMeters, 0.1)
        assertTrue(solution.alongFraction > 1.0)
    }

    @Test
    fun `degenerate line behaves like a single mark`() {
        val point = StartLine(pin, pin)
        val position = Geo.destination(pin, 45.0, 30.0)
        val solution = assertNotNull(StartLineCalculator.solve(point, position, northWind))
        assertEquals(30.0, solution.distanceMeters, 0.1)
        assertEquals(30.0, solution.perpendicularDistanceMeters, 0.1)
        assertEquals(0.0, solution.alongFraction)
        assertEquals(LineSide.UNKNOWN, solution.side)
    }

    @Test
    fun `time to line needs a positive speed`() {
        assertEquals(50.0, assertNotNull(StartLineCalculator.timeToLineSeconds(100.0, 2.0)))
        assertNull(StartLineCalculator.timeToLineSeconds(100.0, 0.0))
        assertNull(StartLineCalculator.timeToLineSeconds(100.0, -1.0))
    }

    @Test
    fun `time to kill is positive when early and negative when late`() {
        assertEquals(20.0, StartLineCalculator.timeToKillSeconds(60.0, 40.0))
        assertEquals(-15.0, StartLineCalculator.timeToKillSeconds(30.0, 45.0))
    }

    @Test
    fun `clamp distance caps the value`() {
        assertEquals(999.0, StartLineCalculator.clampDistance(5000.0, 999.0))
        assertEquals(12.0, StartLineCalculator.clampDistance(12.0, 999.0))
    }
}
