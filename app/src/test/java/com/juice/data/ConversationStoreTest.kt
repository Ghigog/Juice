package com.juice.data

import com.juice.core.character.Expression
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationStoreTest {
    private val file = File.createTempFile("conversations", ".json").also { it.deleteOnExit() }
    private val messages = listOf(ChatMessage("user", "hi"), ChatMessage("assistant", "hello", Expression.NEUTRAL))

    @Test
    fun savedConversationRoundTripsWithVersion1() {
        ConversationStore(file).save("a", messages)
        assertTrue(file.readText().contains(""""version":1"""))
        assertEquals(messages, ConversationStore(file).load("a"))
    }

    @Test
    fun unversionedFileFromPreviousReleaseLoadsSameMessages() {
        file.writeText("""{"a":[{"role":"user","content":"hi","expression":"neutral"},{"role":"assistant","content":"hello","expression":"neutral"}]}""")
        assertEquals(messages, ConversationStore(file).load("a"))
    }

    @Test
    fun savingKeepsOtherCharactersAndClearDropsOne() {
        val store = ConversationStore(file)
        store.save("a", messages)
        store.save("b", messages.take(1))
        store.clear("a")
        assertEquals(emptyList(), store.load("a"))
        assertEquals(messages.take(1), store.load("b"))
    }

    @Test
    fun futureVersionLoadsEmptyAndIsNotRewritten() {
        val future = """{"version":99,"a":[{"role":"user","content":"hi"}]}"""
        file.writeText(future)
        assertEquals(emptyList(), ConversationStore(file).load("a"))
        assertEquals(future, file.readText())
    }
}
