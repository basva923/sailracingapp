package com.sailracing.domain.course

import com.sailracing.domain.geo.GeoPoint
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Every square of the racing area gets a wind, whether it was sailed through or not: its own samples, the
 * samples around it and the wind measured over the whole course, each weighed by how much it is worth.
 * These are the three, one at a time.
 */
class WindFieldTest {

    private val frame = CourseFrame(GeoPoint(51.14, 5.83), windDirectionDegrees = 0.0)
    private val settings = WindFieldSettings()

    /** A racing area big enough that its far corner is out of earshot of everything measured in the near one. */
    private val big = GridSpec(0.0, 0.0, 6, 6, 100.0)
    private val small = GridSpec(0.0, 0.0, 3, 3, 10.0)

    private fun samples(spec: GridSpec, cell: GridCell, windDegrees: Double, count: Int, speedMps: Double = 3.0): List<TrackPoint> {
        val place = frame.toGeo(spec.center(cell))
        return List(count) { TrackPoint(it * 1000L, place, speedMps = speedMps, headingDegrees = 0.0, upwindWindDegrees = windDegrees) }
    }

    private fun field(spec: GridSpec, points: List<TrackPoint>, settings: WindFieldSettings = this.settings): WindField =
        WindField.build(TrackGrid.build(Track(points), frame, spec, spec.topCenter), referenceDegrees = 0.0, settings = settings)

    @Test
    fun `a square that was sailed through keeps the wind measured in it`() {
        val field = field(big, samples(big, GridCell(0, 0), 12.0, 50) + samples(big, GridCell(5, 5), 348.0, 50, speedMps = 4.0))
        val sailed = field.at(GridCell(0, 0))
        assertEquals(12.0, sailed.shiftDegrees, 0.2, "fifty samples of one wind should carry their own square")
        assertEquals(3.0, sailed.speedMps, 0.05)
        assertEquals(50, sailed.samples)
        assertTrue(sailed.measured)
        assertEquals(0.0, sailed.measuredDistanceMeters)
        assertTrue(sailed.confidence > 0.99, "a square measured fifty times is not a guess: ${sailed.confidence}")
        // Steady samples cannot make a square a certainty: the wind is never known better than the floor.
        assertEquals(settings.minSpreadDegrees, sailed.spreadDegrees, 0.3)
        assertEquals(-12.0, field.at(GridCell(5, 5)).shiftDegrees, 0.2)
        assertEquals(4.0, field.at(GridCell(5, 5)).speedMps, 0.05)
        assertEquals(2, field.measuredCount)
        assertEquals(36, field.cells.size)
        assertEquals(big, field.spec)
        assertEquals(settings, field.settings)
        assertEquals("WindField(spec=$big, measured=2)", field.toString())
    }

    @Test
    fun `a square nobody sailed through borrows from the ones around it, less the further away they are`() {
        // Two corners measured, veered and backed by the same amount: the day's wind is straight up, so
        // whatever a square leans one way or the other is what it borrowed from the corner nearest it.
        val field = field(big, samples(big, GridCell(0, 0), 20.0, 30) + samples(big, GridCell(5, 5), 340.0, 30))
        val near = field.at(GridCell(1, 0))
        val further = field.at(GridCell(2, 0))
        val far = field.at(GridCell(5, 0))
        assertFalse(near.measured)
        assertEquals(0, near.samples)
        assertEquals(100.0, near.measuredDistanceMeters, 1e-6)
        assertTrue(near.shiftDegrees > further.shiftDegrees, "the nearer square should follow the measured one more")
        assertTrue(further.shiftDegrees > far.shiftDegrees)
        assertTrue(near.shiftDegrees in 5.0..20.0, "a square next door is neither the measurement nor nothing: ${near.shiftDegrees}")
        // Out of earshot of both the borrowing stops: what is left is the wind over the whole course.
        assertEquals(0.0, field.at(GridCell(5, 0)).shiftDegrees, 3.0)
        assertEquals(0.0, field.at(GridCell(5, 0)).confidence, 0.05)
        assertTrue(near.confidence > further.confidence && further.confidence > far.confidence)
        // And what a square does not know shows up as how far its wind may be off.
        assertTrue(near.spreadDegrees < further.spreadDegrees, "doubt should grow with the distance from what was measured")
        assertTrue(further.spreadDegrees < far.spreadDegrees)
        assertTrue(far.spreadDegrees <= settings.maxSpreadDegrees)
    }

    @Test
    fun `a square out of reach of everything measured is left with the wind over the whole course`() {
        // Two squares in one corner, one veered 20 and one 40: the day's wind is 30, wandering by 10.
        val field = field(big, samples(big, GridCell(0, 0), 20.0, 3) + samples(big, GridCell(1, 0), 40.0, 3))
        val corner = field.at(GridCell(5, 5))
        assertEquals(30.0, corner.shiftDegrees, 0.1, "the far corner should be the day's wind, not the nearest square's")
        assertEquals(0.0, corner.confidence, 1e-9, "the far corner knows nothing of its own")
        assertEquals(big.center(GridCell(1, 0)).let { sqrt(400.0 * 400.0 + 500.0 * 500.0) }, corner.measuredDistanceMeters, 1.0)
        // Its doubt is the day's wandering, plus how far a square can differ from the day's mean.
        val wander = 10.0
        assertEquals(
            sqrt(wander * wander + settings.spatialSpreadDegrees * settings.spatialSpreadDegrees + wander * wander / 6.0),
            corner.spreadDegrees,
            0.6,
        )
    }

