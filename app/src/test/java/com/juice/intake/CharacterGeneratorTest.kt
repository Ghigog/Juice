package com.juice.intake

import java.io.IOException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CharacterGeneratorTest {
    @Test
    fun validPersonaBecomesCharacterWithHobby() {
        val json = """{"name":"Pipe Pete","personality":"Gruff.","personaPrompt":"You are Pete."}"""
        val result = CharacterGenerator({ json }, { "c1" }).generate(" plumbing ")
        val character = assertIs<GenerationResult.Success>(result).character
        assertEquals("c1", character.id)
        assertEquals("plumbing", character.hobby)
        assertEquals("Pipe Pete", character.name)
    }

    @Test
    fun invalidOutputUsesFallbackPersona() {
        val character = assertIs<GenerationResult.Success>(CharacterGenerator({ "{}" }, { "c1" }).generate("plumbing")).character
        assertEquals("Coach plumbing", character.name)
    }

    @Test
    fun failedCallIsAnError() {
        assertIs<GenerationResult.Failure>(CharacterGenerator({ throw IOException("offline") }).generate("plumbing"))
    }
}
