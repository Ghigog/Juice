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

    private val dir = File(context.cacheDir, "sounds").apply { mkdirs() }
    private val greetingIds = ConcurrentHashMap<Pair<SoundSet, Int>, Int>()
    private val pendingGreetings = ConcurrentHashMap.newKeySet<Int>()

    @Volatile private var soundSet = SoundSet.BRIGHT_CLICKS_AND_POPS

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            if (status == 0 && pendingGreetings.remove(id)) pool.play(id, VOLUME, VOLUME, 1, 0, 1f)
        }
        thread(name = "juice-synth", isDaemon = true) {
            // The current set first, so the first sounds after opening are ready soonest.
            (listOf(soundSet) + SoundSet.entries.filter { it != soundSet }).forEach { set ->
                Cue.entries.filter { it != Cue.GREETING }.forEach { cue ->
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

    /** Greetings are rendered on first use (off the main thread), cached per seed, and play once loaded. */
    override fun playGreeting(seed: Int) {
        val key = soundSet to seed
        greetingIds[key]?.let { pool.play(it, VOLUME, VOLUME, 1, 0, 1f); return }
        thread(name = "juice-greeting", isDaemon = true) {
            val file = File(dir, "v$VERSION-${soundSet.name}-greeting-$seed.wav".lowercase())
            if (!file.exists()) file.writeBytes(wav(Synth.renderGreeting(seed, soundSet.flavour)))
            greetingIds.computeIfAbsent(key) { pool.load(file.path, 1).also { pendingGreetings += it } }
        }
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
