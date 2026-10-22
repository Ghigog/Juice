package com.juice.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ItemEntity::class], version = 4, exportSchema = false)
abstract class JuiceDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao

    companion object {
        private val addEditedFields = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN editedFields TEXT NOT NULL DEFAULT ''")
            }
        }

        private val addCompletionCondition = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN completionCondition TEXT")
            }
        }

        private val addPosition = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE items SET position = rowid")
            }
        }

        @Volatile private var instance: JuiceDatabase? = null

        /** One shared instance, so the overlay's observers see writes made by the intake worker. */
        fun open(context: Context): JuiceDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, JuiceDatabase::class.java, "juice.db")
                .addMigrations(addEditedFields, addCompletionCondition, addPosition)
                .build()
                .also { instance = it }
        }
    }
}
