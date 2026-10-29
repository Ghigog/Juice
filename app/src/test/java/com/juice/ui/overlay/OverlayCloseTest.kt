package com.juice.ui.overlay

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.RegisterComposeActivity
import com.juice.core.model.Item
import com.juice.data.ItemRepository
import com.juice.core.intake.ItemType
import com.juice.data.JuiceDatabase
import com.juice.settings.JuiceSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class OverlayCloseTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private lateinit var db: JuiceDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val closing = mutableStateOf(false)
    private var closed = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), JuiceDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun show(
        reduceMotion: Boolean,
        onRevealProgress: (Float) -> Unit = {},
        items: List<Item> = emptyList(),
        onPopsPlanned: (List<Offset>, List<Offset>) -> Unit = { _, _ -> },
    ) {
        val state = OverlayShellState(ItemRepository(db.itemDao()), scope, items)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OverlayShell(
                state,
                settings = JuiceSettings(reduceMotion = reduceMotion),
                closing = closing.value,
                onClosed = { closed++ },
                onRevealProgress = onRevealProgress,
                onPopsPlanned = onPopsPlanned,
            )
        }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
    }

    @Test
    fun closedFiresOnlyAfterThePopAndTheDrain() {
        show(reduceMotion = false)
        closing.value = true
        compose.mainClock.advanceTimeBy(100)
        assertEquals(0, closed)
        compose.mainClock.advanceTimeBy(POP_MILLIS + CLOSE_DRAIN_MILLIS + 200L)
        compose.waitForIdle()
        assertEquals(1, closed)
    }

    @Test
    fun closedWaitsForTheCaptureFieldPopWhenThereAreNoCards() {
        show(reduceMotion = false)
        closing.value = true
        compose.mainClock.advanceTimeBy(elementPopStartMillis(0, 1) + CARD_POP_MILLIS / 2L)
        assertEquals(0, closed)
        compose.mainClock.advanceTimeBy(POP_MILLIS + CLOSE_DRAIN_MILLIS + 200L)
        compose.waitForIdle()
        assertEquals(1, closed)
    }

    @Test
    fun theDrainReportsProgressFallingFromOneToZero() {
        val reported = mutableListOf<Float>()
        show(reduceMotion = false, onRevealProgress = { reported += it })
        compose.mainClock.advanceTimeBy(REVEAL_MILLIS.toLong() + 100)
        val opened = reported.size
        closing.value = true
        compose.mainClock.advanceTimeBy(POP_MILLIS + CLOSE_DRAIN_MILLIS + 200L)
        compose.waitForIdle()
        val drain = reported.drop(opened)
        assertEquals(1f, reported[opened - 1], 0.001f)
        assertEquals(0f, drain.last(), 0.001f)
        assertTrue(drain.size > 2)
        assertEquals(drain.sortedDescending(), drain)
    }

    @Test
    fun closedFiresAtOnceWithReducedMotion() {
        show(reduceMotion = true)
        closing.value = true
        compose.mainClock.advanceTimeBy(50)
        compose.waitForIdle()
        assertEquals(1, closed)
    }

    @Test
    fun everyPopBurstsOverACardOrTheCaptureFieldNotInEmptySpace() {
        var froth = emptyList<Offset>()
        var cards = emptyList<Offset>()
        val items = (1..3).map { Item("t$it", ItemType.Task, "Task $it") }
        show(reduceMotion = false, items = items, onPopsPlanned = { f, c -> froth = f; cards = c })
        compose.mainClock.advanceTimeBy(REVEAL_MILLIS.toLong() + 100)
        closing.value = true
        compose.mainClock.advanceTimeBy(100)
        fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val carousel = bounds(TAG_CAROUSEL)
        val onCards = listOf(carousel, bounds(TAG_JUICE_BOX), bounds(TAG_CAPTURE_FIELD))
        assertEquals(items.size, cards.size)
        assertTrue(froth.isNotEmpty())
        (froth + cards).forEach { origin ->
            assertTrue("$origin is off every card in $onCards", onCards.any { it.contains(origin) })
            assertTrue("$origin starts in the empty top", origin.y > carousel.top)
        }
        assertTrue(cards.all { carousel.contains(it) })
    }
}
