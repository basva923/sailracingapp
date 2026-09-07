package com.sailracing.domain.timer

/** State of the start countdown. */
public sealed interface TimerState {

    /** No start sequence in progress. */
    public data object Idle : TimerState

    /** A start sequence is running; before [startAtMillis] it counts down, afterwards it counts race time up. */
    public data class Running(val startAtMillis: Long) : TimerState
}

/** Coarse phase of the race, derived from the timer. */
public enum class RacePhase {
    /** Before any countdown: setting the line and the wind. */
    SETUP,

    /** Countdown running: pre-start manoeuvring. */
    COUNTDOWN,

    /** The start signal has passed. */
    RACING,
}
