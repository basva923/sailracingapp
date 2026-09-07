package com.sailracing.domain.timer

/** An audible/visual signal emitted during the countdown. */
public enum class Cue {
    /** A whole minute boundary (5:00, 4:00, ... 1:00). */
    MINUTE,

    /** A ten-second boundary inside the final minutes. */
    TEN_SECONDS,

    /** Each second of the final seconds. */
    SECOND,

    /** The start signal. */
    START,
}

/**
 * Which cues to give and when.
 *
 * @property tenSecondWindowSeconds cues every ten seconds once the remaining time is at or below this.
 * @property secondWindowSeconds cues every second once the remaining time is at or below this.
 */
public data class CuePolicy(
    val enabled: Boolean = true,
    val tenSecondWindowSeconds: Int = 120,
    val secondWindowSeconds: Int = 10,
)

public object CueSchedule {

    /** The cue to give when the countdown shows exactly [remainingSeconds], or null for silence. */
    public fun cueAt(remainingSeconds: Long, policy: CuePolicy): Cue? {
        if (!policy.enabled || remainingSeconds < 0) return null
        return when {
            remainingSeconds == 0L -> Cue.START
            remainingSeconds % 60 == 0L -> Cue.MINUTE
            remainingSeconds <= policy.tenSecondWindowSeconds && remainingSeconds % 10 == 0L -> Cue.TEN_SECONDS
            remainingSeconds <= policy.secondWindowSeconds -> Cue.SECOND
            else -> null
        }
    }
}
