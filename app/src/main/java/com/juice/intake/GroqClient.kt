package com.juice.intake

import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Calls Groq chat completions in JSON mode. Throws [IOException] on any transport or HTTP failure. */
class GroqClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.groq.com/openai/v1",
    private val model: String = GroqTransport.MODEL,
) {
    /** The HTTP transport this client talks through; the coach shares it. */
    val transport = GroqTransport(apiKey, baseUrl)

    /** Returns the model's JSON text for [rawText], with [now] injected into the system prompt. */
    fun parse(rawText: String, now: ZonedDateTime): String = complete(systemPrompt(now), rawText)

    /** Returns the model's JSON persona (`name`, `personality`, `personaPrompt`) for a coach about [hobby]. */
    fun generatePersona(hobby: String): String = complete(PERSONA_PROMPT, hobby)

    /** One GET to /models (no tokens spent) to learn whether [apiKey] works. Never logs the key or the body. */
    fun checkKey(): KeyCheck = transport.checkKey(TIMEOUT_MS)

    private fun complete(system: String, user: String): String {
        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0)
            put("response_format", buildJsonObject { put("type", "json_object") })
            put("messages", buildJsonArray {
                add(message("system", system))
                add(message("user", user))
            })
        }
        return transport.chat(body.toString(), TIMEOUT_MS, retry = false)
    }

    private fun message(role: String, content: String): JsonObject =
        buildJsonObject { put("role", role); put("content", JsonPrimitive(content)) }

    companion object {
        private const val TIMEOUT_MS = 10_000

        /** Asks for the persona of a coach about the hobby in the user message. */
        val PERSONA_PROMPT = """
            You create a coach character for the Juice app. The user message is a hobby or skill the player wants to get better at.
            Output only JSON: {"name": "string", "personality": "string", "personaPrompt": "string"}.
            - "name": a short, memorable character name, at most 60 characters.
            - "personality": one or two sentences describing how the coach comes across, at most 300 characters.
            - "personaPrompt": a system prompt, in the second person, telling the coach who it is, how it talks (a warm, human voice with a distinct manner of speaking, such as a short quirk or favourite turn of phrase, never a neutral coach tone), and to interview the player, one short question at a time, about their current routine, health issues and constraints before proposing tasks. Do not describe any output format.
        """.trimIndent()

        /** The design doc §7.4 prompt, with date, time and timezone injected. */
        fun systemPrompt(now: ZonedDateTime): String = """
            Today is ${now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${now.format(DateTimeFormatter.ISO_LOCAL_DATE)}, current time ${now.format(DateTimeFormatter.ofPattern("HH:mm"))}, timezone ${now.zone.id} (UTC offset ${now.offset.id}).
            You are the Juice Intake Parser. Output only JSON matching the schema.

            Types:
            - "Note": plain thoughts or facts.
            - "Task": an actionable item.
            - "Goal": a task with a specific date, time, or target metric.
            - "Routine": a habit that repeats on a cadence ("every day", "every Sunday", "weekly on Friday"). A recurring phrase is a Routine, not a Goal.
            - "List": a series of tasks with no deadline, listed in sub_items.
            - "Project": an item with multiple sub-steps, a deadline or an end goal.

            Schema:
            {
              "type": "Note" | "Task" | "List" | "Goal" | "Project" | "Routine",
              "title": "string",
              "completion_condition": "string | null",
              "deadline": "ISO-8601 string with offset | null",
              "sub_items": ["string"] | null,
              "notify_at": "ISO-8601 with offset | null",
              "notify_text": "string | null",
              "cadence": {"days": ["MON"|"TUE"|"WED"|"THU"|"FRI"|"SAT"|"SUN", ...] | "daily", "time": "HH:mm | null"} | null
            }

            Rules:
            - The app decides the type from the wording, so concentrate on "title", "sub_items" and "deadline".
            - Never invent steps: "sub_items" holds only steps the person actually wrote, otherwise null.
            - "cadence" is only for Routine; use null for every other type. A Routine has a null deadline.
            - A weekday name ("Saturday") means its next occurrence after today, never an earlier day; "tomorrow" is the day after today.
            - "notify_at" and "notify_text" are only for items with a deadline or a time. Choose "notify_at" by the deadline's time of day: before 10:00, 20:00 the evening before; 10:00 to before 17:00, 09:00 that morning; 17:00 or later, 2 hours before; a date with no time, 09:00 that day. It must be after now and never after the deadline. "notify_text" is a short reminder message.
            - Leave "notify_at" and "notify_text" null when no reminder is useful, for every Routine and for plain Notes.
        """.trimIndent()
    }
}

/** What [GroqClient.checkKey] learned: the key works, Groq refused it, or Groq could not be asked. */
enum class KeyCheck { Works, Rejected, Unreachable }

/** Groq answered with a non-2xx [status]; [code] and [detail] are Groq's own, [retryAfterMs] its Retry-After. */
class GroqHttpException(
    val status: Int,
    val code: String?,
    val detail: String,
    val retryAfterMs: Long? = null,
) : IOException("Groq HTTP $status${if (detail.isBlank()) "" else ": ${detail.take(300)}"}")

/** Groq answered 2xx but the body was not a chat completion envelope; asking again will not help. */
class GroqEnvelopeException(cause: Throwable) : IOException("Unexpected Groq response", cause)
