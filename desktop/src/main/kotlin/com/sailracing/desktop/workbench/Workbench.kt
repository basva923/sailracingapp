package com.sailracing.desktop.workbench

import com.sailracing.domain.geo.PlanePosition
import com.sailracing.domain.sketch.CourseSketch
import com.sailracing.domain.sketch.SketchAnalysis
import com.sailracing.domain.wind.WindSettings

/**
 * The workbench as a value: the course being drawn, the tool in hand, and the answer the strategy last
 * gave for it. Every gesture is a function from one of these to the next, so what the mouse does to a
 * course is something a test can state rather than something only a window can show.
 *
 * The answer is dropped the moment the course changes, because an answer to a course that is no longer
 * on the table is worse than none: [needsPlanning] is what the window watches to know it should ask again.
 *
 * @property penSpeedMps how fast the boat is sailing along the track being drawn. It is the other half of
 *   a course - a beat that is slow on one side and quick on the other is pressure on the other - and it
 *   also sets how finely a drag is recorded: one point per second of sailing, which is the rate the
 *   replay walks the track at anyway.
 * @property history the courses this one came from, newest first, so that a stroke - or a course cleared
 *   off the water by mistake - can be taken back. Turning a dial is not in it: a dial turns back.
 */
data class Workbench(
    val sketch: CourseSketch = CourseSketch(),
    val tool: SketchTool = SketchTool.TRACK,
    val penSpeedMps: Double = CourseSketch.DEFAULT_BOAT_SPEED_MPS,
    val analysis: SketchAnalysis? = null,
    val history: List<CourseSketch> = emptyList(),
) {
    /** True when the strategy has not been asked about the course as it stands now. */
    val needsPlanning: Boolean get() = analysis == null

    val canUndo: Boolean get() = history.isNotEmpty()

    fun withTool(tool: SketchTool): Workbench = copy(tool = tool)

    fun withPenSpeed(speedMps: Double): Workbench = copy(penSpeedMps = speedMps.coerceIn(MIN_PEN_SPEED_MPS, MAX_PEN_SPEED_MPS))

    /**
     * The mouse going down at [position]: the start of a gesture, and the point the last one can be taken
     * back to.
     */
    fun touch(position: PlanePosition): Workbench = remembered(laying(position).sketch)

    /**
     * The mouse moving with the button down. It carries on the gesture [touch] began - another second of
     * track, or the mark dragged to where it is wanted - without leaving another step to undo.
     */
    fun drag(position: PlanePosition): Workbench = laying(position)

    fun withWind(wind: WindSettings): Workbench = changed(sketch.copy(wind = wind))

    fun withWindDirection(degrees: Int): Workbench = withWind(sketch.wind.withDirection(degrees))

    fun withTackAngle(degrees: Int): Workbench = withWind(sketch.wind.withTackAngle(degrees))

    /** How big the squares of the racing area are, or null to let the area choose. */
    fun withCellSize(meters: Double?): Workbench = changed(
        sketch.withCourseSettings { copy(grid = grid.copy(cellSizeMeters = meters)) },
    )

    /** How many winds the race line's Monte Carlo tries. */
    fun withRuns(runs: Int): Workbench = changed(
        sketch.withCourseSettings { copy(raceLine = raceLine.copy(runs = runs)) },
    )

    fun withName(name: String): Workbench = changed(sketch.copy(name = name))

    /** Unsails the course, keeping the line, the mark and the wind. */
    fun clearTrack(): Workbench = remembered(sketch.withoutTrack())

    /** Clears the water entirely, keeping the wind and the settings: a new course on the same day. */
    fun clearAll(): Workbench = remembered(CourseSketch(name = sketch.name, wind = sketch.wind, settings = sketch.settings))

    /** Takes back the last gesture. Nothing to take back leaves the workbench as it is. */
    fun undo(): Workbench =
        if (history.isEmpty()) this else copy(sketch = history.first(), analysis = null, history = history.drop(1))

    /** The strategy's answer to the course as it stands. */
    fun planned(analysis: SketchAnalysis): Workbench = copy(analysis = analysis)

    /** A course read from a file: a fresh start, with nothing behind it to undo. */
    fun opened(sketch: CourseSketch): Workbench = copy(sketch = sketch, analysis = null, history = emptyList())

    /** Everything drawn, so the canvas can be fitted around it. */
    fun drawn(): List<PlanePosition> =
        sketch.track.map { it.position } + listOfNotNull(sketch.pinEnd, sketch.boatEnd, sketch.windwardMark)

    private fun laying(position: PlanePosition): Workbench = when (tool) {
        SketchTool.TRACK -> if (tooCloseToSail(position)) this else changed(sketch.plusPoint(position, penSpeedMps))
        SketchTool.PIN -> changed(sketch.copy(pinEnd = position))
        SketchTool.COMMITTEE -> changed(sketch.copy(boatEnd = position))
        SketchTool.MARK -> changed(sketch.copy(windwardMark = position))
    }

    /** A drag records a point a second of sailing apart; the mouse dawdling in between is not more track. */
    private fun tooCloseToSail(position: PlanePosition): Boolean {
        val last = sketch.track.lastOrNull()?.position ?: return false
        return last.distanceTo(position) < penSpeedMps
    }

    /** Nothing changed is nothing changed: the answer on the screen still belongs to the course on it. */
    private fun changed(sketch: CourseSketch): Workbench =
        if (sketch == this.sketch) this else copy(sketch = sketch, analysis = null)

    /**
     * A change worth being able to take back: something laid on the water, or a course cleared off it.
     * A gesture that laid nothing down - a mouse that has hardly moved, a mark dropped where it already
     * lies - leaves nothing to undo either.
     */
    private fun remembered(sketch: CourseSketch): Workbench {
        val next = changed(sketch)
        return if (next === this) this else next.copy(history = (listOf(this.sketch) + history).take(UNDO_DEPTH))
    }

    companion object {
        /** How many gestures can be taken back. A sketch is small; this is a whole afternoon of them. */
        const val UNDO_DEPTH: Int = 200

        /** Slower than this and the boat is drifting; faster and it is not a dinghy any more. */
        const val MIN_PEN_SPEED_MPS: Double = 0.5
        const val MAX_PEN_SPEED_MPS: Double = 12.0
    }
}
