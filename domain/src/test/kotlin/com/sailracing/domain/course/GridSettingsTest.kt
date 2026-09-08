package com.sailracing.domain.course

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The one thing about the racing area the sailor chooses: how big its squares are. */
class GridSettingsTest {

    @Test
    fun `by default the area chooses its own squares`() {
        val settings = GridSettings()
        assertNull(settings.cellSizeMeters)
        assertEquals(GridSpec.MAX_CELLS_PER_SIDE, settings.maxCellsPerSide)
        assertEquals(5.0..200.0, GridSettings.CELL_SIZE_RANGE_METERS)
    }

    @Test
    fun `nonsense settings are refused`() {
        fun message(block: () -> GridSettings): String =
            assertFailsWith<IllegalArgumentException> { block() }.message.orEmpty()

        assertEquals("a square is at least 5.0 m: 4.0", message { GridSettings(cellSizeMeters = 4.0) })
        assertEquals("an area holds 1..20 squares along a side: 0", message { GridSettings(maxCellsPerSide = 0) })
        assertEquals("an area holds 1..20 squares along a side: 21", message { GridSettings(maxCellsPerSide = 21) })
    }
}
