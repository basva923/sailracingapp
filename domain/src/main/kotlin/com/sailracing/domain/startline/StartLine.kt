package com.sailracing.domain.startline

import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint

/** The start line, defined by the positions of its two ends. Either end may still be unset. */
public data class StartLine(val pinEnd: GeoPoint? = null, val boatEnd: GeoPoint? = null) {

    public val isComplete: Boolean get() = pinEnd != null && boatEnd != null

    /** The middle of the line; the one marked end while the other is missing; null when neither is set. */
    public fun middle(): GeoPoint? = when {
        pinEnd != null && boatEnd != null ->
            Geo.destination(pinEnd, Geo.initialBearingDegrees(pinEnd, boatEnd), Geo.distanceMeters(pinEnd, boatEnd) / 2)
        else -> pinEnd ?: boatEnd
    }

    /** Length of the line in metres, or null while one end is missing. */
    public fun lengthMeters(): Double? =
        if (pinEnd != null && boatEnd != null) Geo.distanceMeters(pinEnd, boatEnd) else null
}
