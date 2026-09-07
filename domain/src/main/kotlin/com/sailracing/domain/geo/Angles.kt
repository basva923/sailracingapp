package com.sailracing.domain.geo

import kotlin.math.PI

/** Angle helpers. All angles are compass degrees: 0 = north, 90 = east, increasing clockwise. */
public object Angles {

    /** Maps any angle onto [0, 360). */
    public fun normalize(degrees: Double): Double {
        val remainder = degrees % 360.0
        val wrapped = if (remainder < 0.0) remainder + 360.0 else remainder
        // A tiny negative input rounds up to exactly 360.0; "+ 0.0" turns a negative zero into a positive zero.
        return if (wrapped >= 360.0) 0.0 else wrapped + 0.0
    }

    /** Maps any angle onto [0, 360). */
    public fun normalize(degrees: Int): Int = ((degrees % 360) + 360) % 360

    /**
     * Shortest signed rotation that turns [fromDegrees] into [toDegrees], in (-180, 180].
     * Positive means [toDegrees] lies clockwise (to the right) of [fromDegrees].
     */
    public fun signedDifference(fromDegrees: Double, toDegrees: Double): Double {
        val difference = normalize(toDegrees - fromDegrees)
        return if (difference > 180.0) difference - 360.0 else difference
    }

    public fun toRadians(degrees: Double): Double = degrees * PI / 180.0

    public fun toDegrees(radians: Double): Double = radians * 180.0 / PI
}
