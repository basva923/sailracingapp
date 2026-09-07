package com.sailracing.domain.model

import com.sailracing.domain.geo.GeoPoint

/**
 * One GPS observation. Speed and course are "over ground" and may be missing when the receiver
 * cannot determine them (e.g. when stationary).
 */
public data class PositionFix(
    val point: GeoPoint,
    val timestampMillis: Long,
    val speedMps: Double? = null,
    val courseDegrees: Double? = null,
    val accuracyMeters: Double? = null,
)
