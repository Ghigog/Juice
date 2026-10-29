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

    private val fortnightlyFridays = Cadence(setOf(DayOfWeek.FRIDAY), intervalWeeks = 2)

    @Test fun nextDueAfterSkipsByTheInterval() {
        assertEquals(LocalDate.of(2026, 11, 6), fortnightlyFridays.nextDueAfter(LocalDate.of(2026, 10, 23)))
    }

    @Test fun nextDueAfterKeepsLaterDaysOfTheSameWeek() {
        val cadence = Cadence(setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), intervalWeeks = 2)
        assertEquals(LocalDate.of(2026, 10, 23), cadence.nextDueAfter(LocalDate.of(2026, 10, 20)))
        assertEquals(LocalDate.of(2026, 11, 3), cadence.nextDueAfter(LocalDate.of(2026, 10, 23)))
    }

    @Test fun intervalLabel() {
        assertEquals("Every 2 weeks on Fri", fortnightlyFridays.label())
    }

    @Test(expected = IllegalArgumentException::class)
    fun intervalBelowOneIsInvalid() {
        Cadence(setOf(DayOfWeek.FRIDAY), intervalWeeks = 0)
    }

    private val everyThreeDays = Cadence(DayOfWeek.entries.toSet(), intervalDays = 3)

    @Test fun dayIntervalIsDueOnTheAnchorAndNextDueIsThatManyDaysLater() {
        val anchor = LocalDate.of(2026, 10, 28)
        assertEquals(anchor, everyThreeDays.firstDueFrom(anchor))
        assertEquals(true, everyThreeDays.isDue(anchor))
        assertEquals(LocalDate.of(2026, 10, 31), everyThreeDays.nextDueAfter(anchor))
    }

    @Test fun dayIntervalLabel() {
        assertEquals("Every 3 days", everyThreeDays.label())
    }

    @Test(expected = IllegalArgumentException::class)
    fun dayAndWeekIntervalsCannotBeCombined() {
        Cadence(setOf(DayOfWeek.FRIDAY), intervalWeeks = 2, intervalDays = 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun dayIntervalBelowOneIsInvalid() {
        Cadence(setOf(DayOfWeek.FRIDAY), intervalDays = 0)
    }
}
