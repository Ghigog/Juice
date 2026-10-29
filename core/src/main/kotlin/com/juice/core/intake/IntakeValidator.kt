package com.juice.core.intake

import com.juice.core.routine.Cadence
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object IntakeValidator {
    const val MAX_SUB_ITEMS = 10
    const val MAX_TITLE_LENGTH = 120
    private val MAX_DEADLINE_DISTANCE: Duration = Duration.ofDays(365)

    /**
     * Validates the parser's [json]. A bad deadline, sub_items or completion_condition is dropped on its own;
     * JSON that is not an object, or an unusable type or title, gives a plain Note titled [rawText].
     * Every drop and failure is reported to [onRejected] with its reason.
     */
    fun validate(
        json: String,
        rawText: String,
        now: Instant,
        onRejected: (reason: String) -> Unit = {},
    ): ParsedItem =
        try {
            parse(json, now, onRejected)
        } catch (e: IllegalArgumentException) {
            onRejected("note fallback: ${e.message}")
            note(rawText)
        }

    fun note(rawText: String) = ParsedItem(ItemType.Note, rawText.trim().take(MAX_TITLE_LENGTH))

    // Json parse errors (SerializationException) and our own checks all extend IllegalArgumentException.
    private fun parse(json: String, now: Instant, onRejected: (String) -> Unit): ParsedItem {
        val obj = Json.parseToJsonElement(json) as? JsonObject ?: throw IllegalArgumentException("not an object")
        val type = ItemType.entries.firstOrNull { it.name == string(obj, "type") }
            ?: throw IllegalArgumentException("bad type")
        val title = string(obj, "title").trim().take(MAX_TITLE_LENGTH)
        require(title.isNotEmpty()) { "empty title" }
        val subItems = lenient("sub_items", onRejected, emptyList()) { subItems(obj) }
        val condition = lenient("completion_condition", onRejected, null) { optionalString(obj, "completion_condition") }
        if (type != ItemType.Routine) {
            if (obj["cadence"].let { it != null && it != JsonNull }) onRejected("dropped cadence: not a Routine")
            val deadline = deadline(obj, now, onRejected)
            val (notifyAt, notifyText) = notification(obj, now, deadline, onRejected)
            return ParsedItem(type, title, condition, deadline, subItems, notifyAt = notifyAt, notifyText = notifyText)
        }
        val cadence = lenient("cadence", onRejected, null) { cadence(obj) }
        if (cadence == null) {
            onRejected("downgraded Routine to Task: no valid cadence")
            val deadline = deadline(obj, now, onRejected)
            val (notifyAt, notifyText) = notification(obj, now, deadline, onRejected)
            return ParsedItem(ItemType.Task, title, condition, deadline, subItems, notifyAt = notifyAt, notifyText = notifyText)
        }
        if (obj["notify_at"].let { it != null && it != JsonNull }) onRejected("dropped notify_at: a Routine's schedule derives from its due date")
        // A Routine's deadline derives from its due date.
        return ParsedItem(type, title, condition, null, subItems, cadence)
    }

    internal fun cadence(obj: JsonObject): Cadence {
        val raw = obj["cadence"] as? JsonObject ?: throw IllegalArgumentException("missing cadence")
        val days = when (val d = raw["days"]) {
            JsonPrimitive("daily") -> DayOfWeek.entries.toSet()
            is JsonArray -> d.map { day ->
                val name = (day as? JsonPrimitive)?.takeIf { it.isString }?.content
                DayOfWeek.entries.firstOrNull { it.name.take(3) == name?.uppercase() } ?: throw IllegalArgumentException("bad day")
            }.toSet()
            else -> throw IllegalArgumentException("bad days")
        }
        require(days.isNotEmpty()) { "no days" }
        val time = when (val t = raw["time"]) {
            null, JsonNull -> null
            is JsonPrimitive -> try {
                LocalTime.parse(t.takeIf { it.isString }?.content ?: throw IllegalArgumentException("bad time"))
            } catch (e: java.time.DateTimeException) {
                throw IllegalArgumentException("bad time")
            }
            else -> throw IllegalArgumentException("bad time")
        }
        return Cadence(days, time)
    }

    private fun <T> lenient(field: String, onRejected: (String) -> Unit, default: T, read: () -> T): T =
        try {
            read()
        } catch (e: IllegalArgumentException) {
            onRejected("dropped $field: ${e.message}")
            default
        }

    private fun subItems(obj: JsonObject): List<String> {
        val items = when (val raw = obj["sub_items"]) {
            null, JsonNull -> emptyList()
            is JsonArray -> raw.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.trim()?.take(MAX_TITLE_LENGTH) ?: throw IllegalArgumentException("bad sub_item") }
            else -> throw IllegalArgumentException("bad sub_items")
        }
        require(items.size <= MAX_SUB_ITEMS) { "too many sub_items" }
        return items
    }

    private fun string(obj: JsonObject, key: String): String =
        (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw IllegalArgumentException("missing $key")

    private fun optionalString(obj: JsonObject, key: String): String? =
        when (obj[key]) {
            null, JsonNull -> null
            else -> string(obj, key).trim().take(MAX_TITLE_LENGTH).ifEmpty { null }
        }

    /** A deadline that is unparsable, past, or over a year out is dropped to null. */
    private fun deadline(obj: JsonObject, now: Instant, onRejected: (String) -> Unit): Instant? =
        instant(obj, "deadline", now, onRejected)

    /**
     * The reminder time and text: both are kept, or both dropped. The time must also be no later than the [deadline]
     * when there is one, and the text must not be blank.
     */
    private fun notification(obj: JsonObject, now: Instant, deadline: Instant?, onRejected: (String) -> Unit): Pair<Instant?, String?> {
        val text = lenient("notify_text", onRejected, null) { optionalString(obj, "notify_text") }
        val at = instant(obj, "notify_at", now, onRejected)
        if (at == null) {
            if (text != null) onRejected("dropped notify_text: no valid notify_at")
            return null to null
        }
        if (deadline != null && at.isAfter(deadline)) {
            onRejected("dropped notify_at: after the deadline")
            return null to null
        }
        if (text == null) {
            onRejected("dropped notify_at: no notify_text")
            return null to null
        }
        return at to text
    }

    private fun instant(obj: JsonObject, key: String, now: Instant, onRejected: (String) -> Unit): Instant? {
        val text = lenient(key, onRejected, null) { optionalString(obj, key) } ?: return null
        val at = try {
            OffsetDateTime.parse(text).toInstant()
        } catch (e: java.time.DateTimeException) {
            onRejected("dropped $key: unparsable \"$text\"")
            return null
        }
        val inRange = at.isAfter(now) && !at.isAfter(now.plus(MAX_DEADLINE_DISTANCE))
        if (!inRange) onRejected("dropped $key: out of range $text")
        return at.takeIf { inRange }
    }
}
