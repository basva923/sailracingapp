package com.sailracing.domain.course

import com.sailracing.domain.geo.Angles
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the drawn winds are made of: every big square drawn on its own, from the wind of the moment, from
 * its own histogram, from the whole course's histogram or from nothing at all, in the shares the settings
 * give those four.
 *
 * The assertions are statistical on purpose - a sampler is only right in the long run - but the dice are
 * seeded, so a run either always passes or always fails.
 */
class WindSamplerTest {

    /** 300 x 300 m in squares of 100 m: three across, so one big square is one square of the area. */
    private val spec = GridSpec(-150.0, 0.0, 3, 3, 100.0)
    private val left = GridCell(0, 1)
    private val right = GridCell(2, 1)
    private val settings = WindFieldSettings()

    /**
     * Three quarters of the day sailed in the left square at +30, the last quarter in the right one at -30:
     * a course where a square's own histogram and the whole course's say clearly different things.
     */
    private val lopsided: WindField = run {
        val points = ArrayList<TrackPoint>()
        fun sail(cell: GridCell, shiftDegrees: Double, count: Int) {
            val place = CourseFixtures.frame.toGeo(spec.center(cell))
            repeat(count) { points += TrackPoint(points.size * 1000L, place, 3.0, 0.0, Angles.normalize(shiftDegrees)) }
        }
        sail(left, 30.0, 300)
        sail(right, -30.0, 100)
        WindField.build(CourseFixtures.track(points), CourseFixtures.frame, spec)
    }

    private fun draws(field: WindField, cell: GridCell, currentShiftDegrees: Double? = null, runs: Int = 4000): List<Double> {
        val sampler = WindSampler.of(field, currentShiftDegrees)
        val random = Random(20260908L)
        return List(runs) { sampler.draw(random).blockShiftDegrees(cell) }
    }

    /** How often the drawn winds landed within half a degree of [degrees]: the histogram had one bin there. */
    private fun fractionAt(drawn: List<Double>, degrees: Double): Double =
        drawn.count { abs(Angles.signedDifference(degrees, it)) <= 0.5 } / drawn.size.toDouble()

    @Test
    fun `a square is drawn from the moment, from itself, from the course and from nothing at all`() {
        assertEquals(0.75, lopsided.at(left).share, 1e-9, "the fixture means three of four samples to be the left square's")
        val drawn = draws(lopsided, left, currentShiftDegrees = 0.0)

        // 35% the wind of the moment; 60% split 3:1 between its own histogram and the whole course's, and
        // the course's is itself three parts +30 to one part -30; 5% anywhere on the compass.
        assertEquals(settings.currentWindFraction, fractionAt(drawn, 0.0), 0.03, "the wind of the moment is not a third of it")
        assertEquals(0.5625, fractionAt(drawn, 30.0), 0.03, "0.6 * 0.75 of its own, plus 0.6 * 0.25 * 0.75 of the course's")
        assertEquals(0.0375, fractionAt(drawn, -30.0), 0.02, "0.6 * 0.25 * 0.25 of the course's")
        val anywhere = drawn.count { abs(Angles.signedDifference(0.0, it)) > 0.5 && abs(Angles.signedDifference(30.0, it)) > 0.5 &&
            abs(Angles.signedDifference(-30.0, it)) > 0.5 } / drawn.size.toDouble()
        assertEquals(settings.randomWindFraction, anywhere, 0.02, "chance is not a twentieth of it")
    }

    @Test
    fun `a square nobody sailed through blows what the course blew`() {
        val drawn = draws(lopsided, GridCell(1, 2), currentShiftDegrees = 0.0)
        assertEquals(0.0, lopsided.at(GridCell(1, 2)).share, 1e-9)
        // Nothing of its own, so the whole 60% is the course's histogram: three parts +30 to one part -30.
        assertEquals(0.45, fractionAt(drawn, 30.0), 0.03)
        assertEquals(0.15, fractionAt(drawn, -30.0), 0.03)
        assertEquals(settings.currentWindFraction, fractionAt(drawn, 0.0), 0.03)
    }

    @Test
    fun `without a wind of the moment its share goes to the histograms`() {
        val drawn = draws(lopsided, left)
        assertEquals(0.95 * 0.75 + 0.95 * 0.25 * 0.75, fractionAt(drawn, 30.0), 0.03, "the moment's share was not handed on")
        assertEquals(0.95 * 0.25 * 0.25, fractionAt(drawn, -30.0), 0.02)
        assertEquals(settings.randomWindFraction, 1.0 - fractionAt(drawn, 30.0) - fractionAt(drawn, -30.0), 0.02)
    }

