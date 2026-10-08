package com.qtekfun.ultimategallery.data.video

import com.qtekfun.ultimategallery.core.consent.ConsentBroker
import com.qtekfun.ultimategallery.data.prefs.SaveBehavior
import com.qtekfun.ultimategallery.di.IoDispatcher
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.video.VideoRotateResult
import com.qtekfun.ultimategallery.domain.video.VideoRotation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [VideoRotation] that rewrites the `tkhd` matrix of the MP4 video track, in place or in a copy. */
@Singleton
class MediaVideoRotation @Inject constructor(
    private val store: VideoStore,
    private val consent: ConsentBroker,
    @IoDispatcher private val io: CoroutineDispatcher
) : VideoRotation {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun currentRotation(item: MediaItem): Int? = withContext(io) {
        try {
            store.withRead(item.uri) { Mp4Rotation.currentRotation(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun rotate(item: MediaItem, quarterTurnsClockwise: Int, behavior: SaveBehavior): VideoRotateResult = try {
        // Nothing is asked or created for a file that cannot be rotated.
        val supported = withContext(io) { store.withRead(item.uri) { Mp4Rotation.currentRotation(it) } } != null
        when {
            !supported -> VideoRotateResult.Unsupported
            behavior == SaveBehavior.OVERWRITE -> overwrite(item, quarterTurnsClockwise)
            else -> copy(item, quarterTurnsClockwise)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        VideoRotateResult.Failed(e.message ?: e.javaClass.simpleName)
    }

    private suspend fun overwrite(item: MediaItem, turns: Int): VideoRotateResult {
        if (!consent.request(store.createWriteRequest(item.uri))) return VideoRotateResult.Denied
        return withContext(io) {
            val rotated = store.withReadWrite(item.uri) { Mp4Rotation.rotate(it, turns) }
            if (rotated == null) {
                VideoRotateResult.Unsupported
            } else {
                store.refresh(item.uri)
                VideoRotateResult.Rotated(item.uri, overwritten = true)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun copy(item: MediaItem, turns: Int): VideoRotateResult = withContext(io) {
        val target = store.createPending(copyName(item.displayName), item.mimeType, item.relativePath, item.dateMs)
        try {
            store.copyBytes(item.uri, target)
            val rotated = store.withReadWrite(target) { Mp4Rotation.rotate(it, turns) }
            if (rotated == null) {
                store.delete(target)
                VideoRotateResult.Unsupported
            } else {
                store.publish(target)
                VideoRotateResult.Rotated(target, overwritten = false)
            }
        } catch (e: Throwable) {
            store.delete(target)
            throw e
        }
    }

    companion object {
        /** `clip.mp4` becomes `clip_rotated.mp4`. */
        fun copyName(displayName: String): String {
            val dot = displayName.lastIndexOf('.')
            return if (dot <= 0) "${displayName}_rotated" else "${displayName.substring(0, dot)}_rotated${displayName.substring(dot)}"
        }
    }
}
