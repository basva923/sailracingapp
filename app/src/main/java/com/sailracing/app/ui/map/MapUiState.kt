package com.sailracing.app.ui.map

import com.sailracing.app.ui.format.Formatters
import com.sailracing.app.ui.plan.PlanText
import com.sailracing.domain.course.CoursePosition
import com.sailracing.domain.course.RaceLine
import com.sailracing.domain.course.RaceLinePlan
import com.sailracing.domain.geo.Geo
import com.sailracing.domain.race.RaceSnapshot
import com.sailracing.domain.strategy.FavouredSide
import com.sailracing.domain.wind.Tack
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One big square's wind arrow as drawn, in course-frame metres. The wind is worked out in squares far
 * bigger than the ones the area is cut into - three across the course - because that is where there are
 * enough samples to be worth calling a wind; see `WindField`.
 *
 * @property shiftDegrees the mean of the winds measured inside the square, relative to the reference wind
 *   (the frame's up): positive = veered. Where nothing was measured it is the mean of everything measured
 *   on the course, which is what the race line's simulations draw that square from.
 * @property measured true when enough close-hauled samples were taken inside the square itself.
 * @property confidence how much of the day's evidence this square holds, 1 once it holds its even share of
 *   it and 0 where nobody has sailed: how solid the arrow is drawn.
 */
data class MapArrow(
    val acrossMeters: Double,
    val upwindMeters: Double,
    val sizeMeters: Double,
    val shiftDegrees: Double,
    val measured: Boolean,
    val confidence: Double = 0.0,
)

/** The racing area: the rectangle of cells that follows the track, in course-frame metres. */
data class MapArea(val leftMeters: Double, val bottomMeters: Double, val widthMeters: Double, val heightMeters: Double, val cellMeters: Double) {
    val rightMeters: Double get() = leftMeters + widthMeters
    val topMeters: Double get() = bottomMeters + heightMeters
}

/**
 * The piece of water the map fits into the room it is given, in course-frame metres: the racing area with
 * a margin around it, never so small that a boat drifting on the start line fills the screen. It keeps the
 * shape of the area, so a tall beat is drawn tall; the map scales it to the screen and letterboxes the rest.
 */
data class MapView(
    val centerAcrossMeters: Double,
    val centerUpwindMeters: Double,
    val widthMeters: Double,
    val heightMeters: Double,
)

/** Everything the map draws, in course-frame metres (wind up, origin at the start line), plus the advice text. */
data class MapUiState(
    val area: MapArea = EMPTY_AREA,
    /** The big squares the wind is worked out in, laid over the same water; null before there is a course. */
    val windArea: MapArea? = null,
    val view: MapView = view(EMPTY_AREA),
    val arrows: List<MapArrow> = emptyList(),
    val track: List<CoursePosition> = emptyList(),
    /** The race line to sail: the boat, every square boundary it crosses on its way up, and the mark. */
    val raceLine: List<CoursePosition> = emptyList(),
    /** The line that pays most when the wind is kind, drawn only when it is not the line to sail. */
    val riskyLine: List<CoursePosition> = emptyList(),
    /** What the race line costs: how many tacks, how long it takes, and how bad its bad day is. */
    val raceLineText: String = "",
    /** What the gamble is worth: the flyer and how often it beats the line to sail. Empty without a line. */
    val riskText: String = "",
    /** The race line's cost in a few words, drawn over the map: "2 tacks · 04:37 to the mark". */
    val raceLineGlance: String = "",
    val pinEnd: CoursePosition? = null,
    val boatEnd: CoursePosition? = null,
    val boat: CoursePosition? = null,
    val mark: CoursePosition? = null,
    val markIsSet: Boolean = false,
    /** Frame-relative headings: 0 = straight upwind, 90 = to the right. */
    val boatHeadingDegrees: Double? = null,
    val northDegrees: Double = 0.0,
    val starboardHeadingDegrees: Double = 315.0,
    val portHeadingDegrees: Double = 45.0,
    val favouredTack: Tack? = null,
    val favouredSide: FavouredSide = FavouredSide.UNKNOWN,
    val sideTitle: String = "SIDES UNKNOWN",
    val sideDetail: String = "",
    val scaleText: String = "",
    val markText: String = "Mark: top of the area until you set it",
    val trackText: String = "No track yet",
    val canMarkHere: Boolean = false,
    val canSetMarkFromLine: Boolean = false,
) {
    companion object {
        /** What the map shows before there is a course to draw: a patch of water the size of a start line. */
        val EMPTY_AREA: MapArea = MapArea(-50.0, -50.0, 100.0, 100.0, 10.0)

        /**
         * The map never shows less water than this along the longer side of the racing area: before there
         * is a track the area is a couple of squares around the boat, and a map of that would be a
         * meaningless close-up of the GPS wobbling.
         */
        const val MIN_VIEW_METERS = 100.0

        /** The room left around the racing area, so that its edge and a boat on it are both drawn. */
        const val VIEW_MARGIN = 1.15

        fun from(snapshot: RaceSnapshot): MapUiState {
            val course = snapshot.course
            val plan = snapshot.plan
            val duration = snapshot.trackPointCount.takeIf { it > 0 }?.let { count -> " · ${count / 60} min" } ?: ""
            val common = MapUiState(
                favouredTack = plan.favouredTack,
                favouredSide = plan.favouredSide,
                sideTitle = PlanText.sideTitle(plan),
                sideDetail = PlanText.sideDetail(plan),
                trackText = if (snapshot.trackPointCount == 0) "No track yet" else "${snapshot.trackPointCount} points$duration",
                canMarkHere = snapshot.position != null,
            )
            if (course == null) return common
            val frame = course.frame
            val spec = course.spec
            val area = MapArea(spec.leftMeters, spec.bottomMeters, spec.widthMeters, spec.heightMeters, spec.cellSizeMeters)
            val setMark = course.inputs.windwardMark
            val markText = if (setMark != null) {
                "Mark set ${Formatters.meters(Geo.distanceMeters(frame.origin, setMark))} at ${Formatters.degrees(Geo.initialBearingDegrees(frame.origin, setMark))} from the line"
            } else {
                "Mark: top of the area until you set it"
            }
            val blocks = course.windField.spec
            val day = course.windField.courseShiftDegrees ?: 0.0
            return common.copy(
                area = area,
                windArea = MapArea(blocks.leftMeters, blocks.bottomMeters, blocks.widthMeters, blocks.heightMeters, blocks.cellSizeMeters),
                view = view(area),
                arrows = course.windField.blocks.map { block ->
                    val centre = blocks.center(block.cell)
                    MapArrow(
                        acrossMeters = centre.acrossMeters,
                        upwindMeters = centre.upwindMeters,
                        sizeMeters = blocks.cellSizeMeters,
                        shiftDegrees = block.meanShiftDegrees ?: day,
                        measured = block.measured,
                        // An even share of the day's samples is as sure as a square gets: what the sailor
                        // wants to see is which squares were actually sailed, not a fraction of a ninth.
                        confidence = (block.share * blocks.cellCount).coerceAtMost(1.0),
                    )
                },
                track = course.trackPositions,
                raceLine = course.racePlan.safe.points,
                riskyLine = if (course.racePlan.agree) emptyList() else course.racePlan.fast.points,
                raceLineText = raceLineText(course.racePlan),
                riskText = riskText(course.racePlan),
                raceLineGlance = raceLineGlance(course.racePlan),
                pinEnd = course.pinEnd,
                boatEnd = course.boatEnd,
                boat = course.boat,
                mark = course.windwardMark,
                markIsSet = course.windwardMarkIsSet,
                boatHeadingDegrees = snapshot.headingDegrees?.let(frame::toCourseBearing),
                northDegrees = frame.toCourseBearing(0.0),
                starboardHeadingDegrees = frame.toCourseBearing(snapshot.targetHeadings.starboardUpwind),
                portHeadingDegrees = frame.toCourseBearing(snapshot.targetHeadings.portUpwind),
                scaleText = scaleText(area, course.inputs.settings.grid.cellSizeMeters, blocks.cellSizeMeters),
                markText = markText,
                canSetMarkFromLine = true,
            )
        }

        /**
         * "Area 240 m × 600 m, wind up · 50 m squares · wind in 80 m blocks": how big the racing area is,
         * how finely it is cut up - saying so when the size the sailor chose had to be enlarged to keep the
         * area searchable - and how big the squares are that the wind itself is worked out in.
         */
        fun scaleText(area: MapArea, chosenCellMeters: Double?, windCellMeters: Double? = null): String {
            val squares = if (chosenCellMeters != null && chosenCellMeters < area.cellMeters) {
                "${Formatters.meters(area.cellMeters)} squares, enlarged from ${Formatters.meters(chosenCellMeters)} to fit"
            } else {
                "${Formatters.meters(area.cellMeters)} squares"
            }
            val wind = windCellMeters?.let { " · wind in ${Formatters.meters(it)} blocks" } ?: ""
            return "Area ${Formatters.meters(area.widthMeters)} × ${Formatters.meters(area.heightMeters)}, wind up · $squares$wind"
        }

        /** "Race line: 2 tacks · 04:37 to the mark, 05:02 on a bad day": what the drawn line costs. */
        fun raceLineText(plan: RaceLinePlan): String {
            if (plan.isEmpty) return ""
            val line = plan.safe
            return "Race line: ${tacks(line)} · ${time(line.seconds)} to the mark, ${time(plan.safeRisk.badSeconds)} on a bad day"
        }

        /** "2 tacks · 04:37 to the mark": the same, short enough to sit on the map itself. */
        fun raceLineGlance(plan: RaceLinePlan): String =
            if (plan.isEmpty) "" else "${tacks(plan.safe)} · ${time(plan.safe.seconds)} to the mark"

        /**
         * What the flyer is worth: the line that pays most when the wind is kind, and how often it did over
         * the simulated winds - or that there is no gamble to take, when the safe line is the fast one too.
         */
        fun riskText(plan: RaceLinePlan): String {
            if (plan.isEmpty) return ""
            if (plan.agree) return "No flyer: the same line is the fastest of the ${plan.runs} winds simulated"
            val wins = (plan.winFraction * plan.runs).roundToInt()
            return "Flyer: ${tacks(plan.fast)} · ${time(plan.fastRisk.meanSeconds)}, " +
                "${time(plan.fastRisk.goodSeconds)} at best · beats the race line in $wins of ${plan.runs} winds"
        }

        private fun tacks(line: RaceLine): String = when (line.tacks) {
            0 -> "no tack"
            1 -> "1 tack"
            else -> "${line.tacks} tacks"
        }

        private fun time(seconds: Double): String = Formatters.countdown((seconds * 1000).toLong())

        /**
         * The area with a margin around it, and nothing else: the map scales that to the room it has, so
         * whatever it is given it is filled in the direction the area is longest. A tiny area is blown up
         * to [MIN_VIEW_METERS] - the whole of it, both sides at once, because stretching one side alone
         * would pad the map with water it did not need to show and shrink everything drawn on it.
         */
        fun view(area: MapArea): MapView {
            val width = area.widthMeters * VIEW_MARGIN
            val height = area.heightMeters * VIEW_MARGIN
            val blownUp = max(1.0, MIN_VIEW_METERS / max(width, height))
            return MapView(
                centerAcrossMeters = area.leftMeters + area.widthMeters / 2,
                centerUpwindMeters = area.bottomMeters + area.heightMeters / 2,
                widthMeters = width * blownUp,
                heightMeters = height * blownUp,
            )
        }
    }
}