    @Test
    fun `the winds come out of the histogram itself, not out of a bell curve fitted over it`() {
        // A wind that swung 20 degrees either way all day and never sat in the middle: neither should the draws.
        val swinging = CourseFixtures.field(spec, samplesPerCell = 60) { _, index, _ ->
            CourseFixtures.Sample(if (index % 2 == 0) 20.0 else -20.0, 3.0)
        }
        val drawn = draws(swinging, left)
        assertEquals(0.475, fractionAt(drawn, 20.0), 0.03, "half of the 95% that is measured wind")
        assertEquals(0.475, fractionAt(drawn, -20.0), 0.03)
        val middle = drawn.count { abs(it) < 10.0 } / drawn.size.toDouble()
        assertTrue(middle < 0.02, "a wind that never blew from the middle was drawn from it: $middle")
        // The mean of a histogram that never blows its own mean is still that mean, and it is what the map draws.
        assertEquals(0.0, WindSampler.of(swinging).mean.blockShiftDegrees(left), 1.0)
    }

    @Test
    fun `a drawn direction lands anywhere inside its bin, not on the whole degree`() {
        val drawn = draws(lopsided, left).filter { abs(Angles.signedDifference(30.0, it)) <= 0.5 }
        assertTrue(drawn.none { it == 30.0 }, "every draw landed on the bin's own degree")
        assertEquals(30.0, drawn.average(), 0.05, "the bin should be drawn from evenly")
    }

    @Test
    fun `an area nobody has sailed is the reference wind, and chance`() {
        val nothing = WindField.build(Track(), CourseFixtures.frame, spec)
        assertEquals(0, nothing.samples)
        val drawn = draws(nothing, left)
        assertEquals(1.0 - settings.randomWindFraction, fractionAt(drawn, 0.0), 0.02, "with nothing measured the reference is all there is")
        assertTrue(drawn.any { abs(it) > 90.0 }, "chance never turned up at all")
        // And nothing has been measured of the boat's speed either, so it sails at the speed assumed for it.
        val speeds = WindSampler.of(nothing).draw(Random(1))
        assertEquals(SailingConditions.DEFAULT_BOAT_SPEED_MPS, speeds.blockSpeedMps(left), 1e-9)
        assertEquals(SailingConditions.DEFAULT_BOAT_SPEED_MPS, WindSampler.of(nothing).mean.blockSpeedMps(left), 1e-9)
    }

    @Test
    fun `the wind of the moment can be believed entirely, or not at all`() {
        val only = WindFieldSettings(currentWindFraction = 1.0, randomWindFraction = 0.0)
        val field = CourseFixtures.field(spec, samplesPerCell = 60, settings = only) { _, _, _ -> CourseFixtures.Sample(30.0, 3.0) }
        val drawn = WindSampler.of(field, currentShiftDegrees = -12.0).draw(Random(7))
        for (index in 0 until field.spec.cellCount) {
            assertEquals(-12.0, drawn.blockShiftDegrees(field.spec.cellAt(index)), 1e-9, "the far corners feel it too")
        }
        assertEquals(-12.0, WindSampler.of(field, -12.0).mean.blockShiftDegrees(left), 1e-9)

        val ignored = WindFieldSettings(currentWindFraction = 0.0, randomWindFraction = 0.0)
        val deaf = CourseFixtures.field(spec, samplesPerCell = 60, settings = ignored) { _, _, _ -> CourseFixtures.Sample(30.0, 3.0) }
        assertEquals(30.0, WindSampler.of(deaf, currentShiftDegrees = -12.0).mean.blockShiftDegrees(left), 0.5)
    }

    @Test
    fun `the mean wind is where all the draws add up to`() {
        val sampler = WindSampler.of(lopsided, currentShiftDegrees = -20.0)
        val random = Random(4L)
        var cosine = 0.0
        var sine = 0.0
        val runs = 6000
        repeat(runs) {
            val radians = Angles.toRadians(sampler.draw(random).blockShiftDegrees(left))
            cosine += cos(radians)
            sine += sin(radians)
        }
        val drawnMean = Angles.signedDifference(0.0, Angles.toDegrees(atan2(sine, cosine)))
        assertEquals(drawnMean, sampler.mean.blockShiftDegrees(left), 1.5, "the mean is not the middle of the draws")
        // It sits between the wind of the moment and what the square has been measuring.
        assertTrue(sampler.mean.blockShiftDegrees(left) in -20.0..30.0)
        assertEquals("SampledConditions(spec=$spec, blocks=${lopsided.spec})", sampler.mean.toString())
    }

