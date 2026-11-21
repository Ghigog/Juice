package com.juice.ui.overlay

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.RegisterComposeActivity
import com.juice.core.card.CardOrder
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import com.juice.feedback.FeedbackEvent
import com.juice.settings.JuiceSettings
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CircleSortTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private val clock = Clock.fixed(Instant.parse("2026-01-01T18:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: JuiceDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val feedback = mutableListOf<FeedbackEvent>()
    private val effects = OverlayEffects()
    private var settings = JuiceSettings(cardOrder = CardOrder.Newest)

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

    private fun show(order: CardOrder) {
        val item = Item(id = "n", type = ItemType.Note, title = "Note", createdAt = Instant.parse("2026-01-01T10:00:00Z"))
        val state = OverlayShellState(ItemRepository(db.itemDao()), scope, listOf(item), cardOrder = order)
        settings = JuiceSettings(cardOrder = order)
        compose.setContent {
            CentreCard(
                state = state, item = item, hud = Hud(), tapButtons = false, reduceMotion = true, onDeviceOnly = false,
                actions = OverlayActions(feedback = { feedback += it }, onSettingsChange = { settings = it(settings) }),
                effects = effects, reservoir = {}, modifier = Modifier, clock = clock,
            )
        }
        compose.waitForIdle()
    }

    private fun drawCircle(clockwise: Boolean) {
        compose.onNodeWithTag(TAG_CENTRE_CARD).performTouchInput {
            val points = (0..24).map {
                val a = (if (clockwise) 1 else -1) * it * 2 * PI / 24
                center + Offset((80 * cos(a)).toFloat(), (80 * sin(a)).toFloat())
            }
            down(points.first())
            points.drop(1).forEach { moveTo(it) }
            up()
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2000)
    }

    @Test
    fun anticlockwiseCircleSortsNewestFirstWithSnapAndCaption() {
        show(CardOrder.Oldest)
        drawCircle(clockwise = false)
        assertEquals(CardOrder.Newest, settings.cardOrder)
        assertEquals(listOf(FeedbackEvent.SNAP), feedback)
        assertEquals(listOf("Newest first"), effects.floats.map { it.text })
    }

    @Test
    fun clockwiseCircleSortsOldestFirst() {
        show(CardOrder.Newest)
        drawCircle(clockwise = true)
        assertEquals(CardOrder.Oldest, settings.cardOrder)
        assertEquals(listOf("Oldest first"), effects.floats.map { it.text })
    }

    @Test
    fun drawingTheOrderAlreadySetOnlyPlaysTheCaption() {
        show(CardOrder.Newest)
        val before = settings
        drawCircle(clockwise = false)
        assertEquals(before, settings)
        assertEquals(listOf("Newest first"), effects.floats.map { it.text })
    }
}
