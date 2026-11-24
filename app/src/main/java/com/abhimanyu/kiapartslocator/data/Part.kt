package com.abhimanyu.kiapartslocator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parts")
data class Part(
    @PrimaryKey val id: String,
    val partNumber: String,
    val location: String
)
