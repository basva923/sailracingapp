package com.sailracing.domain.course

/**
 * What the wind over the racing area is believed to be like, before a single sample is taken: how far a
 * wind measured in one place reaches, how much the mean wind varies from one square to the next, and how
 * much it wanders in any one of them. It is what turns a handful of measurements into a wind for every
 * square of the course ([WindField]).
 *
 * @property minCellSamples how many close-hauled samples a square needs before it counts as measured. It
 *   changes nothing about the wind worked out for it - a square with one sample is already worth
 *   something - only whether the map draws its arrow bright.
 * @property correlationLengthMeters how far the wind hangs together: two places this far apart have winds
 *   correlated by `1/e`. It decides how quickly a square listens less to a measurement as it gets further
 *   away, and how smooth a drawn wind field is.
 * @property neighbourhoodRadii how many correlation lengths away a square still listens to a measurement.
 *   Beyond about three there is nothing left to hear and the sum is only work.
 * @property spatialSpreadDegrees how much the mean wind differs from one part of the course to another:
 *   the spread every square starts from, around the wind measured over the whole area. It is what a square
 *   nobody sailed through, far from any that was, is left uncertain by.
 * @property sampleSpreadDegrees how much the wind is assumed to wander where that cannot be measured yet
 *   (a square with a single sample, or a course with no samples at all).
 * @property minSpreadDegrees no square's wind is ever known better than this: the tack angle it is read
 *   from is a model, the compass wanders, and the helm steers.
 * @property maxSpreadDegrees the cap on a square's spread, so that a few samples taken during a tack
 *   cannot turn a square into a lottery.
 * @property minWeightFraction a measurement worth less than this share of everything a square knows is
 *   left out of its speed histogram: it would move nothing and the blending is the expensive part.
 */
public data class WindFieldSettings(
    val minCellSamples: Int = 3,
    val correlationLengthMeters: Double = 150.0,
    val neighbourhoodRadii: Double = 3.0,
    val spatialSpreadDegrees: Double = 12.0,
    val sampleSpreadDegrees: Double = 8.0,
    val minSpreadDegrees: Double = 3.0,
    val maxSpreadDegrees: Double = 30.0,
    val minWeightFraction: Double = 0.005,
) {
    init {
        require(minCellSamples > 0) { "a square is measured by at least one sample: $minCellSamples" }
        require(correlationLengthMeters > 0.0) { "the wind hangs together over a positive distance: $correlationLengthMeters" }
        require(neighbourhoodRadii > 0.0) { "a square listens over a positive distance: $neighbourhoodRadii" }
        require(spatialSpreadDegrees > 0.0 && sampleSpreadDegrees > 0.0) {
            "the spreads believed in must be positive: $spatialSpreadDegrees, $sampleSpreadDegrees"
        }
        require(minSpreadDegrees >= 0.0 && maxSpreadDegrees >= minSpreadDegrees) {
            "spreads must be a range: $minSpreadDegrees..$maxSpreadDegrees"
        }
        require(minWeightFraction in 0.0..1.0) { "a share of the weight is a fraction: $minWeightFraction" }
    }
}
