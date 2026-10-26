package com.juice.settings

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.RegisterComposeActivity
import com.juice.core.character.CoachTask
import com.juice.core.character.Characters
import com.juice.core.routine.Cadence
import com.juice.data.CharacterRepository
import com.juice.data.CharacterStore
import com.juice.data.CreatedCharacterStore
import com.juice.core.character.Character
import com.juice.core.character.Face
import com.juice.core.character.Shape
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import com.juice.core.memory.Memory
import com.juice.memory.MemoryStore
import java.time.DayOfWeek
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CharactersSectionTest {
    private val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private lateinit var db: JuiceDatabase
    private lateinit var items: ItemRepository
    private lateinit var characters: CharacterRepository
    private lateinit var memoryStore: MemoryStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jim = Characters.Jim
    private val createdStore = CreatedCharacterStore(
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("created-section-test", Context.MODE_PRIVATE),
    )
    private val pete = Character(
        id = "pete", name = "Pottery Pete", personaPrompt = "p", unlockLevel = 0, checkInIntervalDays = 14,
        openingLine = "Hi", mutterPitch = 1f, face = Face(eye = Shape.CIRCLE, mouth = Shape.SQUARE), hobby = "pottery",
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JuiceDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        items = ItemRepository(db.itemDao())
        memoryStore = MemoryStore(File.createTempFile("memory", ".json").also { it.delete() })
        characters = CharacterRepository(
            CharacterStore(context.getSharedPreferences("characters-section-test", Context.MODE_PRIVATE)),
            items,
            memory = memoryStore,
            created = createdStore,
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun show() = compose.setContent { CharactersSection(CharacterSettings(characters, scope, level = { 5 })) }

    private val restoreTag = TAG_CHARACTER_RESTORE_PREFIX + jim.id
    private val enableTag = TAG_CHARACTER_ENABLE_PREFIX + jim.id
    private val resetTag = TAG_CHARACTER_RESET_PREFIX + jim.id
    private val disableTag = TAG_CHARACTER_DISABLE_PREFIX + jim.id

    @Test
    fun aLockedCharacterShowsItsUnlockLevelAndNoActions() {
        show()
        compose.onNodeWithText("Unlocks at level 5").assertIsDisplayed()
        compose.onNodeWithTag(restoreTag).assertDoesNotExist()
        compose.onNodeWithTag(disableTag).assertDoesNotExist()
        compose.onNodeWithTag(enableTag).assertDoesNotExist()
        compose.onNodeWithTag(resetTag).assertDoesNotExist()
    }

    @Test
    fun restoringADismissedCharacterBringsBackItsMeetCard() {
        runBlocking {
            characters.unlockAtLevel(jim, 5)
            characters.dismiss(jim)
        }
        assertNull(runBlocking { items.get("meet-jim") })
        show()
        compose.onNodeWithTag(restoreTag).performClick()
        compose.waitForIdle()
        assertNotNull(runBlocking { items.get("meet-jim") })
        compose.onNodeWithTag(restoreTag).assertDoesNotExist()
        compose.onNodeWithTag(disableTag).assertIsDisplayed()
    }

    @Test
    fun aLostCharacterCanBeRestoredButOneWithACardOrConversationCannot() {
        runBlocking {
            characters.unlockAtLevel(jim, 5)
            items.delete("meet-jim")
        }
        show()
        compose.waitForIdle()
        compose.onNodeWithTag(restoreTag).performClick()
        compose.waitForIdle()
        assertNotNull(runBlocking { items.get("meet-jim") })
        compose.onNodeWithTag(restoreTag).assertDoesNotExist()
    }

    @Test
    fun noRestoreForAnUnlockedCharacterWithAConversationOrACard() {
        runBlocking {
            characters.unlockAtLevel(jim, 5)
        }
        show()
        compose.waitForIdle()
        compose.onNodeWithTag(restoreTag).assertDoesNotExist()
    }

    @Test
    fun noRestoreForAConversedCharacterWithoutCards() {
        runBlocking {
            characters.unlockAtLevel(jim, 5)
            items.delete("meet-jim")
            characters.recordConversation(jim.id)
        }
        show()
        compose.waitForIdle()
        compose.onNodeWithTag(restoreTag).assertDoesNotExist()
    }

    @Test
    fun disablingAfterConfirmationHidesCharacterButKeepsItsItems() {
        runBlocking {
            characters.unlockAtLevel(jim, 5)
            characters.createTasks(jim.id, listOf(CoachTask.Add("Walk", Cadence(DayOfWeek.entries.toSet()))))
        }
        show()
        compose.onNodeWithTag(disableTag).performClick()
        // Nothing is deleted until the player confirms.
        assertNotNull(runBlocking { items.get("meet-jim") })
        compose.onNodeWithTag(TAG_CHARACTER_CONFIRM_DISABLE_PREFIX + jim.id).performClick()
        compose.waitForIdle()
        assertEquals(2, runBlocking { items.byCharacter(jim.id) }.size)
        compose.onNode(hasText("Disabled")).assertIsDisplayed()
        compose.onNodeWithTag(disableTag).assertDoesNotExist()
        compose.onNodeWithTag(restoreTag).assertDoesNotExist()
        compose.onNodeWithTag(enableTag).assertIsDisplayed()
        runBlocking { characters.unlockAtLevel(jim, 10) }
        assertEquals(2, runBlocking { items.byCharacter(jim.id) }.size)
    }

    @Test
    fun enablingADisabledCharacterShowsItAvailableAgain() {
        runBlocking {
            characters.unlockAtLevel(jim, 5)
            characters.disable(jim)
        }
        show()
        compose.onNodeWithTag(enableTag).performClick()
        compose.waitForIdle()
        assertEquals(false, characters.state(jim.id).disabled)
        compose.onNodeWithTag(enableTag).assertDoesNotExist()
        compose.onNodeWithTag(disableTag).assertIsDisplayed()
    }

    @Test
    fun resetAfterConfirmationDeletesItems() {
        runBlocking {
            characters.unlockAtLevel(jim, 5)
            characters.disable(jim)
        }
        show()
        compose.onNodeWithTag(resetTag).performClick()
        assertNotNull(runBlocking { items.get("meet-jim") })
        compose.onNodeWithTag(TAG_CHARACTER_CONFIRM_RESET_PREFIX + jim.id).performClick()
        compose.waitForIdle()
        assertEquals(emptyList(), runBlocking { items.byCharacter(jim.id) })
        assertEquals(false, characters.state(jim.id).disabled)
    }

    @Test
    fun resetAfterConfirmationMakesTheRowActive() {
        runBlocking {
            characters.unlockAtLevel(jim, 5)
            characters.disable(jim)
        }
        show()
        compose.onNodeWithTag(resetTag).performClick()
        compose.onNodeWithTag(TAG_CHARACTER_CONFIRM_RESET_PREFIX + jim.id).performClick()
        compose.waitForIdle()
        assertNotNull(runBlocking { items.get("meet-jim") })
        compose.onNodeWithTag(disableTag).assertIsDisplayed()
    }

    @Test
    fun cancellingTheResetConfirmationChangesNothing() {
        runBlocking { characters.unlockAtLevel(jim, 5) }
        show()
        compose.onNodeWithTag(resetTag).performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertNotNull(runBlocking { items.get("meet-jim") })
        compose.onNodeWithTag(resetTag).assertIsDisplayed()
    }

    @Test
    fun sharedAndCharacterNotesAreShown() {
        memoryStore.save(Memory(shared = listOf("Likes mornings"), characters = mapOf(jim.id to listOf("Hates running"))))
        show()
        compose.waitForIdle()
        compose.onNodeWithText("• Likes mornings").assertIsDisplayed()
        compose.onNodeWithText("• Hates running").assertIsDisplayed()
        // Once as the character's row, once as the title of their notes.
        compose.onAllNodesWithText(jim.name).assertCountEquals(2)
        compose.onNodeWithTag(TAG_MEMORY_EMPTY).assertDoesNotExist()
    }

    @Test
    fun clearMemoryRemovesTheNotesFromScreenAndStore() {
        memoryStore.save(Memory(shared = listOf("Likes mornings"), characters = mapOf(jim.id to listOf("Hates running"))))
        show()
        compose.onNodeWithTag(TAG_MEMORY_CLEAR).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("• Likes mornings").assertDoesNotExist()
        compose.onNodeWithText("• Hates running").assertDoesNotExist()
        compose.onNodeWithTag(TAG_MEMORY_EMPTY).assertIsDisplayed()
        assertEquals(Memory(), characters.memory())
    }

    @Test
    fun withoutNotesItSaysNothingIsRemembered() {
        show()
        compose.onNodeWithTag(TAG_MEMORY_EMPTY).assertIsDisplayed()
        compose.onNodeWithTag(TAG_MEMORY_CLEAR).assertDoesNotExist()
    }

    private fun addCreated() = runBlocking {
        createdStore.add(pete)
        items.save(com.juice.core.model.Item("meet-pete", com.juice.core.intake.ItemType.Task, "Meet Pottery Pete", characterId = pete.id))
    }

    @Test
    fun aCreatedCharacterIsListedWithMonogramAndStateButJimHasNoDelete() {
        createdStore.remove(pete.id)
        addCreated()
        show()
        compose.waitForIdle()
        compose.onNodeWithText("Pottery Pete").assertIsDisplayed()
        compose.onNodeWithText("PP").assertIsDisplayed()
        compose.onNodeWithTag(TAG_CHARACTER_DELETE_PREFIX + pete.id).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CHARACTER_DELETE_PREFIX + jim.id).assertDoesNotExist()
    }

    @Test
    fun deletingACreatedCharacterAfterConfirmationRemovesIt() {
        createdStore.remove(pete.id)
        addCreated()
        show()
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_CHARACTER_DELETE_PREFIX + pete.id).performClick()
        assertNotNull(runBlocking { items.get("meet-pete") })
        compose.onNodeWithTag(TAG_CHARACTER_CONFIRM_DELETE_PREFIX + pete.id).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Pottery Pete").assertDoesNotExist()
        assertNull(runBlocking { items.get("meet-pete") })
        assertEquals(emptyList(), createdStore.list())
    }

    @Test
    fun cancellingTheDeleteConfirmationKeepsTheCharacter() {
        createdStore.remove(pete.id)
        addCreated()
        show()
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_CHARACTER_DELETE_PREFIX + pete.id).performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Pottery Pete").assertIsDisplayed()
        assertNotNull(runBlocking { items.get("meet-pete") })
    }
}
