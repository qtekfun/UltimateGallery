package com.qtekfun.ultimategallery.feature.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimategallery.data.media.MediaRepository
import com.qtekfun.ultimategallery.data.prefs.HomeViewMode
import com.qtekfun.ultimategallery.data.prefs.SettingsRepository
import com.qtekfun.ultimategallery.domain.BatchSelection
import com.qtekfun.ultimategallery.domain.Folder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeState {
    data object Loading : HomeState

    data class Loaded(val folders: List<Folder>, val mode: HomeViewMode, val columns: Int) : HomeState
}

@HiltViewModel
class HomeViewModel @Inject constructor(media: MediaRepository, private val settings: SettingsRepository, private val batch: BatchSelection) : ViewModel() {
    val state: StateFlow<HomeState> = combine(media.observeFolders(), settings.settings) { folders, s ->
        HomeState.Loaded(folders, s.homeViewMode, s.folderGridColumns) as HomeState
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeState.Loading)

    fun toggleMode(current: HomeViewMode) {
        viewModelScope.launch {
            settings.setHomeViewMode(if (current == HomeViewMode.GRID) HomeViewMode.LIST else HomeViewMode.GRID)
        }
    }

    fun setColumns(columns: Int) {
        viewModelScope.launch { settings.setFolderGridColumns(columns) }
    }

    /** Starts a fresh watermark selection. */
    fun startPicking() = batch.clear()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
