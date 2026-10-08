package com.qtekfun.ultimategallery.render

import android.graphics.Typeface
import com.qtekfun.ultimategallery.domain.watermark.FontIds
import java.io.File

/** Loads the bundled fonts straight from the asset folder, as a stand-in for the font library in renderer tests. */
object TestFonts {
    val dir = File("src/main/assets/fonts")

    val bundledIds = listOf("Oswald", "PlayfairDisplay", "RobotoMono", "DancingScript", "Pacifico", "BebasNeue").map { FontIds.bundled(it) }

    fun file(name: String) = File(dir, "$name.ttf")

    val lookup: (String) -> Typeface? = { id ->
        id.takeIf { it.startsWith(FontIds.BUNDLED_PREFIX) }
            ?.let { file(it.removePrefix(FontIds.BUNDLED_PREFIX)) }
            ?.takeIf { it.isFile }
            ?.let { Typeface.createFromFile(it) }
    }
}
