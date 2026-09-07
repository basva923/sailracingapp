package com.sailracing.app.time

/** Source of "now" in epoch milliseconds. Abstracted so simulations and tests can control time. */
fun interface Clock {
    fun nowMillis(): Long
}

/** The wall clock. */
object SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/**
 * A clock that runs [speedFactor] times faster than [base], starting from [base]'s time at construction.
 * Used to run simulations faster than real time while keeping every timestamp consistent.
 */
class ScaledClock(private val base: Clock, private val speedFactor: Double) : Clock {
    private val startMillis = base.nowMillis()

    init {
        require(speedFactor > 0.0) { "speed factor must be positive: $speedFactor" }
    }

    override fun nowMillis(): Long = startMillis + ((base.nowMillis() - startMillis) * speedFactor).toLong()
}
