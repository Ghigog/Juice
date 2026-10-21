package com.juice.core.audio

import com.juice.core.progression.Flavour
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Short feedback sounds. [TICK] and [DONE] change character with the flavour; the rest are shared. */
enum class Cue { TICK, DONE, X, FIZZ, POP, BAD, UNDO, OPEN, LEVEL }

/**
 * Renders every cue as mono PCM (-1..1) from oscillators and filtered noise, so each flavour has its
 * own audio character without shipping a sample bank per flavour (design doc §6.4, §9).
 */
object Synth {
    const val SAMPLE_RATE = 44_100

    fun render(cue: Cue, flavour: Flavour): FloatArray {
        val mix = Mix()
        when (cue) {
            Cue.TICK -> {
                mix.tone(900.0, 200.0, 0.05, Wave.TRIANGLE, 0.18)
                when (flavour) {
                    Flavour.BERRY_SURGE -> mix.tone(120.0, 60.0, 0.09, Wave.SINE, 0.25)
                    Flavour.GOLDEN_HONEY -> mix.tone(320.0, 160.0, 0.1, Wave.SINE, 0.2)
                    Flavour.DRAGONFRUIT_OBSIDIAN -> mix.tone(2000.0, 1300.0, 0.04, Wave.SQUARE, 0.04)
                    Flavour.ELECTRIC_CITRUS -> Unit
                }
            }
            Cue.DONE -> {
                mix.noise(0.14, 500.0, 1900.0, 3.0, 0.3, Filter.BANDPASS)
                CHORD.forEachIndexed { i, f ->
                    val d = 0.1 + i * 0.07
                    when (flavour) {
                        Flavour.ELECTRIC_CITRUS -> {
                            mix.tone(f, f, 0.28, Wave.SINE, 0.16, d)
                            mix.tone(f * 1.5, f * 1.5, 0.12, Wave.SINE, 0.05, d)
                        }
                        Flavour.BERRY_SURGE -> mix.tone(f, f, 0.3, Wave.SINE, 0.15, d)
                        Flavour.DRAGONFRUIT_OBSIDIAN -> {
                            mix.tone(f, f, 0.55, Wave.TRIANGLE, 0.12, d)
                            mix.tone(f * 2.76, f * 2.76, 0.35, Wave.SINE, 0.05, d)
                        }
                        Flavour.GOLDEN_HONEY -> {
                            mix.tone(f * 0.5, f * 0.5, 1.0, Wave.SINE, 0.16, d)
                            mix.tone(f * 1.2, f * 1.2, 0.7, Wave.SINE, 0.05, d)
                        }
                    }
                }
                when (flavour) {
                    Flavour.ELECTRIC_CITRUS -> mix.tone(380.0, 900.0, 0.05, Wave.SINE, 0.2, 0.04)
                    Flavour.BERRY_SURGE -> mix.tone(60.0, 38.0, 0.3, Wave.SINE, 0.45, 0.02)
                    Flavour.GOLDEN_HONEY -> mix.noise(0.4, 900.0, 200.0, 0.7, 0.14, Filter.LOWPASS, 0.1, 0.8)
                    Flavour.DRAGONFRUIT_OBSIDIAN -> Unit
                }
            }
            Cue.X -> {
                mix.tone(110.0, 55.0, 0.14, Wave.SINE, 0.5)
                mix.noise(0.07, 400.0, 120.0, 1.0, 0.14, Filter.LOWPASS)
            }
            Cue.FIZZ -> {
                mix.noise(0.9, 7000.0, 3500.0, 0.7, 0.2, Filter.HIGHPASS)
                val random = Random(7)
                repeat(6) { mix.tone(500 + random.nextDouble() * 900, 1600.0, 0.04, Wave.SINE, 0.08, 0.05 + it * 0.1) }
            }
            Cue.POP -> mix.tone(380.0, 900.0, 0.05, Wave.SINE, 0.2)
            Cue.BAD -> {
                mix.tone(180.0, 140.0, 0.09, Wave.SQUARE, 0.05)
                mix.tone(180.0, 140.0, 0.09, Wave.SQUARE, 0.05, 0.12)
            }
            Cue.UNDO -> mix.tone(520.0, 300.0, 0.12, Wave.SINE, 0.16)
            Cue.OPEN -> mix.tone(300.0, 760.0, 0.14, Wave.SINE, 0.12)
            Cue.LEVEL -> LEVEL_NOTES.forEachIndexed { i, f -> mix.tone(f, f, 0.35, Wave.TRIANGLE, 0.13, i * 0.09) }
        }
        return mix.samples()
    }

