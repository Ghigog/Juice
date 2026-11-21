package com.juice.ui.overlay

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import kotlinx.coroutines.flow.first
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.RegisterComposeActivity
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
class CategoryReorderTest {
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

    private fun capsule(i: Int) = compose.onNodeWithTag(TAG_SHELF_CAPSULE_PREFIX + i)

    private fun centreX(i: Int): Float =
        capsule(i).fetchSemanticsNode().boundsInRoot.center.x - compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).fetchSemanticsNode().boundsInRoot.left

    /** Long taps the current capsule, slides it over capsule [target] and lets go. */
    private fun dragCurrentOnto(target: Int) {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).performTouchInput { down(Offset(10f, centerY)) }
        compose.mainClock.advanceTimeBy(1_000)
        val x = centreX(target)
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).performTouchInput { moveTo(Offset(x, centerY)) }
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(SHELF_FOLD_MILLIS + 1_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    private fun names(state: OverlayShellState) = state.categories.list.map { it.name }

    @Test
    fun dropHomeBeforeWorkSetsTheOrderSavesItAndSwipingLeftFromMainShowsHome() {
        val state = show("Work", "Home")
        swipeCard(left = true)
        swipeCard(left = true)
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).assertTextEquals("Home")
        feedback.clear()
        dragCurrentOnto(1)
        assertEquals(listOf("Main", "Home", "Work"), names(state))
        assertEquals(listOf(FeedbackEvent.SNAP), feedback)
        // Saved: a reopened overlay reads the same order.
        assertEquals(listOf("Main", "Home", "Work"), runBlocking { categories.observe().first().map { it.name } })
        // Home stays on screen and its dot moved up one place.
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).assertTextEquals("Home")
        compose.onNodeWithTag(TAG_CATEGORY_DOT_PREFIX + 1).assertIsSelected()
        swipeCard(left = false)
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).assertTextEquals("Main")
        swipeCard(left = true)
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).assertTextEquals("Home")
    }

    @Test
    fun movingTheCurrentCategoryLaterKeepsItOnScreenAndMovesOnlyItsDot() {
        val state = show("Work", "Home")
        swipeCard(left = true)
        compose.onNodeWithTag(TAG_CATEGORY_DOT_PREFIX + 1).assertIsSelected()
        dragCurrentOnto(2)
        assertEquals(listOf("Main", "Home", "Work"), names(state))
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).assertTextEquals("Work")
        compose.onNodeWithTag(TAG_CATEGORY_DOT_PREFIX + 2).assertIsSelected()
        compose.onNodeWithTag(TAG_CATEGORY_DOT_PREFIX + 1).assertIsNotSelected()
    }
}
