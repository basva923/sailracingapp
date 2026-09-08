package com.sailracing.domain.course

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.wind.SpeedHistogram
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What one square of the racing area is believed to blow, relative to the frame's wind (the reference):
 * positive = veered (clockwise). It is never only what was measured inside the square - see [WindField].
 *
 * @property shiftDegrees the wind here: the square's own samples, the samples around it and the wind over
 *   the whole course, weighed by how much each of them is worth.
 * @property spreadDegrees how far the wind here may be off that: how much it wandered while it was
 *   measured, plus how uncertain the mean itself still is. Never nothing, never a lottery
 *   ([WindFieldSettings.minSpreadDegrees], [WindFieldSettings.maxSpreadDegrees]).
 * @property samples how many close-hauled samples were taken inside this square itself.
 * @property measured true when that is [WindFieldSettings.minCellSamples] or more: what the map draws a
 *   bolder arrow for. The wind itself is worked out the same way either way.
 * @property confidence how much the measurements narrowed the square down, from 0 (nothing known here
 *   beyond the wind over the whole course) to 1 (its mean is pinned exactly).
 * @property speedMps the close-hauled speed to expect here, blended the same way, or
 *   [SailingConditions.DEFAULT_BOAT_SPEED_MPS] where no speed was measured anywhere at all.
 * @property speeds the close-hauled speeds to expect here as a histogram: the mixture of the speeds
 *   measured here and around here, and of the speeds measured over the whole course. Its shape is what a
 *   puff or a hole shows up in, and what the race line's Monte Carlo draws a board's speed from.
 * @property measuredDistanceMeters how far the nearest measured square is (0 for a measured one), and
 *   infinite while nothing at all has been measured.
 */
public data class CellWind(
    val cell: GridCell,
    val shiftDegrees: Double,
    val spreadDegrees: Double,
    val samples: Int = 0,
    val measured: Boolean = false,
    val confidence: Double = 0.0,
    val speedMps: Double = SailingConditions.DEFAULT_BOAT_SPEED_MPS,
    val speeds: SpeedHistogram = SpeedHistogram(),
    val measuredDistanceMeters: Double = Double.POSITIVE_INFINITY,
)

/**
 * The wind over the whole racing area, one distribution per square: a mean direction, how far it may be
 * off that, and a histogram of the close-hauled boat speed to expect.
 *
 * ## Where a square's wind comes from
 *
 * Most of a racing area is never sailed through. Rather than hand those squares the nearest measurement
 * and pretend it was taken there, every square - sailed through or not - is a blend of three things, each
 * weighed by how much it is worth:
 *
 * 1. **the samples taken in the square itself**, worth `n / wander²`: the more of them and the steadier
 *    they were, the more the square is simply what it measured;
 * 2. **the samples taken around it**, gathered into one measurement - the squares weighed by how many
 *    samples they hold and by `exp(-distance / correlationLength)`, how much of their wind is this
 *    square's wind - and worth what that measurement is worth here, which can never be more than the
 *    distance between them allows;
 * 3. **the wind over the whole course**, wherever it was measured, worth what a square can differ from the
 *    day's mean at all ([WindFieldSettings.spatialSpreadDegrees]). It is what is left over, and it is all
 *    that a corner nobody went near ever gets.
 *
 * Each of the three is a mean and how wrong it could be; a square's wind is their variance-weighted blend,
 * taken over unit vectors so that 350 and 10 average to 0, and how uncertain the blend still is comes out
 * of the same sum.
 *
 * So a square keeps the wind it measured, a square beside one borrows most of it, and a square in the far
 * corner knows only the day's breeze. What each of them does *not* know is kept as well: a square's spread
 * is how much the wind wandered where it was measured plus how much a square this far from anything
 * measured can differ from it ([WindFieldSettings.spatialSpreadDegrees]), which is what the race line's
 * Monte Carlo needs in order to treat an unsailed side of the course as the gamble it is.
 *
 * The boat speeds are blended with the same three weights. Because a square's speed is kept as a
 * histogram, the blend is a *mixture*: an unsailed square between a puffy corner and a steady one is drawn
 * sometimes from the one and sometimes from the other, instead of always from an average that never
 * happened.
 *
 * As [SailingConditions] the field is that wind held still: one mean per square. The Monte Carlo of
 * [RaceLinePlanner] draws whole winds from the distributions instead.
 */
