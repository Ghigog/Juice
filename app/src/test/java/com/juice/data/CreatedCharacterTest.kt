package com.juice.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.core.character.Availability
import com.juice.core.routine.Cadence
import com.juice.core.character.CoachTask
import com.juice.intake.CharacterGenerator
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class CreatedCharacterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val validJson = """{"name":"Pipe Pete","personality":"Gruff but kind.","personaPrompt":"You are Pete, a plumber."}"""
    private var llm: (String) -> String = { validJson }
    private var counter = 0
    private lateinit var db: JuiceDatabase
    private lateinit var items: ItemRepository
    private lateinit var created: CreatedCharacterStore
    private lateinit var characters: CharacterRepository
    private lateinit var characterStore: CharacterStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, JuiceDatabase::class.java).allowMainThreadQueries().build()
        items = ItemRepository(db.itemDao())
        created = CreatedCharacterStore(context.getSharedPreferences("created-test", Context.MODE_PRIVATE))
        characterStore = CharacterStore(context.getSharedPreferences("characters-created-test", Context.MODE_PRIVATE))
        characters = CharacterRepository(
            characterStore, items, created = created,
            generator = CharacterGenerator({ llm(it) }, { "created-${counter++}" }),
        )
    }

    @After
    fun tearDown() {
        db.close()
        context.getSharedPreferences("created-test", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("characters-created-test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun createSavesCharacterWithMeetCardAndSurvivesNewStore() = runBlocking {
        val result = characters.createCharacter("plumbing")
        val character = assertIs<CreateResult.Created>(result).character
        assertEquals("Pipe Pete", character.name)
        assertEquals(Availability.MetPending, characters.state(character.id).availability)
        assertEquals("Meet Pipe Pete", items.get("meet-${character.id}")?.title)
        val reopened = CreatedCharacterStore(context.getSharedPreferences("created-test", Context.MODE_PRIVATE))
        assertEquals(listOf(character), reopened.list())
        assertEquals(character, CharacterRepository(characterStore, items, created = reopened).registry().byId(character.id))
    }

    @Test
    fun invalidOutputFallsBackButSaves() = runBlocking {
        llm = { "not json" }
        val character = assertIs<CreateResult.Created>(characters.createCharacter("plumbing")).character
        assertEquals("Coach plumbing", character.name)
    }

    @Test
    fun failedCallSavesNothing() = runBlocking {
        llm = { throw IOException("offline") }
        assertIs<CreateResult.Failed>(characters.createCharacter("plumbing"))
        assertTrue(created.list().isEmpty())
        assertTrue(items.active().isEmpty())
    }

    @Test
    fun sixthCharacterIsRefused() = runBlocking {
        repeat(5) { assertIs<CreateResult.Created>(characters.createCharacter("h$it")) }
        assertEquals(CreateResult.LimitReached, characters.createCharacter("one more"))
        assertEquals(5, created.list().size)
    }

    @Test
    fun deleteRemovesCharacterCardsRoutinesAndState() = runBlocking {
        val character = assertIs<CreateResult.Created>(characters.createCharacter("plumbing")).character
        characters.createTasks(character.id, listOf(CoachTask.Add("Check taps", Cadence(java.time.DayOfWeek.entries.toSet()))))
        characters.deleteCharacter(character.id)
        assertTrue(created.list().isEmpty())
        assertTrue(items.byCharacter(character.id).isEmpty())
        assertEquals(Availability.Locked, characters.state(character.id).availability)
        assertNull(characters.registry().byId(character.id))
    }
}
