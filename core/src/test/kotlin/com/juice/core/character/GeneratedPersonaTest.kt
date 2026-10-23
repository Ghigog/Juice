package com.juice.core.character

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GeneratedPersonaTest {
    private fun json(name: String = "Pete", personality: String = "Gruff but kind.", prompt: String = "You are Pete.") =
        """{"name":"$name","personality":"$personality","personaPrompt":"$prompt"}"""

    @Test fun validOutputGetsProposalContractAppended() {
        val result = assertIs<GeneratedPersonaResult.Valid>(GeneratedPersona.parse(json()))
        assertEquals("Pete", result.persona.name)
        assertTrue(result.persona.personaPrompt.startsWith("You are Pete."))
        assertTrue(result.persona.personaPrompt.endsWith(GeneratedPersona.PROPOSAL_CONTRACT))
        assertTrue("\"tasks\"" in result.persona.personaPrompt)
    }

    @Test fun blankFieldIsRejected() {
        assertIs<GeneratedPersonaResult.Rejected>(GeneratedPersona.parse(json(name = "  ")))
        assertIs<GeneratedPersonaResult.Rejected>(GeneratedPersona.parse(json(prompt = "")))
    }

    @Test fun overlongFieldIsRejected() {
        assertIs<GeneratedPersonaResult.Rejected>(GeneratedPersona.parse(json(name = "x".repeat(GeneratedPersona.MAX_NAME_LENGTH + 1))))
    }

    @Test fun malformedOrIncompleteJsonIsRejected() {
        assertIs<GeneratedPersonaResult.Rejected>(GeneratedPersona.parse("not json"))
        assertIs<GeneratedPersonaResult.Rejected>(GeneratedPersona.parse("[]"))
        assertIs<GeneratedPersonaResult.Rejected>(GeneratedPersona.parse("""{"name":"Pete"}"""))
    }

    @Test fun fallbackPersonaCarriesHobbyAndContract() {
        val persona = GeneratedPersona.fallbackPersona("plumbing")
        assertTrue("plumbing" in persona.personaPrompt)
        assertTrue(persona.personaPrompt.endsWith(GeneratedPersona.PROPOSAL_CONTRACT))
    }

    @Test fun createdCharacterIsMetAtOnceAndKeepsItsHobby() {
        val persona = GeneratedPersona.fallbackPersona("plumbing")
        val c = persona.toCharacter("created-1", "plumbing")
        assertEquals(0, c.unlockLevel)
        assertEquals("plumbing", c.hobby)
        assertEquals(c, persona.toCharacter("created-1", "plumbing"))
    }
}
