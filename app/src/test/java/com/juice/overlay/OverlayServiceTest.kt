package com.juice.overlay

import com.juice.RegisterComposeActivity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.juice.ui.reservoir.ThirstReservoirView
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class OverlayServiceTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    @Test
    fun reservoirShowsTheMeterAndFollowsIt() {
        val reservoir = ThirstReservoirView(ApplicationProvider.getApplicationContext())
        val fill = mutableFloatStateOf(0.39f)
        compose.setContent { ReservoirHost(reservoir, fill.floatValue, Modifier) }
        compose.runOnIdle { assertEquals(0.39f, reservoir.fill) }
        fill.floatValue = 0.6f
        compose.runOnIdle { assertEquals(0.6f, reservoir.fill) }
    }

    @Test
    fun overlayWindowIsHardwareAccelerated() {
        val flags = overlayLayoutParams().flags
        assertTrue(flags and WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED != 0)
    }

    @Test
    fun keyboardDoesNotResizeTheOverlayWindow() {
        val mode = overlayLayoutParams().softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING, mode)
    }

    @Test
    fun overlayIsAnOverlayDialogWithTheOverlayParams() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val attrs = overlayDialog(context, View(context)) {}.window!!.attributes
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, attrs.type)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, attrs.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, attrs.height)
        assertTrue(attrs.flags and WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED != 0)
        assertEquals(0, attrs.fitInsetsTypes)
    }

    // The blur is the window's own background blur. FLAG_BLUR_BEHIND hands it to a system dim
    // layer that the window manager drops and re-adds on its own (docs/overlay-blur-flicker.md).
    @Test
    fun overlayWindowDoesNotUseBlurBehindOrDim() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val flags = overlayDialog(context, View(context)) {}.window!!.attributes.flags
        assertEquals(0, flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        assertEquals(0, flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    // Without FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS the window paints black bars behind the status
    // and navigation bars, so the overlay looked like it was not full screen.
    @Test
    @Suppress("DEPRECATION")
    fun overlayDrawsTransparentSystemBarBackgrounds() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val window = overlayDialog(context, View(context)) {}.window!!
        assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS != 0)
        assertEquals(android.graphics.Color.TRANSPARENT, window.statusBarColor)
        assertEquals(android.graphics.Color.TRANSPARENT, window.navigationBarColor)
    }

    @Test
    fun backGoesToTheOverlayInsteadOfClosingTheDialog() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var backs = 0
        val dialog = overlayDialog(context, View(context)) { backs++ }
        var cancelled = false
        dialog.setOnCancelListener { cancelled = true }
        @Suppress("DEPRECATION")
        dialog.onBackPressed()
        assertEquals(1, backs)
        assertEquals(false, cancelled)
    }

    @Test
    fun blurNeverDropsToZeroWhileBlurred() {
        assertEquals(1, windowBlurRadius(Backdrop.BLUR, 0))
        assertEquals(BLUR_RADIUS_PX, windowBlurRadius(Backdrop.BLUR, BLUR_RADIUS_PX))
    }

    @Test
    fun scrimHasNoBlur() {
        assertEquals(0, windowBlurRadius(Backdrop.SCRIM, BLUR_RADIUS_PX))
    }

    @Test
    fun overlayWindowRunsUnderTheSystemBars() {
        assertEquals(0, overlayLayoutParams().fitInsetsTypes)
    }

    @Test
    fun failureBuildingOverlayCleansUpAndRestoresStrip() {
        val calls = mutableListOf<String>()
        guardedShowOverlay(
            show = { error("boom") },
            cleanup = { calls += "cleanup" },
            restoreStrip = { calls += "strip" },
            onFailure = { calls += "report: ${it.message}" },
        )
        assertEquals(listOf("cleanup", "strip", "report: boom"), calls)
    }

    @Test
    fun successLeavesStripHidden() {
        val calls = mutableListOf<String>()
        guardedShowOverlay({ calls += "show" }, { calls += "cleanup" }, { calls += "strip" })
        assertEquals(listOf("show"), calls)
    }

    @Test
    fun openIntentTargetsTheServiceWithActionOpen() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = OverlayService.openIntent(context)
        assertEquals(OverlayService.ACTION_OPEN, intent.action)
        assertEquals(OverlayService::class.java.name, intent.component?.className)
    }

    @Test
    fun openActionWithoutOverlayPermissionStopsTheServiceAndShowsNothing() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        shadowOf(context as android.app.Application).denyPermissions()
        android.provider.Settings.canDrawOverlays(context).let { assertEquals(false, it) }
        val controller = Robolectric.buildService(OverlayService::class.java, OverlayService.openIntent(context)).create()
        controller.startCommand(0, 1)
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
    }
}
