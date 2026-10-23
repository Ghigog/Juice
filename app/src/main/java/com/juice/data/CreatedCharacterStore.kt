package com.juice.data

import android.content.Context
import android.content.SharedPreferences
import com.juice.core.character.Character
import com.juice.core.character.Face
import com.juice.core.character.Shape
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The characters the player created, kept as one JSON list in SharedPreferences. */
class CreatedCharacterStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("created_characters", Context.MODE_PRIVATE))

    // A missing or corrupt list reads as empty.
    fun list(): List<Character> =
        try {
            prefs.getString(KEY, null)?.let { json -> Json.parseToJsonElement(json).jsonArray.map { it.jsonObject.toCharacter() } }.orEmpty()
        } catch (e: RuntimeException) {
            emptyList()
        }

    fun add(character: Character) = write(list() + character)

    fun remove(id: String) = write(list().filterNot { it.id == id })

    private fun write(characters: List<Character>) {
        val json = buildJsonArray { characters.forEach { add(it.toJson()) } }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun Character.toJson() = buildJsonObject {
        put("id", id)
        put("name", name)
        put("personaPrompt", personaPrompt)
        put("unlockLevel", unlockLevel)
        put("checkInIntervalDays", checkInIntervalDays)
        put("openingLine", openingLine)
        put("mutterPitch", mutterPitch)
        put("eye", face.eye.name)
        put("mouth", face.mouth.name)
        put("hobby", hobby)
    }

    private fun kotlinx.serialization.json.JsonObject.toCharacter() = Character(
        id = getValue("id").jsonPrimitive.content,
        name = getValue("name").jsonPrimitive.content,
        personaPrompt = getValue("personaPrompt").jsonPrimitive.content,
        unlockLevel = getValue("unlockLevel").jsonPrimitive.content.toInt(),
        checkInIntervalDays = getValue("checkInIntervalDays").jsonPrimitive.content.toInt(),
        openingLine = getValue("openingLine").jsonPrimitive.content,
        mutterPitch = getValue("mutterPitch").jsonPrimitive.content.toFloat(),
        face = Face(Shape.valueOf(getValue("eye").jsonPrimitive.content), Shape.valueOf(getValue("mouth").jsonPrimitive.content)),
        hobby = getValue("hobby").jsonPrimitive.content,
    )

    private companion object {
        const val KEY = "characters"
    }
}
