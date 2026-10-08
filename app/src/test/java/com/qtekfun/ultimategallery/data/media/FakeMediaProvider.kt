package com.qtekfun.ultimategallery.data.media

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore

/** One row of fake MediaStore data. Dates: [dateTaken] in ms, [dateAdded] in seconds. */
data class FakeRow(
    val id: Long,
    val name: String,
    val isVideo: Boolean = false,
    val bucketId: Long,
    val bucketName: String?,
    val path: String?,
    val dateTaken: Long = 0,
    val dateAdded: Long = 0,
    val width: Int = 4000,
    val height: Int = 3000,
    val orientation: Int = 0,
    val size: Long = 1000,
    val mime: String = if (isVideo) "video/mp4" else "image/jpeg"
)

/**
 * A stand-in for the MediaStore provider. It understands just the selections the repository
 * issues: a bucket filter and an `_id IN (...)` list.
 */
class FakeMediaProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<String>?, queryArgs: Bundle?, signal: CancellationSignal?): Cursor {
        val selection = queryArgs?.getString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION).orEmpty()
        val args = queryArgs?.getStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS).orEmpty()
        var matching = rows.toList()
        if ("bucket_id = ?" in selection) matching = matching.filter { it.bucketId == args[0].toLong() }
        Regex("_id IN \\(([0-9,]+)\\)").find(selection)?.let { m ->
            val ids = m.groupValues[1].split(",").map { it.toLong() }.toSet()
            matching = matching.filter { it.id in ids }
        }
        val columns = projection ?: emptyArray()
        val cursor = MatrixCursor(columns)
        matching.forEach { r -> cursor.addRow(columns.map { value(r, it) }) }
        return cursor
    }

    private fun value(r: FakeRow, column: String): Any? = when (column) {
        MediaStore.MediaColumns._ID -> r.id

        MediaStore.MediaColumns.DISPLAY_NAME -> r.name

        MediaStore.Files.FileColumns.MEDIA_TYPE ->
            if (r.isVideo) {
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            } else {
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
            }

        MediaStore.MediaColumns.MIME_TYPE -> r.mime

        MediaStore.MediaColumns.DATE_TAKEN -> r.dateTaken

        MediaStore.MediaColumns.DATE_ADDED -> r.dateAdded

        MediaStore.MediaColumns.DATE_MODIFIED -> r.dateAdded

        MediaStore.MediaColumns.WIDTH -> r.width

        MediaStore.MediaColumns.HEIGHT -> r.height

        MediaStore.MediaColumns.SIZE -> r.size

        MediaStore.MediaColumns.ORIENTATION -> r.orientation

        MediaStore.MediaColumns.DURATION -> if (r.isVideo) 5000L else null

        MediaStore.MediaColumns.BUCKET_ID -> r.bucketId

        MediaStore.MediaColumns.BUCKET_DISPLAY_NAME -> r.bucketName

        MediaStore.MediaColumns.RELATIVE_PATH -> r.path

        else -> null
    }

    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0

    companion object {
        @Volatile
        var rows: List<FakeRow> = emptyList()
    }
}
