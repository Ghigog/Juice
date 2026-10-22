package com.juice.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.juice.core.intake.IntakeField
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import java.time.Instant

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val deadlineMillis: Long?,
    val status: String,
    val parentId: String?,
    val editedFields: String = "",
    val completionCondition: String? = null,
    /** Place in the user's chosen order of cards; lower comes first. */
    val position: Int = 0,
)

fun Item.toEntity(position: Int = 0) = ItemEntity(
    id, type.name, title, deadline?.toEpochMilli(), status.name, parentId,
    editedFields.joinToString(",") { it.name }, completionCondition, position,
)

fun ItemEntity.toItem() = Item(
    id = id,
    type = ItemType.valueOf(type),
    title = title,
    deadline = deadlineMillis?.let(Instant::ofEpochMilli),
    completionCondition = completionCondition,
    status = ItemStatus.valueOf(status),
    parentId = parentId,
    editedFields = editedFields.split(",").filter { it.isNotEmpty() }.map(IntakeField::valueOf).toSet(),
)
