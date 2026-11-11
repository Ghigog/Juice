package com.juice.intake

import com.juice.data.ItemRepository
import com.juice.llm.GroqClient
import com.juice.settings.SettingsRepository

/** What [IntakeWorker] needs, supplied by the application so intake never imports the app's container. */
interface IntakeDependencies {
    val settings: SettingsRepository
    /** Null without a built-in Groq key. */
    val groq: GroqClient?
    val items: ItemRepository

    /** Implemented by the Application. */
    interface Provider {
        val intakeDependencies: IntakeDependencies
    }
}
