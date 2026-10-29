package com.juice.core.notify

import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** A notification to post at [at] saying [text]. */
data class Reminder(val at: Instant, val text: String)

/** The morning hour a Routine notifies at when its cadence has no earlier time. */
private val MORNING: LocalTime = LocalTime.of(8, 0)

/** The next reminder for [item] after [now], or null when it should not notify. */
fun plan(item: Item, now: Instant, zone: ZoneId): Reminder? {
    if (item.status != ItemStatus.Active || item.characterId != null) return null
    val reminder = if (item.type == ItemType.Routine) routineReminder(item, zone) else itemReminder(item)
    return reminder?.takeIf { it.at.isAfter(now) }
}

private fun itemReminder(item: Item): Reminder? {
    val at = item.notifyAt ?: return null
    val text = item.notifyText ?: item.title
    return Reminder(at, text)
}

private fun routineReminder(item: Item, zone: ZoneId): Reminder? {
    val due = item.nextDueOn ?: return null
    val time = item.cadence?.time?.takeIf { it < MORNING } ?: MORNING
    return Reminder(due.atTime(time).atZone(zone).toInstant(), item.title)
}
