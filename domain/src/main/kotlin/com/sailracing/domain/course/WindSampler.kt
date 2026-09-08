package com.sailracing.domain.course

import com.sailracing.domain.geo.Angles
import com.sailracing.domain.stats.Gaussian
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * One wind the race course might have: a direction and a close-hauled boat speed for every square, drawn
 * from what was measured there. One run of the Monte Carlo sails through one of these.
 */
public class SampledConditions internal constructor(
    override val spec: GridSpec,
    private val shifts: DoubleArray,
    private val speeds: DoubleArray,
) : SailingConditions {

    override fun shiftDegrees(cell: GridCell): Double = shifts[spec.index(cell)]

    override fun speedMps(cell: GridCell): Double = speeds[spec.index(cell)]

    override fun toString(): String = "SampledConditions(spec=$spec)"
}

/**
 * Draws a whole racing area's wind out of what has been measured of it.
 *
 * ## What a square's wind is worth
 *
 * Every square already carries a mean and a spread, blended out of its own samples, the samples around it
 * and the wind over the whole course: that is [WindField]'s work, and this draws from what it says.
 *
 * ## What the wind of the moment is worth
 *
 * The boat is sailing close-hauled right now, so its heading and its tack angle are a measurement of the
 * wind *here, now* - the freshest there is. It is worth more than the square's own mean, so the square the
 * boat is in is drawn around a mean pulled [RaceLineSettings.currentWindWeight] of the way onto it, and
 * with its spread cut by the same confidence. That belief does not stop at the square: it fades away over
 * [RaceLineSettings.currentWindRangeMeters], because the header the boat is in now says a lot about the
 * water just ahead and little about the far corner.
 *
 * ## Why the squares do not come out as a chequerboard
 *
 * Wind is not independent from square to square: it comes in shifts hundreds of metres wide. So the field
 * is not drawn square by square but as one correlated whole: independent draws are run through a first
 * order filter across the area and then up it, which leaves every square with exactly the spread it should
 * have and any two of them correlated by `exp(-distance / correlationLength)`, counted in squares along
 * and up. A drawn wind is therefore a few broad shifts lying over the course, which is what a wind looks
 * like, and never a speckle.
 *
 * That is also how the wind of the moment reaches the squares around the boat. It is not carried from
 * square to square as the search walks outwards; it moves the mean of every square by how far away that
 * square is and narrows the ones nearest the boat, and the correlation of the field then keeps whatever is
 * drawn on top of that hanging together.
 *
 * The boat speeds are drawn the same way and at the same time, from each square's speed histogram rather
 * than from a bell curve, so a corner that is half puff and half hole is drawn as a puff or as a hole and
 * never as its mean. The two draws are independent: a square being shifted says nothing here about it
 * being windy.
 */
public object WindSampler {

    /**
     * One drawn wind over [field], for a boat at [from] measuring [currentShiftDegrees] right now (null
     * when it is not sailing close-hauled and has nothing to say about the wind).
     */
    public fun sample(
        field: WindField,
        from: CoursePosition,
        currentShiftDegrees: Double? = null,
        settings: RaceLineSettings = RaceLineSettings(),
        random: Random = Random(settings.seed),
    ): SampledConditions = draw(field, from, currentShiftDegrees, settings, random)

    /**
     * The wind without the chance: every square at its mean, pulled onto the wind of the moment the same
     * way a drawn one is. It is the wind the race line was searched in before there was a Monte Carlo, and
     * it is the candidate line the simulations are compared against.
     */
    public fun mean(
        field: WindField,
        from: CoursePosition,
        currentShiftDegrees: Double? = null,
        settings: RaceLineSettings = RaceLineSettings(),
    ): SampledConditions = draw(field, from, currentShiftDegrees, settings, random = null)

