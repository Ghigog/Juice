package com.juice.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.juice.core.model.Item
import com.juice.core.notify.plan
import java.time.Clock

/** Sets one inexact, idle-tolerant alarm per Item at its planned reminder time; needs no exact-alarm permission. */
class ReminderScheduler(private val context: Context, private val clock: Clock) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /** Replaces any alarm for [item] with one at its planned time, or leaves none when it should not notify. */
    fun schedule(item: Item) {
        cancel(item.id)
        val reminder = plan(item, clock.instant(), clock.zone) ?: return
        val intent = ReminderReceiver.intent(context, item.id).putExtra(ReminderReceiver.EXTRA_TEXT, reminder.text)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at.toEpochMilli(), pending(intent))
    }

    fun cancel(id: String) {
        alarms.cancel(pending(ReminderReceiver.intent(context, id)))
    }

    fun rescheduleAll(items: List<Item>) {
        items.forEach(::schedule)
    }

    private fun pending(intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}
