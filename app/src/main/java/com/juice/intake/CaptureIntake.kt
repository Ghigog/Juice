package com.juice.intake

import com.juice.core.intake.RulesFallback
import com.juice.core.model.Item
import com.juice.data.ItemRepository

/** Sends a captured item to AI intake, or classifies it locally when on-device only is set. */
class CaptureIntake(
    private val repository: ItemRepository,
    private val onDeviceOnly: suspend () -> Boolean,
    private val enqueue: (itemId: String, text: String, onDeviceOnly: Boolean) -> Unit,
) {
    /** Call once [item] is saved. */
    suspend fun submit(item: Item) {
        val local = onDeviceOnly()
        enqueue(item.id, item.title, local)
        if (local) repository.applyParse(item.id, RulesFallback.classify(item.title))
    }
}
