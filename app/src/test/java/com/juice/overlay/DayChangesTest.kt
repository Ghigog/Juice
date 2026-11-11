package com.juice.overlay

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** A clock the test moves by hand. */
class MutableClock(var now: Instant, private var zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
    fun moveTo(zone: ZoneId) { this.zone = zone }
}

@RunWith(RobolectricTestRunner::class)
class DayChangesTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val clock = MutableClock(Instant.parse("2026-01-01T23:59:00Z"))

    private fun broadcast(action: String) {
        context.sendBroadcast(Intent(action))
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun emitsTodayThenFollowsDateAndZoneChangesWithoutRepeats() {
        val days = mutableListOf<LocalDate>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch { todayChanges(context, clock).toList(days) }
        assertEquals(listOf(LocalDate.parse("2026-01-01")), days)

        broadcast(Intent.ACTION_TIME_CHANGED) // same date: dropped
        assertEquals(1, days.size)

        clock.now = Instant.parse("2026-01-02T00:01:00Z")
        broadcast(Intent.ACTION_DATE_CHANGED)
        assertEquals(LocalDate.parse("2026-01-02"), days.last())

        clock.moveTo(ZoneId.of("America/Los_Angeles"))
        broadcast(Intent.ACTION_TIMEZONE_CHANGED)
        assertEquals(listOf("2026-01-01", "2026-01-02", "2026-01-01").map(LocalDate::parse), days)
        job.cancel()
    }
}
