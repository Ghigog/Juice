package com.juice.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.juice.core.model.ItemStatus
import com.juice.core.model.Step

@Database(entities = [ItemEntity::class], version = 9, exportSchema = true)
abstract class JuiceDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao

    companion object {
        internal val addEditedFields = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN editedFields TEXT NOT NULL DEFAULT ''")
            }
        }

        internal val addCompletionCondition = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN completionCondition TEXT")
            }
        }

        internal val addPosition = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE items SET position = rowid")
            }
        }

        internal val addCadence = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN cadence TEXT")
                db.execSQL("ALTER TABLE items ADD COLUMN nextDueOn INTEGER")
            }
        }

        internal val addTimes = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN createdAtMillis INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN completedAtMillis INTEGER")
                // Completed rows have no recorded time; stamp them now so the Recap (which filters on it) keeps them.
                val now = System.currentTimeMillis()
                db.execSQL("UPDATE items SET completedAtMillis = ?, createdAtMillis = ? WHERE status = 'Completed'", arrayOf<Any>(now, now))
            }
        }

        internal val addCharacterId = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN characterId TEXT")
            }
        }

        /** Room takes one step per version pair, so the two 6→7 column additions run together. */
        internal val addTimesAndCharacterId = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addTimes.migrate(db)
                addCharacterId.migrate(db)
            }
        }

        internal val addStarredAt = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN starredAtMillis INTEGER")
            }
        }

        internal val addNotification = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN notifyAtMillis INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN notifyText TEXT")
            }
        }

        /** Child Task cards become steps of their Project; the parentId column goes. */
        internal val stepsInsideProjects = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val stepsByParent = LinkedHashMap<String, MutableList<Step>>()
                val parents = HashSet<String>()
                db.query("SELECT id FROM items WHERE parentId IS NULL").use { while (it.moveToNext()) parents += it.getString(0) }
                db.query("SELECT id, title, status, parentId FROM items WHERE parentId IS NOT NULL ORDER BY rowid").use {
                    while (it.moveToNext()) {
                        val parent = it.getString(3)
                        if (parent !in parents) continue
                        val status = it.getString(2)
                        if (status == ItemStatus.Archived.name) continue
                        stepsByParent.getOrPut(parent) { mutableListOf() }
                            .add(Step(it.getString(0), it.getString(1), status == ItemStatus.Completed.name))
                    }
                }
                db.execSQL(
                    "CREATE TABLE items_new (id TEXT NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, deadlineMillis INTEGER, " +
                        "status TEXT NOT NULL, editedFields TEXT NOT NULL DEFAULT '', completionCondition TEXT, " +
                        "steps TEXT NOT NULL DEFAULT '[]', PRIMARY KEY(id))",
                )
                // Children of a known Project are now steps; any other row is kept as it was.
                db.execSQL(
                    "INSERT INTO items_new (id, type, title, deadlineMillis, status, editedFields, completionCondition) " +
                        "SELECT id, type, title, deadlineMillis, status, editedFields, completionCondition FROM items " +
                        "WHERE parentId IS NULL OR parentId NOT IN (SELECT id FROM items WHERE parentId IS NULL) ORDER BY rowid",
                )
                stepsByParent.forEach { (parent, steps) ->
                    db.execSQL("UPDATE items_new SET steps = ? WHERE id = ?", arrayOf(steps.encode(), parent))
                }
                db.execSQL("DROP TABLE items")
                db.execSQL("ALTER TABLE items_new RENAME TO items")
            }
        }

        @Volatile private var instance: JuiceDatabase? = null

        /** One shared instance, so the overlay's observers see writes made by the intake worker. */
        fun open(context: Context): JuiceDatabase = instance ?: synchronized(this) {
            instance ?: build(context, "juice.db").also { instance = it }
        }

        /** A database file called [name] with every registered migration; never falls back to wiping data. */
        internal fun build(context: Context, name: String): JuiceDatabase =
            Room.databaseBuilder(context.applicationContext, JuiceDatabase::class.java, name)
                .addMigrations(addEditedFields, addCompletionCondition, stepsInsideProjects, addPosition, addCadence, addTimesAndCharacterId, addStarredAt, addNotification)
                .build()
    }
}
