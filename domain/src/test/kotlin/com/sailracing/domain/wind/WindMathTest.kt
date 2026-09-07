package com.sailracing.domain.wind

import kotlin.test.Test
import kotlin.test.assertEquals

class WindMathTest {

    private val settings = WindSettings(directionDegrees = 0, tackAngleDegrees = 45, downwindAngleDegrees = 140)

    @Test
    fun `wind from a close-hauled heading`() {
        assertEquals(0, WindMath.windFromPortTack(45.4, 45))
        assertEquals(0, WindMath.windFromStarboardTack(315.0, 45))
        assertEquals(325, WindMath.windFromPortTack(10.0, 45))
        assertEquals(5, WindMath.windFromStarboardTack(320.0, 45))
    }

    @Test
    fun `sailing state classifies tack and point of sail`() {
        assertEquals(SailingState(Tack.STARBOARD, PointOfSail.UPWIND, 45.0), WindMath.sailingState(315.0, 0.0))
        assertEquals(SailingState(Tack.PORT, PointOfSail.UPWIND, -45.0), WindMath.sailingState(45.0, 0.0))
        assertEquals(SailingState(Tack.STARBOARD, PointOfSail.DOWNWIND, 135.0), WindMath.sailingState(225.0, 0.0))
        assertEquals(SailingState(Tack.PORT, PointOfSail.DOWNWIND, -135.0), WindMath.sailingState(135.0, 0.0))
        assertEquals(SailingState(Tack.STARBOARD, PointOfSail.DOWNWIND, 180.0), WindMath.sailingState(180.0, 0.0))
        assertEquals(SailingState(Tack.STARBOARD, PointOfSail.UPWIND, 0.0), WindMath.sailingState(0.0, 0.0))
        assertEquals(SailingState(Tack.PORT, PointOfSail.UPWIND, -90.0), WindMath.sailingState(90.0, 0.0))
    }

    @Test
    fun `estimated wind is recovered on every tack and point of sail`() {
        assertEquals(0.0, WindMath.estimatedWindDirection(315.0, settings))
        assertEquals(0.0, WindMath.estimatedWindDirection(45.0, settings))
        assertEquals(5.0, WindMath.estimatedWindDirection(320.0, settings))
        assertEquals(355.0, WindMath.estimatedWindDirection(40.0, settings))
        assertEquals(0.0, WindMath.estimatedWindDirection(220.0, settings))
        assertEquals(0.0, WindMath.estimatedWindDirection(140.0, settings))
        assertEquals(10.0, WindMath.estimatedWindDirection(230.0, settings))
    }

    @Test
    fun `the tack is judged against the reference wind when one is given`() {
        // Against the set wind (0) a heading of 80 would be a reach; against the measured 25 it is close-hauled on port.
        assertEquals(35.0, WindMath.estimatedWindDirection(80.0, settings, referenceDegrees = 25.0))
        assertEquals(25.0, WindMath.estimatedWindDirection(340.0, settings, referenceDegrees = 25.0))
        assertEquals(TargetHeadings(340.0, 70.0, 245.0, 165.0), WindMath.targetHeadings(settings, referenceDegrees = 25.0))
        assertEquals(340.0, WindMath.targetHeading(settings, WindMath.sailingState(300.0, 25.0), referenceDegrees = 25.0))
    }

    @Test
    fun `shift is positive for a veer`() {
        assertEquals(5.0, WindMath.shiftDegrees(0.0, 5.0))
        assertEquals(-5.0, WindMath.shiftDegrees(0.0, 355.0))
    }

    @Test
    fun `target headings`() {
        assertEquals(TargetHeadings(315.0, 45.0, 220.0, 140.0), WindMath.targetHeadings(settings))
        assertEquals(315.0, WindMath.targetHeading(settings, WindMath.sailingState(300.0, 0.0)))
        assertEquals(45.0, WindMath.targetHeading(settings, WindMath.sailingState(60.0, 0.0)))
        assertEquals(220.0, WindMath.targetHeading(settings, WindMath.sailingState(200.0, 0.0)))
        assertEquals(140.0, WindMath.targetHeading(settings, WindMath.sailingState(160.0, 0.0)))
    }

    @Test
    fun `heading error is positive when pointing right of target`() {
        assertEquals(5.0, WindMath.headingErrorDegrees(320.0, 315.0))
        assertEquals(-10.0, WindMath.headingErrorDegrees(35.0, 45.0))
        assertEquals(2.0, WindMath.headingErrorDegrees(1.0, 359.0))
    }

    @Test
    fun `velocity made good is positive on the useful course`() {
        assertEquals(1.0, WindMath.velocityMadeGood(2.0, SailingState(Tack.STARBOARD, PointOfSail.UPWIND, 60.0)), 1e-9)
        assertEquals(2.0, WindMath.velocityMadeGood(2.0, SailingState(Tack.STARBOARD, PointOfSail.DOWNWIND, 180.0)), 1e-9)
        assertEquals(1.0, WindMath.velocityMadeGood(2.0, SailingState(Tack.PORT, PointOfSail.DOWNWIND, -120.0)), 1e-9)
    }
}
