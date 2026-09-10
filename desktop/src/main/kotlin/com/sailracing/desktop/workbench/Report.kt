package com.sailracing.desktop.workbench

import com.sailracing.domain.sketch.SketchAnalysis
import com.sailracing.domain.text.CourseText
import com.sailracing.domain.text.Formatters
import com.sailracing.domain.text.PlanText

/**
 * What the workbench has to say about a course, in the app's own words: every line of it comes out of
 * [CourseText] and [PlanText], which is what the phone reads from, so a sentence here is a sentence the
 * sailor would have seen on the water.
 *
 * @property headline the route, or what is still needed before there can be one.
 * @property lines the rest of the story, in the order it is worth reading.
 */
data class Report(val headline: String, val lines: List<String>) {

    companion object {
        /** What is shown before anything has been drawn or asked. */
        val NOTHING_YET: Report = Report(
            headline = "Draw a track and ask for the route",
            lines = listOf(
                "Lay a start line and a windward mark, set the wind, then drag a beat up the course.",
                "The route is planned from where the track stops, by the code that runs on the phone.",
            ),
        )

        fun of(analysis: SketchAnalysis?): Report {
            if (analysis == null) return NOTHING_YET
            val plan = analysis.plan
            val lines = mutableListOf(wind(analysis), track(analysis))
            if (plan.isEmpty) {
                lines += "A track of at least a few seconds is what a route is planned from."
                return Report("No route yet: the boat has not sailed", lines)
            }
            lines += CourseText.flyer(plan)
            lines += "${PlanText.sideTitle(analysis.upwind)} · ${PlanText.sideDetail(analysis.upwind)}"
            analysis.course?.let { course ->
                lines += CourseText.area(
                    course.spec.widthMeters,
                    course.spec.heightMeters,
                    course.spec.cellSizeMeters,
                    course.inputs.settings.grid.cellSizeMeters,
                )
                lines += mark(analysis, course.windwardMarkIsSet)
            }
            return Report(CourseText.raceLine(plan), lines)
        }

        /** Which wind the strategy judged by, and whether it is the one the sailor set or the one it measured. */
        private fun wind(analysis: SketchAnalysis): String {
            val reference = analysis.reference
            val set = Formatters.degrees(analysis.sketch.wind.directionDegrees)
            val source = if (reference.isMeasured) {
                "measured off the track, ${analysis.windSampleCount} samples; you set $set"
            } else {
                "as you set it: ${analysis.windSampleCount} of 30 samples so far"
            }
            return "Wind ${Formatters.degrees(reference.directionDegrees)} ($source) · tacking through " +
                "${analysis.sketch.wind.tackAngleDegrees * 2}°"
        }

        private fun track(analysis: SketchAnalysis): String {
            val seconds = analysis.trackPointCount
            if (seconds == 0) return "Nothing sailed yet"
            return "Track $seconds s (${Formatters.countdown(seconds * 1000L)}) · " +
                "${analysis.windSampleCount} of it close-hauled enough to say what the wind was"
        }

        private fun mark(analysis: SketchAnalysis, isSet: Boolean): String {
            val mark = analysis.sketch.windwardMark
            if (!isSet || mark == null) return "Mark: the top of the racing area, until you lay one"
            val from = analysis.sketch.pinEnd?.let { pin ->
                analysis.sketch.boatEnd?.let { boat -> pin.towards(boat, 0.5) }
            } ?: analysis.sketch.pinEnd ?: analysis.sketch.boatEnd
            if (from == null) return "Mark laid, without a start line to measure it from"
            return "Mark ${Formatters.meters(from.distanceTo(mark))} at " +
                "${Formatters.degrees(from.bearingTo(mark))} from the start line"
        }
    }
}
