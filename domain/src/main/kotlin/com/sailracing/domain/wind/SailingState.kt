package com.sailracing.domain.wind

/** The side of the boat the wind comes over. */
public enum class Tack { PORT, STARBOARD }

public enum class PointOfSail {
    /** True wind angle within 90 degrees of the bow. */
    UPWIND,

    /** True wind angle more than 90 degrees from the bow. */
    DOWNWIND,
}

/**
 * How the boat sits relative to the wind.
 *
 * @property trueWindAngleDegrees wind direction relative to the bow in (-180, 180];
 *   positive when the wind comes over the starboard side.
 */
public data class SailingState(
    val tack: Tack,
    val pointOfSail: PointOfSail,
    val trueWindAngleDegrees: Double,
)

/** The optimal headings on each tack, upwind and downwind, for a given wind. */
public data class TargetHeadings(
    val starboardUpwind: Double,
    val portUpwind: Double,
    val starboardDownwind: Double,
    val portDownwind: Double,
)
