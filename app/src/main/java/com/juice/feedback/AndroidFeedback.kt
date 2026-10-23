package com.juice.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.juice.core.audio.Cue
import com.juice.core.audio.Synth
import com.juice.core.audio.wav
import com.juice.ui.theme.SoundSet
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * SoundPool mixer over synthesized samples: each flavour's cues are rendered once into the cache
 * directory, then play with low latency and overlap. Rendering runs off the main thread.
 */
class SoundPoolOutput(context: Context) : SoundOutput {
    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = ConcurrentHashMap<Pair<SoundSet, Cue>, Int>()

    @Volatile private var soundSet = SoundSet.BRIGHT_CLICKS_AND_POPS

    init {
        val dir = File(context.cacheDir, "sounds").apply { mkdirs() }
        thread(name = "juice-synth", isDaemon = true) {
            // The current set first, so the first sounds after opening are ready soonest.
            (listOf(soundSet) + SoundSet.entries.filter { it != soundSet }).forEach { set ->
                Cue.entries.forEach { cue ->
                    val file = File(dir, "v$VERSION-${set.name}-${cue.name}.wav".lowercase())
                    if (!file.exists()) file.writeBytes(wav(Synth.render(cue, set.flavour)))
                    ids[set to cue] = pool.load(file.path, 1)
                }
            }
        }
    }

    override fun useSoundSet(soundSet: SoundSet) {
        this.soundSet = soundSet
    }

    override fun play(cue: Cue, rate: Float) {
        ids[soundSet to cue]?.let { pool.play(it, VOLUME, VOLUME, 1, 0, rate) }
    }

    fun release() = pool.release()

    private companion object {
        /** Bump when the recipes in [Synth] change, so old renders are not reused. */
        const val VERSION = 2
        const val VOLUME = 0.9f
    }
}

class VibratorOutput(context: Context) : HapticOutput {
    private val vibrator: Vibrator =
        context.getSystemService(VibratorManager::class.java).defaultVibrator

    override fun vibrate(haptic: Haptic) {
        val effect = when (haptic) {
            Haptic.SELECTION_TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            Haptic.MEDIUM_IMPACT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            Haptic.DOUBLE_LOW_BUZZ -> VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40), intArrayOf(0, 90, 0, 90), -1)
            Haptic.CONTINUOUS_LOW_RUMBLE -> VibrationEffect.createOneShot(400, 60)
            Haptic.REJECT -> VibrationEffect.createWaveform(longArrayOf(0, 10, 30, 10), intArrayOf(0, 120, 0, 120), -1)
        }
        vibrator.vibrate(effect)
    }
}
