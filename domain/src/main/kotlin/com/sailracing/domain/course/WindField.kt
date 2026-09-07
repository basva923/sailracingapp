package com.sailracing.domain.course

import com.sailracing.domain.geo.Angles
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The wind in one cell, relative to the frame's wind (the reference): positive = veered (clockwise).
 *
 * @property measured true when it comes from enough close-hauled samples inside the cell itself;
 *   false when it was estimated from the measured cells around it.
 */
public data class CellWind(val cell: GridCell, val shiftDegrees: Double, val samples: Int, val measured: Boolean)

/**
 * The wind over the whole racing area, one direction per cell: the mean of what was measured in the cells
 * that were sailed through often enough, and for every other cell an estimate from its neighbours
 * (inverse-distance weighted circular mean of all measured cells). Without any measured cell the field is
 * the reference wind everywhere.
 */
public class WindField private constructor(
    public val spec: GridSpec,
    private val shifts: DoubleArray,
    private val samples: IntArray,
    private val measured: BooleanArray,
) {

    public fun at(cell: GridCell): CellWind {
        val index = spec.index(cell)
        return CellWind(cell, shifts[index], samples[index], measured[index])
    }

    /** The wind shift at a position, from the cell containing it (or the nearest cell outside the area). */
    public fun shiftAt(position: CoursePosition): Double = shifts[spec.index(spec.nearestCell(position))]

    public val cells: List<CellWind> get() = (0 until spec.cellCount).map { at(spec.cellAt(it)) }

    public val measuredCount: Int get() = measured.count { it }

    override fun equals(other: Any?): Boolean =
        other is WindField && spec == other.spec && shifts.contentEquals(other.shifts) &&
            samples.contentEquals(other.samples) && measured.contentEquals(other.measured)

    override fun hashCode(): Int = 31 * spec.hashCode() + shifts.contentHashCode()

    override fun toString(): String = "WindField(spec=$spec, measured=$measuredCount)"

    public companion object {
        /** A cell needs this many close-hauled samples before its own wind counts as measured. */
        public const val MIN_CELL_SAMPLES: Int = 3

        public fun build(grid: TrackGrid, referenceDegrees: Double, minSamples: Int = MIN_CELL_SAMPLES): WindField {
            val spec = grid.spec
            val count = spec.cellCount
            val shifts = DoubleArray(count)
            val samples = IntArray(count)
            val measured = BooleanArray(count)
            val known = ArrayList<Triple<CoursePosition, Double, Double>>() // centre, cos, sin of the measured shift
            for (index in 0 until count) {
                val cell = spec.cellAt(index)
                val stats = grid.stats(cell)
                samples[index] = stats.upwindSamples
                val mean = stats.meanWindDegrees?.takeIf { stats.upwindSamples >= minSamples } ?: continue
                val shift = Angles.signedDifference(referenceDegrees, mean)
                shifts[index] = shift
                measured[index] = true
                val radians = Angles.toRadians(shift)
                known += Triple(spec.center(cell), cos(radians), sin(radians))
            }
            if (known.isNotEmpty()) {
                val cellSize = spec.cellSizeMeters
                for (index in 0 until count) {
                    if (measured[index]) continue
                    val centre = spec.center(spec.cellAt(index))
                    var c = 0.0
                    var s = 0.0
                    for ((at, cosine, sine) in known) {
                        val dx = (at.acrossMeters - centre.acrossMeters) / cellSize
                        val dy = (at.upwindMeters - centre.upwindMeters) / cellSize
                        val weight = 1.0 / (dx * dx + dy * dy)
                        c += weight * cosine
                        s += weight * sine
                    }
                    shifts[index] = Angles.signedDifference(0.0, Angles.toDegrees(atan2(s, c)))
                }
            }
            return WindField(spec, shifts, samples, measured)
        }
    }
}
