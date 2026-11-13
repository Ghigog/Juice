package com.juice.data

import com.juice.core.memory.Memory
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryStoreTest {
    private val file = File.createTempFile("memory", ".json").also { it.deleteOnExit() }
    private val memory = Memory(shared = listOf("likes tea"), characters = mapOf("a" to listOf("a-note"), "b" to listOf("b-note")))

    @Test
    fun savedMemoryLoadsFromNewStoreOnSameFile() {
        MemoryStore(file).save(memory)
        assertEquals(memory, MemoryStore(file).load())
    }

    @Test
    fun missingFileLoadsEmpty() {
        file.delete()
        assertEquals(Memory(), MemoryStore(file).load())
    }

    @Test
    fun corruptFileLoadsEmpty() {
        file.writeText("{not json")
        assertEquals(Memory(), MemoryStore(file).load())
    }

    @Test
    fun deleteCharacterKeepsSharedNotesAndOtherCharacters() {
        val store = MemoryStore(file)
        store.save(memory)
        store.deleteCharacter("a")
        assertEquals(Memory(listOf("likes tea"), mapOf("b" to listOf("b-note"))), store.load())
    }

    @Test
    fun clearAllLoadsEmpty() {
        val store = MemoryStore(file)
        store.save(memory)
        store.clearAll()
        assertEquals(Memory(), store.load())
    }

    @Test
    fun unversionedFileFromPreviousReleaseLoadsSameNotes() {
        file.writeText("""{"shared":["likes tea"],"characters":{"a":["a-note"],"b":["b-note"]}}""")
        assertEquals(memory, MemoryStore(file).load())
    }

    @Test
    fun savedFileCarriesVersion1() {
        MemoryStore(file).save(memory)
        assertTrue(file.readText().contains(""""version":1"""))
    }

    @Test
    fun futureVersionLoadsEmptyAndIsNotRewritten() {
        val future = """{"version":99,"shared":["x"],"characters":{}}"""
        file.writeText(future)
        assertEquals(Memory(), MemoryStore(file).load())
        assertEquals(future, file.readText())
    }
}
