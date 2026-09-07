package com.sailracing.domain.course

import com.sailracing.domain.geo.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackGridTest {

    private val frame = CourseFrame(GeoPoint(51.14, 5.83), windDirectionDegrees = 0.0)
    private val spec = GridSpec(leftMeters = -500.0, bottomMeters = -200.0, columns = 10, rows = 10, cellSizeMeters = 100.0)
    private val mark = CoursePosition(0.0, 800.0)

    private fun at(across: Double, upwind: Double, wind: Double? = null, speed: Double? = 3.0) =
        TrackPoint(0L, frame.toGeo(CoursePosition(across, upwind)), speedMps = speed, headingDegrees = 0.0, upwindWindDegrees = wind)

    @Test
    fun `spec validation and geometry`() {
        assertFailsWith<IllegalArgumentException> { GridSpec(0.0, 0.0, 0, 1) }
        assertFailsWith<IllegalArgumentException> { GridSpec(0.0, 0.0, 1, 0) }
        assertFailsWith<IllegalArgumentException> { GridSpec(0.0, 0.0, 1, 1, cellSizeMeters = 0.0) }
        assertEquals(1000.0, spec.widthMeters)
        assertEquals(1000.0, spec.heightMeters)
        assertEquals(500.0, spec.rightMeters)
        assertEquals(800.0, spec.topMeters)
        assertEquals(100, spec.cellCount)
        assertEquals(CoursePosition(0.0, 800.0), spec.topCenter)
        assertEquals(10.0, GridSpec(0.0, 0.0, 1, 1).cellSizeMeters)
    }

    @Test
    fun `the area follows what was sailed, snapped to the 10 m grid and aggregated when big`() {
        assertFailsWith<IllegalArgumentException> { GridSpec.covering(emptyList()) }
        // One point on a grid line still gets a cell.
        assertEquals(GridSpec(0.0, 0.0, 1, 1, 10.0), GridSpec.covering(listOf(CoursePosition(0.0, 0.0))))
        // Twelve 10 m cells fit; one metre more and the cells become 20 m.
        assertEquals(GridSpec(0.0, 0.0, 1, 12, 10.0), GridSpec.covering(listOf(CoursePosition(0.0, 0.0), CoursePosition(0.0, 120.0))))
        assertEquals(GridSpec(0.0, 0.0, 1, 7, 20.0), GridSpec.covering(listOf(CoursePosition(0.0, 0.0), CoursePosition(0.0, 121.0))))
        // A beat: 630 m tall needs 60 m cells, and the box is snapped outwards to whole cells.
        val beat = GridSpec.covering(listOf(CoursePosition(-55.0, -20.0), CoursePosition(130.0, 610.0)))
        assertEquals(GridSpec(-60.0, -60.0, 4, 12, 60.0), beat)
        assertEquals(CoursePosition(60.0, 660.0), beat.topCenter)
        assertEquals(GridSpec(0.0, 0.0, 1, 2, 60.0), GridSpec.covering(listOf(CoursePosition(0.0, 0.0), CoursePosition(0.0, 120.0)), maxCellsPerSide = 2))
        assertEquals(12, GridSpec.MAX_CELLS_PER_SIDE)
    }

    @Test
    fun `cells are addressed from the bottom left`() {
        val beat = GridSpec(-60.0, -60.0, 4, 12, 60.0)
        assertEquals(GridCell(1, 1), beat.cellOf(CoursePosition(0.0, 0.0)))
        assertEquals(GridCell(0, 0), beat.cellOf(CoursePosition(-60.0, -60.0)))
        assertEquals(GridCell(3, 11), beat.cellOf(CoursePosition(179.9, 659.9)))
        assertNull(beat.cellOf(CoursePosition(180.0, 0.0)))
        assertNull(beat.cellOf(CoursePosition(-60.1, 0.0)))
        assertNull(beat.cellOf(CoursePosition(0.0, 660.0)))
        assertNull(beat.cellOf(CoursePosition(0.0, -60.1)))
        assertEquals(GridCell(3, 0), beat.nearestCell(CoursePosition(500.0, -500.0)))
        assertEquals(GridCell(0, 11), beat.nearestCell(CoursePosition(-500.0, 5000.0)))
        assertEquals(GridCell(1, 1), beat.nearestCell(CoursePosition(0.0, 0.0)))
        assertEquals(CoursePosition(-30.0, -30.0), beat.center(GridCell(0, 0)))
        assertEquals(9, beat.index(GridCell(1, 2)))
        assertEquals(GridCell(1, 2), beat.cellAt(9))
    }

    @Test
    fun `sides split along the line from the start to the mark`() {
        assertEquals(Side.LEFT, spec.side(GridCell(4, 3), mark))
        assertEquals(Side.RIGHT, spec.side(GridCell(5, 3), mark))
        assertNull(GridSpec(-5.0, 0.0, 1, 1, 10.0).side(GridCell(0, 0), mark))
        // A mark off to the right tilts the split: a cell straight above the start is now on the left.
        assertEquals(Side.LEFT, spec.side(GridCell(5, 7), CoursePosition(300.0, 300.0)))
        assertEquals(-169.7, GridSpec.acrossAxis(CoursePosition(30.0, 270.0), CoursePosition(300.0, 300.0)), 0.1)
        assertEquals(30.0, GridSpec.acrossAxis(CoursePosition(30.0, 270.0), mark), 1e-9)
        // A mark that is not upwind of the start falls back to the upwind axis through the start.
        assertEquals(-7.0, GridSpec.acrossAxis(CoursePosition(-7.0, 3.0), CoursePosition(100.0, -50.0)))
        assertEquals(5.0, GridSpec.acrossAxis(CoursePosition(5.0, 0.0), CoursePosition(0.0, 0.0)))
    }

    @Test
    fun `cell statistics accumulate wind and speed`() {
        val empty = CellStats()
        assertNull(empty.meanWindDegrees)
        assertNull(empty.meanSpeedMps)
        val stats = CellStats(visits = 2, upwindSamples = 2, windCosSum = 2 * Math.cos(Math.toRadians(350.0)), windSinSum = 2 * Math.sin(Math.toRadians(350.0)), speedSum = 6.0)
        assertEquals(350.0, assertNotNull(stats.meanWindDegrees), 1e-9)
        assertEquals(3.0, assertNotNull(stats.meanSpeedMps))
        val sum = stats + CellStats(visits = 1)
        assertEquals(3, sum.visits)
        assertEquals(2, sum.upwindSamples)
    }

    @Test
    fun `the grid bins the track and aggregates the sides`() {
        val track = Track(
            listOf(
                at(-250.0, 100.0, wind = 355.0),
                at(-250.0, 100.0, wind = 355.0, speed = null),
                at(-250.0, 120.0),
                at(250.0, 100.0, wind = 10.0, speed = 4.0),
                at(250.0, 100.0, wind = 10.0, speed = 2.0),
                at(0.0, 5_000.0, wind = 90.0), // outside the area
            ),
        )
        val grid = TrackGrid.build(track, frame, spec, mark)
        assertEquals(5, grid.totalVisits)
        assertEquals(3, grid.maxVisits)
        assertEquals(mark, grid.axisTarget)
        val left = grid.stats(GridCell(2, 3))
        assertEquals(3, left.visits)
        assertEquals(2, left.upwindSamples)
        assertEquals(355.0, assertNotNull(left.meanWindDegrees), 1e-9)
        assertEquals(1.5, assertNotNull(left.meanSpeedMps))
        assertEquals(CellStats(), grid.stats(GridCell(0, 0)))
        assertEquals(listOf(GridCell(2, 3), GridCell(7, 3)), grid.visited.map { it.first })
        assertEquals(left, grid.side(Side.LEFT))
        val right = grid.side(Side.RIGHT)
        assertEquals(2, right.upwindSamples)
        assertEquals(10.0, assertNotNull(right.meanWindDegrees), 1e-9)
        assertEquals(3.0, assertNotNull(right.meanSpeedMps))
        assertEquals("TrackGrid(spec=$spec, visits=5)", grid.toString())

        val same = TrackGrid.build(track, frame, spec, mark)
        assertEquals(grid, same)
        assertEquals(grid.hashCode(), same.hashCode())
        assertNotEquals(grid, TrackGrid.build(Track(), frame, spec, mark))
        assertNotEquals(grid, TrackGrid.build(track, frame, spec.copy(bottomMeters = -300.0), mark))
        assertNotEquals(grid, TrackGrid.build(track, frame, spec, CoursePosition(10.0, 800.0)))
        assertTrue(!grid.equals("grid"))
        assertEquals(0, TrackGrid.build(Track(), frame, spec, mark).maxVisits)
    }
}
