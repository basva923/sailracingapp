package com.sailracing.desktop.workbench

import com.sailracing.domain.geo.PlanePosition
import com.sailracing.domain.sketch.CourseSketch
import com.sailracing.domain.sketch.SketchAnalysis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WorkbenchTest {

    private val fresh = Workbench()

    @Test
    fun `a fresh workbench has an empty course, a pen and nothing to take back`() {
        assertEquals(CourseSketch(), fresh.sketch)
        assertEquals(SketchTool.TRACK, fresh.tool)
        assertEquals(CourseSketch.DEFAULT_BOAT_SPEED_MPS, fresh.penSpeedMps)
        assertTrue(fresh.needsPlanning)
        assertFalse(fresh.canUndo)
        assertSame(fresh, fresh.undo())
        assertTrue(fresh.drawn().isEmpty())
    }

    @Test
    fun `dragging with the track tool sails the boat`() {
        val sailed = fresh
            .touch(PlanePosition(0.0, 0.0))
            .drag(PlanePosition(0.0, 10.0))
            .drag(PlanePosition(0.0, 20.0))
        assertEquals(3, sailed.sketch.track.size)
        assertEquals(PlanePosition(0.0, 20.0), sailed.sketch.boat)
        assertTrue(sailed.sketch.track.all { it.speedMps == fresh.penSpeedMps })
        // One press, one thing to take back: a stroke, not every point of it.
        assertEquals(1, sailed.history.size)
        assertEquals(CourseSketch(), sailed.undo().sketch)
    }

    @Test
    fun `a drag records a point a second of sailing apart`() {
        val dawdling = fresh
            .touch(PlanePosition(0.0, 0.0))
            .drag(PlanePosition(0.0, 1.0))
            .drag(PlanePosition(0.0, 2.0))
        assertEquals(1, dawdling.sketch.track.size, "a mouse that has hardly moved is not more track")
        val sailing = dawdling.drag(PlanePosition(0.0, 4.0))
        assertEquals(2, sailing.sketch.track.size)
        // A slower boat covers less ground a second, so it records more finely.
        val slow = fresh.withPenSpeed(1.0).touch(PlanePosition(0.0, 0.0)).drag(PlanePosition(0.0, 1.5))
        assertEquals(2, slow.sketch.track.size)
    }

    @Test
    fun `the marks are laid where they are clicked and dragged where they are wanted`() {
        val laid = fresh
            .withTool(SketchTool.PIN).touch(PlanePosition(-50.0, 0.0))
            .withTool(SketchTool.COMMITTEE).touch(PlanePosition(50.0, 0.0))
            .withTool(SketchTool.MARK).touch(PlanePosition(0.0, 400.0)).drag(PlanePosition(10.0, 420.0))
        assertEquals(PlanePosition(-50.0, 0.0), laid.sketch.pinEnd)
        assertEquals(PlanePosition(50.0, 0.0), laid.sketch.boatEnd)
        assertEquals(PlanePosition(10.0, 420.0), laid.sketch.windwardMark)
        assertEquals(3, laid.history.size, "a drag carries on the gesture the click began")
        assertEquals(listOf(PlanePosition(-50.0, 0.0), PlanePosition(50.0, 0.0), PlanePosition(10.0, 420.0)), laid.drawn())
        // Taking back the mark leaves the line where it was laid.
        val undone = laid.undo()
        assertNull(undone.sketch.windwardMark)
        assertEquals(PlanePosition(50.0, 0.0), undone.sketch.boatEnd)
    }

    @Test
    fun `the wind and the search are dials on the course itself`() {
        val set = fresh.withWindDirection(370).withTackAngle(90).withCellSize(20.0).withRuns(32).withName("Shifty")
        assertEquals(10, set.sketch.wind.directionDegrees, "a compass wraps")
        assertEquals(80, set.sketch.wind.tackAngleDegrees, "a dinghy does not point that high")
        assertEquals(20.0, set.sketch.settings.course.grid.cellSizeMeters)
        assertEquals(32, set.sketch.settings.course.raceLine.runs)
        assertEquals("Shifty", set.sketch.name)
        assertNull(set.withCellSize(null).sketch.settings.course.grid.cellSizeMeters)
        // A pen that is not a boat is brought back to one.
        assertEquals(Workbench.MIN_PEN_SPEED_MPS, fresh.withPenSpeed(0.0).penSpeedMps)
        assertEquals(Workbench.MAX_PEN_SPEED_MPS, fresh.withPenSpeed(100.0).penSpeedMps)
    }

    @Test
    fun `clearing the track keeps the course, and clearing everything keeps the day`() {
        val drawn = fresh
            .withWindDirection(225)
            .withTool(SketchTool.MARK).touch(PlanePosition(0.0, 400.0))
            .withTool(SketchTool.TRACK).touch(PlanePosition(0.0, 0.0)).drag(PlanePosition(0.0, 40.0))

        val unsailed = drawn.clearTrack()
        assertTrue(unsailed.sketch.track.isEmpty())
        assertEquals(PlanePosition(0.0, 400.0), unsailed.sketch.windwardMark)

        val emptied = drawn.clearAll()
        assertTrue(emptied.sketch.track.isEmpty())
        assertNull(emptied.sketch.windwardMark)
        assertEquals(225, emptied.sketch.wind.directionDegrees, "the day's wind is not a thing to redraw")

        // Clearing the water is the one thing worth most being able to take back.
        assertEquals(drawn.sketch, emptied.undo().sketch)
        assertEquals(drawn.sketch, unsailed.undo().sketch)
        // Turning a dial is not: a dial turns back.
        assertEquals(drawn.history, drawn.withRuns(32).history)
    }

    @Test
    fun `an answer belongs to the course it was asked about`() {
        val drawn = fresh.touch(PlanePosition(0.0, 0.0)).drag(PlanePosition(0.0, 40.0))
        val answered = drawn.planned(SketchAnalysis.of(drawn.sketch))
        assertFalse(answered.needsPlanning)
        // Anything at all about the course, and the answer is no longer about it.
        assertTrue(answered.drag(PlanePosition(0.0, 80.0)).needsPlanning)
        assertTrue(answered.withWindDirection(90).needsPlanning)
        assertTrue(answered.clearTrack().needsPlanning)
        assertTrue(answered.undo().needsPlanning)
        // Choosing another tool is not a change to the course.
        assertFalse(answered.withTool(SketchTool.MARK).needsPlanning)
        assertFalse(answered.withPenSpeed(2.0).needsPlanning)
    }

    @Test
    fun `a course read from a file starts afresh`() {
        val opened = fresh.touch(PlanePosition(0.0, 0.0)).opened(CourseSketch(name = "From a file"))
        assertEquals("From a file", opened.sketch.name)
        assertFalse(opened.canUndo, "the course before it was opened is not somewhere to go back to")
        assertTrue(opened.needsPlanning)
    }

    @Test
    fun `a gesture that laid nothing down is not a step to take back`() {
        val sailed = fresh.touch(PlanePosition(0.0, 0.0)).drag(PlanePosition(0.0, 40.0))
        val answered = sailed.planned(SketchAnalysis.of(sailed.sketch))
        // Pressing again where the boat already is adds no track, so there is nothing new to undo and
        // nothing to re-plan either.
        val again = answered.touch(PlanePosition(0.0, 40.5))
        assertSame(answered, again)
        assertEquals(1, again.history.size)
        // The same goes for dropping a mark exactly where it already lies.
        val marked = answered.withTool(SketchTool.MARK).touch(PlanePosition(0.0, 400.0))
        assertSame(marked, marked.drag(PlanePosition(0.0, 400.0)))
    }

    @Test
    fun `only so many gestures can be taken back`() {
        var workbench = fresh
        repeat(Workbench.UNDO_DEPTH + 20) { step ->
            workbench = workbench.touch(PlanePosition(0.0, step * 10.0))
        }
        assertEquals(Workbench.UNDO_DEPTH, workbench.history.size)
    }
}
