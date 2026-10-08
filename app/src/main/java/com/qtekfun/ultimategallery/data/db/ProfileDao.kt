package com.qtekfun.ultimategallery.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profile WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Query("SELECT * FROM profile ORDER BY updatedAt DESC LIMIT 1")
    suspend fun mostRecentlyUpdated(): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profile")
    suspend fun count(): Int

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("DELETE FROM profile WHERE id = :id")
    suspend fun delete(id: Long)
}
