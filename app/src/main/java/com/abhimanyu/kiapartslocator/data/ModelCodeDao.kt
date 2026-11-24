package com.abhimanyu.kiapartslocator.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelCodeDao {
    @Query("SELECT * FROM model_codes")
    fun observeAll(): Flow<List<ModelCode>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(modelCode: ModelCode)

    @Delete
    suspend fun delete(modelCode: ModelCode)

    @Query("DELETE FROM model_codes WHERE id = :id")
    suspend fun deleteById(id: String)
}
