package com.abhimanyu.kiapartslocator.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PartDao {
    @Query("SELECT * FROM parts")
    fun observeAll(): Flow<List<Part>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(part: Part)

    @Delete
    suspend fun delete(part: Part)

    @Query("DELETE FROM parts WHERE id = :id")
    suspend fun deleteById(id: String)
}
