package com.qtekfun.ultimategallery.data.folders

import com.qtekfun.ultimategallery.data.db.HiddenFolderDao
import com.qtekfun.ultimategallery.data.db.HiddenFolderEntity
import com.qtekfun.ultimategallery.data.media.MediaRepository
import com.qtekfun.ultimategallery.data.prefs.SettingsRepository
import com.qtekfun.ultimategallery.domain.Folder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** A folder with whether it is hidden from this app's views, and why. */
data class FolderState(val folder: Folder, val hiddenByUser: Boolean, val hiddenByApp: AppFolderEntry?) {
    val hidden: Boolean get() = hiddenByUser || hiddenByApp != null
}

/**
 * Folders as the app shows them. Hiding is app-only: nothing changes on disk and no `.nomedia` is
 * written. A folder is hidden when the user hid it or when its app is in the auto-hide list.
 */
@Singleton
class FolderRepository @Inject constructor(
    private val media: MediaRepository,
    private val hiddenDao: HiddenFolderDao,
    private val settings: SettingsRepository
) {
    /** Every folder with its hidden state. */
    fun all(): Flow<List<FolderState>> = combine(
        media.observeFolders(),
        hiddenDao.observeAll(),
        settings.settings.map { it.autoHideApps }.distinctUntilChanged()
    ) { folders, hidden, apps ->
        val hiddenIds = hidden.mapTo(HashSet()) { it.bucketId }
        val byApp = AppFolderCatalog.hiddenBy(folders, apps)
        folders.map { FolderState(it, it.bucketId in hiddenIds, byApp[it.bucketId]) }
    }

    /** The folders to show; with [includeHidden] the hidden ones too (the explicit folder list). */
    fun visible(includeHidden: Boolean = false): Flow<List<Folder>> = all().map { list -> list.filter { includeHidden || !it.hidden }.map { it.folder } }

    /** App folders found on the device. */
    fun detectedAppFolders(): Flow<List<DetectedAppFolders>> = media.observeFolders().map(AppFolderCatalog::detect).distinctUntilChanged()

    suspend fun hide(folders: List<Folder>, now: Long = System.currentTimeMillis()) {
        hiddenDao.insert(folders.map { HiddenFolderEntity(it.bucketId, it.name, now) })
    }

    suspend fun unhide(bucketIds: List<Long>) = hiddenDao.delete(bucketIds)
}
