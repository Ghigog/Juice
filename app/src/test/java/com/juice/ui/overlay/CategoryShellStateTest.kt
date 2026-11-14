package com.juice.ui.overlay

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.core.category.Category
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.data.CategoryEntity
import com.juice.data.CategoryRepository
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryShellStateTest {
    private lateinit var db: JuiceDatabase
    private lateinit var items: ItemRepository
    private lateinit var categories: CategoryRepository
    private var nextId = 0
    private val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 1, 1)
    private val main = Category(Category.MAIN_ID, Category.MAIN_NAME)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), JuiceDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        items = ItemRepository(db.itemDao())
        categories = CategoryRepository(db.categoryDao()) { "c${nextId++}" }
        runBlocking { db.categoryDao().upsert(CategoryEntity(main.id, main.name, 0)) }
    }

    @After
    fun tearDown() = db.close()

    private fun open() = OverlayShellState(
        items, CoroutineScope(Dispatchers.Unconfined), emptyList(), newId = { "n${nextId++}" },
        today = today, clock = clock, categoryRepository = categories,
    ).also { it.observe() }

    private fun withWork(): Category = runBlocking { categories.create("Work")!! }

    @Test
    fun theOverlayAlwaysOpensOnMain() {
        val work = withWork()
        val first = open()
        first.nextCategory()
        assertEquals(work, first.category)
        assertEquals(main, open().category)
    }

    @Test
    fun capturingInWorkSavesItThereAndShowsItFirst() {
        val work = withWork()
        val state = open()
        state.nextCategory()
        val note = state.capture("Call Sam")!!
        assertEquals(note.id, state.items.first().id)
        assertEquals(work.id, runBlocking { items.get(note.id) }!!.categoryId)
        assertEquals(work.id, note.categoryId)
    }

    @Test
    fun steppingStopsAtBothEnds() {
        withWork()
        val state = open()
        assertFalse(state.previousCategory())
        assertTrue(state.nextCategory())
        assertFalse(state.nextCategory())
        assertTrue(state.previousCategory())
        assertEquals(main, state.category)
    }

    @Test
    fun cancellingOrSavingABlankNameCreatesNothing() {
        val state = open()
        state.beginNewCategory()
        state.cancelNaming()
        assertNull(state.categoryNaming)
        state.beginNewCategory()
        state.saveNaming("   ")
        assertNull(state.categoryNaming)
        assertEquals(listOf(main), state.categories.list)
        assertEquals(main, state.category)
        assertEquals(listOf(main), runBlocking { categories.observe().first() })
    }

    @Test
    fun aNamedCategoryIsCreatedAndOpenedEmpty() {
        val state = open()
        state.capture("In Main")
        state.beginNewCategory()
        state.saveNaming(" Home ")
        assertEquals("Home", state.category.name)
        assertEquals(listOf("Main", "Home"), state.categories.list.map { it.name })
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun renamingWorksOffMainOnly() {
        val state = open()
        state.beginRename()
        assertNull(state.categoryNaming)
        withWork()
        state.nextCategory()
        state.beginRename()
        state.saveNaming("Job")
        assertEquals("Job", state.category.name)
        assertEquals("Job", runBlocking { categories.observe().first() }[1].name)
    }

    @Test
    fun carryingTheHeldCardMovesItToTheTopOfTheNextCategory() {
        val work = withWork()
        runBlocking { items.save(Item("old", ItemType.Note, "Old", categoryId = work.id)) }
        val state = open()
        val note = state.capture("Carry me")!!
        state.pickUp(note)
        assertTrue(state.carryHeld(forward = true))
        assertEquals(work, state.category)
        assertNull(state.held)
        assertEquals(listOf(note.id, "old"), state.items.map { it.id })
        assertEquals(work.id, runBlocking { items.get(note.id) }!!.categoryId)
        state.previousCategory()
        assertTrue(state.items.none { it.id == note.id })
    }
}
