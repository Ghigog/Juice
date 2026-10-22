package com.juice.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.juice.core.character.Availability
import com.juice.core.character.Characters
import com.juice.core.character.CoachTask
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.routine.Cadence
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class CharacterRepositoryTest {
    private lateinit var db: JuiceDatabase
    private lateinit var items: ItemRepository
    private lateinit var characters: CharacterRepository
    private val jim = Characters.Jim

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JuiceDatabase::class.java).allowMainThreadQueries().build()
        items = ItemRepository(db.itemDao())
        characters = CharacterRepository(CharacterStore(context.getSharedPreferences("characters-test", Context.MODE_PRIVATE)), items)
    }

    @After
    fun tearDown() = db.close()

    private val daily = Cadence(DayOfWeek.entries.toSet())
    private val today = LocalDate.of(2026, 10, 21)

    @Test
    fun migrationSixToSevenKeepsItemsWithNoCharacter() {
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
                        db.execSQL("INSERT INTO items (id, type, title, status) VALUES ('a', 'Task', 'A', 'Active')")
                        db.execSQL("INSERT INTO items (id, type, title, status) VALUES ('b', 'Note', 'B', 'Active')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val db = helper.writableDatabase
        JuiceDatabase.addCharacterId.migrate(db)
        db.query("SELECT id, characterId FROM items ORDER BY id").use {
            val rows = generateSequence { if (it.moveToNext()) it.getString(0) to it.isNull(1) else null }.toList()
            assertEquals(listOf("a" to true, "b" to true), rows)
        }
        helper.close()
    }

    @Test
    fun unlockingTwiceLeavesExactlyOneMeetCard() = runBlocking {
        characters.unlockAtLevel(jim, 5)
        characters.unlockAtLevel(jim, 6)
        val cards = items.byCharacter(jim.id)
        assertEquals(listOf("Meet Coach Humble Jumble Jim"), cards.map { it.title })
        assertEquals(ItemType.Task, cards.single().type)
        assertEquals(Availability.MetPending, characters.state(jim.id).availability)
    }

    @Test
    fun belowTheUnlockLevelNothingHappens() = runBlocking {
        characters.unlockAtLevel(jim, 4)
        assertEquals(emptyList(), items.byCharacter(jim.id))
    }

    @Test
    fun disablingRemovesAllTheCharactersCardsAndLeavesOthersAlone() = runBlocking {
        val other = Item("mine", ItemType.Task, "Mine")
        items.save(other)
        characters.unlockAtLevel(jim, 5)
        characters.createTasks(jim.id, listOf(CoachTask.Add("Walk", daily), CoachTask.Add("Stretch", daily)), today)
        characters.dismiss(jim)
        characters.restore(jim)
        items.save(Item("checkin-jim", ItemType.Task, "Check in", characterId = jim.id))
        assertEquals(4, items.byCharacter(jim.id).size)

        characters.disable(jim)
        characters.unlockAtLevel(jim, 9)

        assertEquals(emptyList(), items.byCharacter(jim.id))
        assertEquals(other, items.get("mine"))
        assertEquals(true, characters.state(jim.id).disabled)
    }

    @Test
    fun restoringADismissedCharacterBringsBackTheMeetCard() = runBlocking {
        characters.unlockAtLevel(jim, 5)
        characters.dismiss(jim)
        assertEquals(emptyList(), items.byCharacter(jim.id))
        characters.restore(jim)
        assertEquals(listOf("Meet Coach Humble Jumble Jim"), items.byCharacter(jim.id).map { it.title })
        assertFalse(characters.state(jim.id).dismissed)
    }

    @Test
    fun tasksFromAProposalAreRoutinesOfTheCharacter() = runBlocking {
        characters.createTasks(jim.id, listOf(CoachTask.Add("Walk", daily)), today, newId = { "w" })
        val walk = items.get("w")!!
        assertEquals(ItemType.Routine, walk.type)
        assertEquals(jim.id, walk.characterId)
        assertEquals(today, walk.nextDueOn)
        characters.createTasks(jim.id, listOf(CoachTask.Change("w", "Run", daily), CoachTask.Remove("w")), today)
        assertNull(items.get("w"))
    }

    @Test
    fun aCheckInCardIsCreatedOnceWhenDue() = runBlocking {
        characters.unlockAtLevel(jim, 5)
        val store = CharacterStore(ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("characters-test", Context.MODE_PRIVATE))
        val met = Instant.parse("2026-10-01T00:00:00Z")
        store.save(jim.id, store.load(jim.id).copy(availability = Availability.Unlocked, lastConversationAt = met))
        assertFalse(characters.createCheckInIfDue(jim, met.plusSeconds(3600)))
        assertEquals(true, characters.createCheckInIfDue(jim, Instant.parse("2026-10-16T00:00:00Z")))
        assertFalse(characters.createCheckInIfDue(jim, Instant.parse("2026-10-17T00:00:00Z")))
        assertEquals(2, items.byCharacter(jim.id).size)
    }
}