    @Test
    fun `a puffy square is drawn as puffs and holes, never as its mean`() {
        // Two thirds of the samples at 2 m/s, one third at 4.4: a mean of 2.8 the boat never sails.
        val field = CourseFixtures.field(spec, samplesPerCell = 60) { _, index, _ ->
            CourseFixtures.Sample(0.0, if (index % 3 == 0) 4.4 else 2.0)
        }
        val sampler = WindSampler.of(field)
        val random = Random(11L)
        val drawn = List(2000) { sampler.draw(random).blockSpeedMps(left) }
        assertEquals(2.8, drawn.average(), 0.15, "the drawn speeds are not centred on what was measured")
        assertEquals(2.8, sampler.mean.blockSpeedMps(left), 0.15)
        assertTrue(drawn.any { it > 3.5 }, "never drew the puff: ${drawn.max()}")
        assertTrue(drawn.any { it < 2.4 }, "never drew the lull: ${drawn.min()}")
        // Only part of the spread of a per-second histogram is felt over a whole board across a square.
        val histogram = assertNotNull(field.at(left).speeds.histogram.spreadMps)
        val mean = drawn.average()
        val spread = sqrt(drawn.sumOf { (it - mean) * (it - mean) } / drawn.size)
        assertEquals(settings.speedSpreadFactor * histogram, spread, 0.15, "the speed spread is not the settings'")
    }

    @Test
    fun `a drawn speed is never slower than the settings allow`() {
        val stopped = CourseFixtures.field(spec, samplesPerCell = 20) { _, index, _ ->
            CourseFixtures.Sample(0.0, if (index == 0) 0.05 else 0.4)
        }
        val sampler = WindSampler.of(stopped)
        val random = Random(3L)
        val drawn = List(500) { sampler.draw(random).blockSpeedMps(left) }
        assertTrue(drawn.all { it >= settings.minBoatSpeedMps }, "a boat was drawn slower than the floor: ${drawn.min()}")
        assertTrue(sampler.mean.blockSpeedMps(left) >= settings.minBoatSpeedMps)
    }

    @Test
    fun `a square sailed through without a speed to show for it borrows the course's`() {
        val place = CourseFixtures.frame.toGeo(spec.center(left))
        val elsewhere = CourseFixtures.frame.toGeo(spec.center(right))
        val points = List(60) { TrackPoint(it * 1000L, place, speedMps = null, headingDegrees = 0.0, upwindWindDegrees = 10.0) } +
            List(60) { TrackPoint(60_000L + it * 1000L, elsewhere, speedMps = 4.0, headingDegrees = 0.0, upwindWindDegrees = 10.0) }
        val field = WindField.build(CourseFixtures.track(points), CourseFixtures.frame, spec)
        assertEquals(0.5, field.at(left).share, 1e-9)
        assertTrue(field.at(left).speeds.isEmpty, "the fixture means to sail the left square without a speed")
        val sampler = WindSampler.of(field)
        val random = Random(5L)
        val drawn = List(500) { sampler.draw(random).blockSpeedMps(left) }
        assertEquals(4.0, drawn.average(), 0.2, "it should sail at the speed the rest of the course did")
        assertEquals(4.0, sampler.mean.blockSpeedMps(left), 0.2)
    }

    @Test
    fun `the same dice draw the same wind, and other dice draw another`() {
        val sampler = WindSampler.of(lopsided, currentShiftDegrees = 5.0)
        val once = sampler.draw(Random(42))
        val again = sampler.draw(Random(42))
        val other = sampler.draw(Random(43))
        var same = 0
        for (index in 0 until lopsided.spec.cellCount) {
            val block = lopsided.spec.cellAt(index)
            assertEquals(once.blockShiftDegrees(block), again.blockShiftDegrees(block), 0.0, "at $block")
            assertEquals(once.blockSpeedMps(block), again.blockSpeedMps(block), 0.0, "at $block")
            if (once.blockShiftDegrees(block) == other.blockShiftDegrees(block)) same++
        }
        assertTrue(same < lopsided.spec.cellCount, "another seed drew exactly the same wind everywhere")
    }

    @Test
    fun `every big square is drawn on its own, so one wind is not one shift over the whole course`() {
        val sampler = WindSampler.of(lopsided, currentShiftDegrees = 0.0)
        val random = Random(9L)
        var different = 0
        val runs = 500
        repeat(runs) {
            val wind = sampler.draw(random)
            if (abs(wind.blockShiftDegrees(left) - wind.blockShiftDegrees(right)) > 1.0) different++
        }
        assertTrue(different > runs / 4, "the squares came out as one wind laid over the course: $different of $runs")
    }
}
