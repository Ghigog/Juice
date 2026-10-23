package com.juice.core.character

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CharacterRegistryTest {
    private val created = Characters.Jim.copy(id = "pete", name = "Pete the Plumber", hobby = "plumbing", unlockLevel = 0)
    private val registry = CharacterRegistry { listOf(created) }

    @Test fun findsBuiltInAndCreatedCharacters() {
        assertEquals(Characters.Jim, registry.byId(Characters.JIM_ID))
        assertEquals(created, registry.byId("pete"))
        assertEquals(Characters.all + created, registry.all())
    }

    @Test fun unknownIdIsNull() = assertNull(registry.byId("nobody"))

    @Test fun builtInsHaveNoHobbyAndCreatorConstantsAreSet() {
        assertNull(Characters.Jim.hobby)
        assertEquals(10, Characters.CREATOR_UNLOCK_LEVEL)
        assertEquals(5, Characters.MAX_CREATED_CHARACTERS)
    }
}
