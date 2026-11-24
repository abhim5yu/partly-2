package com.abhimanyu.kiapartslocator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val id: String,
    val value: String,
    val group: String
)
