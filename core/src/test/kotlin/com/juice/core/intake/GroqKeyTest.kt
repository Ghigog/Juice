package com.juice.core.intake

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GroqKeyTest {
    @Test fun trimsPastedText() {
        assertEquals("abcd_secret1234", GroqKey.parse("  abcd_secret1234 \n")?.value)
    }

    @Test fun rejectsBlankAndInnerWhitespace() {
        assertNull(GroqKey.parse(""))
        assertNull(GroqKey.parse(" \n\t"))
        assertNull(GroqKey.parse("abcd abc def"))
    }

    @Test fun maskedAndToStringShowTheFirstAndLastFour() {
        val key = assertNotNull(GroqKey.parse("abcd_secret1234"))
        assertEquals("abcd…1234", key.masked)
        assertEquals("abcd…1234", key.toString())
        assertFalse("$key".contains("secret"))
    }

    @Test fun shortKeysNeverMaskToTheWholeKey() {
        assertEquals("ab…78", assertNotNull(GroqKey.parse("abcd5678")).masked)
        assertEquals("…", assertNotNull(GroqKey.parse("abc")).masked)
    }
}
