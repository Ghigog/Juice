package com.juice.data

import com.juice.core.character.Expression
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** One turn of a coach conversation; [role] is "user" or "assistant". */
data class ChatMessage(val role: String, val content: String, val expression: Expression = Expression.NEUTRAL)

/** The last conversation with each character, kept in one JSON file on the device (e.g. in the app's files dir). */
class ConversationStore(private val file: File) {
    private companion object {
        const val VERSION = 1
    }

    fun load(characterId: String): List<ChatMessage> =
        readAll()[characterId]?.jsonArray?.map {
            val m = it.jsonObject
            val saved = m["expression"]?.jsonPrimitive?.content
            ChatMessage(
                m.getValue("role").jsonPrimitive.content,
                m.getValue("content").jsonPrimitive.content,
                Expression.named(saved) ?: Expression.NEUTRAL,
            )
        }.orEmpty()

    fun save(characterId: String, messages: List<ChatMessage>) {
        val all = readAll().toMutableMap()
        all[characterId] = buildJsonArray {
            messages.forEach { m -> add(buildJsonObject { put("role", m.role); put("content", m.content); put("expression", m.expression.name.lowercase()) }) }
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(JsonObject(all + ("version" to JsonPrimitive(VERSION))).toString())
        check(tmp.renameTo(file)) { "Could not write ${file.name}" }
    }

    /** Forgets the saved conversation with [characterId]. */
    fun clear(characterId: String) {
        val all = readAll().toMutableMap()
        if (all.remove(characterId) == null) return
        val tmp = File(file.path + ".tmp")
        tmp.writeText(JsonObject(all + ("version" to JsonPrimitive(VERSION))).toString())
        check(tmp.renameTo(file)) { "Could not write ${file.name}" }
    }

    // A missing or corrupt file reads as no history.
    private fun readAll(): Map<String, kotlinx.serialization.json.JsonElement> =
        try {
            if (!file.exists()) {
                emptyMap()
            } else {
                val root = Json.parseToJsonElement(file.readText()).jsonObject
                // No version is the previous release's format; a newer version than we know reads as empty.
                val version = (root["version"] as? JsonPrimitive)?.int ?: 1
                if (version == VERSION) root - "version" else emptyMap()
            }
        } catch (e: RuntimeException) {
            emptyMap()
        }
}
