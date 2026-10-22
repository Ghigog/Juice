package com.juice.core.model

import com.juice.core.intake.IntakeField
import com.juice.core.intake.ItemType
import com.juice.core.routine.Cadence
import java.time.Instant
import java.time.LocalDate

enum class ItemStatus { Active, Completed, Archived }

/** One step of a Project, kept inside the Project's card. */
data class Step(
    val id: String,
    val title: String,
    val done: Boolean = false,
    /** When the step was ticked; null for legacy rows and open steps. */
    val doneAt: Instant? = null,
)

/** A saved note, task, goal or project. A Project keeps its [steps] in order; [editedFields] are the ones the user changed, which a late parse must not overwrite. */
data class Item(
    val id: String,
    val type: ItemType,
    val title: String,
    val deadline: Instant? = null,
    val status: ItemStatus = ItemStatus.Active,
    val editedFields: Set<IntakeField> = emptySet(),
    /** What "done" means, when the parser found one (e.g. "Dryer ordered"). */
    val completionCondition: String? = null,
    val steps: List<Step> = emptyList(),
    /** How often a Routine repeats; null for every other type. */
    val cadence: Cadence? = null,
    /** The next day a Routine is due. */
    val nextDueOn: LocalDate? = null,
    /** The coach this card belongs to; null for the player's own cards. */
    val characterId: String? = null,
    /** When the item was saved; null for legacy rows. */
    val createdAt: Instant? = null,
    /** When the item was completed; null while it is open or after an undo. */
    val completedAt: Instant? = null,
)
