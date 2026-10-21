package com.juice.intake

import com.juice.core.intake.RulesFallback
import com.juice.core.model.Item
import com.juice.data.ItemRepository
import java.time.ZonedDateTime

/**
 * Classifies a captured item locally at once (Project steps included), then queues an AI parse
 * to refine it unless on-device only is set or no cloud parser is configured.
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
        // The rules result lands at once; a cloud parse then refines it without overwriting user edits.
        repository.land(item.id, RulesFallback.classify(item.title, clock()))
        if (onDeviceOnly() || !cloudAvailable()) return false
        enqueue(item.id, item.title)
        return true
    }
}
