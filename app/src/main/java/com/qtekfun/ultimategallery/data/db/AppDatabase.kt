package com.qtekfun.ultimategallery.data.db

import androidx.room3.Database
import androidx.room3.RoomDatabase

/** Local store for data that needs queries: hidden folders (app-only and on the device) and watermark profiles. */
@Database(
    entities = [HiddenFolderEntity::class, ProfileEntity::class, DeviceHiddenFolderEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hiddenFolderDao(): HiddenFolderDao

    abstract fun profileDao(): ProfileDao

    abstract fun deviceHiddenFolderDao(): DeviceHiddenFolderDao

    companion object {
        const val VERSION = 3

        val MIGRATIONS = arrayOf(Migration1To2, Migration2To3)
    }
}
