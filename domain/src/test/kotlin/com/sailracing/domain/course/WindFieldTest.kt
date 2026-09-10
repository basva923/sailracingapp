package com.sailracing.domain.course

import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.wind.SpeedHistogram
import com.sailracing.domain.wind.WindHistogram
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the wind field is: every close-hauled sample of the track binned into the big square it was taken
 * in, as a histogram of shifts and a histogram of speeds, plus what share of the day's evidence that is.
 * Nothing is smoothed and nothing is invented - a square nobody sailed through holds nothing at all.
 */
class WindFieldTest {

    /** 300 x 300 m in squares of 100 m: three across, so one big square is one square and the sums are plain. */
    private val spec = GridSpec(-150.0, 0.0, 3, 3, 100.0)
    private val frame = CourseFixtures.frame

    private fun samples(spec: GridSpec, cell: GridCell, shiftDegrees: Double, count: Int, speedMps: Double? = 3.0): List<TrackPoint> {
        val place = frame.toGeo(spec.center(cell))
        return List(count) { TrackPoint(it * 1000L, place, speedMps = speedMps, headingDegrees = 0.0, upwindWindDegrees = shiftDegrees) }
    }

    private fun field(points: List<TrackPoint>, settings: WindFieldSettings = WindFieldSettings()): WindField =
        WindField.build(CourseFixtures.track(points), frame, spec, settings)

    @Test
    fun `a big square holds the winds and the speeds sailed in it, and its share of the day`() {
        val field = field(
            samples(spec, GridCell(0, 1), 20.0, 30) + samples(spec, GridCell(2, 1), -10.0, 10, speedMps = 4.0),
        )
        val left = field.at(GridCell(0, 1))
        assertEquals(30, left.samples)
        assertEquals(30, left.winds.count(20))
        assertEquals(20.0, assertNotNull(left.meanShiftDegrees), 1e-9)
        assertEquals(3.0, assertNotNull(left.meanSpeedMps), SpeedHistogram.BIN_WIDTH_MPS)
        assertEquals(0.75, left.share, 1e-9, "three of every four samples were taken here")
        assertTrue(left.measured)

        val right = field.at(GridCell(2, 1))
        assertEquals(-10.0, assertNotNull(right.meanShiftDegrees), 1e-9)
        assertEquals(4.0, assertNotNull(right.meanSpeedMps), SpeedHistogram.BIN_WIDTH_MPS)
        assertEquals(0.25, right.share, 1e-9)

        assertEquals(40, field.samples)
        assertEquals(2, field.measuredCount)
        assertEquals(9, field.blocks.size)
        assertEquals(WindFieldSettings(), field.settings)
        assertEquals("WindField(spec=${field.spec}, samples=40, measured=2)", field.toString())
    }

    @Test
    fun `a square nobody sailed through holds nothing, and says so`() {
        val field = field(samples(spec, GridCell(0, 0), 12.0, 20))
        val empty = field.at(GridCell(2, 2))
        assertEquals(0, empty.samples)
        assertEquals(0.0, empty.share)
        assertFalse(empty.measured)
        assertNull(empty.meanShiftDegrees)
        assertNull(empty.meanSpeedMps)
        assertTrue(empty.winds.isEmpty && empty.speeds.isEmpty)
        assertEquals(GridCell(2, 2), empty.cell)
        assertEquals(BlockWind(GridCell(2, 2)), empty, "an unsailed square is the empty one")
    }

    @Test
    fun `the whole course's histograms are every sample, wherever it was taken`() {
        val field = field(samples(spec, GridCell(0, 0), 20.0, 30) + samples(spec, GridCell(2, 2), -20.0, 10, speedMps = 5.0))
        assertEquals(40, field.courseWinds.totalSamples)
        assertEquals(30, field.courseWinds.count(20))
        assertEquals(10, field.courseWinds.count(-20))
        assertEquals(40.0, field.courseSpeeds.histogram.totalWeight, 1e-9)
        assertEquals(30.0, field.courseSpeeds.histogram.weightAt(3.0), 1e-9)
        assertEquals(10.0, field.courseSpeeds.histogram.weightAt(5.0), 1e-9)
        // The course is the sum of its squares, however they are cut.
        val blocks = field.blocks.fold(WindHistogram()) { sum, block -> sum + block.winds }
        assertEquals(field.courseWinds, blocks)
        assertEquals(1.0, field.blocks.sumOf { it.share }, 1e-9)
    }

