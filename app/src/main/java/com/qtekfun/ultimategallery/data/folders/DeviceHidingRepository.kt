package com.qtekfun.ultimategallery.data.folders

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
import com.qtekfun.ultimategallery.data.db.DeviceHiddenFolderDao
import com.qtekfun.ultimategallery.data.db.DeviceHiddenFolderEntity
import com.qtekfun.ultimategallery.di.IoDispatcher
import com.qtekfun.ultimategallery.domain.Folder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Outcome of hiding or showing a folder on the device. */
enum class DeviceHideResult { DONE, NO_ACCESS, NOT_ALLOWED, NOT_FOUND, FAILED }

/** Where the folders live on disk and how to reach them; replaced by a fake in tests. */
interface DeviceStorage {
    /** True when the app holds all-files access, which writing `.nomedia` needs. */
    fun hasAllFilesAccess(): Boolean

    /** The folder of a MediaStore bucket on disk, or null when it can't be resolved. */
    suspend fun directoryOf(bucketId: Long): File?

    /** Asks the media scanner to look at [dir] again. */
    suspend fun rescan(dir: File)
}

/**
 * Hides folders on the whole device with a `.nomedia` file and shows them again by deleting it.
 * This is the only feature that writes outside of the MediaStore, and it needs all-files access.
 * Originals are never touched: only the empty `.nomedia` marker is created or removed.
 */
@Singleton
class DeviceHidingRepository @Inject constructor(private val dao: DeviceHiddenFolderDao, private val storage: DeviceStorage) {
    /** Folders this app hid on the device. */
    fun observe(): Flow<List<DeviceHiddenFolderEntity>> = dao.observeAll()

    fun hasAccess(): Boolean = storage.hasAllFilesAccess()

    suspend fun hide(folder: Folder, now: Long = System.currentTimeMillis()): DeviceHideResult {
        if (!DeviceHidingRules.canHide(folder.relativePath)) return DeviceHideResult.NOT_ALLOWED
        if (!storage.hasAllFilesAccess()) return DeviceHideResult.NO_ACCESS
        val dir = storage.directoryOf(folder.bucketId)?.takeIf { it.isDirectory } ?: return DeviceHideResult.NOT_FOUND
        val marker = File(dir, DeviceHidingRules.NOMEDIA)
        val written = runCatching { marker.exists() || marker.createNewFile() }.getOrDefault(false)
        if (!written) return DeviceHideResult.FAILED
        dao.insert(DeviceHiddenFolderEntity(dir.path, folder.name, folder.relativePath.orEmpty(), now))
        storage.rescan(dir)
        return DeviceHideResult.DONE
    }

    /** Removes the `.nomedia` of a folder hidden by [hide]. A folder that no longer exists is just forgotten. */
    suspend fun show(path: String): DeviceHideResult {
        if (!storage.hasAllFilesAccess()) return DeviceHideResult.NO_ACCESS
        val dir = File(path)
        val marker = File(dir, DeviceHidingRules.NOMEDIA)
        val removed = runCatching { !marker.exists() || marker.delete() }.getOrDefault(false)
        if (!removed) return DeviceHideResult.FAILED
        dao.delete(path)
        if (dir.isDirectory) storage.rescan(dir)
        return DeviceHideResult.DONE
    }
}

/** [DeviceStorage] on a real device: MediaStore for the location and the media scanner for refreshes. */
class AndroidDeviceStorage @Inject constructor(@ApplicationContext private val context: Context, @IoDispatcher private val io: CoroutineDispatcher) :
    DeviceStorage {
    override fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    override suspend fun directoryOf(bucketId: Long): File? = withContext(io) {
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.BUCKET_ID} = ?")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(bucketId.toString()))
            putInt(ContentResolver.QUERY_ARG_LIMIT, 1)
        }
        val projection = arrayOf(MediaStore.MediaColumns.VOLUME_NAME, MediaStore.MediaColumns.RELATIVE_PATH)
        val (volume, relative) = context.contentResolver.query(FILES_URI, projection, args, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) to c.getString(1) else null
        } ?: return@withContext null
        if (volume == null || relative == null) return@withContext null
        val storageManager = context.getSystemService(StorageManager::class.java)
        val root = runCatching { storageManager.getStorageVolume(MediaStore.Files.getContentUri(volume)).directory }.getOrNull()
        root?.let { File(it, DeviceHidingRules.normalize(relative)) }
    }

    override suspend fun rescan(dir: File) {
        // The scanner may never call back for a folder that just disappeared from the index: don't wait forever.
        withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                MediaScannerConnection.scanFile(context, arrayOf(dir.path), null) { _, _ -> if (cont.isActive) cont.resume(Unit) }
            }
        }
    }

    private companion object {
        const val SCAN_TIMEOUT_MS = 10_000L
        val FILES_URI: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    }
}

/** The system screen where the user grants all-files access to this app. */
fun allFilesAccessIntent(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", context.packageName, null))
