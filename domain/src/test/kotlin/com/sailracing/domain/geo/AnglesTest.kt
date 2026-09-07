package com.sailracing.domain.geo

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class AnglesTest {

    @Test
    fun `normalize maps doubles onto 0 until 360`() {
        assertEquals(0.0, Angles.normalize(0.0))
        assertEquals(0.0, Angles.normalize(360.0))
        assertEquals(1.0, Angles.normalize(361.0))
        assertEquals(0.0, Angles.normalize(720.0))
        assertEquals(359.0, Angles.normalize(-1.0))
        assertEquals(0.0, Angles.normalize(-360.0))
        assertEquals(359.0, Angles.normalize(-361.0))
        assertEquals(0.5, Angles.normalize(-359.5))
        assertEquals(0.0, Angles.normalize(-1e-15))
        assertEquals(0.0, Angles.normalize(-0.0))
    }

    @Test
    fun `normalize maps ints onto 0 until 360`() {
        assertEquals(0, Angles.normalize(0))
        assertEquals(0, Angles.normalize(360))
        assertEquals(1, Angles.normalize(361))
        assertEquals(359, Angles.normalize(-1))
        assertEquals(359, Angles.normalize(-361))
    }

    @Test
    fun `signed difference is the shortest rotation`() {
        assertEquals(0.0, Angles.signedDifference(0.0, 0.0))
        assertEquals(90.0, Angles.signedDifference(0.0, 90.0))
        assertEquals(-90.0, Angles.signedDifference(90.0, 0.0))
        assertEquals(180.0, Angles.signedDifference(0.0, 180.0))
        assertEquals(180.0, Angles.signedDifference(180.0, 0.0))
        assertEquals(-90.0, Angles.signedDifference(0.0, 270.0))
        assertEquals(90.0, Angles.signedDifference(270.0, 0.0))
        assertEquals(0.0, Angles.signedDifference(0.0, 360.0))
        assertEquals(90.0, Angles.signedDifference(0.0, 450.0))
        assertEquals(-90.0, Angles.signedDifference(450.0, 0.0))
        assertEquals(-120.0, Angles.signedDifference(30.0, 270.0))
        assertEquals(120.0, Angles.signedDifference(270.0, 30.0))
        assertEquals(-30.0, Angles.signedDifference(30.0, 720.0))
        assertEquals(30.0, Angles.signedDifference(720.0, 30.0))
        assertEquals(-179.0, Angles.signedDifference(0.0, 181.0))
    }

    @Test
    fun `radians and degrees convert both ways`() {
        assertEquals(PI / 2, Angles.toRadians(90.0), 1e-12)
        assertEquals(PI, Angles.toRadians(180.0), 1e-12)
        assertEquals(90.0, Angles.toDegrees(PI / 2), 1e-12)
        assertEquals(360.0, Angles.toDegrees(2 * PI), 1e-12)
    }
}
