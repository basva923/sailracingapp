package com.sailracing.desktop.workbench

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.sketch.CourseSketchFormat
import com.sailracing.domain.sketch.SketchAnalysis
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The courses in `desktop/courses`, opened and planned exactly as the workbench opens them. They are the
 * worked examples the tool ships with, and a course that no longer plans a route is worth hearing about
 * from a test rather than from a window that has stopped drawing one.
 */
class ExampleCoursesTest {

    private fun open(name: String): SketchAnalysis {
        val file = File("courses/$name.course")
        assertTrue(file.isFile, "${file.absolutePath} is missing")
        return SketchAnalysis.of(CourseSketchFormat.read(file.readText()))
    }

    @Test
    fun `an even breeze plans a route from the boat to the mark`() {
        val analysis = open("even-breeze")
        assertEquals("Even breeze", analysis.sketch.name)
        assertTrue(analysis.plan.isEmpty.not(), "a beat that was sailed has a route up it")

        val route = analysis.onPlane(analysis.raceLine)
        assertEquals(0.0, route.first().distanceTo(assertNotNull(analysis.sketch.boat)), 1.0)
        assertEquals(0.0, route.last().distanceTo(assertNotNull(analysis.sketch.windwardMark)), 1.0)
        assertTrue(analysis.raceLine.seconds > 0.0)

        // Sailed in a steady wind from the north, that is the wind it measured.
        assertTrue(analysis.reference.isMeasured)
        assertTrue(abs(Angles.signedDifference(0.0, analysis.reference.directionDegrees)) < 3.0, "${analysis.reference}")
    }

    @Test
    fun `a course sailed with the right side veered measures the veer`() {
        val analysis = open("right-hand-veer")
        assertTrue(analysis.reference.isMeasured)
        // Half the beat was sailed in a wind twelve degrees to the right of the other half.
        val veer = Angles.signedDifference(0.0, analysis.reference.directionDegrees)
        assertTrue(veer in 2.0..11.0, "the mean of the two sides should sit between them, was $veer")
        val sides = analysis.upwind.sides
        assertTrue(sides.leftSamples > 0 && sides.rightSamples > 0)
        assertEquals(true, (sides.windDifferenceDegrees ?: 0.0) > 0.0, "the right was the veered side")
    }
}
