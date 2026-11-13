package com.juice.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.juice.R

/** The foreground service's ongoing, minimum-importance notification. */
internal object OverlayNotification {
    const val ID = 1
    private const val CHANNEL_ID = "juice_overlay"

    fun build(context: Context): Notification {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.overlay_channel_name), NotificationManager.IMPORTANCE_MIN),
        )
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(context.getString(R.string.overlay_notification_title))
            .setContentText(context.getString(R.string.overlay_notification_text))
            .setOngoing(true)
            .build()
    }
}
