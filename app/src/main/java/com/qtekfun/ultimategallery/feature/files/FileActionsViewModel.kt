package com.qtekfun.ultimategallery.feature.files

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimategallery.data.media.FileOperations
import com.qtekfun.ultimategallery.data.media.MediaRepository
import com.qtekfun.ultimategallery.data.media.MetadataStripper
import com.qtekfun.ultimategallery.data.media.OpResult
import com.qtekfun.ultimategallery.domain.Folder
import com.qtekfun.ultimategallery.domain.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DestinationMode { MOVE, COPY }

/** The dialog the file actions currently want on screen. */
sealed interface FileDialog {
    data object None : FileDialog

    data class ChooseDestination(val mode: DestinationMode, val items: List<MediaItem>) : FileDialog

    data class Rename(val item: MediaItem) : FileDialog

    data class InfoMulti(val items: List<MediaItem>) : FileDialog
}

/** One-off results the screen turns into snackbars (or, for [ShareReady], a share sheet). */
sealed interface FileEvent {
    /** [items] are the trashed ones, kept so Undo can restore them. */
    data class Trashed(val count: Int, val items: List<MediaItem>) : FileEvent

    data class Moved(val count: Int) : FileEvent

    data class Copied(val count: Int) : FileEvent

    data object Renamed : FileEvent

    data object CopySaved : FileEvent

    /** A metadata-free temp file is ready; the screen launches the share sheet for [uri]. */
    data class ShareReady(val uri: Uri, val mimeType: String) : FileEvent

    data object Unsupported : FileEvent

    data object Failed : FileEvent

    data object Denied : FileEvent
}

@HiltViewModel
class FileActionsViewModel @Inject constructor(private val operations: FileOperations, media: MediaRepository) : ViewModel() {
    private val _dialog = MutableStateFlow<FileDialog>(FileDialog.None)
    val dialog: StateFlow<FileDialog> = _dialog.asStateFlow()

    private val eventChannel = Channel<FileEvent>(Channel.BUFFERED)
    val events: Flow<FileEvent> = eventChannel.receiveAsFlow()

    /** Existing folders, for the destination picker. */
    val folders: StateFlow<List<Folder>> = media.observeFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun moveTo(items: List<MediaItem>) = open(items) { FileDialog.ChooseDestination(DestinationMode.MOVE, it) }

    fun copyTo(items: List<MediaItem>) = open(items) { FileDialog.ChooseDestination(DestinationMode.COPY, it) }

    fun rename(item: MediaItem) {
        _dialog.value = FileDialog.Rename(item)
    }

    fun showInfo(items: List<MediaItem>) = open(items) { FileDialog.InfoMulti(it) }

    fun dismissDialog() {
        _dialog.value = FileDialog.None
    }

    /** Moves [items] to the system trash; the event carries them for [undoTrash]. */
    fun trash(items: List<MediaItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch { handle(operations.trash(items)) { FileEvent.Trashed(it, items) } }
    }

    fun undoTrash(items: List<MediaItem>) {
        viewModelScope.launch { handle(operations.restore(items)) { null } }
    }

    /** Runs the pending [FileDialog.ChooseDestination] against [destination]. */
    fun confirmDestination(destination: String) {
        val dialog = _dialog.value as? FileDialog.ChooseDestination ?: return
        _dialog.value = FileDialog.None
        viewModelScope.launch {
            when (dialog.mode) {
                DestinationMode.MOVE -> handle(operations.move(dialog.items, destination)) { FileEvent.Moved(it) }
                DestinationMode.COPY -> handle(operations.copy(dialog.items, destination)) { FileEvent.Copied(it) }
            }
        }
    }

    /** Applies the pending [FileDialog.Rename] with [newName] (extension optional). */
    fun confirmRename(newName: String) {
        val dialog = _dialog.value as? FileDialog.Rename ?: return
        _dialog.value = FileDialog.None
        viewModelScope.launch { handle(operations.rename(dialog.item, newName)) { FileEvent.Renamed } }
    }

    fun shareWithoutMetadata(item: MediaItem) {
        viewModelScope.launch {
            if (!MetadataStripper.isSupported(item.mimeType)) {
                eventChannel.send(FileEvent.Unsupported)
                return@launch
            }
            val uri = operations.strippedCopyForSharing(item)
            eventChannel.send(if (uri != null) FileEvent.ShareReady(uri, item.mimeType) else FileEvent.Failed)
        }
    }

    fun saveCopyWithoutMetadata(item: MediaItem) {
        viewModelScope.launch {
            if (!MetadataStripper.isSupported(item.mimeType)) {
                eventChannel.send(FileEvent.Unsupported)
                return@launch
            }
            eventChannel.send(if (operations.saveCopyWithoutMetadata(item) != null) FileEvent.CopySaved else FileEvent.Failed)
        }
    }

    private inline fun open(items: List<MediaItem>, dialog: (List<MediaItem>) -> FileDialog) {
        if (items.isNotEmpty()) _dialog.value = dialog(items)
    }

    private suspend fun handle(result: OpResult, success: (Int) -> FileEvent?) {
        val event = when (result) {
            is OpResult.Done -> if (result.succeeded > 0 || result.failed == 0) success(result.succeeded) else FileEvent.Failed
            OpResult.Denied -> FileEvent.Denied
            is OpResult.Failed -> FileEvent.Failed
        }
        if (event != null) eventChannel.send(event)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
