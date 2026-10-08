package com.qtekfun.ultimategallery.feature.viewer

import android.net.Uri
import com.qtekfun.ultimategallery.data.prefs.SaveBehavior
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.video.VideoRotateResult
import com.qtekfun.ultimategallery.domain.video.VideoRotation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val QUARTER_TURNS = 4

/**
 * State of the video rotate preview.
 *
 * @property item the video being previewed, or null when no preview is active.
 * @property turns clockwise quarter turns previewed so far; nothing is written until Save.
 * @property revision bumped after each overwrite so the player reloads the rewritten file.
 */
data class VideoRotateState(val item: MediaItem? = null, val turns: Int = 0, val asking: Boolean = false, val saving: Boolean = false, val revision: Int = 0) {
    val previewing: Boolean get() = item != null
    val canSave: Boolean get() = previewing && !saving && Math.floorMod(turns, QUARTER_TURNS) != 0
}

/** One-shot outcomes shown as snackbars. */
sealed interface VideoRotateEvent {
    data class Rotated(val uri: Uri, val overwritten: Boolean) : VideoRotateEvent

    data object Denied : VideoRotateEvent

    data object Unsupported : VideoRotateEvent

    data class Failed(val message: String) : VideoRotateEvent
}

/** Drives the rotate preview and the save through [VideoRotation]. Pure logic, so it is tested with fakes. */
class VideoRotateController(
    private val rotation: VideoRotation,
    private val saveBehavior: suspend () -> SaveBehavior,
    private val rememberBehavior: suspend (SaveBehavior) -> Unit,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(VideoRotateState())
    private val channel = Channel<VideoRotateEvent>(Channel.BUFFERED)

    val state: StateFlow<VideoRotateState> = mutableState.asStateFlow()
    val events: Flow<VideoRotateEvent> = channel.receiveAsFlow()

    /** Starts a preview turned one quarter clockwise, or reports that the container cannot be rotated. */
    fun start(item: MediaItem) {
        if (mutableState.value.previewing) return
        scope.launch {
            val stored = runCatching { rotation.currentRotation(item) }.getOrNull()
            if (stored == null) {
                channel.trySend(VideoRotateEvent.Unsupported)
            } else {
                mutableState.update { if (it.previewing) it else it.copy(item = item, turns = 1) }
            }
        }
    }

    fun turn(delta: Int) = mutableState.update { if (it.previewing && !it.saving) it.copy(turns = it.turns + delta) else it }

    /** Drops the preview. Ignored while a save is running. */
    fun cancel() = mutableState.update { if (it.saving) it else it.copy(item = null, turns = 0, asking = false) }

    /** Saves the previewed rotation, asking first when the save-behavior setting says so. */
    fun save() {
        val current = mutableState.value
        if (!current.canSave || current.asking) return
        scope.launch {
            when (val behavior = saveBehavior()) {
                SaveBehavior.ASK -> mutableState.update { it.copy(asking = true) }
                else -> perform(behavior)
            }
        }
    }

    fun confirmAsk(behavior: SaveBehavior, remember: Boolean) {
        mutableState.update { it.copy(asking = false) }
        scope.launch {
            if (remember) rememberBehavior(behavior)
            perform(behavior)
        }
    }

    fun dismissAsk() = mutableState.update { it.copy(asking = false) }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun perform(behavior: SaveBehavior) {
        val current = mutableState.value
        val item = current.item ?: return
        if (current.saving) return
        mutableState.update { it.copy(saving = true) }
        val result = try {
            rotation.rotate(item, current.turns, behavior)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            VideoRotateResult.Failed(e.message ?: e.javaClass.simpleName)
        }
        when (result) {
            is VideoRotateResult.Rotated -> {
                mutableState.update { VideoRotateState(revision = if (result.overwritten) it.revision + 1 else it.revision) }
                channel.trySend(VideoRotateEvent.Rotated(result.uri, result.overwritten))
            }
            VideoRotateResult.Denied -> fail(VideoRotateEvent.Denied)
            VideoRotateResult.Unsupported -> fail(VideoRotateEvent.Unsupported)
            is VideoRotateResult.Failed -> fail(VideoRotateEvent.Failed(result.message))
        }
    }

    private fun fail(event: VideoRotateEvent) {
        mutableState.update { it.copy(saving = false) }
        channel.trySend(event)
    }
}
