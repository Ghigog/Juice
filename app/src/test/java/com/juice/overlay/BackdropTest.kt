package com.juice.overlay

import kotlin.test.Test
import kotlin.test.assertEquals

class BackdropTest {
    @Test
    fun blursWhenEnabledSupportedAndBatterySaverOff() {
        assertEquals(Backdrop.BLUR, Backdrop.choose(blurEnabled = true, blurSupported = true, batterySaver = false))
    }

    @Test
    fun scrimUnderBatterySaver() {
        assertEquals(Backdrop.SCRIM, Backdrop.choose(blurEnabled = true, blurSupported = true, batterySaver = true))
    }

    @Test
    fun scrimWhenOffOrUnsupported() {
        assertEquals(Backdrop.SCRIM, Backdrop.choose(blurEnabled = false, blurSupported = true, batterySaver = false))
        assertEquals(Backdrop.SCRIM, Backdrop.choose(blurEnabled = true, blurSupported = false, batterySaver = false))
    }
}
