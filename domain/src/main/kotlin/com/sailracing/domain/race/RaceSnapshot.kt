package com.sailracing.domain.race

import com.sailracing.domain.course.CourseModel
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.startline.StartLineSolution
import com.sailracing.domain.stats.SpeedStats
import com.sailracing.domain.strategy.UpwindPlan
import com.sailracing.domain.timer.RacePhase
import com.sailracing.domain.wind.SailingState
import com.sailracing.domain.wind.TargetHeadings
import com.sailracing.domain.wind.WindHistogram
import com.sailracing.domain.wind.WindHistory
import com.sailracing.domain.wind.WindReference
import com.sailracing.domain.wind.WindSettings

/**
 * Everything the sailor sees, derived from a [RaceState] at one instant by [RaceCalculator].
 * Speeds in m/s, distances in metres, angles in degrees, times in seconds unless the name says otherwise.
 */
public data class RaceSnapshot(
    val nowMillis: Long,
    val phase: RacePhase,
    /** Milliseconds until the start; negative once racing; null while idle. */
    val remainingMillis: Long?,
    val remainingWholeSeconds: Long?,

    val startLine: StartLine,
    val startLineLengthMeters: Double?,
    val line: StartLineSolution?,
    val approachSpeedMps: Double,
    /** True when [approachSpeedMps] comes from measured upwind VMG rather than a fixed value. */
    val approachSpeedIsMeasured: Boolean,
    val timeToLineSeconds: Double?,
    val timeToKillSeconds: Double?,

    val position: GeoPoint?,
    val fixAgeMillis: Long?,
    val fixIsFresh: Boolean,
    val accuracyMeters: Double?,
    val speedMps: Double?,
    val headingDegrees: Double?,
    val headingSource: HeadingSource?,

    val windSettings: WindSettings,
    /** The wind everything is judged against: the histogram's centre once measured, the set wind until then. */
    val windReference: WindReference,
    val sailing: SailingState?,
    /** The wind the boat's heading implies this instant, at the tack (or downwind) angle. */
    val estimatedWindDegrees: Double?,
    /** The estimated wind relative to the reference: positive = veered. */
    val shiftDegrees: Double?,
    /**
     * The wind of the moment: the last ten seconds of samples on this tack, so that one wave does not
     * move it; null off the angle, where a heading says nothing about the wind. It is what the shift
     * shown and the tack advice are judged from.
     */
    val steadyWindDegrees: Double?,
    /** [steadyWindDegrees] relative to the reference: positive = veered. */
    val steadyShiftDegrees: Double?,
    /** The close-hauled and downwind headings around the reference wind. */
    val targetHeadings: TargetHeadings,
    val targetHeadingDegrees: Double?,
    val headingErrorDegrees: Double?,
    val vmgMps: Double?,

    val speedStats: SpeedStats,
    val histogram: WindHistogram,
    val history: WindHistory,

    /** The course as the map shows it; null until there is a position to centre its frame on. */
    val course: CourseModel?,
    val trackPointCount: Int,
    val plan: UpwindPlan,
)
