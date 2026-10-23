package com.juice.data

import android.content.Context
import android.content.SharedPreferences
import com.juice.core.character.Availability
import com.juice.core.character.CharacterState
import java.time.Instant
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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

    /** Forgets everything saved for [characterId]. */
    fun clear(characterId: String) {
        prefs.edit().apply { prefs.all.keys.filter { it.startsWith("$characterId.") }.forEach(::remove) }.apply()
    }

    /** Ids of the characters currently disabled. */
    fun disabledIds(): Set<String> =
        prefs.all.keys.filter { it.endsWith(DISABLED_SUFFIX) && prefs.getBoolean(it, false) }.map { it.removeSuffix(DISABLED_SUFFIX) }.toSet()

    /** Emits whenever any saved state changes. */
    fun changes(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val NONE = Long.MIN_VALUE
        const val DISABLED_SUFFIX = ".disabled"
    }
}
