package com.sailracing.domain.course

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.geo.GeoPoint
import kotlin.random.Random

/**
 * Racing areas to search through, built the way the app builds them: a track of close-hauled samples,
 * binned onto the area, turned into a [WindField]. Nothing here fabricates a field directly, so the tests
 * see the same means, spreads and speed histograms a boat would have measured.
 */
object CourseFixtures {

    val frame: CourseFrame = CourseFrame(GeoPoint(51.14, 5.83), windDirectionDegrees = 0.0)

    /** One second of sailing in a square: the wind estimated there and the speed the boat was making. */
    data class Sample(val shiftDegrees: Double, val speedMps: Double)

    /**
     * A field over [spec] where every square was sailed through [samplesPerCell] times, each sample as
     * [sample] describes it - or not sailed through at all, where [sample] returns null.
     */
    fun field(
        spec: GridSpec,
        samplesPerCell: Int = 20,
        seed: Long = 1L,
        sample: (cell: GridCell, sampleIndex: Int, random: Random) -> Sample?,
    ): WindField {
        val random = Random(seed)
        val points = ArrayList<TrackPoint>()
        for (index in 0 until spec.cellCount) {
            val cell = spec.cellAt(index)
            val place = frame.toGeo(spec.center(cell))
            for (sampleIndex in 0 until samplesPerCell) {
                val drawn = sample(cell, sampleIndex, random) ?: continue
                points += TrackPoint(
                    timestampMillis = points.size * 1000L,
                    point = place,
                    speedMps = drawn.speedMps,
                    headingDegrees = 0.0,
                    upwindWindDegrees = Angles.normalize(drawn.shiftDegrees),
                )
            }
        }
        val track = Track(points, capacity = points.size.coerceAtLeast(1))
        return WindField.build(TrackGrid.build(track, frame, spec, spec.topCenter), referenceDegrees = 0.0)
    }

    /** A field with one steady wind and one speed everywhere: the plainest racing area there is. */
    fun even(spec: GridSpec, shiftDegrees: Double = 0.0, speedMps: Double = 3.0, samplesPerCell: Int = 20): WindField =
        field(spec, samplesPerCell) { _, _, _ -> Sample(shiftDegrees, speedMps) }
}
