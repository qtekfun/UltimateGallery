package com.qtekfun.ultimategallery.data.folders

/**
 * Which folders can be hidden on the device with a `.nomedia` file. The media scanner ignores a
 * `.nomedia` at the volume root and in the standard top-level folders (it would hide everything
 * below them), and `Android/data` and `Android/obb` are out of reach even with all-files access.
 */
object DeviceHidingRules {
    const val NOMEDIA = ".nomedia"

    private val STANDARD_TOP_LEVEL = setOf(
        "alarms", "android", "audiobooks", "dcim", "documents", "download", "movies", "music",
        "notifications", "pictures", "podcasts", "recordings", "ringtones"
    )
    private val PRIVATE_APP_ROOTS = listOf("android/data/", "android/obb/")

    /** True when a folder at [relativePath] (for example `Pictures/Trips/`) can get a `.nomedia`. */
    fun canHide(relativePath: String?): Boolean {
        val segments = segments(relativePath ?: return false)
        if (segments.isEmpty()) return false
        if (segments.size == 1 && segments[0].lowercase() in STANDARD_TOP_LEVEL) return false
        val normalized = segments.joinToString("/", postfix = "/").lowercase()
        return PRIVATE_APP_ROOTS.none { normalized.startsWith(it) }
    }

    /** [relativePath] without empty segments or a leading or trailing slash: `Pictures/Trips`. */
    fun normalize(relativePath: String): String = segments(relativePath).joinToString("/")

    private fun segments(path: String): List<String> = path.split('/').filter { it.isNotBlank() }
}
