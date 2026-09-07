package com.sailracing.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.sailracing.domain.timer.Cue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Beeps through the alarm stream (loud, independent of the media volume) and vibrates in the same pattern,
 * so cues are noticed on a noisy boat. The vibration is declared as an alarm too: as ordinary touch
 * feedback the system would scale it down with the haptics setting, or drop it altogether.
 */
class AndroidCuePlayer(
    context: Context,
    private val scope: CoroutineScope,
    private val vibrate: () -> Boolean = { true },
) : CuePlayer {

    private val toneGenerator: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, TONE_VOLUME) }.getOrNull()
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private var current: Job? = null

    override fun play(cue: Cue) {
        val pattern = BeepPattern.forCue(cue)
        current?.cancel()
        current = scope.launch {
            if (vibrate()) vibrate(pattern)
            for (beep in pattern.beeps) {
                val tone = if (beep.high) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
                toneGenerator?.startTone(tone, beep.durationMillis)
                delay((beep.durationMillis + beep.gapMillis).toLong())
            }
        }
    }

    private fun vibrate(pattern: BeepPattern) {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return
        val timings = mutableListOf(0L)
        for (beep in pattern.beeps) {
            timings += beep.durationMillis.toLong()
            timings += beep.gapMillis.toLong()
        }
        val effect = VibrationEffect.createWaveform(timings.toLongArray(), -1)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, ALARM_AUDIO_ATTRIBUTES)
            }
        }
    }

    fun release() {
        current?.cancel()
        toneGenerator?.release()
    }

    private companion object {
        const val TONE_VOLUME = 100

        /** The pre-Tiramisu way of asking for an alarm-class vibration. */
        val ALARM_AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
