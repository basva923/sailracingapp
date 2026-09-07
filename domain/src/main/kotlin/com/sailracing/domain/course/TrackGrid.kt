package com.sailracing.domain.course

import com.sailracing.domain.geo.Angles
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** The two halves of the course, split by the axis from the frame origin (the start) to the windward mark. */
public enum class Side { LEFT, RIGHT }

/**
 * The racing area: a rectangle of square cells laid over a [CourseFrame], in metres. It is not a setting
 * but follows the track: [covering] fits it around everything sailed, snapped to the 10 m sub-grid, with
 * cells of 10 m or a multiple of it when finer cells would be too small to draw.
 *
 * @property leftMeters across-coordinate of the left edge.
 * @property bottomMeters upwind-coordinate of the bottom (downwind) edge.
 * @property cellSizeMeters side of a cell, a multiple of [BASE_CELL_METERS].
 */
public data class GridSpec(
    val leftMeters: Double,
    val bottomMeters: Double,
    val columns: Int,
    val rows: Int,
    val cellSizeMeters: Double = BASE_CELL_METERS,
) {
    init {
        require(columns > 0 && rows > 0) { "cells must be positive: $columns x $rows" }
        require(cellSizeMeters > 0.0) { "cell size must be positive: $cellSizeMeters" }
    }

    public val widthMeters: Double get() = columns * cellSizeMeters
    public val heightMeters: Double get() = rows * cellSizeMeters
    public val rightMeters: Double get() = leftMeters + widthMeters
    public val topMeters: Double get() = bottomMeters + heightMeters
    public val cellCount: Int get() = columns * rows

    /** The middle of the top (upwind) edge: where the windward mark is assumed to be until one is set. */
    public val topCenter: CoursePosition get() = CoursePosition(leftMeters + widthMeters / 2, topMeters)

    /** The cell containing [position], or null when it lies outside the area. */
    public fun cellOf(position: CoursePosition): GridCell? {
        val column = floor((position.acrossMeters - leftMeters) / cellSizeMeters).toInt()
        val row = floor((position.upwindMeters - bottomMeters) / cellSizeMeters).toInt()
        return if (column in 0 until columns && row in 0 until rows) GridCell(column, row) else null
    }

    /** The cell containing [position], or the nearest cell when it lies outside the area. */
    public fun nearestCell(position: CoursePosition): GridCell = GridCell(
        floor((position.acrossMeters - leftMeters) / cellSizeMeters).toInt().coerceIn(0, columns - 1),
        floor((position.upwindMeters - bottomMeters) / cellSizeMeters).toInt().coerceIn(0, rows - 1),
    )

    public fun center(cell: GridCell): CoursePosition = CoursePosition(
        acrossMeters = leftMeters + (cell.column + 0.5) * cellSizeMeters,
        upwindMeters = bottomMeters + (cell.row + 0.5) * cellSizeMeters,
    )

    public fun index(cell: GridCell): Int = cell.row * columns + cell.column

    public fun cellAt(index: Int): GridCell = GridCell(index % columns, index / columns)

    /**
     * Which half of the course a cell lies in, split by the line from the origin towards [axisTarget] (the
     * windward mark); null for a cell centred on that line.
     */
    public fun side(cell: GridCell, axisTarget: CoursePosition): Side? {
        val offset = acrossAxis(center(cell), axisTarget)
        return when {
            offset < 0.0 -> Side.LEFT
            offset > 0.0 -> Side.RIGHT
            else -> null
        }
    }

    public companion object {
        public const val BASE_CELL_METERS: Double = 10.0

        /** More cells than this along the longer side and the 10 m cells are aggregated into bigger ones. */
        public const val MAX_CELLS_PER_SIDE: Int = 12

        /**
         * How far [position] lies to the right (positive) of the axis from the origin towards [axisTarget].
         * When the target is not upwind of the origin the upwind axis through the origin is used instead.
         */
        public fun acrossAxis(position: CoursePosition, axisTarget: CoursePosition): Double {
            if (axisTarget.upwindMeters <= 0.0) return position.acrossMeters
            val length = sqrt(axisTarget.acrossMeters * axisTarget.acrossMeters + axisTarget.upwindMeters * axisTarget.upwindMeters)
            return (position.acrossMeters * axisTarget.upwindMeters - position.upwindMeters * axisTarget.acrossMeters) / length
        }

        /**
         * The smallest area of whole cells around [positions]: 10 m cells aligned on the origin, aggregated
         * into the smallest multiple of 10 m that keeps the longer side within about [maxCellsPerSide] cells.
         */
        public fun covering(
            positions: List<CoursePosition>,
            maxCellsPerSide: Int = MAX_CELLS_PER_SIDE,
            baseCellMeters: Double = BASE_CELL_METERS,
        ): GridSpec {
            require(positions.isNotEmpty()) { "an area needs at least one position" }
            fun down(value: Double, cell: Double) = floor(value / cell) * cell
            fun up(value: Double, cell: Double) = ceil(value / cell) * cell

            var left = down(positions.minOf { it.acrossMeters }, baseCellMeters)
            var right = up(positions.maxOf { it.acrossMeters }, baseCellMeters)
            var bottom = down(positions.minOf { it.upwindMeters }, baseCellMeters)
            var top = up(positions.maxOf { it.upwindMeters }, baseCellMeters)
            if (right <= left) right = left + baseCellMeters
            if (top <= bottom) top = bottom + baseCellMeters

            val extent = max(right - left, top - bottom)
            val cell = ceil(extent / (baseCellMeters * maxCellsPerSide) - EPSILON).coerceAtLeast(1.0) * baseCellMeters
            left = down(left, cell)
            right = up(right, cell)
            bottom = down(bottom, cell)
            top = up(top, cell)
            return GridSpec(left, bottom, ((right - left) / cell).roundToInt(), ((top - bottom) / cell).roundToInt(), cell)
        }

        private const val EPSILON: Double = 1e-9
    }
}

