package com.juice.data

import com.juice.core.intake.IntakeField
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.core.model.Step
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ItemEntityTest {
    @Test fun unknownTypeAndStatusReadAsActiveNote() {
        val item = ItemEntity("a", "Reminder", "Buy milk", null, "Snoozed").toItem()
        assertEquals(ItemType.Note, item.type)
        assertEquals(ItemStatus.Active, item.status)
        assertEquals("Buy milk", item.title)
    }

    @Test fun unknownEditedFieldsAreSkipped() {
        val item = ItemEntity("a", "Task", "T", null, "Active", editedFields = "Title,Colour").toItem()
        assertEquals(setOf(IntakeField.Title), item.editedFields)
    }

    @Test fun knownValuesRoundTrip() {
        val item = Item(
            id = "a", type = ItemType.Project, title = "P", deadline = Instant.ofEpochMilli(5_000),
            status = ItemStatus.Completed, editedFields = setOf(IntakeField.Title),
            steps = listOf(Step("s1", "One", true, Instant.ofEpochMilli(9))),
            createdAt = Instant.ofEpochMilli(1),
        )
        assertEquals(item, item.toEntity().toItem())
    }
}
