package com.sailracing.domain.course

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.geo.LocalPlane
import com.sailracing.domain.geo.PlanePosition
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
 * upwind.
 *
 * It is a [LocalPlane] - the same water, north up - turned into the wind, and nothing more: setting
 * another reference wind turns the frame, not the water. That is why the track and the marks are kept on
 * the plane and only ever brought into a frame to be worked with.
 */
public data class CourseFrame(val origin: GeoPoint, val windDirectionDegrees: Double) {

    /** The water this frame is a rotation of: metres east and north of [origin]. */
    public val plane: LocalPlane = LocalPlane(origin)

    private val cosWind = cos(Angles.toRadians(windDirectionDegrees))
    private val sinWind = sin(Angles.toRadians(windDirectionDegrees))

    public fun toCourse(point: GeoPoint): CoursePosition = fromPlane(plane.toPlane(point))

    public fun toGeo(position: CoursePosition): GeoPoint = plane.toGeo(toPlane(position))

    /** A north-up position turned into this frame. */
    public fun fromPlane(position: PlanePosition): CoursePosition = CoursePosition(
        acrossMeters = position.eastMeters * cosWind - position.northMeters * sinWind,
        upwindMeters = position.northMeters * cosWind + position.eastMeters * sinWind,
    )

    /** A position in this frame turned back onto the north-up plane. */
    public fun toPlane(position: CoursePosition): PlanePosition = PlanePosition(
        eastMeters = position.acrossMeters * cosWind + position.upwindMeters * sinWind,
        northMeters = position.upwindMeters * cosWind - position.acrossMeters * sinWind,
    )

    /** A compass bearing expressed in the frame: 0 = straight upwind, 90 = to the right. */
    public fun toCourseBearing(bearingDegrees: Double): Double = Angles.normalize(bearingDegrees - windDirectionDegrees)
}
