package com.juice.ui.overlay

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.RegisterComposeActivity
import com.juice.data.CategoryEntity
import com.juice.data.CategoryRepository
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import com.juice.settings.JuiceSettings
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class CategoryHeaderTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private lateinit var db: JuiceDatabase
    private val clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC)
    private var nextId = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), JuiceDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        runBlocking { db.categoryDao().upsert(CategoryEntity("main", "Main", 0)) }
    }

    @After
    fun tearDown() = db.close()

    /** An overlay with Main and Work, Work current. */
    private fun showWork(): OverlayShellState {
        val categories = CategoryRepository(db.categoryDao()) { "c${nextId++}" }
        runBlocking { categories.create("Work") }
        val state = OverlayShellState(
            ItemRepository(db.itemDao()), CoroutineScope(Dispatchers.Unconfined), emptyList(),
            today = LocalDate.of(2026, 10, 28), clock = clock, categoryRepository = categories,
        ).also { it.observe() }
        state.nextCategory()
        compose.setContent {
            OverlayShell(state, clock = clock, settings = JuiceSettings(reduceMotion = true), hud = Hud(), reservoir = { PlainReservoir(Hud(), it) })
        }
        compose.waitForIdle()
        return state
    }

    @Test
    fun theCapsuleNamesTheCurrentCategoryAndTheSecondOfTwoDotsIsCurrent() {
        showWork()
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).assertTextEquals("Work")
        compose.onNodeWithTag(TAG_CATEGORY_DOT_PREFIX + 1, useUnmergedTree = true).assertIsSelected()
        compose.onNodeWithTag(TAG_CATEGORY_DOT_PREFIX + 0, useUnmergedTree = true).assertIsNotSelected()
    }

    @Test
    fun tappingTheCapsuleRenamesTheCategory() {
        showWork()
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).performClick()
        compose.onNodeWithTag(TAG_CATEGORY_NAME_FIELD).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CATEGORY_NAME_INPUT).assertTextEquals("Work", includeEditableText = true)
        compose.onNodeWithTag(TAG_CATEGORY_NAME_INPUT).performTextReplacement("Office")
        compose.onNodeWithTag(TAG_CATEGORY_NAME_SAVE).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_CATEGORY_NAME_FIELD).assertDoesNotExist()
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).assertTextEquals("Office")
    }

    @Test
    fun onMainATapOpensNothing() {
        val state = showWork()
        compose.runOnIdle { state.previousCategory() }
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).assertTextEquals("Main").performClick()
        compose.onNodeWithTag(TAG_CATEGORY_NAME_FIELD).assertDoesNotExist()
    }

    @Test
    fun cancelClosesTheField() {
        showWork()
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).performClick()
        compose.onNodeWithTag(TAG_CATEGORY_NAME_CANCEL).performClick()
        compose.onNodeWithTag(TAG_CATEGORY_NAME_FIELD).assertDoesNotExist()
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).assertTextEquals("Work")
    }
}
