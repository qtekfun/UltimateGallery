package com.qtekfun.ultimategallery.domain.export

import com.qtekfun.ultimategallery.domain.watermark.ExportSettings

/** Destination and example-name helpers shared by the export UI and the pipeline. */
object ExportPaths {
    private val AllowedRoots = listOf("Pictures", "DCIM")

    /** Trims blanks and slashes and collapses repeated slashes: `/Pictures//Default/` -> `Pictures/Trips`. */
    fun normalize(destination: String): String = destination.trim().split('/').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("/")

    /** The MediaStore only accepts image folders under Pictures or DCIM. */
    fun isValidDestination(destination: String): Boolean {
        val parts = normalize(destination).split('/')
        return parts.size >= 2 && parts[0] in AllowedRoots && parts.none { it == "." || it == ".." }
    }

    /** What a file name looks like with these settings, for the live preview. */
    fun exampleName(settings: ExportSettings): String {
        val base = settings.fileNamePattern
            .replace("{name}", "IMG_0042")
            .replace("{n}", "01")
            .replace("{date}", "20261008")
            .ifBlank { "IMG_0042" }
        return base + "." + settings.format.extension
    }
}
