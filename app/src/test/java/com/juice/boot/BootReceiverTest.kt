package com.juice.boot

import android.content.Intent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BootReceiverTest {
    @Test
    fun startsStripOnBootWhenOverlayGranted() {
        assertTrue(BootReceiver.shouldStartStrip(Intent.ACTION_BOOT_COMPLETED, overlayGranted = true))
    }

    @Test
    fun doesNotStartWithoutOverlayPermission() {
        assertFalse(BootReceiver.shouldStartStrip(Intent.ACTION_BOOT_COMPLETED, overlayGranted = false))
    }

    @Test
    fun ignoresOtherActions() {
        assertFalse(BootReceiver.shouldStartStrip(Intent.ACTION_MAIN, overlayGranted = true))
        assertFalse(BootReceiver.shouldStartStrip(null, overlayGranted = true))
    }
}
