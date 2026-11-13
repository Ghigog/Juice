package com.juice.overlay

import com.juice.core.intake.GroqKey
import com.juice.intake.GroqClient
import com.juice.intake.KeyCheck
import com.juice.intake.saveAndCheckGroqKey
import com.juice.settings.GroqKeyStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The overlay settings' Groq key row: save and check, remove, and the masked key it shows. */
class GroqKeyActions(
    private val store: GroqKeyStore,
    private val groqFor: (GroqKey) -> GroqClient,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _masked = MutableStateFlow<String?>(null)
    val masked: StateFlow<String?> = _masked

    /** Shows the stored key's mask, if there is a key. */
    fun loadMasked() {
        if (store.hasKey.value) scope.launch { _masked.value = withContext(io) { store.load()?.masked } }
    }

    /** Saves [key], has the client [groqFor] builds check it, then shows its mask, calls [onSaved] and hands [onChecked] the result. */
    fun save(key: GroqKey, onSaved: () -> Unit, onChecked: (KeyCheck) -> Unit) {
        scope.launch {
            val check = withContext(io) { saveAndCheckGroqKey(key, store, groqFor) }
            _masked.value = key.masked
            onSaved()
            onChecked(check)
        }
    }

    fun remove() {
        store.remove()
        _masked.value = null
    }
}
