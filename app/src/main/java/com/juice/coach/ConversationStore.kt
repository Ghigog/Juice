package com.juice.coach

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The last conversation with each character, kept in one JSON file on the device (e.g. in the app's files dir). */
class ConversationStore(private val file: File) {
    fun load(characterId: String): List<ChatMessage> =
        readAll()[characterId]?.jsonArray?.map {
            val m = it.jsonObject
            ChatMessage(m.getValue("role").jsonPrimitive.content, m.getValue("content").jsonPrimitive.content)
        }.orEmpty()

    fun save(characterId: String, messages: List<ChatMessage>) {
        val all = readAll().toMutableMap()
        all[characterId] = buildJsonArray {
            messages.forEach { m -> add(buildJsonObject { put("role", m.role); put("content", m.content) }) }
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(JsonObject(all).toString())
        check(tmp.renameTo(file)) { "Could not write ${file.name}" }
    }

    /** Forgets the saved conversation with [characterId]. */
    fun clear(characterId: String) {
        val all = readAll().toMutableMap()
        if (all.remove(characterId) == null) return
        val tmp = File(file.path + ".tmp")
        tmp.writeText(JsonObject(all).toString())
        check(tmp.renameTo(file)) { "Could not write ${file.name}" }
    }

    // A missing or corrupt file reads as no history.
    private fun readAll(): Map<String, kotlinx.serialization.json.JsonElement> =
        try {
            if (file.exists()) Json.parseToJsonElement(file.readText()).jsonObject else emptyMap()
        } catch (e: RuntimeException) {
            emptyMap()
        }
}
