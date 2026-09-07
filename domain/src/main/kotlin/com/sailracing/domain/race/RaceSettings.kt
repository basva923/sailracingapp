package com.sailracing.domain.race

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
 * @property courseMinSpeedMps below this speed the GPS course is unreliable and the compass is used instead.
 * @property fixMaxAgeMillis a fix older than this is considered stale.
 * @property compassOffsetDegrees correction added to the compass, e.g. 180 when the phone is mounted backwards.
 * @property upwindMaxTwaDegrees the boat counts as sailing upwind (for the wind histogram and the upwind speed
 *   statistics) at true wind angles up to this. The wind estimate assumes the boat sails its optimal upwind
 *   angle, so only close-hauled-ish sailing is meaningful; reaching along the line before the start would
 *   otherwise pollute the statistics and the time-to-line speed.
 * @property downwindMinTwaDegrees the boat counts as sailing downwind (for the downwind speed statistics)
 *   at true wind angles from this up to 180.
 * @property maxSamplingTurnRateDegreesPerSecond no wind/speed sample is taken while the heading changes faster
 *   than this, because a heading-based wind estimate is meaningless in the middle of a tack or gybe.
 */
public data class RaceSettings(
    val approachSpeed: ApproachSpeed = ApproachSpeed.AverageUpwindVmg(),
    val minSailingSpeedMps: Double = 0.5,
    val courseMinSpeedMps: Double = 0.5,
    val fixMaxAgeMillis: Long = 5_000L,
    val compassOffsetDegrees: Int = 0,
    val cuePolicy: CuePolicy = CuePolicy(),
    val upwindMaxTwaDegrees: Int = DEFAULT_UPWIND_MAX_TWA,
    val downwindMinTwaDegrees: Int = DEFAULT_DOWNWIND_MIN_TWA,
    val maxSamplingTurnRateDegreesPerSecond: Double = DEFAULT_MAX_SAMPLING_TURN_RATE,
    val windHistoryCapacity: Int = 3600,
) {
    public companion object {
        public const val DEFAULT_UPWIND_MAX_TWA: Int = 60
        public const val DEFAULT_DOWNWIND_MIN_TWA: Int = 120
        public const val DEFAULT_MAX_SAMPLING_TURN_RATE: Double = 8.0
    }
}
