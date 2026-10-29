package com.juice.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import com.juice.notify.ReminderScheduler
import com.juice.overlay.OverlayPermission
import com.juice.overlay.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * After a reboot, sets the reminder alarms again (alarms do not survive one) whatever the overlay permission,
 * and brings the trigger strip back only when overlay permission is granted.
 */
class BootReceiver : BroadcastReceiver() {
    internal var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal var clock: Clock = Clock.systemDefaultZone()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (shouldStartStrip(intent.action, OverlayPermission.isGranted(context))) {
            OverlayService.start(context)
        }
        val pending = goAsync()
        scope.launch {
            try {
                val items = ItemRepository(JuiceDatabase.open(context).itemDao()).active()
                ReminderScheduler(context, clock).rescheduleAll(items)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        fun shouldStartStrip(action: String?, overlayGranted: Boolean): Boolean =
            action == Intent.ACTION_BOOT_COMPLETED && overlayGranted
    }
}
