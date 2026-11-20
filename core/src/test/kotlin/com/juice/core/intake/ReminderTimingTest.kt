package com.juice.core.intake

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderTimingTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(month: Int, day: Int, hour: Int = 9) = ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, zone)
    private val monday = at(11, 16, 10)
    private val wednesday = at(11, 18, 10)

    private fun merged(deadline: ZonedDateTime, notify: ZonedDateTime, now: ZonedDateTime) =
        ParsedItem(ItemType.Task, "Thing", deadline = deadline.toInstant(), notifyAt = notify.toInstant())
            .improvingOn(ParsedItem(ItemType.Task, "Thing"), now, "Thing").notifyAt

    @Test fun trashOnThursdayAfternoonIsRemindedThursdayMorning() {
        assertEquals(at(11, 19), reminderFor(at(11, 19, 15), monday, hasTime = true))
        assertEquals(at(11, 19), merged(at(11, 19, 15), at(11, 19), monday)?.atZone(zone))
    }

    @Test fun essayOnTwelfthDecemberIsRemindedAboutAWeekBefore() {
        val reminder = reminderFor(at(12, 12, 23).withMinute(59), wednesday, hasTime = false)!!
        assertEquals(9, reminder.hour)
        assertTrue(reminder.toLocalDate() in at(12, 5).toLocalDate()..at(12, 7).toLocalDate())
    }

    @Test fun aProjectIsRemindedEarlierThanATaskAtTheSameDistance() {
        val deadline = at(12, 1, 12)
        val task = reminderFor(deadline, wednesday, hasTime = true, type = ItemType.Task)!!
        val project = reminderFor(deadline, wednesday, hasTime = true, type = ItemType.Project)!!
        assertTrue(project.isBefore(task))
    }

    @Test fun theLeadNeverExceedsSevenDays() {
        val reminder = reminderFor(at(12, 31, 12), wednesday, hasTime = true, type = ItemType.Project)!!
        assertEquals(at(12, 24), reminder)
    }

    @Test fun aModelReminderFiveDaysBeforeAFarDeadlineIsKept() =
        assertEquals(at(12, 7).toInstant(), merged(at(12, 12, 12), at(12, 7), wednesday))

    @Test fun aModelReminderTenDaysBeforeIsReplaced() {
        val deadline = at(12, 12, 12)
        assertEquals(reminderFor(deadline, wednesday, hasTime = true)?.toInstant(), merged(deadline, at(12, 2), wednesday))
    }
}
