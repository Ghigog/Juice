package com.juice.overlay

import com.juice.settings.JuiceSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class OverlayParamsTest {
    @Test
    fun `handle centre is its fractions of the screen`() {
        assertEquals(540f to 240f, JuiceSettings(handleX = 0.5f, handleY = 0.1f).handleCentre(1080, 2400))
    }

    @Test
    fun `handle centre stays on screen`() {
        assertEquals(1080f to 0f, JuiceSettings(handleX = 3f, handleY = -1f).handleCentre(1080, 2400))
    }

    @Test
    fun `a dropped handle is saved as fractions, kept inside the edge margin`() {
        assertEquals(0.5f to 0.25f, handleFractions(540f, 600f, 1080, 2400, margin = 90f))
        assertEquals(90f / 1080 to (2400f - 90f) / 2400, handleFractions(-50f, 5000f, 1080, 2400, margin = 90f))
    }
}
