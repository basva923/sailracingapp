package com.sailracing.app.ui.wind

import com.sailracing.app.ui.format.Formatters
import com.sailracing.domain.geo.Angles
import com.sailracing.domain.race.HeadingSource
import com.sailracing.domain.race.RaceSnapshot
import com.sailracing.domain.wind.HistogramBin
import com.sailracing.domain.wind.PointOfSail
import com.sailracing.domain.wind.SailingState
import com.sailracing.domain.wind.Tack

data class WindUiState(
    val configuredWind: String = Formatters.PLACEHOLDER,
    val windDirection: Int = 0,
    val tackAngle: Int = 45,
    val downwindAngle: Int = 140,
    val estimatedWind: String = Formatters.PLACEHOLDER,
    val shift: String = Formatters.PLACEHOLDER,
    val shiftDegrees: Double? = null,
    val heading: String = Formatters.PLACEHOLDER,
    val headingSource: String = "",
    val speed: String = Formatters.PLACEHOLDER,
    val tack: Tack? = null,
    val tackLabel: String = "",
    /** "Stbd" / "Port", short enough for a third of the screen. */
    val tackShort: String = "—",
    /** "Upwind" / "Downwind", or "Tack" when unknown. */
    val pointOfSailLabel: String = "Tack",
    val histogram: List<HistogramBin> = emptyList(),
    val estimatedOffset: Double? = null,
    val shifts: List<Double> = emptyList(),
    val statistics: String = "No upwind samples yet",
    val canSetFromHeading: Boolean = false,
) {
    companion object {
        const val HISTOGRAM_HALF_WIDTH = 40
        const val HISTORY_SAMPLES = 300

        fun from(snapshot: RaceSnapshot): WindUiState {
            val wind = snapshot.windSettings
            val configured = wind.directionDegrees.toDouble()
            val histogram = snapshot.histogram
            val mean = histogram.meanDirection()
            val deviation = histogram.standardDeviation()
            val statistics = if (mean == null) {
                "No upwind samples yet"
            } else {
                "Mean ${Formatters.degrees(mean)}" +
                    (deviation?.let { " ±${it.toInt()}°" } ?: "") +
                    " · ${histogram.totalSamples} samples"
            }
            return WindUiState(
                configuredWind = Formatters.degrees(wind.directionDegrees),
                windDirection = wind.directionDegrees,
                tackAngle = wind.tackAngleDegrees,
                downwindAngle = wind.downwindAngleDegrees,
                estimatedWind = snapshot.estimatedWindDegrees?.let(Formatters::degrees) ?: Formatters.PLACEHOLDER,
                shift = snapshot.shiftDegrees?.let(Formatters::signedDegrees) ?: Formatters.PLACEHOLDER,
                shiftDegrees = snapshot.shiftDegrees,
                heading = snapshot.headingDegrees?.let(Formatters::degrees) ?: Formatters.PLACEHOLDER,
                headingSource = when (snapshot.headingSource) {
                    HeadingSource.COURSE_OVER_GROUND -> "GPS course"
                    HeadingSource.COMPASS -> "Compass"
                    null -> ""
                },
                speed = snapshot.speedMps?.let(Formatters::knots) ?: Formatters.PLACEHOLDER,
                tack = snapshot.sailing?.tack,
                tackLabel = snapshot.sailing?.let(::tackLabel) ?: "",
                tackShort = when (snapshot.sailing?.tack) {
                    Tack.STARBOARD -> "Stbd"
                    Tack.PORT -> "Port"
                    null -> "—"
                },
                pointOfSailLabel = when (snapshot.sailing?.pointOfSail) {
                    PointOfSail.UPWIND -> "Upwind"
                    PointOfSail.DOWNWIND -> "Downwind"
                    null -> "Tack"
                },
                histogram = histogram.window(wind.directionDegrees, HISTOGRAM_HALF_WIDTH),
                estimatedOffset = snapshot.shiftDegrees,
                shifts = snapshot.history.last(HISTORY_SAMPLES).map { Angles.signedDifference(configured, it.directionDegrees) },
                statistics = statistics,
                canSetFromHeading = snapshot.headingDegrees != null,
            )
        }

        fun tackLabel(state: SailingState): String {
            val tack = if (state.tack == Tack.STARBOARD) "Starboard" else "Port"
            val point = if (state.pointOfSail == PointOfSail.UPWIND) "upwind" else "downwind"
            return "$tack $point"
        }
    }
}
