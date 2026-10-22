package com.juice.core.routine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CadenceTest {
    private val daily = Cadence(DayOfWeek.entries.toSet())
    private val sundays = Cadence(setOf(DayOfWeek.SUNDAY))

    @Test fun nextDueAfterSundayIsTheFollowingSunday() {
        assertEquals(LocalDate.of(2026, 11, 1), sundays.nextDueAfter(LocalDate.of(2026, 10, 25)))
    }

    @Test fun firstDueFromIncludesTheDateItself() {
        assertEquals(LocalDate.of(2026, 10, 25), sundays.firstDueFrom(LocalDate.of(2026, 10, 25)))
        assertEquals(LocalDate.of(2026, 10, 25), sundays.firstDueFrom(LocalDate.of(2026, 10, 22)))
    }

    @Test fun isDueOnlyOnItsDays() {
        assertTrue(sundays.isDue(LocalDate.of(2026, 10, 25)))
        assertFalse(sundays.isDue(LocalDate.of(2026, 10, 26)))
    }

    @Test fun deadlineIsTheDaysTimeInTheZone() {
        val zone = ZoneId.of("Europe/Paris")
        val deadline = Cadence(DayOfWeek.entries.toSet(), LocalTime.of(9, 0)).deadlineOn(LocalDate.of(2026, 10, 22), zone)
        assertEquals(ZonedDateTime.of(2026, 10, 22, 9, 0, 0, 0, zone).toInstant(), deadline)
    }

    @Test fun deadlineIsNullWithoutTime() {
        assertNull(daily.deadlineOn(LocalDate.of(2026, 10, 22), ZoneId.of("UTC")))
    }

    @Test fun labels() {
        assertEquals("Every day", daily.label())
        assertEquals("Every Sun", sundays.label())
        assertEquals("Every Mon, Thu", Cadence(setOf(DayOfWeek.THURSDAY, DayOfWeek.MONDAY)).label())
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyDaysIsInvalid() {
        Cadence(emptySet())
    }
}
