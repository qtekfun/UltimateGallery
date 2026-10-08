package com.qtekfun.ultimategallery.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimategallery.data.folders.DetectedAppFolders
import com.qtekfun.ultimategallery.data.folders.FolderRepository
import com.qtekfun.ultimategallery.data.folders.FolderState
import com.qtekfun.ultimategallery.data.prefs.AppSettings
import com.qtekfun.ultimategallery.data.prefs.SaveBehavior
import com.qtekfun.ultimategallery.data.prefs.SettingsRepository
import com.qtekfun.ultimategallery.data.prefs.ThemeMode
import com.qtekfun.ultimategallery.domain.watermark.ExportSettings
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
class SettingsViewModel @Inject constructor(private val folders: FolderRepository, private val settingsRepo: SettingsRepository) : ViewModel() {
    /** Every folder with its hidden state; null while loading. */
    val folderStates: StateFlow<List<FolderState>?> = folders.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val appFolders: StateFlow<List<AppFolderRow>?> = combine(
        folders.detectedAppFolders(),
        settingsRepo.settings.map { it.autoHideApps }
    ) { detected, hidden -> detected.map { AppFolderRow(it, it.entry.id in hidden) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings())

    fun setThemeMode(mode: ThemeMode) = launch { settingsRepo.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = launch { settingsRepo.setDynamicColor(enabled) }

    fun setHapticsEnabled(enabled: Boolean) = launch { settingsRepo.setHapticsEnabled(enabled) }

    fun setSaveBehavior(behavior: SaveBehavior) = launch { settingsRepo.setSaveBehavior(behavior) }

    fun setFolderColumns(columns: Int) = launch { settingsRepo.setFolderGridColumns(columns) }

    fun setPhotoColumns(columns: Int) = launch { settingsRepo.setPhotoGridColumns(columns) }

    fun setExportDefaults(e: ExportSettings) = launch { settingsRepo.setExportDefaults(e) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun setFolderVisible(state: FolderState, visible: Boolean) {
        viewModelScope.launch {
            if (visible) folders.unhide(listOf(state.folder.bucketId)) else folders.hide(listOf(state.folder))
        }
    }

    fun setAppFolderHidden(id: String, hidden: Boolean) {
        viewModelScope.launch { settingsRepo.setAutoHideApp(id, hidden) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
