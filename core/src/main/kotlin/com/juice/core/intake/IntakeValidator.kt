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

    /** Validates the parser's [json]. Any failure gives a plain Note titled [rawText]. */
    fun validate(json: String, rawText: String, now: Instant): ParsedItem =
        try {
            parse(json, now)
        } catch (e: IllegalArgumentException) {
            note(rawText)
        }

    fun note(rawText: String) = ParsedItem(ItemType.Note, rawText.trim().take(MAX_TITLE_LENGTH))

    // Json parse errors (SerializationException) and our own checks all extend IllegalArgumentException.
    private fun parse(json: String, now: Instant): ParsedItem {
        val obj = Json.parseToJsonElement(json) as? JsonObject ?: throw IllegalArgumentException("not an object")
        val type = ItemType.entries.firstOrNull { it.name == string(obj, "type") }
            ?: throw IllegalArgumentException("bad type")
        val title = string(obj, "title").trim().take(MAX_TITLE_LENGTH)
        require(title.isNotEmpty()) { "empty title" }
        val subItems = when (val raw = obj["sub_items"]) {
            null, JsonNull -> emptyList()
            is JsonArray -> raw.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.trim()?.take(MAX_TITLE_LENGTH) ?: throw IllegalArgumentException("bad sub_item") }
            else -> throw IllegalArgumentException("bad sub_items")
        }
        require(subItems.size <= MAX_SUB_ITEMS) { "too many sub_items" }
        return ParsedItem(type, title, optionalString(obj, "completion_condition"), deadline(obj, now), subItems)
    }

    private fun string(obj: JsonObject, key: String): String =
        (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw IllegalArgumentException("missing $key")

    private fun optionalString(obj: JsonObject, key: String): String? =
        when (obj[key]) {
            null, JsonNull -> null
            else -> string(obj, key).trim().take(MAX_TITLE_LENGTH).ifEmpty { null }
        }

    /** A deadline that is unparsable, past, or over a year out is dropped to null. */
    private fun deadline(obj: JsonObject, now: Instant): Instant? {
        val text = optionalString(obj, "deadline") ?: return null
        val at = try {
            OffsetDateTime.parse(text).toInstant()
        } catch (e: java.time.DateTimeException) {
            return null
        }
        return at.takeIf { it.isAfter(now) && !it.isAfter(now.plus(MAX_DEADLINE_DISTANCE)) }
    }
}
