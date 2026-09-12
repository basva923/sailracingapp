package com.sailracing.domain.course

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.wind.Tack
import kotlin.math.abs

/**
 * Everything the course model is built from apart from the track, so an unchanged model can be reused
 * from one snapshot to the next (the clock ticks several times a second, the track grows once a second).
 *
 * @property windwardMark the mark the sailor set, if any; @property boat the latest position.
 * @property currentTack the tack the boat is beating on right now, null when it is not beating.
 * @property currentWindDegrees the wind the boat is measuring right now from its own heading and tack
 *   angle, steadied over the last half minute of beating; null when it is not beating and its heading says
 *   nothing about the wind. It moves all the time, so it is compared with a tolerance rather than for
 *   equality: see [sameCourseAs].
 * @property settings how the area is cut into squares, what its wind is believed to be like and how the
 *   race line is searched for.
 */
public data class CourseInputs(
    val frame: CourseFrame,
    val line: StartLine,
    val windwardMark: GeoPoint?,
    val boat: GeoPoint?,
    val tackAngleDegrees: Int,
    val currentTack: Tack? = null,
    val currentWindDegrees: Double? = null,
    val settings: CourseSettings = CourseSettings(),
) {
    /** The same course, bar a wind of the moment that may have moved: everything else has to match exactly. */
    public fun sameCourseAs(other: CourseInputs): Boolean =
        copy(currentWindDegrees = other.currentWindDegrees) == other && sameCurrentWindAs(other)

    /** True when the wind of the moment has not moved enough to be worth working the race line out again. */
    public fun sameCurrentWindAs(other: CourseInputs): Boolean {
        val was = currentWindDegrees
        val now = other.currentWindDegrees
        if (was == null || now == null) return was == null && now == null
        return abs(Angles.signedDifference(was, now)) <= settings.raceLine.recomputeShiftDegrees
    }
}

/**
 * The course as the map shows it, in the wind-up [CourseFrame] (metres, origin at the start): the racing
 * area that follows the track, the track binned onto it, the wind field over it, the marks, the boat and
 * the race lines from the boat to the windward mark. All of it is derived, none of it is state.
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
    /** The safe and the fast way up the beat, over many simulated winds; empty without a boat position. */
    public val racePlan: RaceLinePlan,
) {
    public val frame: CourseFrame get() = inputs.frame

    /** The race line to sail: the safe one of [racePlan]. */
    public val raceLine: RaceLine get() = racePlan.safe

    /**
     * The race plan of this model moved onto [boat], while it is still the plan to sail: the same frame,
     * racing area, mark, tack angle and settings, the boat still in the square it was worked out from, on
     * the same tack, and in a wind that has not moved since. Null when it has to be worked out again.
     */
    private fun reusablePlan(spec: GridSpec, mark: CoursePosition, inputs: CourseInputs, boat: CoursePosition): RaceLinePlan? {
        val was = this.boat ?: return null
        // The frame has to match too: everything else here is metres in it, and they can all stay the same
        // while the reference wind turns the frame, which turns the water the line was searched over.
        val same = this.inputs.frame == inputs.frame && this.spec == spec && windwardMark == mark &&
            this.inputs.tackAngleDegrees == inputs.tackAngleDegrees &&
            this.inputs.currentTack == inputs.currentTack &&
            this.inputs.settings == inputs.settings &&
            this.inputs.sameCurrentWindAs(inputs) && !racePlan.isEmpty &&
            spec.nearestCell(was) == spec.nearestCell(boat)
        return if (same) racePlan.anchoredAt(boat) else null
    }

    /** True when this model was built from exactly these inputs (the track by identity: it is only ever replaced). */
    public fun isFor(track: Track, inputs: CourseInputs): Boolean =
        this.track === track && this.inputs.sameCourseAs(inputs)

    override fun toString(): String = "CourseModel(spec=$spec, points=${track.points.size}, mark=$windwardMark)"

    public companion object {
        /** The track is thinned to this many points for drawing; the map cannot show more anyway. */
        public const val MAX_TRACK_POINTS: Int = 600

        /**
         * The model for a track and its inputs. [previous] is the model of the moment before, if any: its
         * race plan is kept, pinned to the boat, until the boat sails into another square of the same area.
         */
        public fun build(track: Track, inputs: CourseInputs, previous: CourseModel? = null): CourseModel {
            val frame = inputs.frame
            val positions = track.points.map { frame.toCourse(it.point) }
            val pinEnd = inputs.line.pinEnd?.let(frame::toCourse)
            val boatEnd = inputs.line.boatEnd?.let(frame::toCourse)
            val setMark = inputs.windwardMark?.let(frame::toCourse)
            val boat = inputs.boat?.let(frame::toCourse)
            // The racing area is where the race is: around the line, the mark and the boat. The track out
            // to it from the harbour is not part of it - an area that covered a sail of several miles
            // would be cut into squares too big to say anything about the wind on the course.
            val anchors = listOfNotNull(pinEnd, boatEnd, setMark, boat)
            val nearby = if (anchors.isEmpty()) positions else positions.filter { position -> anchors.any { within(position, it) } }
            val spec = GridSpec.covering((nearby + anchors).ifEmpty { listOf(CoursePosition(0.0, 0.0)) }, inputs.settings.grid)
            val mark = setMark ?: spec.topCenter
            val grid = TrackGrid.build(track, frame, spec, mark)
            val field = WindField.build(track, frame, spec, inputs.settings.windField)
            // The wind of the moment is measured against north, the field against the reference wind.
            val currentShift = inputs.currentWindDegrees?.let { Angles.signedDifference(frame.windDirectionDegrees, it) }
            val plan = boat?.let {
                previous?.reusablePlan(spec, mark, inputs, it) ?: RaceLinePlanner.plan(
                    field = field,
                    from = it,
                    to = mark,
                    tackAngleDegrees = inputs.tackAngleDegrees,
                    currentTack = inputs.currentTack,
                    currentShiftDegrees = currentShift,
                    settings = inputs.settings.raceLine,
                )
            } ?: RaceLinePlan.NONE
            return CourseModel(track, inputs, spec, grid, field, thin(positions), pinEnd, boatEnd, boat, mark, setMark != null, plan)
        }

        /**
         * How far from the line, the mark or the boat the track still counts as part of the racing area:
         * further than a long beat away it is the way here, not the course.
         */
        public const val RACING_REACH_METERS: Double = 2_000.0

        private fun within(position: CoursePosition, anchor: CoursePosition): Boolean {
            val across = position.acrossMeters - anchor.acrossMeters
            val upwind = position.upwindMeters - anchor.upwindMeters
            return across * across + upwind * upwind <= RACING_REACH_METERS * RACING_REACH_METERS
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
