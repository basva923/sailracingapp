package com.sailracing.domain.race

import com.sailracing.domain.course.CourseSettings
import com.sailracing.domain.timer.CuePolicy

/** How the speed used for "time to line" is chosen. */
public sealed interface ApproachSpeed {

    /** A fixed speed entered by the sailor, in metres per second. */
    public data class Manual(val speedMps: Double) : ApproachSpeed {
        init {
            require(speedMps > 0.0) { "speed must be positive: $speedMps" }
        }
    }

    /** The measured average upwind VMG, falling back to [fallbackMps] until enough has been sailed. */
    public data class AverageUpwindVmg(val fallbackMps: Double = DEFAULT_FALLBACK_MPS) : ApproachSpeed {
        init {
            require(fallbackMps > 0.0) { "fallback speed must be positive: $fallbackMps" }
        }

        public companion object {
            public const val DEFAULT_FALLBACK_MPS: Double = 1.5
        }
    }
}

/**
 * Tunables that rarely change during a race.
 *
 * @property minSailingSpeedMps below this speed the boat is considered not sailing; no wind/speed samples are taken.
 * @property courseMinSpeedMps below this speed the GPS course over ground is meaningless: it is not used.
 * @property fixMaxAgeMillis a fix older than this is considered stale.
 * @property headingSource which of the two the heading is taken from, see [HeadingSource]. The GPS course
 *   costs nothing extra and needs no mounting; the compass is only read when it is the chosen source.
 * @property compassOffsetDegrees correction added to the compass, e.g. 180 when the phone is mounted backwards.
 * @property closeHauledBandDegrees the boat counts as close-hauled (for the wind it measures, the histogram,
 *   the upwind speed statistics and the tack advice) while it points no more than this below its tack angle
 *   off the reference wind. The wind estimate assumes the boat sails its close-hauled angle, so only
 *   close-hauled sailing is meaningful: a boat footing a little in a header still counts, a boat reaching
 *   along the line before the start does not, or it would pollute the wind and the time-to-line speed.
 * @property downwindMinTwaDegrees the boat counts as sailing downwind (for the downwind speed statistics)
 *   at true wind angles from this up to 180.
 * @property maxSamplingTurnRateDegreesPerSecond no wind/speed sample is taken while the heading changes faster
 *   than this, because a heading-based wind estimate is meaningless in the middle of a tack or gybe.
 * @property course how the racing area is cut into squares, what its wind is believed to be like, and how
 *   the race line over it is searched for.
 */
public data class RaceSettings(
    val approachSpeed: ApproachSpeed = ApproachSpeed.AverageUpwindVmg(),
    val minSailingSpeedMps: Double = 0.5,
    val courseMinSpeedMps: Double = 0.5,
    val fixMaxAgeMillis: Long = 5_000L,
    val headingSource: HeadingSource = HeadingSource.COURSE_OVER_GROUND,
    val compassOffsetDegrees: Int = 0,
    val cuePolicy: CuePolicy = CuePolicy(),
    val closeHauledBandDegrees: Int = DEFAULT_CLOSE_HAULED_BAND,
    val downwindMinTwaDegrees: Int = DEFAULT_DOWNWIND_MIN_TWA,
    val maxSamplingTurnRateDegreesPerSecond: Double = DEFAULT_MAX_SAMPLING_TURN_RATE,
    val windHistoryCapacity: Int = 3600,
    val course: CourseSettings = CourseSettings(),
) {
    public companion object {
        public const val DEFAULT_CLOSE_HAULED_BAND: Int = 20

        /** The bands the settings screen offers: from strict to a boat that foots a lot. */
        public val CLOSE_HAULED_BAND_RANGE: IntRange = 10..35
        public const val DEFAULT_DOWNWIND_MIN_TWA: Int = 120
        public const val DEFAULT_MAX_SAMPLING_TURN_RATE: Double = 8.0
    }
}
