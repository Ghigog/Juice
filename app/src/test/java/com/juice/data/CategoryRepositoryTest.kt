package com.juice.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.core.category.Category
import com.juice.core.category.CategoryPalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class CategoryRepositoryTest {
    private lateinit var db: JuiceDatabase
    private lateinit var repository: CategoryRepository
    private var next = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JuiceDatabase::class.java).allowMainThreadQueries().build()
        repository = CategoryRepository(db.categoryDao()) { "c${next++}" }
    }

    @After
    fun tearDown() = db.close()

    private val main = Category(Category.MAIN_ID, Category.MAIN_NAME)

    @Test
    fun aFreshDatabaseHasMainAlone() = runBlocking {
        assertEquals(listOf(main), repository.observe().first())
    }

    @Test
    fun createdCategoriesAreObservedInOrderAfterMain() = runBlocking {
        db.categoryDao().upsert(CategoryEntity(main.id, main.name, 0))
        val groceries = repository.create("  Groceries ")
        val work = repository.create("Work")
        assertEquals(Category("c0", "Groceries", CategoryPalette.colours[0].accent), groceries)
        assertEquals(listOf(main, groceries, work), repository.observe().first())
    }

    @Test
    fun aBlankNameCreatesNothing() = runBlocking {
        assertNull(repository.create("   "))
        assertEquals(listOf(main), repository.observe().first())
    }

    @Test
    fun renamingChangesACategoryButNeverMain() = runBlocking {
        db.categoryDao().upsert(CategoryEntity(main.id, main.name, 0))
        val groceries = repository.create("Groceries")!!
        repository.rename(groceries.id, "Shopping")
        repository.rename(Category.MAIN_ID, "Home")
        assertEquals(listOf(main, groceries.copy(name = "Shopping")), repository.observe().first())
    }

    @Test
    fun reorderSavesTheNewOrder() = runBlocking {
        db.categoryDao().upsert(CategoryEntity(main.id, main.name, 0))
        val work = repository.create("Work")!!
        val home = repository.create("Home")!!
        repository.reorder(listOf(main.id, home.id, work.id))
        assertEquals(listOf(main, home, work), repository.observe().first())
    }

    @Test
    fun reorderKeepsMainFirstAndIgnoresUnknownIds() = runBlocking {
        db.categoryDao().upsert(CategoryEntity(main.id, main.name, 0))
        val work = repository.create("Work")!!
        val home = repository.create("Home")!!
        repository.reorder(listOf(home.id, "ghost", work.id, main.id))
        assertEquals(listOf(main, home, work), repository.observe().first())
    }

    @Test
    fun reorderWorksWithoutAMainRow() = runBlocking {
        val work = repository.create("Work")!!
        val home = repository.create("Home")!!
        repository.reorder(listOf(home.id, work.id))
        assertEquals(listOf(main, home, work), repository.observe().first())
    }

    @Test
    fun aNewCategoryTakesTheFirstPaletteColourNoOtherUses() = runBlocking {
        val colours = CategoryPalette.colours
        db.categoryDao().upsert(CategoryEntity(main.id, main.name, 0, colours[0].accent))
        db.categoryDao().upsert(CategoryEntity("x", "X", 1, colours[1].accent))
        assertEquals(colours[2].accent, repository.create("game ideas")?.colour)
    }

    @Test
    fun aColourSurvivesClosingAndReopeningTheDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = "colour-test.db"
        val first = Room.databaseBuilder(context, JuiceDatabase::class.java, file).allowMainThreadQueries().build()
        val created = CategoryRepository(first.categoryDao()) { "g" }.create("game ideas")!!
        val picked = CategoryPalette.colours[5].accent
        CategoryRepository(first.categoryDao()) { "g" }.setColour(created.id, picked)
        first.close()
        val second = Room.databaseBuilder(context, JuiceDatabase::class.java, file).allowMainThreadQueries().build()
        try {
            assertEquals(picked, second.categoryDao().all().single { it.id == "g" }.colour)
        } finally {
            second.close()
            context.deleteDatabase(file)
        }
    }

    @Test
    fun settingMainsColourWithoutARowKeepsItsNameAndPosition() = runBlocking {
        val colour = CategoryPalette.colours[3].accent
        repository.setColour(Category.MAIN_ID, colour)
        val row = db.categoryDao().all().single()
        assertEquals(CategoryEntity(Category.MAIN_ID, Category.MAIN_NAME, 0, colour), row)
        assertEquals(listOf(Category(Category.MAIN_ID, Category.MAIN_NAME, colour)), repository.observe().first())
    }

    @Test
    fun ensureCoachMakesOneCategoryAfterTheLastAndKeepsARename() = runBlocking {
        db.categoryDao().upsert(CategoryEntity(main.id, main.name, 0))
        val work = repository.create("Work")!!
        repository.ensureCoach("jim", "Jim")
        val jim = Category("coach-jim", "Jim", CategoryPalette.colours[1].accent)
        assertEquals(listOf(main, work, jim), repository.observe().first())
        repository.rename(jim.id, "Coach J")
        repository.ensureCoach("jim", "Jim")
        assertEquals(listOf(main, work, jim.copy(name = "Coach J")), repository.observe().first())
    }

    @Test
    fun deleteMovesRemainingCardsToMainFirst() = runBlocking {
        db.categoryDao().upsert(CategoryEntity(main.id, main.name, 0))
        repository.ensureCoach("jim", "Jim")
        val items = ItemRepository(db.itemDao())
        items.save(com.juice.core.model.Item("a", com.juice.core.intake.ItemType.Task, "A", categoryId = "coach-jim"))
        repository.delete("coach-jim")
        assertEquals(listOf(main), repository.observe().first())
        assertEquals(Category.MAIN_ID, items.get("a")!!.categoryId)
    }

    @Test
    fun observeHidesTheCategoriesOfDisabledCoaches() = runBlocking {
        var hidden = setOf("jim")
        val hiding = CategoryRepository(db.categoryDao(), hiddenCharacterIds = { hidden }) { "x" }
        db.categoryDao().upsert(CategoryEntity(main.id, main.name, 0))
        hiding.ensureCoach("jim", "Jim")
        assertEquals(listOf(main), hiding.observe().first())
        hidden = emptySet()
        assertEquals(listOf("main", "coach-jim"), hiding.observe().first().map { it.id })
    }
}
