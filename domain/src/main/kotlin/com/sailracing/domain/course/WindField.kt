package com.sailracing.domain.course

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.wind.SpeedHistogram
import com.sailracing.domain.wind.WindHistogram
import kotlin.math.roundToInt

/**
 * The close-hauled speeds measured somewhere: their shape, and the speed they average.
 *
 * @property histogram the speeds in quarter-metre bins - what a puff and a hole are told apart by, and what
 *   a drawn speed takes its shape from.
 * @property sumMps every speed added up, unbinned, so that the mean is the mean and not the middle of a
 *   bin: a quarter of a metre a second is a twelfth of a dinghy's speed, and telling one side of the course
 *   from the other turns on less than that.
 */
public data class SpeedStats(
    val histogram: SpeedHistogram = SpeedHistogram(),
    val sumMps: Double = 0.0,
) {
    /** The mean speed measured, or null when none was. */
    public val meanMps: Double? get() = histogram.totalWeight.takeIf { it > 0.0 }?.let { sumMps / it }

    public val isEmpty: Boolean get() = histogram.isEmpty
}

/**
 * What one big square of the racing area has blown, and how much of the course's evidence that is.
 *
 * @property cell where the square is in [WindField.spec], the grid of big squares.
 * @property winds every close-hauled wind sample taken inside it, as a histogram of shifts off the
 *   reference wind (positive = veered), binned to the degree. Not a mean and a spread: the shape of it is
 *   the point, because a square that swung twenty degrees either way all afternoon and one that sat five
 *   degrees veered all day are not the same water, however close their averages.
 * @property speeds the close-hauled speeds measured inside it, likewise: a puff and a hole rather than
 *   their average, and what they average.
 * @property share this square's share of every sample taken on the course, from 0 (nobody sailed here) to
 *   1 (nobody sailed anywhere else). It is what its own histogram is worth against the whole course's when
 *   a wind is drawn for it: a square with a hundred samples speaks for itself, a square with two borrows.
 * @property measured true when it holds [WindFieldSettings.minBlockSamples] samples or more: what the map
 *   draws a bolder arrow for. The wind drawn for it is worked out the same way either way.
 */
public data class BlockWind(
    val cell: GridCell,
    val winds: WindHistogram = WindHistogram(),
    val speeds: SpeedStats = SpeedStats(),
    val share: Double = 0.0,
    val measured: Boolean = false,
) {
    /** How many close-hauled samples were taken inside this square. */
    public val samples: Int get() = winds.totalSamples

    /** The middle of the winds measured here, in degrees off the reference; null when none were. */
    public val meanShiftDegrees: Double? get() = winds.meanDirection()?.let { Angles.signedDifference(0.0, it) }

    /** The mean close-hauled speed measured here, or null when none was. */
    public val meanSpeedMps: Double? get() = speeds.meanMps
}

/**
 * The wind over the whole racing area as it has been measured, kept in big squares.
 *
 * ## Why big squares
 *
 * A race is sailed up the middle of the course and back down it. Cut the area into the squares the track
 * is binned onto and almost every one of them holds no close-hauled samples at all, and the handful that
 * were sailed hold a few seconds each - far too little to call a wind, let alone a distribution. So the
 * wind is worked out in squares big enough to fill up: [WindFieldSettings.columns] of them across the area
 * (three by default - the left, the middle and the right of the course) and as many rows as it is tall.
 * The fine squares are still there and still carry what was measured in them; they simply take their wind
 * from the big square they lie in ([WindSampler]).
 *
 * ## What a big square knows
 *
 * Only what was measured in it: a histogram of the wind shifts sailed through and a histogram of the
 * close-hauled speeds, plus what share of the course's samples that is. Nothing is smoothed, blended or
 * borrowed here - a square that nobody sailed through holds nothing, and says so with a share of zero.
 * What a square that knows nothing should blow is a question about drawing a wind, not about measuring
 * one, and it is answered in [WindSampler].
 *
 * The whole course's histograms are kept alongside ([courseWinds], [courseSpeeds]): the wind of the day,
 * wherever it was measured, which is what a square with little of its own falls back on.
 */
