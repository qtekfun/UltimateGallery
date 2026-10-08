package com.qtekfun.ultimategallery.data.db

import androidx.room3.Database
import androidx.room3.RoomDatabase

/** Local store for data that needs queries: hidden folders and watermark profiles. */
@Database(
    entities = [HiddenFolderEntity::class, ProfileEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hiddenFolderDao(): HiddenFolderDao

    abstract fun profileDao(): ProfileDao

    companion object {
        const val VERSION = 2

        val MIGRATIONS = arrayOf(Migration1To2)
    }
}
