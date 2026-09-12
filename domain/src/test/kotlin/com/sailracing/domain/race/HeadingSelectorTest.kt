package com.sailracing.domain.race

import com.sailracing.domain.race.TestFixtures.fix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeadingSelectorTest {

    private val gps = RaceSettings(courseMinSpeedMps = 1.0)
    private val compass = gps.copy(headingSource = HeadingSource.COMPASS)

    @Test
    fun `the gps course is used while the boat moves`() {
        val moving = fix(1_000, speedMps = 2.0, courseDegrees = 370.0)
        val navigation = HeadingSelector.select(moving, 200.0, gps)
        assertEquals(10.0, navigation.headingDegrees)
        assertEquals(HeadingSource.COURSE_OVER_GROUND, navigation.headingSource)
        assertEquals(200.0, navigation.compassHeadingDegrees)
        assertEquals(moving, navigation.lastFix)
    }

    /** A phone that need not be fixed to anything cannot say which way a stopped boat points. */
    @Test
    fun `the gps course does not fall back to the compass`() {
        val slow = fix(1_000, speedMps = 0.4, courseDegrees = 90.0)
        val navigation = HeadingSelector.select(slow, 200.0, gps)
        assertNull(navigation.headingDegrees)
        assertNull(navigation.headingSource)
        // The reading is still kept, for the moment the sailor switches to the compass.
        assertEquals(200.0, navigation.compassHeadingDegrees)

        assertNull(HeadingSelector.select(fix(1_000, speedMps = 3.0, courseDegrees = null), 200.0, gps).headingDegrees)
        assertNull(HeadingSelector.select(fix(1_000, speedMps = null, courseDegrees = 90.0), 200.0, gps).headingDegrees)
        assertNull(HeadingSelector.select(null, 200.0, gps).headingDegrees)
    }

    @Test
    fun `the compass wins at any speed once it has a reading`() {
        val moving = fix(1_000, speedMps = 2.0, courseDegrees = 90.0)
        assertEquals(HeadingSource.COMPASS, HeadingSelector.select(moving, 200.0, compass).headingSource)
        assertEquals(200.0, HeadingSelector.select(moving, 200.0, compass).headingDegrees)

        val stopped = fix(1_000, speedMps = 0.1, courseDegrees = 90.0)
        assertEquals(200.0, HeadingSelector.select(stopped, 200.0, compass).headingDegrees)
        assertEquals(200.0, HeadingSelector.select(null, 200.0, compass).headingDegrees)
    }

    /** Without a rotation sensor, or in a simulated race, the course fills in until a reading arrives. */
    @Test
    fun `the compass falls back to the gps course`() {
        val moving = fix(1_000, speedMps = 2.0, courseDegrees = 370.0)
        val navigation = HeadingSelector.select(moving, null, compass)
        assertEquals(10.0, navigation.headingDegrees)
        assertEquals(HeadingSource.COURSE_OVER_GROUND, navigation.headingSource)
    }

    @Test
    fun `no heading without any source`() {
        val navigation = HeadingSelector.select(fix(1_000, speedMps = 0.1), null, compass)
        assertNull(navigation.headingDegrees)
        assertNull(navigation.headingSource)
        assertNull(navigation.compassHeadingDegrees)
        assertNull(HeadingSelector.select(null, null, gps).lastFix)
    }
}
