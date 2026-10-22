package com.juice.core.character

/** A persona the player can meet, unlocked at [unlockLevel] and checking in every [checkInIntervalDays]. */
data class Character(
    val id: String,
    val name: String,
    val personaPrompt: String,
    val unlockLevel: Int,
    val checkInIntervalDays: Int,
)

object Characters {
    const val JIM_ID = "jim"

    val Jim = Character(
        id = JIM_ID,
        name = "Coach Humble Jumble Jim",
        personaPrompt = JimPersona.PROMPT,
        unlockLevel = 5,
        checkInIntervalDays = 14,
    )

    val all: List<Character> = listOf(Jim)

    fun byId(id: String): Character? = all.firstOrNull { it.id == id }
}