    @Test
    fun `the samples in a square are not all it goes by`() {
        // Three samples at 10 degrees, with a square 14 m away that measured 40: the wind here is pulled up.
        val field = field(small, samples(small, GridCell(0, 0), 10.0, 3) + samples(small, GridCell(1, 1), 40.0, 3))
        val sailed = field.at(GridCell(0, 0))
        assertTrue(sailed.measured && sailed.shiftDegrees > 10.0 && sailed.shiftDegrees < 25.0, "${sailed.shiftDegrees}")
        // Alone in its own area, the same three samples are the whole story.
        val alone = field(small, samples(small, GridCell(0, 0), 10.0, 3)).at(GridCell(0, 0))
        assertEquals(10.0, alone.shiftDegrees, 1e-6)
    }

    @Test
    fun `the reference is what the shifts are measured from`() {
        val points = samples(big, GridCell(0, 0), 20.0, 30)
        val grid = TrackGrid.build(Track(points), frame, big, big.topCenter)
        assertEquals(15.0, WindField.build(grid, referenceDegrees = 5.0).at(GridCell(0, 0)).shiftDegrees, 0.2)
        assertEquals(-10.0, WindField.build(grid, referenceDegrees = 30.0).at(GridCell(0, 0)).shiftDegrees, 0.2)
    }

    @Test
    fun `how many samples make a square measured is a setting, and it changes nothing else`() {
        val points = samples(small, GridCell(1, 1), 40.0, 2)
        val strict = field(small, points)
        val lenient = field(small, points, settings.copy(minCellSamples = 2))
        assertFalse(strict.at(GridCell(1, 1)).measured)
        assertTrue(lenient.at(GridCell(1, 1)).measured)
        assertEquals(0, strict.measuredCount)
        assertEquals(1, lenient.measuredCount)
        assertEquals(strict.at(GridCell(1, 1)).shiftDegrees, lenient.at(GridCell(1, 1)).shiftDegrees, 1e-9)
        assertEquals(2, strict.at(GridCell(1, 1)).samples)
        // Only what counts as measured moves, so only the distance to a measured square changes with it.
        assertEquals(Double.POSITIVE_INFINITY, strict.at(GridCell(0, 0)).measuredDistanceMeters)
        assertTrue(lenient.at(GridCell(0, 0)).measuredDistanceMeters < 20.0)
    }

    @Test
    fun `without a single sample the field is the reference wind everywhere`() {
        val field = field(small, emptyList())
        assertEquals(0, field.measuredCount)
        assertTrue(field.cells.all { it.shiftDegrees == 0.0 && !it.measured && it.confidence == 0.0 })
        assertTrue(field.cells.all { it.speedMps == SailingConditions.DEFAULT_BOAT_SPEED_MPS && it.speeds.isEmpty })
        // Nothing is known, so the doubt is everything the wind is believed to do, by day and by place.
        val doubt = settings.sampleSpreadDegrees
        assertEquals(sqrt(2.0 * doubt * doubt + settings.spatialSpreadDegrees * settings.spatialSpreadDegrees), field.spreadDegrees(GridCell(1, 1)), 1e-9)
    }

    @Test
    fun `the speeds are blended the same way, as a mixture of what was measured`() {
        // A quick corner and a slow one, and nothing sailed in between.
        val field = field(
            big,
            samples(big, GridCell(0, 0), 0.0, 30, speedMps = 5.0) + samples(big, GridCell(5, 5), 0.0, 30, speedMps = 2.0),
        )
        val between = field.at(GridCell(3, 3))
        assertTrue(between.speedMps in 2.0..5.0, "a square between a quick corner and a slow one: ${between.speedMps}")
        assertEquals(field.speedMps(GridCell(3, 3)), between.speedMps)
        // It is a mixture, not an average: both the quick and the slow speed can come out of it.
        assertTrue(between.speeds.weightAt(5.0) > 0.0 && between.speeds.weightAt(2.0) > 0.0)
        assertEquals(5.0, field.speedAt(big.center(GridCell(0, 0))), 0.1)
        assertEquals(0.0, field.shiftAt(CoursePosition(-100.0, -100.0)), 1e-9)
    }

    @Test
    fun `equality`() {
        val points = samples(small, GridCell(0, 0), 10.0, 3)
        val grid = TrackGrid.build(Track(points), frame, small, small.topCenter)
        val field = WindField.build(grid, referenceDegrees = 0.0)
        assertEquals(field, WindField.build(grid, referenceDegrees = 0.0))
        assertEquals(field.hashCode(), WindField.build(grid, referenceDegrees = 0.0).hashCode())
        assertNotEquals(field, WindField.build(grid, referenceDegrees = 5.0))
        assertNotEquals(field, WindField.build(grid, referenceDegrees = 0.0, settings = settings.copy(minCellSamples = 2)))
        assertNotEquals(field, WindField.build(grid, referenceDegrees = 0.0, settings = settings.copy(spatialSpreadDegrees = 20.0)))
        assertTrue(!field.equals("field"))
    }
}
