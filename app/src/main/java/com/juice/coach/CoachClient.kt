package com.juice.coach

import com.juice.core.character.Expression
import java.io.IOException
import java.net.HttpURLConnection
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

/** Multi-turn Groq chat completions for the coach. Throws [IOException] on any transport or HTTP failure. */
class CoachClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.groq.com/openai/v1",
    private val model: String = "llama-3.3-70b-versatile",
) {
    /** Returns the model's reply to [messages] under [systemPrompt]; [jsonMode] forces a JSON object reply. */
    fun chat(systemPrompt: String, messages: List<ChatMessage>, jsonMode: Boolean = false): String {
        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0.7)
            if (jsonMode) put("response_format", buildJsonObject { put("type", "json_object") })
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", systemPrompt) })
                messages.forEach { m -> add(buildJsonObject { put("role", m.role); put("content", m.content) }) }
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
            if (conn.responseCode !in 200..299) throw IOException("Groq HTTP ${conn.responseCode}")
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

    private companion object {
        const val TIMEOUT_MS = 20_000
    }
}
