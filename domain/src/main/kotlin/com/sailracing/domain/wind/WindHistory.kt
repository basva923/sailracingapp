package com.sailracing.domain.wind

import com.sailracing.domain.geo.Angles
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** One estimated wind direction, with whether the boat was sailing upwind at the time. */
public data class WindSample(val timestampMillis: Long, val directionDegrees: Double, val upwind: Boolean)

/** A bounded, chronological list of wind samples; oldest samples drop off when [capacity] is exceeded. */
public data class WindHistory(val samples: List<WindSample> = emptyList(), val capacity: Int = DEFAULT_CAPACITY) {

    init {
        require(capacity > 0) { "capacity must be positive: $capacity" }
    }

    public val latest: WindSample? get() = samples.lastOrNull()

    public operator fun plus(sample: WindSample): WindHistory {
        val updated = if (samples.size >= capacity) samples.drop(samples.size - capacity + 1) + sample else samples + sample
        return copy(samples = updated)
    }

    public fun last(count: Int): List<WindSample> = samples.takeLast(count)

    /** The most recent [count] samples taken while beating, oldest first: what the wind is doing lately. */
    public fun recentUpwind(count: Int): List<WindSample> = samples.filter { it.upwind }.takeLast(count)

    /**
     * The wind the boat has been measuring over its last [count] close-hauled samples, or null when it has
     * none. Over a handful of samples this is the wind of the moment, steadied against the wave that
     * knocked the boat off course; over hundreds of them it is the trend.
     */
    public fun recentUpwindMeanDegrees(count: Int): Double? = meanDirectionDegrees(recentUpwind(count))

    public fun withCapacity(newCapacity: Int): WindHistory = WindHistory(samples.takeLast(newCapacity), newCapacity)

    public companion object {
        public const val DEFAULT_CAPACITY: Int = 3600

        /** The circular mean direction of [samples] in [0, 360), or null when there are none. */
        public fun meanDirectionDegrees(samples: List<WindSample>): Double? {
            if (samples.isEmpty()) return null
            var c = 0.0
            var s = 0.0
            for (sample in samples) {
                val radians = Angles.toRadians(sample.directionDegrees)
                c += cos(radians)
                s += sin(radians)
            }
            return Angles.normalize(Angles.toDegrees(atan2(s, c)))
        }
    }
}
