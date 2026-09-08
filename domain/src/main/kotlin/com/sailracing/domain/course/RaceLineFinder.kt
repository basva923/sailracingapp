package com.sailracing.domain.course

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.wind.Tack
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The race line: how the boat sails from where it is now to the windward mark, tack by tack.
 *
 * @property points the boat, then every square boundary the line crosses, then the mark. Two consecutive
 *   points are one straight board across one square; a bend is either a tack or the local wind changing.
 * @property seconds how long the whole line takes at the assumed boat speed, tack losses included.
 * @property tacks how often the boat changes tack along the line.
 */
public data class RaceLine(
    val points: List<CoursePosition> = emptyList(),
    val seconds: Double = 0.0,
    val tacks: Int = 0,
) {
    public val isEmpty: Boolean get() = points.isEmpty()

    /**
     * The same line with its first point moved to [boat]. The line is only searched again when the boat
     * enters a new square, so in between it is pinned to the boat instead of hanging behind it; the shift
     * is at most the width of one square and [seconds] stays the time from where it was searched.
     */
    public fun anchoredAt(boat: CoursePosition): RaceLine =
        if (isEmpty) this else copy(points = listOf(boat) + points.drop(1))

    public companion object {
        /** No line: no boat position, so nothing to sail from. */
        public val NONE: RaceLine = RaceLine()
    }
}

/**
 * Finds the fastest way to beat from the boat to the windward mark through one wind ([SailingConditions]),
 * as a real track that can be sailed: a sequence of close-hauled boards, each one crossing a single square
 * of the racing area, joined by tacks.
 *
 * One wind, not the wind: this is the search a single run of [RaceLinePlanner]'s Monte Carlo uses, and the
 * wind it is given is either the mean of what was measured ([WindField]) or one draw from what might be
 * ([SampledConditions]).
 *
 * ## The model
 *
 * The racing area is the grid of squares of the [GridSpec]; every square carries one wind direction and
 * one close-hauled boat speed, so a square in more wind is sailed through faster than a square in a hole.
 * Inside a square both are taken to be constant, so a close-hauled boat has exactly two courses available:
 * the wind in that square minus the tack angle (starboard tack, sailing up and to the left of the wind)
 * and the wind plus the tack angle (port tack, up and to the right). No other course is sailed: this is a
 * beat.
 *
 * ## The search
 *
 * A state is a point on a square boundary plus the tack the boat is on there. From a state the search
 * takes every square that touches the point, works out the two close-hauled courses for the wind in that
 * square and follows each of them from the point until it leaves that square: that intersection with the
 * boundary is the next point, and the board between the two points is one move. Moves that would leave
 * the racing area do not exist, which is what forces a tack at the edge of the area.
 *
 * A move is only kept when it shortens what is left to sail, which is the distance to the mark once the
 * boat can lay it and the length of the beat, distance / [vmgFactor], while the mark is inside the no-go
 * zone. That single rule removes both sailing backwards and sailing away from the mark, and it is the
 * layline: it lets a board be held while the beat still gets shorter, and refuses the board that would
 * carry the boat past the point where tacking lays the mark, because from there every metre is a metre
 * lost. Measuring it on the straight distance to the mark instead would be the same rule only for a boat
 * that tacks through exactly a right angle.
 *
 * A move costs the time to sail the board, its length divided by the boat speed in the square it crosses,
 * plus [TACK_LOSS_SECONDS] when it is sailed on the other tack than the move before it. The boat's first
 * board is charged that too when it is not the tack the boat is on right now; without a tack given, the
 * line starts on whichever tack suits it and the first board is free.
 *
 * The target is reached as soon as the boat arrives at a point that touches the square holding the mark -
 * whichever way it is heading, and of any of those squares when the mark sits on a boundary itself and so
 * belongs to two or four of them. What is left of the leg is added as the time to sail straight from there
 * to the mark, at the speed made good towards it ([vmgFactor]), plus a tack when that last stretch has to
 * be sailed on the other tack, so that reaching the mark's square in a far corner is not free. The line
 * with the smallest total time wins; of two that take the same time, the one that sails furthest into the
 * mark's square, because the search's geometry is exact where that last stretch is an estimate.
 *
 * The search runs breadth first: it expands the whole frontier of states reachable in one board, then in
 * two, and so on, remembering the best time found for every state and only carrying a state forward when
 * that time improved. Because a state can be reached again with a better time by a longer route, this is a
 * relaxation over the number of boards, and after the last round the best time for every state is optimal.
 * Boards are cut off at [MAX_BOARDS_PER_CELL] per row and column of the area, far more than a beat needs.
 *
 * States are compared on a lattice of [EDGE_SLOTS] slots per square side: two points on the same boundary
 * within a slot of one another, on the same tack, are the same state and only the faster one survives.
 * That keeps a continuous problem finite. The geometry of the line itself stays exact.
 *
 * Two lines can take exactly the same time - in an even wind a beat and its mirror image do - and then the
 * one that sails furthest into the mark's square wins, and failing that the one the search reaches first.
 * That is deterministic, but it favours neither side of the course.
 *
 * When the mark cannot be beaten to at all - it is abeam or downwind of the boat, the boat is outside the
 * area, or it is already in the mark's square - the line is simply the straight course to the mark.
 */
