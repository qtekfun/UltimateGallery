package com.qtekfun.ultimategallery.data.fonts

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.net.Uri
import android.provider.OpenableColumns
import com.qtekfun.ultimategallery.di.IoDispatcher
import com.qtekfun.ultimategallery.domain.watermark.FontIds
import com.qtekfun.ultimategallery.domain.watermark.FontImportResult
import com.qtekfun.ultimategallery.domain.watermark.FontInfo
import com.qtekfun.ultimategallery.domain.watermark.FontKind
import com.qtekfun.ultimategallery.domain.watermark.FontLibrary
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Fonts shipped in `assets/fonts` (SIL OFL) plus fonts the user imported into `filesDir/fonts`.
 * The original uri of an import is never kept; the bytes are copied and validated first.
 */
@Singleton
class AppFontLibrary @Inject constructor(@ApplicationContext private val context: Context, @IoDispatcher private val io: CoroutineDispatcher) : FontLibrary {
    private val importDir get() = File(context.filesDir, DIR)
    private val cache = ConcurrentHashMap<String, Typeface>()
    private val changes = MutableStateFlow(0)

    override val fonts: Flow<List<FontInfo>> = changes.map { withContext(io) { scan() } }

    private fun scan(): List<FontInfo> {
        val bundled = bundledFiles()
            .map { FontInfo(FontIds.bundled(it.substringBeforeLast('.')), bundledLabel(it), FontKind.BUNDLED) }
            .sortedBy { it.label.lowercase() }
        val imported = importedFiles().map { infoOf(it) }.sortedBy { it.label.lowercase() }
        return listOf(DEFAULT_INFO) + bundled + imported
    }

    private fun bundledFiles(): List<String> = runCatching { context.assets.list(DIR) }.getOrNull().orEmpty().filter { hasFontExtension(it) }

    private fun importedFiles(): List<File> = importDir.listFiles().orEmpty().filter { it.isFile && hasFontExtension(it.name) }

    override fun typeface(id: String): Typeface? {
        cache[id]?.let { return it }
        val loaded = when {
            id.startsWith(FontIds.BUNDLED_PREFIX) -> loadBundled(id.removePrefix(FontIds.BUNDLED_PREFIX))
            id.startsWith(FontIds.IMPORTED_PREFIX) -> loadImported(id.removePrefix(FontIds.IMPORTED_PREFIX))
            else -> null
        }
        return loaded?.also { cache[id] = it }
    }

    private fun loadBundled(name: String): Typeface? {
        val file = bundledFiles().firstOrNull { it.substringBeforeLast('.') == name } ?: return null
        return runCatching { Typeface.createFromAsset(context.assets, "$DIR/$file") }.getOrNull()
    }

    private fun loadImported(fileName: String): Typeface? {
        if (File(fileName).name != fileName) return null
        val file = File(importDir, fileName)
        if (!file.isFile) return null
        return runCatching { Typeface.createFromFile(file) }.getOrNull()
    }

    @Suppress("SwallowedException")
    override suspend fun import(uri: Uri): FontImportResult = withContext(io) {
        val dir = importDir.apply { mkdirs() }
        val temp = File.createTempFile("font-import", ".tmp", context.cacheDir)
        try {
            val hash = copyTo(uri, temp)
            val extension = if (hash == null) null else sniff(temp)
            if (hash == null || extension == null || !loadable(temp)) {
                FontImportResult.Invalid
            } else {
                val existing = importedFiles().firstOrNull { sha256(it) == hash }
                if (existing != null) {
                    FontImportResult.AlreadyPresent(infoOf(existing))
                } else {
                    val target = uniqueTarget(dir, baseNameOf(uri), extension)
                    temp.copyTo(target)
                    changes.value++
                    FontImportResult.Imported(infoOf(target))
                }
            }
        } catch (e: IOException) {
            FontImportResult.Invalid
        } finally {
            temp.delete()
        }
    }

    override suspend fun remove(id: String) {
        if (!id.startsWith(FontIds.IMPORTED_PREFIX)) return
        withContext(io) {
            val name = id.removePrefix(FontIds.IMPORTED_PREFIX)
            if (File(name).name == name) {
                File(importDir, name).delete()
                cache.remove(id)
                changes.value++
            }
        }
    }

    private fun infoOf(file: File) = FontInfo(FontIds.imported(file.name), file.nameWithoutExtension, FontKind.IMPORTED)

    /** Copies the uri into [dest] and returns the SHA-256 of the bytes, or null when it cannot be read or is too big. */
    private fun copyTo(uri: Uri, dest: File): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() ?: return null
        var total = 0L
        input.use { source ->
            dest.outputStream().use { out ->
                val buffer = ByteArray(BUFFER)
                while (total <= MAX_BYTES) {
                    val n = source.read(buffer)
                    if (n < 0) break
                    total += n
                    digest.update(buffer, 0, n)
                    out.write(buffer, 0, n)
                }
            }
        }
        return if (total > MAX_BYTES) null else digest.digest().toHex()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().toHex()
    }

    /** The file extension that matches the magic bytes, or null when the file is not a TrueType/OpenType font. */
    private fun sniff(file: File): String? {
        val head = ByteArray(4)
        val read = file.inputStream().use { it.read(head) }
        if (read < 4) return null
        return when {
            head.contentEquals(byteArrayOf(0, 1, 0, 0)) || head.contentEquals("true".toByteArray(Charsets.US_ASCII)) -> "ttf"
            head.contentEquals("OTTO".toByteArray(Charsets.US_ASCII)) -> "otf"
            head.contentEquals("ttcf".toByteArray(Charsets.US_ASCII)) -> "ttc"
            else -> null
        }
    }

    private fun loadable(file: File): Boolean =
        runCatching { Font.Builder(file).build() }.isSuccess || runCatching { Typeface.Builder(file).build() }.getOrNull() != null

    private fun baseNameOf(uri: Uri): String {
        val display = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
        val name = display.substringAfterLast('/')
        val withoutExtension = if (hasFontExtension(name)) name.substringBeforeLast('.') else name
        val cleaned = withoutExtension.replace(UNSAFE, "_").trim().trim('.').take(MAX_NAME)
        return cleaned.ifEmpty { "Font" }
    }

    private fun uniqueTarget(dir: File, base: String, extension: String): File {
        var candidate = File(dir, "$base.$extension")
        var n = 2
        while (candidate.exists()) {
            candidate = File(dir, "$base ($n).$extension")
            n++
        }
        return candidate
    }

    private fun hasFontExtension(name: String) = name.substringAfterLast('.', "").lowercase() in EXTENSIONS

    private fun bundledLabel(fileName: String) = fileName.substringBeforeLast('.').replace(CAMEL_BOUNDARY, " ")

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private companion object {
        const val DIR = "fonts"
        const val BUFFER = 16 * 1024
        const val MAX_BYTES = 64L * 1024 * 1024
        const val MAX_NAME = 80
        val EXTENSIONS = setOf("ttf", "otf", "ttc")
        val UNSAFE = Regex("[^\\p{L}\\p{N} ._()-]")
        val CAMEL_BOUNDARY = Regex("(?<=[a-z])(?=[A-Z])")
        val DEFAULT_INFO = FontInfo(FontIds.DEFAULT, "Default", FontKind.DEFAULT)
    }
}
