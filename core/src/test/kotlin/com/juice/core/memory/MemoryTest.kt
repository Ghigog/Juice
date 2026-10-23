package com.juice.core.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MemoryTest {
    private val known = setOf("jim", "nutritionist")

    private fun valid(json: String) = assertIs<MemoryUpdateResult.Valid>(MemoryUpdate.validate(json, known)).memory

    @Test fun extraAndOverLongNotesAreDropped() {
        val notes = (1..12).joinToString(",") { "\"n$it\"" } + ",\"" + "x".repeat(300) + "\""
        val memory = valid("""{"shared":[$notes],"characters":{}}""")
        assertEquals(10, memory.shared.size)
        assertTrue(memory.shared.all { it.length <= 140 })
        assertEquals(listOf("n1", "n2"), valid("""{"shared":["n1","${"x".repeat(141)}","n2"],"characters":{}}""").shared)
    }

    @Test fun characterNotesAreCappedToo() {
        val notes = (1..11).joinToString(",") { "\"n$it\"" }
        assertEquals(10, valid("""{"shared":[],"characters":{"jim":[$notes]}}""").characters.getValue("jim").size)
    }

    @Test fun validUpdateKeepsScopes() {
        val memory = valid("""{"shared":["a"],"characters":{"jim":["b"]}}""")
        assertEquals(Memory(listOf("a"), mapOf("jim" to listOf("b"))), memory)
    }

    @Test fun malformedOrWrongShapeIsRejected() {
        listOf(
            "not json", "[]", "{}", """{"shared":"a","characters":{}}""", """{"shared":[],"characters":[]}""",
            """{"shared":[1],"characters":{}}""", """{"shared":[],"characters":{"jim":"x"}}""",
            """{"shared":[],"characters":{"ghost":["x"]}}""",
        ).forEach { assertIs<MemoryUpdateResult.Rejected>(MemoryUpdate.validate(it, known), it) }
    }

    @Test fun promptSectionHasSharedAndOwnNotesOnly() {
        val memory = Memory(listOf("likes mornings"), mapOf("jim" to listOf("knee injury"), "nutritionist" to listOf("vegan")))
        val section = MemoryPrompt.promptSection(memory, "jim")
        assertTrue("likes mornings" in section && "knee injury" in section)
        assertFalse("vegan" in section)
    }

    @Test fun emptyMemoryGivesEmptySection() {
        assertEquals("", MemoryPrompt.promptSection(Memory(), "jim"))
        assertEquals("", MemoryPrompt.promptSection(Memory(characters = mapOf("nutritionist" to listOf("x"))), "jim"))
    }

    @Test fun updatePromptNamesScopesAndForbidsSecrets() {
        val prompt = MemoryPrompt.updatePrompt(Memory(listOf("a")), "hello")
        assertTrue("shared" in prompt && "hello" in prompt && "API keys" in prompt)
    }
}
