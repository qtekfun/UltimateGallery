package com.qtekfun.ultimategallery.feature.photoedit

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimategallery.data.edit.PhotoEditImages
import com.qtekfun.ultimategallery.data.edit.PhotoEditSaver
import com.qtekfun.ultimategallery.data.edit.SaveResult
import com.qtekfun.ultimategallery.data.media.MediaRepository
import com.qtekfun.ultimategallery.data.prefs.SaveBehavior
import com.qtekfun.ultimategallery.data.prefs.SettingsRepository
import com.qtekfun.ultimategallery.di.DefaultDispatcher
import com.qtekfun.ultimategallery.di.IoDispatcher
import com.qtekfun.ultimategallery.domain.edit.AdjustKind
import com.qtekfun.ultimategallery.domain.edit.Adjustments
import com.qtekfun.ultimategallery.domain.edit.Annotation
import com.qtekfun.ultimategallery.domain.edit.AnnotationGeometry
import com.qtekfun.ultimategallery.domain.edit.BackgroundEdit
import com.qtekfun.ultimategallery.domain.edit.BackgroundFill
import com.qtekfun.ultimategallery.domain.edit.Cover
import com.qtekfun.ultimategallery.domain.edit.CoverMode
import com.qtekfun.ultimategallery.domain.edit.EditPoint
import com.qtekfun.ultimategallery.domain.edit.FilterChoice
import com.qtekfun.ultimategallery.domain.edit.FilterPreset
import com.qtekfun.ultimategallery.domain.edit.MaskOp
import com.qtekfun.ultimategallery.domain.edit.PhotoEdit
import com.qtekfun.ultimategallery.domain.edit.ShapeKind
import com.qtekfun.ultimategallery.domain.edit.SnapshotHistory
import com.qtekfun.ultimategallery.domain.edit.StrokeStyle
import com.qtekfun.ultimategallery.domain.watermark.FontLibrary
import com.qtekfun.ultimategallery.render.edit.AnnotationPainter
import com.qtekfun.ultimategallery.render.edit.PhotoEditRenderer
import com.qtekfun.ultimategallery.render.export.ExportImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
@Suppress("TooManyFunctions")
class PhotoEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val media: MediaRepository,
    private val settings: SettingsRepository,
    private val saver: PhotoEditSaver,
    private val loader: ExportImageLoader,
    fonts: FontLibrary,
    @IoDispatcher private val io: CoroutineDispatcher,
    @DefaultDispatcher private val compute: CoroutineDispatcher
) : ViewModel() {
    private val mediaId: Long = checkNotNull(savedState["mediaId"])
    private val renderer = PhotoEditRenderer(fonts::typeface)
    private val history = SnapshotHistory<PhotoEdit>()

    /** Typefaces of the fonts, for the font chips. */
    val typefaceOf: (String) -> Typeface? = fonts::typeface

    /** Draws annotations on the stage; the same painter the saved photo uses. */
    val painter: AnnotationPainter get() = renderer.painter

    private val _state = MutableStateFlow(PhotoEditorState())
    val state: StateFlow<PhotoEditorState> = _state.asStateFlow()

    private val _events = Channel<PhotoEditorEvent>(Channel.BUFFERED)
    val events: Flow<PhotoEditorEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch { fonts.fonts.collect { list -> _state.update { it.copy(fonts = list) } } }
        viewModelScope.launch { renderBases() }
    }

    private suspend fun load() {
        val item = media.loadItems(listOf(mediaId)).firstOrNull()
        if (item == null || item.isVideo) {
            _state.update { it.copy(loadFailed = true) }
            return
        }
        @Suppress("TooGenericExceptionCaught")
        val decoded = try {
            withContext(io) {
                val preview = loader.load(item.uri, PREVIEW_EDGE).bitmap
                val thumb = PhotoEditRenderer.downscale(preview, thumbWidth(preview), thumbHeight(preview))
                preview to thumb
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        _state.update {
            if (decoded == null) it.copy(loadFailed = true) else it.copy(item = item, preview = decoded.first, thumbnail = decoded.second)
        }
    }

    /** Re-renders the base layer whenever it changes; a newer edit replaces a render in progress. */
    private suspend fun renderBases() {
        _state.filter { it.preview != null }
            .map { it.preview to it.edit.base }
            .distinctUntilChanged()
            .collectLatest { (preview, base) ->
                val src = preview ?: return@collectLatest
                if (base.isIdentity) {
                    _state.update { it.copy(base = src, rendering = false) }
                    return@collectLatest
                }
                _state.update { it.copy(rendering = true) }
                val rendered = withContext(compute) { renderer.renderBase(src, base) }
                _state.update { it.copy(base = rendered, rendering = false) }
            }
    }

    // Tools and settings.

    fun selectTool(tool: EditorTool) = _state.update { it.copy(tool = tool, selectedId = null) }

    fun selectAdjust(kind: AdjustKind) = _state.update { it.copy(adjustKind = kind) }

    fun setDrawMode(mode: DrawMode) = _state.update {
        it.copy(
            draw = it.draw.copy(mode = mode),
            selectedId = if (mode ==
                DrawMode.SELECT
            ) {
                it.selectedId
            } else {
                null
            }
        )
    }

    fun setDrawColor(color: Int) {
        _state.update { it.copy(draw = it.draw.copy(color = color)) }
        restyleSelected { a -> recolor(a, color) }
    }

    fun setDrawWidth(width: Float) {
        _state.update { it.copy(draw = it.draw.copy(width = width)) }
        restyleSelected { a ->
            when (a) {
                is Annotation.Stroke -> a.copy(width = width)
                is Annotation.Shape -> a.copy(width = width)
                is Annotation.Text -> a
            }
        }
    }

    fun setDrawFilled(filled: Boolean) {
        _state.update { it.copy(draw = it.draw.copy(filled = filled)) }
        restyleSelected { a -> if (a is Annotation.Shape) a.copy(filled = filled) else a }
    }

    fun setTextColor(color: Int) {
        _state.update { it.copy(text = it.text.copy(color = color)) }
        restyleSelected { a -> if (a is Annotation.Text) a.copy(color = color) else a }
    }

    fun setTextFont(fontId: String) {
        _state.update { it.copy(text = it.text.copy(fontId = fontId)) }
        restyleSelected { a -> if (a is Annotation.Text) a.copy(fontId = fontId) else a }
    }

    fun setTextBoxed(boxed: Boolean) {
        _state.update { it.copy(text = it.text.copy(boxed = boxed)) }
        restyleSelected { a -> if (a is Annotation.Text) a.copy(boxed = boxed) else a }
    }

    fun setCoverMode(mode: CoverMode) = _state.update { it.copy(cover = it.cover.copy(mode = mode)) }

    fun setCoverWidth(width: Float) = _state.update { it.copy(cover = it.cover.copy(width = width)) }

    fun setCoverStrength(strength: Float) = _state.update { it.copy(cover = it.cover.copy(strength = strength)) }

    fun setBackgroundMode(mode: BackgroundMode) = _state.update { it.copy(background = it.background.copy(mode = mode)) }

    fun setTolerance(tolerance: Float) = _state.update { it.copy(background = it.background.copy(tolerance = tolerance)) }

    fun setBackgroundBrush(width: Float) = _state.update { it.copy(background = it.background.copy(brushWidth = width)) }

    // Light, color and filters.

    fun setAdjust(kind: AdjustKind, value: Float) = commit("adjust:$kind") { it.copy(adjust = it.adjust.with(kind, value)) }

    fun resetAdjust() = commit(null) { it.copy(adjust = Adjustments.NONE) }

    fun setFilter(preset: FilterPreset) = commit(null) { it.copy(filter = FilterChoice(preset, if (preset == it.filter.preset) it.filter.intensity else 1f)) }

    fun setFilterIntensity(intensity: Float) = commit("filter") { it.copy(filter = it.filter.copy(intensity = intensity.coerceIn(0f, 1f))) }

    // Drawing.

    /** Finishes a freehand gesture in the current tool: a pen line, a cover or a background brush. */
    fun finishStroke(points: List<EditPoint>) {
        if (points.isEmpty()) return
        val s = _state.value
        when (s.tool) {
            EditorTool.DRAW -> {
                val style = if (s.draw.mode == DrawMode.HIGHLIGHTER) StrokeStyle.HIGHLIGHTER else StrokeStyle.PEN
                val width = if (style == StrokeStyle.HIGHLIGHTER) s.draw.width * HIGHLIGHTER_WIDTH else s.draw.width
                commit(null) { it.copy(annotations = it.annotations + Annotation.Stroke(it.nextAnnotationId(), points, s.draw.color, width, style)) }
            }
            EditorTool.BLUR -> commit(null) { it.copy(covers = it.covers + Cover(points, s.cover.width, s.cover.mode, s.cover.strength)) }
            EditorTool.BACKGROUND -> {
                val op = MaskOp.Brush(points, s.background.brushWidth, restore = s.background.mode == BackgroundMode.RESTORE)
                commit(null) { it.copy(background = it.background.copy(ops = it.background.ops + op)) }
            }
            else -> Unit
        }
    }

    /** The stroke the pen would draw through [points], for the live preview of a gesture. */
    fun previewStroke(points: List<EditPoint>): Annotation.Stroke {
        val draw = _state.value.draw
        val highlighter = draw.mode == DrawMode.HIGHLIGHTER
        return Annotation.Stroke(
            LIVE_ID,
            points,
            draw.color,
            if (highlighter) draw.width * HIGHLIGHTER_WIDTH else draw.width,
            if (highlighter) StrokeStyle.HIGHLIGHTER else StrokeStyle.PEN
        )
    }

    /** The shape of the current mode from [start] to [end], or null when the mode draws no shape. */
    fun previewShape(start: EditPoint, end: EditPoint): Annotation.Shape? {
        val draw = _state.value.draw
        val kind = when (draw.mode) {
            DrawMode.LINE -> ShapeKind.LINE
            DrawMode.ARROW -> ShapeKind.ARROW
            DrawMode.RECTANGLE -> ShapeKind.RECTANGLE
            DrawMode.ELLIPSE -> ShapeKind.ELLIPSE
            else -> return null
        }
        return Annotation.Shape(LIVE_ID, kind, start, end, draw.color, draw.width, draw.filled)
    }

    fun finishShape(start: EditPoint, end: EditPoint) {
        val shape = previewShape(start, end) ?: return
        val preview = _state.value.preview ?: return
        // A tap is not a shape.
        if (hypot((end.x - start.x) * preview.width, (end.y - start.y) * preview.height) < MIN_SHAPE_PX) return
        commit(null) { it.copy(annotations = it.annotations + shape.copy(id = it.nextAnnotationId())) }
    }

    /** Selects the topmost item under [point] that the current tool can edit, or clears the selection. Returns the selected id. */
    fun selectAt(point: EditPoint, slopPx: Float): Long? {
        val s = _state.value
        val preview = s.preview ?: return null
        val candidates = s.edit.annotations.filter { (it is Annotation.Text) == (s.tool == EditorTool.TEXT) }
        val id = AnnotationGeometry.hitTest(candidates, point, preview.width, preview.height, slopPx, painter)
        _state.update { it.copy(selectedId = id) }
        id?.let { syncSettingsWith(s.edit.annotation(it)) }
        history.breakCoalescing()
        return id
    }

    fun clearSelection() = _state.update { it.copy(selectedId = null) }

    /** Moves the selected item by [dx], [dy] (fractions) and, for text, scales it by [zoom] and turns it by [rotationDeg]. */
    fun transformSelected(dx: Float, dy: Float, zoom: Float, rotationDeg: Float) {
        val id = _state.value.selectedId ?: return
        commit("transform:$id") { edit ->
            val current = edit.annotation(id) ?: return@commit edit
            var moved = AnnotationGeometry.move(current, dx, dy)
            if (moved is Annotation.Text && (zoom != 1f || rotationDeg != 0f)) moved = AnnotationGeometry.transformText(moved, zoom, rotationDeg)
            edit.replaceAnnotation(moved)
        }
    }

    fun endTransform() = history.breakCoalescing()

    fun deleteSelected() {
        val id = _state.value.selectedId ?: return
        commit(null) { it.removeAnnotation(id) }
        _state.update { it.copy(selectedId = null) }
    }

    // Text.

    fun addText(text: String) {
        if (text.isBlank()) return
        val settings = _state.value.text
        var newId = 0L
        commit(null) { edit ->
            newId = edit.nextAnnotationId()
            edit.copy(
                annotations =
                    edit.annotations + Annotation.Text(newId, text, EditPoint(CENTER, CENTER), TEXT_SIZE, 0f, settings.color, settings.fontId, settings.boxed)
            )
        }
        _state.update { it.copy(selectedId = newId) }
    }

    fun updateText(id: Long, text: String) {
        if (text.isBlank()) {
            commit(null) { it.removeAnnotation(id) }
            _state.update { it.copy(selectedId = null) }
            return
        }
        commit(null) { edit -> (edit.annotation(id) as? Annotation.Text)?.let { edit.replaceAnnotation(it.copy(text = text)) } ?: edit }
    }

    // Background.

    fun autoRemoveBackground() {
        val tolerance = _state.value.background.tolerance
        commit(null) { it.copy(background = it.background.copy(ops = it.background.ops + MaskOp.Auto(tolerance))) }
    }

    fun removeRegionAt(point: EditPoint) {
        val tolerance = _state.value.background.tolerance
        commit(null) { it.copy(background = it.background.copy(ops = it.background.ops + MaskOp.Seed(point, tolerance))) }
    }

    fun setFeather(feather: Float) = commit("feather") { it.copy(background = it.background.copy(feather = feather.coerceIn(0f, 1f))) }

    fun setBackgroundFill(fill: BackgroundFill) = commit(null) { it.copy(background = it.background.copy(fill = fill)) }

    fun resetBackground() = commit(null) { it.copy(background = BackgroundEdit()) }

    // History.

    fun undo() {
        val previous = history.undo(_state.value.edit) ?: return
        _state.update { it.copy(edit = previous, selectedId = it.selectedId?.takeIf { id -> previous.annotation(id) != null }) }
        refreshHistory()
    }

    fun redo() {
        val next = history.redo(_state.value.edit) ?: return
        _state.update { it.copy(edit = next, selectedId = it.selectedId?.takeIf { id -> next.annotation(id) != null }) }
        refreshHistory()
    }

    // Saving.

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

    fun dismissAsk() = _state.update { it.copy(askingBehavior = false) }

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
        _state.update { it.copy(saving = true, selectedId = null) }
        val result = saver.save(item, current.edit, behavior)
        _state.update { it.copy(saving = false) }
        _events.send(
            when (result) {
                is SaveResult.Saved -> PhotoEditorEvent.Saved(
                    result.uri,
                    asCopy =
                        behavior == SaveBehavior.OVERWRITE && !result.overwritten && !PhotoEditImages.canOverwrite(item.mimeType, current.edit.hasTransparency)
                )
                SaveResult.Denied -> PhotoEditorEvent.Denied
                is SaveResult.Failed -> PhotoEditorEvent.Failed
            }
        )
    }

    private fun commit(key: String?, transform: (PhotoEdit) -> PhotoEdit) {
        val before = _state.value
        if (before.item == null || before.saving) return
        val after = transform(before.edit)
        if (after == before.edit) return
        history.record(before.edit, key, System.currentTimeMillis())
        _state.update { it.copy(edit = after) }
        refreshHistory()
    }

    private fun refreshHistory() = _state.update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo) }

    private fun restyleSelected(transform: (Annotation) -> Annotation) {
        val id = _state.value.selectedId ?: return
        commit("style:$id") { edit -> edit.annotation(id)?.let { edit.replaceAnnotation(transform(it)) } ?: edit }
    }

    /** The settings follow the item just selected, so the panel shows its color and width. */
    private fun syncSettingsWith(annotation: Annotation?) {
        when (annotation) {
            is Annotation.Stroke -> _state.update { it.copy(draw = it.draw.copy(color = annotation.color)) }
            is Annotation.Shape -> _state.update {
                it.copy(draw = it.draw.copy(color = annotation.color, width = annotation.width, filled = annotation.filled))
            }
            is Annotation.Text -> _state.update { it.copy(text = TextSettings(annotation.color, annotation.fontId, annotation.boxed)) }
            null -> Unit
        }
    }

    private fun recolor(a: Annotation, color: Int): Annotation = when (a) {
        is Annotation.Stroke -> a.copy(color = color)
        is Annotation.Shape -> a.copy(color = color)
        is Annotation.Text -> a
    }

    private fun thumbWidth(b: Bitmap) = maxOf(1, b.width * THUMB_EDGE / maxOf(b.width, b.height))

    private fun thumbHeight(b: Bitmap) = maxOf(1, b.height * THUMB_EDGE / maxOf(b.width, b.height))

    companion object {
        const val PREVIEW_EDGE = 1600
        private const val THUMB_EDGE = 160
        private const val LIVE_ID = -1L
        private const val HIGHLIGHTER_WIDTH = 3f
        private const val MIN_SHAPE_PX = 6f
        private const val CENTER = 0.5f
        private const val TEXT_SIZE = 0.08f
    }
}
