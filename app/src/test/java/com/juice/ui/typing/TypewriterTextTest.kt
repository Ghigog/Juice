package com.juice.ui.typing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class TypewriterTextTest {
    @get:Rule val compose = createComposeRule()

    private val letters = mutableListOf<Char>()

    @Test fun lettersAppearOneByOneAndEachIsReported() {
        compose.setContent { TypewriterText("Howdy", { letters += it }, delayMs = 30) }
        compose.onNodeWithText("").assertExists()
        compose.mainClock.advanceTimeBy(30)
        compose.onNodeWithText("H").assertExists()
        compose.mainClock.advanceTimeBy(30)
        compose.onNodeWithText("Ho").assertExists()
        assertEquals(listOf('H', 'o'), letters)
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Howdy").assertExists()
        assertEquals("Howdy".toList(), letters)
    }

    @Test fun tapShowsFullTextWithoutFurtherLetters() {
        // A long delay so the few ms the click gesture takes cannot reveal another letter.
        compose.setContent { TypewriterText("Howdy", { letters += it }, delayMs = 1000) }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("H").performClick()
        compose.onNodeWithText("Howdy").assertExists()
        compose.mainClock.advanceTimeBy(5000)
        assertEquals(listOf('H'), letters)
    }

    @Test fun whitespaceIsNotReportedAsALetter() {
        compose.setContent { TypewriterText("a b", { letters += it }, delayMs = 30) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("a b").assertExists()
        assertEquals(listOf('a', 'b'), letters)
    }

    @Test fun recompositionDoesNotRestartAFinishedReveal() {
        var tick by mutableIntStateOf(0)
        compose.setContent {
            tick
            TypewriterText("Hi", { letters += it }, delayMs = 30)
        }
        compose.mainClock.advanceTimeBy(300)
        tick++
        compose.waitForIdle()
        compose.onNodeWithText("Hi").assertExists()
        compose.mainClock.advanceTimeBy(300)
        assertEquals(listOf('H', 'i'), letters)
    }
}
