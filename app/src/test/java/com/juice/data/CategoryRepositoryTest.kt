package com.juice.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.core.category.Category
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
        assertEquals(Category("c0", "Groceries"), groceries)
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
        assertEquals(listOf(main, Category(groceries.id, "Shopping")), repository.observe().first())
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
}
