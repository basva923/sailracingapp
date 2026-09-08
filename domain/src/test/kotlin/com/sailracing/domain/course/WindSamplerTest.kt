package com.sailracing.domain.course

import com.sailracing.domain.wind.SpeedHistogram
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the drawn winds are made of: every square around its own measured mean, spread by how much the wind
 * wandered there, pulled onto the wind the boat is measuring right now, and correlated with the squares
 * next to it rather than drawn as a chequerboard.
 *
 * The assertions are statistical on purpose - a sampler is only right in the long run - but the seed is
 * fixed, so a run either always passes or always fails.
 */
class WindSamplerTest {

    private val spec = GridSpec(-150.0, 0.0, 6, 6, 50.0)
    private val boat = CoursePosition(-125.0, 25.0)
    private val settings = RaceLineSettings()

    /** A shifty field: every square measured, the wind wandering about 10 degrees, the boat at 3 m/s. */
    private fun shifty(spreadDegrees: Double = 10.0, meanShift: (GridCell) -> Double = { 0.0 }): WindField =
        CourseFixtures.field(spec, samplesPerCell = 60) { cell, index, _ ->
            // A wind that swings evenly either side of the square's mean: measured, with a known spread.
            val swing = if (index % 2 == 0) spreadDegrees else -spreadDegrees
            CourseFixtures.Sample(meanShift(cell) + swing, 3.0)
        }

    private fun draws(field: WindField, cell: GridCell, currentShiftDegrees: Double? = null, runs: Int = 400): List<Double> =
        List(runs) { WindSampler.sample(field, boat, currentShiftDegrees, settings, Random(it.toLong())).shiftDegrees(cell) }

    @Test
    fun `a square is drawn around its own measured wind, with its own spread`() {
        val field = shifty(spreadDegrees = 10.0) { cell -> (cell.column - 2.5) * 4.0 }
        for (cell in listOf(GridCell(0, 0), GridCell(3, 2), GridCell(5, 5))) {
            val measured = field.at(cell)
            val drawn = draws(field, cell)
            val mean = drawn.average()
            val spread = sqrt(drawn.sumOf { (it - mean) * (it - mean) } / drawn.size)
            assertEquals(measured.shiftDegrees, mean, 1.5, "the draws are not centred on the measured wind of $cell")
            assertEquals(measured.spreadDegrees, spread, 2.0, "the draws of $cell are the wrong size")
        }
    }

    @Test
    fun `the wind measured now carries the square the boat is in and fades away from it`() {
        val field = shifty(spreadDegrees = 6.0)
        val boatCell = spec.nearestCell(boat)
        val far = GridCell(5, 5)
        assertEquals(0.0, field.at(boatCell).shiftDegrees, 0.5, "the fixture means to measure no shift anywhere")

        // The boat is beating in a wind veered 20 degrees from what the square has averaged so far.
        val here = draws(field, boatCell, currentShiftDegrees = 20.0).average()
        val there = draws(field, far, currentShiftDegrees = 20.0).average()
        assertEquals(20.0 * settings.currentWindWeight, here, 1.5, "the wind of the moment did not carry the boat's own square")
        assertTrue(there in 1.0..8.0, "the wind of the moment should reach the far corner faintly, not fully: $there")
        assertTrue(there < here / 2, "the wind of the moment did not fade with distance: $here here, $there there")

        // It narrows the square the boat is in as well as moving it: that wind has just been measured.
        val spread = draws(field, boatCell, currentShiftDegrees = 20.0).let { drawn ->
            val mean = drawn.average()
            sqrt(drawn.sumOf { (it - mean) * (it - mean) } / drawn.size)
        }
        val blind = draws(field, boatCell).let { drawn ->
            val mean = drawn.average()
            sqrt(drawn.sumOf { (it - mean) * (it - mean) } / drawn.size)
        }
        assertEquals(sqrt(1.0 - settings.currentWindWeight), spread / blind, 0.15, "knowing the wind here now did not narrow it")
    }

