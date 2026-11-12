package com.juice.intake

import com.juice.data.GenerationResult
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CharacterGeneratorTest {
    @Test
    fun validPersonaBecomesCharacterWithHobby() = runTest {
        val json = """{"name":"Pipe Pete","personality":"Gruff.","personaPrompt":"You are Pete."}"""
        val result = CharacterGenerator({ json }, { "c1" }).generate(" plumbing ")
        val character = assertIs<GenerationResult.Success>(result).character
        assertEquals("c1", character.id)
        assertEquals("plumbing", character.hobby)
        assertEquals("Pipe Pete", character.name)
    }

    @Test
    fun invalidOutputUsesFallbackPersona() = runTest {
        val character = assertIs<GenerationResult.Success>(CharacterGenerator({ "{}" }, { "c1" }).generate("plumbing")).character
        assertEquals("Coach plumbing", character.name)
    }

    @Test
    fun failedCallIsAnError() = runTest {
        assertIs<GenerationResult.Failure>(CharacterGenerator({ throw IOException("offline") }).generate("plumbing"))
    }

    @Test
    fun onDeviceOnlyRefusesWithoutCallingTheModel() = runTest {
        var called = false
        val generator = CharacterGenerator({ called = true; "{}" }, onDeviceOnly = { true })
        assertEquals(GenerationResult.Failure("Needs cloud access: turn off On-device only"), generator.generate("plumbing"))
        assertEquals(false, called)
    }

    @Test
    fun noCloudKeyRefusesWithoutCallingTheModel() = runTest {
        var called = false
        val generator = CharacterGenerator({ called = true; "{}" }, cloudAvailable = { false })
        assertEquals(GenerationResult.Failure("Needs cloud access: turn off On-device only"), generator.generate("plumbing"))
        assertEquals(false, called)
    }
}
