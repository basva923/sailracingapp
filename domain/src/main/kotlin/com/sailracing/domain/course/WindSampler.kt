package com.sailracing.domain.course

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.wind.SpeedHistogram
import com.sailracing.domain.wind.WindHistogram
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * One wind the racing area might have: a direction and a close-hauled boat speed for every square of it.
 * One run of the Monte Carlo sails through one of these.
 *
 * The wind is drawn per big square of [blocks] - that is where there are enough samples to draw from - and
 * read per square of [spec], the finer grid the race line is searched over, so that the geometry of a line
 * stays as sharp as the area is cut while its wind stays as coarse as the evidence for it.
 */
public class SampledConditions internal constructor(
    override val spec: GridSpec,
    /** The big squares the wind was drawn in. */
    public val blocks: GridSpec,
    private val blockOfCell: IntArray,
    private val shifts: DoubleArray,
    private val speeds: DoubleArray,
) : SailingConditions {

    override fun shiftDegrees(cell: GridCell): Double = shifts[blockOfCell[spec.index(cell)]]

    override fun speedMps(cell: GridCell): Double = speeds[blockOfCell[spec.index(cell)]]

    /** The wind drawn in one big square, in degrees off the reference. */
    public fun blockShiftDegrees(block: GridCell): Double = shifts[blocks.index(block)]

    /** The close-hauled speed drawn in one big square. */
    public fun blockSpeedMps(block: GridCell): Double = speeds[blocks.index(block)]

    override fun toString(): String = "SampledConditions(spec=$spec, blocks=$blocks)"
}

/**
 * Draws whole winds over the racing area out of what has been measured of it.
 *
 * ## What one big square is drawn from
 *
 * Four things, and the dice decide which of them a square blows this time round
 * ([WindFieldSettings]):
 *
 * 1. **the wind the boat is measuring right now**, in [WindFieldSettings.currentWindFraction] of the draws
 *    (35% by default). Its own heading and tack angle are the freshest measurement of the day, and a beat
 *    is planned in the wind you are in, not the wind you averaged an hour ago. Without one - the boat is
 *    not close-hauled and its heading says nothing about the wind - this share goes to the histograms;
 * 2. **the histogram of this square itself**, in [WindFieldSettings.measuredFraction] of the draws
 *    (60%) times [BlockWind.share], the square's share of every sample taken on the course;
 * 3. **the histogram of the whole course**, in the rest of that 60%. So a square where half the race was
 *    sailed mostly blows its own wind, a square nobody went near blows the day's wind, and nothing had to
 *    be smoothed, borrowed or interpolated to say so;
 * 4. **anything at all**, in [WindFieldSettings.randomWindFraction] of the draws (5%): a direction drawn
 *    flat around the compass. It is the shift nobody saw coming, and it is what a line has to survive to
 *    be called safe.
 *
 * A direction from a histogram is drawn from *exactly* the distribution it holds - a bin in proportion to
 * its count, and anywhere inside that bin - never from a bell curve fitted over it. A wind that has been
 * oscillating between two shifts is drawn as one shift or the other, which is what it does, instead of as
 * the middle it never blows.
 *
 * ## What that leaves the squares looking like
 *
 * Every big square is drawn on its own, so one drawn wind is not a single shift laid over the whole course:
 * it is the left, the middle and the right of it each doing their own thing, which is the situation the
 * race line is there to judge. A block is about a third of the width of the racing area - the scale a shift
 * actually has - so treating one as independent of the next is honest, where the same assumption over 20 m
 * squares would have been a chequerboard.
 *
 * The boat speeds are drawn alongside and independently: from the square's own speed histogram or the whole
 * course's, by the same share, so a corner that was half puff and half hole comes out as a puff or a hole
 * and never as its mean. A square that swung 20 degrees is not thereby a windy one.
 */
