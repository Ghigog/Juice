package com.juice.core.intake

import java.time.LocalTime
import java.time.ZonedDateTime

/** Applies a late parse to a card: user edits win, and the title is never overwritten. */
fun IntakeCard.mergeParse(parsed: ParsedItem): IntakeCard {
    fun touched(field: IntakeField) = field in editedFields
    val mergedType = if (touched(IntakeField.Type)) type else parsed.type
    return copy(
        type = mergedType,
        completionCondition = if (touched(IntakeField.CompletionCondition)) completionCondition else parsed.completionCondition,
        deadline = if (mergedType == ItemType.List) null else if (touched(IntakeField.Deadline)) deadline else parsed.deadline,
        subItems = if (touched(IntakeField.SubItems)) subItems else parsed.subItems,
        // A user-set type that is not Routine drops the parsed cadence.
        cadence = if (touched(IntakeField.Cadence) || touched(IntakeField.Type) && type != ItemType.Routine) cadence else parsed.cadence,
    )
}

/**
 * The rules always decide the type (and a Routine's cadence); the model only supplies the title, the steps and the
 * deadline. Only a Project and a List have steps, a Routine, a Note and a List have no deadline or reminder, and a Project the model gave no
 * steps keeps the rules' own.
 *
 * When [rawText] names a day (today, tonight, tomorrow, a weekday) the rules' date wins over a model deadline on another
 * local date; the model's time of day is kept when it gave one. A reminder from the model is kept only when the deadline was
 * not corrected and it is sensible; otherwise it comes from [reminderFor].
 */
fun ParsedItem.improvingOn(rules: ParsedItem, now: ZonedDateTime, rawText: String): ParsedItem {
    val steps = if (rules.type.hasSteps) subItems.ifEmpty { rules.subItems } else emptyList()
    if (rules.type in setOf(ItemType.Routine, ItemType.Note, ItemType.List)) {
        return copy(type = rules.type, deadline = null, subItems = steps, cadence = rules.cadence, notifyAt = null, notifyText = null)
    }
    val zone = now.zone
    val modelDeadline = deadline?.atZone(zone)
    val named = RulesFallback.namedDayDeadline(rawText, now)
    val merged = when {
        modelDeadline == null -> rules.deadline?.atZone(zone)
        named != null && modelDeadline.toLocalDate() != named.toLocalDate() -> {
            val time = if (modelDeadline.toLocalTime() == DATE_ONLY_TIME) named.toLocalTime() else modelDeadline.toLocalTime()
            named.toLocalDate().atTime(time).atZone(zone)
        }
        else -> modelDeadline
    }
    val corrected = merged != modelDeadline
    val modelReminder = notifyAt?.atZone(zone)
    val keep = !corrected && modelReminder != null && modelReminder.isAfter(now) &&
        (merged == null || (!modelReminder.isAfter(merged) && !modelReminder.toLocalDateTime().isBefore(merged.toLocalDate().minusDays(1).atTime(EARLIEST_EVE))))
    val reminder = when {
        keep || merged == null -> notifyAt
        else -> reminderFor(merged, now, merged.toLocalTime() != DATE_ONLY_TIME)?.toInstant()
    }
    return copy(
        type = rules.type,
        deadline = merged?.toInstant(),
        subItems = steps,
        cadence = rules.cadence,
        notifyAt = reminder,
        notifyText = if (reminder == null) null else notifyText ?: title,
    )
}

private val EARLIEST_EVE: LocalTime = LocalTime.of(18, 0)