    @Test
    fun `how far the wind of the moment is believed is a setting`() {
        assertEquals(settings.currentWindWeight, WindSampler.currentWindWeight(0.0, settings), 1e-9)
        assertEquals(
            settings.currentWindWeight / exp(1.0),
            WindSampler.currentWindWeight(settings.currentWindRangeMeters, settings),
            1e-9,
        )
        assertTrue(WindSampler.currentWindWeight(1000.0, settings) < 0.05)
        // Turned off, the wind of the moment says nothing at all.
        assertEquals(0.0, WindSampler.currentWindWeight(0.0, settings.copy(currentWindWeight = 0.0)), 1e-9)

        val field = shifty()
        val ignored = settings.copy(currentWindWeight = 0.0)
        val cell = spec.nearestCell(boat)
        val drawn = List(200) { WindSampler.sample(field, boat, 25.0, ignored, Random(it.toLong())).shiftDegrees(cell) }
        assertEquals(0.0, drawn.average(), 1.5, "a weight of zero still moved the wind")
    }

    @Test
    fun `neighbouring squares are drawn shifted together, not as a chequerboard`() {
        val field = shifty(spreadDegrees = 10.0)
        val runs = 400
        val here = ArrayList<Double>(runs)
        val next = ArrayList<Double>(runs)
        val across = ArrayList<Double>(runs)
        for (run in 0 until runs) {
            val drawn = WindSampler.sample(field, boat, settings = settings, random = Random(run.toLong()))
            here += drawn.shiftDegrees(GridCell(2, 2))
            next += drawn.shiftDegrees(GridCell(3, 2))
            across += drawn.shiftDegrees(GridCell(5, 5))
        }
        val neighbours = correlation(here, next)
        val corners = correlation(here, across)
        // 50 m squares in a wind that hangs together over 150 m: about exp(-1/3) between neighbours.
        assertEquals(exp(-spec.cellSizeMeters / field.settings.correlationLengthMeters), neighbours, 0.15, "neighbours: $neighbours")
        assertTrue(corners < neighbours, "the far corner should follow the boat's square less: $corners against $neighbours")
        assertTrue(corners > 0.0, "the whole field should still lean one way or the other: $corners")
    }

    @Test
    fun `a puffy square is drawn as puffs and holes, never as its mean`() {
        // Two thirds of the samples at 2 m/s, one third at 4.4: a mean of 2.8 that the boat never sails.
        val field = CourseFixtures.field(spec, samplesPerCell = 60) { _, index, _ ->
            CourseFixtures.Sample(0.0, if (index % 3 == 0) 4.4 else 2.0)
        }
        val cell = GridCell(2, 2)
        assertEquals(2.8, field.speedMps(cell), 0.01)
        val drawn = List(400) { WindSampler.sample(field, boat, settings = settings, random = Random(it.toLong())).speedMps(cell) }
        assertEquals(2.8, drawn.average(), 0.15, "the drawn speeds are not centred on what was measured")
        assertTrue(drawn.any { it > 3.5 }, "never drew the puff: ${drawn.max()}")
        assertTrue(drawn.any { it < 2.4 }, "never drew the lull: ${drawn.min()}")
        // Only part of the spread of a per-second histogram is felt over a whole board across the square.
        val histogram = assertNotNull(field.at(cell).speeds.spreadMps)
        val spread = drawn.let { d -> sqrt(d.sumOf { (it - d.average()) * (it - d.average()) } / d.size) }
        assertEquals(settings.speedSpreadFactor * histogram, spread, 0.15, "the speed spread is not the settings'")
    }

    @Test
    fun `a drawn speed is never slower than the settings allow`() {
        // A square where the boat was measured stopped: drawn speeds still have to be sailable.
        val field = CourseFixtures.field(spec, samplesPerCell = 20) { _, index, _ ->
            CourseFixtures.Sample(0.0, if (index == 0) 0.05 else 0.4)
        }
        val cell = GridCell(1, 1)
        val drawn = List(200) { WindSampler.sample(field, boat, settings = settings, random = Random(it.toLong())).speedMps(cell) }
        assertTrue(drawn.all { it >= settings.minBoatSpeedMps }, "a boat was drawn slower than the floor: ${drawn.min()}")
    }

