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
import com.juice.core.routine.Cadence
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
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

    @Test
    fun concurrentSavesOfOneItemLandInTheOrderAsked() = runBlocking {
        repository.save(item("a"))
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        repeat(20) {
            val jobs = listOf(ItemStatus.Completed, ItemStatus.Active).map { status ->
                scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { repository.save(item("a", status)) }
            }
            jobs.joinAll()
            assertEquals(ItemStatus.Active, repository.get("a")?.status)
        }
    }

    private val wednesday = LocalDate.of(2026, 10, 21)
    private val sunday = LocalDate.of(2026, 10, 25)
    private val sundays = Cadence(setOf(DayOfWeek.SUNDAY))

    @Test
    fun migrationFiveToSixKeepsItemsWithoutCadence() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE items (id TEXT NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, deadlineMillis INTEGER, " +
                                "status TEXT NOT NULL, editedFields TEXT NOT NULL DEFAULT '', completionCondition TEXT, " +
                                "steps TEXT NOT NULL DEFAULT '[]', position INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(id))",
                        )
                        db.execSQL("INSERT INTO items (id, type, title, deadlineMillis, status) VALUES ('a', 'Task', 'A', 5, 'Active')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val db = helper.writableDatabase
        JuiceDatabase.addCadence.migrate(db)
        db.query("SELECT id, title, deadlineMillis, cadence, nextDueOn FROM items").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(listOf("a", "A", "5"), (0..2).map { i -> it.getString(i) })
            assertEquals(true, it.isNull(3) && it.isNull(4))
        }
        helper.close()
    }

    @Test
    fun aRoutineIsOnlyActiveOnItsDueDays() = runBlocking {
        repository.save(Item("r", ItemType.Routine, "Review", cadence = sundays, nextDueOn = sunday))
        repository.save(item("t"))
        assertEquals(listOf("t"), repository.active(wednesday).map { it.id })
        assertEquals(listOf("r", "t"), repository.active(sunday).map { it.id })
        assertEquals(Item("r", ItemType.Routine, "Review", cadence = sundays, nextDueOn = sunday), repository.get("r"))
    }

    @Test
    fun advancingAMissedRoutineSkipsToTheFirstDayAfterToday() {
        val missed = Item("r", ItemType.Routine, "Review", cadence = sundays, nextDueOn = sunday.minusWeeks(2))
        assertEquals(sunday, missed.advance(wednesday).nextDueOn)
        val ontime = missed.copy(nextDueOn = sunday)
        assertEquals(sunday.plusWeeks(1), ontime.advance(wednesday).nextDueOn)
    }

    @Test
    fun advancingARoutineKeepsOneRowAndSetsTheDeadline() = runBlocking {
        val zone = ZoneId.systemDefault()
        val timed = Cadence(setOf(DayOfWeek.SUNDAY), LocalTime.of(9, 0))
        repository.save(Item("r", ItemType.Routine, "Review", cadence = timed, nextDueOn = sunday.minusWeeks(2), status = ItemStatus.Completed))
        repository.advanceRoutine("r", wednesday)
        val advanced = repository.get("r")!!
        assertEquals(sunday, advanced.nextDueOn)
        assertEquals(ItemStatus.Active, advanced.status)
        assertEquals(sunday.atTime(9, 0).atZone(zone).toInstant(), advanced.deadline)
        assertEquals(1, repository.active(sunday).size)
    }

    @Test
    fun aDailyRoutineLandingFromAParseIsDueTodayWithItsDeadline() = runBlocking {
        repository.save(Item("r", ItemType.Note, "Stretch"))
        val daily = Cadence(DayOfWeek.entries.toSet(), LocalTime.of(9, 0))
        repository.land("r", ParsedItem(ItemType.Routine, "Stretch", cadence = daily), today = wednesday)
        val saved = repository.get("r")!!
        assertEquals(wednesday, saved.nextDueOn)
        assertEquals(daily, saved.cadence)
        assertEquals(wednesday.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant(), saved.deadline)
    }
}