public object RaceLineFinder {

    /** How long a tack takes, from bearing away out of the old course to sailing full speed on the new one. */
    public const val TACK_SECONDS: Double = 8.0

    /** The mean speed through a tack, as a fraction of the close-hauled speed: the boat loses way and rebuilds it. */
    public const val TACK_SPEED_FRACTION: Double = 0.55

    /** The time a tack costs: the part of [TACK_SECONDS] the boat does not sail at full speed. */
    public const val TACK_LOSS_SECONDS: Double = TACK_SECONDS * (1.0 - TACK_SPEED_FRACTION)

    /**
     * The close-hauled speed assumed where nothing has been measured, in metres per second. Where the boat
     * has sailed, every square carries the speed measured in it instead, so a square in more wind is worth
     * sailing through.
     */
    public const val BOAT_SPEED_MPS: Double = SailingConditions.DEFAULT_BOAT_SPEED_MPS

    /**
     * Slots per square side on which two crossing points count as the same state. The coarser this is the
     * more the search throws away: at 8 slots two mirror images of the same course came out up to 2 s apart,
     * at 16 up to 0.6 s, and at 32 they agree exactly, for twice the work of 8 - a few milliseconds either
     * way on a search that runs once per square sailed through.
     */
    public const val EDGE_SLOTS: Int = 32

    /** The search gives up after this many boards per row and column of the area; a beat needs far fewer. */
    public const val MAX_BOARDS_PER_CELL: Int = 4

    /**
     * The speed the boat makes good along a course [twaDegrees] off the wind, as a fraction of its speed
     * through the water: 1 when it can sail that course, and cos(tack angle) / cos(twa) when the course
     * lies inside the no-go zone, because it then has to beat up to it on both tacks.
     */
    public fun vmgFactor(twaDegrees: Double, tackAngleDegrees: Int): Double {
        val twa = abs(twaDegrees)
        if (twa >= tackAngleDegrees) return 1.0
        return cos(Angles.toRadians(tackAngleDegrees.toDouble())) / cos(Angles.toRadians(twa))
    }

