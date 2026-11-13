package com.juice.overlay

import android.content.Context
import com.juice.feedback.FeedbackPlayer
import com.juice.feedback.FeedbackSettings
import com.juice.feedback.SoundPoolOutput
import com.juice.feedback.VibratorOutput

/** The service's sound output and the two players on it: one for the handle, one per open overlay. */
class OverlayFeedback(private val context: Context, private val settings: () -> FeedbackSettings) {
    private val sounds by lazy { SoundPoolOutput(context) }

    /** Plays the handle's cues; lives as long as the service. */
    val handle by lazy { player() }

    /** A player for one overlay's session. */
    fun forOverlay(): FeedbackPlayer = player()

    fun release() = sounds.release()

    private fun player() = FeedbackPlayer(sounds, VibratorOutput(context), settings)
}
