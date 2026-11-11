package com.juice.llm

import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class GroqClientTest {
    private val now = ZonedDateTime.of(2026, 10, 19, 9, 30, 0, 0, ZoneId.of("Europe/Berlin"))
    private val server = MockWebServer().apply { start() }

    @AfterTest fun stop() = server.shutdown()

    private fun assertMalformed(body: String) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val client = GroqClient("key-1", server.url("/").toString().trimEnd('/'))
        assertFailsWith<IOException> { client.parse("hello", now) }
    }

    @Test fun responseWithNoChoicesThrowsIOException() = assertMalformed("""{"id":"x"}""")

    @Test fun responseWithEmptyChoicesThrowsIOException() = assertMalformed("""{"choices":[]}""")

    @Test fun choiceWithoutMessageThrowsIOException() = assertMalformed("""{"choices":[{"index":0}]}""")

    @Test fun messageWithoutContentThrowsIOException() = assertMalformed("""{"choices":[{"message":{"role":"assistant"}}]}""")

    private fun check(response: MockResponse): KeyCheck {
        server.enqueue(response)
        return GroqClient("key-1", server.url("/").toString().trimEnd('/')).checkKey()
    }

    @Test fun checkKeyRejectedOn401AfterOneRequest() {
        assertEquals(KeyCheck.Rejected, check(MockResponse().setResponseCode(401)))
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/models", request.path)
        assertEquals("Bearer key-1", request.getHeader("Authorization"))
    }

    @Test fun checkKeyWorksOn200() = assertEquals(KeyCheck.Works, check(MockResponse().setBody("{}")))

    @Test fun checkKeyRejectedOn403() = assertEquals(KeyCheck.Rejected, check(MockResponse().setResponseCode(403)))

    @Test fun checkKeyUnreachableOnServerError() =
        assertEquals(KeyCheck.Unreachable, check(MockResponse().setResponseCode(500)))

    @Test fun checkKeyUnreachableOnIOException() {
        val client = GroqClient("key-1", server.url("/").toString().trimEnd('/'))
        server.shutdown()
        assertEquals(KeyCheck.Unreachable, client.checkKey())
    }
}
