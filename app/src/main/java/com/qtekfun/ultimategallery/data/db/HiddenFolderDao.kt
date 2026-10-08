package com.qtekfun.ultimategallery.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenFolderDao {
    @Query("SELECT * FROM hidden_folder ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<HiddenFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folders: List<HiddenFolderEntity>)

    @Query("DELETE FROM hidden_folder WHERE bucketId IN (:bucketIds)")
    suspend fun delete(bucketIds: List<Long>)
}
