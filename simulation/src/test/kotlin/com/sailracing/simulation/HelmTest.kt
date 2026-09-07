package com.sailracing.simulation

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.wind.Tack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HelmTest {

    private val center = GeoPoint(51.14, 5.83)
    private val course = RaceCourse.square(center, windDirectionDegrees = 0.0)
    private val polar = BoatPolar()

    private fun context(position: GeoPoint, time: Double = 0.0, wind: Double = 0.0, heading: Double = 0.0) =
        HelmContext(BoatState(time, position, heading, 3.0), wind, course, polar)

    private fun assertHeading(expected: Double, actual: Double) =
        assertEquals(0.0, Angles.signedDifference(expected, actual), 0.01, "expected heading $expected but was $actual")

    @Test
    fun `simple helms`() {
        val south = Geo.destination(center, 180.0, 100.0)
        assertHeading(0.0, GoTo(center).steer(context(south)))
        assertEquals(350.0, HoldHeading(-10.0).steer(context(south)))
        assertEquals(100.0, HoldWindAngle(90.0).steer(context(south, wind = 10.0)))
        assertEquals(315.0, CloseHauled(Tack.STARBOARD).steer(context(south)))
        assertEquals(45.0, CloseHauled(Tack.PORT).steer(context(south)))
        assertEquals(325.0, CloseHauled(Tack.STARBOARD).steer(context(south, wind = 10.0)))
    }

    @Test
    fun `tack helpers`() {
        assertEquals(Tack.PORT, Tack.STARBOARD.opposite())
        assertEquals(Tack.STARBOARD, Tack.PORT.opposite())
        assertEquals(220.0, downwindHeading(0.0, 140.0, Tack.STARBOARD))
        assertEquals(140.0, downwindHeading(0.0, 140.0, Tack.PORT))
    }

    @Test
    fun `beat sails close-hauled on the current tack and fetches the mark from the layline`() {
        val mark = course.windwardMark
        val helm = BeatTo(mark, Tack.STARBOARD)
        // Directly below the mark, heading 315: stay close-hauled on starboard.
        assertEquals(315.0, helm.steer(context(Geo.destination(mark, 180.0, 300.0), heading = 315.0)))
        assertEquals(Tack.STARBOARD, helm.tack)
        // Same spot but heading 45: the current tack is port.
        assertEquals(45.0, helm.steer(context(Geo.destination(mark, 180.0, 300.0), heading = 45.0)))
        assertEquals(Tack.PORT, helm.tack)
        // Past the port layline (mark bears 50): point at the mark.
        val onPortLayline = Geo.destination(mark, 230.0, 200.0)
        assertHeading(50.0, helm.steer(context(onPortLayline, heading = 315.0)))
        assertEquals(Tack.PORT, helm.tack)
        // Overstood to the right of the starboard layline: point at the mark on starboard.
        val overstood = Geo.destination(mark, 100.0, 200.0)
        assertHeading(280.0, helm.steer(context(overstood, heading = 45.0)))
        assertEquals(Tack.STARBOARD, helm.tack)
        // With a shifted wind the close-hauled heading follows the wind.
        assertEquals(325.0, helm.steer(context(Geo.destination(mark, 180.0, 300.0), wind = 10.0, heading = 320.0)))
    }

    @Test
    fun `run sails the downwind angle and points at the mark once it can`() {
        val mark = course.leewardMark
        val helm = RunTo(mark, Tack.STARBOARD)
        // Directly above the mark, heading 220: keep running on starboard.
        assertEquals(220.0, helm.steer(context(Geo.destination(mark, 0.0, 300.0), heading = 220.0)))
        assertEquals(Tack.STARBOARD, helm.tack)
        // Heading 140 there: current gybe is port.
        assertEquals(140.0, helm.steer(context(Geo.destination(mark, 0.0, 300.0), heading = 140.0)))
        assertEquals(Tack.PORT, helm.tack)
        // Mark bears 140 (40 degrees left of the downwind axis): point at it, on port.
        val leftLayline = Geo.destination(mark, 320.0, 200.0)
        assertHeading(140.0, helm.steer(context(leftLayline, heading = 220.0)))
        assertEquals(Tack.PORT, helm.tack)
        // Mark only 30 degrees right of the axis: not reachable at the downwind angle yet, keep the gybe.
        val slightlyRight = Geo.destination(mark, 30.0, 200.0)
        assertEquals(140.0, helm.steer(context(slightlyRight, heading = 140.0)))
        assertEquals(Tack.PORT, helm.tack)
        // Mark 45 degrees right of the axis: point at it, on starboard.
        val rightOfAxis = Geo.destination(mark, 45.0, 200.0)
        assertHeading(225.0, helm.steer(context(rightOfAxis, heading = 140.0)))
        assertEquals(Tack.STARBOARD, helm.tack)
    }

    @Test
    fun `timed start shuttles, holds head to wind and goes on time`() {
        val standoff = 60.0
        val below = Geo.destination(center, 180.0, standoff)
        val left = Geo.destination(below, 270.0, standoff)
        val right = Geo.destination(below, 90.0, standoff)
        val helm = TimedStart(gunTimeSeconds = 600.0, standoffMeters = standoff)

        // Starts by heading for the left launch point.
        assertHeading(270.0, helm.steer(context(below, time = 0.0)))
        assertEquals(TimedStart.Phase.SHUTTLE, helm.phase)
        // At the left point with lots of time: turn round to the right point.
        assertHeading(90.0, helm.steer(context(left, time = 20.0)))
        assertHeading(90.0, helm.steer(context(Geo.destination(left, 90.0, 30.0), time = 30.0)))
        // At the right point with lots of time: back to the left.
        assertHeading(270.0, helm.steer(context(right, time = 60.0)))
        // Arriving at the left point with less than a round trip (~90 s) plus the approach (~38 s) left: hold.
        assertEquals(0.0, helm.steer(context(left, time = 490.0)))
        assertEquals(TimedStart.Phase.HOLD, helm.phase)
        assertEquals(10.0, helm.steer(context(left, time = 500.0, wind = 10.0)))
        // 85 m at 2.55 m/s plus 5 s allowance = 38 s: go at 562 s, close-hauled on port towards the centre.
        assertEquals(0.0, helm.steer(context(left, time = 561.0)))
        assertEquals(TimedStart.Phase.HOLD, helm.phase)
        assertEquals(45.0, helm.steer(context(left, time = 563.0)))
        assertEquals(TimedStart.Phase.GO, helm.phase)
        assertEquals(50.0, helm.steer(context(left, time = 570.0, wind = 5.0)))
    }

    @Test
    fun `timed start holds at the right launch point on starboard`() {
        val standoff = 60.0
        val below = Geo.destination(center, 180.0, standoff)
        val left = Geo.destination(below, 270.0, standoff)
        val right = Geo.destination(below, 90.0, standoff)
        val helm = TimedStart(gunTimeSeconds = 200.0, standoffMeters = standoff)
        assertHeading(270.0, helm.steer(context(below, time = 0.0)))
        assertHeading(90.0, helm.steer(context(left, time = 20.0)))
        assertEquals(0.0, helm.steer(context(right, time = 80.0)))
        assertEquals(TimedStart.Phase.HOLD, helm.phase)
        assertEquals(315.0, helm.steer(context(right, time = 170.0)))
        assertEquals(TimedStart.Phase.GO, helm.phase)
        assertTrue(helm.steer(context(right, time = 171.0)) == 315.0)
    }
}
