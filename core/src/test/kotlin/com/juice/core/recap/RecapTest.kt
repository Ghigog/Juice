package com.juice.core.recap

import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.core.model.Step
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecapTest {
    private val zone = ZoneId.of("Europe/London")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12): Instant = LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant()
    private fun clock(now: Instant) = Clock.fixed(now, zone)

    // Wednesday 2026-10-21
    private val wednesday = clock(at(2026, 10, 21))

    private fun done(id: String, type: ItemType, completedAt: Instant?, createdAt: Instant? = null) =
        Item(id, type, id, status = ItemStatus.Completed, createdAt = createdAt, completedAt = completedAt)

    private fun ids(recap: Recap) = recap.groups.flatMap { g -> g.entries.map { it.item.id } }

    @Test fun periodStartsAreMidnightMondayAndFirstOfMonth() {
        assertEquals(at(2026, 10, 21, 0), RecapPeriod.Today.range(wednesday).start)
        assertEquals(at(2026, 10, 19, 0), RecapPeriod.Week.range(wednesday).start)
        assertEquals(at(2026, 10, 1, 0), RecapPeriod.Month.range(wednesday).start)
    }

    @Test fun sundayBelongsToTheWeekThatStartedPreviousMonday() {
        val sunday = clock(at(2026, 10, 25, 23))
        assertEquals(at(2026, 10, 19, 0), RecapPeriod.Week.range(sunday).start)
        assertEquals(at(2026, 10, 26, 0), RecapPeriod.Week.range(sunday).end)
        val monday = clock(at(2026, 10, 26, 0))
        assertEquals(at(2026, 10, 26, 0), RecapPeriod.Week.range(monday).start)
    }

    @Test fun periodsAreDstSafe() {
        // UK clocks go back on 2026-10-25: that Sunday is 25 hours long.
        val sunday = clock(at(2026, 10, 25, 12))
        val today = RecapPeriod.Today.range(sunday)
        assertEquals(25L, java.time.Duration.between(today.start, today.end).toHours())
        assertEquals(LocalDate.of(2026, 10, 26), today.end.atZone(zone).toLocalDate())
        assertEquals(at(2026, 11, 1, 0), RecapPeriod.Month.range(sunday).end)
    }

    @Test fun itemCompletedTodayIsInEveryPeriod() {
        val items = listOf(done("a", ItemType.Task, at(2026, 10, 21, 9)))
        RecapPeriod.entries.forEach { assertEquals(listOf("a"), ids(RecapBuilder.build(items, it, wednesday))) }
    }

    @Test fun lastSundayIsOnlyInMonth() {
        val items = listOf(done("a", ItemType.Task, at(2026, 10, 18, 9)))
        assertTrue(ids(RecapBuilder.build(items, RecapPeriod.Today, wednesday)).isEmpty())
        assertTrue(ids(RecapBuilder.build(items, RecapPeriod.Week, wednesday)).isEmpty())
        assertEquals(listOf("a"), ids(RecapBuilder.build(items, RecapPeriod.Month, wednesday)))
    }

    @Test fun groupsAreOrderedProjectGoalTaskRoutineNote() {
        val t = at(2026, 10, 21, 9)
        val items = listOf(ItemType.Note, ItemType.Routine, ItemType.Task, ItemType.Goal, ItemType.Project)
            .map { done(it.name, it, t) }
        val recap = RecapBuilder.build(items, RecapPeriod.Today, wednesday)
        assertEquals(listOf("Project", "Goal", "Task", "Routine", "Note"), recap.groups.map { it.type.name })
    }

    @Test fun projectCarriesOnlyStepsDoneInThePeriod() {
        val project = done("p", ItemType.Project, at(2026, 10, 21, 9)).copy(
            steps = listOf(
                Step("old", "old", true, at(2026, 10, 1, 9)),
                Step("new", "new", true, at(2026, 10, 21, 8)),
                Step("legacy", "legacy", true),
            ),
        )
        val entry = RecapBuilder.build(listOf(project), RecapPeriod.Today, wednesday).groups.single().entries.single()
        assertEquals(listOf("new"), entry.steps.map { it.id })
    }

    @Test fun totalsCountItemsAndWeightByBaseXp() {
        val t = at(2026, 10, 21, 9)
        val items = listOf(done("p", ItemType.Project, t), done("t", ItemType.Task, t), done("n", ItemType.Note, t))
        val recap = RecapBuilder.build(items, RecapPeriod.Today, wednesday)
        assertEquals(3, recap.completedCount)
        assertEquals(535L, recap.size)
    }

    @Test fun undoneAndArchivedItemsAreNotCounted() {
        val items = listOf(
            Item("u", ItemType.Task, "u", createdAt = at(2026, 10, 1)),
            done("x", ItemType.Task, at(2026, 10, 21, 9)).copy(status = ItemStatus.Archived),
        )
        val recap = RecapBuilder.build(items, RecapPeriod.Month, wednesday)
        assertTrue(recap.groups.isEmpty())
        assertEquals(listOf(1), recap.burndown.actual.map { it.open })
    }

    @Test fun burndownStartsAtScopeAndStepsDownAtCompletions() {
        val start = at(2026, 10, 19, 0)
        val open = (1..3).map { Item("o$it", ItemType.Task, "o$it", createdAt = at(2026, 10, 1)) }
        val legacyOpen = Item("legacy", ItemType.Task, "legacy")
        val doneBefore = done("before", ItemType.Task, at(2026, 10, 10), at(2026, 10, 1))
        val added = done("added", ItemType.Task, at(2026, 10, 21, 9), at(2026, 10, 20, 9))
        val recap = RecapBuilder.build(open + legacyOpen + doneBefore + added, RecapPeriod.Week, wednesday)
        // 3 open + 1 legacy open + 1 added
        assertEquals(listOf(BurndownPoint(start, 5), BurndownPoint(at(2026, 10, 21, 9), 4)), recap.burndown.actual)
        assertEquals(listOf(BurndownPoint(start, 5), BurndownPoint(at(2026, 10, 26, 0), 0)), recap.burndown.ideal)
        assertEquals(listOf("added"), ids(recap))
    }
}
