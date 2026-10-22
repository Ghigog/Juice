package com.juice.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.core.intake.ItemType
import com.juice.core.intake.ParsedItem
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.core.model.Step
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import com.juice.ui.overlay.OverlayShellState
import com.juice.ui.overlay.Swipe
import com.juice.ui.overlay.swipeOf
import com.juice.core.gesture.GesturePoint
import kotlinx.coroutines.Dispatchers
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
        db = Room.inMemoryDatabaseBuilder(context, JuiceDatabase::class.java)
            .allowMainThreadQueries()
            // Run queries where they are called, so no save is still in flight when the database closes.
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
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
        assertEquals(ItemStatus.Active, repository.get("t")?.status)
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
        assertEquals(ItemStatus.Active, repository.get("t")?.status)
    }

    @Test
    fun capturedTextShowsAsASavedNote() = runBlocking {
        val state = state()
        state.capture("  buy milk ")
        assertEquals("buy milk", state.items.single().title)
        assertEquals(ItemType.Note, repository.get("new")?.type)
        assertNull(state.undo)
    }

    private val steps = listOf("a", "b").map { Step("s$it", it) }
    private val project = Item("p", ItemType.Project, "Move house", steps = steps)

    @Test
    fun aLandedProjectShowsAsOneCardWithItsSteps() = runBlocking {
        val ids = ArrayDeque(listOf("x", "y", "z"))
        val state = OverlayShellState(
            repository, CoroutineScope(Dispatchers.Unconfined), listOf(Item("n", ItemType.Note, "raw")),
            newId = { ids.removeFirst() },
        )
        state.land("n", ParsedItem(ItemType.Project, "Move house", subItems = listOf("1", "2", "3")))
        assertEquals(listOf("n"), state.items.map { it.id })
        assertEquals(listOf("1", "2", "3"), state.items.single().steps.map { it.title })
        assertEquals(3, repository.get("n")?.steps?.size)
    }

    @Test
    fun tickingAStepKeepsTheProjectAndTheLastOneCompletesItOnce() = runBlocking {
        repository.save(project)
        val state = state(project)
        state.completeStep(project, "sa")
        assertEquals(listOf(true, false), state.items.single().steps.map { it.done })
        assertEquals(emptyList(), completed)
        state.completeStep(state.items.single(), "sb")
        assertEquals(listOf(project.id), completed.map { it.id })
        assertEquals(emptyList(), state.items)
        assertEquals(ItemStatus.Completed, repository.get("p")?.status)
        state.undoLast()
        assertEquals(1, undone)
        assertEquals(listOf(false), state.items.single().steps.filter { it.id == "sb" }.map { it.done })
        assertEquals(ItemStatus.Active, repository.get("p")?.status)
    }

    @Test
    fun crossingAStepRemovesItFromTheProject() = runBlocking {
        repository.save(project)
        val state = state(project)
        state.removeStep(project, "sa")
        assertEquals(listOf("b"), state.items.single().steps.map { it.title })
        assertEquals(listOf("b"), repository.get("p")?.steps?.map { it.title })
    }

    @Test
    fun stepsOfListsOnlyOpenStepsAndGatesCompletion() {
        val state = state(project.copy(steps = listOf(Step("a", "a", done = true), Step("b", "b"))))
        assertEquals(listOf("b"), state.stepsOf(state.items.single()).map { it.id })
        assertEquals(false, state.canComplete(state.items.single()))
        assertEquals(true, state.canComplete(project.copy(steps = listOf(Step("a", "a", done = true)))))
    }

    @Test
    fun aTypeThePlayerPickedSurvivesALateParse() = runBlocking {
        val note = Item("n", ItemType.Note, "Call Sam")
        repository.save(note)
        val state = state(note)
        state.setType(note, ItemType.Goal)
        assertEquals(ItemType.Goal, state.items.single().type)
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
        val deadline = java.time.Instant.ofEpochSecond(1_800_000_000)
        repository.applyParse("n", ParsedItem(ItemType.Task, "Call Samuel", deadline = deadline))
        val saved = repository.get("n")!!
        assertEquals("Call Sam", saved.title)
        assertEquals(deadline, saved.deadline)
        assertEquals(ItemType.Task, saved.type)
    }

    @Test
    fun theTypePillStepsThroughTheTypes() {
        val note = Item("n", ItemType.Note, "Call Sam")
        val state = state(note)
        state.cycleType(state.items.single())
        state.cycleType(state.items.single())
        assertEquals(ItemType.Goal, state.items.single().type)
        repeat(2) { state.cycleType(state.items.single()) }
        assertEquals(ItemType.Note, state.items.single().type)
    }

    @Test
    fun aProjectWithOpenStepsCannotBeTickedDirectly() {
        val state = state(project)
        assertEquals(false, state.complete(project))
        assertEquals(emptyList(), completed)
        assertEquals(1, state.items.size)
    }

    @Test
    fun aCaptureWaitingOnTheCloudShowsAsParsingAndNewestComesFirst() = runBlocking {
        val state = OverlayShellState(
            repository, CoroutineScope(Dispatchers.Unconfined), listOf(task),
            newId = { "new" }, onCaptured = { true },
        )
        state.capture("buy milk")
        assertEquals(listOf("new", "t"), state.items.map { it.id })
        assertEquals(setOf("new"), state.parsing)
    }

    private val abc = arrayOf("a", "b", "c").map { Item(it, ItemType.Task, it) }.toTypedArray()

    @Test
    fun droppingAHeldCardAtANewIndexMovesItAndSavesTheOrder() = runBlocking {
        abc.forEach { repository.save(it) }
        val s = state(*abc)
        s.pickUp(abc[0])
        s.moveTarget(2)
        assertEquals(true, s.drop())
        assertEquals(listOf("b", "c", "a"), s.items.map { it.id })
        assertNull(s.held)
        assertEquals(listOf("b", "c", "a"), repository.active().map { it.id })
    }

    @Test
    fun droppingInPlaceLeavesTheOrderAndPutsTheCardDown() {
        val s = state(*abc)
        s.pickUp(abc[1])
        s.moveTarget(1)
        assertEquals(false, s.drop())
        assertEquals(listOf("a", "b", "c"), s.items.map { it.id })
        assertNull(s.held)
    }

    @Test
    fun cancellingAHoldKeepsTheOrder() {
        val s = state(*abc)
        s.pickUp(abc[0])
        s.moveTarget(2)
        s.cancelHold()
        assertNull(s.held)
        assertEquals(false, s.drop())
        assertEquals(listOf("a", "b", "c"), s.items.map { it.id })
    }

    private fun stroke(vararg p: Pair<Double, Double>) = p.map { GesturePoint(it.first, it.second) }

    @Test
    fun aLongFlatStrokeIsASwipeAndADrawnCheckOrCrossIsNot() {
        assertEquals(Swipe.RIGHT, swipeOf(stroke(0.0 to 10.0, 100.0 to 15.0), 50.0))
        assertEquals(Swipe.LEFT, swipeOf(stroke(100.0 to 10.0, 0.0 to 5.0), 50.0))
        assertNull(swipeOf(stroke(0.0 to 10.0, 20.0 to 10.0), 50.0))
        assertNull(swipeOf(stroke(0.0 to 50.0, 40.0 to 90.0, 100.0 to 0.0), 50.0))
        assertNull(swipeOf(stroke(0.0 to 0.0, 100.0 to 100.0), 50.0))
    }
}
