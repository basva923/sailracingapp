package com.sailracing.app.audio

import com.sailracing.domain.timer.Cue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BeepPatternTest {

    @Test
    fun everyCueHasADistinctPattern() {
        val patterns = Cue.entries.associateWith(BeepPattern::forCue)
        assertEquals(Cue.entries.size, patterns.values.toSet().size)
        assertTrue(patterns.getValue(Cue.MINUTE).totalMillis > patterns.getValue(Cue.SECOND).totalMillis)
        assertTrue(patterns.getValue(Cue.START).beeps.size > 1)
        assertEquals(600, patterns.getValue(Cue.MINUTE).totalMillis)
        assertEquals(4 * 180 + 900, patterns.getValue(Cue.START).totalMillis)
        SilentCuePlayer.play(Cue.START)
    }
}
