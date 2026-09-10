package com.sailracing.domain.sketch

import com.sailracing.domain.course.CoursePosition
import com.sailracing.domain.geo.PlanePosition
import com.sailracing.domain.wind.WindSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SketchAnalysisTest {

    /** A beat in a north wind: a line 100 m wide, a mark 400 m up it, and a boat that tacked up to it. */
    private val beat = CourseSketch(
        name = "A beat in a northerly",
        wind = WindSettings(directionDegrees = 0, tackAngleDegrees = 45),
        pinEnd = PlanePosition(-50.0, 0.0),
        boatEnd = PlanePosition(50.0, 0.0),
        windwardMark = PlanePosition(0.0, 400.0),
        track = listOf(
            PlanePosition(0.0, 0.0),
            PlanePosition(-60.0, 60.0),
            PlanePosition(0.0, 120.0),
            PlanePosition(-60.0, 180.0),
            PlanePosition(-10.0, 230.0),
        ).map { SketchPoint(it, speedMps = 3.0) },
    )

    @Test
    fun `the drawn beat comes back as a route from the boat to the mark`() {
        val analysis = SketchAnalysis.of(beat)
        val course = assertNotNull(analysis.course)
        assertFalse(analysis.plan.isEmpty)

        // The route starts where the drawing stopped and finishes at the mark that was drawn.
        val route = analysis.onPlane(analysis.raceLine)
        assertTrue(route.size >= 2)
        assertEquals(0.0, route.first().distanceTo(assertNotNull(beat.boat)), 1.0)
        assertEquals(0.0, route.last().distanceTo(assertNotNull(beat.windwardMark)), 1.0)
        assertTrue(course.windwardMarkIsSet)
        assertTrue(analysis.raceLine.seconds > 0.0)

        // Every board of it is sailed close-hauled, so none of it points at the wind or away from the mark.
        assertTrue(route.zipWithNext().all { (from, to) -> to.northMeters > from.northMeters - 1.0 })
    }

    @Test
    fun `a beat drawn at the tack angle measures the wind it was drawn in`() {
        val analysis = SketchAnalysis.of(beat)
        assertEquals(0.0, analysis.reference.directionDegrees, 1.0)
        assertTrue(analysis.windSampleCount > 0, "a close-hauled track says what the wind was")
        assertTrue(analysis.trackPointCount > analysis.windSampleCount, "the samples at the tacks are thrown away")
        assertEquals(analysis.state.track.points.size, analysis.trackPointCount)
        assertEquals(analysis.snapshot, analysis.snapshot.copy())
    }

    @Test
    fun `a shifty course offers a flyer and a settled one does not`() {
        // A course whose two sides were sailed in different winds is a course with a side to gamble on.
        val analysis = SketchAnalysis.of(beat)
        val flyer = analysis.flyer
        if (flyer == null) {
            assertTrue(analysis.plan.agree)
        } else {
            assertTrue(flyer.points != analysis.raceLine.points)
        }
    }

    @Test
    fun `nothing drawn is nothing to advise on`() {
        val analysis = SketchAnalysis.of(CourseSketch())
        assertNull(analysis.course)
        assertTrue(analysis.plan.isEmpty)
        assertTrue(analysis.raceLine.isEmpty)
        assertNull(analysis.flyer)
        assertEquals(0, analysis.windSampleCount)
        assertEquals(0, analysis.trackPointCount)
        assertEquals(PlanePosition(), analysis.onPlane(CoursePosition(10.0, 20.0)))
        assertEquals(emptyList(), analysis.onPlane(analysis.raceLine))
        assertEquals(0.0, analysis.upwind.referenceWindDegrees)
    }
}
