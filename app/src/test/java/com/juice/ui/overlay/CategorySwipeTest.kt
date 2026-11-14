package com.juice.ui.overlay

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.RegisterComposeActivity
import com.juice.core.model.Item
import com.juice.core.intake.ItemType
import com.juice.data.CategoryEntity
import com.juice.data.CategoryRepository
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import com.juice.feedback.FeedbackEvent
import com.juice.settings.JuiceSettings
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
class CategorySwipeTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private lateinit var db: JuiceDatabase
    private lateinit var categories: CategoryRepository
    private val clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC)
    private val feedback = mutableListOf<FeedbackEvent>()
    private val steps = mutableListOf<Int>()
    private var nextId = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), JuiceDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        runBlocking { db.categoryDao().upsert(CategoryEntity("main", "Main", 0)) }
        categories = CategoryRepository(db.categoryDao()) { "c${nextId++}" }
    }

    @After
    fun tearDown() = db.close()

    private fun show(vararg names: String, reduceMotion: Boolean = true): OverlayShellState {
        names.forEach { name -> runBlocking { categories.create(name) } }
        val state = OverlayShellState(
            ItemRepository(db.itemDao()), CoroutineScope(Dispatchers.Unconfined), emptyList(),
            today = LocalDate.of(2026, 10, 28), clock = clock, categoryRepository = categories,
        ).also { it.observe() }
        compose.setContent {
            OverlayShell(
                state, clock = clock, settings = JuiceSettings(reduceMotion = reduceMotion), hud = Hud(),
                actions = OverlayActions(feedback = { feedback += it }, onScroll = { steps += it }),
                reservoir = { PlainReservoir(Hud(), it) },
            )
        }
        compose.waitForIdle()
        return state
    }

    private fun swipeCard(left: Boolean) {
        compose.onNodeWithTag(TAG_CENTRE_CARD).performTouchInput { if (left) swipeLeft() else swipeRight() }
        compose.waitForIdle()
    }

    @Test
    fun swipingLeftShowsTheNextCategoryAndSnaps() {
        show("Work")
        swipeCard(left = true)
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).assertTextEquals("Work")
        compose.onNodeWithTag(TAG_CATEGORY_EMPTY).assertTextEquals("Nothing in Work yet. Add a note below.")
        assertEquals(listOf(FeedbackEvent.SNAP), feedback)
        assertEquals(listOf(CATEGORY_SLOSH_STEPS), steps)
    }

    @Test
    fun swipingRightGoesBackOneCategory() {
        show("Work")
        swipeCard(left = true)
        swipeCard(left = false)
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).assertTextEquals("Main")
        compose.onNodeWithTag(TAG_OVERLAY_EMPTY).assertIsDisplayed()
    }

    @Test
    fun swipingRightOnMainStaysOnMainWithMotion() {
        show("Work", reduceMotion = false)
        swipeCard(left = false)
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).assertTextEquals("Main")
        assertEquals(emptyList(), feedback)
    }

    @Test
    fun swipingLeftOnTheLastCategoryNamesAndOpensANewOne() {
        val state = show("Work")
        swipeCard(left = true)
        swipeCard(left = true)
        compose.onNodeWithTag(TAG_CATEGORY_NAME_FIELD).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CATEGORY_NAME_INPUT).performTextReplacement("Groceries")
        compose.onNodeWithTag(TAG_CATEGORY_NAME_SAVE).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).assertTextEquals("Groceries")
        compose.onNodeWithTag(TAG_CATEGORY_EMPTY).assertTextEquals("Nothing in Groceries yet. Add a note below.")
        // Saved, so a reopened overlay finds Groceries after Main and Work.
        assertEquals(listOf("Main", "Work", "Groceries"), state.categories.list.map { it.name })
        assertEquals(listOf("Main", "Work", "Groceries"), runBlocking { db.categoryDao().observe().first().map { it.name } })
    }

    @Test
    fun withMotionTheCategoriesSlideAndSettle() {
        show("Work", reduceMotion = false)
        swipeCard(left = true)
        compose.mainClock.advanceTimeBy(5000)
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).assertTextEquals("Work")
        compose.onNodeWithTag(TAG_CATEGORY_EMPTY).assertIsDisplayed()
    }

    @Test
    fun theOutgoingCategoryKeepsItsOwnCardsWhileSliding() {
        runBlocking { ItemRepository(db.itemDao()).save(Item("m1", ItemType.Note, "Only in main")) }
        val state = show("Work", reduceMotion = false)
        compose.mainClock.autoAdvance = false
        state.nextCategory()
        repeat(20) { compose.mainClock.advanceTimeByFrame() }
        // Mid-slide the old Main card is still drawn, not the incoming list.
        assert(compose.onAllNodesWithText("Only in main", substring = true).fetchSemanticsNodes().isNotEmpty())
        compose.mainClock.autoAdvance = true
        compose.mainClock.advanceTimeBy(5000)
        assertEquals(0, compose.onAllNodesWithText("Only in main", substring = true).fetchSemanticsNodes().size)
    }
}
