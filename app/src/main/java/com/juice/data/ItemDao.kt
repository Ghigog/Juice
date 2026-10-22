package com.juice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ItemEntity)

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: String): ItemEntity?

    @Query("SELECT MAX(position) FROM items")
    suspend fun maxPosition(): Int?

    @Query("UPDATE items SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)

    @Query("SELECT * FROM items WHERE status = 'Active' ORDER BY position, rowid")
    suspend fun active(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE status = 'Active' ORDER BY position, rowid")
    fun observeActive(): Flow<List<ItemEntity>>
}
