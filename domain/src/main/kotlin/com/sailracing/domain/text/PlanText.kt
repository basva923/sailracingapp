package com.sailracing.domain.text

import com.sailracing.domain.strategy.FavouredSide
import com.sailracing.domain.strategy.TackAdvice
import com.sailracing.domain.strategy.UpwindPlan
import com.sailracing.domain.wind.PointOfSail
import com.sailracing.domain.wind.Tack
import kotlin.math.abs
import kotlin.math.roundToInt

/** Words for the [UpwindPlan], shared by the wind and map screens and by the workbench. */
public object PlanText {

    public fun tackTitle(plan: UpwindPlan): String = when (plan.tackAdvice) {
        TackAdvice.HOLD -> "HOLD"
        TackAdvice.TACK -> if (plan.pointOfSail == PointOfSail.DOWNWIND) "GYBE" else "TACK"
        TackAdvice.EITHER -> if (plan.pointOfSail == PointOfSail.DOWNWIND) "EITHER GYBE" else "EITHER TACK"
        TackAdvice.UNKNOWN -> "—"
    }

    public fun tackDetail(plan: UpwindPlan): String {
        val reference = if (plan.referenceIsMeasured) "the mean wind" else "the set wind"
        val shift = plan.shiftFromReferenceDegrees?.let { "${abs(it).roundToInt()}°" }
        return when (plan.tackAdvice) {
            TackAdvice.HOLD -> "${tackName(plan.currentTack)} is lifted $shift from $reference"
            TackAdvice.TACK -> "Headed $shift from $reference: ${tackName(plan.favouredTack).lowercase()} is lifted"
            TackAdvice.EITHER -> "Wind within ${plan.oscillationDegrees?.let { "±${it.roundToInt()}°" } ?: "a few degrees"} of $reference"
            TackAdvice.UNKNOWN -> when {
                plan.currentTack == null -> "Waiting for a heading"
                else -> "Sail close-hauled or on the downwind angle for advice"
            }
        }
    }

    public fun sideTitle(plan: UpwindPlan): String = when (plan.favouredSide) {
        FavouredSide.LEFT -> "GO LEFT"
        FavouredSide.RIGHT -> "GO RIGHT"
        FavouredSide.EVEN -> "SIDES EVEN"
        FavouredSide.UNKNOWN -> "SIDES UNKNOWN"
    }

    public fun sideDetail(plan: UpwindPlan): String {
        val parts = mutableListOf<String>()
        plan.sides.windDifferenceDegrees?.let { parts += "right side wind ${Formatters.signedDegrees(it)}" }
        plan.sides.speedDifferenceMps?.let { parts += "speed ${Formatters.signedKnots(it)}" }
        plan.trendDegrees?.let { parts += "trend ${Formatters.signedDegrees(it)}" }
        if (parts.isEmpty()) {
            return "Sail upwind on both sides to compare them (${plan.sides.leftSamples} left, ${plan.sides.rightSamples} right samples)"
        }
        val verdict = when (plan.favouredSide) {
            FavouredSide.EVEN -> "play the shifts"
            FavouredSide.UNKNOWN -> "not enough yet"
            else -> "go towards the shift"
        }
        return parts.joinToString(", ").replaceFirstChar { it.uppercase() } + " · " + verdict
    }

    private fun tackName(tack: Tack?): String = when (tack) {
        Tack.STARBOARD -> "Starboard"
        Tack.PORT -> "Port"
        null -> "—"
    }
}
