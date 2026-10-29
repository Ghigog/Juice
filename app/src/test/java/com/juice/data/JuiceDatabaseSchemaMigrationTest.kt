package com.juice.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** Runs the registered migrations over the exported schemas in app/schemas, validating the result against the entity. */
@RunWith(RobolectricTestRunner::class)
class JuiceDatabaseSchemaMigrationTest {
    private val name = "schema-migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), JuiceDatabase::class.java)

    private val migrations = arrayOf(
        JuiceDatabase.addEditedFields, JuiceDatabase.addCompletionCondition, JuiceDatabase.stepsInsideProjects,
        JuiceDatabase.addPosition, JuiceDatabase.addCadence, JuiceDatabase.addTimesAndCharacterId,
        JuiceDatabase.addStarredAt, JuiceDatabase.addNotification,
    )

    @Test
    fun everyMigrationFromTheOldestExportedSchemaMatchesTheEntity() {
        helper.createDatabase(name, 1).use { db ->
            db.execSQL("INSERT INTO items (id, type, title, status) VALUES ('a', 'Task', 'A', 'Active')")
        }
        helper.runMigrationsAndValidate(name, 9, true, *migrations).use { db ->
            db.query("SELECT id, steps, position FROM items").use { c ->
                c.moveToFirst()
                assertEquals(listOf("a", "[]", "1"), listOf(c.getString(0), c.getString(1), c.getString(2)))
            }
        }
    }
}
