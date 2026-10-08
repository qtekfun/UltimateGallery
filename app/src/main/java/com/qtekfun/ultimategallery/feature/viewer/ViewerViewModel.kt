package com.qtekfun.ultimategallery.feature.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimategallery.data.media.MediaRepository
import com.qtekfun.ultimategallery.data.prefs.SettingsRepository
import com.qtekfun.ultimategallery.di.IoDispatcher
import com.qtekfun.ultimategallery.domain.BatchSelection
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.video.VideoRotation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    media: MediaRepository,
    private val detailsReader: MediaDetailsReader,
    private val batch: BatchSelection,
    rotation: VideoRotation,
    settings: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {
    val bucketId: Long = checkNotNull(savedState["bucketId"])
    val initialMediaId: Long = checkNotNull(savedState["mediaId"])

    /** The folder's items, or null while loading. */
    val items: StateFlow<List<MediaItem>?> = media.observeItems(bucketId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** The lossless video rotate preview and save. */
    val rotate = VideoRotateController(
        rotation = rotation,
        saveBehavior = { settings.settings.first().saveBehavior },
        rememberBehavior = settings::setSaveBehavior,
        scope = viewModelScope
    )

    suspend fun details(item: MediaItem): MediaDetails = withContext(io) { detailsReader.read(item) }

    /** Hands a single photo to the watermark editor. */
    fun startWatermark(item: MediaItem) = batch.set(listOf(item.id))

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
