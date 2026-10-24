package com.juice.core.character

/**
 * A persona the player can meet, unlocked at [unlockLevel] and checking in every [checkInIntervalDays].
 * [openingLine] is the character's own greeting; [mutterPitch] is the SoundPool rate of its muttering (0.5..2.0).
 * [hobby] is what a player-created character is about; null for built-ins.
 */
data class Character(
    val id: String,
    val name: String,
    val personaPrompt: String,
    val unlockLevel: Int,
    val checkInIntervalDays: Int,
    val openingLine: String,
    val mutterPitch: Float,
    val face: Face,
    val hobby: String? = null,
) {
    init {
        require(mutterPitch in 0.5f..2.0f) { "mutterPitch must be within 0.5..2.0" }
    }
}

object Characters {
    /** Player level at which the character creator opens. */
    const val CREATOR_UNLOCK_LEVEL = 10
    const val MAX_CREATED_CHARACTERS = 5
    const val CREATED_UNLOCK_LEVEL = 0
    const val CREATED_CHECK_IN_INTERVAL_DAYS = 14

    const val JIM_ID = "jim"

    val Jim = Character(
        id = JIM_ID,
        name = "Coach Humble Jumble Jim",
        personaPrompt = JimPersona.PROMPT,
        unlockLevel = 5,
        checkInIntervalDays = 14,
        openingLine = "Well howdy, friend! Jimothy here. I'll push you like a stubborn mule, but I ain't a doctor, so see one for health worries. What does your exercise look like right now?",
        mutterPitch = 0.8f,
        face = Face(eye = Shape.SQUARE, mouth = Shape.TRIANGLE),
    )

    val Nutritionist = Character(
        id = "nutritionist",
        name = "Nurse-free Nibbles Nora",
        personaPrompt = NutritionistPersona.PROMPT,
        unlockLevel = 10,
        checkInIntervalDays = 14,
        openingLine = "Hi hi, Nora here, your nibbles nerd! Let's get kale-ing it with shopping, cooking and new foods. I'm no doctor, so ask one about health or diets. What does your eating look like right now?",
        mutterPitch = 1.3f,
        face = Face(eye = Shape.CIRCLE, mouth = Shape.CIRCLE),
    )

    val SleepScientist = Character(
        id = "sleep-scientist",
        name = "Professor Snooze McDrowsy",
        personaPrompt = SleepScientistPersona.PROMPT,
        unlockLevel = 15,
        checkInIntervalDays = 14,
        openingLine = "*yawn* Professor Snooze here. Let's talk bedtime, putting devices down and winding down. I'm not a doctor, so see one for real sleep trouble. What time do you usually go to bed and get up?",
        mutterPitch = 0.6f,
        face = Face(eye = Shape.TRIANGLE, mouth = Shape.SQUARE),
    )

    val all: List<Character> = listOf(Jim, Nutritionist, SleepScientist)

    fun byId(id: String): Character? = all.firstOrNull { it.id == id }
}
