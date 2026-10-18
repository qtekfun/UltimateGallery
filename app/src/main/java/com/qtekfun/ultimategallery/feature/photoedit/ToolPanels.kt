package com.qtekfun.ultimategallery.feature.photoedit

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterVintage
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.domain.edit.AdjustKind
import com.qtekfun.ultimategallery.domain.edit.Annotation
import com.qtekfun.ultimategallery.domain.edit.BackgroundFill
import com.qtekfun.ultimategallery.domain.edit.ColorMatrices
import com.qtekfun.ultimategallery.domain.edit.CoverMode
import com.qtekfun.ultimategallery.domain.edit.FilterPreset
import com.qtekfun.ultimategallery.domain.edit.ShapeKind
import com.qtekfun.ultimategallery.feature.watermark.ColorRow
import com.qtekfun.ultimategallery.feature.watermark.rememberFontFamily
import kotlin.math.abs
import kotlin.math.roundToInt

private const val PERCENT = 100f
private const val DETENT = 0.03f
private val THUMB_SIZE = 64.dp
private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()

/** Everything the tool panels can ask the editor to do. */
internal class PanelActions(val vm: PhotoEditorViewModel, val onAddText: () -> Unit, val onEditText: (Long) -> Unit)

@Composable
internal fun ToolPanel(state: PhotoEditorState, actions: PanelActions, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (state.tool) {
            EditorTool.ADJUST -> AdjustPanel(state, actions.vm)
            EditorTool.FILTER -> FilterPanel(state, actions.vm)
            EditorTool.DRAW -> DrawPanel(state, actions.vm)
            EditorTool.TEXT -> TextPanel(state, actions)
            EditorTool.BLUR -> BlurPanel(state, actions.vm)
            EditorTool.BACKGROUND -> BackgroundPanel(state, actions.vm)
        }
    }
}

@Composable
private fun AdjustPanel(state: PhotoEditorState, vm: PhotoEditorViewModel) {
    val kinds = AdjustKind.entries
    ChipRow {
        kinds.forEach { kind ->
            val changed = state.edit.adjust.get(kind) != 0f
            FilterChip(
                selected = state.adjustKind == kind,
                onClick = { vm.selectAdjust(kind) },
                label = { Text(stringResource(adjustLabel(kind)) + if (changed) " •" else "") }
            )
        }
    }
    val kind = state.adjustKind
    ValueSlider(
        label = stringResource(adjustLabel(kind)),
        value = state.edit.adjust.get(kind),
        range = kind.min..1f,
        signed = kind.min < 0f,
        onChange = { vm.setAdjust(kind, it) }
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = vm::resetAdjust, enabled = !state.edit.adjust.isIdentity) { Text(stringResource(R.string.photo_edit_reset_adjust)) }
    }
}

