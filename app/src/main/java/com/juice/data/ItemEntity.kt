package com.juice.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.juice.core.intake.IntakeField
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.core.model.Step
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val deadlineMillis: Long?,
    val status: String,
    val editedFields: String = "",
    val completionCondition: String? = null,
    /** The Project's steps as a JSON array of {id, title, done}. */
    val steps: String = "[]",
)

fun Item.toEntity() = ItemEntity(
    id, type.name, title, deadline?.toEpochMilli(), status.name,
    editedFields.joinToString(",") { it.name }, completionCondition, steps.encode(),
)

fun ItemEntity.toItem() = Item(
    id = id,
    type = ItemType.valueOf(type),
    title = title,
    deadline = deadlineMillis?.let(Instant::ofEpochMilli),
    completionCondition = completionCondition,
    status = ItemStatus.valueOf(status),
    editedFields = editedFields.split(",").filter { it.isNotEmpty() }.map(IntakeField::valueOf).toSet(),
    steps = steps.decodeSteps(),
)

internal fun List<Step>.encode(): String = JSONArray().also { array ->
    forEach { array.put(JSONObject().put("id", it.id).put("title", it.title).put("done", it.done)) }
}.toString()

internal fun String.decodeSteps(): List<Step> = JSONArray(this).let { array ->
    (0 until array.length()).map { array.getJSONObject(it).let { o -> Step(o.getString("id"), o.getString("title"), o.getBoolean("done")) } }
}
