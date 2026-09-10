package com.sailracing.domain.text

import com.sailracing.domain.course.RaceLine
import com.sailracing.domain.course.RaceLinePlan
import kotlin.math.roundToInt

/** Words for the racing area and the lines up it, shared by the map on the phone and the workbench. */
public object CourseText {

    /** "Race line: 2 tacks · 04:37 to the mark, 05:02 on a bad day": what the drawn line costs. */
    public fun raceLine(plan: RaceLinePlan): String {
        if (plan.isEmpty) return ""
        val line = plan.safe
        return "Race line: ${tacks(line)} · ${time(line.seconds)} to the mark, ${time(plan.safeRisk.badSeconds)} on a bad day"
    }

    /**
     * What the flyer is worth: the line that pays most when the wind is kind, and how often it did over
     * the simulated winds - or that there is no gamble to take, when the safe line is the fast one too.
     */
    public fun flyer(plan: RaceLinePlan): String {
        if (plan.isEmpty) return ""
        if (plan.agree) return "No flyer: the same line is the fastest of the ${plan.runs} winds simulated"
        val wins = (plan.winFraction * plan.runs).roundToInt()
        return "Flyer: ${tacks(plan.fast)} · ${time(plan.fastRisk.meanSeconds)}, " +
            "${time(plan.fastRisk.goodSeconds)} at best · beats the race line in $wins of ${plan.runs} winds"
    }

    /**
     * "Area 240 m × 600 m, wind up · 50 m squares": how big the racing area is and how finely it is cut
     * up, saying so when the size the sailor chose had to be enlarged to keep the area searchable.
     */
    public fun area(widthMeters: Double, heightMeters: Double, cellMeters: Double, chosenCellMeters: Double?): String {
        val squares = if (chosenCellMeters != null && chosenCellMeters < cellMeters) {
            "${Formatters.meters(cellMeters)} squares, enlarged from ${Formatters.meters(chosenCellMeters)} to fit"
        } else {
            "${Formatters.meters(cellMeters)} squares"
        }
        return "Area ${Formatters.meters(widthMeters)} × ${Formatters.meters(heightMeters)}, wind up · $squares"
    }

    private fun tacks(line: RaceLine): String = when (line.tacks) {
        0 -> "no tack"
        1 -> "1 tack"
        else -> "${line.tacks} tacks"
    }

    private fun time(seconds: Double): String = Formatters.countdown((seconds * 1000).toLong())
}
