package com.juice.core.character

/** A character's avatar fallback: up to two [initials] (first and last word) on a colour with the given [hue] (0..359 degrees), stable per character id. */
data class Monogram(val initials: String, val hue: Int) {
    companion object {
        operator fun invoke(character: Character): Monogram = Monogram(initialsOf(character.name), hueOf(character.id))

        private fun initialsOf(name: String): String =
            name.split(Regex("\\s+")).mapNotNull { word -> word.firstOrNull { it.isLetter() } }
                .let { if (it.size > 2) listOf(it.first(), it.last()) else it }
                .joinToString("").uppercase()

        // Mask the sign so a negative hashCode still gives a hue in range.
        private fun hueOf(id: String): Int = (id.hashCode() and 0x7fffffff) % 360
    }
}
