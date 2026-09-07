package com.sailracing.app.ui.map

import com.sailracing.app.ui.format.Formatters
import com.sailracing.app.ui.plan.PlanText
import com.sailracing.domain.course.CoursePosition
import com.sailracing.domain.geo.Geo
import com.sailracing.domain.race.RaceSnapshot
import com.sailracing.domain.strategy.FavouredSide
import com.sailracing.domain.strategy.TackAdvice
import com.sailracing.domain.wind.Tack
import kotlin.math.max

/**
 * One cell's wind arrow as drawn.
 *
 * @property shiftDegrees the wind in the cell relative to the reference wind (the frame's up): positive = veered.
 * @property measured true when measured in the cell itself, false when estimated from the neighbours.
 */
data class MapArrow(val column: Int, val row: Int, val shiftDegrees: Double, val measured: Boolean)

/** The racing area: the rectangle of cells that follows the track, in course-frame metres. */
data class MapArea(val leftMeters: Double, val bottomMeters: Double, val widthMeters: Double, val heightMeters: Double, val cellMeters: Double) {
    val rightMeters: Double get() = leftMeters + widthMeters
    val topMeters: Double get() = bottomMeters + heightMeters
}

/** The square of the course frame the map shows, in metres: the racing area with a margin, never tiny. */
data class MapView(val centerAcrossMeters: Double, val centerUpwindMeters: Double, val sideMeters: Double)

/** Everything the map draws, in course-frame metres (wind up, origin at the start line), plus the advice text. */
data class MapUiState(
    val hasFrame: Boolean = false,
    val area: MapArea = MapArea(-50.0, -50.0, 100.0, 100.0, 10.0),
    val view: MapView = MapView(0.0, 0.0, MIN_VIEW_METERS),
    val arrows: List<MapArrow> = emptyList(),
    val track: List<CoursePosition> = emptyList(),
    /** The fastest line from the boat to the mark through the measured wind. */
    val route: List<CoursePosition> = emptyList(),
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
    val advice: TackAdvice = TackAdvice.UNKNOWN,
    val tackTitle: String = "—",
    val tackDetail: String = "",
    val scaleText: String = "",
    val markText: String = "Mark: top of the area until you set it",
    val trackText: String = "No track yet",
    val canMarkHere: Boolean = false,
    val canSetMarkFromLine: Boolean = false,
) {
    companion object {
        /** The view never shows less than this, and leaves this much room around the area. */
        const val MIN_VIEW_METERS = 200.0
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
                advice = plan.tackAdvice,
                tackTitle = PlanText.tackTitle(plan),
                tackDetail = PlanText.tackDetail(plan),
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
            return common.copy(
                hasFrame = true,
                area = area,
                view = view(area),
                arrows = course.windField.cells.map { MapArrow(it.cell.column, it.cell.row, it.shiftDegrees, it.measured) },
                track = course.trackPositions,
                route = course.route,
                pinEnd = course.pinEnd,
                boatEnd = course.boatEnd,
                boat = course.boat,
                mark = course.windwardMark,
                markIsSet = course.windwardMarkIsSet,
                boatHeadingDegrees = snapshot.headingDegrees?.let(frame::toCourseBearing),
                northDegrees = frame.toCourseBearing(0.0),
                starboardHeadingDegrees = frame.toCourseBearing(snapshot.targetHeadings.starboardUpwind),
                portHeadingDegrees = frame.toCourseBearing(snapshot.targetHeadings.portUpwind),
                scaleText = "Area ${Formatters.meters(area.widthMeters)} × ${Formatters.meters(area.heightMeters)}, wind up · ${Formatters.meters(area.cellMeters)} cells",
                markText = markText,
                canSetMarkFromLine = true,
            )
        }

        /** The square around the area with a margin, at least [MIN_VIEW_METERS] across. */
        fun view(area: MapArea): MapView = MapView(
            centerAcrossMeters = area.leftMeters + area.widthMeters / 2,
            centerUpwindMeters = area.bottomMeters + area.heightMeters / 2,
            sideMeters = max(MIN_VIEW_METERS, max(area.widthMeters, area.heightMeters) * VIEW_MARGIN),
        )
    }
}
