package com.qtekfun.ultimategallery.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimategallery.data.folders.DetectedAppFolders
import com.qtekfun.ultimategallery.data.folders.FolderRepository
import com.qtekfun.ultimategallery.data.folders.FolderState
import com.qtekfun.ultimategallery.data.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An app folder group with whether it is hidden automatically. */
data class AppFolderRow(val detected: DetectedAppFolders, val autoHidden: Boolean)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val folders: FolderRepository, private val settings: SettingsRepository) : ViewModel() {
    /** Every folder with its hidden state; null while loading. */
    val folderStates: StateFlow<List<FolderState>?> = folders.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val appFolders: StateFlow<List<AppFolderRow>?> = combine(
        folders.detectedAppFolders(),
        settings.settings.map { it.autoHideApps }
    ) { detected, hidden -> detected.map { AppFolderRow(it, it.entry.id in hidden) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun setFolderVisible(state: FolderState, visible: Boolean) {
        viewModelScope.launch {
            if (visible) folders.unhide(listOf(state.folder.bucketId)) else folders.hide(listOf(state.folder))
        }
    }

    fun setAppFolderHidden(id: String, hidden: Boolean) {
        viewModelScope.launch { settings.setAutoHideApp(id, hidden) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
