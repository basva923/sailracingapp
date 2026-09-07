package com.sailracing.domain.wind

/**
 * The wind everything tactical is judged against: the weighted centre (circular mean) of the histogram of
 * measured upwind samples once there are enough of them, and the set wind until then.
 *
 * The set wind is only a rough, general direction the sailor enters: it seeds the sampling and orients the
 * map until the measurements take over. The real wind oscillates around the measured centre, and that centre
 * is what a shift, a lift or a header is measured from.
 */
public data class WindReference(val directionDegrees: Double, val isMeasured: Boolean) {

    public companion object {
        /** Fewer histogram samples than this and the set wind is the reference instead of the mean. */
        public const val MIN_HISTOGRAM_SAMPLES: Int = 30

        public fun of(settings: WindSettings, histogram: WindHistogram): WindReference {
            val mean = histogram.meanDirection()?.takeIf { histogram.totalSamples >= MIN_HISTOGRAM_SAMPLES }
            return WindReference(mean ?: settings.directionDegrees.toDouble(), mean != null)
        }
    }
}
