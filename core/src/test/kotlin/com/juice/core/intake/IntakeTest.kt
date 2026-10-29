package com.juice.core.intake

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    private fun withNotify(at: String?, text: String? = "Early start tomorrow", deadline: String = "2026-06-02T08:00:00Z", type: String = "Task") =
        """{"type":"$type","title":"Dentist","deadline":"$deadline",
            "notify_at":${at?.let { "\"$it\"" }},"notify_text":${text?.let { "\"$it\"" }}
            ${if (type == "Routine") ""","cadence":{"days":"daily","time":null}""" else ""}}"""

    private fun rejections(s: String) = mutableListOf<String>().also { IntakeValidator.validate(s, "raw text", now) { r -> it += r } }

    @Test fun keepsNotificationBeforeDeadline() {
        val item = validate(withNotify("2026-06-01T20:00:00+00:00", "  Early start tomorrow  "))
        assertEquals(Instant.parse("2026-06-01T20:00:00Z"), item.notifyAt)
        assertEquals("Early start tomorrow", item.notifyText)
        assertTrue(rejections(withNotify("2026-06-01T20:00:00Z")).isEmpty())
    }

    @Test fun dropsNotificationThatIsPastTooFarOrAfterDeadline() {
        for (at in listOf("2025-12-31T00:00:00Z", "2027-06-01T00:00:00Z", "2026-06-02T09:00:00Z")) {
            val json = withNotify(at, deadline = if (at.startsWith("2027")) "2026-12-01T00:00:00Z" else "2026-06-02T08:00:00Z")
            val item = validate(json)
            assertNull(item.notifyAt, at)
            assertNull(item.notifyText, at)
            assertTrue(rejections(json).isNotEmpty(), at)
        }
    }

    @Test fun dropsNotificationWithoutTimeOrText() {
        assertNull(validate(withNotify(null)).notifyText)
        assertNull(validate(withNotify("2026-06-01T20:00:00Z", "   ")).notifyAt)
        assertNull(validate(withNotify("2026-06-01T20:00:00Z", null)).notifyAt)
    }

    @Test fun noNotificationFieldsMeansNothingRejected() {
        val item = validate(json("2026-06-01T18:00:00Z"))
        assertNull(item.notifyAt)
        assertNull(item.notifyText)
        assertTrue(rejections(json("2026-06-01T18:00:00Z")).isEmpty())
    }

    @Test fun routineNeverKeepsNotification() {
        val item = validate(withNotify("2026-06-01T20:00:00Z", type = "Routine"))
        assertEquals(ItemType.Routine, item.type)
        assertNull(item.notifyAt)
        assertNull(item.notifyText)
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

    @Test fun actionVerbGivesTaskAndColonListGivesList() {
        assertEquals(ItemType.Task, RulesFallback.classify("call the dentist").type)
        val list = RulesFallback.classify("Launch: write copy, record demo and ship")
        assertEquals(ItemType.List, list.type)
        assertEquals("Launch", list.title)
        assertEquals(listOf("Write copy", "Record demo", "Ship"), list.subItems)
        assertEquals(ItemType.Project, RulesFallback.classify("Launch: write copy, record demo and ship by Friday").type)
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


    @Test fun joinAndRelatedVerbsGiveTaskWhenLeadingButNoteMidSentence() {
        for (verb in listOf("join", "attend", "enrol", "enroll", "subscribe", "reserve", "request", "remind", "follow", "watch", "speak",
            "talk", "discuss", "volunteer", "complete", "add", "remove", "delete")) {
            assertEquals(ItemType.Task, RulesFallback.classify("$verb the gym").type, verb)
        }
        assertEquals(Triple(ItemType.Task, listOf(ChipKind.Verb), listOf("join")), spans("please join the book club"))
        assertEquals(ItemType.Note, RulesFallback.classify("I want to join the gym").type)
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


    private val D = java.time.DayOfWeek.entries.toTypedArray().associateBy { it.name.take(3) }
    private val tuesday = java.time.ZonedDateTime.parse("2026-03-03T10:00:00Z[UTC]")

    @Test fun routineRuleReadsRepeatsIncludingEveryNWeeks() {
        val trash = routine("take out the trash every Tue/Thu")
        assertEquals(ItemType.Routine, trash.type)
        assertEquals(cadence(D.getValue("TUE"), D.getValue("THU")), trash.cadence)
        val towels = routine("put the towels to wash every two weeks on a Friday")
        assertEquals(ItemType.Routine, towels.type)
        assertEquals(com.juice.core.routine.Cadence(setOf(D.getValue("FRI")), null, 2), towels.cadence)
        assertEquals(3, routine("water plants every 3 weeks on Monday").cadence?.intervalWeeks)
    }

    @Test fun listRuleReadsListsWithAndWithoutAColon() {
        val item = RulesFallback.classify("get tape, rope and boxes when I go to the warehouse tomorrow", tuesday)
        assertEquals(ItemType.Project, item.type)
        assertEquals(listOf("Get tape", "Get rope", "Get boxes"), item.subItems)
        assertEquals(Instant.parse("2026-03-04T23:59:00Z"), item.deadline)
        val verbs = RulesFallback.classify("call mum, email Sam and book flights")
        assertEquals(ItemType.List, verbs.type)
        assertEquals(listOf("Call mum", "Email Sam", "Book flights"), verbs.subItems)
        val shopping = RulesFallback.classify("get parmesan, wraps, coarse salt", tuesday)
        assertEquals(ItemType.List, shopping.type)
        assertEquals(listOf("Get parmesan", "Get wraps", "Get coarse salt"), shopping.subItems)
        assertEquals(null, shopping.deadline)
        assertEquals(ItemType.Task, RulesFallback.classify("get milk and eggs").type)
    }

    @Test fun taskNoteAndGoalRules() {
        assertEquals(ItemType.Task, routine("Make a game where you play as a little pig").type)
        assertEquals(ItemType.Task, routine("Call the dentist").type)
        assertEquals(ItemType.Note, routine("I'd like to make a game where you play as a little pig").type)
        assertEquals(ItemType.Note, routine("one day I will learn the cello").type)
        assertEquals(ItemType.Goal, routine("the report is due friday").type)
        assertEquals(ItemType.Task, routine("grab a coffee").type)
    }

    @Test fun aListKeepsItsSubItemsAndNeverHasADeadline() {
        val list = ParsedItem(ItemType.List, "Chores", deadline = now, subItems = listOf("a", "b"))
        val card = IntakeCard(ItemType.Note, "Chores").mergeParse(list)
        assertEquals(ItemType.List, card.type)
        assertEquals(null, card.deadline)
        assertEquals(listOf("a", "b"), card.subItems)
        assertTrue(ItemType.List.hasSteps && ItemType.Project.hasSteps && !ItemType.Task.hasSteps)
    }

    @Test fun rulesAlwaysDecideTheTypeAndTheModelSuppliesTitleStepsAndDeadline() {
        val note = routine("I'd like to make a game where you play as a little pig")
        val invented = ParsedItem(ItemType.Project, "Pig game", deadline = now.plusSeconds(60), subItems = listOf("Design", "Code"))
        assertEquals(ParsedItem(ItemType.Note, "Pig game"), invented.improvingOn(note))

        val rules = RulesFallback.classify("get tape, rope and boxes at the warehouse tomorrow", tuesday)
        val merged = ParsedItem(ItemType.Task, "Warehouse run").improvingOn(rules)
        assertEquals(ItemType.Project, merged.type)
        assertEquals("Warehouse run", merged.title)
        assertEquals(listOf("Get tape", "Get rope", "Get boxes"), merged.subItems)
        assertEquals(rules.deadline, merged.deadline)

        val sunday = routine("go to the library every Sunday")
        val routineMerged = ParsedItem(ItemType.Goal, "Library", deadline = now).improvingOn(sunday)
        assertEquals(ItemType.Routine, routineMerged.type)
        assertEquals(sunday.cadence, routineMerged.cadence)
        assertNull(routineMerged.deadline)

        val task = ParsedItem(ItemType.Goal, "Dentist", deadline = now).improvingOn(routine("call the dentist"))
        assertEquals(ParsedItem(ItemType.Task, "Dentist", deadline = now), task)
    }

    private fun spans(text: String): Triple<ItemType, List<ChipKind>, List<String>> {
        val type = RulesFallback.classify(text).type
        val spans = RulesFallback.spansOf(text, type)
        val title = text.trim()
        return Triple(type, spans.map { it.kind }, spans.map { title.substring(it.range) })
    }

    @Test fun spansCoverTheMatchedWords() {
        assertEquals(Triple(ItemType.Task, listOf(ChipKind.Verb), listOf("call")), spans("call the dentist"))
        assertEquals(Triple(ItemType.Task, listOf(ChipKind.Verb), listOf("call")), spans("please call the dentist"))
        assertEquals(Triple(ItemType.Goal, listOf(ChipKind.Deadline), listOf("Tuesday")), spans("take out the trash on Tuesday"))
        assertEquals(Triple(ItemType.Goal, listOf(ChipKind.Deadline), listOf("next week")), spans("  plan trip next week"))
    }

    @Test fun routineSpansMarkIntervalAndEachWeekday() {
        assertEquals(
            Triple(ItemType.Routine, listOf(ChipKind.Interval, ChipKind.Weekday, ChipKind.Weekday), listOf("every 2 weeks", "Mon", "Thu")),
            spans("water plants every 2 weeks on Mon and Thu"),
        )
        assertEquals(
            Triple(ItemType.Routine, listOf(ChipKind.Interval, ChipKind.Weekday), listOf("every other week", "Fri")),
            spans("clean every other week on Fri"),
        )
        assertEquals(
            Triple(ItemType.Routine, listOf(ChipKind.Interval, ChipKind.Weekday, ChipKind.Weekday), listOf("every", "Mon", "Thu")),
            spans("bins every Mon and Thu"),
        )
        assertEquals(Triple(ItemType.Routine, listOf(ChipKind.Interval), listOf("every day")), spans("walk every day"))
    }

    @Test fun noteHasNoSpans() {
        assertEquals(Triple(ItemType.Note, emptyList(), emptyList()), spans("one day I'd like a boat"))
    }

    @Test fun lateParseKeepsEditedCadence() {
        val edited = com.juice.core.routine.Cadence(setOf(java.time.DayOfWeek.FRIDAY), intervalWeeks = 2)
        val parsed = com.juice.core.routine.Cadence(setOf(java.time.DayOfWeek.SUNDAY))
        val card = IntakeCard(ItemType.Routine, "t", cadence = edited, editedFields = setOf(IntakeField.Cadence))
        assertEquals(edited, card.mergeParse(ParsedItem(ItemType.Routine, "t", cadence = parsed)).cadence)
    }

    private val wednesday = java.time.ZonedDateTime.parse("2026-10-28T10:00:00Z[UTC]")

    private fun spanOf(title: String, type: ItemType, kind: ChipKind) =
        RulesFallback.spansOf(title, type).first { it.kind == kind }

    @Test fun relativeDeadlineNamesTheResolvedDayWithDateWhenFarAway() {
        val title = rewriteRelativeDeadline("take out the trash next week", wednesday)
        assertEquals("take out the trash on Sunday 8 Nov", title)
        assertEquals("plan on Sunday", rewriteRelativeDeadline("plan end of the week", wednesday).replace(" 1 Nov", ""))
        assertEquals("call the dentist", rewriteRelativeDeadline("call the dentist", wednesday))
    }

    @Test fun deadlineDayReplacesTheWeekdayAfterOn() {
        val title = "take out the trash on Tuesday"
        val span = spanOf(title, ItemType.Goal, ChipKind.Deadline)
        assertEquals("take out the trash on Thursday", withDeadlineDay(title, span, java.time.LocalDate.parse("2026-10-29"), wednesday))
    }

    @Test fun deadlineDayAddsOnWhenTheSpanHasNone() {
        val title = "plan trip tomorrow"
        val span = spanOf(title, ItemType.Goal, ChipKind.Deadline)
        assertEquals("plan trip on Friday", withDeadlineDay(title, span, java.time.LocalDate.parse("2026-10-30"), wednesday))
    }

    @Test fun intervalIsWrittenIntoTheTitleAndClassifiedBack() {
        val title = "water plants every Friday"
        val span = spanOf(title, ItemType.Routine, ChipKind.Interval)
        val two = withInterval(title, span, 2)
        assertEquals("water plants every 2 weeks Friday", two)
        val item = RulesFallback.classify(two)
        assertEquals(ItemType.Routine, item.type)
        assertEquals(2, item.cadence?.intervalWeeks)
        assertEquals(setOf(java.time.DayOfWeek.FRIDAY), item.cadence?.days)
        val back = withInterval(two, spanOf(two, ItemType.Routine, ChipKind.Interval), 1)
        assertEquals(title, back)
    }

    @Test fun everyNDaysIsARoutineWithAnIntervalChip() {
        val title = "give water to tomatoes every three days"
        val item = routine(title)
        assertEquals(ItemType.Routine, item.type)
        assertEquals(com.juice.core.routine.Cadence(java.time.DayOfWeek.entries.toSet(), null, intervalDays = 3), item.cadence)
        assertEquals(Triple(ItemType.Routine, listOf(ChipKind.Interval), listOf("every three days")), spans(title))
        assertEquals(2, routine("water ferns every other day").cadence?.intervalDays)
        assertEquals(4, routine("water ferns every 4 days").cadence?.intervalDays)
        assertEquals(2, routine("water ferns every other week on Fri").cadence?.intervalWeeks)
    }

    @Test fun intervalDaysIsWrittenIntoTheTitleAndClassifiedBack() {
        val title = "water plants every 2 weeks Friday"
        val three = withIntervalDays(title, spanOf(title, ItemType.Routine, ChipKind.Interval), 3)
        assertEquals("water plants every 3 days Friday", three)
        assertEquals(3, RulesFallback.classify(three).cadence?.intervalDays)
        assertEquals("water plants every day Friday", withIntervalDays(three, spanOf(three, ItemType.Routine, ChipKind.Interval), 1))
    }

    @Test fun weekdayKeepsTheStyleOfTheReplacedWord() {
        val short = "bins every Mon"
        assertEquals("bins every Thu", withWeekday(short, spanOf(short, ItemType.Routine, ChipKind.Weekday), java.time.DayOfWeek.THURSDAY))
        val full = "bins every Monday"
        assertEquals("bins every Thursday", withWeekday(full, spanOf(full, ItemType.Routine, ChipKind.Weekday), java.time.DayOfWeek.THURSDAY))
    }
}
