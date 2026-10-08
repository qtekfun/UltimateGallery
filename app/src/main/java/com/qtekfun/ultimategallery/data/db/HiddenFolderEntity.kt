package com.qtekfun.ultimategallery.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** A folder (MediaStore bucket) the user hid from this app's views. Nothing changes on disk. */
@Entity(tableName = "hidden_folder")
data class HiddenFolderEntity(@PrimaryKey val bucketId: Long, val name: String, val hiddenAt: Long)
