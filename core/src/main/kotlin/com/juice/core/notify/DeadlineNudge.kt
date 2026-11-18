package com.juice.core.notify

import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import java.time.Duration
import java.time.Instant

/** Slowest handle spin, in degrees per second, the moment a reminder passes. */
const val MIN_NUDGE_SPIN_DEG_PER_SEC = 20f

/** Fastest handle spin, in degrees per second, just before a deadline. */
const val MAX_NUDGE_SPIN_DEG_PER_SEC = 120f

/**
 * What the slice handle shows at one instant.
 *
 * [incomingCount] is how many items are past their reminder and before their deadline;
 * [spinDegPerSec] is the speed for the most urgent of them (0 when none);
 * [nextChange] is the next instant the set of incoming items changes, or null when it never will.
 */
data class DeadlineNudge(val incomingCount: Int, val spinDegPerSec: Float, val nextChange: Instant?)

/** Incoming means Active with a reminder and deadline set, reminder reached and deadline not yet. */
private fun Item.isIncoming(now: Instant): Boolean {
    val reminder = notifyAt ?: return false
    val due = deadline ?: return false
    return status == ItemStatus.Active && !reminder.isAfter(now) && now.isBefore(due)
}

/** How far [now] is through the reminder-to-deadline window, 0 at the reminder and 1 at the deadline. */
private fun Item.progress(now: Instant): Float {
    val window = Duration.between(notifyAt, deadline).toMillis()
    if (window <= 0) return 1f
    return (Duration.between(notifyAt, now).toMillis().toFloat() / window).coerceIn(0f, 1f)
}

/** Spin speed for window progress [p]: eases from the minimum up to the maximum, so it depends on [p] alone. */
fun nudgeSpinFor(p: Float): Float {
    val eased = p.coerceIn(0f, 1f).let { it * it }
    return MIN_NUDGE_SPIN_DEG_PER_SEC + (MAX_NUDGE_SPIN_DEG_PER_SEC - MIN_NUDGE_SPIN_DEG_PER_SEC) * eased
}

/** The nudge for [items] at [now]. */
fun deadlineNudge(items: List<Item>, now: Instant): DeadlineNudge {
    val incoming = items.filter { it.isIncoming(now) }
    val spin = incoming.maxOfOrNull { nudgeSpinFor(it.progress(now)) } ?: 0f
    val next = items
        .filter { it.status == ItemStatus.Active && it.deadline != null && it.notifyAt != null }
        .flatMap { listOf(it.notifyAt!!, it.deadline!!) }
        .filter { it.isAfter(now) }
        .minOrNull()
    return DeadlineNudge(incoming.size, spin, next)
}
