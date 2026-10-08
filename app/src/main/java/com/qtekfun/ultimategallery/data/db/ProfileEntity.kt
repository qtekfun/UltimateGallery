package com.qtekfun.ultimategallery.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** A stored watermark profile; the watermark itself is kept as JSON (see `ProfileCodec`). */
@Entity(tableName = "profile")
data class ProfileEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val json: String, val createdAt: Long, val updatedAt: Long)
