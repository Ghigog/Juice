package com.juice.intake

import com.juice.core.intake.ItemType
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class IntakeParserTest {
    private val now = ZonedDateTime.of(2026, 10, 19, 9, 30, 0, 0, ZoneId.of("Europe/Berlin"))
    private val server = MockWebServer().apply { start() }

    @AfterTest fun stop() = server.shutdown()

    private fun reply(content: String, status: Int = 200) {
        val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"")
        server.enqueue(MockResponse().setResponseCode(status).setBody("""{"choices":[{"message":{"content":"$escaped"}}]}"""))
    }

    private fun parser(onDeviceOnly: Boolean = false, baseUrl: String = server.url("/").toString().trimEnd('/')) =
        IntakeParser(GroqClient("key-1", baseUrl), { onDeviceOnly }, { now })

    @Test fun onlineParseGivesGoalWithDeadline() {
        reply("""{"type":"Goal","title":"Do laundry","completion_condition":null,"deadline":"2026-10-19T18:00:00+02:00","sub_items":null}""")
        val result = parser().parse("do laundry by 6pm")
        assertEquals(ItemType.Goal, result.item.type)
        assertEquals(Instant.parse("2026-10-19T16:00:00Z"), result.item.deadline)
        assertFalse(result.retryLater)
        assertEquals("Bearer key-1", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun requestUsesJsonModeAndInjectsDateTimeAndTimezone() {
        reply("""{"type":"Note","title":"x"}""")
        parser().parse("x")
        val body = server.takeRequest().body.readUtf8()
        assertTrue("json_object" in body)
        assertTrue("2026-10-19" in body)
        assertTrue("09:30" in body)
        assertTrue("Europe/Berlin" in body)
    }

    @Test fun httpErrorFallsBackToRulesAndRetries() {
        reply("", status = 503)
        val result = parser().parse("do laundry by 6pm")
        assertEquals(ItemType.Goal, result.item.type)
        assertTrue(result.retryLater)
    }

    @Test fun offlineFallsBackToRulesAndRetries() {
        val url = server.url("/").toString().trimEnd('/')
        server.shutdown()
        val result = parser(baseUrl = url).parse("ideas for the garden")
        assertEquals(ItemType.Note, result.item.type)
        assertTrue(result.retryLater)
    }

    @Test fun invalidModelOutputGivesNoteWithoutRetry() {
        reply("not json")
        val result = parser().parse("hello")
        assertEquals(ItemType.Note, result.item.type)
        assertFalse(result.retryLater)
    }

    @Test fun groqNoteWithoutDeadlineKeepsTheRulesDeadline() {
        reply("""{"type":"Note","title":"Call the dentist","deadline":null}""")
        val result = parser().parse("call the dentist tomorrow at 3pm")
        assertEquals(ItemType.Goal, result.item.type)
        assertEquals("Call the dentist", result.item.title)
        assertEquals(Instant.parse("2026-10-20T13:00:00Z"), result.item.deadline)
    }

    @Test fun onDeviceOnlySkipsTheNetwork() {
        val result = parser(onDeviceOnly = true).parse("call mum tomorrow")
        assertEquals(ItemType.Goal, result.item.type)
        assertFalse(result.retryLater)
        assertEquals(0, server.requestCount)
    }
}