    /**
     * The race line from [from] (the boat) to [to] (the windward mark) through [conditions].
     *
     * @param tackAngleDegrees the angle between the wind and the boat's close-hauled course.
     * @param startTack the tack the boat is on now, so that starting on the other one costs a tack; null
     *   when it is not beating and there is no tack to leave on.
     * @param tackLossSeconds the time a tack costs.
     */
    public fun find(
        conditions: SailingConditions,
        from: CoursePosition,
        to: CoursePosition,
        tackAngleDegrees: Int,
        startTack: Tack? = null,
        tackLossSeconds: Double = TACK_LOSS_SECONDS,
    ): RaceLine {
        val spec = conditions.spec
        val goals = goalCells(spec, to)
        val start = spec.cellOf(from)
        if (start == null || goals[spec.index(start)]) return straight(conditions, from, to, tackAngleDegrees)

        // The whole search is done on the cosine of the tack angle rather than on the angle: a course is
        // close-hauled or free, and a beat is long or short, by how a cosine compares with this one, which
        // keeps the arc tangents and degrees out of a loop that runs a hundred thousand times. The two
        // close-hauled courses in a square follow from the wind's own cosine and sine the same way.
        val tack = Angles.toRadians(tackAngleDegrees.toDouble())
        val cosTack = cos(tack)
        val sinTack = sin(tack)
        // The wind and the speed of every square, read once: the search comes back to the same square from
        // hundreds of states, and a sine is dear where an array lookup is not.
        val cosWinds = DoubleArray(spec.cellCount)
        val sinWinds = DoubleArray(spec.cellCount)
        val cellSpeeds = DoubleArray(spec.cellCount)
        for (index in 0 until spec.cellCount) {
            val cell = spec.cellAt(index)
            val wind = Angles.toRadians(conditions.shiftDegrees(cell))
            cosWinds[index] = cos(wind)
            sinWinds[index] = sin(wind)
            cellSpeeds[index] = conditions.speedMps(cell)
        }
        val steps = ArrayList<Step>()
        steps += Step(from, tack = startTack, seconds = 0.0, tacks = 0, parent = -1)
        val best = BestTimes()
        var finish = -1
        var finishSeconds = Double.POSITIVE_INFINITY
        var finishTacks = 0
        var finishRunIn = Double.POSITIVE_INFINITY

        /** Every board out of one state, keeping the ones that improve on what is known. */
        fun boardsFrom(index: Int, next: ArrayList<Int>) {
            val step = steps[index]
            // Only lines that are slower than the best finish are dropped: one that ties with it may still
            // be the tidier of the two, and win on that.
            if (step.seconds > finishSeconds + TIE_SECONDS) return
            for (column in touching(spec, step.at.acrossMeters - spec.leftMeters, spec.columns)) {
                for (row in touching(spec, step.at.upwindMeters - spec.bottomMeters, spec.rows)) {
                    val cell = row * spec.columns + column
                    val cosWind = cosWinds[cell]
                    val sinWind = sinWinds[cell]
                    val boatSpeedMps = cellSpeeds[cell]
                    val remaining = toGoMeters(cosWind, sinWind, step.at, to, cosTack)
                    for (tack in TACKS) {
                        // The close-hauled course, straight from the wind's cosine and sine: starboard is
                        // the wind less the tack angle, port the wind plus it.
                        val side = if (tack == Tack.STARBOARD) -1.0 else 1.0
                        val across = sinWind * cosTack + side * cosWind * sinTack
                        val upwind = cosWind * cosTack - side * sinWind * sinTack
                        val at = crossing(spec, column, row, step.at, across, upwind) ?: continue
                        if (toGoMeters(cosWind, sinWind, at, to, cosTack) > remaining - PROGRESS_METERS) continue
                        val tacked = step.tack != null && step.tack != tack
                        val seconds = step.seconds + distance(step.at, at) / boatSpeedMps + if (tacked) tackLossSeconds else 0.0
                        if (seconds > finishSeconds + TIE_SECONDS) continue
                        val key = key(spec, at, tack)
                        if (!best.improve(key, seconds)) continue
                        val added = steps.size
                        steps += Step(at, tack, seconds, step.tacks + if (tacked) 1 else 0, index)
                        next += added
                        if (!touchesGoal(spec, at, goals)) continue
                        // On the boundary of the mark's own square: what is left is the run in, through the
                        // wind of that square. The board is kept in the frontier all the same, because a line
                        // that only clips a corner of the square can still be bettered by carrying on.
                        val middle = CoursePosition((at.acrossMeters + to.acrossMeters) / 2, (at.upwindMeters + to.upwindMeters) / 2)
                        val runInWind = conditions.shiftAt(middle)
                        val runInSpeed = conditions.speedAt(middle)
                        val runIn = distance(at, to)
                        val twa = Angles.signedDifference(runInWind, Angles.toDegrees(atan2(to.acrossMeters - at.acrossMeters, to.upwindMeters - at.upwindMeters)))
                        // A run in the boat can lay is one course on one tack, and costs a tack when that is not
                        // the tack it arrived on. One it cannot lay is the last of the beat, sailed on this tack first.
                        val tackedIn = abs(twa) >= tackAngleDegrees - ANGLE_EPSILON && (if (twa < 0.0) Tack.STARBOARD else Tack.PORT) != tack
                        val total = seconds + runIn / (runInSpeed * vmgFactor(twa, tackAngleDegrees)) + if (tackedIn) tackLossSeconds else 0.0
                        // Of two lines that take the same time, the one that sails furthest into the mark's own
                        // square is drawn: its geometry is searched, where the run in is only an estimate.
                        if (total < finishSeconds - TIE_SECONDS || (total < finishSeconds + TIE_SECONDS && runIn < finishRunIn)) {
                            finishSeconds = total
                            finishRunIn = runIn
                            finishTacks = steps[added].tacks + if (tackedIn) 1 else 0
                            finish = added
                        }
                    }
                }
            }
        }

        var frontier: List<Int> = listOf(0)
        val maxBoards = MAX_BOARDS_PER_CELL * (spec.columns + spec.rows)
        var boards = 0
        while (frontier.isNotEmpty() && boards < maxBoards) {
            val next = ArrayList<Int>()
            for (index in frontier) boardsFrom(index, next)
            frontier = next
            boards++
        }
        if (finish < 0) return straight(conditions, from, to, tackAngleDegrees)
        // A line that ends on the mark itself has no run in to draw: the mark replaces that last point
        // rather than being repeated after it.
        val path = pathTo(steps, finish)
        val points = if (finishRunIn <= TOLERANCE_METERS) path.dropLast(1) + to else path + to
        return RaceLine(points, finishSeconds, finishTacks)
    }

