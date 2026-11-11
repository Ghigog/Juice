package com.juice.core.character

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExpressionTest {
    @Test fun thereAreNineExpressions() {
        assertEquals(
            listOf("neutral", "happy", "excited", "worried", "sad", "stern", "surprised", "thinking", "cheeky"),
            Expression.entries.map { it.name.lowercase() },
        )
    }

    @Test fun parsesLeadingTagAndStripsIt() {
        assertEquals(Expression.HAPPY to "Nice work", Expression.parse("[happy] Nice work"))
        assertEquals(Expression.CHEEKY to "Hi", Expression.parse("  [CHEEKY]Hi"))
    }

    @Test fun missingOrUnknownTagIsNeutralWithFullText() {
        assertEquals(Expression.NEUTRAL to "Plain", Expression.parse("Plain"))
        assertEquals(Expression.NEUTRAL to "[bogus] hi", Expression.parse("[bogus] hi"))
    }

    @Test fun oldTagsReadAsTheirMergedExpression() {
        assertEquals(Expression.STERN to "Grr", Expression.parse("[angry] Grr"))
        assertEquals(Expression.WORRIED to "Eek", Expression.parse("[scared] Eek"))
        assertEquals(Expression.STERN, Expression.named("ANGRY"))
        assertNull(Expression.named("bogus"))
        assertNull(Expression.named(null))
    }

    @Test fun stripsTagsFromAnywhereAndTheLastOneWins() {
        assertEquals(
            Expression.STERN to "How many minutes could you set aside? What's driving you to get moving now?",
            Expression.parse("How many minutes could you set aside?[neutral] What's driving you to get moving now?[stern]"),
        )
        assertEquals(Expression.SAD to "Well done. Hmm", Expression.parse("[HAPPY] Well done. [Sad]Hmm"))
        assertEquals(Expression.STERN to "a b", Expression.parse("a [scared]  [angry] b"))
    }

    @Test fun unknownTagsStayWhileKnownOnesGo() {
        assertEquals(Expression.HAPPY to "[bogus] hi there", Expression.parse("[bogus] hi [happy] there"))
    }

    @Test fun tagListNamesEveryExpressionOnce() {
        assertEquals(Expression.entries.joinToString(", ") { "[${it.name.lowercase()}]" }, Expression.TAG_LIST)
    }
}
