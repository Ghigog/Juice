package com.juice.core.character

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonogramTest {
    private val pete = Characters.Jim.copy(id = "created-1", name = "Pete the Plumber")

    @Test fun initialsAreFirstLettersOfFirstAndLastWord() {
        assertEquals("PP", Monogram(pete).initials)
        assertEquals("C", Monogram(pete.copy(name = "Cher")).initials)
        assertEquals("AC", Monogram(pete.copy(name = "  \"Al\" Bo Cy")).initials)
    }

    @Test fun colourIsStableAndWithinHueRange() {
        assertEquals(Monogram(pete), Monogram(pete))
        assertTrue(Monogram(pete).hue in 0..359)
        assertTrue(Monogram(pete.copy(id = "")).hue in 0..359)
    }
}
