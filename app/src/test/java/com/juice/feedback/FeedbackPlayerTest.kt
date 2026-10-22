package com.juice.feedback

import com.juice.core.audio.Cue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedbackPlayerTest {
    private val sounds = mutableListOf<Pair<Cue, Float>>()
    private val haptics = mutableListOf<Haptic>()

    private fun player(settings: FeedbackSettings) = FeedbackPlayer(
        sounds = object : SoundOutput { override fun play(cue: Cue, rate: Float) { sounds += cue to rate } },
        haptics = object : HapticOutput { override fun vibrate(haptic: Haptic) { haptics += haptic } },
        settings = { settings },
    )

    @Test fun checkmarkPlaysSqueezeWithMediumImpact() {
        player(FeedbackSettings()).play(FeedbackEvent.CHECKMARK)
        assertEquals(listOf(Cue.DONE to 1f), sounds)
        assertEquals(listOf(Haptic.MEDIUM_IMPACT), haptics)
    }

    @Test fun satiatedCompletionAndPeakArePitchedUp() {
        val p = player(FeedbackSettings())
        p.play(FeedbackEvent.CHECKMARK, boosted = true)
        p.play(FeedbackEvent.SATIATED_PEAK)
        assertEquals(listOf(Cue.DONE to BOOSTED_RATE, Cue.FIZZ to BOOSTED_RATE), sounds)
    }

    @Test fun hapticsOffNeverVibrates() {
        val p = player(FeedbackSettings(hapticsEnabled = false))
        FeedbackEvent.entries.forEach { p.play(it) }
        assertTrue(haptics.isEmpty())
        assertEquals(FeedbackEvent.entries.size, sounds.size)
    }

    @Test fun soundOffStaysSilent() {
        val p = player(FeedbackSettings(soundEnabled = false))
        FeedbackEvent.entries.forEach { p.play(it) }
        assertTrue(sounds.isEmpty())
        assertEquals(FeedbackEvent.entries.size, haptics.size)
    }

    @Test fun documentedMomentsHaveTheirDocumentedEffects() {
        val p = player(FeedbackSettings())
        listOf(FeedbackEvent.SNAP, FeedbackEvent.CHECKMARK, FeedbackEvent.DISMISS, FeedbackEvent.SATIATED_PEAK).forEach { p.play(it) }
        assertEquals(listOf(Cue.TICK, Cue.DONE, Cue.X, Cue.FIZZ), sounds.map { it.first })
        assertEquals(
            listOf(Haptic.SELECTION_TICK, Haptic.MEDIUM_IMPACT, Haptic.DOUBLE_LOW_BUZZ, Haptic.CONTINUOUS_LOW_RUMBLE),
            haptics,
        )
    }

    @Test fun recapOpenPlaysItsCueAndHaptic() {
        player(FeedbackSettings()).play(FeedbackEvent.RECAP_OPEN)
        assertEquals(listOf(Cue.RECAP_OPEN to 1f), sounds)
        assertEquals(listOf(Haptic.MEDIUM_IMPACT), haptics)
    }

    @Test fun recapRevealWithSoundOffOnlyVibrates() {
        player(FeedbackSettings(soundEnabled = false)).play(FeedbackEvent.RECAP_REVEAL)
        assertTrue(sounds.isEmpty())
        assertEquals(listOf(Haptic.SELECTION_TICK), haptics)
    }

    @Test fun recapOpenWithHapticsOffOnlySounds() {
        player(FeedbackSettings(hapticsEnabled = false)).play(FeedbackEvent.RECAP_OPEN)
        assertEquals(listOf(Cue.RECAP_OPEN to 1f), sounds)
        assertTrue(haptics.isEmpty())
    }
}
