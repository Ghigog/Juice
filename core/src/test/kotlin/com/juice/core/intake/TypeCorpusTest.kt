package com.juice.core.intake

import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A labelled corpus of real-style captures and the type each should become. It pins what [RulesFallback.classify] does today:
 * rows it gets wrong are listed in [knownMisses] and asserted to still be wrong, so fixing a rule fails this test until the row
 * moves out of that list. See docs/intake-type-classification.md.
 */
class TypeCorpusTest {
    private val now = ZonedDateTime.of(2026, 1, 5, 9, 0, 0, 0, ZoneOffset.UTC) // a Monday

    private class Row(val text: String, val expected: ItemType)

    private fun row(text: String, expected: ItemType) = Row(text, expected)

    private val corpus = listOf(
        // Lists: a colon with items, or a verb with items.
        row("Groceries: milk, eggs and bread", ItemType.List),
        row("Packing: passport, charger, sunscreen", ItemType.List),
        row("Hardware store: tape, rope and boxes", ItemType.List),
        row("get tape, rope and boxes", ItemType.List),
        row("buy milk, eggs, bread and butter", ItemType.List),
        row("pick up screws, glue and sandpaper", ItemType.List),
        row("call mum, email Sam and book flights", ItemType.List),
        row("Weekend chores: laundry, vacuum and dishes", ItemType.List),
        // Prose with a colon, commas or 'and' that is not a list.
        row("formic factories: instead of a board, make a Factorio like factory to have multiple steps such as artists, designers and modellers", ItemType.Note),
        row("Idea: a widget that shows the weather, the time and my next meeting on the lock screen", ItemType.Note),
        row("Note to self: the landlord said the boiler is fine, but the radiator in the hall still leaks", ItemType.Note),
        row("Thought: maybe the overlay should fade out when idle, and come back on a shake", ItemType.Note),
        row("Reminder: Sam's birthday is a surprise, so don't mention the party", ItemType.Note),
        row("Recipe idea: roast the tomatoes first, then blend them with basil and a little cream", ItemType.Note),
        row("Why the build failed: the cache was stale, and the lockfile had drifted", ItemType.Note),
        row("I think we should rewrite the intro, trim the middle and keep the ending", ItemType.Note),
        // Notes.
        row("I'd like to learn the cello one day", ItemType.Note),
        row("The best pizza place is on Mill Street", ItemType.Note),
        row("Wifi password is on the fridge", ItemType.Note),
        row("Maybe a dog would suit us", ItemType.Note),
        row("Book recommendation from Ana: Piranesi", ItemType.Note),
        // Single tasks, some with a clause.
        row("call the dentist", ItemType.Task),
        row("email Sam about the invoice, then wait for the reply", ItemType.Task),
        row("find my passport", ItemType.Task),
        row("write the quarterly report and send it to Priya", ItemType.Task),
        row("fix the leaking tap in the bathroom", ItemType.Task),
        row("renew the car insurance, it is cheaper online", ItemType.Task),
        // Goals: a date or time, no steps.
        row("finish the slides by Friday", ItemType.Goal),
        row("submit the tax return by 6pm", ItemType.Goal),
        row("dentist tomorrow", ItemType.Goal),
        row("pay rent before 1 February", ItemType.Goal),
        row("Anniversary dinner on 14 March", ItemType.Goal),
        // Projects: a list with a date.
        row("Launch: write copy, record demo and ship by Friday", ItemType.Project),
        row("Move flat: pack boxes, book van and change address by 20 February", ItemType.Project),
        row("Trip prep: book hotel, buy tickets and renew passport before next month", ItemType.Project),
        row("get tape, rope and boxes by Thursday", ItemType.Project),
        row("Website relaunch: draft copy, review design and publish on 12 March", ItemType.Project),
        // Routines.
        row("water the plants every Sunday", ItemType.Routine),
        row("stretch every day", ItemType.Routine),
        row("take vitamins daily at 8am", ItemType.Routine),
        row("gym every Mon and Thu", ItemType.Routine),
        row("clean the kitchen every other day", ItemType.Routine),
        row("weekly review on Friday", ItemType.Routine),
        // Mixed prose with commas that a verb-led rule might take for a list.
        row("make a Factorio like factory, with artists, designers and modellers", ItemType.Task),
        row("write a blog post about why lists are good, bad and ugly", ItemType.Task),
        row("plan a surprise party for Sam, with cake, music and games", ItemType.Task),
        row("Shopping\n- milk\n- eggs\n- bread", ItemType.List),
        row("Notes from the call\n- pricing is too high\n- they want a demo", ItemType.List),
        row("Sunday roast: lamb, potatoes, carrots and gravy", ItemType.List),
        // Held out: written after the thresholds were set, not used to tune them.
        row("Camping: tent, stove, torch and sleeping bags", ItemType.List),
        row("Party shopping: balloons, candles and paper plates", ItemType.List),
        row("buy oat milk, bananas and coffee", ItemType.List),
        row("Reading list\n* Piranesi\n* Beloved\n* Circe", ItemType.List),
        row("Garden: dig the beds, plant beans and build a trellis by 30 April", ItemType.Project),
        row("Idea: first sort the budget, then decide on the venue and invite everyone", ItemType.Note),
        row("Observation: the app feels slow because the animations run twice, and the list jumps", ItemType.Note),
        row("Todo: ask Mia whether she can come, since the table seats six", ItemType.Note),
        row("Standup notes: I fixed the login bug and I will write the docs", ItemType.Note),
        row("The meeting went well, the team liked the demo and the budget is approved", ItemType.Note),
        row("send the invoice to Tom and chase the payment", ItemType.Task),
        row("book a table for four, near the window, for Saturday", ItemType.Goal),
        row("Sunday lunch at Gran's", ItemType.Goal),
        row("clear the shed every two weeks on Saturday", ItemType.Routine),
        row("Weekend trip\n- pack the car\n- book the ferry by Friday", ItemType.Project),
    )

