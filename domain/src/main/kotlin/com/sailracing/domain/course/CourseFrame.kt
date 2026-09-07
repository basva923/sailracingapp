package com.sailracing.domain.course

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import kotlin.math.cos
import kotlin.math.sin

/**
 * A position in a [CourseFrame], in metres: [acrossMeters] across the wind (positive to the right when
 * looking upwind) and [upwindMeters] towards the wind.
 */
public data class CoursePosition(val acrossMeters: Double, val upwindMeters: Double)

/**
 * A flat, wind-up frame over the racing area. The origin is the reference point (normally the middle of
 * the start line), the upwind axis points into the wind and the across axis to the right when looking
 * upwind. An equirectangular projection is exact to well under a metre at race-course scale.
 */
public data class CourseFrame(val origin: GeoPoint, val windDirectionDegrees: Double) {

    private val cosLatitude = cos(Angles.toRadians(origin.latitude))
    private val cosWind = cos(Angles.toRadians(windDirectionDegrees))
    private val sinWind = sin(Angles.toRadians(windDirectionDegrees))

    public fun toCourse(point: GeoPoint): CoursePosition {
        val north = Angles.toRadians(point.latitude - origin.latitude) * Geo.EARTH_RADIUS_METERS
        val east = Angles.toRadians(Angles.signedDifference(origin.longitude, point.longitude)) * Geo.EARTH_RADIUS_METERS * cosLatitude
        return CoursePosition(
            acrossMeters = east * cosWind - north * sinWind,
            upwindMeters = north * cosWind + east * sinWind,
        )
    }

    public fun toGeo(position: CoursePosition): GeoPoint {
        val east = position.acrossMeters * cosWind + position.upwindMeters * sinWind
        val north = position.upwindMeters * cosWind - position.acrossMeters * sinWind
        val latitude = origin.latitude + Angles.toDegrees(north / Geo.EARTH_RADIUS_METERS)
        val longitude = origin.longitude + Angles.toDegrees(east / (Geo.EARTH_RADIUS_METERS * cosLatitude))
        return GeoPoint(latitude.coerceIn(-90.0, 90.0), Angles.normalize(longitude + 180.0) - 180.0)
    }

    /** A compass bearing expressed in the frame: 0 = straight upwind, 90 = to the right. */
    public fun toCourseBearing(bearingDegrees: Double): Double = Angles.normalize(bearingDegrees - windDirectionDegrees)
}
