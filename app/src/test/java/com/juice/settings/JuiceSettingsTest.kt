package com.juice.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JuiceSettingsTest {
    @Test
    fun `reduce motion turns slosh and particles off`() {
        val s = JuiceSettings(reduceMotion = true)
        assertFalse(s.sloshEnabled)
        assertFalse(s.particlesEnabled)
    }

    @Test
    fun `motion effects are on by default`() {
        val s = JuiceSettings()
        assertTrue(s.sloshEnabled)
        assertTrue(s.particlesEnabled)
    }

    @Test
    fun `haptics off silences vibration but keeps sound`() {
        val f = JuiceSettings(haptics = false).feedback
        assertFalse(f.hapticsEnabled)
        assertTrue(f.soundEnabled)
    }

    @Test
    fun `sound off silences sound but keeps haptics`() {
        val f = JuiceSettings(sound = false).feedback
        assertFalse(f.soundEnabled)
        assertTrue(f.hapticsEnabled)
    }
}
