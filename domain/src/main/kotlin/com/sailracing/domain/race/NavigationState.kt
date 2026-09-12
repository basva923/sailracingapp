package com.sailracing.domain.race

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.model.PositionFix

/**
 * Where a heading comes from. Also the sailor's choice in [RaceSettings.headingSource]: which of the two
 * the app should believe when both have something to say.
 *
 * [COURSE_OVER_GROUND] is where the boat actually goes, drift and current included, and asks nothing of
 * where the phone lies - but it only exists while the boat moves and it lags a second or so behind.
 * [COMPASS] answers at once, at any speed, and points more precisely, but only means anything with the
 * phone held still in the boat's axis (see `DeviceHeading`).
 */
public enum class HeadingSource { COURSE_OVER_GROUND, COMPASS }

/**
 * The latest sensor picture of the boat.
 *
 * @property compassHeadingDegrees compass heading with the mounting offset already applied, kept even when
 *   the heading itself is taken from the GPS.
 * @property headingDegrees the heading in use, chosen by [HeadingSelector].
 */
public data class NavigationState(
    val lastFix: PositionFix? = null,
    val compassHeadingDegrees: Double? = null,
    val headingDegrees: Double? = null,
    val headingSource: HeadingSource? = null,
)

/**
 * Chooses the heading from the source the sailor picked, falling back to the other one only where the
 * chosen one says nothing at all.
 *
 * With [HeadingSource.COMPASS] the compass wins whenever it has a reading, and the GPS course fills in
 * until it has one (a device without a rotation sensor, or a simulated race). With
 * [HeadingSource.COURSE_OVER_GROUND] the course is used while the boat moves faster than
 * [RaceSettings.courseMinSpeedMps] and there is no heading below that: a phone that need not be fixed to
 * anything cannot be asked which way the boat points while it is stopped.
 */
public object HeadingSelector {

    public fun select(fix: PositionFix?, compassHeadingDegrees: Double?, settings: RaceSettings): NavigationState {
        val speed = fix?.speedMps
        val course = fix?.courseDegrees
            ?.takeIf { speed != null && speed >= settings.courseMinSpeedMps }
            ?.let(Angles::normalize)
        val compassFirst = settings.headingSource == HeadingSource.COMPASS
        return when {
            compassFirst && compassHeadingDegrees != null ->
                NavigationState(fix, compassHeadingDegrees, compassHeadingDegrees, HeadingSource.COMPASS)
            course != null -> NavigationState(fix, compassHeadingDegrees, course, HeadingSource.COURSE_OVER_GROUND)
            else -> NavigationState(fix, compassHeadingDegrees, null, null)
        }
    }
}
