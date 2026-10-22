package com.juice.intake

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.core.intake.IntakeField
import com.juice.core.intake.ItemType
import com.juice.core.intake.ParsedItem
import com.juice.core.model.Item
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class CaptureIntakeTest {
    private lateinit var db: JuiceDatabase
    private lateinit var repository: ItemRepository
    private val enqueued = mutableListOf<Pair<String, String>>()
    private val deadline = Instant.ofEpochMilli(1_800_000_000_000)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JuiceDatabase::class.java).allowMainThreadQueries().build()
        repository = ItemRepository(db.itemDao())
    }

    @After
    fun tearDown() = db.close()

    private fun intake(onDeviceOnly: Boolean, cloud: Boolean = true) =
        CaptureIntake(repository, { onDeviceOnly }, { id, text -> enqueued += id to text }, cloudAvailable = { cloud })

    @Test
    fun onlineCaptureIsQueuedAndTheLateParseUpdatesTheCard() = runBlocking {
        val note = Item("a", ItemType.Note, "do laundry by 6pm")
        repository.save(note)
        assertEquals(true, intake(onDeviceOnly = false).submit(note))
        assertEquals(listOf("a" to "do laundry by 6pm"), enqueued)
        assertEquals(ItemType.Goal, repository.get("a")?.type)

        repository.applyParse("a", ParsedItem(ItemType.Task, "Laundry", deadline = deadline))
        val seen = repository.observeActive().first().single()
        assertEquals(ItemType.Task, seen.type)
        assertEquals(deadline, seen.deadline)
        assertEquals("do laundry by 6pm", seen.title)
    }

    @Test
    fun rulesResultLandsImmediatelyWhenACloudParseIsEnqueued() = runBlocking {
        val note = Item("a", ItemType.Note, "call mum tomorrow")
        repository.save(note)
        assertEquals(true, intake(onDeviceOnly = false).submit(note))
        assertEquals(1, enqueued.size)
        assertEquals(ItemType.Goal, repository.get("a")?.type)
    }

    @Test
    fun onDeviceOnlyAppliesTheRulesFallbackLocally() = runBlocking {
        val note = Item("a", ItemType.Note, "call mum tomorrow")
        repository.save(note)
        assertEquals(false, intake(onDeviceOnly = true).submit(note))
        assertEquals(emptyList<Pair<String, String>>(), enqueued)
        assertEquals(ItemType.Goal, repository.get("a")?.type)
    }

    @Test
    fun withoutACloudParserARulesProjectLandsWithItsSteps() = runBlocking {
        val note = Item("a", ItemType.Note, "Launch: write copy, record demo and ship")
        repository.save(note)
        intake(onDeviceOnly = false, cloud = false).submit(note)
        assertEquals(emptyList<Pair<String, String>>(), enqueued)
        val active = repository.active()
        assertEquals(ItemType.Project, active.first { it.id == "a" }.type)
        assertEquals(listOf("Write copy", "Record demo", "Ship"), active.single().steps.map { it.title })
        // A second parse of the same card does not add the steps twice.
        repository.land("a", com.juice.core.intake.RulesFallback.classify(note.title))
        assertEquals(3, repository.get("a")!!.steps.size)
    }

    @Test
    fun aLateParseKeepsWhatTheUserEdited() = runBlocking {
        repository.save(Item("a", ItemType.Note, "do laundry by 6pm"))
        repository.save(repository.get("a")!!.copy(type = ItemType.Task))
        repository.markEdited("a", IntakeField.Type)
        repository.applyParse("a", ParsedItem(ItemType.Goal, "Laundry", deadline = deadline))
        val saved = repository.get("a")!!
        assertEquals(ItemType.Task, saved.type)
        assertEquals("do laundry by 6pm", saved.title)
    }

    @Test
    fun aParsedCompletionConditionIsStoredUnlessTheUserSetOne() = runBlocking {
        repository.save(Item("a", ItemType.Note, "buy a dryer before the end of the week"))
        repository.land("a", ParsedItem(ItemType.Goal, "Buy a dryer", completionCondition = "Dryer ordered", deadline = deadline))
        assertEquals("Dryer ordered", repository.get("a")?.completionCondition)
        repository.save(repository.get("a")!!.copy(completionCondition = "Dryer delivered"))
        repository.markEdited("a", IntakeField.CompletionCondition)
        repository.applyParse("a", ParsedItem(ItemType.Goal, "Buy a dryer", completionCondition = "Dryer ordered"))
        assertEquals("Dryer delivered", repository.get("a")?.completionCondition)
    }
}
