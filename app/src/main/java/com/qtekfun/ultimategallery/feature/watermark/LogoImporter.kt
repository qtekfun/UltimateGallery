package com.qtekfun.ultimategallery.feature.watermark

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Copies a picked logo into app-private storage so a profile keeps working after the original moves. */
@Singleton
class LogoImporter @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir: File get() = File(context.filesDir, "watermarks").apply { mkdirs() }

    /** Returns a `file:` Uri of the stored copy, or null when [source] is not a decodable image. */
    fun import(source: Uri): Uri? = runCatching {
        val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() } ?: return null
        // Reject anything that is not an image before keeping it.
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes)))
        val target = File(dir, "logo-${UUID.randomUUID()}.img")
        target.writeBytes(bytes)
        Uri.fromFile(target)
    }.getOrNull()
}
