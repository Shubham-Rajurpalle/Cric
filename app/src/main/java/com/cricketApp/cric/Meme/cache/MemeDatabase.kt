package com.cricketApp.cric.Meme.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities  = [MemeEntity::class],
    version   = 1,
    exportSchema = false
)
@TypeConverters(MemeTypeConverters::class)
abstract class MemeDatabase : RoomDatabase() {

    abstract fun memeDao(): MemeDao

    companion object {
        @Volatile private var INSTANCE: MemeDatabase? = null

        fun getInstance(context: Context): MemeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MemeDatabase::class.java,
                    "meme_cache.db"
                )
                    .fallbackToDestructiveMigration() // fine for a cache DB
                    .build()
                    .also { INSTANCE = it }
            }
    }
}