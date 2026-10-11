package com.qtekfun.ultimategallery.data.folders

import com.qtekfun.ultimategallery.data.db.DeviceHiddenFolderDao
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
 * Folders as the app shows them. Hiding here is app-only: nothing changes on disk. A folder is hidden
 * when the user hid it or when its app is in the auto-hide list. Folders hidden on the device with a
 * `.nomedia` (see [DeviceHidingRepository]) are left out altogether, even while the media scanner
 * still lists them.
 */
@Singleton
class FolderRepository @Inject constructor(
    private val media: MediaRepository,
    private val hiddenDao: HiddenFolderDao,
    private val settings: SettingsRepository,
    private val deviceHiddenDao: DeviceHiddenFolderDao
) {
    /** Every folder with its hidden state. */
    fun all(): Flow<List<FolderState>> = combine(
        media.observeFolders(),
        hiddenDao.observeAll(),
        settings.settings.map { it.autoHideApps }.distinctUntilChanged(),
        deviceHiddenDao.observeAll().map { rows -> rows.mapTo(HashSet()) { DeviceHidingRules.normalize(it.relativePath) } }
    ) { folders, hidden, apps, onDevice ->
        val hiddenIds = hidden.mapTo(HashSet()) { it.bucketId }
        val shown = folders.filter { f -> f.relativePath == null || DeviceHidingRules.normalize(f.relativePath) !in onDevice }
        val byApp = AppFolderCatalog.hiddenBy(shown, apps)
        shown.map { FolderState(it, it.bucketId in hiddenIds, byApp[it.bucketId]) }
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