    /**
     * What [line] costs in [conditions]: the same track, sailed through a wind that is not the one it was
     * found in. This is how the Monte Carlo scores a line - every candidate is sailed through every drawn
     * wind, so a line that only wins in the wind it was searched in is found out.
     *
     * Every leg is timed at the speed of the square its middle lies in, and a leg the boat can no longer
     * lay - the wind drew a header, and the course that was close-hauled is now inside the no-go zone - is
     * charged as the beat it has become, `distance / vmgFactor`, which is what overstanding a layline
     * costs when the shift goes the other way. A tack is charged wherever the tack changes, starting from
     * [startTack].
     *
     * Sailed through the wind it was found in, this gives exactly the time [find] gave it.
     */
    public fun secondsToSail(
        conditions: SailingConditions,
        line: RaceLine,
        tackAngleDegrees: Int,
        startTack: Tack? = null,
        tackLossSeconds: Double = TACK_LOSS_SECONDS,
    ): Double {
        var seconds = 0.0
        var tack = startTack
        for ((at, next) in line.points.zipWithNext()) {
            val length = distance(at, next)
            if (length <= 0.0) continue
            val middle = CoursePosition((at.acrossMeters + next.acrossMeters) / 2, (at.upwindMeters + next.upwindMeters) / 2)
            val bearing = Angles.toDegrees(atan2(next.acrossMeters - at.acrossMeters, next.upwindMeters - at.upwindMeters))
            val twa = Angles.signedDifference(conditions.shiftAt(middle), bearing)
            seconds += length / (conditions.speedAt(middle) * vmgFactor(twa, tackAngleDegrees))
            // A leg that has to be beaten is sailed on both tacks, so it leaves the boat on whichever one
            // it likes and costs no tack of its own beyond the beating already charged.
            if (abs(twa) < tackAngleDegrees - ANGLE_EPSILON) continue
            val board = if (twa < 0.0) Tack.STARBOARD else Tack.PORT
            if (tack != null && tack != board) seconds += tackLossSeconds
            tack = board
        }
        return seconds
    }

    private val TACKS: List<Tack> = listOf(Tack.STARBOARD, Tack.PORT)

    /** A board has to take at least this much off the beat; anything else is sailing away from the mark. */
    private const val PROGRESS_METERS: Double = 1e-6

    /** A board shorter than this is a course leaving the square it starts on, not a crossing of it. */
    private const val TOLERANCE_METERS: Double = 1e-6

    /** A course within this of a square side counts as parallel to it. */
    private const val PARALLEL: Double = 1e-9

    /** Slack on the test of whether a course is close-hauled: one worked out from two positions lands a
     *  hair either side of the tack angle, and a tack costs the same on both sides of that hair. */
    private const val ANGLE_EPSILON: Double = 1e-6

    /** Two finishing times within this of each other are a dead heat, and the tidier line wins. */
    private const val TIE_SECONDS: Double = 1e-9

    /** Slack, in squares, on the test of whether a point lies on a square boundary. */
    private const val EDGE_EPSILON: Double = 1e-9

    /** Keys pack the two slot coordinates and the tack into a long; the area never has this many slots. */
    private const val SLOT_STRIDE: Long = 1L shl 20

