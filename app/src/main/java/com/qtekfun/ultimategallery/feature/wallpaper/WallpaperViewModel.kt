package com.qtekfun.ultimategallery.feature.wallpaper

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimategallery.data.media.MediaRepository
import com.qtekfun.ultimategallery.data.wallpaper.CropFraction
import com.qtekfun.ultimategallery.data.wallpaper.WallpaperSetter
import com.qtekfun.ultimategallery.data.wallpaper.WallpaperTarget
import com.qtekfun.ultimategallery.domain.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WallpaperState(
    val item: MediaItem? = null,
    val missing: Boolean = false,
    val target: WallpaperTarget = WallpaperTarget.BOTH,
    val applying: Boolean = false
)

@HiltViewModel
class WallpaperViewModel @Inject constructor(savedState: SavedStateHandle, media: MediaRepository, private val setter: WallpaperSetter) : ViewModel() {
    private val mediaId: Long = checkNotNull(savedState["mediaId"])

    private val _state = MutableStateFlow(WallpaperState())
    val state: StateFlow<WallpaperState> = _state.asStateFlow()

    private val _results = Channel<Boolean>(Channel.BUFFERED)

    /** True when the wallpaper was set, false when it failed. */
    val results: Flow<Boolean> = _results.receiveAsFlow()

    init {
        viewModelScope.launch {
            val item = media.loadItems(listOf(mediaId)).firstOrNull()
            _state.update { it.copy(item = item, missing = item == null) }
        }
    }

    fun setTarget(target: WallpaperTarget) = _state.update { it.copy(target = target) }

    fun apply(crop: CropFraction, screenWidth: Int, screenHeight: Int) {
        val s = _state.value
        val item = s.item ?: return
        if (s.applying) return
        _state.update { it.copy(applying = true) }
        viewModelScope.launch {
            val ok = setter.set(item.uri, crop, screenWidth, screenHeight, s.target)
            _state.update { it.copy(applying = false) }
            _results.send(ok)
        }
    }
}
