package com.juice.ui.overlay

import android.content.Context
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.Modifier
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.RegisterComposeActivity
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ReminderLineTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private val clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: JuiceDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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

    private fun show(item: Item) {
        val state = OverlayShellState(ItemRepository(db.itemDao()), scope, listOf(item))
        compose.setContent {
            CentreCard(
                state = state, item = item, hud = Hud(), tapButtons = false, reduceMotion = true, onDeviceOnly = false,
                actions = OverlayActions(), effects = OverlayEffects(), reservoir = {}, modifier = Modifier, clock = clock,
            )
        }
        compose.waitForIdle()
    }

    private val goal = Item(
        id = "g", type = ItemType.Goal, title = "Order dryer", completionCondition = "Dryer ordered",
        deadline = Instant.parse("2026-01-02T17:00:00Z"),
    )

    @Test
    fun goalWithTomorrowNotifyAtShowsTheLine() {
        show(goal.copy(notifyAt = Instant.parse("2026-01-02T08:00:00Z")))
        compose.onNodeWithTag(TAG_REMINDER_LINE).assertTextEquals("Will remind you: Tomorrow 08:00")
    }

    @Test
    fun itemWithoutNotifyAtShowsNoLine() {
        show(goal)
        compose.onAllNodesWithTag(TAG_REMINDER_LINE).assertCountEquals(0)
    }

    @Test
    fun pastReminderShowsNoLine() {
        show(goal.copy(notifyAt = Instant.parse("2026-01-01T09:00:00Z")))
        compose.onAllNodesWithTag(TAG_REMINDER_LINE).assertCountEquals(0)
    }

    @Test
    fun completedItemShowsNoLine() {
        show(goal.copy(notifyAt = Instant.parse("2026-01-02T08:00:00Z"), status = ItemStatus.Completed))
        compose.onAllNodesWithTag(TAG_REMINDER_LINE).assertCountEquals(0)
    }
}