/** A cell address: [column] counts from the left, [row] from the bottom (downwind) edge. */
public data class GridCell(val column: Int, val row: Int)

/**
 * What was observed inside one cell (or one half of the course): how often the boat was there, and the
 * wind and boat speed measured while sailing close-hauled through it.
 */
public data class CellStats(
    val visits: Int = 0,
    val upwindSamples: Int = 0,
    val windCosSum: Double = 0.0,
    val windSinSum: Double = 0.0,
    val speedSum: Double = 0.0,
) {
    /** Circular mean of the wind directions estimated here, or null without upwind samples. */
    public val meanWindDegrees: Double?
        get() = if (upwindSamples == 0) null else Angles.normalize(Angles.toDegrees(atan2(windSinSum, windCosSum)))

    /** Mean close-hauled boat speed here, or null without upwind samples. */
    public val meanSpeedMps: Double? get() = if (upwindSamples == 0) null else speedSum / upwindSamples

    public operator fun plus(other: CellStats): CellStats = CellStats(
        visits + other.visits,
        upwindSamples + other.upwindSamples,
        windCosSum + other.windCosSum,
        windSinSum + other.windSinSum,
        speedSum + other.speedSum,
    )
}

/**
 * The track binned onto the racing area: a histogram of where the boat has been, with the wind and
 * speed observed per cell. Built from scratch from the [Track] so that a change of wind reference or
 * line position simply re-bins everything.
 *
 * @property axisTarget the windward mark, in the frame: the course is split into its two sides along the
 *   line from the origin to it.
 */
public class TrackGrid private constructor(
    public val spec: GridSpec,
    public val axisTarget: CoursePosition,
    private val cells: Array<CellStats>,
) {

    public fun stats(cell: GridCell): CellStats = cells[spec.index(cell)]

    /** Every cell the boat has visited, with its statistics. */
    public val visited: List<Pair<GridCell, CellStats>> by lazy {
        cells.withIndex().filter { it.value.visits > 0 }.map { (index, stats) -> spec.cellAt(index) to stats }
    }

    public val maxVisits: Int get() = cells.maxOf { it.visits }

    public val totalVisits: Int get() = cells.sumOf { it.visits }

    /** Everything observed in one half of the course. */
    public fun side(side: Side): CellStats =
        visited.filter { (cell, _) -> spec.side(cell, axisTarget) == side }.fold(CellStats()) { sum, (_, stats) -> sum + stats }

    override fun equals(other: Any?): Boolean =
        other is TrackGrid && spec == other.spec && axisTarget == other.axisTarget && cells.contentEquals(other.cells)

    override fun hashCode(): Int = 31 * (31 * spec.hashCode() + axisTarget.hashCode()) + cells.contentHashCode()

    override fun toString(): String = "TrackGrid(spec=$spec, visits=$totalVisits)"

    public companion object {

        public fun build(track: Track, frame: CourseFrame, spec: GridSpec, axisTarget: CoursePosition): TrackGrid {
            val count = spec.cellCount
            val visits = IntArray(count)
            val upwind = IntArray(count)
            val cosSum = DoubleArray(count)
            val sinSum = DoubleArray(count)
            val speedSum = DoubleArray(count)
            for (point in track.points) {
                val cell = spec.cellOf(frame.toCourse(point.point)) ?: continue
                val index = spec.index(cell)
                visits[index]++
                val wind = point.upwindWindDegrees ?: continue
                upwind[index]++
                val radians = Angles.toRadians(wind)
                cosSum[index] += cos(radians)
                sinSum[index] += sin(radians)
                speedSum[index] += point.speedMps ?: 0.0
            }
            return TrackGrid(spec, axisTarget, Array(count) { CellStats(visits[it], upwind[it], cosSum[it], sinSum[it], speedSum[it]) })
        }
    }
}
