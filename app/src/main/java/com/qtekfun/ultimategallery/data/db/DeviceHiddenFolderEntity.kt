package com.qtekfun.ultimategallery.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * A folder hidden on the whole device with a `.nomedia` file. The MediaStore stops listing it, so the
 * app keeps its own record to show it in Settings and to undo the hiding.
 */
@Entity(tableName = "device_hidden_folder")
data class DeviceHiddenFolderEntity(
    /** Absolute path of the folder, for example `/storage/emulated/0/Pictures/Trips`. */
    @PrimaryKey val path: String,
    val name: String,
    /** Path relative to the volume root, for example `Pictures/Trips/`. */
    val relativePath: String,
    val hiddenAt: Long
)
