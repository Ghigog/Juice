package com.juice.memory

import com.juice.core.memory.Memory
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The characters' notes about the player, kept in one JSON file on the device (e.g. in the app's files dir). */
class MemoryStore(private val file: File) {
    // A missing or corrupt file reads as empty memory.
    fun load(): Memory =
        try {
            if (!file.exists()) {
                Memory()
            } else {
                val root = Json.parseToJsonElement(file.readText()).jsonObject
                Memory(
                    shared = notes(root.getValue("shared")),
                    characters = root.getValue("characters").jsonObject.mapValues { notes(it.value) },
                )
            }
        } catch (e: RuntimeException) {
            Memory()
        }

    fun save(memory: Memory) {
        val json = buildJsonObject {
            put("shared", buildJsonArray { memory.shared.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            put("characters", buildJsonObject {
                memory.characters.forEach { (id, notes) ->
                    put(id, buildJsonArray { notes.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                }
            })
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.toString())
        check(tmp.renameTo(file)) { "Could not write ${file.name}" }
    }

    /** Forgets every note, shared and per character. */
    fun clearAll() = save(Memory())

    /** Forgets the notes of [id]; shared notes stay. */
    fun deleteCharacter(id: String) {
        val memory = load()
        if (id in memory.characters) save(memory.copy(characters = memory.characters - id))
    }

    private fun notes(element: kotlinx.serialization.json.JsonElement): List<String> =
        element.jsonArray.map { it.jsonPrimitive.content }
}
