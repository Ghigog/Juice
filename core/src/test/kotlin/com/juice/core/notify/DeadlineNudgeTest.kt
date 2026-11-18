package com.juice.core.notify

import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeadlineNudgeTest {
    private fun item(id: String, notifyAt: String?, deadline: String?, status: ItemStatus = ItemStatus.Active) =
        Item(id, ItemType.Task, id, deadline = deadline?.let(Instant::parse), status = status, notifyAt = notifyAt?.let(Instant::parse))

    private val trash = item("trash", "2026-12-03T09:00:00Z", "2026-12-03T15:00:00Z")
    private val essay = item("essay", "2026-12-05T09:00:00Z", "2026-12-12T23:59:00Z")

    @Test
    fun `half way through either window gives the same speed`() {
        val trashHalf = deadlineNudge(listOf(trash), Instant.parse("2026-12-03T12:00:00Z"))
        val essayHalf = deadlineNudge(listOf(essay), Instant.parse("2026-12-09T04:29:30Z"))
        assertEquals(trashHalf.spinDegPerSec, essayHalf.spinDegPerSec, 0.001f)
        assertEquals(1, trashHalf.incomingCount)
    }

    @Test
    fun `speed starts at the minimum rises monotonically and stays under the maximum`() {
        assertEquals(MIN_NUDGE_SPIN_DEG_PER_SEC, nudgeSpinFor(0f), 0f)
        val speeds = (0..100).map { nudgeSpinFor(it / 100f) }
        assertTrue(speeds.zipWithNext().all { (a, b) -> b > a })
        assertTrue(speeds.last() <= MAX_NUDGE_SPIN_DEG_PER_SEC && MAX_NUDGE_SPIN_DEG_PER_SEC < 720f)
    }

    @Test
    fun `two incoming items count 2 and spin at the one further through`() {
        val now = Instant.parse("2026-12-05T13:00:00Z")
        val nudge = deadlineNudge(listOf(essay, trash.copy(notifyAt = Instant.parse("2026-12-05T10:00:00Z"), deadline = Instant.parse("2026-12-05T15:00:00Z"))), now)
        assertEquals(2, nudge.incomingCount)
        assertEquals(nudgeSpinFor(0.6f), nudge.spinDegPerSec, 0.001f)
    }

    @Test
    fun `passed ticked or unset items are not incoming`() {
        val now = Instant.parse("2026-12-03T16:00:00Z")
        val ticked = item("t", "2026-12-03T09:00:00Z", "2026-12-04T09:00:00Z", ItemStatus.Completed)
        val noReminder = item("n", null, "2026-12-04T09:00:00Z")
        val noDeadline = item("d", "2026-12-03T09:00:00Z", null)
        val nudge = deadlineNudge(listOf(trash, ticked, noReminder, noDeadline), now)
        assertEquals(DeadlineNudge(0, 0f, null), nudge)
    }

    @Test
    fun `nothing past its reminder gives 0 and the earliest upcoming reminder`() {
        val nudge = deadlineNudge(listOf(essay, trash), Instant.parse("2026-12-03T08:00:00Z"))
        assertEquals(0, nudge.incomingCount)
        assertEquals(0f, nudge.spinDegPerSec, 0f)
        assertEquals(Instant.parse("2026-12-03T09:00:00Z"), nudge.nextChange)
    }

    @Test
    fun `next change is the earliest deadline once reminders have passed and none at the deadline itself`() {
        val nudge = deadlineNudge(listOf(trash, essay), Instant.parse("2026-12-03T10:00:00Z"))
        assertEquals(Instant.parse("2026-12-03T15:00:00Z"), nudge.nextChange)
        assertEquals(0, deadlineNudge(listOf(trash), Instant.parse("2026-12-03T15:00:00Z")).incomingCount)
        assertNull(deadlineNudge(emptyList(), Instant.EPOCH).nextChange)
    }
}
