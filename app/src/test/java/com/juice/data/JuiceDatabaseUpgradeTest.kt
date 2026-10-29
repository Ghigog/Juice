package com.juice.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
class JuiceDatabaseUpgradeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "upgrade-test.db"
    private var db: JuiceDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(name)
    }

    private val columnsByVersion = mapOf(
        1 to "id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, title TEXT NOT NULL, deadlineMillis INTEGER, status TEXT NOT NULL, parentId TEXT",
        2 to "id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, title TEXT NOT NULL, deadlineMillis INTEGER, status TEXT NOT NULL, parentId TEXT, " +
            "editedFields TEXT NOT NULL DEFAULT ''",
        3 to "id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, title TEXT NOT NULL, deadlineMillis INTEGER, status TEXT NOT NULL, parentId TEXT, " +
            "editedFields TEXT NOT NULL DEFAULT '', completionCondition TEXT",
        4 to "id TEXT NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, deadlineMillis INTEGER, status TEXT NOT NULL, " +
            "editedFields TEXT NOT NULL DEFAULT '', completionCondition TEXT, steps TEXT NOT NULL DEFAULT '[]', PRIMARY KEY(id)",
        5 to "id TEXT NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, deadlineMillis INTEGER, status TEXT NOT NULL, " +
            "editedFields TEXT NOT NULL DEFAULT '', completionCondition TEXT, steps TEXT NOT NULL DEFAULT '[]', " +
            "position INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(id)",
        6 to "id TEXT NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, deadlineMillis INTEGER, status TEXT NOT NULL, " +
            "editedFields TEXT NOT NULL DEFAULT '', completionCondition TEXT, steps TEXT NOT NULL DEFAULT '[]', " +
            "position INTEGER NOT NULL DEFAULT 0, cadence TEXT, nextDueOn INTEGER, PRIMARY KEY(id)",
        7 to "id TEXT NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, deadlineMillis INTEGER, status TEXT NOT NULL, " +
            "editedFields TEXT NOT NULL DEFAULT '', completionCondition TEXT, steps TEXT NOT NULL DEFAULT '[]', " +
            "position INTEGER NOT NULL DEFAULT 0, cadence TEXT, nextDueOn INTEGER, createdAtMillis INTEGER, " +
            "completedAtMillis INTEGER, characterId TEXT, PRIMARY KEY(id)",
        8 to "id TEXT NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, deadlineMillis INTEGER, status TEXT NOT NULL, " +
            "editedFields TEXT NOT NULL DEFAULT '', completionCondition TEXT, steps TEXT NOT NULL DEFAULT '[]', " +
            "position INTEGER NOT NULL DEFAULT 0, cadence TEXT, nextDueOn INTEGER, createdAtMillis INTEGER, " +
            "completedAtMillis INTEGER, characterId TEXT, starredAtMillis INTEGER, PRIMARY KEY(id)",
    )

    private val seeded = listOf("a" to "Active", "c" to "Completed", "z" to "Archived", "r" to "Active")

    private fun seed(version: Int) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE items (${columnsByVersion.getValue(version)})")
                        seeded.forEach { (id, status) ->
                            val type = if (id == "r") "Routine" else "Task"
                            db.execSQL("INSERT INTO items (id, type, title, status) VALUES (?, ?, ?, ?)", arrayOf(id, type, "Title $id", status))
                        }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        helper.writableDatabase
        helper.close()
    }

    private fun upgradeFrom(version: Int) {
        seed(version)
        val opened = JuiceDatabase.build(context, name).also { db = it }
        runBlocking {
            seeded.forEach { (id, status) ->
                val item = assertNotNull(opened.itemDao().get(id), "row $id lost from v$version")
                assertEquals("Title $id", item.title)
                assertEquals(status, item.status)
            }
        }
    }

    @Test fun versionOneKeepsEveryRow() = upgradeFrom(1)

    @Test fun versionTwoKeepsEveryRow() = upgradeFrom(2)

    @Test fun versionThreeKeepsEveryRow() = upgradeFrom(3)

    @Test fun versionFourKeepsEveryRow() = upgradeFrom(4)

    @Test fun versionFiveKeepsEveryRow() = upgradeFrom(5)

    @Test fun versionSixKeepsEveryRow() = upgradeFrom(6)

    @Test fun versionSevenKeepsEveryRow() = upgradeFrom(7)

    @Test fun versionEightKeepsEveryRow() = upgradeFrom(8)

    @Test
    fun versionEightCardsHaveNoNotification() {
        upgradeFrom(8)
        val active = runBlocking { db!!.itemDao().active(Long.MAX_VALUE) }
        assertEquals(listOf("a", "r"), active.map { it.id })
        assertEquals(listOf(null, null), active.map { it.notifyAtMillis })
        assertEquals(listOf(null, null), active.map { it.notifyText })
    }

    @Test
    fun versionSevenCardAreUnstarredAndKeepTheirOrder() {
        upgradeFrom(7)
        val active = runBlocking { db!!.itemDao().active(Long.MAX_VALUE) }
        assertEquals(listOf("a", "r"), active.map { it.id })
        assertEquals(listOf(null, null), active.map { it.starredAtMillis })
    }

    @Test
    fun completedRowsFromBeforeV7StayInTheRecap() {
        upgradeFrom(6)
        val recap = runBlocking { db!!.itemDao().recapItems(Instant.parse("2026-10-28T12:00:00Z").minusSeconds(60).toEpochMilli()) }
        assertEquals(setOf("a", "c", "r"), recap.map { it.id }.toSet())
    }

    @Test
    fun savedItemsSurviveClosingAndReopeningTheFile() = runBlocking {
        val first = JuiceDatabase.build(context, name)
        val repository = ItemRepository(first.itemDao()) { Instant.ofEpochMilli(1_000) }
        listOf("x", "y", "w").forEach { repository.save(Item(it, ItemType.Task, "Title $it")) }
        first.close()
        db = JuiceDatabase.build(context, name)
        assertEquals(listOf("x", "y", "w"), ItemRepository(db!!.itemDao()).active().map { it.id })
    }
}
