package com.qtekfun.ultimategallery.data.db

import androidx.room3.Database
import androidx.room3.RoomDatabase

/** Local store for data that needs queries: hidden folders and watermark profiles. */
@Database(
    entities = [HiddenFolderEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hiddenFolderDao(): HiddenFolderDao

    companion object {
        const val VERSION = 1
    }
}
