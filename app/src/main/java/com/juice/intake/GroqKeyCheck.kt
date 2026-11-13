package com.juice.intake

import com.juice.core.intake.GroqKey
import com.juice.settings.GroqKeyStore

/** Saves the pasted [key], then asks the client [groqFor] builds whether Groq accepts it; any failure reads as [KeyCheck.Unreachable]. */
fun saveAndCheckGroqKey(key: GroqKey, store: GroqKeyStore, groqFor: (GroqKey) -> GroqClient): KeyCheck {
    store.save(key)
    return try {
        groqFor(key).checkKey()
    } catch (e: Exception) {
        KeyCheck.Unreachable
    }
}
