package com.juice.core.notify

import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.core.routine.Cadence
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReminderPlanTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 3, 10)
    private val everyTwoDays = Cadence(DayOfWeek.entries.toSet(), intervalDays = 2)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = date.atTime(hour, minute).toInstant(zone)

    private fun routine(due: LocalDate, cadence: Cadence = everyTwoDays) =
        Item("r", ItemType.Routine, "Water plants", cadence = cadence, nextDueOn = due)

    private fun timed(status: ItemStatus = ItemStatus.Active, notifyAt: Instant? = at(day, 12), characterId: String? = null) =
        Item("t", ItemType.Task, "Call", status = status, notifyAt = notifyAt, notifyText = "Ring Sam", characterId = characterId)

    @Test
    fun `a routine without a time notifies at 8 on its due day with the title`() {
        assertEquals(Reminder(at(day, 8), "Water plants"), plan(routine(day), at(day, 6), zone))
    }

    @Test
    fun `a routine with an earlier cadence time notifies then`() {
        val cadence = everyTwoDays.copy(time = LocalTime.of(7, 30))
        assertEquals(at(day, 7, 30), plan(routine(day, cadence), at(day, 6), zone)?.at)
    }

    @Test
    fun `a later cadence time still notifies at 8`() {
        val cadence = everyTwoDays.copy(time = LocalTime.of(18, 0))
        assertEquals(at(day, 8), plan(routine(day, cadence), at(day, 6), zone)?.at)
    }

    @Test
    fun `a routine advanced two days notifies on that day and not the day between`() {
        val advanced = routine(day.plusDays(2))
        assertEquals(at(day.plusDays(2), 8), plan(advanced, at(day, 9), zone)?.at)
    }

    @Test
    fun `a routine whose moment has passed plans nothing`() {
        assertNull(plan(routine(day), at(day, 9), zone))
    }

    @Test
    fun `a routine without a due day plans nothing`() {
        assertNull(plan(routine(day).copy(nextDueOn = null), at(day, 6), zone))
    }

    @Test
    fun `a future notifyAt uses its text`() {
        assertEquals(Reminder(at(day, 12), "Ring Sam"), plan(timed(), at(day, 6), zone))
    }

    @Test
    fun `missing notifyText falls back to the title`() {
        assertEquals("Call", plan(timed().copy(notifyText = null), at(day, 6), zone)?.text)
    }

    @Test
    fun `completed and archived items plan nothing`() {
        assertNull(plan(timed(ItemStatus.Completed), at(day, 6), zone))
        assertNull(plan(timed(ItemStatus.Archived), at(day, 6), zone))
        assertNull(plan(routine(day).copy(status = ItemStatus.Completed), at(day, 6), zone))
    }

    @Test
    fun `coach items plan nothing`() {
        assertNull(plan(timed(characterId = "coach"), at(day, 6), zone))
    }

    @Test
    fun `past or missing notifyAt plans nothing`() {
        assertNull(plan(timed(notifyAt = at(day, 5)), at(day, 6), zone))
        assertNull(plan(timed(notifyAt = null), at(day, 6), zone))
    }
}
