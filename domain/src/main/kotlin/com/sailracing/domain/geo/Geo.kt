package com.sailracing.domain.geo

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Great-circle geometry on a spherical earth. Accurate to well under a metre at race-course scale. */
public object Geo {

    public const val EARTH_RADIUS_METERS: Double = 6_371_000.0

    /** Haversine distance between two points, in metres. */
    public fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Angles.toRadians(a.latitude)
        val lat2 = Angles.toRadians(b.latitude)
        val dLat = Angles.toRadians(b.latitude - a.latitude)
        val dLon = Angles.toRadians(b.longitude - a.longitude)

        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Initial (forward) bearing from [from] to [to], normalised to [0, 360). */
    public fun initialBearingDegrees(from: GeoPoint, to: GeoPoint): Double =
        Angles.normalize(Angles.toDegrees(initialBearingRadians(from, to)))

    /**
     * Signed distance from [point] to the great circle through [lineStart] and [lineEnd].
     * Positive when the point lies to the right of the direction lineStart -> lineEnd, negative to the left.
     */
    public fun crossTrackDistanceMeters(lineStart: GeoPoint, lineEnd: GeoPoint, point: GeoPoint): Double {
        val angularDistance = distanceMeters(lineStart, point) / EARTH_RADIUS_METERS
        val bearingToPoint = initialBearingRadians(lineStart, point)
        val bearingOfLine = initialBearingRadians(lineStart, lineEnd)
        return asin(sin(angularDistance) * sin(bearingToPoint - bearingOfLine)) * EARTH_RADIUS_METERS
    }

    /**
     * Signed distance along the line from [lineStart] to the foot of the perpendicular dropped from [point].
     * Negative when the foot lies "behind" [lineStart], i.e. on the far side from [lineEnd].
     */
    public fun alongTrackDistanceMeters(lineStart: GeoPoint, lineEnd: GeoPoint, point: GeoPoint): Double {
        val angularDistance = distanceMeters(lineStart, point) / EARTH_RADIUS_METERS
        val crossTrack = crossTrackDistanceMeters(lineStart, lineEnd, point) / EARTH_RADIUS_METERS
        val ratio = (cos(angularDistance) / cos(crossTrack)).coerceIn(-1.0, 1.0)
        val magnitude = acos(ratio) * EARTH_RADIUS_METERS

        val bearingToPoint = initialBearingDegrees(lineStart, point)
        val bearingOfLine = initialBearingDegrees(lineStart, lineEnd)
        val behind = abs(Angles.signedDifference(bearingOfLine, bearingToPoint)) > 90.0
        return if (behind) -magnitude else magnitude
    }

    /** The point reached after travelling [distanceMeters] from [from] on the initial bearing [bearingDegrees]. */
    public fun destination(from: GeoPoint, bearingDegrees: Double, distanceMeters: Double): GeoPoint {
        val angularDistance = distanceMeters / EARTH_RADIUS_METERS
        val bearing = Angles.toRadians(bearingDegrees)
        val lat1 = Angles.toRadians(from.latitude)
        val lon1 = Angles.toRadians(from.longitude)

        val lat2 = asin(sin(lat1) * cos(angularDistance) + cos(lat1) * sin(angularDistance) * cos(bearing))
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angularDistance) * cos(lat1),
            cos(angularDistance) - sin(lat1) * sin(lat2),
        )
        val longitude = Angles.normalize(Angles.toDegrees(lon2) + 180.0) - 180.0
        return GeoPoint(Angles.toDegrees(lat2), longitude)
    }

    private fun initialBearingRadians(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Angles.toRadians(from.latitude)
        val lat2 = Angles.toRadians(to.latitude)
        val dLon = Angles.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return atan2(y, x)
    }
}
