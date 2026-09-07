package com.sailracing.domain.startline

/** Which side of the start line a position is on. */
public enum class LineSide {
    /** The side boats wait on before the start: downwind of the line. */
    PRE_START,

    /** The side of the course: upwind of the line. Being here before the start means you are over early. */
    COURSE,

    /** Unknown because no wind direction is configured. */
    UNKNOWN,
}

/**
 * Where a position is relative to the start line.
 *
 * @property distanceMeters shortest distance to the line segment; when the position is beyond one
 *   of the ends this is the distance to that end, so it never underestimates the sailing distance.
 * @property perpendicularDistanceMeters unsigned distance to the (infinitely extended) line.
 * @property alongFraction 0 at the pin end, 1 at the boat end, outside [0, 1] beyond the ends.
 * @property side which side of the line the position is on.
 */
public data class StartLineSolution(
    val distanceMeters: Double,
    val perpendicularDistanceMeters: Double,
    val alongFraction: Double,
    val side: LineSide,
) {
    /** True when the position lies between the two ends (perpendicular foot is on the segment). */
    public val isBetweenEnds: Boolean get() = alongFraction in 0.0..1.0
}
