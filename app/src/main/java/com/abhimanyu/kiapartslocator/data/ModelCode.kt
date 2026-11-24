package com.abhimanyu.kiapartslocator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "model_codes")
data class ModelCode(
    @PrimaryKey val id: String,
    val code: String,
    val model: String
)
