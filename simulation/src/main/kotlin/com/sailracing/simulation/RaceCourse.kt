package com.sailracing.simulation

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.startline.StartLine

/** A windward-leeward course: a start/finish line, a windward mark and a leeward mark. */
public data class RaceCourse(
    val pinEnd: GeoPoint,
    val boatEnd: GeoPoint,
    val windwardMark: GeoPoint,
    val leewardMark: GeoPoint,
) {
    public val line: StartLine get() = StartLine(pinEnd, boatEnd)

    public val lineCenter: GeoPoint get() = Geo.destination(pinEnd, Geo.initialBearingDegrees(pinEnd, boatEnd), Geo.distanceMeters(pinEnd, boatEnd) / 2)

    public companion object {
        /**
         * Lays a course square to the wind: the line is perpendicular to [windDirectionDegrees] with the pin
         * on the left when looking upwind, the windward mark [beatLengthMeters] upwind of the line centre and
         * the leeward mark [leewardOffsetMeters] downwind of it.
         */
        public fun square(
            lineCenter: GeoPoint,
            windDirectionDegrees: Double,
            lineLengthMeters: Double = 100.0,
            beatLengthMeters: Double = 500.0,
            leewardOffsetMeters: Double = 80.0,
        ): RaceCourse {
            require(lineLengthMeters > 0 && beatLengthMeters > 0 && leewardOffsetMeters > 0) { "course dimensions must be positive" }
            val left = Angles.normalize(windDirectionDegrees - 90.0)
            val right = Angles.normalize(windDirectionDegrees + 90.0)
            return RaceCourse(
                pinEnd = Geo.destination(lineCenter, left, lineLengthMeters / 2),
                boatEnd = Geo.destination(lineCenter, right, lineLengthMeters / 2),
                windwardMark = Geo.destination(lineCenter, windDirectionDegrees, beatLengthMeters),
                leewardMark = Geo.destination(lineCenter, Angles.normalize(windDirectionDegrees + 180.0), leewardOffsetMeters),
            )
        }
    }
}