public class WindField private constructor(
    /** The big squares the wind is kept in: [WindFieldSettings.columns] across the area, as many rows as it is tall. */
    public val spec: GridSpec,
    /** The squares of the racing area itself: what the race line is searched over, each taking its big square's wind. */
    public val cellSpec: GridSpec,
    public val settings: WindFieldSettings,
    /** Which big square each square of [cellSpec] lies in, by the square's middle: worked out once, read per drawn wind. */
    internal val blockOfCell: IntArray,
    private val winds: Array<BlockWind>,
    /** Every wind sample taken anywhere on the course: what a square with little of its own goes by. */
    public val courseWinds: WindHistogram,
    /** Every close-hauled speed measured anywhere on the course, likewise. */
    public val courseSpeeds: SpeedStats,
) {

    /** What was measured in one big square. */
    public fun at(block: GridCell): BlockWind = winds[spec.index(block)]

    /** Every big square of the area, bottom row first. */
    public val blocks: List<BlockWind> get() = winds.asList()

    /** How many close-hauled samples were taken on the course at all. */
    public val samples: Int get() = courseWinds.totalSamples

    /**
     * The middle of every wind measured anywhere on the course, in degrees off the reference; null when
     * nothing has been measured at all. It is what a square that measured nothing of its own is drawn from,
     * and what the map draws its arrow of.
     */
    public val courseShiftDegrees: Double?
        get() = courseWinds.meanDirection()?.let { Angles.signedDifference(0.0, it) }

    /** How many big squares hold enough samples to count as measured. */
    public val measuredCount: Int get() = winds.count { it.measured }

    override fun toString(): String = "WindField(spec=$spec, samples=$samples, measured=$measuredCount)"

    public companion object {

        /**
         * The wind measured by [track], in the big squares of the area [spec] covers. Every close-hauled
         * sample is binned as a shift off the frame's reference wind, so changing the reference - or the
         * squares - is simply a matter of building the field again.
         */
        public fun build(
            track: Track,
            frame: CourseFrame,
            spec: GridSpec,
            settings: WindFieldSettings = WindFieldSettings(),
        ): WindField {
            val blockSpec = spec.blocks(settings.columns)
            val count = blockSpec.cellCount
            // Binned in place: a track is thousands of points, and a histogram that copied itself per
            // sample would copy a few hundred thousand bins a second.
            val windBins = Array(count) { IntArray(WindHistogram.BIN_COUNT) }
            val speedBins = Array(count) { DoubleArray(SpeedHistogram.BIN_COUNT) }
            val courseWindBins = IntArray(WindHistogram.BIN_COUNT)
            val courseSpeedBins = DoubleArray(SpeedHistogram.BIN_COUNT)
            val speedSums = DoubleArray(count)
            var courseSpeedSum = 0.0
            var samples = 0
            for (point in track.points) {
                val wind = point.upwindWindDegrees ?: continue
                val index = blockSpec.cellOf(frame.toCourse(point.point))?.let(blockSpec::index) ?: continue
                val shift = Angles.normalize(Angles.signedDifference(frame.windDirectionDegrees, wind).roundToInt())
                windBins[index][shift]++
                courseWindBins[shift]++
                samples++
                val speed = point.speedMps ?: continue
                val bin = SpeedHistogram.binOf(speed)
                speedBins[index][bin] += 1.0
                courseSpeedBins[bin] += 1.0
                speedSums[index] += speed
                courseSpeedSum += speed
            }
            val blocks = Array(count) { index ->
                val winds = WindHistogram.fromCounts(windBins[index])
                BlockWind(
                    cell = blockSpec.cellAt(index),
                    winds = winds,
                    speeds = SpeedStats(SpeedHistogram.fromWeights(speedBins[index]), speedSums[index]),
                    share = if (samples == 0) 0.0 else winds.totalSamples.toDouble() / samples,
                    measured = winds.totalSamples >= settings.minBlockSamples,
                )
            }
            val blockOfCell = IntArray(spec.cellCount) { index ->
                blockSpec.index(blockSpec.nearestCell(spec.center(spec.cellAt(index))))
            }
            return WindField(
                spec = blockSpec,
                cellSpec = spec,
                settings = settings,
                blockOfCell = blockOfCell,
                winds = blocks,
                courseWinds = WindHistogram.fromCounts(courseWindBins),
                courseSpeeds = SpeedStats(SpeedHistogram.fromWeights(courseSpeedBins), courseSpeedSum),
            )
        }
    }
}