@Composable
private fun FilterPanel(state: PhotoEditorState, vm: PhotoEditorViewModel) {
    val thumb = state.thumbnail
    val image = remember(thumb) { thumb?.asImageBitmap() }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterPreset.entries.forEach { preset ->
            val selected = state.edit.filter.preset == preset
            val label = stringResource(filterLabel(preset))
            Column(
                Modifier
                    .width(THUMB_SIZE + 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.RadioButton) { vm.setFilter(preset) }
                    .semantics { stateDescription = if (selected) "selected" else "" }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (image != null) {
                    Image(
                        image,
                        contentDescription = label,
                        contentScale = ContentScale.Crop,
                        colorFilter = ColorFilter.colorMatrix(ColorMatrix(ColorMatrices.forPreset(preset))),
                        modifier = Modifier
                            .size(THUMB_SIZE)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                BorderStroke(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.primary),
                                RoundedCornerShape(10.dp)
                            )
                    )
                }
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
    if (!state.edit.filter.isIdentity || state.edit.filter.preset != FilterPreset.NONE) {
        ValueSlider(
            label = stringResource(R.string.photo_edit_intensity),
            value = state.edit.filter.intensity,
            range = 0f..1f,
            signed = false,
            onChange = vm::setFilterIntensity
        )
    }
}

@Composable
private fun DrawPanel(state: PhotoEditorState, vm: PhotoEditorViewModel) {
    val modes = listOf(
        Triple(DrawMode.SELECT, Icons.Outlined.TouchApp, R.string.photo_edit_mode_select),
        Triple(DrawMode.PEN, Icons.Outlined.Draw, R.string.photo_edit_mode_pen),
        Triple(DrawMode.HIGHLIGHTER, Icons.Outlined.BorderColor, R.string.photo_edit_mode_highlighter),
        Triple(DrawMode.LINE, Icons.Outlined.HorizontalRule, R.string.photo_edit_mode_line),
        Triple(DrawMode.ARROW, Icons.Outlined.NorthEast, R.string.photo_edit_mode_arrow),
        Triple(DrawMode.RECTANGLE, Icons.Outlined.CropSquare, R.string.photo_edit_mode_rectangle),
        Triple(DrawMode.ELLIPSE, Icons.Outlined.Circle, R.string.photo_edit_mode_ellipse)
    )
    ChipRow {
        modes.forEach { (mode, icon, label) -> IconChip(state.draw.mode == mode, icon, stringResource(label)) { vm.setDrawMode(mode) } }
    }
    val selected = state.edit.annotation(state.selectedId)
    if (state.draw.mode == DrawMode.SELECT && selected == null) {
        Hint(R.string.photo_edit_select_hint)
    }
    ColorRow(selected = state.draw.color, onSelect = vm::setDrawColor, modifier = Modifier.fillMaxWidth())
    ValueSlider(
        label = stringResource(R.string.photo_edit_width),
        value = state.draw.width,
        range = MIN_WIDTH..MAX_WIDTH,
        signed = false,
        onChange = vm::setDrawWidth,
        shown = { ((it - MIN_WIDTH) / (MAX_WIDTH - MIN_WIDTH) * PERCENT).roundToInt().toString() }
    )
    val shapeFillable = state.draw.mode in setOf(DrawMode.RECTANGLE, DrawMode.ELLIPSE) ||
        (selected as? Annotation.Shape)?.kind in setOf(ShapeKind.RECTANGLE, ShapeKind.ELLIPSE)
    if (shapeFillable) SwitchRow(R.string.photo_edit_filled, state.draw.filled, vm::setDrawFilled)
    if (selected != null) DeleteButton(vm::deleteSelected)
}

@Composable
private fun TextPanel(state: PhotoEditorState, actions: PanelActions) {
    val vm = actions.vm
    val selected = state.edit.annotation(state.selectedId) as? Annotation.Text
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = actions.onAddText) { Text(stringResource(R.string.photo_edit_add_text)) }
        if (selected != null) {
            OutlinedButton(onClick = { actions.onEditText(selected.id) }) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.photo_edit_edit_text), Modifier.padding(start = 6.dp))
            }
        }
    }
    Hint(if (selected == null) R.string.photo_edit_text_hint else R.string.photo_edit_text_selected_hint)
    ColorRow(selected = state.text.color, onSelect = vm::setTextColor, modifier = Modifier.fillMaxWidth())
    ChipRow {
        state.fonts.forEach { font ->
            val family: FontFamily = rememberFontFamily(font.id, vm.typefaceOf)
            FilterChip(
                selected = state.text.fontId == font.id,
                onClick = { vm.setTextFont(font.id) },
                label = { Text(font.label.ifEmpty { stringResource(R.string.photo_edit_font_default) }, fontFamily = family, maxLines = 1) }
            )
        }
    }
    SwitchRow(R.string.photo_edit_text_box, state.text.boxed, vm::setTextBoxed)
    if (selected != null) DeleteButton(vm::deleteSelected)
}

