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
        val spot = backdropSliceSpot(screenWidthPx = 1080, screenHeightPx = 2400, density = 2f)
        assertEquals(560f, spot.size)
        // A 280dp box aligned top-right is centred 140dp in; the 70dp shift out leaves 70dp.
        assertEquals(1080f - 140f, spot.x)
        assertEquals(140f, spot.y)
    }

    @Test
    fun inLandscapeTheBackdropSliceIsHalfTheScreenTall() {
        // 400dp tall: a 200dp box, centred 100dp in and shifted 50dp out.
        val spot = backdropSliceSpot(screenWidthPx = 1800, screenHeightPx = 800, density = 2f)
        assertEquals(400f, spot.size)
        assertEquals(1800f - 100f, spot.x)
        assertEquals(100f, spot.y)
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
    fun handOverShowsTheOverlaySliceOnArrivalAndDismissesTheStageAfterThatFrame() {
        val calls = mutableListOf<String>()
        var arrive = {}
        val frames = mutableListOf<() -> Unit>()
        handOverToOverlay(
            fly = { arrive = it; calls += "fly" },
            show = { calls += "show" },
            showOverlaySlice = { calls += "slice" },
            whenOverlayDrawn = { frames += it; calls += "await-frame" },
            dismissStage = { calls += "dismiss" },
        )
        assertEquals(listOf("show", "await-frame", "fly"), calls)
        frames[0]()
        assertEquals(listOf("show", "await-frame", "fly"), calls)
        arrive()
        assertEquals(listOf("show", "await-frame", "fly", "slice", "await-frame"), calls)
        frames[1]()
        assertEquals(listOf("show", "await-frame", "fly", "slice", "await-frame", "dismiss"), calls)
    }

    @Test
    fun handOverKeepsTheStageUntilTheSliceFrameEvenIfTheFirstFrameIsLate() {
        val calls = mutableListOf<String>()
        var arrive = {}
        val frames = mutableListOf<() -> Unit>()
        handOverToOverlay(
            fly = { arrive = it },
            show = {},
            showOverlaySlice = {},
            whenOverlayDrawn = { frames += it },
            dismissStage = { calls += "dismiss" },
        )
        arrive()
        frames[1]()
        assertEquals(emptyList(), calls)
        frames[0]()
        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun handOverDismissesAtOnceWhenEverythingCompletesSynchronously() {
        var dismissed = 0
        handOverToOverlay(
            fly = { it() },
            show = {},
            showOverlaySlice = {},
            whenOverlayDrawn = { it() },
            dismissStage = { dismissed++ },
        )
        assertEquals(1, dismissed)
    }

    @Test
    fun aFailingShowNeverStartsTheFlight() {
        var flew = false
        assertFailsWith<IllegalStateException> {
            handOverToOverlay(
                fly = { flew = true },
                show = { throw IllegalStateException("no window") },
                showOverlaySlice = {},
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
        val back = HandBack(fly = { _, landed -> land = landed; calls += "fly" }, hideOverlaySlice = {}, finish = { calls += "finish" })
        back.draining()
        assertEquals(listOf("fly"), calls)
        back.drained()
        assertEquals(listOf("fly"), calls)
        land()
        assertEquals(listOf("fly", "finish"), calls)
    }

    @Test
    fun handBackHidesTheOverlaySliceOnlyOnceTheStageHasDrawn() {
        val calls = mutableListOf<String>()
        var drawn = {}
        val back = HandBack(
            fly = { stageDrawn, _ -> drawn = stageDrawn; calls += "fly" },
            hideOverlaySlice = { calls += "hide" },
            finish = {},
        )
        back.draining()
        assertEquals(listOf("fly"), calls)
        drawn()
        assertEquals(listOf("fly", "hide"), calls)
    }

    @Test
    fun handBackFinishesOnceWhicheverEndsLast() {
        val calls = mutableListOf<String>()
        var land = {}
        val back = HandBack(fly = { _, landed -> land = landed }, hideOverlaySlice = {}, finish = { calls += "finish" })
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
        HandBack(fly = { _, _ -> flew = true }, hideOverlaySlice = {}, finish = { finished++ }).drained()
        assertEquals(false, flew)
        assertEquals(1, finished)
    }
}