public class WindSampler private constructor(
    private val field: WindField,
    /** The wind the boat is measuring now, or the reference wind when it is measuring none - which is worth nothing. */
    private val currentShiftDegrees: Double,
    /** How many of the simulated winds are that wind of the moment, over the whole course. */
    private val currentWeight: Double,
    private val blocks: Array<Block>,
    private val courseWinds: WindHistogram.Draws?,
    private val courseSpeeds: Speeds?,
) {

    /** One wind over the whole area, drawn with [random]. */
    public fun draw(random: Random): SampledConditions {
        // The wind of the moment is one measurement of one moment, so it is drawn once for the whole
        // course: in that share of the simulations every square blows it, in the rest none of them does.
        // A square still blows it in exactly [WindFieldSettings.currentWindFraction] of the simulations -
        // what changes is that the shift the boat is sitting in is a shift over the water and not a
        // scattering of squares that happen to agree.
        val now = currentWeight > 0.0 && random.nextDouble() < currentWeight
        val count = blocks.size
        val shifts = DoubleArray(count)
        val speeds = DoubleArray(count)
        for (index in 0 until count) {
            val block = blocks[index]
            shifts[index] = if (now) currentShiftDegrees else drawShift(block, random)
            speeds[index] = drawSpeed(block, random)
        }
        return conditions(shifts, speeds)
    }

    /**
     * The wind without the chance: every big square at the mean of everything it might have been drawn as,
     * the four sources weighed exactly as they are weighed in a draw (a flat draw round the compass has no
     * mean, so it pulls nowhere). The beat searched in it is the plain answer to the wind as it stands, and
     * the line every simulated one is measured against.
     */
    public val mean: SampledConditions by lazy {
        conditions(
            DoubleArray(blocks.size) { blocks[it].meanShiftDegrees },
            DoubleArray(blocks.size) { blocks[it].meanSpeedMps },
        )
    }

    private fun conditions(shifts: DoubleArray, speeds: DoubleArray): SampledConditions =
        SampledConditions(field.cellSpec, field.spec, field.blockOfCell, shifts, speeds)

    /**
     * Which of the other three winds this square blows this time round, and what it is: its own histogram,
     * the whole course's, or anything at all. The three are weighed against each other as they are weighed
     * in the mixture, the wind of the moment having already had its turn over the whole course.
     */
    private fun drawShift(block: Block, random: Random): Double {
        val chance = random.nextDouble()
        val histogram = when {
            chance < block.ownLimit -> block.winds
            chance < block.courseLimit -> courseWinds
            else -> return random.nextDouble(-HALF_TURN_DEGREES, HALF_TURN_DEGREES)
        }
        // Nothing measured anywhere on the course leaves only the reference wind to fall back on, which is
        // the one thing the sailor did tell the app.
        return histogram?.let { shift(it.next(random)) } ?: 0.0
    }

    private fun drawSpeed(block: Block, random: Random): Double {
        // Its own speeds or the course's, by the same share as its winds - and the course's either way when
        // it was sailed through without a speed to show for it.
        val speeds = (if (random.nextDouble() < block.share) block.speeds ?: courseSpeeds else courseSpeeds)
            ?: return SailingConditions.DEFAULT_BOAT_SPEED_MPS
        return speeds.next(random, field.settings)
    }

    /**
     * One big square made ready to draw from: what it draws from, and the weights of the three winds that
     * are its own business as running totals - already divided by what the wind of the moment left over,
     * because by the time these are read that draw has been lost.
     */
    private class Block(
        val ownLimit: Double,
        val courseLimit: Double,
        val share: Double,
        val winds: WindHistogram.Draws?,
        val speeds: Speeds?,
        val meanShiftDegrees: Double,
        val meanSpeedMps: Double,
    )

    /** Speeds made ready to draw from: the speed they average, and the shape they scatter in around it. */
    internal class Speeds(private val histogram: SpeedHistogram, val meanMps: Double, private val binnedMeanMps: Double) {

        /**
         * One speed, drawn from the histogram's own shape but only [WindFieldSettings.speedSpreadFactor] as
         * far from the mean: a board across a square averages a handful of the seconds it was measured in.
         * The draw is taken as a deviation from the histogram's own mean and added to the exact one, so that
         * binning the speeds moves the shape of the draw and never the speed it is drawn around.
         */
        fun next(random: Random, settings: WindFieldSettings): Double {
            val drawn = meanMps + settings.speedSpreadFactor * (histogram.quantile(random.nextDouble()) - binnedMeanMps)
            return drawn.coerceAtLeast(settings.minBoatSpeedMps)
        }

        companion object {
            fun of(stats: SpeedStats): Speeds? {
                val mean = stats.meanMps ?: return null
                return Speeds(stats.histogram, mean, stats.histogram.meanMps ?: mean)
            }
        }
    }

    public companion object {

        /** Half a turn: how far either way a wind drawn flat around the compass can land. */
        private const val HALF_TURN_DEGREES: Double = 180.0

        /**
         * [field] made ready to draw winds from, for a boat measuring [currentShiftDegrees] right now (off
         * the reference wind; null when it is not beating and has nothing to say about the wind). The
         * mixture of every big square is worked out once here and drawn from as often as the Monte Carlo
         * asks, which is what makes a thousand simulated winds cost less than one searched beat.
         */
        public fun of(field: WindField, currentShiftDegrees: Double? = null): WindSampler {
            val settings = field.settings
            val courseWinds = field.courseWinds
            val courseSpeeds = Speeds.of(field.courseSpeeds)
            val current = if (currentShiftDegrees == null) 0.0 else settings.currentWindFraction
            // What the wind of the moment does not take is what the histograms are worth; chance keeps its
            // own share whatever else is known.
            val measured = 1.0 - settings.randomWindFraction - current
            // What is left once the wind of the moment has had its share of the simulations: the histograms
            // and chance, weighed against one another rather than against everything.
            val rest = 1.0 - current
            val blocks = Array(field.spec.cellCount) { index ->
                val block = field.at(field.spec.cellAt(index))
                val own = measured * block.share
                val course = measured * (1.0 - block.share)
                val speeds = Speeds.of(block.speeds)
                Block(
                    ownLimit = if (rest <= 0.0) 0.0 else own / rest,
                    courseLimit = if (rest <= 0.0) 0.0 else (own + course) / rest,
                    share = block.share,
                    winds = block.winds.draws(),
                    speeds = speeds,
                    meanShiftDegrees = meanShift(current, currentShiftDegrees, own, block.winds, course, courseWinds),
                    meanSpeedMps = meanSpeed(block.share, speeds, courseSpeeds, settings),
                )
            }
            return WindSampler(field, currentShiftDegrees ?: 0.0, current, blocks, courseWinds.draws(), courseSpeeds)
        }

        /** The four winds added up as vectors, each as long as its weight and as sure as its histogram. */
        private fun meanShift(
            currentWeight: Double,
            currentShiftDegrees: Double?,
            ownWeight: Double,
            ownWinds: WindHistogram,
            courseWeight: Double,
            courseWinds: WindHistogram,
        ): Double {
            var cosine = 0.0
            var sine = 0.0
            fun add(weight: Double, degrees: Double?, concentration: Double) {
                if (weight <= 0.0 || degrees == null) return
                val radians = Angles.toRadians(degrees)
                cosine += weight * concentration * cos(radians)
                sine += weight * concentration * sin(radians)
            }
            add(currentWeight, currentShiftDegrees, 1.0)
            add(ownWeight, ownWinds.meanDirection(), ownWinds.concentration)
            add(courseWeight, courseWinds.meanDirection(), courseWinds.concentration)
            if (cosine == 0.0 && sine == 0.0) return 0.0
            return Angles.signedDifference(0.0, Angles.toDegrees(atan2(sine, cosine)))
        }

        /**
         * The square's own mean speed and the course's, weighed by the same share the winds are. The
         * course's speeds are every square's, so a square with speeds of its own is never alone with them,
         * and a square without any is simply the course's.
         */
        private fun meanSpeed(share: Double, own: Speeds?, course: Speeds?, settings: WindFieldSettings): Double {
            val mean = course?.let { share * (own?.meanMps ?: it.meanMps) + (1.0 - share) * it.meanMps }
                ?: SailingConditions.DEFAULT_BOAT_SPEED_MPS
            return mean.coerceAtLeast(settings.minBoatSpeedMps)
        }

        /** A drawn direction, off the reference wind: the histograms hold shifts, wrapped onto the compass. */
        private fun shift(drawnDegrees: Double): Double = Angles.signedDifference(0.0, drawnDegrees)
    }
}
