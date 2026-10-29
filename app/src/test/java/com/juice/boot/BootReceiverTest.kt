package com.juice.boot

import android.app.AlarmManager
import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BootReceiverTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val job = Job()
    private val receiver = BootReceiver().apply {
        scope = CoroutineScope(job + Dispatchers.IO)
        clock = Clock.fixed(now, ZoneOffset.UTC)
    }

    private fun store(vararg ids: String) = runBlocking {
        val database = JuiceDatabase.open(context)
        withContext(Dispatchers.IO) { database.clearAllTables() }
        val repository = ItemRepository(database.itemDao())
        ids.forEach {
            repository.save(
                Item(it, ItemType.Task, "Call $it", notifyAt = Instant.parse("2026-01-01T09:00:00Z"), notifyText = "Call $it now"),
            )
        }
    }

    private fun receive(action: String) {
        receiver.onReceive(context, Intent(action))
        runBlocking { job.children.toList().joinAll() }
    }

    @Test
    fun bootSchedulesAnAlarmForEachActiveItemWithAReminder() {
        store("a", "b")
        receive(Intent.ACTION_BOOT_COMPLETED)
        assertEquals(2, alarms.scheduledAlarms.size)
    }

    @Test
    fun bootReschedulesRemindersWithoutOverlayPermissionAndDoesNotStartTheStrip() {
        store("a")
        receive(Intent.ACTION_BOOT_COMPLETED)
        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals(null, shadowOf(context).nextStartedService)
    }

    @Test
    fun otherActionsScheduleNothing() {
        store("a")
        receive(Intent.ACTION_MAIN)
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

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
