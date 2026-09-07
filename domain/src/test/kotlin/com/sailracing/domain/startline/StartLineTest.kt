package com.sailracing.domain.startline

import com.sailracing.domain.geo.Geo
import com.sailracing.domain.geo.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StartLineTest {

    private val pin = GeoPoint(51.14, 5.83)
    private val boat = Geo.destination(pin, 90.0, 100.0)

    @Test
    fun `incomplete line has no length`() {
        assertFalse(StartLine().isComplete)
        assertNull(StartLine().lengthMeters())
        assertFalse(StartLine(pinEnd = pin).isComplete)
        assertNull(StartLine(pinEnd = pin).lengthMeters())
        assertFalse(StartLine(boatEnd = boat).isComplete)
        assertNull(StartLine(boatEnd = boat).lengthMeters())
    }

    @Test
    fun `complete line reports its length`() {
        val line = StartLine(pin, boat)
        assertTrue(line.isComplete)
        assertEquals(100.0, assertNotNull(line.lengthMeters()), 0.01)
    }
}
