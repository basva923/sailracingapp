package com.sailracing.domain.course

/**
 * What the boat sails through, square by square: a wind direction and a close-hauled boat speed for every
 * square of the racing area. It is what the race line search reads, and there are two of them: the wind as
 * it has been measured ([WindField], one mean per square) and one drawn realisation of it
 * ([SampledConditions], one of the many the Monte Carlo tries).
 */
public interface SailingConditions {

    public val spec: GridSpec

    /** The wind in [cell]: degrees off the reference wind, positive when veered (clockwise). */
    public fun shiftDegrees(cell: GridCell): Double

    /** The speed the boat makes close-hauled in [cell], in metres per second. */
    public fun speedMps(cell: GridCell): Double

    public companion object {
        /**
         * The close-hauled speed assumed where nothing has been measured yet: a dinghy beating at 3 m/s
         * (5.8 knots). Only the ratio between it and what a tack costs can change which line wins.
         */
        public const val DEFAULT_BOAT_SPEED_MPS: Double = 3.0
    }
}

/** The wind where [position] is, from the square holding it (the nearest one when it is outside the area). */
public fun SailingConditions.shiftAt(position: CoursePosition): Double = shiftDegrees(spec.nearestCell(position))

/** The close-hauled speed where [position] is, from the square holding it. */
public fun SailingConditions.speedAt(position: CoursePosition): Double = speedMps(spec.nearestCell(position))
