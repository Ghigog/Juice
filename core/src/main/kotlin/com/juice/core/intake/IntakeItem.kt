package com.juice.core.intake

import com.juice.core.routine.Cadence
import java.time.Instant

enum class ItemType { Note, Task, Goal, Project, Routine }

/** A validated parser result. Only [IntakeValidator] and [RulesFallback] build these. */
data class ParsedItem(
    val type: ItemType,
    val title: String,
    val completionCondition: String? = null,
    val deadline: Instant? = null,
    val subItems: List<String> = emptyList(),
    val cadence: Cadence? = null,
)

/** The card fields a user can touch before the parse lands. */
enum class IntakeField { Type, Title, CompletionCondition, Deadline, SubItems }

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
