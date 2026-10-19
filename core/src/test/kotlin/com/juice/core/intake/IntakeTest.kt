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

    @Test fun moreThanTenSubItemsGiveNote() {
        val many = (1..11).joinToString(",", "[", "]") { "\"s$it\"" }
        assertEquals(ItemType.Note, validate(json(subItems = many)).type)
        val ten = (1..10).joinToString(",", "[", "]") { "\"s$it\"" }
        assertEquals(10, validate(json(subItems = ten)).subItems.size)
    }

    @Test fun titlesAreCapped() {
        val item = validate(json(title = "a".repeat(500)))
        assertEquals(IntakeValidator.MAX_TITLE_LENGTH, item.title.length)
    }

    @Test fun datePhraseGivesGoalOtherwiseNote() {
        assertEquals(ItemType.Goal, RulesFallback.classify("do laundry by 6pm").type)
        assertEquals(ItemType.Goal, RulesFallback.classify("call mum tomorrow").type)
        assertEquals(ItemType.Note, RulesFallback.classify("ideas for the garden").type)
        assertEquals("call mum tomorrow", RulesFallback.classify("call mum tomorrow").title)
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
}
