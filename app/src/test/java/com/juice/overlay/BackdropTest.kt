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

    /** The inputs applyBackdrop() reads each time one of its three triggers fires. */
    private fun chooseAfter(vararg blurSupportedReadings: Boolean) =
        blurSupportedReadings.map { Backdrop.choose(blurEnabled = true, blurSupported = it, batterySaver = false) }

    @Test
    fun unchangedInputsGiveTheSameBackdropOnEveryTrigger() {
        assertEquals(List(3) { Backdrop.BLUR }, chooseAfter(true, true, true))
    }

    // A single transient isCrossWindowBlurEnabled == false reading (one of the hypotheses in
    // docs/overlay-blur-flicker.md) flips the backdrop to SCRIM and back with the setting unchanged.
    // choose() is stateless, so it follows each reading with no debounce.
    @Test
    fun oneTransientUnsupportedReadingFlipsToScrimAndBack() {
        assertEquals(listOf(Backdrop.BLUR, Backdrop.SCRIM, Backdrop.BLUR), chooseAfter(true, false, true))
    }
}
