package com.sailracing.domain.race

/**
 * Convenience wrapper that keeps the current [RaceState] and applies events through [RaceReducer].
 * Not thread-safe: callers must dispatch from a single thread or serialise access.
 */
public class RaceEngine(initialState: RaceState = RaceState()) {

    public var state: RaceState = initialState
        private set

    /** Applies [event] and returns the effects it produced. */
    public fun dispatch(event: RaceEvent): List<RaceEffect> {
        val transition = RaceReducer.reduce(state, event)
        state = transition.state
        return transition.effects
    }

    public fun snapshot(nowMillis: Long): RaceSnapshot = RaceCalculator.snapshot(state, nowMillis)
}
