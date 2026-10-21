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

    @Test fun eachFlavourSoundsDifferentOnCompletion() {
        val done = Flavour.entries.map { Synth.render(Cue.DONE, it).toList() }
        assertEquals(done.size, done.toSet().size)
    }

    @Test fun honeyRingsLongestAndShortCuesStayShort() {
        val seconds = { cue: Cue, f: Flavour -> Synth.render(cue, f).size / Synth.SAMPLE_RATE.toDouble() }
        assertTrue(seconds(Cue.DONE, Flavour.GOLDEN_HONEY) > seconds(Cue.DONE, Flavour.ELECTRIC_CITRUS))
        assertTrue(seconds(Cue.TICK, Flavour.ELECTRIC_CITRUS) < 0.2)
    }

    @Test fun wavHasAHeaderAndTwoBytesPerSample() {
        val bytes = wav(FloatArray(100))
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals(44 + 200, bytes.size)
    }
}
