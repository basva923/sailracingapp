package com.sailracing.app.ui.format

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Pure formatting of domain values for display. Locale-independent, big-screen friendly. */
object Formatters {

    const val METERS_PER_SECOND_TO_KNOTS = 1.943844

    /** "04:37" while counting down, "+01:12" once racing. Rounds up so the display hits 00:00 exactly at the gun. */
    fun countdown(remainingMillis: Long): String {
        val seconds = if (remainingMillis >= 0) (remainingMillis + 999) / 1000 else -((-remainingMillis) / 1000)
        val sign = if (seconds < 0) "+" else ""
        val total = abs(seconds)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%s%d:%02d:%02d", sign, hours, minutes, secs)
        } else {
            String.format(Locale.ROOT, "%s%02d:%02d", sign, minutes, secs)
        }
    }

    /** Local time of day as "HH:mm". */
    fun timeOfDay(epochMillis: Long, zoneOffsetMillis: Int = java.util.TimeZone.getDefault().getOffset(epochMillis)): String {
        val local = (epochMillis + zoneOffsetMillis) / 1000
        val minutesOfDay = ((local / 60) % (24 * 60) + 24 * 60) % (24 * 60)
        return String.format(Locale.ROOT, "%02d:%02d", minutesOfDay / 60, minutesOfDay % 60)
    }

    /** Whole seconds with sign, e.g. "+12 s" or "-4 s". */
    fun signedSeconds(seconds: Double): String {
        val rounded = seconds.roundToLong()
        val sign = if (rounded > 0) "+" else if (rounded < 0) "-" else ""
        return "$sign${abs(rounded)} s"
    }

    fun seconds(seconds: Double): String = "${seconds.roundToLong()} s"

    /** Metres, whole numbers; kilometres with one decimal above 1 km. */
    fun meters(meters: Double): String =
        if (meters >= 1000.0) String.format(Locale.ROOT, "%.1f km", meters / 1000.0) else "${meters.roundToInt()} m"

    fun knots(metersPerSecond: Double): String = String.format(Locale.ROOT, "%.1f kn", metersPerSecond * METERS_PER_SECOND_TO_KNOTS)

    fun knotsValue(metersPerSecond: Double): String = String.format(Locale.ROOT, "%.1f", metersPerSecond * METERS_PER_SECOND_TO_KNOTS)

    fun knotsToMetersPerSecond(knots: Double): Double = knots / METERS_PER_SECOND_TO_KNOTS

    /** Signed knots with one decimal: "+0.3 kn", "-1.2 kn", "0.0 kn". */
    fun signedKnots(metersPerSecond: Double): String {
        val knots = metersPerSecond * METERS_PER_SECOND_TO_KNOTS
        val rounded = String.format(Locale.ROOT, "%.1f", abs(knots))
        val sign = if (rounded == "0.0") "" else if (knots > 0) "+" else "-"
        return "$sign$rounded kn"
    }

    /** Compass degrees, three digits: "005°". */
    fun degrees(degrees: Double): String = String.format(Locale.ROOT, "%03d°", ((degrees.roundToInt() % 360) + 360) % 360)

    fun degrees(degrees: Int): String = degrees(degrees.toDouble())

    /** Signed degrees for shifts and errors: "+4°", "-12°", "0°". */
    fun signedDegrees(degrees: Double): String {
        val rounded = degrees.roundToInt()
        val sign = if (rounded > 0) "+" else if (rounded < 0) "-" else ""
        return "$sign${abs(rounded)}°"
    }

    const val PLACEHOLDER = "---"
}