    private val CHORD = doubleArrayOf(523.25, 659.25, 783.99, 1046.5)
    private val LEVEL_NOTES = doubleArrayOf(523.0, 659.0, 784.0, 1047.0, 1319.0)

    private enum class Wave { SINE, TRIANGLE, SQUARE }

    private enum class Filter { LOWPASS, BANDPASS, HIGHPASS }

    /** A growing buffer that voices are summed into. */
    private class Mix {
        private var buffer = FloatArray(0)

        private fun ensure(length: Int) {
            if (length > buffer.size) buffer = buffer.copyOf(length)
        }

        /** Exponential attack to [vol] in 8 ms, exponential decay to silence at [dur]. */
        private fun envelope(t: Double, dur: Double, vol: Double, attack: Double): Double {
            val floor = 1e-4
            return if (t < attack) floor * exp(ln(vol / floor) * t / attack)
            else vol * exp(ln(floor / vol) * ((t - attack) / max(dur - attack, 1e-6)).coerceAtMost(1.0))
        }

        fun tone(f0: Double, f1: Double, dur: Double, wave: Wave, vol: Double, delay: Double = 0.0) {
            val start = (delay * SAMPLE_RATE).toInt()
            val n = ((dur + 0.03) * SAMPLE_RATE).toInt()
            ensure(start + n)
            var phase = 0.0
            for (i in 0 until n) {
                val t = i.toDouble() / SAMPLE_RATE
                val f = if (f0 == f1) f0 else f0 * exp(ln(f1 / f0) * (t / dur).coerceAtMost(1.0))
                phase += f / SAMPLE_RATE
                val p = phase - kotlin.math.floor(phase)
                val s = when (wave) {
                    Wave.SINE -> sin(2 * PI * p)
                    Wave.TRIANGLE -> 1 - 4 * kotlin.math.abs(p - 0.5)
                    Wave.SQUARE -> if (p < 0.5) 1.0 else -1.0
                }
                buffer[start + i] += (s * envelope(t, dur, vol, 0.008)).toFloat()
            }
        }

        /** White noise through a swept biquad filter (RBJ cookbook), like a Web Audio BiquadFilterNode. */
        fun noise(
            dur: Double, f0: Double, f1: Double, q: Double, vol: Double, filter: Filter,
            delay: Double = 0.0, rate: Double = 1.0,
        ) {
            val start = (delay * SAMPLE_RATE).toInt()
            val n = ((dur + 0.03) * SAMPLE_RATE).toInt()
            ensure(start + n)
            val random = Random(11)
            var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
            var held = 0.0; var hold = 0.0
            for (i in 0 until n) {
                val t = i.toDouble() / SAMPLE_RATE
                // A slower playback rate holds each noise sample longer, darkening it.
                hold += rate
                if (hold >= 1.0 || i == 0) { held = random.nextDouble() * 2 - 1; hold -= kotlin.math.floor(hold) }
                val fc = f0 * exp(ln(f1 / f0) * (t / dur).coerceAtMost(1.0))
                val w = 2 * PI * fc.coerceAtMost(SAMPLE_RATE * 0.45) / SAMPLE_RATE
                val alpha = sin(w) / (2 * q)
                val cw = cos(w)
                val (b0, b1, b2) = when (filter) {
                    Filter.LOWPASS -> Triple((1 - cw) / 2, 1 - cw, (1 - cw) / 2)
                    Filter.HIGHPASS -> Triple((1 + cw) / 2, -(1 + cw), (1 + cw) / 2)
                    Filter.BANDPASS -> Triple(alpha, 0.0, -alpha)
                }
                val a0 = 1 + alpha
                val y = (b0 * held + b1 * x1 + b2 * x2 - (-2 * cw) * y1 - (1 - alpha) * y2) / a0
                x2 = x1; x1 = held; y2 = y1; y1 = y
                buffer[start + i] += (y * envelope(t, dur, vol, 0.01)).toFloat()
            }
        }

        fun samples(): FloatArray = FloatArray(buffer.size) { buffer[it].coerceIn(-1f, 1f) }
    }
}

/** 16-bit mono PCM WAV bytes for [samples], so a renderer's output can be loaded like any sample. */
fun wav(samples: FloatArray, sampleRate: Int = Synth.SAMPLE_RATE): ByteArray {
    val data = samples.size * 2
    val out = java.nio.ByteBuffer.allocate(44 + data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    out.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
    out.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        .putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
    out.put("data".toByteArray()).putInt(data)
    samples.forEach { out.putShort((it * Short.MAX_VALUE).toInt().toShort()) }
    return out.array()
}
