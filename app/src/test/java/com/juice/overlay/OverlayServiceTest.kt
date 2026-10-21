package com.juice.overlay

import android.view.WindowManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverlayServiceTest {
    @Test
    fun overlayWindowIsHardwareAccelerated() {
        val flags = overlayLayoutParams().flags
        assertTrue(flags and WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED != 0)
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
