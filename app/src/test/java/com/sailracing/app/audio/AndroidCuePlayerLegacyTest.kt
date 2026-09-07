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
import org.robolectric.annotation.Config

/** Before Android 12 the vibrator comes from the legacy system service. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class AndroidCuePlayerLegacyTest {

    @Test
    fun vibratesThroughTheLegacyService() = runTest {
        val app: Application = ApplicationProvider.getApplicationContext()
        val player = AndroidCuePlayer(app, this) { true }
        player.play(Cue.MINUTE)
        advanceUntilIdle()
        player.release()
    }
}