    /** Rows the current rules misclassify, with the type they wrongly get. Each must stay wrong until a rule change flips it. */
    private val knownMisses = mapOf(
        "Book recommendation" to ItemType.Task, // "book" is a noun here
        "weekly review on Friday" to ItemType.Goal, // "weekly" alone is not a cadence
    ).mapKeys { (prefix, _) -> corpus.single { it.text.startsWith(prefix) }.text }

    @Test fun corpusCoversEveryTypeAndHasEnoughRows() {
        assertTrue(corpus.size >= 60, "rows: ${corpus.size}")
        assertEquals(ItemType.entries.toSet(), corpus.map { it.expected }.toSet())
        assertEquals(corpus.size, corpus.map { it.text }.toSet().size, "duplicate rows")
    }

    @Test fun everyRowGetsItsTypeExceptTheKnownMisses() {
        val wrong = corpus.filter { RulesFallback.classify(it.text, now).type != it.expected }
        assertEquals(knownMisses.keys, wrong.map { it.text }.toSet(), "rows the rules get wrong changed")
        for (r in wrong) assertEquals(knownMisses.getValue(r.text), RulesFallback.classify(r.text, now).type, r.text)
    }

    @Test fun theScreenshotCaptureIsANote() {
        val text = corpus.first { it.text.startsWith("formic factories") }.text
        assertEquals(ItemType.Note, RulesFallback.classify(text, now).type)
    }

    @Test fun bulletsBecomeTheStepsOfTheTitleLine() {
        val item = RulesFallback.classify("Notes from the call\n- pricing is too high\n\u2022 they want a demo", now)
        assertEquals(ItemType.List, item.type)
        assertEquals("Notes from the call", item.title)
        assertEquals(listOf("Pricing is too high", "They want a demo"), item.subItems)
    }

    @Test fun launchIsAProjectWithItsSteps() {
        val item = RulesFallback.classify("Launch: write copy, record demo and ship by Friday", now)
        assertEquals(ItemType.Project, item.type)
        assertEquals("Launch", item.title)
    }

    @Test fun getTapeRopeAndBoxesIsAList() {
        assertEquals(
            listOf("Get tape", "Get rope", "Get boxes"),
            RulesFallback.classify("get tape, rope and boxes", now).subItems,
        )
    }

    @Test fun groceriesStaysAList() {
        val item = RulesFallback.classify("Groceries: milk, eggs and bread", now)
        assertEquals(ItemType.List, item.type)
        assertEquals(listOf("Milk", "Eggs", "Bread"), item.subItems)
    }
}
