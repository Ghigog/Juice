package com.juice.overlay

import com.juice.RegisterComposeActivity
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
import org.robolectric.RobolectricTestRunner

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
    fun blurIsPartOfTheParamsTheWindowIsAddedWith() {
        val params = overlayLayoutParams().also { withBackdrop(it, Backdrop.BLUR) }
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND != 0)
        assertTrue(params.blurBehindRadius > 0)
        withBackdrop(params, Backdrop.SCRIM)
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
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
}
