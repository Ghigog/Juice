package com.juice.core.character

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

sealed interface GeneratedPersonaResult {
    data class Valid(val persona: GeneratedPersona) : GeneratedPersonaResult
    data class Rejected(val reason: String) : GeneratedPersonaResult
}

/** A persona written by the model for a created character. [personaPrompt] always ends with the task-proposal contract. */
data class GeneratedPersona(val name: String, val personality: String, val personaPrompt: String) {
    companion object {
        const val MAX_NAME_LENGTH = 60
        const val MAX_PERSONALITY_LENGTH = 300
        const val MAX_PROMPT_LENGTH = 4000

        /** The task-proposal instructions every character's prompt carries. */
        val PROPOSAL_CONTRACT = """
            When you have enough, end by proposing tasks as JSON only, with no emotion tag, in this shape:
            {"tasks":[{"action":"add","title":"Walk 20 minutes","cadence":{"days":["mon","wed","fri"],"time":"07:30"}},
            {"action":"change","id":"<existing id>","title":"...","cadence":{"days":"daily"}},
            {"action":"remove","id":"<existing id>"}]}
            Use "daily" or a list of three-letter days (mon..sun) for days; time is optional. Propose at most 10 tasks.
        """.trimIndent()

        /** Parses and checks the model's `{name, personality, personaPrompt}`; anything blank, overlong or malformed is rejected. */
        fun parse(json: String): GeneratedPersonaResult =
            try {
                val obj = Json.parseToJsonElement(json) as? JsonObject ?: throw IllegalArgumentException("not an object")
                val name = field(obj, "name", MAX_NAME_LENGTH)
                val personality = field(obj, "personality", MAX_PERSONALITY_LENGTH)
                val prompt = field(obj, "personaPrompt", MAX_PROMPT_LENGTH)
                GeneratedPersonaResult.Valid(GeneratedPersona(name, personality, "$prompt\n\n$PROPOSAL_CONTRACT"))
            } catch (e: IllegalArgumentException) {
                // Json parse errors (SerializationException) extend IllegalArgumentException too.
                GeneratedPersonaResult.Rejected(e.message ?: "invalid persona")
            }

        /** The persona a caller uses when the model fails or its output is rejected. */
        fun fallbackPersona(hobby: String): GeneratedPersona {
            val topic = hobby.trim()
            return GeneratedPersona(
                name = "Coach $topic".take(MAX_NAME_LENGTH),
                personality = "A friendly, encouraging coach for $topic.".take(MAX_PERSONALITY_LENGTH),
                personaPrompt = "You are a friendly, encouraging coach inside the Juice app, helping the player with $topic. " +
                    "Ask about their current routine and constraints, keep replies short and conversational, " +
                    "and never give medical advice.\n\n$PROPOSAL_CONTRACT",
            )
        }

        private fun field(obj: JsonObject, key: String, max: Int): String {
            val value = ((obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw IllegalArgumentException("missing $key")).trim()
            require(value.isNotEmpty()) { "blank $key" }
            require(value.length <= max) { "$key too long" }
            return value
        }
    }
}

/** Turns a persona into a [Character] that is met at once, with look and voice taken from [id] so they stay stable. */
fun GeneratedPersona.toCharacter(id: String, hobby: String): Character {
    val shapes = Shape.entries
    val h = id.hashCode().toLong() and 0x7fffffff
    return Character(
        id = id,
        name = name,
        personaPrompt = personaPrompt,
        unlockLevel = Characters.CREATED_UNLOCK_LEVEL,
        checkInIntervalDays = Characters.CREATED_CHECK_IN_INTERVAL_DAYS,
        openingLine = "Hi, I'm $name. $personality What are we working on?",
        mutterPitch = 0.7f + (h % 13) * 0.1f,
        face = Face(eye = shapes[(h % shapes.size).toInt()], mouth = shapes[((h / shapes.size) % shapes.size).toInt()]),
        hobby = hobby,
    )
}
