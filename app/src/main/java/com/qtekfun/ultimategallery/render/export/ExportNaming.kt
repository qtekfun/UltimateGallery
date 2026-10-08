package com.qtekfun.ultimategallery.render.export

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Builds output file names from the pattern of the export settings. */
object ExportNaming {
    private val illegal = Regex("""[/\\:*?"<>|\u0000-\u001F\u007F]""")
    private val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** The original file name without its extension. */
    fun baseName(displayName: String): String = displayName.substringBeforeLast('.', displayName)

    /**
     * Expands `{name}`, `{n}` (1-based position of [index], zero padded to the width of [total]) and
     * `{date}`, sanitizes the result and appends [extension] (without dot).
     */
    fun fileName(pattern: String, originalName: String, index: Int, total: Int, extension: String, date: LocalDate = LocalDate.now()): String {
        val base = baseName(originalName)
        val width = total.coerceAtLeast(1).toString().length
        val expanded = pattern
            .replace("{name}", base)
            .replace("{n}", (index + 1).toString().padStart(width, '0'))
            .replace("{date}", date.format(dateFormat))
        var stem = sanitize(expanded)
        if (isEmptyStem(stem)) stem = sanitize(base)
        if (isEmptyStem(stem)) stem = "image"
        return "$stem.$extension"
    }

    /** Replaces characters that are illegal in file names and trims spaces and dots at the ends. */
    fun sanitize(name: String): String = name.replace(illegal, "_").trim().trimEnd('.', ' ')

    private fun isEmptyStem(stem: String) = stem.isBlank() || stem.all { it == '.' }
}
