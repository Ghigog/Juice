package com.juice.intake

import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
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
}
