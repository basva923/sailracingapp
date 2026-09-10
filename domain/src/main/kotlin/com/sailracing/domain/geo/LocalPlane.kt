package com.sailracing.domain.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/**
 * A position on a [LocalPlane], in metres: [eastMeters] east of the plane's origin and [northMeters]
 * north of it. This is the water as a chart shows it, north up, and it is what a course drawn by hand is
 * drawn in; [com.sailracing.domain.course.CoursePosition] is the same water turned wind up.
 */
public data class PlanePosition(val eastMeters: Double = 0.0, val northMeters: Double = 0.0) {

    public operator fun plus(other: PlanePosition): PlanePosition =
        PlanePosition(eastMeters + other.eastMeters, northMeters + other.northMeters)

    public operator fun minus(other: PlanePosition): PlanePosition =
        PlanePosition(eastMeters - other.eastMeters, northMeters - other.northMeters)

    public fun distanceTo(other: PlanePosition): Double =
        hypot(other.eastMeters - eastMeters, other.northMeters - northMeters)

    /** The compass bearing from here to [other]: 0 = north, 90 = east. North for the same point twice. */
    public fun bearingTo(other: PlanePosition): Double =
        Angles.normalize(Angles.toDegrees(atan2(other.eastMeters - eastMeters, other.northMeters - northMeters)))

    /** The point [fraction] of the way from here to [other]; beyond the two of them outside 0..1. */
    public fun towards(other: PlanePosition, fraction: Double): PlanePosition = PlanePosition(
        eastMeters = eastMeters + (other.eastMeters - eastMeters) * fraction,
        northMeters = northMeters + (other.northMeters - northMeters) * fraction,
    )
}

/**
 * A flat, north-up metre grid around [origin]: the projection itself, without any opinion about where the
 * wind is coming from. An equirectangular projection is exact to well under a metre at race-course scale.
 *
 * It is the one place the earth is flattened. [com.sailracing.domain.course.CourseFrame] is this plane
 * turned into the wind, so a track drawn on the plane stays where it was drawn when the wind is set again.
 */
public data class LocalPlane(val origin: GeoPoint) {

    private val cosLatitude = cos(Angles.toRadians(origin.latitude))

    public fun toPlane(point: GeoPoint): PlanePosition = PlanePosition(
        eastMeters = Angles.toRadians(Angles.signedDifference(origin.longitude, point.longitude)) *
            Geo.EARTH_RADIUS_METERS * cosLatitude,
        northMeters = Angles.toRadians(point.latitude - origin.latitude) * Geo.EARTH_RADIUS_METERS,
    )

    public fun toGeo(position: PlanePosition): GeoPoint {
        val latitude = origin.latitude + Angles.toDegrees(position.northMeters / Geo.EARTH_RADIUS_METERS)
        val longitude = origin.longitude + Angles.toDegrees(position.eastMeters / (Geo.EARTH_RADIUS_METERS * cosLatitude))
        return GeoPoint(latitude.coerceIn(-90.0, 90.0), Angles.normalize(longitude + 180.0) - 180.0)
    }
}
