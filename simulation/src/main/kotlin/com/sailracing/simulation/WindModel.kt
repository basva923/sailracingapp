package com.sailracing.simulation

import com.sailracing.domain.geo.Angles
import kotlin.math.PI
import kotlin.math.sin

/**
 * True wind over the racing area: a direction and a strength, both of which change with time and with
 * where on the course they are measured, as real wind does.
 *
 * The direction oscillates around a mean, as it does over a beat, may turn steadily one way (a persistent
 * shift), and may differ from one side of the course to the other, as it does near a shore. The strength
 * puffs and lulls, may build or die over the afternoon, and may be stronger on one side of the course.
 *
 * @property meanDirectionDegrees direction the wind blows from, on the course axis, at the start.
 * @property oscillationDegrees amplitude of the oscillation (peak shift to either side).
 * @property periodSeconds duration of one full oscillation.
 * @property shearDegreesPerMeter how much the wind veers per metre to the right of the course axis
 *   (looking upwind); negative when it backs to the right.
 * @property veerDegreesPerHour a persistent shift: how far the wind turns clockwise in an hour
 *   (negative to back). This is the shift that does not come back, so the side of the course it comes
 *   from is the side that pays.
 * @property meanSpeedKnots the wind strength on the course axis, at the start.
 * @property gustKnots amplitude of the puffs and lulls (peak either side of the mean).
 * @property gustPeriodSeconds duration of one full puff-lull cycle.
 * @property speedShearKnotsPerMeter how much stronger the wind is per metre to the right of the course
 *   axis; negative when the pressure is on the left.
 * @property buildKnotsPerHour how much the whole breeze builds in an hour (negative when it dies).
 */
public data class WindModel(
    val meanDirectionDegrees: Double,
    val oscillationDegrees: Double = 0.0,
    val periodSeconds: Double = 240.0,
    val shearDegreesPerMeter: Double = 0.0,
    val veerDegreesPerHour: Double = 0.0,
    val meanSpeedKnots: Double = DEFAULT_SPEED_KNOTS,
    val gustKnots: Double = 0.0,
    val gustPeriodSeconds: Double = 180.0,
    val speedShearKnotsPerMeter: Double = 0.0,
    val buildKnotsPerHour: Double = 0.0,
) {
    init {
        require(periodSeconds > 0.0) { "period must be positive: $periodSeconds" }
        require(gustPeriodSeconds > 0.0) { "gust period must be positive: $gustPeriodSeconds" }
        require(meanSpeedKnots > 0.0) { "mean wind speed must be positive: $meanSpeedKnots" }
    }

    /** The wind on the course axis. */
    public fun directionAt(timeSeconds: Double): Double = directionAt(timeSeconds, acrossMeters = 0.0)

    /** The wind [acrossMeters] to the right of the course axis. */
    public fun directionAt(timeSeconds: Double, acrossMeters: Double): Double = Angles.normalize(
        meanDirectionDegrees +
            oscillationDegrees * sin(2 * PI * timeSeconds / periodSeconds) +
            shearDegreesPerMeter * acrossMeters +
            veerDegreesPerHour * timeSeconds / SECONDS_PER_HOUR,
    )

    /** The wind strength on the course axis, in knots. */
    public fun speedKnotsAt(timeSeconds: Double): Double = speedKnotsAt(timeSeconds, acrossMeters = 0.0)

    /** The wind strength [acrossMeters] to the right of the course axis, in knots. Never quite calm. */
    public fun speedKnotsAt(timeSeconds: Double, acrossMeters: Double): Double = (
        meanSpeedKnots +
            gustKnots * sin(2 * PI * timeSeconds / gustPeriodSeconds) +
            speedShearKnotsPerMeter * acrossMeters +
            buildKnotsPerHour * timeSeconds / SECONDS_PER_HOUR
        ).coerceAtLeast(MIN_SPEED_KNOTS)

    public companion object {
        /** A middling breeze: what the boat polar is measured in. */
        public const val DEFAULT_SPEED_KNOTS: Double = 12.0

        /** Below this the boat would not be sailing at all, so the simulation never goes there. */
        public const val MIN_SPEED_KNOTS: Double = 1.0

        internal const val SECONDS_PER_HOUR: Double = 3600.0
    }
}