    @Test
    fun `where nothing was measured the boat sails at the assumed speed`() {
        val nothing = CourseFixtures.field(spec, samplesPerCell = 20) { _, _, _ -> null }
        assertEquals(0, nothing.measuredCount)
        assertEquals(SpeedHistogram(), nothing.at(GridCell(0, 0)).speeds)
        val drawn = WindSampler.sample(nothing, boat, settings = settings, random = Random(1))
        for (index in 0 until spec.cellCount) {
            assertEquals(SailingConditions.DEFAULT_BOAT_SPEED_MPS, drawn.speedMps(spec.cellAt(index)), 1e-9)
        }
        // The wind is still drawn: an area nobody has sailed is uncertain, not steady.
        val winds = List(200) { WindSampler.sample(nothing, boat, settings = settings, random = Random(it.toLong())).shiftDegrees(GridCell(3, 3)) }
        assertTrue(winds.any { abs(it) > 5.0 }, "an unmeasured area came out as a steady wind")
    }

    @Test
    fun `the mean wind is the field itself, held still`() {
        val field = shifty(spreadDegrees = 8.0) { cell -> (cell.column - 2.5) * 5.0 }
        val mean = WindSampler.mean(field, boat, settings = settings)
        for (index in 0 until spec.cellCount) {
            val cell = spec.cellAt(index)
            assertEquals(field.shiftDegrees(cell), mean.shiftDegrees(cell), 1e-9, "at $cell")
            assertEquals(field.speedMps(cell), mean.speedMps(cell), 1e-9, "at $cell")
        }
        assertEquals("SampledConditions(spec=$spec)", mean.toString())

        // With a wind of the moment it is the field pulled onto it, and still not random.
        val pulled = WindSampler.mean(field, boat, currentShiftDegrees = 30.0, settings = settings)
        val boatCell = spec.nearestCell(boat)
        assertEquals(
            field.shiftDegrees(boatCell) + settings.currentWindWeight * (30.0 - field.shiftDegrees(boatCell)),
            pulled.shiftDegrees(boatCell),
            1e-9,
        )
        for (index in 0 until spec.cellCount) {
            assertEquals(
                pulled.shiftDegrees(spec.cellAt(index)),
                WindSampler.mean(field, boat, 30.0, settings).shiftDegrees(spec.cellAt(index)),
                1e-9,
            )
        }
    }

    @Test
    fun `the same seed draws the same wind, and another seed draws another`() {
        val field = shifty()
        val once = WindSampler.sample(field, boat, 5.0, settings, Random(42))
        val again = WindSampler.sample(field, boat, 5.0, settings, Random(42))
        val other = WindSampler.sample(field, boat, 5.0, settings, Random(43))
        var same = 0
        for (index in 0 until spec.cellCount) {
            val cell = spec.cellAt(index)
            assertEquals(once.shiftDegrees(cell), again.shiftDegrees(cell), 0.0, "at $cell")
            assertEquals(once.speedMps(cell), again.speedMps(cell), 0.0, "at $cell")
            if (once.shiftDegrees(cell) == other.shiftDegrees(cell)) same++
        }
        assertEquals(0, same, "another seed drew the same wind")
        // The default random comes from the settings' seed, and the default settings are the defaults, so
        // a drawn wind is reproducible without either being spelled out.
        assertEquals(
            WindSampler.sample(field, boat, 5.0, settings).shiftDegrees(GridCell(0, 0)),
            WindSampler.sample(field, boat, 5.0, settings, Random(settings.seed)).shiftDegrees(GridCell(0, 0)),
        )
        assertEquals(
            WindSampler.sample(field, boat, 5.0, RaceLineSettings()).shiftDegrees(GridCell(0, 0)),
            WindSampler.sample(field, boat, 5.0).shiftDegrees(GridCell(0, 0)),
        )
        assertEquals(
            WindSampler.mean(field, boat, 5.0, RaceLineSettings()).shiftDegrees(GridCell(0, 0)),
            WindSampler.mean(field, boat, 5.0).shiftDegrees(GridCell(0, 0)),
        )
    }

    /** Pearson correlation of two series of draws. */
    private fun correlation(first: List<Double>, second: List<Double>): Double {
        val firstMean = first.average()
        val secondMean = second.average()
        var covariance = 0.0
        var firstVariance = 0.0
        var secondVariance = 0.0
        for (index in first.indices) {
            val a = first[index] - firstMean
            val b = second[index] - secondMean
            covariance += a * b
            firstVariance += a * a
            secondVariance += b * b
        }
        return covariance / sqrt(firstVariance * secondVariance)
    }
}
