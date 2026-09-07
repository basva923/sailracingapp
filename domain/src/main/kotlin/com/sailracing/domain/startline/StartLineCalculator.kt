package com.sailracing.domain.startline

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import kotlin.math.abs
import kotlin.math.min

/** Start-line arithmetic: distance to the line, time to the line and "time to kill". */
public object StartLineCalculator {

    /**
     * Solves the geometry of [position] relative to [line].
     * Returns null while the line is incomplete.
     *
     * @param windDirectionDegrees direction the wind blows from; used to decide which side is the course side.
     */
    public fun solve(line: StartLine, position: GeoPoint, windDirectionDegrees: Double?): StartLineSolution? {
        val pin = line.pinEnd ?: return null
        val boat = line.boatEnd ?: return null

        val length = Geo.distanceMeters(pin, boat)
        if (length < MIN_LINE_LENGTH_METERS) {
            // Degenerate line (both ends marked in the same spot): treat it as a point.
            val distance = Geo.distanceMeters(pin, position)
            return StartLineSolution(distance, distance, 0.0, LineSide.UNKNOWN)
        }

        val crossTrack = Geo.crossTrackDistanceMeters(pin, boat, position)
        val alongTrack = Geo.alongTrackDistanceMeters(pin, boat, position)
        val alongFraction = alongTrack / length
        val perpendicular = abs(crossTrack)

        val distance = when {
            alongFraction < 0.0 -> Geo.distanceMeters(pin, position)
            alongFraction > 1.0 -> Geo.distanceMeters(boat, position)
            else -> perpendicular
        }

        return StartLineSolution(distance, perpendicular, alongFraction, side(pin, boat, crossTrack, windDirectionDegrees))
    }

    /** Seconds needed to reach the line at [approachSpeedMps]; null when the speed is not positive. */
    public fun timeToLineSeconds(distanceMeters: Double, approachSpeedMps: Double): Double? =
        if (approachSpeedMps > 0.0) distanceMeters / approachSpeedMps else null

    /**
     * Time to kill: how long you can wait before sailing full speed to the line and still cross exactly at the gun.
     * Positive means you are early (kill time), negative means you are late.
     */
    public fun timeToKillSeconds(remainingToStartSeconds: Double, timeToLineSeconds: Double): Double =
        remainingToStartSeconds - timeToLineSeconds

    private fun side(pin: GeoPoint, boat: GeoPoint, crossTrack: Double, windDirectionDegrees: Double?): LineSide {
        if (windDirectionDegrees == null) return LineSide.UNKNOWN
        // The course side is upwind of the line. The right-hand normal of pin->boat points to bearing + 90.
        val lineBearing = Geo.initialBearingDegrees(pin, boat)
        val rightNormal = Angles.normalize(lineBearing + 90.0)
        val upwindIsRight = abs(Angles.signedDifference(rightNormal, windDirectionDegrees)) < 90.0
        val onRight = crossTrack > 0.0
        return if (onRight == upwindIsRight) LineSide.COURSE else LineSide.PRE_START
    }

    private const val MIN_LINE_LENGTH_METERS = 1.0

    /** Utility for callers that want a display distance capped to something sensible. */
    public fun clampDistance(distanceMeters: Double, maxMeters: Double): Double = min(distanceMeters, maxMeters)
}
