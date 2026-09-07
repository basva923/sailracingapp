package com.sailracing.app.audio

import com.sailracing.domain.timer.Cue

/** Plays the audible/haptic signal for a countdown cue. */
interface CuePlayer {
    fun play(cue: Cue)
}

/** A player that does nothing; used before audio is set up and in tests. */
object SilentCuePlayer : CuePlayer {
    override fun play(cue: Cue) = Unit
}

/**
 * The tone pattern for each cue, as (durationMillis, tone) pairs played back to back.
 * Kept separate from Android so the mapping is testable.
 */
data class BeepPattern(val beeps: List<Beep>) {
    data class Beep(val durationMillis: Int, val gapMillis: Int, val high: Boolean)

    val totalMillis: Int get() = beeps.sumOf { it.durationMillis + it.gapMillis }

    companion object {
        fun forCue(cue: Cue): BeepPattern = when (cue) {
            Cue.MINUTE -> BeepPattern(listOf(Beep(600, 0, high = false)))
            Cue.TEN_SECONDS -> BeepPattern(listOf(Beep(150, 0, high = true)))
            Cue.SECOND -> BeepPattern(listOf(Beep(80, 0, high = true)))
            Cue.START -> BeepPattern(List(4) { Beep(120, 60, high = true) } + Beep(900, 0, high = false))
        }
    }
}
