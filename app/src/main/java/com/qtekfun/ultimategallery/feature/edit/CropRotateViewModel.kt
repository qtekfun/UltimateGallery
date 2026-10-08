package com.qtekfun.ultimategallery.feature.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimategallery.data.edit.CropRect
import com.qtekfun.ultimategallery.data.edit.CropRotateSpec
import com.qtekfun.ultimategallery.data.edit.EditSaver
import com.qtekfun.ultimategallery.data.edit.SaveResult
import com.qtekfun.ultimategallery.data.media.MediaRepository
import com.qtekfun.ultimategallery.data.prefs.SaveBehavior
import com.qtekfun.ultimategallery.data.prefs.SettingsRepository
import com.qtekfun.ultimategallery.domain.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the crop and rotate screen shows. */
data class CropRotateState(
    val item: MediaItem? = null,
    val loadFailed: Boolean = false,
    val spec: CropRotateSpec = CropRotateSpec(),
    val aspect: AspectRatio = AspectRatio.FREE,
    val dragging: Boolean = false,
    val saving: Boolean = false,
    /** True while the "copy, overwrite or cancel" question is shown. */
    val askingBehavior: Boolean = false,
    /** Size of the picture as the image loader decoded it (orientation applied), once known; its units don't matter. */
    val decodedSize: CanvasSize? = null
) {
    /**
     * The size of the photo as shown, with the orientation applied. The stored size is the best guess until the picture is
     * decoded; the decoded shape wins when the two disagree, as the stored one can be stale or not orientation-adjusted.
     */
    val photoSize: CanvasSize?
        get() = item?.let { CropGeometry.reconcileSize(it.width, it.height, decodedSize) }

    /** The size of the canvas the crop frame lives on, in source pixels. */
    val canvas: CanvasSize?
        get() = photoSize?.let { CropGeometry.canvasSize(it.width, it.height, spec.turns) }

    val hasChanges: Boolean get() = !spec.isIdentity
}

/** One-shot results of a save. */
sealed interface CropRotateEvent {
    data class Saved(val uri: Uri) : CropRotateEvent

    data object Denied : CropRotateEvent

    data object Failed : CropRotateEvent
}

