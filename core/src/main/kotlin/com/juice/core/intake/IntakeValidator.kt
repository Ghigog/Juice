package com.juice.core.intake

import java.time.Duration
import java.time.Instant
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
        return ParsedItem(type, title, condition, deadline(obj, now, onRejected), subItems)
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
    private fun deadline(obj: JsonObject, now: Instant, onRejected: (String) -> Unit): Instant? {
        val text = lenient("deadline", onRejected, null) { optionalString(obj, "deadline") } ?: return null
        val at = try {
            OffsetDateTime.parse(text).toInstant()
        } catch (e: java.time.DateTimeException) {
            onRejected("dropped deadline: unparsable \"$text\"")
            return null
        }
        val inRange = at.isAfter(now) && !at.isAfter(now.plus(MAX_DEADLINE_DISTANCE))
        if (!inRange) onRejected("dropped deadline: out of range $text")
        return at.takeIf { inRange }
    }
}
