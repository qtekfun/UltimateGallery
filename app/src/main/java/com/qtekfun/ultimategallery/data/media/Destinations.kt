package com.qtekfun.ultimategallery.data.media

import com.qtekfun.ultimategallery.domain.Folder
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.export.ExportPaths

/** Rules for where move and copy may put files. MediaStore only accepts media under certain top-level folders. */
object Destinations {
    private val ImageRoots = listOf("Pictures", "DCIM")
    private val VideoRoots = listOf("Movies", "DCIM", "Pictures")
    private val IllegalFolderChars = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    /** Where new folders are created from the picker. */
    const val NEW_FOLDER_ROOT = "Pictures"

    fun normalize(destination: String): String = ExportPaths.normalize(destination.replace('\\', '/'))

    /** The value for `RELATIVE_PATH`: normalised with a trailing slash. */
    fun toRelativePath(destination: String): String = normalize(destination) + "/"

    fun isValid(destination: String, isVideo: Boolean): Boolean {
        val parts = normalize(destination).split('/')
        val roots = if (isVideo) VideoRoots else ImageRoots
        return parts[0] in roots && parts.none { it == "." || it == ".." }
    }

    /** True when every item can be written to [destination]. */
    fun isValidFor(destination: String, items: List<MediaItem>): Boolean = items.isNotEmpty() && items.all { isValid(destination, it.isVideo) }

    /** The distinct existing folder paths (without trailing slash) where images (or videos, with [forVideo]) may be written, sorted. */
    fun suggestions(folders: List<Folder>, forVideo: Boolean = false): List<String> = folders.mapNotNull { it.relativePath }
        .map { normalize(it) }
        .filter { it.isNotEmpty() && isValid(it, forVideo) }
        .distinct()
        .sortedBy { it.lowercase() }

    /** Whether [name] is usable as a single new folder name. */
    fun isValidFolderName(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.isNotEmpty() && trimmed != "." && trimmed != ".." && !IllegalFolderChars.containsMatchIn(trimmed)
    }

    /** `Pictures` + `Trips` -> `Pictures/Trips`; null when [name] is not a valid folder name. */
    fun newFolderPath(root: String, name: String): String? {
        if (!isValidFolderName(name)) return null
        val base = normalize(root)
        return if (base.isEmpty()) name.trim() else "$base/${name.trim()}"
    }
}
