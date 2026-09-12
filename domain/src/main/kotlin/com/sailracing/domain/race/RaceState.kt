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

/**
 * Everything known about the wind: the sailor's configuration plus the measured statistics.
 *
 * @property measuredTackAngleDegrees the tack angle last read off the boat's own two tacks, kept while only
 *   one of them is being sailed; null until both have been.
 */
public data class WindState(
    val settings: WindSettings = WindSettings(),
    val histogram: WindHistogram = WindHistogram(),
    val history: WindHistory = WindHistory(),
    val measuredTackAngleDegrees: Double? = null,
) {
    /**
     * The wind to judge everything against at [nowMillis]: the bisector of the headings held close-hauled
     * on the two tacks lately, one of them turned by the tack angle while the other has not been sailed,
     * and the set wind until either has - see [WindReference].
     */
    public fun reference(nowMillis: Long): WindReference = WindReference.of(settings, history, measuredTackAngleDegrees, nowMillis)
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
