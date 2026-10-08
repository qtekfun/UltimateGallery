package com.qtekfun.ultimategallery.data.media

/** Pure helpers for file names used by rename and copy. */
object FileNames {
    private val Illegal = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    /** Removes path separators, characters MediaStore rejects and leading dots, then trims. Returns an empty string when nothing is left. */
    fun sanitize(input: String): String = input.replace(Illegal, "").trim().trimStart('.').trim()

    /** The extension without the dot, or an empty string. A leading dot alone is not an extension. */
    fun extension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0 || dot == name.length - 1) "" else name.substring(dot + 1)
    }

    fun baseName(name: String): String {
        val ext = extension(name)
        return if (ext.isEmpty()) name else name.dropLast(ext.length + 1)
    }

    /**
     * The new file name for a rename: [input] sanitized, with the extension of [original] appended when the
     * user left it out. Null when nothing usable is left.
     */
    fun renamed(original: String, input: String): String? {
        val clean = sanitize(input)
        if (clean.isEmpty()) return null
        val ext = extension(original)
        return if (ext.isNotEmpty() && !clean.endsWith(".$ext", ignoreCase = true)) "$clean.$ext" else clean
    }

    /** `photo.jpg` + `_nometa` -> `photo_nometa.jpg`. */
    fun withSuffix(name: String, suffix: String): String {
        val ext = extension(name)
        return if (ext.isEmpty()) name + suffix else baseName(name) + suffix + "." + ext
    }

    /** [name], or `base (2).ext`, `base (3).ext`... the first one not in [taken] (case-insensitive). */
    fun unique(name: String, taken: Set<String>): String {
        val lower = taken.mapTo(HashSet()) { it.lowercase() }
        if (name.lowercase() !in lower) return name
        val ext = extension(name)
        val base = baseName(name)
        var n = 2
        while (true) {
            val candidate = if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext"
            if (candidate.lowercase() !in lower) return candidate
            n++
        }
    }
}
