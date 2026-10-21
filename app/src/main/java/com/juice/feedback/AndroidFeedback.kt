package com.juice.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.juice.R
import com.juice.ui.theme.SoundSet

/** SoundPool mixer: samples load once, then play with low latency and overlap. */
class SoundPoolOutput(context: Context) : SoundOutput {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = Sound.entries.associateWith { pool.load(context, resourceOf(it), 1) }

    private var soundSet = SoundSet.BRIGHT_CLICKS_AND_POPS

    override fun useSoundSet(soundSet: SoundSet) {
        this.soundSet = soundSet
    }

    /** The samples are shared; each set is the same bank pitched to its own character. */
    override fun play(sound: Sound) {
        ids[sound]?.let { pool.play(it, 1f, 1f, 1, 0, sound.playbackRate * pitchOf(soundSet)) }
    }

    private fun pitchOf(set: SoundSet) = when (set) {
        SoundSet.BRIGHT_CLICKS_AND_POPS -> 1f
        SoundSet.SUB_BASS_AND_FIZZ -> 0.8f
        SoundSet.METALLIC_CHIMES -> 1.25f
        SoundSet.BELLS_AND_SPLASHES -> 1.5f
    }

    fun release() = pool.release()

    private fun resourceOf(sound: Sound) = when (sound) {
        Sound.SNAP -> R.raw.snap
        Sound.SQUEEZE -> R.raw.squeeze
        Sound.THUD -> R.raw.thud
        Sound.FIZZ -> R.raw.fizz
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
        }
        vibrator.vibrate(effect)
    }
}
