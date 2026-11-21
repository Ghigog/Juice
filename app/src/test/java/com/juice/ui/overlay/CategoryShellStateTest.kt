package com.juice.ui.overlay

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.core.category.Category
import com.juice.core.category.CategoryPalette
import com.juice.coach.CoachClient
import com.juice.coach.CoachConversation
import com.juice.core.category.coachCategoryId
import com.juice.core.character.Characters
import com.juice.core.intake.ItemType
import com.juice.data.CharacterRepository
import com.juice.data.CharacterStore
import com.juice.data.ConversationStore
import com.juice.data.MemoryStore
import java.io.File
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
    fun renamingChangesTheNameAndColourOffMain() {
        val state = open()
        withWork()
        state.nextCategory()
        state.beginRename()
        val purple = CategoryPalette.colours[2]
        state.saveNaming("Job", purple.accent)
        assertEquals("Job", state.category.name)
        assertEquals(purple, state.categories.colourOf(state.category.id))
        val saved = runBlocking { categories.observe().first() }[1]
        assertEquals("Job", saved.name)
        assertEquals(purple.accent, saved.colour)
    }

    @Test
    fun mainOpensTheSheetAndTakesAColourButKeepsItsName() {
        val state = open()
        state.beginRename()
        assertEquals(main, state.categoryNaming?.renaming)
        val pink = CategoryPalette.colours[1]
        state.saveNaming("Renamed", pink.accent)
        assertEquals("Main", state.category.name)
        assertEquals(pink, state.categories.colourOf(Category.MAIN_ID))
        val saved = runBlocking { categories.observe().first() }[0]
        assertEquals("Main", saved.name)
        assertEquals(pink.accent, saved.colour)
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

    @Test
    fun movingACategoryReordersSavesAndKeepsTheCurrentOne() {
        val work = withWork()
        val home = runBlocking { categories.create("Home")!! }
        val state = open()
        state.nextCategory()
        assertTrue(state.moveCategory(home.id, 1))
        assertEquals(listOf(main, home, work), state.categories.list)
        assertEquals(work, state.category)
        assertEquals(listOf(main, home, work), runBlocking { categories.observe().first() })
        state.previousCategory()
        state.previousCategory()
        assertEquals(home, state.categories.next(main.id))
        assertFalse(state.moveCategory(home.id, 1))
        assertFalse(state.moveCategoryEarlier(home.id))
        assertTrue(state.moveCategoryLater(home.id))
        assertEquals(listOf(main, work, home), state.categories.list)
    }

    @Test
    fun aHeldCardIsCarriedToTheReorderedNeighbour() {
        withWork()
        val home = runBlocking { categories.create("Home")!! }
        val state = open()
        state.moveCategory(home.id, 1)
        val note = state.capture("Carry me")!!
        state.pickUp(note)
        assertTrue(state.carryHeld(forward = true))
        assertEquals(home, state.category)
    }

    private fun openWithJim(onChat: (() -> Unit) -> Unit = {}): Pair<OverlayShellState, CharacterRepository> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val characters = CharacterRepository(
            CharacterStore(context.getSharedPreferences("coach-category-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }),
            items, categories = categories,
        )
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val state = OverlayShellState(
            items, scope, emptyList(), newId = { "n${nextId++}" }, today = today, clock = clock,
            categoryRepository = categories, characters = characters, io = Dispatchers.Unconfined,
            registry = com.juice.core.character.CharacterRegistry { Characters.all },
            coachChatFor = { _, onFailed, onConfirmed ->
                onChat(onConfirmed)
                val conversation = CoachConversation(
                    Characters.Jim, CoachClient("key", "http://localhost"),
                    ConversationStore(File.createTempFile("conversations", ".json")), MemoryStore(File.createTempFile("memory", ".json")), { false }, { emptyList() },
                )
                CoachChatState(Characters.Jim, conversation, characters, scope, onFailed = onFailed, onConfirmed = onConfirmed, io = Dispatchers.Unconfined, clock = clock)
            },
        ).also { it.observe() }
        return state to characters
    }

    @Test
    fun completingTheMeetCardAddsTheCoachCategoryAndStaysOnMain() {
        val (state, characters) = openWithJim()
        runBlocking { characters.unlockAtLevel(Characters.Jim, 5) }
        assertEquals(listOf(main), state.categories.list)

        state.complete(runBlocking { items.get("meet-jim") }!!)

        assertEquals(listOf(Category.MAIN_ID, coachCategoryId("jim")), state.categories.list.map { it.id })
        assertEquals(Characters.Jim.name, state.categories.list.last().name)
        assertEquals(main, state.category)
    }

    @Test
    fun confirmingTheProposalMovesToTheCoachCategory() {
        var confirmed: () -> Unit = {}
        val (state, characters) = openWithJim { confirmed = it }
        runBlocking { characters.unlockAtLevel(Characters.Jim, 5) }
        state.complete(runBlocking { items.get("meet-jim") }!!)

        confirmed()

        assertEquals(coachCategoryId("jim"), state.category.id)
        assertEquals(Characters.Jim.name, state.category.name)
    }

    @Test
    fun meetingTheCoachAgainKeepsTheRenamedCategory() {
        val (state, characters) = openWithJim()
        runBlocking { characters.unlockAtLevel(Characters.Jim, 5) }
        val meet = runBlocking { items.get("meet-jim") }!!
        state.complete(meet)
        state.nextCategory()
        state.beginRename()
        state.saveNaming("Running")

        runBlocking { items.save(meet) }
        state.complete(runBlocking { items.get("meet-jim") }!!)

        val coach = state.categories.list.filter { it.id == coachCategoryId("jim") }
        assertEquals(listOf("Running"), coach.map { it.name })
        assertEquals("Running", runBlocking { categories.get(coachCategoryId("jim")) }!!.name)
    }
}
