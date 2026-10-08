package com.qtekfun.ultimategallery.feature.gallery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimategallery.data.media.MediaRepository
import com.qtekfun.ultimategallery.data.prefs.SettingsRepository
import com.qtekfun.ultimategallery.domain.BatchSelection
import com.qtekfun.ultimategallery.domain.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FolderUiState(
    val loading: Boolean = true,
    val items: List<MediaItem> = emptyList(),
    val entries: List<GridEntry> = emptyList(),
    val selection: Set<Long> = emptySet(),
    val columns: Int = 4
)

@HiltViewModel
class FolderViewModel @Inject constructor(
    savedState: SavedStateHandle,
    media: MediaRepository,
    private val settings: SettingsRepository,
    private val batch: BatchSelection
) : ViewModel() {
    val bucketId: Long = checkNotNull(savedState["bucketId"])
    val name: String = savedState["name"] ?: ""

    /** Picking mode feeds the watermark flow: images only, and the selection is shared across folders. */
    val pickMode: Boolean = savedState["pick"] ?: false

    private val selection = MutableStateFlow<Set<Long>>(if (pickMode) LinkedHashSet(batch.ids.value) else emptySet())

    val state: StateFlow<FolderUiState> = combine(
        media.observeItems(bucketId).map { all -> if (pickMode) all.filterNot { it.isVideo } else all },
        selection,
        settings.settings
    ) { items, selected, s ->
        val present = items.mapTo(HashSet()) { it.id }
        FolderUiState(
            loading = false,
            items = items,
            entries = GridEntries.build(items, ZoneId.systemDefault(), LocalDate.now()),
            selection = if (pickMode) selected else selected.filterTo(LinkedHashSet()) { it in present },
            columns = s.photoGridColumns
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FolderUiState())

    fun toggle(id: Long) {
        val current = selection.value
        setSelection(if (id in current) current - id else LinkedHashSet(current).apply { add(id) })
    }

    fun setSelection(ids: Set<Long>) {
        selection.value = ids
        if (pickMode) batch.set(ids.toList())
    }

    fun selectAll() = setSelection(LinkedHashSet(state.value.items.map { it.id }))

    fun clearSelection() = setSelection(emptySet())

    fun setColumns(columns: Int) {
        viewModelScope.launch { settings.setPhotoGridColumns(columns) }
    }

    /** Hands the selected photos (not videos) to the watermark flow; returns how many were handed over. */
    fun commitSelectionToBatch(): Int {
        val images = state.value.items.filter { it.id in selection.value && !it.isVideo }.map { it.id }
        batch.set(images)
        return images.size
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
