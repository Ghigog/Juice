package com.juice.core.intake

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RulesFallbackTest {
    private val now = ZonedDateTime.now(Clock.fixed(Instant.parse("2026-11-21T09:00:00Z"), ZoneOffset.UTC))
    private fun at(time: String, day: String = "2026-11-21") = Instant.parse("${day}T$time:00Z")
    private fun classify(text: String) = RulesFallback.classify(text, now)

    @Test fun clockedGoalGetsDeadlineAndReminderTwoHoursBefore() {
        val item = classify("today at 19:00, is the Christmas dinner")
        assertEquals(ItemType.Goal, item.type)
        assertEquals(at("19:00"), item.deadline)
        assertEquals(at("17:00"), item.notifyAt)
        assertEquals(item.title, item.notifyText)
    }

    @Test fun bareTwentyFourHourTimeIsTheDeadline() {
        assertEquals(at("19:00"), classify("dinner 19:00").deadline)
        assertEquals(at("19:00"), classify("19:00 today").deadline)
    }

    @Test fun colonListStillSplitsAfterTitleColon() {
        val item = classify("Launch: write copy, record demo and ship")
        assertEquals(ItemType.List, item.type)
        assertEquals("Launch", item.title)
        assertEquals(3, item.subItems.size)
        assertNull(item.deadline)
        assertNull(item.notifyAt)
    }

    @Test fun clockTimeBeforeTheColonIsNotTheSeparator() {
        val item = classify("Standup at 10:30: notes, demo and retro")
        assertEquals(ItemType.List, item.type)
        assertEquals(3, item.subItems.size)
    }

    @Test fun dateOnlyGoalIsRemindedInTheMorning() {
        val item = classify("pay rent tomorrow")
        assertEquals(at("09:00", "2026-11-22"), item.notifyAt)
    }
}