@Composable
private fun BlurPanel(state: PhotoEditorState, vm: PhotoEditorViewModel) {
    ChipRow {
        IconChip(state.cover.mode == CoverMode.BLUR, Icons.Outlined.BlurOn, stringResource(R.string.photo_edit_blur)) { vm.setCoverMode(CoverMode.BLUR) }
        IconChip(state.cover.mode == CoverMode.PIXELATE, Icons.Outlined.GridOn, stringResource(R.string.photo_edit_pixelate)) {
            vm.setCoverMode(CoverMode.PIXELATE)
        }
    }
    Hint(R.string.photo_edit_blur_hint)
    BrushSlider(state.cover.width, vm::setCoverWidth)
    ValueSlider(stringResource(R.string.photo_edit_strength), state.cover.strength, 0f..1f, signed = false, onChange = vm::setCoverStrength)
}

@Composable
private fun BackgroundPanel(state: PhotoEditorState, vm: PhotoEditorViewModel) {
    val bg = state.background
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = vm::autoRemoveBackground, enabled = !state.saving) {
            Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.photo_edit_bg_auto), Modifier.padding(start = 6.dp))
        }
        TextButton(onClick = vm::resetBackground, enabled = !state.edit.background.isIdentity) { Text(stringResource(R.string.photo_edit_bg_reset)) }
    }
    ChipRow {
        IconChip(bg.mode == BackgroundMode.TAP, Icons.Outlined.TouchApp, stringResource(R.string.photo_edit_bg_tap)) {
            vm.setBackgroundMode(BackgroundMode.TAP)
        }
        IconChip(bg.mode == BackgroundMode.ERASE, Icons.Outlined.LayersClear, stringResource(R.string.photo_edit_bg_erase)) {
            vm.setBackgroundMode(BackgroundMode.ERASE)
        }
        IconChip(bg.mode == BackgroundMode.RESTORE, Icons.Outlined.Brush, stringResource(R.string.photo_edit_bg_restore)) {
            vm.setBackgroundMode(BackgroundMode.RESTORE)
        }
    }
    Hint(
        when (bg.mode) {
            BackgroundMode.TAP -> R.string.photo_edit_bg_tap_hint
            BackgroundMode.ERASE -> R.string.photo_edit_bg_erase_hint
            BackgroundMode.RESTORE -> R.string.photo_edit_bg_restore_hint
        }
    )
    if (bg.mode == BackgroundMode.TAP) {
        ValueSlider(stringResource(R.string.photo_edit_tolerance), bg.tolerance, 0f..1f, signed = false, onChange = vm::setTolerance)
    } else {
        BrushSlider(bg.brushWidth, vm::setBackgroundBrush)
    }
    ValueSlider(stringResource(R.string.photo_edit_edge_softness), state.edit.background.feather, 0f..1f, signed = false, onChange = vm::setFeather)
    Text(stringResource(R.string.photo_edit_bg_fill), style = MaterialTheme.typography.labelLarge)
    val fill = state.edit.background.fill
    ChipRow {
        FilterChip(selected = fill == BackgroundFill.Transparent, onClick = {
            vm.setBackgroundFill(BackgroundFill.Transparent)
        }, label = { Text(stringResource(R.string.photo_edit_bg_transparent)) })
        FilterChip(selected = fill == BackgroundFill.Solid(WHITE), onClick = {
            vm.setBackgroundFill(BackgroundFill.Solid(WHITE))
        }, label = { Text(stringResource(R.string.photo_edit_bg_white)) })
        FilterChip(selected = fill == BackgroundFill.Solid(BLACK), onClick = {
            vm.setBackgroundFill(BackgroundFill.Solid(BLACK))
        }, label = { Text(stringResource(R.string.photo_edit_bg_black)) })
    }
    ColorRow(selected = (fill as? BackgroundFill.Solid)?.color ?: WHITE, onSelect = {
        vm.setBackgroundFill(BackgroundFill.Solid(it))
    }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

@Composable
private fun IconChip(selected: Boolean, icon: ImageVector, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
    )
}

