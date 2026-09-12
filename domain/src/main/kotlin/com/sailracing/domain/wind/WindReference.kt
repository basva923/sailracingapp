package com.sailracing.domain.wind

import com.sailracing.domain.geo.Angles
import kotlin.math.abs

/**
 * The wind everything is judged against, and the angle the boat is believed to sail to it.
 *
 * It is the middle of what the boat has measured close-hauled over the last [WINDOW_MILLIS]: the circular
 * median of its wind estimates ([WindHistory.medianCloseHauledDirectionDegrees]), so that the histogram of
 * the wind is what a shift is judged against - half the samples veered from the reference, half backed -
 * and a reach that got counted, or the tail of a shift that has passed, cannot pull it far. The set wind
 * is the reference until [MIN_SAMPLES] have been measured.
 *
 * On the side, the two tacks are compared: the heading held close-hauled on starboard and the one held on
 * port lie two tack angles apart, and half of that is the tack angle the boat is actually sailing,
 * [measuredTackAngleDegrees], shown to the sailor to set the tack angle by. The estimates keep to the set
 * angle, [tackAngleDegrees], so that they mean what the sailor set and do not wander with a measurement.
 *
 * @property isMeasured true when the direction was read off the boat rather than typed in.
 * @property measuredTackAngleDegrees half the angle between the two tacks' headings when both have been
 *   sailed lately and lie about two set tack angles apart, else the last such measurement handed in;
 *   null until there has been one.
 */
public data class WindReference(
    val directionDegrees: Double,
    val isMeasured: Boolean,
    val tackAngleDegrees: Double = WindSettings.DEFAULT_TACK_ANGLE.toDouble(),
    val measuredTackAngleDegrees: Double? = null,
) {

    public companion object {
        /** Fewer close-hauled samples than this and the set wind is the reference; and a tack's heading needs as many. */
        public const val MIN_SAMPLES: Int = 30

        /** How far back the close-hauled samples are taken: a couple of oscillations of the wind. */
        public const val WINDOW_MILLIS: Long = 30L * 60L * 1000L

        /** The measured tack angle may differ from the set one by this much before the pair is doubted. */
        public const val MAX_TACK_ANGLE_ERROR_DEGREES: Double = 10.0

        public fun of(settings: WindSettings, history: WindHistory, measuredTackAngleDegrees: Double?, nowMillis: Long): WindReference {
            val since = nowMillis - WINDOW_MILLIS
            val angle = settings.tackAngleDegrees.toDouble()
            val median = history.medianCloseHauledDirectionDegrees(since, MIN_SAMPLES)
            val measured = measuredTackAngleDegrees(settings, history, nowMillis) ?: measuredTackAngleDegrees
            return if (median != null) {
                WindReference(median, true, angle, measured)
            } else {
                WindReference(settings.directionDegrees.toDouble(), false, angle, measured)
            }
        }

        /**
         * The tack angle the boat is sailing: half the angle from the close-hauled heading on starboard
         * round to the one on port, when both have been sailed in the window before [nowMillis] and the
         * pair is within [MAX_TACK_ANGLE_ERROR_DEGREES] of the set angle - a pair further off has a reach
         * in it and says nothing about the angle. Null otherwise.
         */
        public fun measuredTackAngleDegrees(settings: WindSettings, history: WindHistory, nowMillis: Long): Double? {
            val since = nowMillis - WINDOW_MILLIS
            val starboard = history.closeHauledHeadingDegrees(Tack.STARBOARD, since, MIN_SAMPLES) ?: return null
            val port = history.closeHauledHeadingDegrees(Tack.PORT, since, MIN_SAMPLES) ?: return null
            // Port's close-hauled heading lies clockwise of starboard's, two tack angles round.
            val half = Angles.signedDifference(starboard, port) / 2
            return half.takeIf { abs(it - settings.tackAngleDegrees) <= MAX_TACK_ANGLE_ERROR_DEGREES }
        }
    }
}
