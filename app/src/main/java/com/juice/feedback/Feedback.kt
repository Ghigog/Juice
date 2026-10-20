package com.juice.feedback

/** Moments that produce audio and haptic feedback (design doc §9). */
enum class FeedbackEvent { SNAP, CHECKMARK, DISMISS, SATIATED_PEAK }

/** What the UI calls. */
interface Feedback {
    fun play(event: FeedbackEvent)
}

enum class Sound(val playbackRate: Float = 1f) {
    SNAP, SQUEEZE, THUD,
    /** Fizz sample pitched +4 semitones. */
    FIZZ(playbackRate = 1.2599f),
}

enum class Haptic { SELECTION_TICK, MEDIUM_IMPACT, DOUBLE_LOW_BUZZ, CONTINUOUS_LOW_RUMBLE }

/** The comfort-setting switches. */
data class FeedbackSettings(val soundEnabled: Boolean = true, val hapticsEnabled: Boolean = true)

interface SoundOutput { fun play(sound: Sound) }

interface HapticOutput { fun vibrate(haptic: Haptic) }

/** Maps events to sound and haptic effects, honouring the switches. */
class FeedbackPlayer(
    private val sounds: SoundOutput,
    private val haptics: HapticOutput,
    private val settings: () -> FeedbackSettings,
) : Feedback {
    override fun play(event: FeedbackEvent) {
        val (sound, haptic) = when (event) {
            FeedbackEvent.SNAP -> Sound.SNAP to Haptic.SELECTION_TICK
            FeedbackEvent.CHECKMARK -> Sound.SQUEEZE to Haptic.MEDIUM_IMPACT
            FeedbackEvent.DISMISS -> Sound.THUD to Haptic.DOUBLE_LOW_BUZZ
            FeedbackEvent.SATIATED_PEAK -> Sound.FIZZ to Haptic.CONTINUOUS_LOW_RUMBLE
        }
        val current = settings()
        if (current.soundEnabled) sounds.play(sound)
        if (current.hapticsEnabled) haptics.vibrate(haptic)
    }
}