    /** How much the wind measured at the boat right now is believed [distanceMeters] away from it. */
    public fun currentWindWeight(distanceMeters: Double, settings: RaceLineSettings): Double =
        settings.currentWindWeight * exp(-distanceMeters / settings.currentWindRangeMeters)

    private fun draw(
        field: WindField,
        from: CoursePosition,
        currentShiftDegrees: Double?,
        settings: RaceLineSettings,
        random: Random?,
    ): SampledConditions {
        val spec = field.spec
        val count = spec.cellCount
        val shifts = DoubleArray(count)
        val speeds = DoubleArray(count)
        val correlationLengthMeters = field.settings.correlationLengthMeters
        val windDeviates = correlatedField(spec, correlationLengthMeters, random)
        val speedDeviates = correlatedField(spec, correlationLengthMeters, random)
        val boat = spec.center(spec.nearestCell(from))

        for (index in 0 until count) {
            val cell = spec.cellAt(index)
            val measured = field.at(cell)
            val centre = spec.center(cell)
            val weight = if (currentShiftDegrees == null) {
                0.0
            } else {
                currentWindWeight(hypot(centre.acrossMeters - boat.acrossMeters, centre.upwindMeters - boat.upwindMeters), settings)
            }
            val mean = measured.shiftDegrees + weight * Angles.signedDifference(measured.shiftDegrees, currentShiftDegrees ?: 0.0)
            // Knowing the wind here now does not only move the square's mean, it narrows it: the closer to
            // the boat, the less is left to chance.
            val spread = measured.spreadDegrees * sqrt(1.0 - weight)
            shifts[index] = mean + spread * windDeviates[index]
            speeds[index] = speedMps(measured, settings, if (random == null) null else Gaussian.cdf(speedDeviates[index]))
        }
        return SampledConditions(spec, shifts, speeds)
    }

    /**
     * A field of standard normal deviates over the whole area - mean nothing, spread one - in which two
     * squares are correlated by `exp(-distance / correlationLength)`, the distance counted along the area
     * and up it. Independent draws are filtered along every row and then up every column, each square
     * keeping [WindFieldSettings.correlationLengthMeters] worth of the one before it and the rest of its
     * spread left to chance: two passes of a first order filter, and the spread of every square survives
     * them exactly. All zero when there is nothing to draw with, which is what makes the mean field.
     */
    private fun correlatedField(spec: GridSpec, correlationLengthMeters: Double, random: Random?): DoubleArray {
        val values = DoubleArray(spec.cellCount) { normal(random) }
        if (random == null) return values
        val coupling = exp(-spec.cellSizeMeters / correlationLengthMeters)
        val chance = sqrt(1.0 - coupling * coupling)
        for (row in 0 until spec.rows) {
            for (column in 1 until spec.columns) {
                val index = row * spec.columns + column
                values[index] = coupling * values[index - 1] + chance * values[index]
            }
        }
        for (row in 1 until spec.rows) {
            for (column in 0 until spec.columns) {
                val index = row * spec.columns + column
                values[index] = coupling * values[index - spec.columns] + chance * values[index]
            }
        }
        return values
    }

    /**
     * The speed drawn at [fraction] of the square's histogram, or its mean when there is nothing to draw.
     * The draw is taken as a deviation from the histogram's own mean and added to the square's exact mean
     * speed, so that binning the speeds moves the shape of the draw and never the speed it is drawn around.
     */
    private fun speedMps(cell: CellWind, settings: RaceLineSettings, fraction: Double?): Double {
        val histogramMean = cell.speeds.meanMps
        if (fraction == null || histogramMean == null) return cell.speedMps.coerceAtLeast(settings.minBoatSpeedMps)
        val drawn = cell.speedMps + settings.speedSpreadFactor * (cell.speeds.quantile(fraction) - histogramMean)
        return drawn.coerceAtLeast(settings.minBoatSpeedMps)
    }

    private fun normal(random: Random?): Double = if (random == null) 0.0 else Gaussian.sample(random)
}
