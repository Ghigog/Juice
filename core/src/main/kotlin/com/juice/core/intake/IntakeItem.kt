package com.juice.core.intake

import com.juice.core.routine.Cadence
import java.time.Instant

enum class ItemType {
    Note, Task, List, Goal, Project, Routine;

    /** A Project and a List keep their tasks as steps inside the card. */
    val hasSteps get() = this == Project || this == List
}

/** A validated parser result. Only [IntakeValidator] and [RulesFallback] build these. */
data class ParsedItem(
    val type: ItemType,
    val title: String,
    val completionCondition: String? = null,
    val deadline: Instant? = null,
    val subItems: List<String> = emptyList(),
    val cadence: Cadence? = null,
    val notifyAt: Instant? = null,
    val notifyText: String? = null,
)

/** What a [ChipSpan] marks in the title. */
enum class ChipKind { Verb, Deadline, Interval, Weekday }

/** The words the classifier acted on: [range] indexes the trimmed, truncated title. */
data class ChipSpan(val kind: ChipKind, val range: IntRange)

/** The card fields a user can touch before the parse lands. */
enum class IntakeField { Type, Title, CompletionCondition, Deadline, SubItems, Cadence }

/** A card as the user sees it. */
data class IntakeCard(
    val type: ItemType,
    val title: String,
    val completionCondition: String? = null,
    val deadline: Instant? = null,
    val subItems: List<String> = emptyList(),
    val cadence: Cadence? = null,
    val editedFields: Set<IntakeField> = emptySet(),
)
