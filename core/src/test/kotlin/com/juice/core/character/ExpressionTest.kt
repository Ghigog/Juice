package com.juice.core.character

import kotlin.test.Test
import kotlin.test.assertEquals

class ExpressionTest {
    @Test fun noTwoExpressionsShareATransform() {
        assertEquals(7, Expression.entries.size)
        assertEquals(7, Expression.entries.map { it.transform }.toSet().size)
    }

    @Test fun parsesLeadingTagAndStripsIt() {
        assertEquals(Expression.HAPPY to "Nice work", Expression.parse("[happy] Nice work"))
        assertEquals(Expression.CHEEKY to "Hi", Expression.parse("  [CHEEKY]Hi"))
    }

    @Test fun missingOrUnknownTagIsNeutralWithFullText() {
        assertEquals(Expression.NEUTRAL to "Plain", Expression.parse("Plain"))
        assertEquals(Expression.NEUTRAL to "[bogus] hi", Expression.parse("[bogus] hi"))
    }
}
