package com.sailracing.domain.race

import com.sailracing.domain.race.TestFixtures.fix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeadingSelectorTest {

    private val settings = RaceSettings(courseMinSpeedMps = 1.0)

    @Test
    fun `course over ground wins while moving`() {
        val moving = fix(1_000, speedMps = 2.0, courseDegrees = 370.0)
        val navigation = HeadingSelector.select(moving, 200.0, settings)
        assertEquals(10.0, navigation.headingDegrees)
        assertEquals(HeadingSource.COURSE_OVER_GROUND, navigation.headingSource)
        assertEquals(200.0, navigation.compassHeadingDegrees)
        assertEquals(moving, navigation.lastFix)
    }

    @Test
    fun `compass is used when slow or without a course`() {
        val slow = fix(1_000, speedMps = 0.4, courseDegrees = 90.0)
        assertEquals(HeadingSource.COMPASS, HeadingSelector.select(slow, 200.0, settings).headingSource)
        assertEquals(200.0, HeadingSelector.select(slow, 200.0, settings).headingDegrees)

        val noCourse = fix(1_000, speedMps = 3.0, courseDegrees = null)
        assertEquals(HeadingSource.COMPASS, HeadingSelector.select(noCourse, 200.0, settings).headingSource)

        val noSpeed = fix(1_000, speedMps = null, courseDegrees = 90.0)
        assertEquals(HeadingSource.COMPASS, HeadingSelector.select(noSpeed, 200.0, settings).headingSource)

        assertEquals(HeadingSource.COMPASS, HeadingSelector.select(null, 200.0, settings).headingSource)
    }

    @Test
    fun `no heading without any source`() {
        val navigation = HeadingSelector.select(fix(1_000, speedMps = 0.1), null, settings)
        assertNull(navigation.headingDegrees)
        assertNull(navigation.headingSource)
        assertNull(navigation.compassHeadingDegrees)
        assertNull(HeadingSelector.select(null, null, settings).lastFix)
    }
}
