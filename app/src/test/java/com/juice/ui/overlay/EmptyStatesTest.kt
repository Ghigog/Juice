package com.juice.ui.overlay

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.RegisterComposeActivity
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import com.juice.settings.JuiceSettings
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

/** The empty and error copy documented in docs/design-system.md shows under its test tag. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h740dp-xxhdpi")
class EmptyStatesTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

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

    @Test
    fun anEmptyRoutinesViewShowsItsWarmCopy() {
        val state = OverlayShellState(ItemRepository(db.itemDao()), scope, emptyList())
        compose.setContent {
            RoutinesView(state, Hud(), JuiceSettings(), false, OverlayActions(), OverlayEffects(), {}, {}, Modifier)
        }
        compose.onNodeWithTag(TAG_ROUTINES_EMPTY).assertIsDisplayed()
            .assertTextEquals("Nothing on repeat yet. Squeeze a habit in and it will come back around.")
    }

    @Test
    fun aFailedCreatorShowsItsWarmError() {
        val creator = CreatorState().apply { error = "no signal" }
        compose.setContent { CreatorFlow(creator, Hud(), {}, {}) }
        compose.onNodeWithTag(TAG_CREATOR_ERROR)
            .assertTextEquals("Your coach slipped away: no signal Give it another squeeze.")
    }
}
