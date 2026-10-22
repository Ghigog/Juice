package com.juice.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.juice.core.intake.IntakeValidator
import com.juice.core.intake.ItemType
import com.juice.core.intake.ParsedItem
import com.juice.core.model.Step
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
        Item(id, ItemType.Task, "Title $id", Instant.ofEpochMilli(1_800_000_000_000), status)

    @Test
    fun aLandedProjectKeepsAtMostMaxSubItemsAsStepsAndNoExtraCards() = runBlocking {
        repository.save(Item("p", ItemType.Note, "Move"))
        var n = 0
        val subItems = (1..IntakeValidator.MAX_SUB_ITEMS + 2).map { "step $it" }
        repository.land("p", ParsedItem(ItemType.Project, "Move", subItems = subItems)) { "s${n++}" }
        val active = repository.active()
        assertEquals(listOf("p"), active.map { it.id })
        assertEquals(subItems.take(IntakeValidator.MAX_SUB_ITEMS), active.single().steps.map { it.title })
        assertEquals(false, active.single().steps.any { it.done })
    }

    @Test
    fun migrationTurnsChildTaskCardsIntoStepsOfTheirProject() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE items (id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, title TEXT NOT NULL, " +
                                "deadlineMillis INTEGER, status TEXT NOT NULL, parentId TEXT, " +
                                "editedFields TEXT NOT NULL DEFAULT '', completionCondition TEXT)",
                        )
                        fun row(id: String, type: String, status: String, parent: String?) = db.execSQL(
                            "INSERT INTO items (id, type, title, status, parentId) VALUES (?, ?, ?, ?, ?)",
                            arrayOf(id, type, "Title $id", status, parent),
                        )
                        row("p", "Project", "Active", null)
                        row("a", "Task", "Active", "p")
                        row("b", "Task", "Completed", "p")
                        row("c", "Task", "Archived", "p")
                        row("solo", "Task", "Active", null)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val db = helper.writableDatabase
        JuiceDatabase.stepsInsideProjects.migrate(db)
        val ids = db.query("SELECT id FROM items ORDER BY rowid").use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList() }
        assertEquals(listOf("p", "solo"), ids)
        val steps = db.query("SELECT steps FROM items WHERE id = 'p'").use { it.moveToFirst(); it.getString(0).decodeSteps() }
        assertEquals(listOf(Step("a", "Title a", false), Step("b", "Title b", true)), steps)
        helper.close()
    }

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

    @Test
    fun reorderedItemsComeBackInTheChosenOrderAndKeepItAfterAnEdit() = runBlocking {
        listOf("a", "b", "c").forEach { repository.save(item(it)) }
        repository.reorder(listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b"), repository.active().map { it.id })
        repository.save(item("c").copy(title = "Edited"))
        assertEquals(listOf("c", "a", "b"), repository.active().map { it.id })
    }

    @Test
    fun aNewItemGoesAfterTheReorderedOnes() = runBlocking {
        listOf("a", "b").forEach { repository.save(item(it)) }
        repository.reorder(listOf("b", "a"))
        repository.save(item("z"))
        assertEquals(listOf("b", "a", "z"), repository.active().map { it.id })
    }
}
