package com.juice.core.audio

import com.juice.core.progression.Flavour
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SynthTest {
    @Test fun everyCueRendersAudibleClippedSound() {
        Cue.entries.forEach { cue ->
            Flavour.entries.forEach { flavour ->
                val pcm = Synth.render(cue, flavour)
                assertTrue(pcm.isNotEmpty(), "$cue $flavour is empty")
                assertTrue(pcm.all { it in -1f..1f }, "$cue $flavour clips")
                assertTrue(pcm.maxOf { abs(it) } > 0.02f, "$cue $flavour is silent")
            }
        }
    }

    @Test fun sameGreetingSeedRendersIdenticalSamples() {
        Flavour.entries.forEach { assertTrue(Synth.renderGreeting(7, it).contentEquals(Synth.renderGreeting(7, it))) }
    }

    @Test fun differentGreetingSeedsRenderDifferentSamples() {
        val greetings = (1..20).map { Synth.renderGreeting(it, Flavour.ELECTRIC_CITRUS).toList() }
        assertTrue(greetings.toSet().size > 10)
        assertTrue(!Synth.renderGreeting(1, Flavour.ELECTRIC_CITRUS).contentEquals(Synth.renderGreeting(2, Flavour.ELECTRIC_CITRUS)))
    }

    @Test fun greetingIsAudibleAndClipped() {
        (1..20).forEach { seed ->
            val pcm = Synth.renderGreeting(seed, Flavour.BERRY_SURGE)
            assertTrue(pcm.isNotEmpty() && pcm.all { it in -1f..1f } && pcm.maxOf { abs(it) } > 0.02f)
        }
    }

    @Test fun eachFlavourSoundsDifferentOnCompletion() {
        val done = Flavour.entries.map { Synth.render(Cue.DONE, it).toList() }
        assertEquals(done.size, done.toSet().size)
    }

    @Test fun honeyRingsLongestAndShortCuesStayShort() {
        val seconds = { cue: Cue, f: Flavour -> Synth.render(cue, f).size / Synth.SAMPLE_RATE.toDouble() }
        assertTrue(seconds(Cue.DONE, Flavour.GOLDEN_HONEY) > seconds(Cue.DONE, Flavour.ELECTRIC_CITRUS))
        assertTrue(seconds(Cue.TICK, Flavour.ELECTRIC_CITRUS) < 0.2)
    }

    @Test fun mutterIsAShortBlipForEveryFlavour() {
        Flavour.entries.forEach { f ->
            val pcm = Synth.render(Cue.MUTTER, f)
            assertTrue(pcm.isNotEmpty() && pcm.all { it in -1f..1f })
            assertTrue(pcm.size / Synth.SAMPLE_RATE.toDouble() < 0.1)
        }
    }

    @Test fun wavHasAHeaderAndTwoBytesPerSample() {
        val bytes = wav(FloatArray(100))
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals(44 + 200, bytes.size)
    }
}
