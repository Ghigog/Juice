package com.juice.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.juice.coach.ChatMessage
import com.juice.coach.ConversationStore
import com.juice.core.character.Availability
import com.juice.core.character.CharacterState
import java.io.File
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
import kotlin.test.assertTrue
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
    fun disablingKeepsTheCharactersItemsAndStopsNewCards() = runBlocking {
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

        assertEquals(4, items.byCharacter(jim.id).size)
        assertEquals(other.copy(createdAt = items.get("mine")?.createdAt), items.get("mine"))
        assertEquals(true, characters.state(jim.id).disabled)
    }

    @Test
    fun enablingBringsBackADisabledCharacterWithItsItemsAndConversation() = runBlocking {
        val conversations = ConversationStore(File.createTempFile("conv", ".json").also { it.delete() })
        val withChat = CharacterRepository(CharacterStore(ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("enable-test", Context.MODE_PRIVATE)), items, conversations)
        withChat.unlockAtLevel(jim, 5)
        conversations.save(jim.id, listOf(ChatMessage("user", "hi")))
        withChat.disable(jim)
        withChat.enable(jim)
        assertFalse(withChat.state(jim.id).disabled)
        assertEquals(1, items.byCharacter(jim.id).size)
        assertEquals(listOf(ChatMessage("user", "hi")), conversations.load(jim.id))
    }

    @Test
    fun resetDeletesItemsAndConversationAndSavesAFreshState() = runBlocking {
        val conversations = ConversationStore(File.createTempFile("conv", ".json").also { it.delete() })
        val withChat = CharacterRepository(CharacterStore(ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("reset-test", Context.MODE_PRIVATE)), items, conversations)
        withChat.unlockAtLevel(jim, 5)
        withChat.createTasks(jim.id, listOf(CoachTask.Add("Walk", daily)), today)
        withChat.recordConversation(jim.id)
        conversations.save(jim.id, listOf(ChatMessage("user", "hi")))
        withChat.disable(jim)

        withChat.reset(jim)

        assertEquals(emptyList(), items.byCharacter(jim.id))
        assertEquals(emptyList(), conversations.load(jim.id))
        assertEquals(CharacterState(), withChat.state(jim.id))
        withChat.unlockAtLevel(jim, 5)
        assertEquals(listOf("Meet Coach Humble Jumble Jim"), items.byCharacter(jim.id).map { it.title })
    }

    @Test
    fun resetDoesNothingWhileLocked() = runBlocking {
        items.save(Item("checkin-jim", ItemType.Task, "x", characterId = jim.id))
        characters.reset(jim)
        assertEquals(1, items.byCharacter(jim.id).size)
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
    fun aLostCharacterIsRestorableAndGetsAMeetCard() = runBlocking {
        characters.unlockAtLevel(jim, 5)
        items.delete("meet-jim")
        assertTrue(characters.canRestore(jim))
        characters.restore(jim)
        assertEquals(listOf("Meet Coach Humble Jumble Jim"), items.byCharacter(jim.id).map { it.title })
        assertFalse(characters.canRestore(jim))
    }

    @Test
    fun aCharacterWithACardAConversationOrLockedOrDisabledIsNotRestorable() = runBlocking {
        assertFalse(characters.canRestore(jim)) // locked
        characters.unlockAtLevel(jim, 5)
        assertFalse(characters.canRestore(jim)) // meet card
        items.delete("meet-jim")
        items.save(Item("checkin-jim", ItemType.Task, "Check in", characterId = jim.id))
        assertFalse(characters.canRestore(jim)) // check-in card
        items.delete("checkin-jim")
        characters.recordConversation(jim.id)
        assertFalse(characters.canRestore(jim)) // conversation
        characters.restore(jim)
        assertEquals(emptyList(), items.byCharacter(jim.id))
    }

    @Test
    fun aDisabledCharacterIsNotRestorable() = runBlocking {
        characters.unlockAtLevel(jim, 5)
        characters.disable(jim)
        assertFalse(characters.canRestore(jim))
        characters.restore(jim)
        assertEquals(listOf("meet-jim"), items.byCharacter(jim.id).map { it.id })
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
