package com.juice.ui.typing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TypewriterStateTest {
    @Test fun advanceRevealsOneLetterAtATime() {
        val state = TypewriterState("Hi", 30)
        assertEquals("", state.visible)
        assertEquals('H', state.advance())
        assertEquals("H", state.visible)
        assertFalse(state.done)
        assertEquals('i', state.advance())
        assertTrue(state.done)
        assertNull(state.advance())
        assertEquals("Hi", state.visible)
    }

    @Test fun skipShowsEverything() {
        val state = TypewriterState("Howdy", 30)
        state.advance()
        state.skip()
        assertEquals("Howdy", state.visible)
        assertTrue(state.done)
    }

    @Test fun emptyTextIsDone() {
        assertTrue(TypewriterState("", 30).done)
    }
}
