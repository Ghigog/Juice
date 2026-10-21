package com.juice.settings

import com.juice.core.progression.Flavour
import com.juice.feedback.FeedbackSettings

enum class StripPosition { Top, Bottom }

/** User preferences read by the overlay, feedback, blur and intake code. */
data class JuiceSettings(
    val sound: Boolean = true,
    val tapButtons: Boolean = false,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    val blur: Boolean = true,
    val onDeviceOnly: Boolean = false,
    val stripPosition: StripPosition = StripPosition.Top,
    val stripWidthPercent: Int = DEFAULT_STRIP_WIDTH_PERCENT,
    /** The flavour the player picked; null follows the highest one unlocked. */
    val flavour: Flavour? = null,
) {
    /** Slosh and particles are the overlay's motion effects; reduce motion turns both off. */
    val sloshEnabled: Boolean get() = !reduceMotion
    val particlesEnabled: Boolean get() = !reduceMotion

    val feedback: FeedbackSettings get() = FeedbackSettings(soundEnabled = sound, hapticsEnabled = haptics)

    companion object {
        const val MIN_STRIP_WIDTH_PERCENT = 20
        const val MAX_STRIP_WIDTH_PERCENT = 60
        const val DEFAULT_STRIP_WIDTH_PERCENT = 30
    }
}
