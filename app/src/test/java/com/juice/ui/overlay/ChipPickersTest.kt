package com.juice.ui.overlay

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.RegisterComposeActivity
import com.juice.core.intake.ChipKind
import com.juice.core.intake.ItemType
import com.juice.core.intake.RulesFallback
import com.juice.core.model.Item
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ChipPickersTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private lateinit var db: JuiceDatabase
    private lateinit var state: OverlayShellState
    private val today = LocalDate.of(2026, 10, 28) // a Wednesday

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), JuiceDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun open(title: String, type: ItemType, kind: ChipKind) {
        val item = Item("i", type, title)
        state = OverlayShellState(ItemRepository(db.itemDao()), CoroutineScope(Dispatchers.Unconfined), listOf(item), today = today)
        state.openChipPicker(item, RulesFallback.spansOf(title, type).first { it.kind == kind })
        compose.setContent { ChipPickers(state, today, Locale.ENGLISH) }
    }

    @Test fun deadlinePickerListsTheNextSevenDays() {
        open("take out the trash on Tuesday", ItemType.Goal, ChipKind.Deadline)
        compose.onAllNodes(tagStartsWith("chipPickerDay")).assertCountEquals(7)
        listOf("Wednesday", "Thursday", "Friday", "Saturday", "Sunday", "Monday", "Tuesday").forEach {
            compose.onNodeWithText(it).assertExists()
        }
    }

    @Test fun choosingADayRewritesTheTitleAndCloses() {
        open("take out the trash on Tuesday", ItemType.Goal, ChipKind.Deadline)
        compose.onNodeWithTag("chipPickerDay1").performClick()
        assertEquals("take out the trash on Thursday", state.items.single().title)
        assertNull(state.chipPicker)
        compose.onAllNodes(tagStartsWith("chipPickerDay")).assertCountEquals(0)
    }

    @Test fun intervalPickerOffersOneToFourWeeks() {
        open("gym every 2 weeks on Mon", ItemType.Routine, ChipKind.Interval)
        listOf("1 week", "2 weeks", "3 weeks", "4 weeks").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onAllNodes(tagStartsWith("chipPickerWeeks")).assertCountEquals(4)
        compose.onNodeWithTag("chipPickerWeeks3").performClick()
        assertEquals("gym every 3 weeks on Mon", state.items.single().title)
        assertNull(state.chipPicker)
    }

    @Test fun weekdayPickerOffersSevenWeekdaysAndCloses() {
        open("gym every 2 weeks on Mon", ItemType.Routine, ChipKind.Weekday)
        compose.onAllNodes(tagStartsWith("chipPickerWeekday")).assertCountEquals(7)
        compose.onNodeWithTag("chipPickerWeekdayFRIDAY").performClick()
        assertEquals("gym every 2 weeks on Fri", state.items.single().title)
        assertNull(state.chipPicker)
    }
}

private fun tagStartsWith(prefix: String) =
    SemanticsMatcher("tag starts with $prefix") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true }
