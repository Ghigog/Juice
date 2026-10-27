package com.juice.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.juice.core.intake.IntakeField
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.core.model.Step
import com.juice.core.routine.Cadence
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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
    /** The Project's steps as a JSON array of {id, title, done, doneAt?}. */
    val steps: String = "[]",
    /** Place in the user's chosen order of cards; lower comes first. */
    val position: Int = 0,
    /** A Routine's cadence, e.g. "SUN,THU;09:00" (days, then an optional time). */
    val cadence: String? = null,
    /** The epoch day a Routine is next due. */
    val nextDueOn: Long? = null,
    /** When the item was first saved; null for rows from before it was recorded. */
    val createdAtMillis: Long? = null,
    /** When the item was completed (a Routine: its latest occurrence); null while open or after an undo. */
    val completedAtMillis: Long? = null,
    /** The character (coach) this card belongs to; null for the player's own cards. */
    val characterId: String? = null,
    /** When the item was starred; null while unstarred. */
    val starredAtMillis: Long? = null,
)

fun Item.toEntity(position: Int = 0) = ItemEntity(
    id, type.name, title, deadline?.toEpochMilli(), status.name,
    editedFields.joinToString(",") { it.name }, completionCondition, steps.encode(), position,
    cadence?.encode(), nextDueOn?.toEpochDay(), createdAt?.toEpochMilli(), completedAt?.toEpochMilli(),
    characterId, starredAt?.toEpochMilli(),
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
    cadence = cadence?.decodeCadence(),
    nextDueOn = nextDueOn?.let(LocalDate::ofEpochDay),
    createdAt = createdAtMillis?.let(Instant::ofEpochMilli),
    completedAt = completedAtMillis?.let(Instant::ofEpochMilli),
    characterId = characterId,
    starredAt = starredAtMillis?.let(Instant::ofEpochMilli),
)

internal fun List<Step>.encode(): String = JSONArray().also { array ->
    forEach { step ->
        val json = JSONObject().put("id", step.id).put("title", step.title).put("done", step.done)
        step.doneAt?.let { json.put("doneAt", it.toEpochMilli()) }
        array.put(json)
    }
}.toString()

internal fun String.decodeSteps(): List<Step> = JSONArray(this).let { array ->
    (0 until array.length()).map { array.getJSONObject(it).let { o -> Step(o.getString("id"), o.getString("title"), o.getBoolean("done"), if (o.has("doneAt")) Instant.ofEpochMilli(o.getLong("doneAt")) else null) } }
}

internal fun Cadence.encode(): String =
    (if (intervalWeeks > 1) "${intervalWeeks}W:" else "") +
        days.sorted().joinToString(",") { it.name.take(3) } + (time?.let { ";$it" } ?: "")

internal fun String.decodeCadence(): Cadence {
    val daysPart = substringBefore(';')
    val intervalWeeks = if (':' in daysPart) daysPart.substringBefore(':').removeSuffix("W").toInt() else 1
    val days = daysPart.substringAfter(':').split(",").map { abbreviation -> DayOfWeek.entries.first { it.name.take(3) == abbreviation } }
    return Cadence(days.toSet(), if (';' in this) LocalTime.parse(substringAfter(';')) else null, intervalWeeks)
}
