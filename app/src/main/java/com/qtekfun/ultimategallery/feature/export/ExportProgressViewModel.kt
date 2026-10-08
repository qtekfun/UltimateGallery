package com.qtekfun.ultimategallery.feature.export

import android.content.ContentUris
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimategallery.data.media.MediaRepository
import com.qtekfun.ultimategallery.domain.export.ExportController
import com.qtekfun.ultimategallery.domain.export.ExportResult
import com.qtekfun.ultimategallery.domain.export.ExportStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The gallery folder an export wrote into, so "Open folder" can show it. */
data class ExportFolder(val bucketId: Long, val name: String)

@HiltViewModel
class ExportProgressViewModel @Inject constructor(savedState: SavedStateHandle, private val controller: ExportController, private val media: MediaRepository) :
    ViewModel() {
    private val jobId: String = checkNotNull(savedState["jobId"])

    val status: StateFlow<ExportStatus> = controller.observe(jobId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExportStatus.Queued)

    private val _cancelling = MutableStateFlow(false)
    val cancelling: StateFlow<Boolean> = _cancelling.asStateFlow()

    private val _folder = MutableStateFlow<ExportFolder?>(null)
    val folder: StateFlow<ExportFolder?> = _folder.asStateFlow()

    fun cancel() {
        _cancelling.value = true
        controller.cancel(jobId)
    }

    /** Resolves the destination folder of a finished result from its first written file. */
    fun resolveFolder(result: ExportResult) {
        if (_folder.value != null) return
        val first = result.succeeded.firstOrNull()?.outputUri ?: return
        viewModelScope.launch {
            val id = runCatching { ContentUris.parseId(first) }.getOrNull() ?: return@launch
            media.loadItems(listOf(id)).firstOrNull()?.let { item ->
                val name = item.relativePath?.trimEnd('/')?.substringAfterLast('/').orEmpty()
                _folder.value = ExportFolder(item.bucketId, name)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
