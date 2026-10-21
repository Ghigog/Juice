package com.juice.overlay

import android.view.Gravity
import com.juice.settings.JuiceSettings
import com.juice.settings.StripPosition
import kotlin.test.Test
import kotlin.test.assertEquals

class OverlayParamsTest {
    @Test
    fun `bottom position maps to bottom-centre gravity`() {
        assertEquals(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, StripPosition.Bottom.gravity())
        assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, StripPosition.Top.gravity())
    }

    @Test
    fun `strip width is a percentage of the screen`() {
        assertEquals(540, JuiceSettings(stripWidthPercent = 50).stripWidthPx(1080))
    }

    @Test
    fun `strip width is clamped to 20-60 percent`() {
        assertEquals(216, JuiceSettings(stripWidthPercent = 5).stripWidthPx(1080))
        assertEquals(648, JuiceSettings(stripWidthPercent = 90).stripWidthPx(1080))
    }
}
