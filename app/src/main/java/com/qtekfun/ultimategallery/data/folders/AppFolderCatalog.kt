package com.qtekfun.ultimategallery.data.folders

import androidx.annotation.StringRes
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.domain.Folder

/**
 * A kind of app-generated folder (WhatsApp, Telegram, Screenshots...) recognised by folder name or
 * path fragments, compared in lower case.
 */
data class AppFolderEntry(
    val id: String,
    /** Brand name, used when [labelRes] is null. */
    val label: String,
    @StringRes val labelRes: Int? = null,
    val nameFragments: List<String> = emptyList(),
    val pathFragments: List<String> = emptyList()
) {
    fun matches(folder: Folder): Boolean {
        val name = folder.name.lowercase()
        val path = folder.relativePath?.lowercase().orEmpty()
        return nameFragments.any { it in name } || pathFragments.any { it in path }
    }
}

/** App folders found on the device for one catalog entry. */
data class DetectedAppFolders(val entry: AppFolderEntry, val folders: List<Folder>)

/** The editable catalog of known app folders, matched against the real MediaStore buckets. */
object AppFolderCatalog {
    const val WHATSAPP = "whatsapp"

    val entries: List<AppFolderEntry> = listOf(
        AppFolderEntry(WHATSAPP, "WhatsApp", nameFragments = listOf("whatsapp"), pathFragments = listOf("whatsapp")),
        AppFolderEntry("telegram", "Telegram", nameFragments = listOf("telegram"), pathFragments = listOf("telegram")),
        AppFolderEntry(
            "screenshots",
            "Screenshots",
            R.string.app_folder_screenshots,
            nameFragments = listOf("screenshot", "capturas de pantalla"),
            pathFragments = listOf("screenshots")
        ),
        AppFolderEntry(
            "screenrecordings",
            "Screen recordings",
            R.string.app_folder_screen_recordings,
            nameFragments = listOf("screen recording", "screenrecord", "grabaciones de pantalla"),
            pathFragments = listOf("screenrecord", "screen recordings")
        ),
        AppFolderEntry("instagram", "Instagram", nameFragments = listOf("instagram"), pathFragments = listOf("instagram")),
        AppFolderEntry("messenger", "Messenger", nameFragments = listOf("messenger"), pathFragments = listOf("messenger")),
        AppFolderEntry("facebook", "Facebook", nameFragments = listOf("facebook"), pathFragments = listOf("facebook")),
        AppFolderEntry("snapchat", "Snapchat", nameFragments = listOf("snapchat"), pathFragments = listOf("snapchat")),
        AppFolderEntry("tiktok", "TikTok", nameFragments = listOf("tiktok"), pathFragments = listOf("tiktok")),
        AppFolderEntry("signal", "Signal", nameFragments = listOf("signal"), pathFragments = listOf("signal")),
        AppFolderEntry("pinterest", "Pinterest", nameFragments = listOf("pinterest"), pathFragments = listOf("pinterest")),
        AppFolderEntry("twitter", "X (Twitter)", nameFragments = listOf("twitter"), pathFragments = listOf("twitter"))
    )

    fun byId(id: String): AppFolderEntry? = entries.firstOrNull { it.id == id }

    /** The catalog entries with at least one matching folder, WhatsApp first, then in catalog order. */
    fun detect(folders: List<Folder>): List<DetectedAppFolders> = entries.mapNotNull { entry ->
        folders.filter(entry::matches).takeIf { it.isNotEmpty() }?.let { DetectedAppFolders(entry, it) }
    }

    /** Bucket ids hidden because their app is in [hiddenApps]. */
    fun hiddenBy(folders: List<Folder>, hiddenApps: Set<String>): Map<Long, AppFolderEntry> {
        if (hiddenApps.isEmpty()) return emptyMap()
        val active = entries.filter { it.id in hiddenApps }
        val out = LinkedHashMap<Long, AppFolderEntry>()
        for (folder in folders) active.firstOrNull { it.matches(folder) }?.let { out[folder.bucketId] = it }
        return out
    }
}
