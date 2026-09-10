package com.sailracing.domain.sketch

import com.sailracing.domain.course.CourseModel
import com.sailracing.domain.course.CoursePosition
import com.sailracing.domain.course.RaceLine
import com.sailracing.domain.course.RaceLinePlan
import com.sailracing.domain.geo.PlanePosition
import com.sailracing.domain.race.RaceEngine
import com.sailracing.domain.race.RaceSnapshot
import com.sailracing.domain.race.RaceState
import com.sailracing.domain.strategy.UpwindPlan
import com.sailracing.domain.wind.WindReference

/**
 * What the app makes of a drawn course: the route up the beat, the flyer beside it and the plan for the
 * day, worked out by the very code that runs on the phone.
 *
 * [of] replays the sketch through a [RaceEngine] and takes a snapshot at the moment the boat stops
 * drawing, so nothing here is a second opinion or a simplified copy: [snapshot] is the same
 * [RaceSnapshot] the Race screen is drawn from, and a course that draws a bad line here draws the same
 * bad line on the water.
 *
 * Everything the sketch is drawn in is north-up plane metres and everything the strategy answers in is
 * wind-up course metres; [onPlane] carries the answers back onto the drawing.
 */
public data class SketchAnalysis(
    val sketch: CourseSketch,
    val state: RaceState,
    val snapshot: RaceSnapshot,
) {
    /** The racing area, the wind over it and the lines up it; null while there is nothing sailed to build it from. */
    public val course: CourseModel? get() = snapshot.course

    /** The safe line and the flyer, over all the winds the Monte Carlo tried. */
    public val plan: RaceLinePlan get() = course?.racePlan ?: RaceLinePlan.NONE

    /** The route to the windward mark: the line to sail. */
    public val raceLine: RaceLine get() = plan.safe

    /** The line that pays when the wind is kind, or null when it is the line to sail and there is no bet. */
    public val flyer: RaceLine? get() = if (plan.isEmpty || plan.agree) null else plan.fast

    /** Which tack is lifted and which side of the course pays. */
    public val upwind: UpwindPlan get() = snapshot.plan

    /**
     * The wind the strategy actually judged by: the measured centre of the drawn track's own wind
     * estimates once there are enough of them, and the wind the sailor set until then. A long enough
     * track overrules the set wind, on the workbench exactly as on the water.
     */
    public val reference: WindReference get() = snapshot.windReference

    /** How many of the track's points were close-hauled enough to say something about the wind. */
    public val windSampleCount: Int get() = state.track.points.count { it.upwindWindDegrees != null }

    /** How many seconds of sailing the drawing came to. */
    public val trackPointCount: Int get() = state.track.points.size

    /** Where the course frame's origin - the middle of the line - sits on the drawing. */
    private val frameOrigin: PlanePosition =
        course?.frame?.origin?.let(sketch.plane::toPlane) ?: PlanePosition()

    /** A position the strategy worked out, back on the north-up plane the sketch is drawn on. */
    public fun onPlane(position: CoursePosition): PlanePosition =
        frameOrigin + (course?.frame?.toPlane(position) ?: PlanePosition())

    /** A whole line the strategy worked out, back on the drawing. */
    public fun onPlane(line: RaceLine): List<PlanePosition> = line.points.map(::onPlane)

    public companion object {
        /**
         * The app's answer to [sketch]: every event the drawing implies, fed to a fresh race engine, and
         * the snapshot taken at the last fix so that the boat's position is as fresh as the strategy needs.
         */
        public fun of(sketch: CourseSketch): SketchAnalysis {
            val engine = RaceEngine()
            for (event in SketchReplay.events(sketch)) engine.dispatch(event)
            val now = engine.state.navigation.lastFix?.timestampMillis ?: 0L
            return SketchAnalysis(sketch, engine.state, engine.snapshot(now))
        }
    }
}
