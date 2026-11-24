package com.abhimanyu.kiapartslocator.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Part::class, LocationEntity::class, ModelCode::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun partDao(): PartDao
    abstract fun locationDao(): LocationDao
    abstract fun modelCodeDao(): ModelCodeDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "parts_picker.db"
                ).build().also { INSTANCE = it }
            }
    }
}
