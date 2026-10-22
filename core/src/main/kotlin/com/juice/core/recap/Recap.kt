package com.juice.core.recap

import com.juice.core.economy.EconomyConfig
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.core.model.Step
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.temporal.TemporalAdjusters

/** The windows the Recap looks back over, ending at the start of the next day/week/month. */
enum class RecapPeriod {
    Today, Week, Month;

    /** The period containing [clock]'s now, in its zone: Week starts Monday 00:00, Month on the 1st. */
    fun range(clock: Clock): PeriodRange {
        val first = clock.instant().atZone(clock.zone).toLocalDate()
        val (startDay, endDay) = when (this) {
            Today -> first to first.plusDays(1)
            Week -> first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusWeeks(1) }
            Month -> first.withDayOfMonth(1).let { it to it.plusMonths(1) }
        }
        return PeriodRange(startDay.atStartOfDay(clock.zone).toInstant(), endDay.atStartOfDay(clock.zone).toInstant())
    }
}

/** [start] inclusive, [end] exclusive. */
data class PeriodRange(val start: Instant, val end: Instant) {
    operator fun contains(at: Instant) = at >= start && at < end
}

/** A completed item; a Project carries the [steps] ticked within the period. */
data class RecapEntry(val item: Item, val steps: List<Step> = emptyList())

data class RecapGroup(val type: ItemType, val entries: List<RecapEntry>)

data class BurndownPoint(val at: Instant, val open: Int)

/** [actual] starts at the period's scope and steps down at each completion; [ideal] runs straight from that scope to 0. */
data class Burndown(val actual: List<BurndownPoint>, val ideal: List<BurndownPoint>)

data class Recap(
    val period: RecapPeriod,
    val range: PeriodRange,
    val groups: List<RecapGroup>,
    val completedCount: Int,
    /** Juice-weighted size: the sum of each completed item's base Juice. */
    val size: Long,
    val burndown: Burndown,
)

private val groupOrder = listOf(ItemType.Project, ItemType.Goal, ItemType.Task, ItemType.Routine, ItemType.Note)

object RecapBuilder {
    fun build(items: List<Item>, period: RecapPeriod, clock: Clock, config: EconomyConfig = EconomyConfig()): Recap {
        val range = period.range(clock)
        val live = items.filter { it.status != ItemStatus.Archived }
        val completed = live
            .filter { it.completedAt?.let(range::contains) == true }
            .sortedBy { it.completedAt }

        val groups = groupOrder.mapNotNull { type ->
            val entries = completed.filter { it.type == type }.map { item ->
                RecapEntry(item, if (type == ItemType.Project) item.steps.filter { it.doneAt?.let(range::contains) == true } else emptyList())
            }
            if (entries.isEmpty()) null else RecapGroup(type, entries)
        }
        val size = completed.sumOf { config.baseXp.getValue(it.type.economyType()) }
        return Recap(period, range, groups, completed.size, size, burndown(live, completed, range))
    }

    private fun burndown(live: List<Item>, completed: List<Item>, range: PeriodRange): Burndown {
        val openAtStart = live.count { item ->
            (item.createdAt == null || item.createdAt < range.start) && !(item.completedAt != null && item.completedAt < range.start)
        }
        val createdDuring = live.count { item -> item.createdAt?.let(range::contains) == true }
        val scope = openAtStart + createdDuring
        var open = scope
        val actual = listOf(BurndownPoint(range.start, scope)) +
            completed.map { BurndownPoint(it.completedAt!!, (--open)) }
        val ideal = listOf(BurndownPoint(range.start, scope), BurndownPoint(range.end, 0))
        return Burndown(actual, ideal)
    }
}

private fun ItemType.economyType() = when (this) {
    ItemType.Note -> com.juice.core.economy.ItemType.NOTE
    ItemType.Task -> com.juice.core.economy.ItemType.TASK
    ItemType.Goal -> com.juice.core.economy.ItemType.GOAL
    ItemType.Project -> com.juice.core.economy.ItemType.PROJECT
    ItemType.Routine -> com.juice.core.economy.ItemType.ROUTINE
}
