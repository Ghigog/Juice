package com.juice.core.intake

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GroqKeyTest {
    @Test fun trimsPastedText() {
        assertEquals("gsk_abcdefgh1234", GroqKey.parse("  gsk_abcdefgh1234 \n")?.value)
    }

    @Test fun rejectsBlankAndInnerWhitespace() {
        assertNull(GroqKey.parse(""))
        assertNull(GroqKey.parse(" \n\t"))
        assertNull(GroqKey.parse("gsk_abc def"))
    }

    @Test fun maskedAndToStringShowOnlyTheLastFour() {
        val key = assertNotNull(GroqKey.parse("gsk_abcdefgh1234"))
        assertEquals("gsk_…1234", key.masked)
        assertEquals("gsk_…1234", key.toString())
        assertFalse("$key".contains("abcdefgh"))
    }
}
