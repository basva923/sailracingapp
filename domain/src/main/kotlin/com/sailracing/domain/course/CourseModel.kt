package com.sailracing.domain.course

import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.startline.StartLine

/**
 * Everything the course model is built from apart from the track, so an unchanged model can be reused
 * from one snapshot to the next (the clock ticks several times a second, the track grows once a second).
 *
 * @property windwardMark the mark the sailor set, if any; @property boat the latest position.
 */
public data class CourseInputs(
    val frame: CourseFrame,
    val line: StartLine,
    val windwardMark: GeoPoint?,
    val boat: GeoPoint?,
    val tackAngleDegrees: Int,
    val downwindAngleDegrees: Int,
)

/**
 * The course as the map shows it, in the wind-up [CourseFrame] (metres, origin at the start): the racing
 * area that follows the track, the track binned onto it, the wind field over it, the marks, the boat and
 * the fastest line from the boat to the windward mark. All of it is derived, none of it is state.
 *
 * @property windwardMark the set mark, or the middle of the top edge of the area when none is set.
 */
public class CourseModel private constructor(
    public val track: Track,
    public val inputs: CourseInputs,
    public val spec: GridSpec,
    public val grid: TrackGrid,
    public val windField: WindField,
    /** The track for drawing, thinned to at most [MAX_TRACK_POINTS] points. */
    public val trackPositions: List<CoursePosition>,
    public val pinEnd: CoursePosition?,
    public val boatEnd: CoursePosition?,
    public val boat: CoursePosition?,
    public val windwardMark: CoursePosition,
    public val windwardMarkIsSet: Boolean,
    /** The fastest line from the boat to the windward mark; empty without a boat position. */
    public val route: List<CoursePosition>,
) {
    public val frame: CourseFrame get() = inputs.frame

    /** True when this model was built from exactly these inputs (the track by identity: it is only ever replaced). */
    public fun isFor(track: Track, inputs: CourseInputs): Boolean = this.track === track && this.inputs == inputs

    override fun toString(): String = "CourseModel(spec=$spec, points=${track.points.size}, mark=$windwardMark)"

    public companion object {
        /** The track is thinned to this many points for drawing; the map cannot show more anyway. */
        public const val MAX_TRACK_POINTS: Int = 600

        public fun build(track: Track, inputs: CourseInputs): CourseModel {
            val frame = inputs.frame
            val positions = track.points.map { frame.toCourse(it.point) }
            val pinEnd = inputs.line.pinEnd?.let(frame::toCourse)
            val boatEnd = inputs.line.boatEnd?.let(frame::toCourse)
            val setMark = inputs.windwardMark?.let(frame::toCourse)
            val boat = inputs.boat?.let(frame::toCourse)
            val spec = GridSpec.covering(positions + listOfNotNull(pinEnd, boatEnd, setMark, boat, CoursePosition(0.0, 0.0)))
            val mark = setMark ?: spec.topCenter
            val grid = TrackGrid.build(track, frame, spec, mark)
            val field = WindField.build(grid, frame.windDirectionDegrees)
            val route = boat?.let { RouteFinder.route(field, it, mark, inputs.tackAngleDegrees, inputs.downwindAngleDegrees) } ?: emptyList()
            return CourseModel(track, inputs, spec, grid, field, thin(positions), pinEnd, boatEnd, boat, mark, setMark != null, route)
        }

        private fun thin(positions: List<CoursePosition>): List<CoursePosition> {
            if (positions.isEmpty()) return emptyList()
            val step = (positions.size + MAX_TRACK_POINTS - 1) / MAX_TRACK_POINTS
            val thinned = ArrayList<CoursePosition>(positions.size / step + 2)
            for (index in positions.indices step step) thinned += positions[index]
            if ((positions.size - 1) % step != 0) thinned += positions.last()
            return thinned
        }
    }
}