@HiltViewModel
class CropRotateViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val media: MediaRepository,
    private val settings: SettingsRepository,
    private val saver: EditSaver
) : ViewModel() {
    private val mediaId: Long = checkNotNull(savedState["mediaId"])

    private val _state = MutableStateFlow(CropRotateState())
    val state: StateFlow<CropRotateState> = _state.asStateFlow()

    private val _events = Channel<CropRotateEvent>(Channel.BUFFERED)
    val events: Flow<CropRotateEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val item = media.loadItems(listOf(mediaId)).firstOrNull()
            _state.update { if (item == null || item.isVideo) it.copy(loadFailed = true) else it.copy(item = item) }
        }
    }

    /** Turns the photo and the frame by 90 degrees. */
    fun rotate(clockwise: Boolean) = edit { s ->
        val spec = s.spec
        // After a single mirror, a clockwise turn of the picture is a counterclockwise turn of the pre-mirror one.
        val mirrored = spec.flipH != spec.flipV
        val step = if (clockwise != mirrored) 1 else -1
        val turned = spec.copy(quarterTurns = spec.turns + step, crop = CropGeometry.rotateRect(spec.crop, clockwise))
        s.copy(spec = turned.copy(quarterTurns = turned.turns), aspect = s.aspect.turned())
    }

    /** Mirrors the picture as it is shown. The straighten angle follows the mirror so the horizon stays level. */
    fun flip(horizontal: Boolean) = edit { s ->
        val spec = s.spec
        s.copy(
            spec = spec.copy(
                flipH = if (horizontal) !spec.flipH else spec.flipH,
                flipV = if (horizontal) spec.flipV else !spec.flipV,
                straightenDeg = -spec.straightenDeg,
                crop = CropGeometry.flipRect(spec.crop, horizontal)
            )
        )
    }

    fun setStraighten(degrees: Float) = edit { s ->
        val canvas = s.canvas ?: return@edit s
        val angle = degrees.coerceIn(-CropGeometry.MAX_STRAIGHTEN, CropGeometry.MAX_STRAIGHTEN)
        s.copy(spec = s.spec.copy(straightenDeg = angle, crop = CropGeometry.constrain(s.spec.crop, canvas, angle)))
    }

    fun setAspect(aspect: AspectRatio) = edit { s ->
        val canvas = s.canvas ?: return@edit s
        val ratio = aspect.ratio ?: return@edit s.copy(aspect = aspect)
        val shaped = CropGeometry.applyAspect(s.spec.crop, ratio, canvas)
        s.copy(aspect = aspect, spec = s.spec.copy(crop = CropGeometry.constrain(shaped, canvas, s.spec.straightenDeg)))
    }

    /** The image loader decoded the picture at [width] x [height]; its shape is the real one. */
    fun photoMeasured(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val size = CanvasSize(width.toFloat(), height.toFloat())
        _state.update { if (it.decodedSize == size) it else it.copy(decodedSize = size) }
    }

    fun dragStarted() = edit { it.copy(dragging = true) }

    fun dragEnded() = edit { it.copy(dragging = false) }

    /**
     * Moves or resizes the frame. Returns true when a free frame has just taken the shape of one of
     * the presets, which the screen marks with a tick.
     */
    fun drag(handle: CropHandle, dx: Float, dy: Float): Boolean {
        val before = _state.value
        val canvas = before.canvas ?: return false
        val crop = CropGeometry.drag(before.spec.crop, handle, dx, dy, before.aspect.ratio, canvas, before.spec.straightenDeg)
        _state.update { it.copy(spec = it.spec.copy(crop = crop)) }
        if (before.aspect != AspectRatio.FREE || handle == CropHandle.MOVE) return false
        val snapped = CropGeometry.snappedAspect(crop, canvas)
        return snapped != null && snapped != CropGeometry.snappedAspect(before.spec.crop, canvas)
    }

    fun reset() = edit { it.copy(spec = CropRotateSpec(crop = CropRect.FULL), aspect = AspectRatio.FREE) }

    /** Saves with the stored behavior, or asks the user first when it is [SaveBehavior.ASK]. */
    fun requestSave() {
        val current = _state.value
        if (current.saving || current.item == null || !current.hasChanges) return
        viewModelScope.launch {
            when (val behavior = settings.settings.first().saveBehavior) {
                SaveBehavior.ASK -> _state.update { it.copy(askingBehavior = true) }
                else -> save(behavior)
            }
        }
    }

    fun dismissAsk() = edit { it.copy(askingBehavior = false) }

    /** Answers the question: save as [behavior], and keep it as the setting when [remember] is set. */
    fun confirmAsk(behavior: SaveBehavior, remember: Boolean) {
        _state.update { it.copy(askingBehavior = false) }
        viewModelScope.launch {
            if (remember) settings.setSaveBehavior(behavior)
            save(behavior)
        }
    }

    private suspend fun save(behavior: SaveBehavior) {
        val current = _state.value
        val item = current.item ?: return
        if (current.saving) return
        _state.update { it.copy(saving = true) }
        val result = saver.save(item, current.spec, behavior)
        _state.update { it.copy(saving = false) }
        _events.send(
            when (result) {
                is SaveResult.Saved -> CropRotateEvent.Saved(result.uri)
                SaveResult.Denied -> CropRotateEvent.Denied
                is SaveResult.Failed -> CropRotateEvent.Failed
            }
        )
    }

    private fun edit(transform: (CropRotateState) -> CropRotateState) {
        _state.update { if (it.item == null || it.saving) it else transform(it) }
    }
}
