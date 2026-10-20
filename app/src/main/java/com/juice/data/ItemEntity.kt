package com.juice.data

import androidx.room.Entity
import androidx.room.PrimaryKey
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
)

fun Item.toEntity() = ItemEntity(id, type.name, title, deadline?.toEpochMilli(), status.name, parentId)

fun ItemEntity.toItem() = Item(
    id = id,
    type = ItemType.valueOf(type),
    title = title,
    deadline = deadlineMillis?.let(Instant::ofEpochMilli),
    status = ItemStatus.valueOf(status),
    parentId = parentId,
)