public class WindField private constructor(
    override val spec: GridSpec,
    public val settings: WindFieldSettings,
    private val shifts: DoubleArray,
    private val spreads: DoubleArray,
    private val samples: IntArray,
    private val measured: BooleanArray,
    private val confidences: DoubleArray,
    private val speedsMps: DoubleArray,
    private val speeds: Array<SpeedHistogram>,
    private val distances: DoubleArray,
) : SailingConditions {

    public fun at(cell: GridCell): CellWind {
        val index = spec.index(cell)
        return CellWind(
            cell = cell,
            shiftDegrees = shifts[index],
            spreadDegrees = spreads[index],
            samples = samples[index],
            measured = measured[index],
            confidence = confidences[index],
            speedMps = speedMps(cell),
            speeds = speeds[index],
            measuredDistanceMeters = distances[index],
        )
    }

    override fun shiftDegrees(cell: GridCell): Double = shifts[spec.index(cell)]

    /** How far the wind in a cell may be off its mean: what one drawn wind is spread by. */
    public fun spreadDegrees(cell: GridCell): Double = spreads[spec.index(cell)]

    /** The close-hauled speed expected in a cell, or the default where nothing was measured at all. */
    override fun speedMps(cell: GridCell): Double =
        speedsMps[spec.index(cell)].takeIf { it > 0.0 } ?: SailingConditions.DEFAULT_BOAT_SPEED_MPS

    public val cells: List<CellWind> get() = (0 until spec.cellCount).map { at(spec.cellAt(it)) }

    public val measuredCount: Int get() = measured.count { it }

    override fun equals(other: Any?): Boolean =
        other is WindField && spec == other.spec && settings == other.settings && shifts.contentEquals(other.shifts) &&
            spreads.contentEquals(other.spreads) && samples.contentEquals(other.samples) &&
            measured.contentEquals(other.measured) && speedsMps.contentEquals(other.speedsMps) &&
            speeds.contentEquals(other.speeds)

    override fun hashCode(): Int = 31 * spec.hashCode() + shifts.contentHashCode()

    override fun toString(): String = "WindField(spec=$spec, measured=$measuredCount)"

    public companion object {

        public fun build(
            grid: TrackGrid,
            referenceDegrees: Double,
            settings: WindFieldSettings = WindFieldSettings(),
        ): WindField {
            val spec = grid.spec
            val count = spec.cellCount
            val shifts = DoubleArray(count)
            val spreads = DoubleArray(count)
            val sampleCounts = IntArray(count)
            val measured = BooleanArray(count)
            val confidences = DoubleArray(count)
            val speedsMps = DoubleArray(count)
            val speeds = Array(count) { SpeedHistogram() }
            val distances = DoubleArray(count) { Double.POSITIVE_INFINITY }

            val sources = ArrayList<Measured>()
            for (index in 0 until count) {
                val stats = grid.stats(spec.cellAt(index))
                sampleCounts[index] = stats.upwindSamples
                measured[index] = stats.upwindSamples >= settings.minCellSamples
                sources += Measured.of(index, spec.center(spec.cellAt(index)), stats, referenceDegrees, settings) ?: continue
            }
            // The wind over the whole course, wherever it was measured: what a square with nothing of its
            // own goes by, and what is left of every square once its own samples have had their say.
            val global = Measured.of(-1, CoursePosition(0.0, 0.0), grid.total, referenceDegrees, settings)
            val globalWander = global?.wanderDegrees ?: settings.sampleSpreadDegrees
            val spatialVariance = settings.spatialSpreadDegrees * settings.spatialSpreadDegrees
            // What the day's wind is worth in a square: how uncertain its own mean is, plus how far this
            // square could differ from it whatever it is. It is what a square with nothing of its own is
            // left with, and what everything else is measured against.
            val globalPrecision = 1.0 /
                (globalWander * globalWander / ((global?.samples ?: 0) + 1) + spatialVariance)

            val bins = DoubleArray(SpeedHistogram.BIN_COUNT)
            val range = settings.neighbourhoodRadii * settings.correlationLengthMeters
            for (index in 0 until count) {
                val centre = spec.center(spec.cellAt(index))
                val local = sources.firstOrNull { it.index == index }
                // Everything measured around this square, as one measurement: the squares weighed by how
                // far away they are and by how many samples they hold. Weighing them one by one against
                // the rest would count a hundred squares of one shift as a hundred separate opinions,
                // when the whole point of a shift is that they are all saying the same thing.
                var mass = 0.0
                var cosine = 0.0
                var sine = 0.0
                var wanderSum = 0.0
                var sharedSum = 0.0
                var speedSum = 0.0
                var speedMass = 0.0
                var nearest = Double.POSITIVE_INFINITY
                for (source in sources) {
                    val distance = distance(centre, source.centre)
                    if (source.measured) nearest = min(nearest, distance)
                    if (source.index == index || distance > range) continue
                    val shared = exp(-distance / settings.correlationLengthMeters)
                    val weight = shared * source.samples
                    mass += weight
                    cosine += weight * source.cosine
                    sine += weight * source.sine
                    wanderSum += weight * source.wanderDegrees * source.wanderDegrees
                    sharedSum += weight * shared
                    if (source.speedMps != null) {
                        speedSum += weight * source.speedMps
                        speedMass += weight
                    }
                }
                // How much of this square's wind the neighbourhood could know even if it were measured
                // perfectly: what two places that far apart share.
                val shared = if (mass > 0.0) sharedSum / mass else 0.0
                val neighbourWander = if (mass > 0.0) sqrt(wanderSum / mass) else globalWander
                // Each of the three as a precision - one over how wrong it could be - so that they weigh
                // themselves: the samples here by how many they are, the neighbourhood by how far away and
                // how many it is, the day's wind by how much a square can differ from it at all.
                val localPrecision = if (local == null) 0.0 else local.samples / (local.wanderDegrees * local.wanderDegrees)
                val neighbourPrecision = if (mass <= 0.0) {
                    0.0
                } else {
                    1.0 / (neighbourWander * neighbourWander / mass + spatialVariance * (1.0 - shared * shared))
                }
                val total = localPrecision + neighbourPrecision + globalPrecision

                val blend = Blend()
                if (local != null) blend.add(localPrecision / total, local.cosine, local.sine, local.wanderDegrees)
                if (mass > 0.0) blend.add(neighbourPrecision / total, cosine / mass, sine / mass, neighbourWander)
                blend.add(globalPrecision / total, global?.cosine ?: 1.0, global?.sine ?: 0.0, globalWander)
                val wanderVariance = blend.wanderVariance / blend.weight

                shifts[index] = blend.degrees()
                // What the wind here may be: how much it wanders, and how far its mean could still be out.
                spreads[index] = sqrt(wanderVariance + 1.0 / total).coerceIn(settings.minSpreadDegrees, settings.maxSpreadDegrees)
                // How much of what this square knows is its own and its neighbours', rather than the day's.
                confidences[index] = (1.0 - globalPrecision / total).coerceIn(0.0, 1.0)

                bins.fill(0.0)
                var speed = 0.0
                var speedWeight = 0.0
                if (local?.speedMps != null) {
                    val weight = localPrecision / total
                    speed += weight * local.speedMps
                    speedWeight += weight
                    local.addSpeedsTo(bins, weight)
                }
                if (speedMass > 0.0) {
                    val weight = neighbourPrecision / total
                    speed += weight * speedSum / speedMass
                    speedWeight += weight
                    // A neighbour is only worth mixing into the histogram when it is worth seeing in it.
                    val cutoff = speedMass * settings.minWeightFraction
                    for (source in sources) {
                        if (source.index == index || source.speedMps == null) continue
                        val distance = distance(centre, source.centre)
                        if (distance > range) continue
                        val share = exp(-distance / settings.correlationLengthMeters) * source.samples
                        if (share >= cutoff) source.addSpeedsTo(bins, weight * share / speedMass)
                    }
                }
                if (global?.speedMps != null) {
                    val weight = globalPrecision / total
                    speed += weight * global.speedMps
                    speedWeight += weight
                    global.addSpeedsTo(bins, weight)
                }
                speedsMps[index] = if (speedWeight > 0.0) speed / speedWeight else 0.0
                speeds[index] = SpeedHistogram.fromWeights(bins)
                distances[index] = nearest
            }
            return WindField(spec, settings, shifts, spreads, sampleCounts, measured, confidences, speedsMps, speeds, distances)
        }

        private fun distance(from: CoursePosition, to: CoursePosition): Double {
            val across = from.acrossMeters - to.acrossMeters
            val upwind = from.upwindMeters - to.upwindMeters
            return sqrt(across * across + upwind * upwind)
        }

        /** Three winds and what each of them is worth, added up as vectors so that 350 and 10 average to 0. */
        private class Blend {
            var weight: Double = 0.0
            private var cosine: Double = 0.0
            private var sine: Double = 0.0
            var wanderVariance: Double = 0.0
                private set

            fun add(weight: Double, cosine: Double, sine: Double, wanderDegrees: Double) {
                if (weight <= 0.0) return
                this.weight += weight
                this.cosine += weight * cosine
                this.sine += weight * sine
                wanderVariance += weight * wanderDegrees * wanderDegrees
            }

            fun degrees(): Double = Angles.signedDifference(0.0, Angles.toDegrees(atan2(sine, cosine)))
        }

        /**
         * What was measured in one place - one square, or the whole course: its mean wind as a unit vector
         * off the reference, how much the wind wandered there, and the speeds seen there.
         */
        private class Measured(
            val index: Int,
            val centre: CoursePosition,
            val samples: Int,
            val measured: Boolean,
            val cosine: Double,
            val sine: Double,
            val wanderDegrees: Double,
            val speedMps: Double?,
            val speeds: SpeedHistogram,
        ) {
            /**
             * Adds this place's speeds into a blend, weighed by [weight]. The histogram is normalised
             * first: how much a place's speeds count is [weight]'s business, not the number of seconds
             * that happened to be sailed there.
             */
            fun addSpeedsTo(bins: DoubleArray, weight: Double) {
                val total = speeds.totalWeight
                if (total > 0.0) speeds.addTo(bins, weight / total)
            }

            companion object {
                fun of(
                    index: Int,
                    centre: CoursePosition,
                    stats: CellStats,
                    referenceDegrees: Double,
                    settings: WindFieldSettings,
                ): Measured? {
                    val mean = stats.meanWindDegrees ?: return null
                    val radians = Angles.toRadians(Angles.signedDifference(referenceDegrees, mean))
                    val speedWeight = stats.speeds.totalWeight
                    return Measured(
                        index = index,
                        centre = centre,
                        samples = stats.upwindSamples,
                        measured = stats.upwindSamples >= settings.minCellSamples,
                        cosine = cos(radians),
                        sine = sin(radians),
                        // A square with a single sample has nothing to say about how much its wind wanders,
                        // so it is credited with what wind does anyway; a steadier one is never believed
                        // beyond the floor, because the tack angle it was read from is only a model.
                        wanderDegrees = (stats.windSpreadDegrees ?: settings.sampleSpreadDegrees)
                            .coerceAtLeast(settings.minSpreadDegrees),
                        speedMps = if (speedWeight > 0.0) stats.speedSum / speedWeight else null,
                        speeds = stats.speeds,
                    )
                }
            }
        }
    }
}
