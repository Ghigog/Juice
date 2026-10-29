package com.juice.notify

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.overlay.OverlayService
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ReminderNotificationTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val scheduler = ReminderScheduler(context, Clock.fixed(now, ZoneOffset.UTC))
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val at = Instant.parse("2026-01-01T09:00:00Z")
    private val item = Item("a", ItemType.Task, "Call Sam", notifyAt = at, notifyText = "Call Sam now")

    @Test
    fun scheduleSetsOneAlarmAtThePlannedTime() {
        scheduler.schedule(item)
        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals(at.toEpochMilli(), alarms.scheduledAlarms.single().triggerAtMs)
    }

    @Test
    fun schedulingTwiceKeepsOneAlarm() {
        scheduler.schedule(item)
        scheduler.schedule(item)
        assertEquals(1, alarms.scheduledAlarms.size)
    }

    @Test
    fun cancelRemovesTheAlarm() {
        scheduler.schedule(item)
        scheduler.cancel("a")
        assertNull(alarms.nextScheduledAlarm)
    }

    @Test
    fun rescheduleAllSchedulesEachItem() {
        scheduler.rescheduleAll(listOf(item, item.copy(id = "b")))
        assertEquals(2, alarms.scheduledAlarms.size)
    }

    @Test
    fun firedAlarmPostsTheTextOnTheRemindersChannelAndOpensTheOverlay() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        scheduler.schedule(item)
        val fired = shadowOf(alarms.nextScheduledAlarm!!.operation).savedIntent
        ReminderReceiver().onReceive(context, fired)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = shadowOf(manager).allNotifications.single()
        assertEquals("juice_reminders", posted.channelId)
        assertEquals("Call Sam now", posted.extras.getCharSequence("android.text").toString())
        val tap = shadowOf(posted.contentIntent)
        assertTrue(tap.isForegroundService)
        assertEquals(OverlayService.ACTION_OPEN, tap.savedIntent.action)
    }

    @Test
    fun skipsSilentlyWithoutNotificationPermission() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        scheduler.schedule(item)
        ReminderReceiver().onReceive(context, shadowOf(alarms.nextScheduledAlarm!!.operation).savedIntent)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
