package com.juice.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class ItemRepositoryTest {
    private lateinit var db: JuiceDatabase
    private lateinit var repository: ItemRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JuiceDatabase::class.java).allowMainThreadQueries().build()
        repository = ItemRepository(db.itemDao())
    }

    @After
    fun tearDown() = db.close()

    private fun item(id: String, status: ItemStatus = ItemStatus.Active) =
        Item(id, ItemType.Task, "Title $id", Instant.ofEpochMilli(1_800_000_000_000), status, parentId = "p")

    @Test
    fun savedItemIsLoadedWithTheSameFields() = runBlocking {
        val saved = item("a")
        repository.save(saved)
        assertEquals(saved, repository.get("a"))
    }

    @Test
    fun nullableFieldsRoundTrip() = runBlocking {
        val saved = Item("n", ItemType.Note, "Just a note")
        repository.save(saved)
        assertEquals(saved, repository.get("n"))
        assertNull(repository.get("missing"))
    }

    @Test
    fun completedAndArchivedItemsAreNotActive() = runBlocking {
        repository.save(item("a"))
        repository.save(item("b"))
        repository.save(item("c"))
        repository.complete("b")
        repository.archive("c")
        assertEquals(listOf("a"), repository.active().map { it.id })
        assertEquals(ItemStatus.Completed, repository.get("b")?.status)
        assertEquals(ItemStatus.Archived, repository.get("c")?.status)
    }
}
