package com.juice.intake

import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy

class GroqTransportTest {
    private val server = MockWebServer().apply { start() }
    private val sleeps = mutableListOf<Long>()
    private val ok = """{"choices":[{"message":{"content":"hi"}}]}"""

    @AfterTest fun stop() = server.shutdown()

    private fun transport() = GroqTransport("key-1", server.url("/").toString().trimEnd('/'), sleeps::add)

    @Test fun sendsBearerAuthJsonBodyAndReturnsContent() {
        server.enqueue(MockResponse().setBody(ok))
        assertEquals("hi", transport().chat("{}", 1_000, retry = false))
        val request = server.takeRequest()
        assertEquals("/chat/completions", request.path)
        assertEquals("Bearer key-1", request.getHeader("Authorization"))
        assertEquals("application/json", request.getHeader("Content-Type"))
    }

    @Test fun retriesRateLimitWithBackoffThenSucceeds() {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(429)) }
        server.enqueue(MockResponse().setBody(ok))
        assertEquals("hi", transport().chat("{}", 1_000, retry = true))
        assertEquals(3, server.requestCount)
        assertEquals(listOf(1_000L, 2_000L), sleeps)
    }

    @Test fun honoursRetryAfterUpToTenSeconds() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("retry-after", "2"))
        server.enqueue(MockResponse().setBody(ok))
        transport().chat("{}", 1_000, retry = true)
        assertEquals(listOf(2_000L), sleeps)
        server.enqueue(MockResponse().setResponseCode(429).setHeader("retry-after", "11"))
        assertFailsWith<GroqHttpException> { transport().chat("{}", 1_000, retry = true) }
        assertEquals(3, server.requestCount)
    }

    @Test fun stopsAfterThreeAttempts() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503)) }
        assertEquals(503, assertFailsWith<GroqHttpException> { transport().chat("{}", 1_000, retry = true) }.status)
        assertEquals(3, server.requestCount)
    }

    @Test fun makesOneAttemptWithoutRetry() {
        server.enqueue(MockResponse().setResponseCode(503))
        assertFailsWith<GroqHttpException> { transport().chat("{}", 1_000, retry = false) }
        assertEquals(1, server.requestCount)
    }

    @Test fun doesNotRetryClientErrors() {
        server.enqueue(MockResponse().setResponseCode(401))
        assertFailsWith<GroqHttpException> { transport().chat("{}", 1_000, retry = true) }
        assertEquals(1, server.requestCount)
    }

    @Test fun retriesDroppedConnections() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setBody(ok))
        assertEquals("hi", transport().chat("{}", 1_000, retry = true))
        assertEquals(2, server.requestCount)
    }

    @Test fun retriesJsonValidateFailed() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"code":"json_validate_failed","message":"bad"}}"""))
        server.enqueue(MockResponse().setBody(ok))
        assertEquals("hi", transport().chat("{}", 1_000, retry = true))
    }

    @Test fun unreadableEnvelopeIsNotRetried() {
        server.enqueue(MockResponse().setBody("""{"id":"x"}"""))
        assertFailsWith<GroqEnvelopeException> { transport().chat("{}", 1_000, retry = true) }
        assertEquals(1, server.requestCount)
    }

    @Test fun httpErrorCarriesStatusCodeDetailAndRetryAfter() {
        server.enqueue(
            MockResponse().setResponseCode(400).setHeader("retry-after", "1.5")
                .setBody("""{"error":{"code":"c","message":"m"}}"""),
        )
        val e = assertFailsWith<GroqHttpException> { transport().chat("{}", 1_000, retry = false) }
        assertEquals(Triple(400, "c", "m"), Triple(e.status, e.code, e.detail))
        assertEquals(1_500L, e.retryAfterMs)
    }

    @Test fun reasonReadsLikeASentence() {
        assertEquals("Groq is having trouble (503). Try again soon.", GroqTransport.reason(GroqHttpException(503, null, "")))
        assertEquals("offline", GroqTransport.reason(IOException("offline")))
    }

    @Test fun checkKeyMapsStatuses() {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(listOf(KeyCheck.Works, KeyCheck.Rejected, KeyCheck.Unreachable), List(3) { transport().checkKey(1_000) })
        assertEquals("/models", server.takeRequest().path)
    }
}
