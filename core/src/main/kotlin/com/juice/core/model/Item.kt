package com.juice.core.model

import com.juice.core.intake.IntakeField
import com.juice.core.intake.ItemType
import java.time.Instant

enum class ItemStatus { Active, Completed, Archived }

/** A saved note, task, goal or project. [parentId] links a sub-item to its parent;
 * [editedFields] are the ones the user changed, which a late parse must not overwrite. */
data class Item(
    val id: String,
    val type: ItemType,
    val title: String,
    val deadline: Instant? = null,
    val status: ItemStatus = ItemStatus.Active,
    val parentId: String? = null,
    val editedFields: Set<IntakeField> = emptySet(),
)
