package com.sailracing.domain.timer

import kotlin.math.roundToLong

/** Pure countdown arithmetic. Time is always passed in explicitly so behaviour is deterministic. */
public object CountdownTimer {

    public const val MILLIS_PER_MINUTE: Long = 60_000L

    /** Starts a sequence that reaches zero [minutes] minutes from [nowMillis]. */
    public fun start(nowMillis: Long, minutes: Int): TimerState.Running {
        require(minutes > 0) { "minutes must be positive: $minutes" }
        return TimerState.Running(nowMillis + minutes * MILLIS_PER_MINUTE)
    }

    /**
     * Rounds the remaining time to the nearest whole minute, for when the start button was pressed a little
     * late or early. Has no effect once the start has passed or while idle.
     */
    public fun syncToNearestMinute(state: TimerState, nowMillis: Long): TimerState {
        val running = state as? TimerState.Running ?: return state
        val remaining = running.startAtMillis - nowMillis
        if (remaining <= 0) return state
        val wholeMinutes = (remaining.toDouble() / MILLIS_PER_MINUTE).roundToLong()
        return TimerState.Running(nowMillis + wholeMinutes * MILLIS_PER_MINUTE)
    }

    /** Milliseconds until the start; negative after the start; null while idle. */
    public fun remainingMillis(state: TimerState, nowMillis: Long): Long? =
        (state as? TimerState.Running)?.let { it.startAtMillis - nowMillis }

    public fun phase(state: TimerState, nowMillis: Long): RacePhase {
        val remaining = remainingMillis(state, nowMillis) ?: return RacePhase.SETUP
        return if (remaining > 0) RacePhase.COUNTDOWN else RacePhase.RACING
    }

    /**
     * Whole seconds remaining, rounded up, so that the value N is first reported at the exact instant
     * remaining time drops to N.000 s. After the start this counts down through zero into negatives.
     */
    public fun remainingWholeSeconds(remainingMillis: Long): Long =
        if (remainingMillis >= 0) (remainingMillis + 999) / 1000 else -((-remainingMillis) / 1000)
}
