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

    @Test fun tagListNamesEveryExpressionOnce() {
        assertEquals(Expression.entries.joinToString(", ") { "[${it.name.lowercase()}]" }, Expression.TAG_LIST)
    }
}
