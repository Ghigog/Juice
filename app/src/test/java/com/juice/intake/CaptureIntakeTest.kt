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
    private val enqueued = mutableListOf<Triple<String, String, Boolean>>()
    private val deadline = Instant.ofEpochMilli(1_800_000_000_000)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JuiceDatabase::class.java).allowMainThreadQueries().build()
        repository = ItemRepository(db.itemDao())
    }

    @After
    fun tearDown() = db.close()

    private fun intake(onDeviceOnly: Boolean) =
        CaptureIntake(repository, { onDeviceOnly }, { id, text, local -> enqueued += Triple(id, text, local) })

    @Test
    fun onlineCaptureIsQueuedAndTheLateParseUpdatesTheCard() = runBlocking {
        val note = Item("a", ItemType.Note, "do laundry by 6pm")
        repository.save(note)
        intake(onDeviceOnly = false).submit(note)
        assertEquals(listOf(Triple("a", "do laundry by 6pm", false)), enqueued)
        assertEquals(ItemType.Note, repository.get("a")?.type)

        repository.applyParse("a", ParsedItem(ItemType.Goal, "Laundry", deadline = deadline))
        val seen = repository.observeActive().first().single()
        assertEquals(ItemType.Goal, seen.type)
        assertEquals(deadline, seen.deadline)
        assertEquals("do laundry by 6pm", seen.title)
    }

    @Test
    fun onDeviceOnlyAppliesTheRulesFallbackLocally() = runBlocking {
        val note = Item("a", ItemType.Note, "call mum tomorrow")
        repository.save(note)
        intake(onDeviceOnly = true).submit(note)
        assertEquals(listOf(Triple("a", "call mum tomorrow", true)), enqueued)
        assertEquals(ItemType.Goal, repository.get("a")?.type)
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
}
