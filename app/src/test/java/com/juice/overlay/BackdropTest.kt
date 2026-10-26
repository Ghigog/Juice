package com.juice.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @Test
    fun backdropSliceIsTheTopRightBoxShiftedSeventyDpOut() {
        val spot = backdropSliceSpot(screenWidthPx = 1080, density = 2f)
        assertEquals(560f, spot.size)
        // A 280dp box aligned top-right is centred 140dp in; the 70dp shift out leaves 70dp.
        assertEquals(1080f - 140f, spot.x)
        assertEquals(140f, spot.y)
    }

    @Test
    fun spotInterpolatesPositionAndSize() {
        val from = SliceSpot(100f, 400f, 100f)
        val to = SliceSpot(300f, 0f, 500f)
        assertEquals(from, from.lerp(to, 0f))
        assertEquals(SliceSpot(200f, 200f, 300f), from.lerp(to, 0.5f))
        assertEquals(to, from.lerp(to, 1f))
    }

    @Test
    fun blurRadiusRisesFromNothingToFullWithTheReveal() {
        assertEquals(0, blurRadiusFor(0f))
        assertEquals(BLUR_RADIUS_PX / 2, blurRadiusFor(0.5f))
        assertEquals(BLUR_RADIUS_PX, blurRadiusFor(1f))
    }

    @Test
    fun blurRadiusStaysWithinZeroAndFullForOutOfRangeProgress() {
        assertEquals(0, blurRadiusFor(-0.2f))
        assertEquals(BLUR_RADIUS_PX, blurRadiusFor(1.3f))
    }

    @Test
    fun handOverShowsTheOverlayAsTheFlightStartsAndDismissesTheStageOnceBothFlightAndFirstFrameAreDone() {
        val calls = mutableListOf<String>()
        var arrive = {}
        var drawn = {}
        handOverToOverlay(
            fly = { arrive = it; calls += "fly" },
            show = { calls += "show" },
            whenOverlayDrawn = { drawn = it; calls += "await-frame" },
            dismissStage = { calls += "dismiss" },
        )
        assertEquals(listOf("show", "await-frame", "fly"), calls)
        drawn()
        assertEquals(listOf("show", "await-frame", "fly"), calls)
        arrive()
        assertEquals(listOf("show", "await-frame", "fly", "dismiss"), calls)
    }

    @Test
    fun handOverDismissesOnceWhicheverFinishesLast() {
        val calls = mutableListOf<String>()
        var arrive = {}
        var drawn = {}
        handOverToOverlay(
            fly = { arrive = it },
            show = {},
            whenOverlayDrawn = { drawn = it },
            dismissStage = { calls += "dismiss" },
        )
        arrive()
        assertEquals(emptyList(), calls)
        drawn()
        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun aFailingShowNeverStartsTheFlight() {
        var flew = false
        assertFailsWith<IllegalStateException> {
            handOverToOverlay(
                fly = { flew = true },
                show = { throw IllegalStateException("no window") },
                whenOverlayDrawn = {},
                dismissStage = {},
            )
        }
        assertEquals(false, flew)
    }

    @Test
    fun handBackFliesWhenTheDrainStartsAndFinishesOnceBothDrainAndLandingAreDone() {
        val calls = mutableListOf<String>()
        var land = {}
        val back = HandBack(fly = { land = it; calls += "fly" }, finish = { calls += "finish" })
        back.draining()
        assertEquals(listOf("fly"), calls)
        back.drained()
        assertEquals(listOf("fly"), calls)
        land()
        assertEquals(listOf("fly", "finish"), calls)
    }

    @Test
    fun handBackFinishesOnceWhicheverEndsLast() {
        val calls = mutableListOf<String>()
        var land = {}
        val back = HandBack(fly = { land = it }, finish = { calls += "finish" })
        back.draining()
        land()
        assertEquals(emptyList(), calls)
        back.drained()
        assertEquals(listOf("finish"), calls)
    }

    @Test
    fun handBackWithoutADrainFinishesAtOnceWithoutAFlight() {
        var flew = false
        var finished = 0
        HandBack(fly = { flew = true }, finish = { finished++ }).drained()
        assertEquals(false, flew)
        assertEquals(1, finished)
    }
}
