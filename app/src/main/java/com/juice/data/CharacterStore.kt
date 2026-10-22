package com.juice.data

import android.content.Context
import android.content.SharedPreferences
import com.juice.core.character.Availability
import com.juice.core.character.CharacterState
import java.time.Instant

/** Each character's [CharacterState] as last saved, keyed by character id. */
class CharacterStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("characters", Context.MODE_PRIVATE))

    fun load(characterId: String): CharacterState {
        val availability = prefs.getString("$characterId.availability", null)?.let(Availability::valueOf)
            ?: return CharacterState()
        return CharacterState(
            availability = availability,
            dismissed = prefs.getBoolean("$characterId.dismissed", false),
            disabled = prefs.getBoolean("$characterId.disabled", false),
            lastConversationAt = prefs.getLong("$characterId.lastConversationAt", NONE).takeIf { it != NONE }?.let(Instant::ofEpochMilli),
        )
    }

    fun save(characterId: String, state: CharacterState) {
        prefs.edit()
            .putString("$characterId.availability", state.availability.name)
            .putBoolean("$characterId.dismissed", state.dismissed)
            .putBoolean("$characterId.disabled", state.disabled)
            .putLong("$characterId.lastConversationAt", state.lastConversationAt?.toEpochMilli() ?: NONE)
            .apply()
    }

    private companion object {
        const val NONE = Long.MIN_VALUE
    }
}
