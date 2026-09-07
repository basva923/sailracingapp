package com.sailracing.domain.course

import com.sailracing.domain.geo.Angles
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * The fastest course from the boat to the windward mark through the measured wind field: a shortest-path
 * search over the area where the cost of crossing a cell in a given direction is the time it takes, given
 * the wind in that cell and the boat's tacking angles.
 *
 * The line it returns is the corridor to sail up, not the tacks themselves: where the wind is veered the
 * corridor bends right (the boat that goes into the shift tacks onto a longer lift), where it is backed
 * it bends left, and in an even wind it is the straight line to the mark.
 */
public object RouteFinder {

    /** Each cell is split this many times per side for the search, so the line is not too coarse. */
    public const val SUBDIVISION: Int = 2

    /**
     * Among routes that take the same time (in an even wind every zigzag inside the no-go cone does), a small
     * cost per metre sailed makes the straightest one win. Time is counted in metres at full speed.
     */
    public const val DISTANCE_PENALTY: Double = 0.005

    private val MOVES: List<Pair<Int, Int>> = listOf(
        1 to 0, -1 to 0, 0 to 1, 0 to -1,
        1 to 1, 1 to -1, -1 to 1, -1 to -1,
        1 to 2, 1 to -2, -1 to 2, -1 to -2, 2 to 1, 2 to -1, -2 to 1, -2 to -1,
    )

    /**
     * The speed a boat makes good along a course [twaDegrees] off the wind, as a fraction of its speed
     * through the water: 1 when it can sail the course directly, and cos(angle) / cos(twa) when the course
     * lies in the no-go zone upwind (closer than the tack angle) or downwind (broader than the downwind
     * angle), because it then has to zigzag between the two tacks or gybes to hold that course.
     */
    public fun speedFactor(twaDegrees: Double, tackAngleDegrees: Int, downwindAngleDegrees: Int): Double {
        val twa = abs(twaDegrees)
        return when {
            twa < tackAngleDegrees -> cos(Angles.toRadians(tackAngleDegrees.toDouble())) / cos(Angles.toRadians(twa))
            twa > downwindAngleDegrees ->
                cos(Angles.toRadians(180.0 - downwindAngleDegrees)) / cos(Angles.toRadians(180.0 - twa))
            else -> 1.0
        }
    }

    public fun route(
        field: WindField,
        from: CoursePosition,
        to: CoursePosition,
        tackAngleDegrees: Int,
        downwindAngleDegrees: Int,
    ): List<CoursePosition> {
        val spec = field.spec
        val columns = spec.columns * SUBDIVISION
        val rows = spec.rows * SUBDIVISION
        val step = spec.cellSizeMeters / SUBDIVISION
        fun nodeOf(position: CoursePosition): Int {
            val column = ((position.acrossMeters - spec.leftMeters) / step).toInt().coerceIn(0, columns - 1)
            val row = ((position.upwindMeters - spec.bottomMeters) / step).toInt().coerceIn(0, rows - 1)
            return row * columns + column
        }
        fun centerOf(node: Int) = CoursePosition(
            spec.leftMeters + (node % columns + 0.5) * step,
            spec.bottomMeters + (node / columns + 0.5) * step,
        )

        val start = nodeOf(from)
        val goal = nodeOf(to)
        if (start == goal) return listOf(from, to)

        val cost = DoubleArray(columns * rows) { Double.POSITIVE_INFINITY }
        val previous = IntArray(columns * rows) { -1 }
        val queue = PriorityQueue<Entry>()
        cost[start] = 0.0
        queue += Entry(0.0, start)
        while (queue.isNotEmpty()) {
            val (here, node) = queue.poll()
            if (node == goal) break
            if (here > cost[node]) continue
            val column = node % columns
            val row = node / columns
            val at = centerOf(node)
            for ((dx, dy) in MOVES) {
                val nextColumn = column + dx
                val nextRow = row + dy
                if (nextColumn !in 0 until columns || nextRow !in 0 until rows) continue
                val next = nextRow * columns + nextColumn
                val length = sqrt((dx * dx + dy * dy).toDouble()) * step
                val bearing = Angles.toDegrees(atan2(dx.toDouble(), dy.toDouble()))
                val midpoint = CoursePosition(at.acrossMeters + dx * step / 2, at.upwindMeters + dy * step / 2)
                val twa = Angles.signedDifference(field.shiftAt(midpoint), bearing)
                val total = here + length / speedFactor(twa, tackAngleDegrees, downwindAngleDegrees) + DISTANCE_PENALTY * length
                if (total < cost[next]) {
                    cost[next] = total
                    previous[next] = node
                    queue += Entry(total, next)
                }
            }
        }

        val nodes = ArrayList<Int>()
        var node = goal
        while (node != -1) {
            nodes += node
            node = previous[node]
        }
        nodes.reverse()
        val path = nodes.map(::centerOf).toMutableList()
        path[0] = from
        path[path.size - 1] = to
        return smooth(path)
    }

    /** One pass of corner cutting, keeping both ends, so the line does not show the search grid. */
    private fun smooth(path: List<CoursePosition>): List<CoursePosition> {
        if (path.size < 3) return path
        val result = ArrayList<CoursePosition>(path.size * 2)
        result += path.first()
        for (i in 0 until path.size - 1) {
            val a = path[i]
            val b = path[i + 1]
            result += CoursePosition(0.75 * a.acrossMeters + 0.25 * b.acrossMeters, 0.75 * a.upwindMeters + 0.25 * b.upwindMeters)
            result += CoursePosition(0.25 * a.acrossMeters + 0.75 * b.acrossMeters, 0.25 * a.upwindMeters + 0.75 * b.upwindMeters)
        }
        result += path.last()
        return result
    }

    private data class Entry(val cost: Double, val node: Int) : Comparable<Entry> {
        override fun compareTo(other: Entry): Int = cost.compareTo(other.cost)
    }
}
