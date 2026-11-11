package com.juice.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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

    private val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
    private val migrations = JuiceDatabase.migrations(clock)

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
