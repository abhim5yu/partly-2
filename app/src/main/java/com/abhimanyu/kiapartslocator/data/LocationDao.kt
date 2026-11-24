package com.abhimanyu.kiapartslocator.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations")
    fun observeAll(): Flow<List<LocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(location: LocationEntity)

    @Delete
    suspend fun delete(location: LocationEntity)

    @Query("DELETE FROM locations WHERE id = :id")
    suspend fun deleteById(id: String)
}
