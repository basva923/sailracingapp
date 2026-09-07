package com.sailracing.domain.race

import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.model.PositionFix

/** Shared builders for race tests. */
internal object TestFixtures {
    val origin = GeoPoint(51.14, 5.83)

    fun fix(
        timestampMillis: Long,
        point: GeoPoint = origin,
        speedMps: Double? = 3.0,
        courseDegrees: Double? = 315.0,
        accuracyMeters: Double? = 4.0,
    ) = PositionFix(point, timestampMillis, speedMps, courseDegrees, accuracyMeters)

    fun north(meters: Double, from: GeoPoint = origin): GeoPoint = Geo.destination(from, 0.0, meters)
    fun east(meters: Double, from: GeoPoint = origin): GeoPoint = Geo.destination(from, 90.0, meters)
}
