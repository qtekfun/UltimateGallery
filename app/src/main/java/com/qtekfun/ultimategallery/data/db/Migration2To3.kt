package com.qtekfun.ultimategallery.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** v3: folders hidden on the device with `.nomedia`. */
@Suppress("MagicNumber")
object Migration2To3 : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `device_hidden_folder` (" +
                "`path` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`relativePath` TEXT NOT NULL, " +
                "`hiddenAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`path`))"
        )
    }
}
