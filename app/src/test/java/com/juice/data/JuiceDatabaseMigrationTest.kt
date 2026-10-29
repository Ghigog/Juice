package com.juice.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class JuiceDatabaseMigrationTest {
    private var helper: SupportSQLiteOpenHelper? = null

    @After
    fun tearDown() {
        helper?.close()
    }

    /** An in-memory items table with [columns] and the rows [rows] as (id, type, title, status). */
    private fun database(columns: String, vararg rows: List<String>): SupportSQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val created = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE items ($columns)")
                        rows.forEach { db.execSQL("INSERT INTO items (id, type, title, status) VALUES (?, ?, ?, ?)", it.toTypedArray()) }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        helper = created
        return created.writableDatabase
    }

    private fun SupportSQLiteDatabase.column(sql: String): List<String?> =
        query(sql).use { c ->
            buildList { while (c.moveToNext()) add(if (c.isNull(0)) null else c.getString(0)) }
        }

    private val base = "id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, title TEXT NOT NULL, deadlineMillis INTEGER, status TEXT NOT NULL"
    private val task = listOf("a", "Task", "A", "Active")

    @Test
    fun addEditedFieldsDefaultsToEmpty() {
        val db = database("$base, parentId TEXT", task)
        JuiceDatabase.addEditedFields.migrate(db)
        assertEquals(listOf<String?>(""), db.column("SELECT editedFields FROM items"))
    }

    @Test
    fun addCompletionConditionStartsNull() {
        val db = database("$base, parentId TEXT, editedFields TEXT NOT NULL DEFAULT ''", task)
        JuiceDatabase.addCompletionCondition.migrate(db)
        assertEquals(listOf<String?>(null), db.column("SELECT completionCondition FROM items"))
    }

    @Test
    fun addPositionNumbersRowsInInsertionOrder() {
        val db = database(
            "id TEXT NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, deadlineMillis INTEGER, status TEXT NOT NULL, PRIMARY KEY(id)",
            task, listOf("b", "Task", "B", "Active"),
        )
        JuiceDatabase.addPosition.migrate(db)
        assertEquals(listOf<String?>("a", "b"), db.column("SELECT id FROM items ORDER BY position"))
        assertEquals(listOf<String?>("1", "2"), db.column("SELECT position FROM items ORDER BY position"))
    }

    @Test
    fun addTimesAndCharacterIdAddsAllThreeColumnsAndStampsCompletedRows() {
        val db = database("$base", task, listOf("c", "Task", "C", "Completed"))
        JuiceDatabase.addTimesAndCharacterId.migrate(db)
        assertEquals(listOf<String?>(null, null), db.column("SELECT characterId FROM items ORDER BY id"))
        assertEquals(listOf(true, false), db.column("SELECT completedAtMillis FROM items ORDER BY id").map { it == null })
        assertEquals(listOf(true, false), db.column("SELECT createdAtMillis FROM items ORDER BY id").map { it == null })
    }
}
