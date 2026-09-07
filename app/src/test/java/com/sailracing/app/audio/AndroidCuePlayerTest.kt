package com.sailracing.app.audio

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sailracing.domain.timer.Cue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidCuePlayerTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun playsEveryCueWithAndWithoutVibration() = runTest {
        val vibrating = AndroidCuePlayer(app, this) { true }
        Cue.entries.forEach { vibrating.play(it) }
        advanceUntilIdle()
        vibrating.release()

        val silent = AndroidCuePlayer(app, this) { false }
        silent.play(Cue.START)
        // A new cue interrupts the previous one.
        silent.play(Cue.SECOND)
        advanceUntilIdle()
        silent.release()
    }
}
