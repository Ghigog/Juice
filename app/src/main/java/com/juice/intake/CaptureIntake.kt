package com.juice.intake

import com.juice.core.intake.RulesFallback
import com.juice.core.model.Item
import com.juice.data.ItemRepository
import java.time.ZonedDateTime

/**
 * Sends a captured item to AI intake, or classifies it locally when on-device only is set or no
 * cloud parser is configured. Local results land at once, Project steps included.
 */
class CaptureIntake(
    private val repository: ItemRepository,
    private val onDeviceOnly: suspend () -> Boolean,
    private val enqueue: (itemId: String, text: String) -> Unit,
    private val cloudAvailable: () -> Boolean = { true },
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    /** Call once [item] is saved. Returns true when a cloud parse is on its way. */
    suspend fun submit(item: Item): Boolean {
        if (onDeviceOnly() || !cloudAvailable()) {
            repository.land(item.id, RulesFallback.classify(item.title, clock()))
            return false
        }
        enqueue(item.id, item.title)
        return true
    }
}
