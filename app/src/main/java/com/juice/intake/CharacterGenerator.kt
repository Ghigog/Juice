package com.juice.intake

import com.juice.core.character.Character
import com.juice.core.character.GeneratedPersona
import com.juice.core.character.GeneratedPersonaResult
import com.juice.core.character.toCharacter
import java.io.IOException
import java.util.UUID

sealed interface GenerationResult {
    data class Success(val character: Character) : GenerationResult
    data class Failure(val message: String) : GenerationResult
}

/**
 * Turns a hobby into a [Character] with the model's persona. [llm] returns the model's JSON for a hobby and throws [IOException] on failure,
 * which is an error and yields no character. Output that is invalid although the call succeeded falls back to [GeneratedPersona.fallbackPersona].
 */
class CharacterGenerator(
    private val llm: (hobby: String) -> String,
    private val newId: () -> String = { "created-${UUID.randomUUID()}" },
) {
    fun generate(hobby: String): GenerationResult {
        val topic = hobby.trim()
        if (topic.isEmpty()) return GenerationResult.Failure("Hobby is empty")
        val json = try {
            llm(topic)
        } catch (e: IOException) {
            return GenerationResult.Failure(GroqClient.reason(e))
        }
        val persona = (GeneratedPersona.parse(json) as? GeneratedPersonaResult.Valid)?.persona ?: GeneratedPersona.fallbackPersona(topic)
        return GenerationResult.Success(persona.toCharacter(newId(), topic))
    }
}
