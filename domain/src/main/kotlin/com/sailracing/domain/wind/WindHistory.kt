package com.sailracing.domain.wind

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

    public fun withCapacity(newCapacity: Int): WindHistory = WindHistory(samples.takeLast(newCapacity), newCapacity)

    public companion object {
        public const val DEFAULT_CAPACITY: Int = 3600
    }
}
