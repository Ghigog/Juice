package com.juice.core.memory

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Small notes the characters keep: [shared] ones every character sees, [characters] ones only that character sees. */
data class Memory(
    val shared: List<String> = emptyList(),
    val characters: Map<String, List<String>> = emptyMap(),
)

object MemoryLimits {
    const val MAX_NOTES = 10
    const val MAX_NOTE_LENGTH = 140

    /** Blank and over-long notes are dropped, then only the first [MAX_NOTES] are kept. */
    fun cap(notes: List<String>): List<String> =
        notes.map { it.trim() }.filter { it.isNotEmpty() && it.length <= MAX_NOTE_LENGTH }.take(MAX_NOTES)
}

sealed interface MemoryUpdateResult {
    data class Valid(val memory: Memory) : MemoryUpdateResult
    data class Rejected(val reason: String) : MemoryUpdateResult
}

object MemoryUpdate {
    /** All-or-nothing: bad shape or an unknown character id rejects the whole update. Notes are capped by [MemoryLimits]. */
    fun validate(json: String, knownCharacterIds: Set<String>): MemoryUpdateResult =
        try {
            MemoryUpdateResult.Valid(parse(json, knownCharacterIds))
        } catch (e: IllegalArgumentException) {
            MemoryUpdateResult.Rejected(e.message ?: "invalid memory update")
        }

    // Json parse errors (SerializationException) and our own checks all extend IllegalArgumentException.
    private fun parse(json: String, knownCharacterIds: Set<String>): Memory {
        val obj = Json.parseToJsonElement(json) as? JsonObject ?: throw IllegalArgumentException("not an object")
        val shared = notes(obj["shared"], "shared")
        val characters = (obj["characters"] as? JsonObject ?: throw IllegalArgumentException("missing characters"))
            .entries.associate { (id, value) ->
                require(id in knownCharacterIds) { "unknown character" }
                id to notes(value, id)
            }
        return Memory(shared, characters)
    }

    private fun notes(element: Any?, name: String): List<String> {
        val array = element as? JsonArray ?: throw IllegalArgumentException("missing $name notes")
        return MemoryLimits.cap(array.map {
            (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: throw IllegalArgumentException("$name note is not text")
        })
    }
}

object MemoryPrompt {
    /** The shared notes plus those of [characterId] only; empty when there are none. */
    fun promptSection(memory: Memory, characterId: String): String {
        val own = memory.characters[characterId].orEmpty()
        if (memory.shared.isEmpty() && own.isEmpty()) return ""
        return buildList {
            add("What you remember about the player:")
            if (memory.shared.isNotEmpty()) {
                add("Shared notes:")
                memory.shared.forEach { add("- $it") }
            }
            if (own.isNotEmpty()) {
                add("Your own notes:")
                own.forEach { add("- $it") }
            }
        }.joinToString("\n")
    }

    /** Asks the model to rewrite the notes after [conversation], given the [current] ones. */
    fun updatePrompt(current: Memory, conversation: String): String = """
        Update the notes you keep about the player, using the conversation below.
        "shared" notes are seen by every character. Notes under a character id are seen only by that character.
        Keep each note short, at most ${MemoryLimits.MAX_NOTE_LENGTH} characters, and at most ${MemoryLimits.MAX_NOTES} notes per list.
        Keep what still matters, drop what is stale. Never store secrets, passwords or API keys.
        Reply with JSON only, in this shape: {"shared":["..."],"characters":{"<character id>":["..."]}}

        Current notes:
        ${current.shared.joinToString("\n") { "shared: $it" }}
        ${current.characters.entries.joinToString("\n") { (id, notes) -> notes.joinToString("\n") { "$id: $it" } }}

        Conversation:
        $conversation
    """.trimIndent()
}
