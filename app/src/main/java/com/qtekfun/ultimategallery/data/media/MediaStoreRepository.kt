package com.qtekfun.ultimategallery.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.qtekfun.ultimategallery.di.IoDispatcher
import com.qtekfun.ultimategallery.domain.Folder
import com.qtekfun.ultimategallery.domain.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * MediaStore-backed [MediaRepository]. Queries use a thin projection and group and sort in Kotlin
 * because the MediaStore does not support GROUP BY. Date taken falls back to date added.
 */
@Singleton
class MediaStoreRepository @Inject constructor(@ApplicationContext private val context: Context, @IoDispatcher private val io: CoroutineDispatcher) :
    MediaRepository {
    private val resolver: ContentResolver get() = context.contentResolver

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.debounce(CHANGE_DEBOUNCE_MS).onStart { emit(Unit) }.conflate()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFolders(): Flow<List<Folder>> = changes().mapLatest { queryFolders() }.distinctUntilChanged().flowOn(io)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeItems(bucketId: Long): Flow<List<MediaItem>> = changes().mapLatest { queryItems(BUCKET_SELECTION, arrayOf(bucketId.toString())) }
        .distinctUntilChanged()
        .flowOn(io)

    override suspend fun loadItems(ids: List<Long>): List<MediaItem> = withContext(io) {
        if (ids.isEmpty()) return@withContext emptyList()
        val byId = ids.chunked(ID_CHUNK).flatMap { chunk ->
            queryItems("${MediaStore.MediaColumns._ID} IN (${chunk.joinToString(",")})", emptyArray())
        }.associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }

    private fun queryFolders(): List<Folder> {
        class Acc(val bucketId: Long, var name: String?, var path: String?) {
            var count = 0
            var newestDate = Long.MIN_VALUE
            var newestId = 0L
            var newestVideo = false
        }
        val accs = LinkedHashMap<Long, Acc>()
        query(FOLDER_PROJECTION, MEDIA_TYPE_SELECTION, emptyArray()) { c ->
            val id = c.getLong(0)
            val bucketId = c.getLong(1)
            val acc = accs.getOrPut(bucketId) { Acc(bucketId, c.getString(2), c.getString(3)) }
            val isVideo = c.getInt(4) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val date = effectiveDate(c.getLong(5), c.getLong(6))
            acc.count++
            if (date > acc.newestDate || (date == acc.newestDate && id > acc.newestId)) {
                acc.newestDate = date
                acc.newestId = id
                acc.newestVideo = isVideo
            }
        }
        return accs.values.map {
            Folder(
                bucketId = it.bucketId,
                name = folderName(it.name, it.path),
                relativePath = it.path,
                count = it.count,
                coverUri = mediaUri(it.newestId, it.newestVideo),
                coverIsVideo = it.newestVideo,
                newestDateMs = it.newestDate
            )
        }.sortedByDescending { it.newestDateMs }
    }

    private fun queryItems(selection: String, args: Array<String>): List<MediaItem> {
        val items = ArrayList<MediaItem>()
        query(ITEM_PROJECTION, "($selection) AND $MEDIA_TYPE_SELECTION", args) { c ->
            val id = c.getLong(0)
            val isVideo = c.getInt(2) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val rotated = c.getInt(10) % HALF_TURN != 0
            val w = c.getInt(7)
            val h = c.getInt(8)
            items += MediaItem(
                id = id,
                uri = mediaUri(id, isVideo),
                displayName = c.getString(1).orEmpty(),
                mimeType = c.getString(3).orEmpty(),
                isVideo = isVideo,
                dateMs = effectiveDate(c.getLong(4), c.getLong(5)),
                width = if (rotated) h else w,
                height = if (rotated) w else h,
                sizeBytes = c.getLong(9),
                durationMs = c.getLong(11),
                bucketId = c.getLong(12),
                relativePath = c.getString(13)
            )
        }
        return items.sortedWith(compareByDescending<MediaItem> { it.dateMs }.thenByDescending { it.id })
    }

    private inline fun query(projection: Array<String>, selection: String, args: Array<String>, row: (Cursor) -> Unit) {
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
        }
        resolver.query(FILES_URI, projection, queryArgs, null)?.use { c ->
            while (c.moveToNext()) row(c)
        }
    }

    companion object {
        private const val CHANGE_DEBOUNCE_MS = 400L
        private const val ID_CHUNK = 500
        private const val HALF_TURN = 180
        private const val MS_PER_SECOND = 1000L

        private val FILES_URI: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        private val MEDIA_TYPE_SELECTION =
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN " +
                "(${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
        private const val BUCKET_SELECTION = "${MediaStore.MediaColumns.BUCKET_ID} = ?"

        private val FOLDER_PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED
        )
        private val ITEM_PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.ORIENTATION,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.RELATIVE_PATH
        )

        /** Date taken in ms, or date added (seconds) converted to ms when the former is missing. */
        internal fun effectiveDate(dateTakenMs: Long, dateAddedSec: Long): Long = if (dateTakenMs > 0) dateTakenMs else dateAddedSec * MS_PER_SECOND

        internal fun folderName(displayName: String?, relativePath: String?): String = displayName?.takeIf { it.isNotBlank() }
            ?: relativePath?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "?"

        fun mediaUri(id: Long, isVideo: Boolean): Uri = ContentUris.withAppendedId(
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            id
        )
    }
}
