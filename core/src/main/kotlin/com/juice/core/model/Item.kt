package com.juice.core.model

import com.juice.core.intake.ItemType
import java.time.Instant

enum class ItemStatus { Active, Completed, Archived }

/** A saved note, task, goal or project. [parentId] links a sub-item to its parent. */
data class Item(
    val id: String,
    val type: ItemType,
    val title: String,
    val deadline: Instant? = null,
    val status: ItemStatus = ItemStatus.Active,
    val parentId: String? = null,
)
