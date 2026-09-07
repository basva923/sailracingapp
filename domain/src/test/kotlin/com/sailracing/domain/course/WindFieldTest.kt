package com.sailracing.domain.course

import com.sailracing.domain.geo.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WindFieldTest {

    private val frame = CourseFrame(GeoPoint(51.14, 5.83), windDirectionDegrees = 0.0)
    private val spec = GridSpec(0.0, 0.0, 3, 3, 10.0)
    private val mark = spec.topCenter

    private fun samples(column: Int, row: Int, wind: Double, count: Int): List<TrackPoint> {
        val centre = spec.center(GridCell(column, row))
        return List(count) { TrackPoint(0L, frame.toGeo(centre), speedMps = 3.0, headingDegrees = 0.0, upwindWindDegrees = wind) }
    }

    private val track = Track(samples(0, 0, 10.0, 3) + samples(2, 2, 350.0, 3) + samples(1, 1, 40.0, 2))
    private val grid = TrackGrid.build(track, frame, spec, mark)

    @Test
    fun `measured cells keep their mean and the others are estimated from them`() {
        val field = WindField.build(grid, referenceDegrees = 0.0)
        val veered = field.at(GridCell(0, 0))
        assertEquals(CellWind(GridCell(0, 0), veered.shiftDegrees, 3, true), veered)
        assertEquals(10.0, veered.shiftDegrees, 1e-9)
        val backed = field.at(GridCell(2, 2))
        assertEquals(-10.0, backed.shiftDegrees, 1e-9)
        assertTrue(backed.measured)
        // Two samples are not enough: estimated, halfway between the two measured cells.
        val middle = field.at(GridCell(1, 1))
        assertFalse(middle.measured)
        assertEquals(2, middle.samples)
        assertEquals(0.0, middle.shiftDegrees, 1e-9)
        assertEquals(0.0, field.at(GridCell(0, 2)).shiftDegrees, 1e-9)
        // Next to the veered cell: weighted 1 : 1/5 towards it.
        assertEquals(6.7, field.at(GridCell(0, 1)).shiftDegrees, 0.05)
        assertEquals(6.7, field.shiftAt(CoursePosition(5.0, 15.0)), 0.05)
        assertEquals(10.0, field.shiftAt(CoursePosition(-100.0, -100.0)), 1e-9)
        assertEquals(2, field.measuredCount)
        assertEquals(9, field.cells.size)
        assertEquals(spec, field.spec)
        assertEquals("WindField(spec=$spec, measured=2)", field.toString())
    }

    @Test
    fun `the reference and the sample threshold are parameters`() {
        val shifted = WindField.build(grid, referenceDegrees = 5.0)
        assertEquals(5.0, shifted.at(GridCell(0, 0)).shiftDegrees, 1e-9)
        val lenient = WindField.build(grid, referenceDegrees = 0.0, minSamples = 2)
        assertEquals(CellWind(GridCell(1, 1), 40.0, 2, true), lenient.at(GridCell(1, 1)))
        assertEquals(3, lenient.measuredCount)
    }

    @Test
    fun `without a measured cell the field is the reference wind everywhere`() {
        val field = WindField.build(TrackGrid.build(Track(samples(1, 1, 40.0, 2)), frame, spec, mark), referenceDegrees = 0.0)
        assertEquals(0, field.measuredCount)
        assertTrue(field.cells.all { it.shiftDegrees == 0.0 && !it.measured })
        assertEquals(2, field.at(GridCell(1, 1)).samples)
    }

    @Test
    fun `equality`() {
        val field = WindField.build(grid, referenceDegrees = 0.0)
        assertEquals(field, WindField.build(grid, referenceDegrees = 0.0))
        assertEquals(field.hashCode(), WindField.build(grid, referenceDegrees = 0.0).hashCode())
        assertNotEquals(field, WindField.build(grid, referenceDegrees = 5.0))
        assertNotEquals(field, WindField.build(grid, referenceDegrees = 0.0, minSamples = 2))
        assertTrue(!field.equals("field"))
    }
}
