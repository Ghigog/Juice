package com.juice.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class CharactersSectionTest {
    private val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private lateinit var db: JuiceDatabase
    private lateinit var items: ItemRepository
    private lateinit var characters: CharacterRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jim = Characters.Jim

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JuiceDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        items = ItemRepository(db.itemDao())
        characters = CharacterRepository(CharacterStore(context.getSharedPreferences("characters-section-test", Context.MODE_PRIVATE)), items)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun show() = compose.setContent { CharactersSection(CharacterSettings(characters, scope)) }

    private val restoreTag = TAG_CHARACTER_RESTORE_PREFIX + jim.id
    private val disableTag = TAG_CHARACTER_DISABLE_PREFIX + jim.id

    @Test
    fun aLockedCharacterShowsItsUnlockLevelAndNoActions() {
        show()
        compose.onNodeWithText("Unlocks at level 5").assertIsDisplayed()
        compose.onNodeWithTag(restoreTag).assertDoesNotExist()
        compose.onNodeWithTag(disableTag).assertDoesNotExist()
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
    fun disablingAfterConfirmationDeletesCardsAndTasksAndShowsTheStateOnly() {
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
        assertEquals(emptyList(), runBlocking { items.byCharacter(jim.id) })
        compose.onNode(hasText("Disabled")).assertIsDisplayed()
        compose.onNodeWithTag(disableTag).assertDoesNotExist()
        compose.onNodeWithTag(restoreTag).assertDoesNotExist()
        runBlocking { characters.unlockAtLevel(jim, 10) }
        assertEquals(emptyList(), runBlocking { items.byCharacter(jim.id) })
    }
}
