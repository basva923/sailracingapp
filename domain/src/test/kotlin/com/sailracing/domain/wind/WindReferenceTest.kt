package com.sailracing.domain.wind

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindReferenceTest {

    private val settings = WindSettings(directionDegrees = 20, tackAngleDegrees = 45)

    /** [count] close-hauled samples on [tack] at [heading], one a second from [fromSeconds]. */
    private fun WindHistory.beating(tack: Tack, heading: Double, count: Int, fromSeconds: Long = 0L, upwind: Boolean = true): WindHistory {
        var history = this
        val estimate = if (tack == Tack.STARBOARD) heading + 45 else heading - 45
        repeat(count) { i ->
            history += WindSample((fromSeconds + i) * 1_000L, (estimate + 360) % 360, upwind, headingDegrees = heading, tack = tack)
        }
        return history
    }

    @Test
    fun `the set wind is the reference until enough has been measured close-hauled`() {
        val none = WindReference.of(settings, WindHistory(), null, nowMillis = 100_000L)
        assertEquals(WindReference(20.0, isMeasured = false, tackAngleDegrees = 45.0), none)
        assertNull(none.measuredTackAngleDegrees)
        // Twenty-nine samples are not a wind yet; the thirtieth makes one.
        val few = WindHistory().beating(Tack.STARBOARD, 340.0, WindReference.MIN_SAMPLES - 1)
        assertFalse(WindReference.of(settings, few, null, 100_000L).isMeasured)
        val enough = WindHistory().beating(Tack.STARBOARD, 340.0, WindReference.MIN_SAMPLES)
        val starboard = WindReference.of(settings, enough, null, 100_000L)
        assertEquals(25.0, starboard.directionDegrees, 1e-9)
        assertTrue(starboard.isMeasured)
        assertEquals(45.0, starboard.tackAngleDegrees)
        assertNull(starboard.measuredTackAngleDegrees)
        // Samples that were not close-hauled do not count, nor do those from before the window.
        val reaching = WindHistory().beating(Tack.STARBOARD, 300.0, 30, upwind = false)
        assertFalse(WindReference.of(settings, reaching, null, 100_000L).isMeasured)
        assertTrue(WindReference.of(settings, enough, null, WindReference.WINDOW_MILLIS).isMeasured)
        assertFalse(WindReference.of(settings, enough, null, 1_000L + WindReference.WINDOW_MILLIS).isMeasured)
    }

    @Test
    fun `the reference is the median of the histogram, which a reach cannot pull far`() {
        // Twenty samples at 25, ten at 35 and one at 90 that got counted on a reach: the median sits at 25.
        var history = WindHistory().beating(Tack.STARBOARD, 340.0, 20).beating(Tack.STARBOARD, 350.0, 10, fromSeconds = 20)
        history += WindSample(31_000L, 90.0, upwind = true, headingDegrees = 45.0, tack = Tack.STARBOARD)
        assertEquals(25.0, WindReference.of(settings, history, null, 100_000L).directionDegrees, 1e-9)
        // An even count averages the two middle samples: fifteen at 25 and fifteen at 35 give 30.
        val even = WindHistory().beating(Tack.STARBOARD, 340.0, 15).beating(Tack.STARBOARD, 350.0, 15, fromSeconds = 15)
        assertEquals(30.0, WindReference.of(settings, even, null, 100_000L).directionDegrees, 1e-9)
        // And a set straddling north is still one set.
        val north = WindHistory().beating(Tack.STARBOARD, 310.0, 15).beating(Tack.PORT, 50.0, 16, fromSeconds = 15)
        assertEquals(5.0, WindReference.of(settings, north, null, 100_000L).directionDegrees, 1e-9)
    }

    @Test
    fun `both tacks give the tack angle the boat really sails`() {
        // Starboard at 312 and port at 48: the boat tacks through 96. The estimates keep to the set 45,
        // so they sit at 357 and 3 and the reference between them.
        val both = WindHistory().beating(Tack.STARBOARD, 312.0, 30).beating(Tack.PORT, 48.0, 30, fromSeconds = 100)
        val reference = WindReference.of(settings, both, null, 200_000L)
        assertEquals(0.0, reference.directionDegrees, 1e-9)
        assertTrue(reference.isMeasured)
        assertEquals(45.0, reference.tackAngleDegrees, 1e-9)
        assertEquals(48.0, reference.measuredTackAngleDegrees!!, 1e-9)
        // A pair straddling north works the same.
        val north = WindHistory().beating(Tack.STARBOARD, 320.0, 30).beating(Tack.PORT, 40.0, 30, fromSeconds = 100)
        assertEquals(40.0, WindReference.of(settings, north, null, 200_000L).measuredTackAngleDegrees!!, 1e-9)
        // One tack alone measures nothing, and hands on what was measured before.
        val starboard = WindHistory().beating(Tack.STARBOARD, 312.0, 30)
        assertNull(WindReference.of(settings, starboard, null, 100_000L).measuredTackAngleDegrees)
        assertEquals(48.0, WindReference.of(settings, starboard, 48.0, 100_000L).measuredTackAngleDegrees)
        assertEquals(48.0, WindReference.of(settings, WindHistory(), 48.0, 100_000L).measuredTackAngleDegrees)
    }

    @Test
    fun `a pair that is not two tack angles apart measures no angle`() {
        // Starboard close-hauled at 315 and port on a reach at 80 that got counted: 125 apart is no tack
        // angle of 45. The last measurement is handed on; the reference is still the median of everything.
        val reach = WindHistory().beating(Tack.STARBOARD, 315.0, 30).beating(Tack.PORT, 80.0, 30, fromSeconds = 100)
        val reference = WindReference.of(settings, reach, 47.0, 200_000L)
        assertEquals(47.0, reference.measuredTackAngleDegrees)
        assertNull(WindReference.of(settings, reach, null, 200_000L).measuredTackAngleDegrees)
        assertEquals(17.5, reference.directionDegrees, 1e-9)
        assertTrue(reference.isMeasured)
    }
}
