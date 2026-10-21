package com.juice.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.juice.overlay.OverlayPermission
import com.juice.overlay.OverlayService

/** Brings the trigger strip back after a reboot, but only when overlay permission is granted. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (shouldStartStrip(intent.action, OverlayPermission.isGranted(context))) {
            OverlayService.start(context)
        }
    }

    companion object {
        fun shouldStartStrip(action: String?, overlayGranted: Boolean): Boolean =
            action == Intent.ACTION_BOOT_COMPLETED && overlayGranted
    }
}
