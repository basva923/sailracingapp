package com.sailracing.desktop.workbench

import com.sailracing.domain.geo.PlanePosition
import com.sailracing.domain.sketch.CourseSketch
import com.sailracing.domain.sketch.SketchAnalysis
import com.sailracing.domain.sketch.SketchPoint
import com.sailracing.domain.wind.WindSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportTest {

    /** A beat in a north wind, tacked all the way up to a mark 400 m above the line. */
    private val beat = CourseSketch(
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

    private fun report(sketch: CourseSketch) = Report.of(SketchAnalysis.of(sketch))

    @Test
    fun `with nothing to go on it says what to draw`() {
        assertEquals(Report.NOTHING_YET, Report.of(null))
        assertTrue(Report.NOTHING_YET.headline.isNotEmpty())
        assertTrue(Report.NOTHING_YET.lines.isNotEmpty())
    }

    @Test
    fun `a course that has not been sailed has no route`() {
        val story = report(CourseSketch())
        assertEquals("No route yet: the boat has not sailed", story.headline)
        assertTrue(story.lines.any { it == "Nothing sailed yet" })
        assertTrue(story.lines.any { it.contains("of 30 samples so far") }, story.lines.toString())
    }

    @Test
    fun `a drawn beat is reported in the app's own words`() {
        val story = report(beat)
        assertTrue(story.headline.startsWith("Race line: "), story.headline)
        assertTrue(story.headline.endsWith(" on a bad day"), story.headline)
        assertTrue(story.lines.any { it.startsWith("Wind ") && it.contains("tacking through 90°") }, story.lines.toString())
        assertTrue(story.lines.any { it.startsWith("Track ") && it.contains("close-hauled") }, story.lines.toString())
        assertTrue(story.lines.any { it.startsWith("Flyer: ") || it.startsWith("No flyer: ") }, story.lines.toString())
        assertTrue(story.lines.any { it.contains("SIDES") || it.contains("GO LEFT") || it.contains("GO RIGHT") }, story.lines.toString())
        assertTrue(story.lines.any { it.startsWith("Area ") && it.contains("wind up") }, story.lines.toString())
        assertTrue(story.lines.any { it.startsWith("Mark 400 m at 000°") }, story.lines.toString())
    }

    @Test
    fun `the wind it judged by is the one it measured, once the track has said enough`() {
        // A long beat measures its own wind and says so; a short one is still going on the set wind.
        val long = beat.copy(track = beat.track + longBeat())
        val story = Report.of(SketchAnalysis.of(long))
        assertTrue(story.lines.any { it.contains("measured off the track") }, story.lines.toString())
        assertTrue(story.lines.any { it.contains("you set 000°") }, story.lines.toString())
    }

    @Test
    fun `a mark is measured from whatever there is of a start line`() {
        assertTrue(
            report(beat.copy(windwardMark = null)).lines
                .any { it == "Mark: the top of the racing area, until you lay one" },
        )
        // One end of the line is enough to measure from; none at all, and it can only say the mark is there.
        assertTrue(report(beat.copy(boatEnd = null)).lines.any { it.startsWith("Mark ") && it.contains("from the start line") })
        assertTrue(report(beat.copy(pinEnd = null)).lines.any { it.startsWith("Mark ") })
        assertTrue(
            report(beat.copy(pinEnd = null, boatEnd = null)).lines
                .any { it == "Mark laid, without a start line to measure it from" },
        )
    }

    /** Enough close-hauled sailing for the app to stop believing the wind it was told about. */
    private fun longBeat(): List<SketchPoint> {
        val points = mutableListOf<SketchPoint>()
        var position = PlanePosition(-10.0, 230.0)
        repeat(8) { leg ->
            val across = if (leg % 2 == 0) 60.0 else -60.0
            position = PlanePosition(position.eastMeters + across, position.northMeters + 60.0)
            points += SketchPoint(position, speedMps = 3.0)
        }
        return points
    }
}
