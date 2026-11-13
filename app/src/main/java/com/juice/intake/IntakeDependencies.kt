package com.juice.intake

import com.juice.core.intake.GroqKey
import com.juice.data.ItemRepository
import com.juice.settings.SettingsRepository

/** What [IntakeWorker] needs, supplied by the application so intake never imports the app's container. */
interface IntakeDependencies {
    val settings: SettingsRepository
    /** The saved Groq key, read at run time; null when there is none. */
    val key: () -> GroqKey?
    val groqFor: (GroqKey) -> GroqClient
    val items: ItemRepository

    /** Implemented by the Application. */
    interface Provider {
        val intakeDependencies: IntakeDependencies
    }
}
