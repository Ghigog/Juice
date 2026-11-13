package com.juice.coach

import com.juice.data.ChatMessage
import com.juice.intake.GroqTransport
import java.io.IOException
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Multi-turn Groq chat completions for the coach. A rate limit, a Groq outage, a timeout or invalid JSON is tried
 * again, up to [MAX_ATTEMPTS] times in all; after that, or on any other failure, it throws [IOException].
 */
class CoachClient(
    private val transport: GroqTransport,
    private val model: String = GroqTransport.MODEL,
) {
    constructor(
        apiKey: String,
        baseUrl: String = "https://api.groq.com/openai/v1",
        model: String = GroqTransport.MODEL,
        /** Waits between attempts, in milliseconds. */
        sleep: (Long) -> Unit = Thread::sleep,
    ) : this(GroqTransport(apiKey, baseUrl, sleep), model)

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
        return transport.chat(body, TIMEOUT_MS, retry)
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
    }
}
