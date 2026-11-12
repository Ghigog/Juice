package com.juice.data

import com.juice.core.character.Character

sealed interface GenerationResult {
    data class Success(val character: Character) : GenerationResult
    data class Failure(val message: String) : GenerationResult
}

/** Port the data layer uses to create a persona from a hobby; intake implements it. */
fun interface PersonaGenerator {
    suspend fun generate(hobby: String): GenerationResult
}
