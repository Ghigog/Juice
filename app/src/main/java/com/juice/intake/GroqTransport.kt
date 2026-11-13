package com.juice.intake

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The one place that talks HTTP to Groq: base URL, Bearer auth, JSON content type, per-call timeout, the optional
 * retry loop, the chat-completion envelope and the key check. Throws [IOException] (a [GroqHttpException] for a
 * non-2xx answer, a [GroqEnvelopeException] for an unreadable 2xx body) on any failure.
 */
class GroqTransport(
    private val apiKey: String,
    private val baseUrl: String = "https://api.groq.com/openai/v1",
    /** Waits between attempts, in milliseconds. */
    private val sleep: (Long) -> Unit = Thread::sleep,
) {
    /**
     * POSTs [body] to /chat/completions and returns the first choice's content. With [retry], a rate limit, a Groq
     * outage, a timeout or invalid JSON is tried again, up to [MAX_ATTEMPTS] attempts in all.
     */
    fun chat(body: String, timeoutMs: Int, retry: Boolean): String {
        var attempt = 1
        while (true) {
            try {
                return post(body, timeoutMs)
            } catch (e: IOException) {
                val wait = waitBeforeRetry(e, attempt)
                if (!retry || attempt >= MAX_ATTEMPTS || wait == null) throw e
                sleep(wait)
                attempt++
            }
        }
    }

    /** One GET to /models (no tokens spent) to learn whether the key works. Never logs the key or the body. */
    fun checkKey(timeoutMs: Int): KeyCheck {
        val conn = URL("$baseUrl/models").openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            when (conn.responseCode) {
                in 200..299 -> KeyCheck.Works
                401, 403 -> KeyCheck.Rejected
                else -> KeyCheck.Unreachable
            }
        } catch (e: IOException) {
            KeyCheck.Unreachable
        } finally {
            conn.disconnect()
        }
    }

    private fun post(body: String, timeoutMs: Int): String {
        val conn = URL("$baseUrl/chat/completions").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode !in 200..299) throw httpError(conn)
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            return try {
                Json.parseToJsonElement(response).jsonObject["choices"]!!.jsonArray[0]
                    .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
            } catch (e: RuntimeException) {
                throw GroqEnvelopeException(e)
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

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val BACKOFF_MS = 1_000L

        /** A rate limit that lifts later than this is reported at once rather than waited out. */
        private const val MAX_WAIT_MS = 10_000L

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
    }
}
