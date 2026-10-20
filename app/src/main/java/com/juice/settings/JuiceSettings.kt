package com.juice.settings

enum class StripPosition { Top, Bottom }

/** User preferences read by the overlay, feedback, blur and intake code. */
data class JuiceSettings(
    val tapButtons: Boolean = false,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    val blur: Boolean = true,
    val onDeviceOnly: Boolean = false,
    val stripPosition: StripPosition = StripPosition.Top,
) {
    /** Slosh and particles are the overlay's motion effects; reduce motion turns both off. */
    val sloshEnabled: Boolean get() = !reduceMotion
    val particlesEnabled: Boolean get() = !reduceMotion
}
