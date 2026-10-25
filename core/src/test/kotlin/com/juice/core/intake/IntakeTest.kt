package com.juice.core.intake

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IntakeTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private fun json(deadline: String? = null, subItems: String = "null", title: String = "Do laundry") =
        """{"type":"Goal","title":"$title","completion_condition":null,
            "deadline":${deadline?.let { "\"$it\"" } ?: "null"},"sub_items":$subItems}"""

    private fun validate(s: String) = IntakeValidator.validate(s, "raw text", now)

    @Test fun keepsDeadlineWithinOneYear() {
        assertEquals(Instant.parse("2026-06-01T18:00:00Z"), validate(json("2026-06-01T18:00:00Z")).deadline)
    }

    @Test fun dropsDeadlineTwoYearsOut() {
        val item = validate(json("2028-01-01T00:00:00Z"))
        assertNull(item.deadline)
        assertEquals(ItemType.Goal, item.type)
    }

    @Test fun dropsPastAndUnparsableDeadlines() {
        assertNull(validate(json("2025-12-31T00:00:00Z")).deadline)
        assertNull(validate(json("tomorrow")).deadline)
    }

    @Test fun malformedJsonStaysNote() {
        assertEquals(ParsedItem(ItemType.Note, "raw text"), validate("{not json"))
    }

    @Test fun schemaViolationsGiveNote() {
        assertEquals(ItemType.Note, validate("""{"type":"Epic","title":"x"}""").type)
        assertEquals(ItemType.Note, validate("""{"type":"Task"}""").type)
        assertEquals(ItemType.Note, validate("[]").type)
    }

    @Test fun moreThanTenSubItemsAreDroppedButTheItemStays() {
        val many = (1..11).joinToString(",", "[", "]") { "\"s$it\"" }
        val item = validate(json(subItems = many))
        assertEquals(ItemType.Goal, item.type)
        assertEquals(emptyList(), item.subItems)
        val ten = (1..10).joinToString(",", "[", "]") { "\"s$it\"" }
        assertEquals(10, validate(json(subItems = ten)).subItems.size)
    }

    @Test fun malformedSubItemsAreDroppedButTypeAndTitleStay() {
        for (bad in listOf("\"oops\"", "[1,2]", "{\"a\":1}")) {
            assertEquals(ParsedItem(ItemType.Goal, "Do laundry"), validate(json(subItems = bad)))
        }
    }

    @Test fun badCompletionConditionIsDropped() {
        val item = validate("""{"type":"Goal","title":"x","completion_condition":5}""")
        assertEquals(ParsedItem(ItemType.Goal, "x"), item)
    }

    @Test fun rejectionsAreReportedWithAReason() {
        val reasons = mutableListOf<String>()
        IntakeValidator.validate(json("tomorrow", subItems = "\"x\""), "raw", now) { reasons += it }
        assertEquals(2, reasons.size)
        IntakeValidator.validate("""{"type":"Epic","title":"x"}""", "raw", now) { reasons += it }
        assertEquals("note fallback: bad type", reasons.last())
    }

    @Test fun modelNoteWithoutDeadlineKeepsTheRulesTypeAndDeadline() {
        val rules = ParsedItem(ItemType.Goal, "call the dentist", deadline = now.plusSeconds(60))
        val model = ParsedItem(ItemType.Note, "Call the dentist")
        assertEquals(model.copy(type = ItemType.Goal, deadline = rules.deadline), model.improvingOn(rules))
        val task = model.copy(type = ItemType.Task)
        assertEquals(ItemType.Goal, task.improvingOn(rules).type)
    }

    @Test fun modelResultIsKeptWhenItHasADeadlineOrIsNotNoteOrTask() {
        val rules = ParsedItem(ItemType.Goal, "x", deadline = now.plusSeconds(60))
        val timed = ParsedItem(ItemType.Task, "x", deadline = now.plusSeconds(5))
        assertEquals(timed, timed.improvingOn(rules))
        val project = ParsedItem(ItemType.Project, "x")
        assertEquals(project, project.improvingOn(rules))
        assertEquals(ParsedItem(ItemType.Note, "x"), ParsedItem(ItemType.Note, "x").improvingOn(ParsedItem(ItemType.Note, "x")))
    }

    @Test fun titlesAreCapped() {
        val item = validate(json(title = "a".repeat(500)))
        assertEquals(IntakeValidator.MAX_TITLE_LENGTH, item.title.length)
    }

    @Test fun datePhraseGivesGoalPlainThoughtGivesNote() {
        assertEquals(ItemType.Goal, RulesFallback.classify("do laundry by 6pm").type)
        assertEquals(ItemType.Goal, RulesFallback.classify("call mum tomorrow").type)
        assertEquals(ItemType.Note, RulesFallback.classify("ideas for the garden").type)
        assertEquals("call mum tomorrow", RulesFallback.classify("call mum tomorrow").title)
    }

    @Test fun actionVerbGivesTaskAndColonListGivesProject() {
        assertEquals(ItemType.Task, RulesFallback.classify("call the dentist").type)
        val project = RulesFallback.classify("Launch: write copy, record demo and ship")
        assertEquals(ItemType.Project, project.type)
        assertEquals("Launch", project.title)
        assertEquals(listOf("Write copy", "Record demo", "Ship"), project.subItems)
        assertEquals(ItemType.Note, RulesFallback.classify("note: just one thing").type)
    }

    @Test fun leadingVerbGivesTaskButMidSentenceVerbGivesNote() {
        for (text in listOf("find my passport", "go to the bank", "get milk", "Please pick up the parcel", "to remember the charger")) {
            assertEquals(ItemType.Task, RulesFallback.classify(text).type, text)
        }
        for (text in listOf("the weekend plan was great", "idea for an app", "the report I should write")) {
            assertEquals(ItemType.Note, RulesFallback.classify(text).type, text)
        }
    }

    @Test fun modelNoteBecomesTaskWhenTheRulesFoundALeadingVerb() {
        val rules = RulesFallback.classify("get the car washed")
        val model = ParsedItem(ItemType.Note, "Car wash", completionCondition = "washed")
        assertEquals(model.copy(type = ItemType.Task), model.improvingOn(rules))
    }

    @Test fun simplePhrasesResolveToAFutureDeadline() {
        val now = java.time.ZonedDateTime.parse("2026-03-02T10:00:00Z[UTC]") // a Monday
        assertEquals(Instant.parse("2026-03-02T18:00:00Z"), RulesFallback.classify("send invoice by 6pm", now).deadline)
        assertEquals(Instant.parse("2026-03-02T18:00:00Z"), RulesFallback.classify("send invoice by 6", now).deadline)
        assertEquals(Instant.parse("2026-03-03T08:00:00Z"), RulesFallback.classify("gym at 8am", now).deadline)
        // A day with no time means by the end of that day.
        assertEquals(Instant.parse("2026-03-03T23:59:00Z"), RulesFallback.classify("call mum tomorrow", now).deadline)
        assertEquals(Instant.parse("2026-03-06T23:59:00Z"), RulesFallback.classify("pay rent friday", now).deadline)
        assertEquals(Instant.parse("2026-03-02T23:59:00Z"), RulesFallback.classify("call mum tonight", now).deadline)
        assertNull(RulesFallback.classify("send invoice by 6pm").deadline)
    }

    @Test fun timeframesMakeAGoalDueAtTheirEnd() {
        val now = java.time.ZonedDateTime.parse("2026-03-02T10:00:00Z[UTC]") // a Monday
        fun due(text: String) = RulesFallback.classify(text, now).also { assertEquals(ItemType.Goal, it.type, text) }.deadline
        assertEquals(Instant.parse("2026-03-08T23:59:00Z"), due("i need to buy a dryer before the end of the week"))
        assertEquals(Instant.parse("2026-03-08T23:59:00Z"), due("tidy the garage this weekend"))
        assertEquals(Instant.parse("2026-03-15T23:59:00Z"), due("book flights next week"))
        assertEquals(Instant.parse("2026-03-31T23:59:00Z"), due("renew passport by end of month"))
        assertEquals(Instant.parse("2026-03-02T23:59:00Z"), due("reply to Sam by end of day"))
        assertEquals(Instant.parse("2026-03-05T10:00:00Z"), due("send the draft in 3 days"))
        assertEquals(Instant.parse("2026-03-09T10:00:00Z"), due("finish the report within a week"))
        assertEquals(ItemType.Task, RulesFallback.classify("write the weekly report", now).type)
    }

    @Test fun lateParseKeepsEditedTitleAndFillsUntouchedFields() {
        val card = IntakeCard(ItemType.Note, "my own title", editedFields = setOf(IntakeField.Title))
        val parsed = ParsedItem(ItemType.Goal, "ai title", deadline = Instant.parse("2026-02-01T00:00:00Z"))
        val merged = card.mergeParse(parsed)
        assertEquals("my own title", merged.title)
        assertEquals(ItemType.Goal, merged.type)
        assertEquals(parsed.deadline, merged.deadline)
    }

    @Test fun lateParseNeverOverwritesTitleEvenIfUntouched() {
        assertEquals("raw", IntakeCard(ItemType.Note, "raw").mergeParse(ParsedItem(ItemType.Task, "ai")).title)
    }

    @Test fun lateParseKeepsEditedType() {
        val card = IntakeCard(ItemType.Task, "t", editedFields = setOf(IntakeField.Type))
        assertEquals(ItemType.Task, card.mergeParse(ParsedItem(ItemType.Goal, "t")).type)
    }

    @Test fun lateParseDropsCadenceWhenUserSetANonRoutineType() {
        val cadence = com.juice.core.routine.Cadence(setOf(java.time.DayOfWeek.SUNDAY))
        val card = IntakeCard(ItemType.Task, "t", editedFields = setOf(IntakeField.Type))
        val merged = card.mergeParse(ParsedItem(ItemType.Routine, "t", cadence = cadence))
        assertEquals(ItemType.Task, merged.type)
        assertEquals(null, merged.cadence)
        assertEquals(cadence, IntakeCard(ItemType.Note, "t").mergeParse(ParsedItem(ItemType.Routine, "t", cadence = cadence)).cadence)
    }

    private fun routine(text: String) = RulesFallback.classify(text)
    private fun cadence(vararg d: java.time.DayOfWeek, time: java.time.LocalTime? = null) =
        com.juice.core.routine.Cadence(d.toSet(), time)

    @Test fun rulesReadEverySundayAsRoutine() {
        val item = routine("go to the library every Sunday")
        assertEquals(ItemType.Routine, item.type)
        assertEquals("go to the library every Sunday", item.title)
        assertEquals(cadence(java.time.DayOfWeek.SUNDAY), item.cadence)
    }

    @Test fun rulesReadDailyWithClockTime() {
        val item = routine("go to the gym every day at 9")
        assertEquals(ItemType.Routine, item.type)
        assertEquals(com.juice.core.routine.Cadence(java.time.DayOfWeek.entries.toSet(), java.time.LocalTime.of(9, 0)), item.cadence)
        assertEquals(java.time.DayOfWeek.entries.toSet(), routine("stretch daily").cadence?.days)
    }

    @Test fun rulesReadWeekdayListsAndWeekly() {
        assertEquals(cadence(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.THURSDAY), routine("every Mon and Thu").cadence)
        assertEquals(cadence(java.time.DayOfWeek.FRIDAY), routine("weekly on Friday").cadence)
    }

    @Test fun rulesKeepGoalWithoutCadencePhrase() {
        assertEquals(ItemType.Goal, routine("call mum on Sunday").type)
        assertNull(routine("call mum on Sunday").cadence)
    }

    @Test fun validatorAcceptsRoutineCadence() {
        val item = validate("""{"type":"Routine","title":"Gym","deadline":"2026-06-01T18:00:00Z","cadence":{"days":["MON","thu"],"time":"07:30"}}""")
        assertEquals(ItemType.Routine, item.type)
        assertEquals(cadence(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.THURSDAY, time = java.time.LocalTime.of(7, 30)), item.cadence)
        assertNull(item.deadline)
        assertEquals(java.time.DayOfWeek.entries.toSet(), validate("""{"type":"Routine","title":"x","cadence":{"days":"daily","time":null}}""").cadence?.days)
    }

    @Test fun validatorDowngradesRoutineWithoutValidCadenceToTask() {
        for (bad in listOf("", ""","cadence":null""", ""","cadence":{"days":[],"time":null}""", ""","cadence":{"days":["FUNDAY"]}""", ""","cadence":{"days":"daily","time":"9am"}""")) {
            val reasons = mutableListOf<String>()
            val item = IntakeValidator.validate("""{"type":"Routine","title":"x"$bad}""", "raw", now) { reasons += it }
            assertEquals(ItemType.Task, item.type)
            assertNull(item.cadence)
            assertEquals(true, reasons.isNotEmpty())
        }
    }

    @Test fun validatorDropsCadenceOnOtherTypes() {
        val reasons = mutableListOf<String>()
        val item = IntakeValidator.validate("""{"type":"Goal","title":"x","cadence":{"days":"daily","time":null}}""", "raw", now) { reasons += it }
        assertEquals(ItemType.Goal, item.type)
        assertNull(item.cadence)
        assertEquals(1, reasons.size)
    }

    @Test fun improvingOnKeepsTheRulesRoutine() {
        val rules = routine("go to the library every Sunday")
        for (type in listOf(ItemType.Note, ItemType.Task, ItemType.Goal)) {
            val improved = ParsedItem(type, "Library").improvingOn(rules)
            assertEquals(ItemType.Routine, improved.type)
            assertEquals(rules.cadence, improved.cadence)
        }
        val project = ParsedItem(ItemType.Project, "p")
        assertEquals(project, project.improvingOn(rules))
    }
}
