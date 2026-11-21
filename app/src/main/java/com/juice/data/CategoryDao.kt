package com.juice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    @Query("SELECT * FROM categories ORDER BY position, rowid")
    fun observe(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY position, rowid")
    suspend fun all(): List<CategoryEntity>

    @Query("UPDATE categories SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)

    @Query("SELECT MAX(position) FROM categories")
    suspend fun maxPosition(): Int?

    @Query("UPDATE categories SET colour = :colour WHERE id = :id")
    suspend fun setColour(id: String, colour: Int?)

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)
}
