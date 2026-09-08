package com.sailracing.domain.course

/**
 * How the racing area is cut into squares.
 *
 * The area itself is never a setting - it follows the track ([GridSpec.covering]) - but the size of the
 * squares it is cut into is a real choice. Small squares see a puff on one side of the course that big
 * ones average away; big squares gather more samples each, so what they say about the wind is worth more,
 * and the race line's Monte Carlo has fewer of them to search. Nothing is thrown away by changing it: the
 * grid, the wind field and the race line are all derived from the track and are simply built again.
 *
 * @property cellSizeMeters the square size the sailor asked for, in metres, rounded to a multiple of
 *   [GridSpec.BASE_CELL_METERS]; null to let the area choose one, which is what it does by default.
 * @property maxCellsPerSide how many squares an automatically sized area may have along its longer side
 *   before its squares are made bigger. It does not apply to a size the sailor chose, which is only
 *   overruled by [GridSpec.CELL_LIMIT_PER_SIDE], the point beyond which the search would be too slow.
 */
public data class GridSettings(
    val cellSizeMeters: Double? = null,
    val maxCellsPerSide: Int = GridSpec.MAX_CELLS_PER_SIDE,
) {
    init {
        require(cellSizeMeters == null || cellSizeMeters >= GridSpec.BASE_CELL_METERS) {
            "a square is at least ${GridSpec.BASE_CELL_METERS} m: $cellSizeMeters"
        }
        require(maxCellsPerSide in 1..GridSpec.CELL_LIMIT_PER_SIDE) {
            "an area holds 1..${GridSpec.CELL_LIMIT_PER_SIDE} squares along a side: $maxCellsPerSide"
        }
    }

    public companion object {
        /** The sizes the settings screen offers, in metres: from half a boat length up to a cable. */
        public val CELL_SIZE_RANGE_METERS: ClosedFloatingPointRange<Double> = GridSpec.BASE_CELL_METERS..200.0
    }
}
