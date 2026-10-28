package com.juice.coach

import com.juice.core.character.Expression
import com.juice.intake.GroqClient
import com.juice.intake.GroqHttpException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** One turn of a coach conversation; [role] is "user" or "assistant". */
data class ChatMessage(val role: String, val content: String, val expression: Expression = Expression.NEUTRAL)

/**
 * Multi-turn Groq chat completions for the coach. A rate limit, a Groq outage, a timeout or invalid JSON is tried
 * again, up to [MAX_ATTEMPTS] times in all; after that, or on any other failure, it throws [IOException].
 */
class CoachClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.groq.com/openai/v1",
    private val model: String = GroqClient.MODEL,
    /** Waits between attempts, in milliseconds. */
    private val sleep: (Long) -> Unit = Thread::sleep,
) {
    /**
     * Returns the model's reply to [messages] under [systemPrompt]; [jsonMode] forces a JSON object reply, at
     * temperature 0 so it stays well formed. [retry] false makes a single attempt.
     */
    fun chat(systemPrompt: String, messages: List<ChatMessage>, jsonMode: Boolean = false, retry: Boolean = true): String {
        val body = buildJsonObject {
            put("model", model)
            put("temperature", if (jsonMode) 0.0 else 0.7)
            if (jsonMode) put("response_format", buildJsonObject { put("type", "json_object") })
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", systemPrompt) })
                messages.forEach { m -> add(buildJsonObject { put("role", m.role); put("content", m.content) }) }
            })
        }.toString()
        var attempt = 1
        while (true) {
            try {
                return post(body)
            } catch (e: IOException) {
                val wait = waitBeforeRetry(e, attempt)
                if (!retry || attempt >= MAX_ATTEMPTS || wait == null) throw e
                sleep(wait)
                attempt++
            }
        }
    }

    private fun post(body: String): String {
        val conn = URL("$baseUrl/chat/completions").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode !in 200..299) throw GroqClient.httpError(conn)
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

    /** How long to wait before trying again after [e] on [attempt], or null when trying again won't help. */
    private fun waitBeforeRetry(e: IOException, attempt: Int): Long? {
        val retryable = when (e) {
            is GroqHttpException -> e.status == 429 || e.status >= 500 || e.code == "json_validate_failed"
            is SocketTimeoutException, is SocketException, is UnknownHostException -> true
            else -> false
        }
        if (!retryable) return null
        val asked = (e as? GroqHttpException)?.retryAfterMs ?: return BACKOFF_MS * attempt
        return asked.takeIf { it <= MAX_WAIT_MS }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
        const val MAX_ATTEMPTS = 3
        const val BACKOFF_MS = 1_000L

        /** A rate limit that lifts later than this is reported at once rather than waited out. */
        const val MAX_WAIT_MS = 10_000L
    }
}
