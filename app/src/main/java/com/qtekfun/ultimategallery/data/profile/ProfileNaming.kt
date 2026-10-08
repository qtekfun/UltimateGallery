package com.qtekfun.ultimategallery.data.profile

/** Naming rules for profiles. */
object ProfileNaming {
    private const val MAX_LENGTH = 40

    fun clean(name: String): String = name.trim().replace(Regex("\\s+"), " ").take(MAX_LENGTH)

    /** A name based on [base] that is not in [existing], compared ignoring case: "X copy", "X copy 2"... */
    fun copyName(base: String, existing: Collection<String>, suffix: String = "copy"): String {
        val taken = existing.map { it.lowercase() }.toSet()
        var candidate = clean("$base $suffix")
        var n = 2
        while (candidate.lowercase() in taken) {
            candidate = clean("$base $suffix $n")
            n++
        }
        return candidate
    }
}
