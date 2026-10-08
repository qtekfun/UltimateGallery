package com.qtekfun.ultimategallery.feature.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimategallery.data.folders.DetectedAppFolders
import com.qtekfun.ultimategallery.data.folders.FolderRepository
import com.qtekfun.ultimategallery.data.prefs.HomeViewMode
import com.qtekfun.ultimategallery.data.prefs.SettingsRepository
import com.qtekfun.ultimategallery.domain.BatchSelection
import com.qtekfun.ultimategallery.domain.Folder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeState {
    data object Loading : HomeState

    data class Loaded(val folders: List<Folder>, val mode: HomeViewMode, val columns: Int) : HomeState
}

@HiltViewModel
class HomeViewModel @Inject constructor(private val folders: FolderRepository, private val settings: SettingsRepository, private val batch: BatchSelection) :
    ViewModel() {
    private val _showHidden = MutableStateFlow(false)

    /** Whether hidden folders are listed too (only offered in the watermark picker). */
    val showHidden: StateFlow<Boolean> = _showHidden.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeState> = combine(
        _showHidden.flatMapLatest { folders.visible(includeHidden = it) },
        settings.settings
    ) { list, s ->
        HomeState.Loaded(list, s.homeViewMode, s.folderGridColumns) as HomeState
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeState.Loading)

    /** App folders to offer hiding on first run, or null when there is nothing to offer. */
    val appFolderOffer: StateFlow<List<DetectedAppFolders>?> = combine(
        settings.settings.map { it.appFoldersOffered },
        folders.detectedAppFolders()
    ) { offered, detected -> detected.takeIf { !offered && it.isNotEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun toggleShowHidden() {
        _showHidden.value = !_showHidden.value
    }

    fun hide(folder: Folder) {
        viewModelScope.launch { folders.hide(listOf(folder)) }
    }

    fun unhide(bucketId: Long) {
        viewModelScope.launch { folders.unhide(listOf(bucketId)) }
    }

    /** Hides the apps' folders (and future ones of the same apps); nothing is hidden without this call. */
    fun applyAppFolders(appIds: Set<String>) {
        viewModelScope.launch {
            if (appIds.isNotEmpty()) settings.setAutoHideApps(settings.settings.first().autoHideApps + appIds)
            settings.setAppFoldersOffered(true)
        }
    }

    fun dismissAppFolderOffer() {
        viewModelScope.launch { settings.setAppFoldersOffered(true) }
    }

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
