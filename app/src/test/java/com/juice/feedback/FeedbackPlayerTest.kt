package com.juice.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedbackPlayerTest {
    private val sounds = mutableListOf<Sound>()
    private val haptics = mutableListOf<Haptic>()

    private fun player(settings: FeedbackSettings) = FeedbackPlayer(
        sounds = object : SoundOutput { override fun play(sound: Sound) { sounds += sound } },
        haptics = object : HapticOutput { override fun vibrate(haptic: Haptic) { haptics += haptic } },
        settings = { settings },
    )

    @Test fun checkmarkPlaysSqueezeWithMediumImpact() {
        player(FeedbackSettings()).play(FeedbackEvent.CHECKMARK)
        assertEquals(listOf(Sound.SQUEEZE), sounds)
        assertEquals(listOf(Haptic.MEDIUM_IMPACT), haptics)
    }

    @Test fun hapticsOffNeverVibrates() {
        val p = player(FeedbackSettings(hapticsEnabled = false))
        FeedbackEvent.entries.forEach(p::play)
        assertTrue(haptics.isEmpty())
        assertEquals(FeedbackEvent.entries.size, sounds.size)
    }

    @Test fun soundOffStaysSilent() {
        val p = player(FeedbackSettings(soundEnabled = false))
        FeedbackEvent.entries.forEach(p::play)
        assertTrue(sounds.isEmpty())
        assertEquals(FeedbackEvent.entries.size, haptics.size)
    }

    @Test fun eachEventHasItsDocumentedEffects() {
        val p = player(FeedbackSettings())
        FeedbackEvent.entries.forEach(p::play)
        assertEquals(listOf(Sound.SNAP, Sound.SQUEEZE, Sound.THUD, Sound.FIZZ), sounds)
        assertEquals(
            listOf(Haptic.SELECTION_TICK, Haptic.MEDIUM_IMPACT, Haptic.DOUBLE_LOW_BUZZ, Haptic.CONTINUOUS_LOW_RUMBLE),
            haptics,
        )
    }
}
