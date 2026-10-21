package com.juice.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import com.juice.ui.overlay.OverlayShellState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class OverlayShellStateTest {
    private lateinit var db: JuiceDatabase
    private lateinit var repository: ItemRepository
    private val completed = mutableListOf<Item>()
    private var undone = 0
    private val task = Item("t", ItemType.Task, "Task")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JuiceDatabase::class.java).allowMainThreadQueries().build()
        repository = ItemRepository(db.itemDao())
    }

    @After
    fun tearDown() = db.close()

    private fun state(vararg items: Item) = OverlayShellState(
        repository,
        CoroutineScope(Dispatchers.Unconfined),
        items.toList(),
        newId = { "new" },
        onCompleted = { completed += it },
        onCompletionUndone = { undone++ },
    )

    /** Saves run on Room's executor, so wait for the status to settle on what the last save wrote. */
    private suspend fun statusEventually(id: String): ItemStatus? {
        repeat(50) {
            delay(20)
            if (repository.get(id)?.status == ItemStatus.Active) return ItemStatus.Active
        }
        return repository.get(id)?.status
    }

    @Test
    fun completingSavesTheItemAndReportsItForJuice() = runBlocking {
        repository.save(task)
        val state = state(task)
        state.complete(task)
        assertEquals(listOf(task), completed)
        assertEquals(ItemStatus.Completed, repository.get("t")?.status)
        assertEquals(emptyList(), state.items)
    }

    @Test
    fun undoingACompletionRestoresItemAndReportsIt() = runBlocking {
        repository.save(task)
        val state = state(task)
        state.complete(task)
        state.undoLast()
        assertEquals(1, undone)
        assertEquals(ItemStatus.Active, statusEventually("t"))
        assertEquals(listOf(task), state.items)
    }

    @Test
    fun dismissingNeitherEarnsNorRevertsJuice() = runBlocking {
        repository.save(task)
        val state = state(task)
        state.dismiss(task)
        state.undoLast()
        assertEquals(emptyList(), completed)
        assertEquals(0, undone)
        assertEquals(ItemStatus.Active, statusEventually("t"))
    }

    @Test
    fun capturedTextShowsAsASavedNote() = runBlocking {
        val state = state()
        state.capture("  buy milk ")
        assertEquals("buy milk", state.items.single().title)
        assertEquals(ItemType.Note, repository.get("new")?.type)
        assertNull(state.undo)
    }
}
