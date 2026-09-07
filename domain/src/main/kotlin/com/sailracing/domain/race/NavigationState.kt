package com.sailracing.domain.race

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.model.PositionFix

/** Where the heading shown to the sailor comes from. */
public enum class HeadingSource { COURSE_OVER_GROUND, COMPASS }

/**
 * The latest sensor picture of the boat.
 *
 * @property compassHeadingDegrees compass heading with the mounting offset already applied.
 * @property headingDegrees the heading in use, chosen by [HeadingSelector].
 */
public data class NavigationState(
    val lastFix: PositionFix? = null,
    val compassHeadingDegrees: Double? = null,
    val headingDegrees: Double? = null,
    val headingSource: HeadingSource? = null,
)

/**
 * Chooses the heading: course over ground while the boat is moving fast enough for it to be meaningful,
 * otherwise the compass. On a boat the GPS course is what actually matters (it includes leeway and current).
 */
public object HeadingSelector {

    public fun select(fix: PositionFix?, compassHeadingDegrees: Double?, settings: RaceSettings): NavigationState {
        val course = fix?.courseDegrees
        val speed = fix?.speedMps
        val courseUsable = course != null && speed != null && speed >= settings.courseMinSpeedMps
        return when {
            courseUsable -> NavigationState(fix, compassHeadingDegrees, Angles.normalize(course), HeadingSource.COURSE_OVER_GROUND)
            compassHeadingDegrees != null -> NavigationState(fix, compassHeadingDegrees, compassHeadingDegrees, HeadingSource.COMPASS)
            else -> NavigationState(fix, null, null, null)
        }
    }
}
