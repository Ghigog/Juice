package com.juice.settings

import com.juice.core.progression.Flavour
import com.juice.feedback.FeedbackSettings

/** How far the slice handle must be swiped, any way, to open Juice. */
enum class SwipeDistance(val dp: Int) { Short(60), Medium(90), Long(130) }

/** User preferences read by the overlay, feedback, blur and intake code. */
data class JuiceSettings(
    val sound: Boolean = true,
    val tapButtons: Boolean = false,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    val blur: Boolean = true,
    val onDeviceOnly: Boolean = false,
    /** The slice handle's centre, as fractions of the screen's width and height. */
    val handleX: Float = DEFAULT_HANDLE_X,
    val handleY: Float = DEFAULT_HANDLE_Y,
    val swipeDistance: SwipeDistance = SwipeDistance.Medium,
    /** The flavour the player picked; null follows the highest one unlocked. */
    val flavour: Flavour? = null,
) {
    /** Slosh and particles are the overlay's motion effects; reduce motion turns both off. */
    val sloshEnabled: Boolean get() = !reduceMotion
    val particlesEnabled: Boolean get() = !reduceMotion

    val feedback: FeedbackSettings get() = FeedbackSettings(soundEnabled = sound, hapticsEnabled = haptics)

    companion object {
        /** Top centre, where the strip used to be. */
        const val DEFAULT_HANDLE_X = 0.5f
        const val DEFAULT_HANDLE_Y = 0.075f
    }
}
