package com.juice.core.card

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class CreatedStampTest {
    private val today = LocalDate.of(2026, 11, 21)

    @Test
    fun todayShowsTheTime() =
        assertEquals("Today 14:05", createdStamp(Instant.parse("2026-11-21T14:05:00Z"), ZoneOffset.UTC, today))

    @Test
    fun yesterdayShowsTheTime() =
        assertEquals("Yesterday 09:30", createdStamp(Instant.parse("2026-11-20T09:30:00Z"), ZoneOffset.UTC, today))

    @Test
    fun earlierThisYearShowsDayMonthAndTime() =
        assertEquals("3 Nov 14:05", createdStamp(Instant.parse("2026-11-03T14:05:00Z"), ZoneOffset.UTC, today))

    @Test
    fun earlierYearShowsDayMonthYear() =
        assertEquals("3 Nov 2025", createdStamp(Instant.parse("2025-11-03T14:05:00Z"), ZoneOffset.UTC, today))

    @Test
    fun dayIsReadInTheGivenZone() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        assertEquals("Today 08:30", createdStamp(Instant.parse("2026-11-20T23:30:00Z"), tokyo, today))
    }
}
