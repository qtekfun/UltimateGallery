package com.qtekfun.ultimategallery.domain

import android.net.Uri

/** A folder as the user sees it: a MediaStore bucket. */
data class Folder(
    val bucketId: Long,
    val name: String,
    /** Path relative to the volume root, for example `Pictures/Wallapop/`; null when unknown. */
    val relativePath: String?,
    val count: Int,
    val coverUri: Uri,
    val coverIsVideo: Boolean,
    val newestDateMs: Long
)

/** An image or video in the MediaStore. */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val dateMs: Long,
    /** Pixel size as displayed, with the stored orientation already applied. */
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val durationMs: Long,
    val bucketId: Long,
    val relativePath: String?
) {
    val isGif: Boolean get() = mimeType == "image/gif"
}
