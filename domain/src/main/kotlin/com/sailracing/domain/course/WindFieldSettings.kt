package com.sailracing.domain.course

/**
 * How the wind over the racing area is worked out from what has been measured of it: how coarsely the area
 * is cut for it, and what each of the things a square's wind can be drawn from is worth. It is what turns a
 * handful of measurements into a wind for every square of the course ([WindField], [WindSampler]).
 *
 * A race is sailed up the middle. Two beats leave most squares of a finely cut area with no samples at all
 * and the few that were sailed with a handful each, which is not a distribution - it is anecdote. So the
 * wind is not worked out per square of the track grid but per **big square**: [columns] across the area and
 * as many rows as it is tall, each of them holding the samples of every board that crossed it.
 *
 * @property columns how many big squares the racing area is cut into across. Three is the whole point of
 *   the model: the left, the middle and the right of the course, which is the question a beat actually
 *   asks, and enough samples in each to be worth calling a histogram.
 * @property currentWindFraction how often a drawn square simply blows what the boat is measuring right
 *   now. It is the freshest measurement there is and it is worth more than any average, but it is one
 *   moment: a bit over a third of the simulated winds are it, the rest are what the day has done.
 *   Without a wind of the moment (the boat is not beating) its share goes to the histograms.
 * @property randomWindFraction how often a drawn square blows anything at all, anywhere on the compass.
 *   It is the wind nobody saw coming, and it is what keeps a line that only works if the day stays exactly
 *   as it was measured from winning: a line has to survive a share of nonsense to be called safe.
 * @property minBlockSamples how many close-hauled samples a big square needs before it counts as measured.
 *   It changes nothing about the wind drawn for it - a square with one sample already has a histogram -
 *   only whether the map draws its arrow bold.
 * @property speedSpreadFactor how much of the speed histogram's spread a whole board across a square
 *   feels. The histogram is of speeds a second apart; a board averages a handful of them, so the wave that
 *   stopped the boat for one sample is not the wave it crosses the square on.
 * @property minBoatSpeedMps the slowest a drawn speed may be, so that a line never costs eternity.
 */
public data class WindFieldSettings(
    val columns: Int = 3,
    val currentWindFraction: Double = 0.35,
    val randomWindFraction: Double = 0.05,
    val minBlockSamples: Int = 3,
    val speedSpreadFactor: Double = 0.6,
    val minBoatSpeedMps: Double = 0.5,
) {
    init {
        require(columns > 0) { "an area is at least one big square across: $columns" }
        require(currentWindFraction in 0.0..1.0) { "the wind of the moment's share is a fraction: $currentWindFraction" }
        require(randomWindFraction in 0.0..1.0) { "chance's share is a fraction: $randomWindFraction" }
        require(currentWindFraction + randomWindFraction <= 1.0) {
            "the wind of the moment and chance cannot be worth more than every wind there is: " +
                "$currentWindFraction + $randomWindFraction"
        }
        require(minBlockSamples > 0) { "a big square is measured by at least one sample: $minBlockSamples" }
        require(speedSpreadFactor >= 0.0) { "a share of the speed spread is not negative: $speedSpreadFactor" }
        require(minBoatSpeedMps > 0.0) { "the slowest a boat may go must be positive: $minBoatSpeedMps" }
    }

    /**
     * How often a drawn square blows what has actually been measured, split between its own histogram and
     * the whole course's: everything the wind of the moment and chance leave over.
     */
    public val measuredFraction: Double get() = 1.0 - currentWindFraction - randomWindFraction
}