    /** The straight course to the mark, at the speed made good towards it: a leg that is not a beat. */
    private fun straight(
        conditions: SailingConditions,
        from: CoursePosition,
        to: CoursePosition,
        tackAngleDegrees: Int,
    ): RaceLine = RaceLine(
        points = listOf(from, to),
        seconds = toGoMeters(conditions.shiftAt(from), from, to, tackAngleDegrees) / conditions.speedAt(from),
        tacks = 0,
    )

    /**
     * How far the boat still has to sail from [at] to reach [to] in a wind of [cosWind], [sinWind]: the
     * distance when it can lay the mark, and the length of the beat, distance / [vmgFactor], when the mark
     * lies inside the no-go zone. This is what a board has to shorten to count as progress, and it is the
     * right measure whatever the tack angle: the straight distance to the mark starts growing before the
     * boat reaches its layline as soon as it tacks through more than a right angle, and a rule written on
     * that would stop a wide-angled boat short of the layline, where it can never lay the mark at all.
     *
     * The wind comes in as its cosine and sine and the tack angle as [cosTack], because the cosine of the
     * angle between the course to the mark and the wind is a dot product of the two - no arc tangent, no
     * degrees - and everything this decides is a comparison of that cosine with the tack angle's.
     */
    private fun toGoMeters(cosWind: Double, sinWind: Double, at: CoursePosition, to: CoursePosition, cosTack: Double): Double {
        val across = to.acrossMeters - at.acrossMeters
        val upwind = to.upwindMeters - at.upwindMeters
        val distance = sqrt(across * across + upwind * upwind)
        if (distance <= 0.0) return 0.0
        val cosTwa = (upwind * cosWind + across * sinWind) / distance
        return if (cosTwa <= cosTack) distance else distance * cosTwa / cosTack
    }

    /** The same, from a wind in degrees: the readable form, for the once-per-line reckonings. */
    private fun toGoMeters(windDegrees: Double, at: CoursePosition, to: CoursePosition, tackAngleDegrees: Int): Double {
        val wind = Angles.toRadians(windDegrees)
        return toGoMeters(cos(wind), sin(wind), at, to, cos(Angles.toRadians(tackAngleDegrees.toDouble())))
    }

    /** The squares the mark lies in or on the edge of, or the nearest square when it lies outside the area. */
    private fun goalCells(spec: GridSpec, to: CoursePosition): BooleanArray {
        val goals = BooleanArray(spec.cellCount)
        var any = false
        for (column in touching(spec, to.acrossMeters - spec.leftMeters, spec.columns)) {
            for (row in touching(spec, to.upwindMeters - spec.bottomMeters, spec.rows)) {
                goals[row * spec.columns + column] = true
                any = true
            }
        }
        if (!any) goals[spec.index(spec.nearestCell(to))] = true
        return goals
    }

    /** True when one of the squares touching [at] holds the mark. */
    private fun touchesGoal(spec: GridSpec, at: CoursePosition, goals: BooleanArray): Boolean {
        for (column in touching(spec, at.acrossMeters - spec.leftMeters, spec.columns)) {
            for (row in touching(spec, at.upwindMeters - spec.bottomMeters, spec.rows)) {
                if (goals[row * spec.columns + column]) return true
            }
        }
        return false
    }

    /**
     * The one or two square indices along an axis that [offsetMeters] from the corner of the area lies in
     * or on the edge of: a point inside a square touches only it, a point on a boundary touches both.
     */
    private fun touching(spec: GridSpec, offsetMeters: Double, count: Int): IntRange {
        val coordinate = offsetMeters / spec.cellSizeMeters
        val low = floor(coordinate - EDGE_EPSILON).toInt()
        val high = floor(coordinate + EDGE_EPSILON).toInt()
        return low.coerceAtLeast(0)..high.coerceAtMost(count - 1)
    }

