package com.example.socialstasts.persistance

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AccountEntity::class, PostEntity::class, PostDailyStatsEntity::class],
    version = 18,
    exportSchema = true
)
abstract class AppDb : RoomDatabase() {

    abstract fun statsDao(): StatsDao

    companion object {
        private const val DB_NAME = "social_stats.db"

        @Volatile
        private var INSTANCE: AppDb? = null

        /**
         * Returns a single shared Room database instance for the whole app.
         */
        fun get(context: Context): AppDb {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDb::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}