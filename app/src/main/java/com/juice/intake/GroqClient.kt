package com.juice.intake

import java.io.IOException
import java.net.SocketException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Calls Groq chat completions in JSON mode. Throws [IOException] on any transport or HTTP failure. */
class GroqClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.groq.com/openai/v1",
    private val model: String = MODEL,
) {
    /** Returns the model's JSON text for [rawText], with [now] injected into the system prompt. */
    fun parse(rawText: String, now: ZonedDateTime): String = complete(systemPrompt(now), rawText)

    /** Returns the model's JSON persona (`name`, `personality`, `personaPrompt`) for a coach about [hobby]. */
    fun generatePersona(hobby: String): String = complete(PERSONA_PROMPT, hobby)

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
        val conn = URL("$baseUrl/chat/completions").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) throw httpError(conn)
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            return try {
                Json.parseToJsonElement(response).jsonObject["choices"]!!.jsonArray[0]
                    .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
            } catch (e: RuntimeException) {
                throw IOException("Unexpected Groq response", e)
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun message(role: String, content: String): JsonObject =
        buildJsonObject { put("role", role); put("content", JsonPrimitive(content)) }

    companion object {
        private const val TIMEOUT_MS = 10_000

        /** The Groq model every cloud call uses. Groq retires models (llama-3.3-70b-versatile went on 16 Aug 2026); change it here only. */
        const val MODEL = "openai/gpt-oss-120b"

        /** A [GroqHttpException] naming the HTTP status and Groq's own error, so a failure says why. */
        fun httpError(conn: HttpURLConnection): GroqHttpException {
            val body = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
            val error = runCatching { Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject }.getOrNull()
            val code = error?.get("code")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
            val detail = error?.get("message")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() } ?: body
            val retryAfter = conn.getHeaderField("retry-after")?.toDoubleOrNull()?.let { (it * 1000).toLong() }
            return GroqHttpException(conn.responseCode, code, detail, retryAfter)
        }

        /** Why a Groq call failed, in a short sentence the player can read. */
        fun reason(e: Throwable): String = when (e) {
            is GroqHttpException -> when {
                e.status == 429 -> "Groq's rate limit was hit (429). Wait a minute and try again."
                e.status == 401 || e.status == 403 -> "Groq rejected the API key (${e.status})."
                e.code == "json_validate_failed" -> "The coach's answer wasn't valid JSON (${e.status})."
                e.status == 404 || e.code == "model_decommissioned" || e.code == "model_not_found" ->
                    "Groq no longer offers the model (${e.status})."
                e.status >= 500 -> "Groq is having trouble (${e.status}). Try again soon."
                else -> "Groq error ${e.status}${if (e.detail.isBlank()) "" else ": ${e.detail.take(160)}"}"
            }
            is SocketTimeoutException -> "Groq took too long to answer."
            // Neither means the phone is offline: a DNS hiccup, a VPN or Private DNS, or a dropped connection all land here.
            is UnknownHostException -> "Couldn't look up Groq's server. A VPN, ad blocker or Private DNS setting can cause this."
            is SocketException -> "The connection to Groq failed${e.message?.let { ": $it" }.orEmpty()}"
            else -> e.message ?: e.javaClass.simpleName
        }

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
            Today is ${now.format(DateTimeFormatter.ISO_LOCAL_DATE)}, current time ${now.format(DateTimeFormatter.ofPattern("HH:mm"))}, timezone ${now.zone.id} (UTC offset ${now.offset.id}).
            You are the Juice Intake Parser. Output only JSON matching the schema.

            Types:
            - "Note": plain thoughts or facts.
            - "Task": an actionable item.
            - "Goal": a task with a specific date, time, or target metric.
            - "Routine": a habit that repeats on a cadence ("every day", "every Sunday", "weekly on Friday"). A recurring phrase is a Routine, not a Goal.
            - "Project": an item with multiple sub-steps.

            Schema:
            {
              "type": "Note" | "Task" | "Goal" | "Project" | "Routine",
              "title": "string",
              "completion_condition": "string | null",
              "deadline": "ISO-8601 string with offset | null",
              "sub_items": ["string"] | null,
              "cadence": {"days": ["MON"|"TUE"|"WED"|"THU"|"FRI"|"SAT"|"SUN", ...] | "daily", "time": "HH:mm | null"} | null
            }

            Rules:
            - The app decides the type from the wording, so concentrate on "title", "sub_items" and "deadline".
            - Never invent steps: "sub_items" holds only steps the person actually wrote, otherwise null.
            - "cadence" is only for Routine; use null for every other type. A Routine has a null deadline.
        """.trimIndent()
    }
}

/** Groq answered with a non-2xx [status]; [code] and [detail] are Groq's own, [retryAfterMs] its Retry-After. */
class GroqHttpException(
    val status: Int,
    val code: String?,
    val detail: String,
    val retryAfterMs: Long? = null,
) : IOException("Groq HTTP $status${if (detail.isBlank()) "" else ": ${detail.take(300)}"}")
