package com.sailracing.domain.race

import com.sailracing.domain.course.Track
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.stats.SpeedStats
import com.sailracing.domain.timer.TimerState
import com.sailracing.domain.wind.WindHistogram
import com.sailracing.domain.wind.WindHistory
import com.sailracing.domain.wind.WindReference
import com.sailracing.domain.wind.WindSettings

/** Everything known about the wind: the sailor's configuration plus the measured statistics. */
public data class WindState(
    val settings: WindSettings = WindSettings(),
    val histogram: WindHistogram = WindHistogram(),
    val history: WindHistory = WindHistory(),
) {
    /** The wind to judge everything against: the histogram's centre once measured, the set wind until then. */
    public val reference: WindReference get() = WindReference.of(settings, histogram)
}

/**
 * The complete, immutable state of a race session. It is only ever changed by [RaceReducer],
 * which makes every behaviour replayable and testable without any Android dependency.
 *
 * @property lastCuedSecond the countdown second for which a cue was last emitted, to emit each cue once.
 * @property windwardMark the upwind mark when the sailor has set one; otherwise the top of the racing area is assumed.
 * @property track where the boat has been, one point per fix second.
 * @property lastSampleSecond the fix second of the last track point (and wind/speed sample), limiting sampling to 1 Hz.
 */
public data class RaceState(
    val startLine: StartLine = StartLine(),
    val windwardMark: GeoPoint? = null,
    val wind: WindState = WindState(),
    val timer: TimerState = TimerState.Idle,
    val navigation: NavigationState = NavigationState(),
    val speedStats: SpeedStats = SpeedStats(),
    val settings: RaceSettings = RaceSettings(),
    val track: Track = Track(),
    val lastCuedSecond: Long? = null,
    val lastSampleSecond: Long? = null,
)
