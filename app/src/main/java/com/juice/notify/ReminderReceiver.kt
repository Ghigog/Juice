package com.juice.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.juice.R
import com.juice.overlay.OverlayService

/** Posts the planned reminder as a local notification; tapping it opens the overlay. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT) ?: return
        val id = intent.data?.lastPathSegment ?: return
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getForegroundService(
            context, 0, OverlayService.openIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(id.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "juice_reminders"
        const val EXTRA_TEXT = "com.juice.notify.extra.TEXT"

        /** The broadcast for item [id]; its data makes the PendingIntent unique per item. */
        fun intent(context: Context, id: String): Intent =
            Intent(context, ReminderReceiver::class.java).setData(Uri.parse("juice://reminder/" + Uri.encode(id)))
    }
}
