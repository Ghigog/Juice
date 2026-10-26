package com.juice.feedback

import com.juice.core.audio.Cue
import com.juice.ui.theme.SoundSet

/** Moments that produce audio and haptic feedback (design doc §9, plus the overlay's smaller moments). */
enum class FeedbackEvent { SNAP, CHECKMARK, DISMISS, SATIATED_PEAK, CAPTURE, NOT_RECOGNISED, UNDO, OPEN, LEVEL_UP, RECAP_OPEN, RECAP_REVEAL, CARD_POP }

/** What the UI calls. [boosted] raises the pitch, used while the meter is Satiated. */
interface Feedback {
    fun play(event: FeedbackEvent, boosted: Boolean = false)

    /** Switches to the sounds of the player's flavour. */
    fun useSoundSet(soundSet: SoundSet) {}

    /** A coach letter's blip; [rate] is its pitch, clamped to 0.5..2. Sound only, never a haptic. */
    fun mutter(rate: Float) {}

    /** A character's greeting; [seed] picks its sound, so each character is recognisable by ear. Sound only, never a haptic. */
    fun playGreeting(seed: Int) {}
}

/** Satiated completions and the peak play +4 semitones up. */
const val BOOSTED_RATE = 1.2599f

enum class Haptic { SELECTION_TICK, MEDIUM_IMPACT, DOUBLE_LOW_BUZZ, CONTINUOUS_LOW_RUMBLE, REJECT }

/** The comfort-setting switches. */
data class FeedbackSettings(val soundEnabled: Boolean = true, val hapticsEnabled: Boolean = true)

interface SoundOutput {
    fun play(cue: Cue, rate: Float = 1f)

    fun useSoundSet(soundSet: SoundSet) {}

    fun playGreeting(seed: Int) {}
}

interface HapticOutput { fun vibrate(haptic: Haptic) }

/** Maps events to sound and haptic effects, honouring the switches. */
class FeedbackPlayer(
    private val sounds: SoundOutput,
    private val haptics: HapticOutput,
    private val settings: () -> FeedbackSettings,
) : Feedback {
    override fun useSoundSet(soundSet: SoundSet) = sounds.useSoundSet(soundSet)

    override fun mutter(rate: Float) {
        if (settings().soundEnabled) sounds.play(Cue.MUTTER, rate.coerceIn(0.5f, 2f))
    }

    override fun playGreeting(seed: Int) {
        if (settings().soundEnabled) sounds.playGreeting(seed)
    }

    override fun play(event: FeedbackEvent, boosted: Boolean) {
        val (cue, haptic: Haptic?) = when (event) {
            FeedbackEvent.SNAP -> Cue.TICK to Haptic.SELECTION_TICK
            FeedbackEvent.CHECKMARK -> Cue.DONE to Haptic.MEDIUM_IMPACT
            FeedbackEvent.DISMISS -> Cue.X to Haptic.DOUBLE_LOW_BUZZ
            FeedbackEvent.SATIATED_PEAK -> Cue.FIZZ to Haptic.CONTINUOUS_LOW_RUMBLE
            FeedbackEvent.CAPTURE -> Cue.POP to Haptic.SELECTION_TICK
            FeedbackEvent.NOT_RECOGNISED -> Cue.BAD to Haptic.REJECT
            FeedbackEvent.UNDO -> Cue.UNDO to Haptic.SELECTION_TICK
            FeedbackEvent.OPEN -> Cue.OPEN to Haptic.SELECTION_TICK
            FeedbackEvent.LEVEL_UP -> Cue.LEVEL to Haptic.MEDIUM_IMPACT
            FeedbackEvent.RECAP_OPEN -> Cue.RECAP_OPEN to Haptic.MEDIUM_IMPACT
            FeedbackEvent.RECAP_REVEAL -> Cue.RECAP_REVEAL to Haptic.SELECTION_TICK
            FeedbackEvent.CARD_POP -> Cue.POP to null
        }
        val rate = if (boosted || event == FeedbackEvent.SATIATED_PEAK) BOOSTED_RATE else 1f
        val current = settings()
        if (current.soundEnabled) sounds.play(cue, rate)
        if (haptic != null && current.hapticsEnabled) haptics.vibrate(haptic)
    }
}
