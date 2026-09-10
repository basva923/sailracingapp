package com.sailracing.domain.sketch

import com.sailracing.domain.course.GridSettings
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.geo.PlanePosition
import com.sailracing.domain.wind.WindSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CourseSketchFormatTest {

    private val drawn = CourseSketch(
        name = "Pressure on the left",
        origin = GeoPoint(51.14, 5.83),
        wind = WindSettings(directionDegrees = 225, tackAngleDegrees = 42, downwindAngleDegrees = 150),
        pinEnd = PlanePosition(-60.0, 0.0),
        boatEnd = PlanePosition(60.0, 0.0),
        windwardMark = PlanePosition(0.0, 600.0),
        track = listOf(SketchPoint(PlanePosition(-55.0, 4.0), 3.1), SketchPoint(PlanePosition(-48.0, 18.0), 2.7)),
    ).let { it.copy(settings = it.settings.copy(course = it.settings.course.copy(grid = GridSettings(20.0)))) }

    @Test
    fun `a sketch survives being written down and read back`() {
        val text = CourseSketchFormat.write(drawn)
        assertTrue(text.startsWith("${CourseSketchFormat.HEADER}\n"))
        val read = CourseSketchFormat.read(text)
        assertEquals(drawn.name, read.name)
        assertEquals(drawn.origin, read.origin)
        assertEquals(drawn.wind, read.wind)
        assertEquals(drawn.pinEnd, read.pinEnd)
        assertEquals(drawn.boatEnd, read.boatEnd)
        assertEquals(drawn.windwardMark, read.windwardMark)
        assertEquals(drawn.track, read.track)
        assertEquals(20.0, read.settings.course.grid.cellSizeMeters)
        assertEquals(drawn.settings.course.raceLine.runs, read.settings.course.raceLine.runs)
        // And again, so that what it writes of what it read is the same text.
        assertEquals(text, CourseSketchFormat.write(read))
    }

    @Test
    fun `an empty course writes the little there is to say about it`() {
        val text = CourseSketchFormat.write(CourseSketch())
        assertEquals(
            """
            sketch 1
            name Untitled course
            origin 52.0000000 5.0000000
            wind 0 45 140
            runs 16

            """.trimIndent(),
            text,
        )
        val read = CourseSketchFormat.read(text)
        assertNull(read.pinEnd)
        assertNull(read.boatEnd)
        assertNull(read.windwardMark)
        assertNull(read.settings.course.grid.cellSizeMeters)
        assertTrue(read.track.isEmpty())
    }

    @Test
    fun `comments, blank lines and keywords from a later version are sailed past`() {
        val sketch = CourseSketchFormat.read(
            """
            sketch 1
            # drawn on the train

            name A later sketch
            tide 0.4 270
            point 0.0 0.0 3.00
            """.trimIndent(),
        )
        assertEquals("A later sketch", sketch.name)
        assertEquals(listOf(SketchPoint(PlanePosition(0.0, 0.0), 3.0)), sketch.track)
    }

    @Test
    fun `a file that is not a sketch says so`() {
        assertEquals(
            "an empty file is not a sketch",
            assertFailsWith<SketchFormatException> { CourseSketchFormat.read("  \n\n") }.message,
        )
        assertEquals(
            "expected 'sketch 1' on the first line, found 'sketch 9'",
            assertFailsWith<SketchFormatException> { CourseSketchFormat.read("sketch 9\nname x") }.message,
        )
    }

    @Test
    fun `a statement that is missing a value or is not a number says which`() {
        assertEquals(
            "'mark 0.0' needs 3 values",
            assertFailsWith<SketchFormatException> { CourseSketchFormat.read("sketch 1\nmark 0.0") }.message,
        )
        assertEquals(
            "'north' is not a number, in 'pin north 0.0'",
            assertFailsWith<SketchFormatException> { CourseSketchFormat.read("sketch 1\npin north 0.0") }.message,
        )
    }
}
