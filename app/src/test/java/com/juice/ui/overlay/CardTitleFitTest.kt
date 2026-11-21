package com.juice.ui.overlay

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.RegisterComposeActivity
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import com.juice.settings.JuiceSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The centre card's title shrinks to fit long text, down to a minimum, and the tapped card scrolls. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class CardTitleFitTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private lateinit var db: JuiceDatabase
    private lateinit var state: OverlayShellState
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var fitted: TextUnit = 0.sp
    private val medium = (1..40).joinToString(" ") { "word$it" }
    private val huge = (1..600).joinToString(" ") { "word$it" }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JuiceDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun show(title: String, width: Dp = 360.dp, height: Dp = 640.dp) {
        state = OverlayShellState(ItemRepository(db.itemDao()), scope, listOf(Item("a", ItemType.Note, title)))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CentreCard(
                state = state,
                item = state.items.first(),
                hud = Hud(),
                tapButtons = true,
                reduceMotion = true,
                onDeviceOnly = true,
                actions = OverlayActions(),
                effects = OverlayEffects(),
                reservoir = {},
                modifier = Modifier.size(width, height),
                onTitleSize = { fitted = it },
                clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
            )
        }
        idle()
    }

    private fun idle() {
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
    }

    private fun layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(TAG_CARD_TITLE).fetchSemanticsNode().config
            .getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!(results)
        return results.first()
    }

    private fun size() = fitted

    @Test
    fun aShortTitleStaysAtTwentyFiveOverThirty() {
        show("Call Sam")
        assertEquals(25f, size().value, 0f)
        assertFalse(layout().hasVisualOverflow)
    }

    @Test
    fun aLongTitleShrinksUntilEveryWordIsVisible() {
        show(medium)
        assertTrue(size().value < 25f)
        assertTrue(size().value >= 15f)
        assertFalse(layout().hasVisualOverflow)
    }

    @Test
    fun aTitleTooLongForTheMinimumEndsInAnEllipsisAtFifteen() {
        show(huge)
        assertEquals(15f, size().value, 0f)
        assertTrue(layout().hasVisualOverflow)
    }

    @Test
    fun theWideLayoutAlsoShrinksToTheMinimum() {
        show(huge, 560.dp, 260.dp)
        assertEquals(15f, size().value, 0f)
        assertTrue(layout().hasVisualOverflow)
    }

    @Test
    fun tappingALongCardOpensTheFieldAndDraggingScrollsItWithoutDrawing() {
        show(huge)
        compose.onNodeWithTag(TAG_CARD_TITLE).performTouchInput { click() }
        idle()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_TITLE_FIELD).assertExists()
        compose.onNodeWithTag(TAG_TITLE_FIELD).performTouchInput { swipeUp() }
        idle()
        assertEquals(huge, state.items.first().title)
        compose.onNodeWithTag(TAG_TITLE_FIELD).assertExists()
    }
}