@Composable
private fun Hint(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun DeleteButton(onDelete: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onDelete()
        }) {
            Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.photo_edit_delete), Modifier.padding(start = 6.dp), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun BrushSlider(value: Float, onChange: (Float) -> Unit) {
    ValueSlider(
        label = stringResource(R.string.photo_edit_brush_size),
        value = value,
        range = MIN_BRUSH..MAX_BRUSH,
        signed = false,
        onChange = onChange,
        shown = { ((it - MIN_BRUSH) / (MAX_BRUSH - MIN_BRUSH) * PERCENT).roundToInt().toString() }
    )
}

/** A labeled slider. A [signed] slider snaps to zero near the middle with a tick. */
@Composable
private fun ValueSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    signed: Boolean,
    onChange: (Float) -> Unit,
    shown: (Float) -> String = { (it * PERCENT).roundToInt().toString() }
) {
    val haptics = LocalHapticFeedback.current
    val text = shown(value)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value,
            onValueChange = { raw ->
                val snapped = if (signed && abs(raw) < DETENT) 0f else raw
                if (signed && snapped == 0f && value != 0f) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                if (snapped != value) onChange(snapped)
            },
            valueRange = range,
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = text
            }
        )
    }
}

private fun adjustLabel(kind: AdjustKind): Int = when (kind) {
    AdjustKind.BRIGHTNESS -> R.string.photo_edit_brightness
    AdjustKind.CONTRAST -> R.string.photo_edit_contrast
    AdjustKind.EXPOSURE -> R.string.photo_edit_exposure
    AdjustKind.SATURATION -> R.string.photo_edit_saturation
    AdjustKind.WARMTH -> R.string.photo_edit_warmth
    AdjustKind.SHARPNESS -> R.string.photo_edit_sharpness
    AdjustKind.VIGNETTE -> R.string.photo_edit_vignette
}

private fun filterLabel(preset: FilterPreset): Int = when (preset) {
    FilterPreset.NONE -> R.string.photo_edit_filter_none
    FilterPreset.MONO -> R.string.photo_edit_filter_mono
    FilterPreset.SEPIA -> R.string.photo_edit_filter_sepia
    FilterPreset.WARM -> R.string.photo_edit_filter_warm
    FilterPreset.COOL -> R.string.photo_edit_filter_cool
    FilterPreset.VIVID -> R.string.photo_edit_filter_vivid
    FilterPreset.FADE -> R.string.photo_edit_filter_fade
    FilterPreset.NOIR -> R.string.photo_edit_filter_noir
}

/** Tool tab icons and labels, in tab order. */
internal fun toolIcon(tool: EditorTool): ImageVector = when (tool) {
    EditorTool.ADJUST -> Icons.Outlined.Tune
    EditorTool.FILTER -> Icons.Outlined.FilterVintage
    EditorTool.DRAW -> Icons.Outlined.Draw
    EditorTool.TEXT -> Icons.Outlined.TextFields
    EditorTool.BLUR -> Icons.Outlined.BlurOn
    EditorTool.BACKGROUND -> Icons.Outlined.LayersClear
}

internal fun toolLabel(tool: EditorTool): Int = when (tool) {
    EditorTool.ADJUST -> R.string.photo_edit_tool_adjust
    EditorTool.FILTER -> R.string.photo_edit_tool_filters
    EditorTool.DRAW -> R.string.photo_edit_tool_draw
    EditorTool.TEXT -> R.string.photo_edit_tool_text
    EditorTool.BLUR -> R.string.photo_edit_tool_blur
    EditorTool.BACKGROUND -> R.string.photo_edit_tool_background
}

private const val MIN_WIDTH = 0.003f
private const val MAX_WIDTH = 0.05f
private const val MIN_BRUSH = 0.01f
private const val MAX_BRUSH = 0.25f