    /**
     * Where a course leaves the square it is sailed into: the intersection of the ray from [at] along the
     * unit direction ([across], [upwind]) with the far side of the square at [column], [row]. Null when the course does not cross
     * the square at all, which is how a course pointing out of it, or out of the area, is refused.
     */
    private fun crossing(
        spec: GridSpec,
        column: Int,
        row: Int,
        at: CoursePosition,
        across: Double,
        upwind: Double,
    ): CoursePosition? {
        val left = spec.leftMeters + column * spec.cellSizeMeters
        val bottom = spec.bottomMeters + row * spec.cellSizeMeters
        val leave = min(
            exitDistance(at.acrossMeters, across, left, left + spec.cellSizeMeters),
            exitDistance(at.upwindMeters, upwind, bottom, bottom + spec.cellSizeMeters),
        )
        if (leave <= TOLERANCE_METERS) return null
        return CoursePosition(at.acrossMeters + across * leave, at.upwindMeters + upwind * leave)
    }

    /** How far a ray from [origin] in [direction] runs before it leaves the band [low]..[high] it starts in. */
    private fun exitDistance(origin: Double, direction: Double, low: Double, high: Double): Double = when {
        direction > PARALLEL -> (high - origin) / direction
        direction < -PARALLEL -> (low - origin) / direction
        else -> Double.POSITIVE_INFINITY
    }

    /** The state of being at [at] on [tack], to the resolution the search compares states on. */
    private fun key(spec: GridSpec, at: CoursePosition, tack: Tack): Long {
        val slot = spec.cellSizeMeters / EDGE_SLOTS
        val across = ((at.acrossMeters - spec.leftMeters) / slot).roundToLong()
        val upwind = ((at.upwindMeters - spec.bottomMeters) / slot).roundToLong()
        return (across * SLOT_STRIDE + upwind) * 2 + tack.ordinal
    }

    private fun distance(from: CoursePosition, to: CoursePosition): Double {
        val across = to.acrossMeters - from.acrossMeters
        val upwind = to.upwindMeters - from.upwindMeters
        // Not hypot: it guards against an overflow that a racing area a few hundred metres wide cannot
        // reach, and it costs many times a square root in the innermost loop of the search.
        return sqrt(across * across + upwind * upwind)
    }

    /** The points of the line that ends at [index], from the boat onwards. */
    private fun pathTo(steps: List<Step>, index: Int): List<CoursePosition> {
        val points = ArrayList<CoursePosition>()
        var at = index
        while (at >= 0) {
            points += steps[at].at
            at = steps[at].parent
        }
        points.reverse()
        return points
    }

    /**
     * The best time found so far for every state, keyed by [key]: an open-addressed table of primitives,
     * because a search that reaches ten thousand states boxes ten thousand keys and values in a HashMap and
     * spends more time on that than on the geometry.
     */
    private class BestTimes(capacity: Int = 1 shl 12) {
        private var keys = LongArray(capacity)
        private var times = DoubleArray(capacity)
        private var used = BooleanArray(capacity)
        private var size = 0

        /** Records [seconds] for [key] and answers whether it improved on what was known. */
        fun improve(key: Long, seconds: Double): Boolean {
            var slot = slotOf(key, keys.size)
            while (used[slot]) {
                if (keys[slot] == key) {
                    if (seconds >= times[slot]) return false
                    times[slot] = seconds
                    return true
                }
                slot = (slot + 1) and (keys.size - 1)
            }
            used[slot] = true
            keys[slot] = key
            times[slot] = seconds
            size++
            if (size * 2 > keys.size) grow()
            return true
        }

        private fun grow() {
            val capacity = keys.size * 2
            val grownKeys = LongArray(capacity)
            val grownTimes = DoubleArray(capacity)
            val grownUsed = BooleanArray(capacity)
            for (old in keys.indices) {
                if (!used[old]) continue
                var slot = slotOf(keys[old], capacity)
                while (grownUsed[slot]) slot = (slot + 1) and (capacity - 1)
                grownUsed[slot] = true
                grownKeys[slot] = keys[old]
                grownTimes[slot] = times[old]
            }
            keys = grownKeys
            times = grownTimes
            used = grownUsed
        }

        /** Keys are packed lattice coordinates, so they are spread with a multiplicative hash. */
        private fun slotOf(key: Long, capacity: Int): Int = ((key * MIXER) ushr 40).toInt() and (capacity - 1)

        private companion object {
            const val MIXER: Long = -0x61c8864680b583ebL
        }
    }

    /** One searched state: where the boat is, on which tack, how it got there and what it cost. */
    private class Step(
        val at: CoursePosition,
        val tack: Tack?,
        val seconds: Double,
        val tacks: Int,
        val parent: Int,
    )
}
