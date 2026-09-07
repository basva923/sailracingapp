package com.sailracing.domain.strategy

import com.sailracing.domain.course.CourseFrame
import com.sailracing.domain.course.CoursePosition
import com.sailracing.domain.course.GridSpec
import com.sailracing.domain.course.Track
import com.sailracing.domain.course.TrackGrid
import com.sailracing.domain.course.TrackPoint
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.wind.PointOfSail
import com.sailracing.domain.wind.SailingState
import com.sailracing.domain.wind.Tack
import com.sailracing.domain.wind.WindHistogram
import com.sailracing.domain.wind.WindHistory
import com.sailracing.domain.wind.WindMath
import com.sailracing.domain.wind.WindReference
import com.sailracing.domain.wind.WindSample
import com.sailracing.domain.wind.WindSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpwindStrategyTest {

    private val settings = WindSettings(directionDegrees = 0)
    private val frame = CourseFrame(GeoPoint(51.14, 5.83), 0.0)

    /** A histogram of [count] samples centred on [centerDegrees], spread symmetrically by [spread]. */
    private fun histogram(centerDegrees: Double, count: Int, spread: Double = 5.0): WindHistogram {
        var histogram = WindHistogram()
        repeat(count) { i -> histogram += centerDegrees + spread * (if (i % 2 == 0) 1 else -1) }
        return histogram
    }

    private fun plan(
        heading: Double?,
        histogram: WindHistogram = histogram(0.0, 100),
        history: WindHistory = WindHistory(),
        grid: TrackGrid? = null,
        wind: WindSettings = settings,
    ): UpwindPlan {
        val reference = WindReference.of(wind, histogram)
        val sailing = heading?.let { WindMath.sailingState(it, reference.directionDegrees) }
        val estimated = heading?.let { WindMath.estimatedWindDirection(it, wind, reference.directionDegrees) }
        return UpwindStrategy.plan(reference, histogram, history, sailing, estimated, grid, 60, 120)
    }

    private fun grid(vararg points: TrackPoint): TrackGrid =
        TrackGrid.build(Track(points.toList()), frame, GridSpec(-500.0, 0.0, 10, 5, 100.0), CoursePosition(0.0, 500.0))

    private fun at(across: Double, wind: Double?, speed: Double = 3.0) =
        TrackPoint(0L, frame.toGeo(CoursePosition(across, 200.0)), speedMps = speed, headingDegrees = 0.0, upwindWindDegrees = wind)

    private fun side(across: Double, wind: Double, speed: Double = 3.0, count: Int = 20): Array<TrackPoint> =
        Array(count) { at(across, wind, speed) }

    @Test
    fun `without a heading nothing is known`() {
        val plan = plan(heading = null, histogram = WindHistogram())
        assertEquals(0.0, plan.referenceWindDegrees)
        assertFalse(plan.referenceIsMeasured)
        assertNull(plan.oscillationDegrees)
        assertNull(plan.shiftFromReferenceDegrees)
        assertNull(plan.pointOfSail)
        assertNull(plan.currentTack)
        assertNull(plan.favouredTack)
        assertEquals(TackAdvice.UNKNOWN, plan.tackAdvice)
        assertEquals(SideComparison(), plan.sides)
        assertNull(plan.trendDegrees)
        assertNull(plan.sideScoreDegrees)
        assertEquals(FavouredSide.UNKNOWN, plan.favouredSide)
    }

    @Test
    fun `the histogram centre is the reference once it has enough samples`() {
        val few = plan(heading = 315.0, histogram = histogram(10.0, WindReference.MIN_HISTOGRAM_SAMPLES - 1))
        assertEquals(0.0, few.referenceWindDegrees)
        assertFalse(few.referenceIsMeasured)
        assertEquals(0.0, assertNotNull(few.shiftFromReferenceDegrees), 1e-9)
        assertEquals(TackAdvice.EITHER, few.tackAdvice)

        val enough = plan(heading = 315.0, histogram = histogram(10.0, WindReference.MIN_HISTOGRAM_SAMPLES))
        assertEquals(10.0, enough.referenceWindDegrees, 1e-9)
        assertTrue(enough.referenceIsMeasured)
        assertEquals(5.0, assertNotNull(enough.oscillationDegrees), 0.2)
        // Sailing 315 on starboard means the wind is at 0: backed 10 degrees from the centre, so port is lifted.
        assertEquals(-10.0, assertNotNull(enough.shiftFromReferenceDegrees), 1e-9)
        assertEquals(PointOfSail.UPWIND, enough.pointOfSail)
        assertEquals(Tack.STARBOARD, enough.currentTack)
        assertEquals(Tack.PORT, enough.favouredTack)
        assertEquals(TackAdvice.TACK, enough.tackAdvice)
    }

    @Test
    fun `upwind the veered wind lifts starboard`() {
        val veered = histogram(350.0, 100)
        assertEquals(TackAdvice.HOLD, plan(heading = 315.0, histogram = veered).tackAdvice)
        assertEquals(Tack.STARBOARD, plan(heading = 315.0, histogram = veered).favouredTack)
        val onPort = plan(heading = 45.0, histogram = veered)
        assertEquals(Tack.PORT, onPort.currentTack)
        assertEquals(TackAdvice.TACK, onPort.tackAdvice)
        assertEquals(10.0, assertNotNull(onPort.shiftFromReferenceDegrees), 1e-9)
        assertEquals(TackAdvice.HOLD, plan(heading = 45.0, histogram = histogram(10.0, 100)).tackAdvice)
    }

    @Test
    fun `downwind the veered wind favours the port gybe`() {
        val veered = histogram(350.0, 100)
        val starboardRun = plan(heading = 220.0, histogram = veered)
        assertEquals(PointOfSail.DOWNWIND, starboardRun.pointOfSail)
        assertEquals(Tack.STARBOARD, starboardRun.currentTack)
        assertEquals(Tack.PORT, starboardRun.favouredTack)
        assertEquals(TackAdvice.TACK, starboardRun.tackAdvice)
        assertEquals(TackAdvice.HOLD, plan(heading = 140.0, histogram = veered).tackAdvice)
        assertEquals(TackAdvice.HOLD, plan(heading = 220.0, histogram = histogram(10.0, 100)).tackAdvice)
    }

    @Test
    fun `small shifts favour neither tack and off-angle sailing gives no advice`() {
        val slightlyVeered = histogram(358.0, 100)
        val plan = plan(heading = 315.0, histogram = slightlyVeered)
        assertEquals(TackAdvice.EITHER, plan.tackAdvice)
        assertNull(plan.favouredTack)
        assertEquals(2.0, assertNotNull(plan.shiftFromReferenceDegrees), 1e-9)
        // Reaching (70 off the wind) or running too high (100 off): the heading says nothing about the wind.
        assertEquals(TackAdvice.UNKNOWN, plan(heading = 290.0).tackAdvice)
        assertEquals(TackAdvice.UNKNOWN, plan(heading = 100.0).tackAdvice)
        assertNull(plan(heading = 100.0).shiftFromReferenceDegrees)
        assertEquals(PointOfSail.DOWNWIND, plan(heading = 100.0).pointOfSail)
    }

    @Test
    fun `sides need enough samples on both`() {
        val onlyLeft = plan(heading = 315.0, grid = grid(*side(-200.0, 5.0)))
        assertEquals(SideComparison(leftSamples = 20, rightSamples = 0), onlyLeft.sides)
        assertEquals(FavouredSide.UNKNOWN, onlyLeft.favouredSide)
        val tooFew = plan(heading = 315.0, grid = grid(*side(-200.0, 5.0), *side(200.0, 5.0, count = 19)))
        assertNull(tooFew.sides.windDifferenceDegrees)
        assertEquals(FavouredSide.UNKNOWN, tooFew.favouredSide)
    }

    @Test
    fun `go towards the side where the wind is shifted to`() {
        val rightVeered = plan(heading = 315.0, grid = grid(*side(-200.0, 355.0), *side(200.0, 5.0)))
        assertEquals(10.0, assertNotNull(rightVeered.sides.windDifferenceDegrees), 1e-9)
        assertEquals(0.0, assertNotNull(rightVeered.sides.speedDifferenceMps), 1e-9)
        assertEquals(10.0, assertNotNull(rightVeered.sideScoreDegrees), 1e-9)
        assertEquals(FavouredSide.RIGHT, rightVeered.favouredSide)

        val leftVeered = plan(heading = 315.0, grid = grid(*side(-200.0, 5.0), *side(200.0, 355.0)))
        assertEquals(FavouredSide.LEFT, leftVeered.favouredSide)

        val even = plan(heading = 315.0, grid = grid(*side(-200.0, 1.0), *side(200.0, 359.0)))
        assertEquals(FavouredSide.EVEN, even.favouredSide)
        assertEquals(-2.0, assertNotNull(even.sideScoreDegrees), 1e-9)
    }

    @Test
    fun `more speed on a side counts like a lift`() {
        // 10 % more speed on the right is worth 10 / 1.6 = 6.25 degrees.
        val faster = plan(heading = 315.0, grid = grid(*side(-200.0, 0.0, speed = 3.0), *side(200.0, 0.0, speed = 3.3157894)))
        assertEquals(0.0, assertNotNull(faster.sides.windDifferenceDegrees), 1e-9)
        assertEquals(0.3157894, assertNotNull(faster.sides.speedDifferenceMps), 1e-6)
        assertEquals(6.25, assertNotNull(faster.sideScoreDegrees), 0.01)
        assertEquals(FavouredSide.RIGHT, faster.favouredSide)
        // Drifting with no speed on either side is no evidence at all.
        val becalmed = plan(heading = 315.0, grid = grid(*side(-200.0, 0.0, speed = 0.0), *side(200.0, 0.0, speed = 0.0)))
        assertEquals(0.0, assertNotNull(becalmed.sideScoreDegrees), 1e-9)
        assertEquals(FavouredSide.EVEN, becalmed.favouredSide)
    }

    @Test
    fun `a building shift favours the side it shifts to`() {
        fun history(recentDegrees: Double, count: Int): WindHistory {
            var history = WindHistory(capacity = 1000)
            repeat(count) { i -> history += WindSample(i * 1000L, recentDegrees, upwind = true) }
            history += WindSample(999_000L, 90.0, upwind = false)
            return history
        }
        val veering = plan(heading = 315.0, history = history(6.0, UpwindStrategy.MIN_TREND_SAMPLES))
        assertEquals(6.0, assertNotNull(veering.trendDegrees), 1e-9)
        assertEquals(6.0, assertNotNull(veering.sideScoreDegrees), 1e-9)
        assertEquals(FavouredSide.RIGHT, veering.favouredSide)

        val backing = plan(heading = 315.0, history = history(354.0, UpwindStrategy.TREND_SAMPLES + 50))
        assertEquals(-6.0, assertNotNull(backing.trendDegrees), 1e-9)
        assertEquals(FavouredSide.LEFT, backing.favouredSide)

        assertNull(plan(heading = 315.0, history = history(6.0, UpwindStrategy.MIN_TREND_SAMPLES - 1)).trendDegrees)
        // Without a measured reference the trend means nothing.
        assertNull(plan(heading = 315.0, histogram = WindHistogram(), history = history(6.0, 400)).trendDegrees)

        // All the evidence adds up: a 10 degree backed right side against a 7 degree veering trend is a wash.
        val combined = plan(heading = 315.0, history = history(7.0, 400), grid = grid(*side(-200.0, 5.0), *side(200.0, 355.0)))
        assertEquals(-3.0, assertNotNull(combined.sideScoreDegrees), 1e-9)
        assertEquals(FavouredSide.EVEN, combined.favouredSide)
        val decisive = plan(heading = 315.0, history = history(6.0, 400), grid = grid(*side(-200.0, 8.0), *side(200.0, 352.0)))
        assertEquals(-10.0, assertNotNull(decisive.sideScoreDegrees), 1e-9)
        assertEquals(FavouredSide.LEFT, decisive.favouredSide)
    }
}
