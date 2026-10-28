package com.juice.coach

import com.juice.intake.GroqClient
import com.juice.intake.GroqHttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class CoachClientTest {
    private val server = MockWebServer().apply { start() }
    private val waits = mutableListOf<Long>()
    private val client = CoachClient("key", server.url("/").toString().trimEnd('/'), sleep = { waits += it })
    private val hi = listOf(ChatMessage("user", "hi"))

    @AfterTest fun stop() = server.shutdown()

    private fun ok(content: String = "Hello") =
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"$content"}}]}"""))

    private fun error(status: Int, code: String? = null, message: String = "nope") = server.enqueue(
        MockResponse().setResponseCode(status)
            .setBody("""{"error":{"message":"$message","type":"x"${code?.let { ""","code":"$it"""" } ?: ""}}}"""),
    )

    @Test fun aRateLimitIsTriedAgainAfterABackoff() {
        error(429)
        error(429)
        ok()
        assertEquals("Hello", client.chat("system", hi))
        assertEquals(3, server.requestCount)
        assertEquals(listOf(1_000L, 2_000L), waits)
    }

    @Test fun groqsRetryAfterIsWaitedOut() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("retry-after", "2.5"))
        ok()
        assertEquals("Hello", client.chat("system", hi))
        assertEquals(listOf(2_500L), waits)
    }

    @Test fun aLongRateLimitIsReportedAtOnce() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("retry-after", "3600"))
        val e = assertFailsWith<GroqHttpException> { client.chat("system", hi) }
        assertEquals(429, e.status)
        assertEquals(1, server.requestCount)
    }

    @Test fun invalidJsonAndOutagesAreTriedAgain() {
        error(400, code = "json_validate_failed")
        error(503)
        ok("{}")
        assertEquals("{}", client.chat("system", hi, jsonMode = true))
        assertEquals(3, server.requestCount)
    }

    @Test fun aRefusedConnectionIsTriedAgain() {
        val closed = MockWebServer().apply { start(); shutdown() }
        val refused = CoachClient("key", closed.url("/").toString().trimEnd('/'), sleep = { waits += it })
        assertFailsWith<ConnectException> { refused.chat("system", hi) }
        assertEquals(listOf(1_000L, 2_000L), waits)
    }

    @Test fun itGivesUpAfterThreeAttempts() {
        repeat(3) { error(500) }
        assertEquals(500, assertFailsWith<GroqHttpException> { client.chat("system", hi) }.status)
        assertEquals(3, server.requestCount)
    }

    @Test fun aBadKeyIsNotTriedAgain() {
        error(401, code = "invalid_api_key", message = "Invalid API Key")
        val e = assertFailsWith<GroqHttpException> { client.chat("system", hi) }
        assertEquals("invalid_api_key", e.code)
        assertEquals("Invalid API Key", e.detail)
        assertEquals(1, server.requestCount)
    }

    @Test fun noRetryMakesOneAttempt() {
        error(503)
        assertFailsWith<GroqHttpException> { client.chat("system", hi, retry = false) }
        assertEquals(1, server.requestCount)
    }

    @Test fun jsonModeRunsAtTemperatureZero() {
        ok("{}")
        client.chat("system", hi, jsonMode = true)
        assertTrue("\"temperature\":0.0" in server.takeRequest().body.readUtf8())
        ok()
        client.chat("system", hi)
        assertTrue("\"temperature\":0.7" in server.takeRequest().body.readUtf8())
    }

    @Test fun reasonsAreShortAndPlain() {
        assertEquals("Groq's rate limit was hit (429). Wait a minute and try again.", GroqClient.reason(GroqHttpException(429, null, "")))
        assertEquals("Groq rejected the API key (401).", GroqClient.reason(GroqHttpException(401, "invalid_api_key", "")))
        assertEquals("The coach's answer wasn't valid JSON (400).", GroqClient.reason(GroqHttpException(400, "json_validate_failed", "")))
        assertEquals("Groq no longer offers the model (400).", GroqClient.reason(GroqHttpException(400, "model_decommissioned", "")))
        assertEquals("Groq is having trouble (503). Try again soon.", GroqClient.reason(GroqHttpException(503, null, "")))
        assertEquals("Groq error 413: Request too large", GroqClient.reason(GroqHttpException(413, null, "Request too large")))
        assertEquals("Groq took too long to answer.", GroqClient.reason(SocketTimeoutException()))
        assertEquals(
            "Couldn't look up Groq's server. A VPN, ad blocker or Private DNS setting can cause this.",
            GroqClient.reason(UnknownHostException()),
        )
        assertEquals("The connection to Groq failed: Connection reset", GroqClient.reason(ConnectException("Connection reset")))
        assertEquals("offline", GroqClient.reason(IOException("offline")))
    }
}
