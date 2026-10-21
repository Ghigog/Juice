package com.juice.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.core.intake.ItemType
import com.juice.core.intake.ParsedItem
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

    /** Saves run on Room's executor, so wait for [id] to be stored before reading it. */
    private suspend fun savedEventually(id: String, ready: (Item) -> Boolean = { true }): Item? {
        repeat(50) {
            repository.get(id)?.takeIf(ready)?.let { return it }
            delay(20)
        }
        return repository.get(id)
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
        assertEquals(ItemType.Note, savedEventually("new")?.type)
        assertNull(state.undo)
    }

    private val project = Item("p", ItemType.Project, "Move house")
    private val steps = listOf("a", "b").map { Item("s$it", ItemType.Task, it, parentId = "p") }

    @Test
    fun aLandedProjectShowsAsTheProjectAndLinkedTaskCards() = runBlocking {
        val ids = ArrayDeque(listOf("x", "y", "z"))
        val state = OverlayShellState(
            repository, CoroutineScope(Dispatchers.Unconfined), listOf(Item("n", ItemType.Note, "raw")),
            newId = { ids.removeFirst() },
        )
        state.land("n", ParsedItem(ItemType.Project, "Move house", subItems = listOf("1", "2", "3")))
        assertEquals(listOf("n", "x", "y", "z"), state.items.map { it.id })
        assertEquals(listOf(ItemType.Project, ItemType.Task, ItemType.Task, ItemType.Task), state.items.map { it.type })
        assertEquals(listOf(null, "n", "n", "n"), state.items.map { it.parentId })
        assertEquals("n", savedEventually("x")?.parentId)
    }

    @Test
    fun completingTheLastSubItemCompletesTheProjectAndUndoRevertsBoth() = runBlocking {
        (listOf(project) + steps).forEach { repository.save(it) }
        val state = state(project, *steps.toTypedArray())
        state.complete(steps[0])
        assertEquals(listOf(steps[0]), completed)
        state.complete(steps[1])
        assertEquals(listOf(steps[0], steps[1], project), completed)
        assertEquals(emptyList(), state.items)
        assertEquals(ItemStatus.Completed, savedEventually("p") { it.status == ItemStatus.Completed }?.status)
        state.undoLast()
        assertEquals(2, undone)
        assertEquals(listOf(project, steps[1]), state.items)
        assertEquals(ItemStatus.Active, statusEventually("p"))
    }

    @Test
    fun aTypeThePlayerPickedSurvivesALateParse() = runBlocking {
        val note = Item("n", ItemType.Note, "Call Sam")
        repository.save(note)
        val state = state(note)
        state.setType(note, ItemType.Goal)
        assertEquals(ItemType.Goal, state.items.single().type)
        savedEventually("n") { it.type == ItemType.Goal }
        repository.applyParse("n", ParsedItem(ItemType.Task, "Call Sam"))
        assertEquals(ItemType.Goal, repository.get("n")?.type)
    }

    @Test
    fun anEditedTitleSurvivesALateParseWhichStillFillsTheDeadline() = runBlocking {
        val note = Item("n", ItemType.Note, "cal sam")
        repository.save(note)
        val state = state(note)
        state.setTitle(note, " Call Sam ")
        assertEquals("Call Sam", state.items.single().title)
        savedEventually("n") { it.title == "Call Sam" }
        val deadline = java.time.Instant.ofEpochSecond(1_800_000_000)
        repository.applyParse("n", ParsedItem(ItemType.Task, "Call Samuel", deadline = deadline))
        val saved = repository.get("n")!!
        assertEquals("Call Sam", saved.title)
        assertEquals(deadline, saved.deadline)
        assertEquals(ItemType.Task, saved.type)
    }
}
