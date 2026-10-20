package com.juice.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ItemEntity::class], version = 1, exportSchema = false)
abstract class JuiceDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao

    companion object {
        fun open(context: Context): JuiceDatabase =
            Room.databaseBuilder(context, JuiceDatabase::class.java, "juice.db").build()
    }
}
