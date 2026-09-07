package com.sailracing.domain.wind

import com.sailracing.domain.geo.Angles

/**
 * The sailor's model of the wind and the boat's pointing ability.
 *
 * @property directionDegrees direction the wind blows FROM, 0..359.
 * @property tackAngleDegrees angle between the wind and the closest course the boat can hold upwind.
 * @property downwindAngleDegrees true wind angle (from the bow) of the fastest course downwind.
 */
public data class WindSettings(
    val directionDegrees: Int = 0,
    val tackAngleDegrees: Int = DEFAULT_TACK_ANGLE,
    val downwindAngleDegrees: Int = DEFAULT_DOWNWIND_ANGLE,
) {
    init {
        require(directionDegrees in 0..359) { "wind direction must be 0..359: $directionDegrees" }
        require(tackAngleDegrees in TACK_ANGLE_RANGE) { "tack angle must be in $TACK_ANGLE_RANGE: $tackAngleDegrees" }
        require(downwindAngleDegrees in DOWNWIND_ANGLE_RANGE) {
            "downwind angle must be in $DOWNWIND_ANGLE_RANGE: $downwindAngleDegrees"
        }
    }

    public fun withDirection(degrees: Int): WindSettings = copy(directionDegrees = Angles.normalize(degrees))

    public fun withTackAngle(degrees: Int): WindSettings = copy(tackAngleDegrees = degrees.coerceIn(TACK_ANGLE_RANGE))

    public fun withDownwindAngle(degrees: Int): WindSettings =
        copy(downwindAngleDegrees = degrees.coerceIn(DOWNWIND_ANGLE_RANGE))

    public companion object {
        public const val DEFAULT_TACK_ANGLE: Int = 45
        public const val DEFAULT_DOWNWIND_ANGLE: Int = 140
        public val TACK_ANGLE_RANGE: IntRange = 20..80
        public val DOWNWIND_ANGLE_RANGE: IntRange = 100..179
    }
}
