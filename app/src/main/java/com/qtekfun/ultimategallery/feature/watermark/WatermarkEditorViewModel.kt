package com.qtekfun.ultimategallery.feature.watermark

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimategallery.data.media.MediaRepository
import com.qtekfun.ultimategallery.data.profile.ProfileRepository
import com.qtekfun.ultimategallery.domain.BatchSelection
import com.qtekfun.ultimategallery.domain.export.ExportController
import com.qtekfun.ultimategallery.domain.export.ExportPaths
import com.qtekfun.ultimategallery.domain.watermark.ExportSettings
import com.qtekfun.ultimategallery.domain.watermark.Orientation
import com.qtekfun.ultimategallery.domain.watermark.Placement
import com.qtekfun.ultimategallery.domain.watermark.PlacementMath
import com.qtekfun.ultimategallery.domain.watermark.Snapper
import com.qtekfun.ultimategallery.domain.watermark.TextStyleSpec
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import com.qtekfun.ultimategallery.render.WatermarkRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class WatermarkEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val media: MediaRepository,
    private val batch: BatchSelection,
    private val importer: LogoImporter,
    private val exporter: ExportController,
    private val profileRepository: ProfileRepository,
    val renderer: WatermarkRenderer
) : ViewModel() {
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val history = EditorHistory()
    private var gestureBefore: WatermarkProfile? = null
    private var rawPlacement: Placement? = null
    private var lastSnapSignature: Triple<Float?, Float?, Boolean> = Triple(null, null, false)
    private var lastText: WatermarkSource.Text = WatermarkSource.Text("@wallapop")
    private var lastImage: WatermarkSource.Image? = null

    private val snapChannel = Channel<Unit>(Channel.CONFLATED)

    /** Emits once each time a drag newly locks onto a guide or a rotation detent. */
    val snapEvents = snapChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            profileRepository.profiles.collect { list -> _state.update { it.copy(profiles = list) } }
        }
        viewModelScope.launch {
            val initial = profileRepository.lastUsed()
            _state.update { it.copy(profile = initial, savedProfile = initial) }
        }
        viewModelScope.launch {
            val ids = savedState.get<LongArray>(KEY_IDS)?.toList() ?: batch.ids.value.also {
                savedState[KEY_IDS] = it.toLongArray()
            }
            val items = media.loadItems(ids).filterNot { it.isVideo }
            _state.update {
                it.copy(
                    loading = false,
                    items = items,
                    index = savedState.get<Int>(KEY_INDEX)?.coerceIn(0, (items.size - 1).coerceAtLeast(0)) ?: 0,
                    message = if (items.isEmpty()) EditorMessage.EMPTY_SELECTION else null
                )
            }
        }
    }

    // region navigation between photos

    fun select(index: Int) {
        savedState[KEY_INDEX] = index
        _state.update {
            it.copy(index = index.coerceIn(0, (it.items.size - 1).coerceAtLeast(0)), guideX = null, guideY = null)
        }
    }

    fun setTab(tab: EditorTab) = _state.update { it.copy(tab = tab) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // endregion

    // region gestures

    fun gestureStart() {
        val s = _state.value
        gestureBefore = s.profile
        rawPlacement = s.placement
    }

    /** [panX] and [panY] are fractions of the displayed image size. */
    fun gesture(panX: Float, panY: Float, zoom: Float, rotationDeg: Float) {
        val raw = rawPlacement ?: return
        updateRaw(PlacementMath.applyGesture(raw, panX, panY, zoom, rotationDeg))
    }

    /** The scale/rotate handle was dragged to a new size and rotation. */
    fun handleDrag(sizeFraction: Float, rotationDeg: Float) {
        val raw = rawPlacement ?: return
        updateRaw(PlacementMath.clamp(raw.copy(sizeFraction = sizeFraction, rotationDeg = rotationDeg)))
    }

    private fun updateRaw(raw: Placement) {
        rawPlacement = raw
        val s = _state.value
        val item = s.current ?: return
        val imageAspect = item.width.toFloat() / item.height
        val snap = Snapper.snap(
            raw,
            imageAspect,
            renderer.markAspect(s.profile.source),
            s.profile.margin,
            s.snapEnabled
        )
        val signature = Triple(snap.guideX, snap.guideY, snap.rotationSnapped)
        if (snap.isSnapping && signature != lastSnapSignature) snapChannel.trySend(Unit)
        lastSnapSignature = signature
        _state.update {
            it.copy(
                profile = it.profile.withPlacement(it.orientation, snap.placement),
                guideX = snap.guideX,
                guideY = snap.guideY
            )
        }
    }

    fun gestureEnd() {
        gestureBefore?.let { before ->
            if (before != _state.value.profile) {
                history.record(before, key = null, nowMs = now())
                publishHistory()
            }
        }
        gestureBefore = null
        rawPlacement = null
        lastSnapSignature = Triple(null, null, false)
        history.breakCoalescing()
        _state.update { it.copy(guideX = null, guideY = null) }
    }

    // endregion

    // region edits

    /** Applies [change] to the profile as one undo step (coalesced per [key] for typing and sliders). */
    private fun edit(key: String? = null, change: (WatermarkProfile) -> WatermarkProfile) {
        val before = _state.value.profile
        val after = change(before)
        if (after == before) return
        history.record(before, key, now())
        _state.update { it.copy(profile = after) }
        publishHistory()
    }

    fun setOpacity(value: Float) = edit("opacity") { it.copy(opacity = value.coerceIn(0f, 1f)) }

    fun setMargin(value: Float) = edit("margin") { it.copy(margin = value.coerceIn(0f, MAX_MARGIN)) }

    /** Export settings are not part of the undo history. */
    fun setExportSettings(settings: ExportSettings) = _state.update { it.copy(profile = it.profile.copy(export = settings)) }

    /**
     * Starts exporting the whole batch and returns the job id, or null when there is nothing to
     * export or the destination is invalid (the Export tab is shown so the user can fix it).
     */
    fun startExport(): String? {
        val s = _state.value
        if (s.items.isEmpty()) return null
        if (!ExportPaths.isValidDestination(s.profile.export.destination)) {
            _state.update { it.copy(tab = EditorTab.EXPORT) }
            return null
        }
        if (s.profile.id != 0L) viewModelScope.launch { profileRepository.markUsed(s.profile.id) }
        return exporter.start(s.items.map { it.id }, s.profile)
    }

    fun setSnapEnabled(enabled: Boolean) = _state.update { it.copy(snapEnabled = enabled) }

    fun setSource(source: WatermarkSource, key: String? = null) = edit(key) {
        it.copy(source = source)
    }.also { remember(source) }

    fun setText(text: String) = updateText(key = "text") { it.copy(text = text) }

    fun setTextStyle(style: TextStyleSpec) = updateText(key = "style") { it.copy(style = style) }

    private fun updateText(key: String, change: (WatermarkSource.Text) -> WatermarkSource.Text) {
        val source = _state.value.profile.source
        val leaf = leafOf(source) as? WatermarkSource.Text ?: lastText
        setSource(rebuild(source, change(leaf)), key)
    }

    fun setType(type: MarkType) {
        val source = _state.value.profile.source
        val leaf = leafOf(source)
        when (type) {
            MarkType.TEXT -> setSource(lastText)
            MarkType.IMAGE -> lastImage?.let { setSource(it) }
            MarkType.TILED -> if (source !is WatermarkSource.Tiled) setSource(WatermarkSource.Tiled(leaf))
        }
    }

    /** Sets what a tiled mark repeats, or the plain mark when not tiled. */
    fun setBase(base: WatermarkSource) {
        val source = _state.value.profile.source
        setSource(if (source is WatermarkSource.Tiled) source.copy(base = base) else base)
    }

    fun setTiling(spacing: Float? = null, staggerX: Float? = null, staggerY: Float? = null) {
        val source = _state.value.profile.source as? WatermarkSource.Tiled ?: return
        setSource(
            source.copy(
                spacing = spacing ?: source.spacing,
                staggerX = staggerX ?: source.staggerX,
                staggerY = staggerY ?: source.staggerY
            ),
            key = "tiling"
        )
    }

    fun importLogo(uri: Uri) {
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { importer.import(uri) }
            if (stored == null) {
                _state.update { it.copy(message = EditorMessage.LOGO_IMPORT_FAILED) }
            } else {
                setBase(WatermarkSource.Image(stored))
            }
        }
    }

    fun resetPlacement() = edit {
        it.withPlacement(
            _state.value.orientation,
            if (_state.value.orientation ==
                Orientation.PORTRAIT
            ) {
                Placement.DefaultPortrait
            } else {
                Placement.DefaultLandscape
            }
        )
    }

    // endregion

    // region profiles

    /** Loads a stored profile into the editor, replacing the current one and clearing the history. */
    fun selectProfile(id: Long) {
        viewModelScope.launch {
            val loaded = profileRepository.get(id) ?: return@launch
            history.clear()
            profileRepository.markUsed(id)
            _state.update { it.copy(profile = loaded, savedProfile = loaded, canUndo = false, canRedo = false) }
        }
    }

    /** Stores the current changes into the loaded profile. */
    fun saveProfile() {
        viewModelScope.launch {
            val saved = profileRepository.save(_state.value.profile)
            profileRepository.markUsed(saved.id)
            _state.update { it.copy(profile = saved, savedProfile = saved, message = EditorMessage.PROFILE_SAVED) }
        }
    }

    fun saveProfileAs(name: String) {
        viewModelScope.launch {
            val saved = profileRepository.saveAs(_state.value.profile, name)
            profileRepository.markUsed(saved.id)
            _state.update { it.copy(profile = saved, savedProfile = saved, message = EditorMessage.PROFILE_SAVED) }
        }
    }

    fun renameProfile(id: Long, name: String) {
        viewModelScope.launch {
            profileRepository.rename(id, name)
            val renamed = profileRepository.get(id) ?: return@launch
            _state.update { s ->
                if (s.profile.id == id) {
                    s.copy(profile = s.profile.copy(name = renamed.name), savedProfile = s.savedProfile?.copy(name = renamed.name))
                } else {
                    s
                }
            }
        }
    }

    fun duplicateProfile(id: Long) {
        viewModelScope.launch { profileRepository.duplicate(id) }
    }

    fun deleteProfile(id: Long) {
        viewModelScope.launch {
            if (!profileRepository.delete(id)) {
                _state.update { it.copy(message = EditorMessage.LAST_PROFILE) }
            } else if (_state.value.profile.id == id) {
                val next = profileRepository.lastUsed()
                history.clear()
                _state.update { it.copy(profile = next, savedProfile = next, canUndo = false, canRedo = false) }
            }
        }
    }

    // endregion

    fun undo() {
        history.undo(_state.value.profile)?.let { restored -> _state.update { it.copy(profile = restored) } }
        publishHistory()
    }

    fun redo() {
        history.redo(_state.value.profile)?.let { restored -> _state.update { it.copy(profile = restored) } }
        publishHistory()
    }

    private fun publishHistory() = _state.update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo) }

    fun hasLogo(): Boolean = lastImage != null

    fun lastText(): WatermarkSource.Text = lastText

    fun lastImage(): WatermarkSource.Image? = lastImage

    private fun remember(source: WatermarkSource) {
        when (val leaf = leafOf(source)) {
            is WatermarkSource.Text -> lastText = leaf
            is WatermarkSource.Image -> lastImage = leaf
            is WatermarkSource.Tiled -> Unit
        }
    }

    private fun now() = System.currentTimeMillis()

    private fun leafOf(source: WatermarkSource): WatermarkSource = if (source is WatermarkSource.Tiled) leafOf(source.base) else source

    private fun rebuild(source: WatermarkSource, leaf: WatermarkSource): WatermarkSource =
        if (source is WatermarkSource.Tiled) source.copy(base = leaf) else leaf

    private companion object {
        const val KEY_IDS = "ids"
        const val KEY_INDEX = "index"
        const val MAX_MARGIN = 0.15f
    }
}
