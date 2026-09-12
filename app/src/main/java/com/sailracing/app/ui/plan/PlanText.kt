package com.sailracing.app.ui.plan

import com.sailracing.app.ui.format.Formatters
import com.sailracing.domain.strategy.FavouredSide
import com.sailracing.domain.strategy.TackAdvice
import com.sailracing.domain.strategy.UpwindPlan
import com.sailracing.domain.wind.PointOfSail
import com.sailracing.domain.wind.Tack
import kotlin.math.abs
import kotlin.math.roundToInt

/** Words for the [UpwindPlan], shared by the wind and map screens. */
object PlanText {

    fun tackTitle(plan: UpwindPlan): String = when (plan.tackAdvice) {
        TackAdvice.HOLD -> "STAY"
        TackAdvice.TACK -> if (plan.pointOfSail == PointOfSail.DOWNWIND) "GYBE" else "TACK"
        TackAdvice.EITHER -> if (plan.pointOfSail == PointOfSail.DOWNWIND) "EITHER GYBE" else "EITHER TACK"
        TackAdvice.UNKNOWN -> "—"
    }

    fun tackDetail(plan: UpwindPlan): String {
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

    /**
     * The reason for the tack advice in a few words, for the cell it shares with the word itself:
     * "Stbd lifted 5°", "Headed 5° · port lifted", "At the mean ±6°".
     */
    fun tackGlance(plan: UpwindPlan): String {
        val shift = plan.shiftFromReferenceDegrees?.let { "${abs(it).roundToInt()}°" }
        return when (plan.tackAdvice) {
            TackAdvice.HOLD -> "${tackShort(plan.currentTack)} lifted $shift"
            TackAdvice.TACK -> "Headed $shift · ${tackShort(plan.favouredTack).lowercase()} lifted"
            TackAdvice.EITHER -> when {
                plan.oscillationDegrees != null -> "At the mean ±${plan.oscillationDegrees!!.roundToInt()}°"
                plan.referenceIsMeasured -> "At the mean wind"
                else -> "At the set wind"
            }
            TackAdvice.UNKNOWN -> if (plan.currentTack == null) "No heading" else "Sail close-hauled"
        }
    }

    fun sideTitle(plan: UpwindPlan): String = when (plan.favouredSide) {
        FavouredSide.LEFT -> "GO LEFT"
        FavouredSide.RIGHT -> "GO RIGHT"
        FavouredSide.EVEN -> "SIDES EVEN"
        FavouredSide.UNKNOWN -> "SIDES UNKNOWN"
    }

    fun sideDetail(plan: UpwindPlan): String {
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

    private fun tackShort(tack: Tack?): String = when (tack) {
        Tack.STARBOARD -> "Stbd"
        Tack.PORT -> "Port"
        null -> "—"
    }

    private fun tackName(tack: Tack?): String = when (tack) {
        Tack.STARBOARD -> "Starboard"
        Tack.PORT -> "Port"
        null -> "—"
    }
}
