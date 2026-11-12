package com.juice.intake

import com.juice.core.character.Character
import com.juice.core.character.GeneratedPersona
import com.juice.core.character.GeneratedPersonaResult
import com.juice.core.character.toCharacter
import com.juice.data.GenerationResult
import com.juice.data.PersonaGenerator
import com.juice.llm.GroqClient
import java.io.IOException
import java.util.UUID

/**
 * Turns a hobby into a [Character] with the model's persona. [llm] returns the model's JSON for a hobby and throws [IOException] on failure,
 * which is an error and yields no character. It refuses without calling [llm] when [onDeviceOnly] is on or the cloud is not [cloudAvailable]. Output that is invalid although the call succeeded falls back to [GeneratedPersona.fallbackPersona].
 */
class CharacterGenerator(
    private val llm: suspend (hobby: String) -> String,
    private val newId: () -> String = { "created-${UUID.randomUUID()}" },
    private val onDeviceOnly: suspend () -> Boolean = { false },
    private val cloudAvailable: () -> Boolean = { true },
) : PersonaGenerator {
    override suspend fun generate(hobby: String): GenerationResult {
        val topic = hobby.trim()
        if (topic.isEmpty()) return GenerationResult.Failure("Hobby is empty")
        if (!cloudAvailable() || onDeviceOnly()) return GenerationResult.Failure(NEEDS_CLOUD)
        val json = try {
            llm(topic)
        } catch (e: IOException) {
            return GenerationResult.Failure(GroqClient.reason(e))
        }
        val persona = (GeneratedPersona.parse(json) as? GeneratedPersonaResult.Valid)?.persona ?: GeneratedPersona.fallbackPersona(topic)
        return GenerationResult.Success(persona.toCharacter(newId(), topic))
    }

    companion object {
        const val NEEDS_CLOUD = "Needs cloud access: turn off On-device only"
    }
}
