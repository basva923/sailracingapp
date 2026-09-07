package com.sailracing.domain.course

import com.sailracing.domain.geo.GeoPoint

/**
 * One recorded position of the boat.
 *
 * @property upwindWindDegrees the wind direction estimated at this point when it was a valid close-hauled
 *   wind sample (the same rule that feeds the wind histogram), otherwise null.
 */
public data class TrackPoint(
    val timestampMillis: Long,
    val point: GeoPoint,
    val speedMps: Double? = null,
    val headingDegrees: Double? = null,
    val upwindWindDegrees: Double? = null,
)

/** Where the boat has been: a bounded, chronological list of points; the oldest drop off beyond [capacity]. */
public data class Track(val points: List<TrackPoint> = emptyList(), val capacity: Int = DEFAULT_CAPACITY) {

    init {
        require(capacity > 0) { "capacity must be positive: $capacity" }
    }

    public val latest: TrackPoint? get() = points.lastOrNull()

    public val isEmpty: Boolean get() = points.isEmpty()

    public operator fun plus(point: TrackPoint): Track {
        val updated = if (points.size >= capacity) points.drop(points.size - capacity + 1) + point else points + point
        return copy(points = updated)
    }

    public companion object {
        /** Four hours at one point per second. */
        public const val DEFAULT_CAPACITY: Int = 4 * 3600
    }
}
