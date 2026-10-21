package com.juice.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.juice.core.progression.Flavour

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("juice_settings")

/** DataStore-backed [JuiceSettings] plus the first-run onboarding flag. */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<JuiceSettings> = store.data.map { p ->
        val d = JuiceSettings()
        JuiceSettings(
            sound = p[SOUND] ?: d.sound,
            tapButtons = p[TAP_BUTTONS] ?: d.tapButtons,
            haptics = p[HAPTICS] ?: d.haptics,
            reduceMotion = p[REDUCE_MOTION] ?: d.reduceMotion,
            blur = p[BLUR] ?: d.blur,
            onDeviceOnly = p[ON_DEVICE_ONLY] ?: d.onDeviceOnly,
            stripPosition = p[STRIP_POSITION]?.let { runCatching { StripPosition.valueOf(it) }.getOrNull() }
                ?: d.stripPosition,
            stripWidthPercent = (p[STRIP_WIDTH_PERCENT] ?: d.stripWidthPercent).coerceStripWidth(),
            flavour = p[FLAVOUR]?.let { runCatching { Flavour.valueOf(it) }.getOrNull() },
        )
    }

    val onboardingSeen: Flow<Boolean> = store.data.map { it[ONBOARDING_SEEN] ?: false }

    suspend fun current(): JuiceSettings = settings.first()

    suspend fun update(transform: (JuiceSettings) -> JuiceSettings) {
        val next = transform(current())
        store.edit { p ->
            p[SOUND] = next.sound
            p[TAP_BUTTONS] = next.tapButtons
            p[HAPTICS] = next.haptics
            p[REDUCE_MOTION] = next.reduceMotion
            p[BLUR] = next.blur
            p[ON_DEVICE_ONLY] = next.onDeviceOnly
            p[STRIP_POSITION] = next.stripPosition.name
            p[STRIP_WIDTH_PERCENT] = next.stripWidthPercent.coerceStripWidth()
            if (next.flavour == null) p.remove(FLAVOUR) else p[FLAVOUR] = next.flavour.name
        }
    }

    suspend fun markOnboardingSeen() {
        store.edit { it[ONBOARDING_SEEN] = true }
    }

    private companion object {
        val SOUND = booleanPreferencesKey("sound")
        val FLAVOUR = stringPreferencesKey("flavour")
        val TAP_BUTTONS = booleanPreferencesKey("tap_buttons")
        val HAPTICS = booleanPreferencesKey("haptics")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val BLUR = booleanPreferencesKey("blur")
        val ON_DEVICE_ONLY = booleanPreferencesKey("on_device_only")
        val STRIP_POSITION = stringPreferencesKey("strip_position")
        val STRIP_WIDTH_PERCENT = intPreferencesKey("strip_width_percent")
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
    }
}

private fun Int.coerceStripWidth() =
    coerceIn(JuiceSettings.MIN_STRIP_WIDTH_PERCENT, JuiceSettings.MAX_STRIP_WIDTH_PERCENT)