    @Test
    fun `the histograms hold shifts off the reference wind, so turning the frame turns them`() {
        val place = GeoPoint(51.14, 5.83)
        val points = List(20) { TrackPoint(0L, place, speedMps = 3.0, headingDegrees = 0.0, upwindWindDegrees = 20.0) }
        val track = CourseFixtures.track(points)
        fun meanShiftAt(referenceDegrees: Double): Double {
            val frame = CourseFrame(place, windDirectionDegrees = referenceDegrees)
            val field = WindField.build(track, frame, spec)
            return assertNotNull(field.at(assertNotNull(field.spec.cellOf(CoursePosition(0.0, 0.0)))).meanShiftDegrees)
        }
        assertEquals(20.0, meanShiftAt(0.0), 1e-9)
        assertEquals(15.0, meanShiftAt(5.0), 1e-9)
        assertEquals(-10.0, meanShiftAt(30.0), 1e-9, "a wind backed of the reference is a negative shift")
    }

    @Test
    fun `only close-hauled samples inside the area count, and only some of them carry a speed`() {
        val inside = spec.center(GridCell(1, 1))
        val field = field(
            listOf(
                TrackPoint(0L, frame.toGeo(inside), speedMps = 3.0, headingDegrees = 0.0, upwindWindDegrees = 10.0),
                // Not close-hauled: a position and a speed, but nothing to say about the wind.
                TrackPoint(1000L, frame.toGeo(inside), speedMps = 6.0, headingDegrees = 90.0, upwindWindDegrees = null),
                // Close-hauled without a fix of its speed: the wind counts, the speed cannot.
                TrackPoint(2000L, frame.toGeo(inside), speedMps = null, headingDegrees = 0.0, upwindWindDegrees = 10.0),
                // A kilometre away: outside the racing area this field was cut for.
                TrackPoint(3000L, frame.toGeo(CoursePosition(0.0, 1_000.0)), speedMps = 3.0, headingDegrees = 0.0, upwindWindDegrees = 40.0),
            ),
        )
        assertEquals(2, field.samples)
        assertEquals(2, field.at(GridCell(1, 1)).samples)
        assertEquals(1.0, field.courseSpeeds.histogram.totalWeight, 1e-9)
        assertEquals(0, field.courseWinds.count(40), "a sample outside the area is not the area's wind")
    }

    @Test
    fun `how many samples make a square measured is a setting, and it changes nothing else`() {
        val points = samples(spec, GridCell(1, 1), 40.0, 2)
        val strict = field(points)
        val lenient = field(points, WindFieldSettings(minBlockSamples = 2))
        assertFalse(strict.at(GridCell(1, 1)).measured)
        assertTrue(lenient.at(GridCell(1, 1)).measured)
        assertEquals(0, strict.measuredCount)
        assertEquals(1, lenient.measuredCount)
        assertEquals(strict.at(GridCell(1, 1)).winds, lenient.at(GridCell(1, 1)).winds)
        assertEquals(strict.at(GridCell(1, 1)).share, lenient.at(GridCell(1, 1)).share)
    }

    @Test
    fun `the big squares are square, three across the area and as many rows as it is tall`() {
        val tall = WindField.build(Track(), frame, GridSpec(-150.0, 0.0, 6, 24, 50.0))
        assertEquals(3, tall.spec.columns)
        assertEquals(100.0, tall.spec.cellSizeMeters, "a 300 m wide area is cut into three 100 m squares")
        assertEquals(12, tall.spec.rows, "1200 m of course is twelve of them tall")
        assertEquals(GridSpec(-150.0, 0.0, 6, 24, 50.0), tall.cellSpec)

        // A wide, shallow area is one row of big squares, and the row reaches above the water it covers.
        val wide = WindField.build(Track(), frame, GridSpec(-150.0, 0.0, 6, 1, 50.0))
        assertEquals(1, wide.spec.rows)
        assertEquals(100.0, wide.spec.cellSizeMeters)
        assertEquals(100.0, wide.spec.topMeters, "the big squares cover the area even when it is thinner than they are")

        // How many across is a setting; the rows follow it, because a square is a square.
        val fewer = WindField.build(Track(), frame, spec, WindFieldSettings(columns = 2))
        assertEquals(2, fewer.spec.columns)
        assertEquals(150.0, fewer.spec.cellSizeMeters)
        assertEquals(2, fewer.spec.rows)
    }

    @Test
    fun `every square of the racing area belongs to the big square its middle lies in`() {
        // 5 squares of 60 m across 300 m, cut into three big squares of 100: the middle one straddles.
        val area = GridSpec(0.0, 0.0, 5, 5, 60.0)
        val field = WindField.build(Track(), frame, area)
        val drawn = WindSampler.of(field).mean
        for (index in 0 until area.cellCount) {
            val cell = area.cellAt(index)
            val block = field.spec.nearestCell(area.center(cell))
            assertEquals(drawn.blockShiftDegrees(block), drawn.shiftDegrees(cell), 1e-9, "$cell should follow $block")
            assertEquals(drawn.blockSpeedMps(block), drawn.speedMps(cell), 1e-9, "$cell should follow $block")
        }
    }
}
