package com.qtekfun.ultimategallery.data.media

import com.qtekfun.ultimategallery.domain.Folder
import com.qtekfun.ultimategallery.domain.MediaItem
import kotlinx.coroutines.flow.Flow

/** Read access to the device's photos and videos. All flows refresh when the MediaStore changes. */
interface MediaRepository {
    /** Every folder (bucket) that holds at least one image or video, newest first. */
    fun observeFolders(): Flow<List<Folder>>

    /** The items of a folder, newest first. */
    fun observeItems(bucketId: Long): Flow<List<MediaItem>>

    /** The items with these MediaStore ids, in the order given; missing ids are skipped. */
    suspend fun loadItems(ids: List<Long>): List<MediaItem>
}
