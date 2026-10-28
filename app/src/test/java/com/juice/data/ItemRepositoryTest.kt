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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
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
        repository = ItemRepository(db.itemDao()) { now }
    }

    private var now = Instant.ofEpochMilli(1_000)

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
    fun aLandedListIsStoredWithThreeOpenStepsAndNoDeadline() = runBlocking {
        repository.save(Item("l", ItemType.Note, "Chores"))
        var n = 0
        repository.land("l", ParsedItem(ItemType.List, "Chores", deadline = java.time.Instant.now(), subItems = listOf("a", "b", "c"))) { "s${n++}" }
        val list = repository.get("l")!!
        assertEquals(ItemType.List, list.type)
        assertEquals(null, list.deadline)
        assertEquals(listOf("a", "b", "c"), list.steps.map { it.title })
        assertEquals(false, list.steps.any { it.done })
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
    fun itemsOfHiddenCharactersAreLeftOutOfTheLists() = runBlocking {
        var hidden = setOf("jim")
        val changes = MutableSharedFlow<Unit>()
        val filtered = ItemRepository(db.itemDao(), { hidden }, changes) { now }
        filtered.save(item("a"))
        filtered.save(item("b").copy(characterId = "jim"))
        assertEquals(listOf("a"), filtered.active().map { it.id })
        assertEquals(listOf("a"), filtered.recapItems(now.minusSeconds(60)).map { it.id })
        assertEquals(listOf("a"), filtered.observeRecapItems(now.minusSeconds(60)).first().map { it.id })

        val seen = java.util.concurrent.CopyOnWriteArrayList<List<String>>()
        val emissions = async(Dispatchers.Default) { filtered.observeActive().map { list -> list.map { it.id }.sorted() }.take(2).toList(seen) }
        withTimeout(10_000) { while (seen.isEmpty()) delay(10) }
        changes.subscriptionCount.first { it > 0 }
        hidden = emptySet()
        changes.emit(Unit)
        withTimeout(10_000) { emissions.await() }
        assertEquals(listOf(listOf("a"), listOf("a", "b")), seen.toList())
    }

    @Test
    fun savedItemIsLoadedWithTheSameFields() = runBlocking {
        val saved = item("a")
        repository.save(saved)
        assertEquals(saved.copy(createdAt = now), repository.get("a"))
    }

    @Test
    fun nullableFieldsRoundTrip() = runBlocking {
        val saved = Item("n", ItemType.Note, "Just a note")
        repository.save(saved)
        assertEquals(saved.copy(createdAt = now), repository.get("n"))
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
    fun migrationSixToSevenKeepsItemsWithNullTimes() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE items (id TEXT NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, deadlineMillis INTEGER, " +
                                "status TEXT NOT NULL, editedFields TEXT NOT NULL DEFAULT '', completionCondition TEXT, " +
                                "steps TEXT NOT NULL DEFAULT '[]', position INTEGER NOT NULL DEFAULT 0, cadence TEXT, nextDueOn INTEGER, PRIMARY KEY(id))",
                        )
                        db.execSQL("INSERT INTO items (id, type, title, deadlineMillis, status) VALUES ('a', 'Task', 'A', 5, 'Active')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val db = helper.writableDatabase
        JuiceDatabase.addTimes.migrate(db)
        db.query("SELECT id, createdAtMillis, completedAtMillis FROM items").use {
            assertEquals(true, it.moveToFirst())
            assertEquals("a", it.getString(0))
            assertEquals(true, it.isNull(1) && it.isNull(2))
        }
        helper.close()
    }

    @Test
    fun createdAtIsStampedOnFirstSaveAndKeptAfter() = runBlocking {
        repository.save(item("a"))
        now = Instant.ofEpochMilli(2_000)
        repository.save(repository.get("a")!!.copy(title = "New"))
        repository.save(item("a"))
        assertEquals(Instant.ofEpochMilli(1_000), repository.get("a")!!.createdAt)
    }

    @Test
    fun completingStampsCompletedAtAndUndoClearsIt() = runBlocking {
        repository.save(item("a"))
        now = Instant.ofEpochMilli(2_000)
        repository.complete("a")
        assertEquals(Instant.ofEpochMilli(2_000), repository.get("a")!!.completedAt)
        now = Instant.ofEpochMilli(3_000)
        repository.save(repository.get("a")!!)
        assertEquals(Instant.ofEpochMilli(2_000), repository.get("a")!!.completedAt)
        repository.save(repository.get("a")!!.copy(status = ItemStatus.Active))
        assertNull(repository.get("a")!!.completedAt)
    }

    @Test
    fun advancingARoutineStampsItsLatestOccurrenceAndKeepsItAcrossLaterSaves() = runBlocking {
        repository.save(Item("r", ItemType.Routine, "Review", cadence = sundays, nextDueOn = sunday))
        now = Instant.ofEpochMilli(2_000)
        repository.advanceRoutine("r", wednesday)
        now = Instant.ofEpochMilli(3_000)
        repository.save(repository.get("r")!!.copy(title = "Weekly review"))
        assertEquals(Instant.ofEpochMilli(2_000), repository.get("r")!!.completedAt)
        assertEquals(ItemStatus.Active, repository.get("r")!!.status)
    }

    @Test
    fun oldStepJsonDecodesAndATickedStepGetsDoneAt() = runBlocking {
        db.itemDao().upsert(
            ItemEntity("p", "Project", "P", null, "Active", steps = """[{"id":"s1","title":"One","done":false},{"id":"s2","title":"Two","done":true}]"""),
        )
        val loaded = repository.get("p")!!
        assertEquals(listOf(null, null), loaded.steps.map { it.doneAt })
        now = Instant.ofEpochMilli(2_000)
        repository.save(loaded.copy(steps = loaded.steps.map { if (it.id == "s1") it.copy(done = true) else it }))
        assertEquals(listOf(Instant.ofEpochMilli(2_000), Instant.ofEpochMilli(2_000)), repository.get("p")!!.steps.map { it.doneAt })
        now = Instant.ofEpochMilli(3_000)
        val ticked = repository.get("p")!!
        repository.save(ticked.copy(steps = ticked.steps.map { if (it.id == "s2") it.copy(done = false) else it }))
        assertEquals(listOf(Instant.ofEpochMilli(2_000), null), repository.get("p")!!.steps.map { it.doneAt })
    }

    @Test
    fun recapItemsAreActiveOnesAndThoseCompletedSinceExcludingArchived() = runBlocking {
        listOf("active", "old", "recent", "gone").forEach { repository.save(item(it)) }
        now = Instant.ofEpochMilli(5_000)
        repository.complete("old")
        now = Instant.ofEpochMilli(9_000)
        repository.complete("recent")
        repository.archive("gone")
        assertEquals(listOf("active", "recent"), repository.recapItems(Instant.ofEpochMilli(8_000)).map { it.id })
    }

    @Test
    fun aRoutineIsOnlyActiveOnItsDueDays() = runBlocking {
        repository.save(Item("r", ItemType.Routine, "Review", cadence = sundays, nextDueOn = sunday))
        repository.save(item("t"))
        assertEquals(listOf("t"), repository.active(wednesday).map { it.id })
        assertEquals(listOf("r", "t"), repository.active(sunday).map { it.id })
        assertEquals(Item("r", ItemType.Routine, "Review", cadence = sundays, nextDueOn = sunday, createdAt = now), repository.get("r"))
    }

    @Test
    fun observedRoutinesAreEveryActiveRoutineWhateverItsDueDay() = runBlocking {
        val friday = LocalDate.of(2026, 10, 23)
        val tueThuSat = Cadence(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY))
        repository.save(Item("r", ItemType.Routine, "Review", cadence = tueThuSat, nextDueOn = friday.plusDays(1)))
        repository.save(Item("done", ItemType.Routine, "Done", cadence = sundays, nextDueOn = sunday, status = ItemStatus.Completed))
        repository.save(Item("old", ItemType.Routine, "Old", cadence = sundays, nextDueOn = sunday, status = ItemStatus.Archived))
        repository.save(item("t"))
        assertEquals(listOf("r"), repository.observeRoutines().first().map { it.id })
        assertEquals(emptyList(), repository.active(friday).map { it.id }.filter { it == "r" })
    }

    @Test
    fun aRoutineArchivedWhileObservedLeavesTheList() = runBlocking {
        repository.save(Item("r", ItemType.Routine, "Review", cadence = sundays, nextDueOn = sunday))
        val seen = async { repository.observeRoutines().take(2).toList().map { list -> list.map { it.id } } }
        delay(200)
        repository.archive("r")
        assertEquals(listOf(listOf("r"), emptyList()), withTimeout(5_000) { seen.await() })
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

    @Test
    fun starredCardsComeFirstEarliestStarredFirst() = runBlocking {
        listOf("c", "b", "a").forEach { repository.save(item(it)) }
        now = Instant.ofEpochMilli(2_000)
        repository.star("a")
        now = Instant.ofEpochMilli(3_000)
        repository.star("b")
        assertEquals(listOf("a", "b", "c"), repository.active().map { it.id })
        assertEquals(listOf("a", "b", "c"), repository.observeActive().first().map { it.id })
        assertEquals(listOf("a", "b", "c"), repository.recapItems(Instant.EPOCH).map { it.id })
    }

    @Test
    fun starringAgainKeepsTheOriginalPlace() = runBlocking {
        listOf("a", "b").forEach { repository.save(item(it)) }
        now = Instant.ofEpochMilli(2_000)
        repository.star("a")
        now = Instant.ofEpochMilli(3_000)
        repository.star("b")
        now = Instant.ofEpochMilli(4_000)
        repository.star("a")
        assertEquals(Instant.ofEpochMilli(2_000), repository.get("a")?.starredAt)
        assertEquals(listOf("a", "b"), repository.active().map { it.id })
    }

    @Test
    fun savingAnOlderCopyKeepsTheStar() = runBlocking {
        repository.save(item("a"))
        repository.star("a")
        repository.save(item("a"))
        assertEquals(now, repository.get("a")?.starredAt)
    }

    @Test
    fun aLandedGoalNamesTheDayOfItsRelativeDeadlineUnlessTheTitleWasEdited() = runBlocking {
        val now = Instant.parse("2026-10-28T12:00:00Z")
        val clocked = ItemRepository(db.itemDao(), clock = { now })
        val parsed = ParsedItem(ItemType.Goal, "take out the trash next week", deadline = now.plusSeconds(86_400))
        clocked.save(Item("a", ItemType.Note, "take out the trash next week"))
        clocked.applyParse("a", parsed)
        assertEquals(true, clocked.get("a")!!.title.startsWith("take out the trash on "))

        clocked.save(Item("b", ItemType.Note, "take out the trash next week", editedFields = setOf(com.juice.core.intake.IntakeField.Title)))
        clocked.applyParse("b", parsed)
        assertEquals("take out the trash next week", clocked.get("b")!!.title)
    }
}
