package com.qtekfun.ultimategallery.data.media

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import com.qtekfun.ultimategallery.core.consent.ConsentBroker
import com.qtekfun.ultimategallery.di.IoDispatcher
import com.qtekfun.ultimategallery.domain.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Outcome of a file operation. */
sealed interface OpResult {
    /** The operation ran; [failed] counts items that could not be processed. */
    data class Done(val succeeded: Int, val failed: Int) : OpResult

    /** The user refused the system consent dialog. */
    data object Denied : OpResult

    data class Failed(val message: String) : OpResult
}

/** Shows a system consent dialog and reports whether the user allowed it. */
fun interface ConsentRequester {
    suspend fun request(intentSender: IntentSender): Boolean
}

/** Adapts the app-wide [ConsentBroker] to [ConsentRequester]. */
class BrokerConsentRequester(private val broker: ConsentBroker) : ConsentRequester {
    override suspend fun request(intentSender: IntentSender): Boolean = broker.request(intentSender)
}

/**
 * Moves, copies, renames and trashes media through the MediaStore. Changes to existing files go through
 * a system consent dialog; copies create new files and need none. Trash is the system trash: recoverable,
 * and there is no permanent delete here.
 */
@Singleton
class FileOperations(private val store: MediaFileStore, private val consent: ConsentRequester, @IoDispatcher private val io: CoroutineDispatcher) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        consentBroker: ConsentBroker,
        @IoDispatcher io: CoroutineDispatcher
    ) : this(ResolverFileStore(context), BrokerConsentRequester(consentBroker), io)

    /** Moves [items] to the system trash. */
    suspend fun trash(items: List<MediaItem>): OpResult = setTrashed(items, true)

    /** Takes [items] back out of the trash (Undo). */
    suspend fun restore(items: List<MediaItem>): OpResult = setTrashed(items, false)

    private suspend fun setTrashed(items: List<MediaItem>, trashed: Boolean): OpResult {
        if (items.isEmpty()) return OpResult.Done(0, 0)
        return guarded {
            val sender = withContext(io) { store.trashRequest(items.map { it.uri }, trashed) }
            if (consent.request(sender)) OpResult.Done(items.size, 0) else OpResult.Denied
        }
    }

    /** Renames [item]; [newName] may omit the extension, which is then kept. */
    suspend fun rename(item: MediaItem, newName: String): OpResult {
        val name = FileNames.renamed(item.displayName, newName) ?: return OpResult.Failed("Invalid file name")
        if (name == item.displayName) return OpResult.Done(1, 0)
        return guarded {
            val sender = withContext(io) { store.writeRequest(listOf(item.uri)) }
            if (!consent.request(sender)) return@guarded OpResult.Denied
            val ok = withContext(io) { store.setDisplayName(item.uri, name) }
            if (ok) OpResult.Done(1, 0) else OpResult.Done(0, 1)
        }
    }

    /** Moves [items] into [destination], a relative path such as `Pictures/Trips`. */
    suspend fun move(items: List<MediaItem>, destination: String): OpResult {
        if (items.isEmpty()) return OpResult.Done(0, 0)
        if (!Destinations.isValidFor(destination, items)) return OpResult.Failed("Invalid destination")
        val path = Destinations.toRelativePath(destination)
        return guarded {
            val sender = withContext(io) { store.writeRequest(items.map { it.uri }) }
            if (!consent.request(sender)) return@guarded OpResult.Denied
            withContext(io) { count(items) { store.setRelativePath(it.uri, path) } }
        }
    }

    /** Copies [items] into [destination] as new files. */
    suspend fun copy(items: List<MediaItem>, destination: String): OpResult {
        if (items.isEmpty()) return OpResult.Done(0, 0)
        if (!Destinations.isValidFor(destination, items)) return OpResult.Failed("Invalid destination")
        val path = Destinations.toRelativePath(destination)
        return guarded {
            withContext(io) { count(items) { copyOne(it, path) } }
        }
    }

    /**
     * Saves a copy of [item] beside the original, named `<name>_nometa`, with all EXIF, GPS and XMP data
     * removed. Returns null when the type is not JPEG, PNG or WebP or when anything goes wrong.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun saveCopyWithoutMetadata(item: MediaItem): Uri? = withContext(io) {
        if (item.isVideo || !MetadataStripper.isSupported(item.mimeType)) return@withContext null
        var temp: File? = null
        try {
            temp = strippedTemp(item) ?: return@withContext null
            val folder = item.relativePath?.takeIf { Destinations.isValid(it, false) } ?: (Destinations.NEW_FOLDER_ROOT + "/")
            val name = FileNames.withSuffix(item.displayName, "_nometa")
            publish(temp, name, item, Destinations.toRelativePath(folder))
        } catch (e: Exception) {
            null
        } finally {
            temp?.delete()
        }
    }

    /** A metadata-free temporary copy of [item] as a shareable content uri, or null when unsupported or on failure. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun strippedCopyForSharing(item: MediaItem): Uri? = withContext(io) {
        if (item.isVideo || !MetadataStripper.isSupported(item.mimeType)) return@withContext null
        try {
            val file = strippedTemp(item) ?: return@withContext null
            store.shareUri(file)
        } catch (e: Exception) {
            null
        }
    }

    private fun strippedTemp(item: MediaItem): File? {
        val file = store.tempFile(FileNames.extension(item.displayName).ifEmpty { "img" })
        try {
            val input = store.openInput(item.uri) ?: return null
            input.use { source -> file.outputStream().use { source.copyTo(it) } }
            MetadataStripper.strip(file)
            return file
        } catch (e: IOException) {
            file.delete()
            throw e
        }
    }

    private fun copyOne(item: MediaItem, path: String): Boolean {
        val input = store.openInput(item.uri) ?: return false
        return input.use { publishStream(it, item.displayName, item, path) != null }
    }

    private fun publish(file: File, name: String, item: MediaItem, path: String): Uri? = file.inputStream().use { publishStream(it, name, item, path) }

    /** Inserts a hidden row, streams the bytes in and reveals it; removes the row when anything fails. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun publishStream(input: java.io.InputStream, name: String, item: MediaItem, path: String): Uri? {
        val uri = store.insertPending(item.isVideo, name, item.mimeType, item.dateMs, path) ?: return null
        return try {
            val out = store.openOutput(uri) ?: throw IOException("Could not open the output file")
            out.use { input.copyTo(it) }
            store.publish(uri)
            uri
        } catch (e: Exception) {
            runCatching { store.delete(uri) }
            null
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private inline fun count(items: List<MediaItem>, action: (MediaItem) -> Boolean): OpResult.Done {
        var ok = 0
        var bad = 0
        for (item in items) {
            val success = try {
                action(item)
            } catch (e: Exception) {
                false
            }
            if (success) ok++ else bad++
        }
        return OpResult.Done(ok, bad)
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private inline fun guarded(block: () -> OpResult): OpResult = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        OpResult.Failed(e.message ?: e.javaClass.simpleName)
    }
}
