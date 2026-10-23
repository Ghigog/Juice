package com.juice.core.character

/**
 * A persona the player can meet, unlocked at [unlockLevel] and checking in every [checkInIntervalDays].
 * [openingLine] is the character's own greeting; [mutterPitch] is the SoundPool rate of its muttering (0.5..2.0).
 */
data class Character(
    val id: String,
    val name: String,
    val personaPrompt: String,
    val unlockLevel: Int,
    val checkInIntervalDays: Int,
    val openingLine: String,
    val mutterPitch: Float,
) {
    init {
        require(mutterPitch in 0.5f..2.0f) { "mutterPitch must be within 0.5..2.0" }
    }
}

object Characters {
    const val JIM_ID = "jim"

    val Jim = Character(
        id = JIM_ID,
        name = "Coach Humble Jumble Jim",
        personaPrompt = JimPersona.PROMPT,
        unlockLevel = 5,
        checkInIntervalDays = 14,
        openingLine = "Well howdy, friend! Jimothy here. I'll push you like a stubborn mule, but I ain't a doctor, so see one for health worries. What're we workin' on?",
        mutterPitch = 0.8f,
    )

    val all: List<Character> = listOf(Jim)

    fun byId(id: String): Character? = all.firstOrNull { it.id == id }
}
