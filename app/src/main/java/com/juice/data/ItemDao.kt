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

    @Query("SELECT * FROM items WHERE characterId = :characterId ORDER BY position, rowid")
    suspend fun byCharacter(characterId: String): List<ItemEntity>

    @Query("DELETE FROM items WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: String)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM items WHERE status = 'Active' AND (nextDueOn IS NULL OR nextDueOn <= :todayEpochDay) ORDER BY position, rowid")
    suspend fun active(todayEpochDay: Long): List<ItemEntity>

    @Query("SELECT * FROM items WHERE status = 'Active' AND (nextDueOn IS NULL OR nextDueOn <= :todayEpochDay) ORDER BY position, rowid")
    fun observeActive(todayEpochDay: Long): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE status != 'Archived' AND (status = 'Active' OR completedAtMillis >= :sinceMillis) ORDER BY position, rowid")
    suspend fun recapItems(sinceMillis: Long): List<ItemEntity>

    @Query("SELECT * FROM items WHERE status != 'Archived' AND (status = 'Active' OR completedAtMillis >= :sinceMillis) ORDER BY position, rowid")
    fun observeRecapItems(sinceMillis: Long): Flow<List<ItemEntity>>
}
