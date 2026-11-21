package com.juice.ui.overlay

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.up
import androidx.compose.ui.test.center
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.performSemanticsAction
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

    // The shelf, on a bare header so each callback can be seen on its own.
    private val moves = mutableListOf<Pair<Int, Int>>()
    private val earlier = mutableListOf<Int>()
    private val later = mutableListOf<Int>()
    private var ticks = 0
    private var renames = 0

    private fun showShelf(names: List<String>, current: Int, reduceMotion: Boolean = false) {
        compose.setContent {
            Header(
                hud = Hud(), entrance = { 1f }, pop = androidx.compose.ui.Modifier,
                onRecap = {}, onRoutines = {}, onSettings = {}, onRoutinesBounds = {},
                categoryOrder = names.mapIndexed { i, n -> ShelfCategory(n, movable = i > 0) },
                categoryIndex = current,
                onCategory = { renames++ },
                onMoveCategory = { from, to -> moves += from to to },
                onMoveEarlier = { earlier += it },
                onMoveLater = { later += it },
                onShelfTick = { ticks++ },
                reduceMotion = reduceMotion,
            )
        }
        compose.mainClock.autoAdvance = false
    }

    private fun capsule(i: Int) = compose.onNodeWithTag(TAG_SHELF_CAPSULE_PREFIX + i)

    /** The x of capsule [i]'s centre, in the strip container's own coordinates. */
    private fun centreX(i: Int): Float =
        capsule(i).fetchSemanticsNode().boundsInRoot.center.x - compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).fetchSemanticsNode().boundsInRoot.left

    private fun settleFor(millis: Long) = compose.mainClock.advanceTimeBy(millis)

    /** Long taps the strip (the current capsule) and keeps the finger down. */
    private fun longPressAndHold() {
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).performTouchInput { down(Offset(10f, centerY)) }
        settleFor(1_000)
    }

    private fun dragTo(x: Float) {
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).performTouchInput { moveTo(Offset(x, centerY)) }
        settleFor(50)
    }

    private fun release() {
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).performTouchInput { up() }
        settleFor(1_000)
    }

    @Test
    fun aBriefTapRenamesAndOpensNoShelf() {
        showShelf(listOf("Main", "Work", "Home"), 2)
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).performClick()
        settleFor(500)
        kotlin.test.assertEquals(1, renames)
        capsule(0).assertDoesNotExist()
    }

    @Test
    fun aLongTapOpensTheShelfWithoutRenaming() {
        showShelf(listOf("Main", "Work", "Home"), 2)
        longPressAndHold()
        capsule(0).assertIsDisplayed()
        capsule(1).assertIsDisplayed()
        kotlin.test.assertEquals(0, renames)
        release()
    }

    @Test
    fun draggingHomePastWorkTicksOnceAndReportsTheMove() {
        showShelf(listOf("Main", "Work", "Home"), 2)
        longPressAndHold()
        dragTo(centreX(1))
        kotlin.test.assertEquals(1, ticks)
        kotlin.test.assertTrue(moves.isEmpty())
        release()
        kotlin.test.assertEquals(listOf(2 to 1), moves)
    }

    @Test
    fun draggingToTheFarLeftStopsRightAfterMainAndTicksPerNeighbour() {
        showShelf(listOf("Main", "Work", "Home", "Play"), 3)
        longPressAndHold()
        dragTo(centreX(2))
        dragTo(centreX(1))
        dragTo(0f)
        kotlin.test.assertEquals(2, ticks)
        release()
        kotlin.test.assertEquals(listOf(3 to 1), moves)
    }

    @Test
    fun mainIsNeverLifted() {
        showShelf(listOf("Main", "Work", "Home"), 0)
        longPressAndHold()
        dragTo(centreX(2))
        release()
        kotlin.test.assertTrue(moves.isEmpty())
        kotlin.test.assertEquals(0, ticks)
    }

    @Test
    fun withOnlyMainAndWorkDraggingNudgesReportsNothingAndFolds() {
        showShelf(listOf("Main", "Work"), 1)
        longPressAndHold()
        dragTo(0f)
        release()
        kotlin.test.assertTrue(moves.isEmpty())
        settleFor(SHELF_FOLD_MILLIS + 1_000)
        capsule(0).assertDoesNotExist()
    }

    @Test
    fun theShelfFoldsAfterThePauseFollowingADrop() {
        showShelf(listOf("Main", "Work", "Home"), 2)
        longPressAndHold()
        dragTo(centreX(1))
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).performTouchInput { up() }
        settleFor(500)
        capsule(0).assertIsDisplayed()
        settleFor(SHELF_FOLD_MILLIS + 1_000)
        capsule(0).assertDoesNotExist()
    }

    @Test
    fun withReducedMotionTheMoveIsStillReported() {
        showShelf(listOf("Main", "Work", "Home"), 2, reduceMotion = true)
        longPressAndHold()
        dragTo(centreX(1))
        release()
        kotlin.test.assertEquals(listOf(2 to 1), moves)
    }

    @Test
    fun screenReaderActionsSkipMainAndTheEnds() {
        showShelf(listOf("Main", "Work", "Home"), 2)
        compose.onNodeWithTag(TAG_CATEGORY_CAPSULE).performSemanticsAction(SemanticsActions.OnLongClick)
        compose.waitForIdle()
        settleFor(1_000)
        fun labels(i: Int) = capsule(i).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
        kotlin.test.assertEquals(emptyList<String>(), labels(0))
        kotlin.test.assertEquals(listOf("Move later"), labels(1))
        kotlin.test.assertEquals(listOf("Move earlier"), labels(2))
        capsule(2).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)!!.first().action()
        kotlin.test.assertEquals(listOf(2), earlier)
    }
}
