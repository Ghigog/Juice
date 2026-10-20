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
}
