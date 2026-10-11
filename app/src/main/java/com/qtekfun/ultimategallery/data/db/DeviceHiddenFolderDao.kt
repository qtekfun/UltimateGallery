package com.qtekfun.ultimategallery.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceHiddenFolderDao {
    @Query("SELECT * FROM device_hidden_folder ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<DeviceHiddenFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: DeviceHiddenFolderEntity)

    @Query("DELETE FROM device_hidden_folder WHERE path = :path")
    suspend fun delete(path: String)
}
