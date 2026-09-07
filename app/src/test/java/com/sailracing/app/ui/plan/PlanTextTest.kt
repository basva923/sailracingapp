package com.sailracing.app.ui.plan

import com.sailracing.domain.strategy.FavouredSide
import com.sailracing.domain.strategy.SideComparison
import com.sailracing.domain.strategy.TackAdvice
import com.sailracing.domain.strategy.UpwindPlan
import com.sailracing.domain.wind.PointOfSail
import com.sailracing.domain.wind.Tack
import org.junit.Test
import kotlin.test.assertEquals

class PlanTextTest {

    private val base = UpwindPlan(referenceWindDegrees = 20.0, referenceIsMeasured = true, oscillationDegrees = 6.4)

    @Test
    fun tackTitles() {
        assertEquals("HOLD", PlanText.tackTitle(base.copy(tackAdvice = TackAdvice.HOLD)))
        assertEquals("TACK", PlanText.tackTitle(base.copy(tackAdvice = TackAdvice.TACK, pointOfSail = PointOfSail.UPWIND)))
        assertEquals("GYBE", PlanText.tackTitle(base.copy(tackAdvice = TackAdvice.TACK, pointOfSail = PointOfSail.DOWNWIND)))
        assertEquals("EITHER TACK", PlanText.tackTitle(base.copy(tackAdvice = TackAdvice.EITHER, pointOfSail = PointOfSail.UPWIND)))
        assertEquals("EITHER GYBE", PlanText.tackTitle(base.copy(tackAdvice = TackAdvice.EITHER, pointOfSail = PointOfSail.DOWNWIND)))
        assertEquals("—", PlanText.tackTitle(base))
    }

    @Test
    fun tackDetails() {
        val hold = base.copy(tackAdvice = TackAdvice.HOLD, currentTack = Tack.STARBOARD, favouredTack = Tack.STARBOARD, shiftFromReferenceDegrees = 6.4)
        assertEquals("Starboard is lifted 6° from the mean wind", PlanText.tackDetail(hold))
        val tack = base.copy(tackAdvice = TackAdvice.TACK, currentTack = Tack.STARBOARD, favouredTack = Tack.PORT, shiftFromReferenceDegrees = -5.0, referenceIsMeasured = false)
        assertEquals("Headed 5° from the set wind: port is lifted", PlanText.tackDetail(tack))
        assertEquals("Wind within ±6° of the mean wind", PlanText.tackDetail(base.copy(tackAdvice = TackAdvice.EITHER)))
        assertEquals("Wind within a few degrees of the set wind", PlanText.tackDetail(base.copy(tackAdvice = TackAdvice.EITHER, referenceIsMeasured = false, oscillationDegrees = null)))
        assertEquals("Waiting for a heading", PlanText.tackDetail(base))
        assertEquals("Sail close-hauled or on the downwind angle for advice", PlanText.tackDetail(base.copy(currentTack = Tack.PORT)))
        assertEquals("— is lifted 3° from the mean wind", PlanText.tackDetail(hold.copy(currentTack = null, shiftFromReferenceDegrees = 3.0)))
    }

    @Test
    fun sideTitlesAndDetails() {
        assertEquals("GO LEFT", PlanText.sideTitle(base.copy(favouredSide = FavouredSide.LEFT)))
        assertEquals("GO RIGHT", PlanText.sideTitle(base.copy(favouredSide = FavouredSide.RIGHT)))
        assertEquals("SIDES EVEN", PlanText.sideTitle(base.copy(favouredSide = FavouredSide.EVEN)))
        assertEquals("SIDES UNKNOWN", PlanText.sideTitle(base))

        assertEquals(
            "Sail upwind on both sides to compare them (12 left, 0 right samples)",
            PlanText.sideDetail(base.copy(sides = SideComparison(leftSamples = 12))),
        )
        val full = base.copy(
            sides = SideComparison(40, 50, windDifferenceDegrees = 4.2, speedDifferenceMps = 0.15),
            trendDegrees = -1.2,
            favouredSide = FavouredSide.RIGHT,
        )
        assertEquals("Right side wind +4°, speed +0.3 kn, trend -1° · go towards the shift", PlanText.sideDetail(full))
        assertEquals("Trend +2° · play the shifts", PlanText.sideDetail(base.copy(trendDegrees = 2.0, favouredSide = FavouredSide.EVEN)))
        assertEquals("Trend +2° · not enough yet", PlanText.sideDetail(base.copy(trendDegrees = 2.0)))
    }
}
