package com.juice.ui.overlay

import android.content.Context
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.RegisterComposeActivity
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import com.juice.settings.JuiceSettings
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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

private val CLOCK = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC)

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class OverlayTouchTargetsTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private lateinit var db: JuiceDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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

    @Test fun undoAndTypePillAreAtLeast44TallWhileTheVisiblePillsKeepTheirHeight() {
        val state = OverlayShellState(
            ItemRepository(db.itemDao()), scope, listOf(Item("a", ItemType.Task, "Call Sam"), Item("b", ItemType.Note, "Buy milk")),
            today = LocalDate.of(2026, 10, 28), clock = { CLOCK.instant() },
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OverlayShell(state, clock = CLOCK, settings = JuiceSettings(reduceMotion = true), hud = Hud(), reservoir = { PlainReservoir(Hud(), it) })
        }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()

        val typePill = compose.onNodeWithTag(TAG_TYPE_PILL).getUnclippedBoundsInRoot()
        assertTrue(typePill.height >= 44.dp)
        assertEquals(28.dp, compose.onNodeWithTag(TAG_TYPE_PILL_VISIBLE, useUnmergedTree = true).getUnclippedBoundsInRoot().height)

        compose.runOnIdle { state.complete(state.items.first()) }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()

        assertTrue(compose.onNodeWithTag(TAG_UNDO).getUnclippedBoundsInRoot().height >= 44.dp)
        assertEquals(32.dp, compose.onNodeWithTag(TAG_UNDO_PILL, useUnmergedTree = true).getUnclippedBoundsInRoot().height)
    }
}
