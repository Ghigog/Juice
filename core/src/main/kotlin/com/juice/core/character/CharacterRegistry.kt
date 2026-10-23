package com.juice.core.character

/** Every character the player can meet: the built-in [Characters.all] plus the [created] ones. */
class CharacterRegistry(private val created: () -> List<Character>) {
    fun all(): List<Character> = Characters.all + created()

    fun byId(id: String): Character? = all().firstOrNull { it.id == id }
}
