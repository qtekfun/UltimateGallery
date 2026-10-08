package com.qtekfun.ultimategallery.feature.edit

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.data.edit.CropRect
import com.qtekfun.ultimategallery.data.prefs.SaveBehavior
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private val FRAME_PAD = 24.dp
private val HANDLE_LENGTH = 22.dp
private val HANDLE_THICKNESS = 3.dp
private val HIT_SLOP = 28.dp
private const val SCRIM_ALPHA = 0.6f
private const val DETENT_DEGREES = 1f
private const val STRAIGHTEN_STEP = 2f
private const val QUARTER_TURN = 90f
private const val PANEL_MAX_HEIGHT_FRACTION = 0.55f
private val WIDE_PANEL_WIDTH = 360.dp
private const val STAGE_OVERDRAW = 10_000f

/**
 * Crop and rotate a photo. [onSaved] receives the uri of the saved copy, or of the original when it was
 * overwritten; [onBack] is called on cancel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropRotateScreen(onBack: () -> Unit, onSaved: (Uri?) -> Unit, modifier: Modifier = Modifier, viewModel: CropRotateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val denied = stringResource(R.string.crop_save_denied)
    val failed = stringResource(R.string.crop_save_failed)
    val currentOnSaved by rememberUpdatedState(onSaved)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CropRotateEvent.Saved -> currentOnSaved(event.uri)
                CropRotateEvent.Denied -> snackbar.showSnackbar(denied)
                CropRotateEvent.Failed -> snackbar.showSnackbar(failed)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.crop_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        CropRotateContent(
            state = state,
            actions = CropRotateActions(
                onRotate = viewModel::rotate,
                onFlip = viewModel::flip,
                onStraighten = viewModel::setStraighten,
                onAspect = viewModel::setAspect,
                onDragStart = viewModel::dragStarted,
                onDrag = viewModel::drag,
                onDragEnd = viewModel::dragEnded,
                onPhotoMeasured = viewModel::photoMeasured,
                onReset = viewModel::reset,
                onSave = viewModel::requestSave,
                onCancel = onBack
            ),
            modifier = Modifier.padding(padding)
        )
    }

    if (state.askingBehavior) {
        AskBehaviorDialog(
            onChoose = viewModel::confirmAsk,
            onDismiss = viewModel::dismissAsk
        )
    }
}

private class CropRotateActions(
    val onRotate: (clockwise: Boolean) -> Unit,
    val onFlip: (horizontal: Boolean) -> Unit,
    val onStraighten: (Float) -> Unit,
    val onAspect: (AspectRatio) -> Unit,
    val onDragStart: () -> Unit,
    val onDrag: (CropHandle, Float, Float) -> Boolean,
    val onDragEnd: () -> Unit,
    val onPhotoMeasured: (Int, Int) -> Unit,
    val onReset: () -> Unit,
    val onSave: () -> Unit,
    val onCancel: () -> Unit
)

@Composable
private fun CropRotateContent(state: CropRotateState, actions: CropRotateActions, modifier: Modifier = Modifier) {
    if (state.loadFailed) {
        Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.crop_load_failed), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            TextButton(onClick = actions.onCancel) { Text(stringResource(R.string.crop_cancel)) }
        }
        return
    }
    val sliderSource = remember { MutableInteractionSource() }
    val sliderActive = sliderSource.collectIsDraggedAsState().value || sliderSource.collectIsPressedAsState().value
    val gridVisible = state.dragging || sliderActive
    val haptics = LocalHapticFeedback.current

    val stage: @Composable (Modifier) -> Unit = { stageModifier ->
        Box(stageModifier) {
            CropStage(
                state = state,
                gridVisible = gridVisible,
                onDragStart = actions.onDragStart,
                onDrag = { handle, dx, dy ->
                    if (actions.onDrag(handle, dx, dy)) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                },
                onDragEnd = actions.onDragEnd,
                onPhotoMeasured = actions.onPhotoMeasured,
                modifier = Modifier.fillMaxSize()
            )
            if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    val controls: @Composable (Modifier) -> Unit = { controlsModifier ->
        EditControls(state, actions, sliderSource, controlsModifier)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val panelMaxHeight = maxHeight * PANEL_MAX_HEIGHT_FRACTION
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                stage(Modifier.weight(1f).fillMaxSize())
                controls(Modifier.width(WIDE_PANEL_WIDTH).fillMaxSize().verticalScroll(rememberScrollState()))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                stage(Modifier.weight(1f).fillMaxWidth())
                controls(Modifier.fillMaxWidth().heightIn(max = panelMaxHeight).verticalScroll(rememberScrollState()))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditControls(state: CropRotateState, actions: CropRotateActions, sliderSource: MutableInteractionSource, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val enabled = !state.saving
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AspectChips(state.aspect, enabled) { aspect ->
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            actions.onAspect(aspect)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ToolButton(R.string.crop_rotate_left, enabled, Icons.AutoMirrored.Filled.RotateLeft) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                actions.onRotate(false)
            }
            ToolButton(R.string.crop_rotate_right, enabled, Icons.AutoMirrored.Filled.RotateRight) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                actions.onRotate(true)
            }
            ToolButton(R.string.crop_flip_horizontal, enabled, Icons.Filled.Flip) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                actions.onFlip(true)
            }
            ToolButton(R.string.crop_flip_vertical, enabled, Icons.Filled.Flip, iconRotation = QUARTER_TURN) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                actions.onFlip(false)
            }
        }
        StraightenSlider(state.spec.straightenDeg, enabled, sliderSource, haptics, actions.onStraighten)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.Center) {
            TextButton(onClick = actions.onCancel, enabled = enabled) { Text(stringResource(R.string.crop_cancel)) }
            OutlinedButton(onClick = actions.onReset, enabled = enabled && state.hasChanges) { Text(stringResource(R.string.crop_reset)) }
            Button(onClick = actions.onSave, enabled = enabled && state.hasChanges && state.item != null) { Text(stringResource(R.string.crop_save)) }
        }
    }
}

@Composable
private fun ToolButton(label: Int, enabled: Boolean, icon: ImageVector, iconRotation: Float = 0f, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(56.dp)) {
        Icon(icon, contentDescription = stringResource(label), modifier = Modifier.rotate(iconRotation))
    }
}

@Composable
private fun AspectChips(selected: AspectRatio, enabled: Boolean, onSelect: (AspectRatio) -> Unit) {
    val chips = listOf(
        AspectRatio.FREE to R.string.crop_aspect_free,
        AspectRatio.SQUARE to R.string.crop_aspect_square,
        AspectRatio.R4_3 to R.string.crop_aspect_4_3,
        AspectRatio.R3_4 to R.string.crop_aspect_3_4,
        AspectRatio.R16_9 to R.string.crop_aspect_16_9
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        chips.forEach { (aspect, label) ->
            val isSelected = selected == aspect || (aspect == AspectRatio.R16_9 && selected == AspectRatio.R9_16)
            FilterChip(
                selected = isSelected,
                enabled = enabled,
                onClick = { onSelect(aspect) },
                label = { Text(stringResource(label), maxLines = 1) }
            )
        }
    }
}

@Composable
private fun StraightenSlider(degrees: Float, enabled: Boolean, source: MutableInteractionSource, haptics: HapticFeedback, onChange: (Float) -> Unit) {
    val value = String.format(androidx.compose.ui.text.intl.Locale.current.platformLocale, "%.1f", degrees)
    val label = stringResource(R.string.crop_straighten)
    val described = stringResource(R.string.crop_straighten_state, value)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.crop_straighten_value, value), style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = degrees,
            onValueChange = { raw ->
                val snapped = if (abs(raw) < DETENT_DEGREES) 0f else (raw * STRAIGHTEN_STEP).roundToInt() / STRAIGHTEN_STEP
                if (snapped == 0f && degrees != 0f) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                if (snapped != degrees) onChange(snapped)
            },
            valueRange = -CropGeometry.MAX_STRAIGHTEN..CropGeometry.MAX_STRAIGHTEN,
            enabled = enabled,
            interactionSource = source,
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = described
            }
        )
    }
}

@Composable
private fun AskBehaviorDialog(onChoose: (SaveBehavior, Boolean) -> Unit, onDismiss: () -> Unit) {
    var rememberChoice by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crop_ask_title)) },
        text = {
            Column {
                Text(stringResource(R.string.crop_ask_message))
                TextButton(onClick = { onChoose(SaveBehavior.OVERWRITE, rememberChoice) }) {
                    Text(stringResource(R.string.crop_overwrite), color = MaterialTheme.colorScheme.error)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = rememberChoice, role = Role.Checkbox, onValueChange = { rememberChoice = it }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = rememberChoice, onCheckedChange = null)
                    Text(stringResource(R.string.crop_remember), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(SaveBehavior.COPY, rememberChoice) }) { Text(stringResource(R.string.crop_save_copy)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.crop_cancel)) } }
    )
}

/** The photo with the crop frame on top. Rotation and flips animate the photo and the frame together. */
@Composable
private fun CropStage(
    state: CropRotateState,
    gridVisible: Boolean,
    onDragStart: () -> Unit,
    onDrag: (CropHandle, Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onPhotoMeasured: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val item = state.item ?: return
    val canvas = state.canvas ?: return
    val photo = state.photoSize ?: return
    val spec = state.spec
    val motion = MaterialTheme.motionScheme
    val density = LocalDensity.current

    // The photo turns the short way round; the angle is accumulated so repeated turns keep spinning.
    var seenTurns by remember { mutableIntStateOf(spec.turns) }
    var targetDeg by remember { mutableFloatStateOf(spec.turns * QUARTER_TURN) }
    if (spec.turns != seenTurns) {
        var delta = ((spec.turns - seenTurns) % TURNS + TURNS) % TURNS
        if (delta == TURNS - 1) delta = -1
        targetDeg += delta * QUARTER_TURN
        seenTurns = spec.turns
    }
    val rotation = remember { Animatable(targetDeg) }
    val rotationSpec = motion.defaultSpatialSpec<Float>()
    LaunchedEffect(targetDeg) { rotation.animateTo(targetDeg, rotationSpec) }
    val flipX by animateFloatAsState(if (spec.flipH) -1f else 1f, motion.defaultSpatialSpec(), label = "flipX")
    val flipY by animateFloatAsState(if (spec.flipV) -1f else 1f, motion.defaultSpatialSpec(), label = "flipY")
    val straighten by animateFloatAsState(spec.straightenDeg, motion.fastSpatialSpec(), label = "straighten")
    val gridAlpha by animateFloatAsState(if (gridVisible) 1f else 0f, motion.fastEffectsSpec(), label = "grid")

    BoxWithConstraints(modifier.padding(FRAME_PAD), contentAlignment = Alignment.Center) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val w = photo.width
        val h = photo.height

        // The photo shrinks while it turns so that its bounding box always fits the stage.
        val pose = PhotoPose(rotation.value, flipX, flipY, straighten, CropStageLayout.fitScale(maxW, maxH, w, h, rotation.value))
        val liveScale = pose.scale
        val photoBase = min(maxW / w, maxH / h)
        val frameBase = CropStageLayout.frameScale(maxW, maxH, canvas)

        // The layers apply from the last to the first: turn, mirror, straighten, then scale. This is the order of the saved result.
        Box(
            Modifier
                .requiredSize(with(density) { (w * photoBase).toDp() }, with(density) { (h * photoBase).toDp() })
                .graphicsLayer {
                    scaleX = pose.scale / photoBase
                    scaleY = pose.scale / photoBase
                }
                .graphicsLayer { rotationZ = pose.straightenDeg }
                .graphicsLayer {
                    scaleX = pose.flipX
                    scaleY = pose.flipY
                }
                .graphicsLayer { rotationZ = pose.rotationDeg }
        ) {
            AsyncImage(
                model = item.uri,
                contentDescription = stringResource(R.string.crop_image_description),
                contentScale = ContentScale.Fit,
                onSuccess = { success -> onPhotoMeasured(success.result.image.width, success.result.image.height) },
                modifier = Modifier.fillMaxSize()
            )
        }

        val mirrored = spec.flipH != spec.flipV
        val residual = (rotation.value - targetDeg) * if (mirrored) -1f else 1f
        CropFrame(
            crop = spec.crop,
            canvasWidth = canvas.width * frameBase,
            canvasHeight = canvas.height * frameBase,
            gridAlpha = gridAlpha,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            modifier = Modifier.graphicsLayer {
                val k = liveScale / frameBase
                scaleX = k * flipX / (if (spec.flipH) -1f else 1f)
                scaleY = k * flipY / (if (spec.flipV) -1f else 1f)
                rotationZ = residual
            }
        )
    }
}

/** The dimmed surroundings, the frame, its handles and the thirds grid, with the gestures that edit the crop. */
@Composable
private fun CropFrame(
    crop: CropRect,
    canvasWidth: Float,
    canvasHeight: Float,
    gridAlpha: Float,
    onDragStart: () -> Unit,
    onDrag: (CropHandle, Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val pad = with(density) { FRAME_PAD.toPx() }
    val slop = with(density) { HIT_SLOP.toPx() }
    val currentCrop by rememberUpdatedState(crop)
    val size = CanvasSize(canvasWidth, canvasHeight)
    val currentSize by rememberUpdatedState(size)
    val dragStart by rememberUpdatedState(onDragStart)
    val dragMove by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val description = stringResource(R.string.crop_frame_description)

    Canvas(
        modifier
            .requiredSize(with(density) { (canvasWidth + 2 * pad).toDp() }, with(density) { (canvasHeight + 2 * pad).toDp() })
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val canvasSize = currentSize
                    val handle = CropGeometry.hitTest(currentCrop, down.position.x - pad, down.position.y - pad, canvasSize, slop)
                        ?: return@awaitEachGesture
                    down.consume()
                    dragStart()
                    drag(down.id) { change ->
                        val delta = change.positionChange()
                        change.consume()
                        dragMove(handle, delta.x / canvasSize.width, delta.y / canvasSize.height)
                    }
                    dragEnd()
                }
            }
    ) {
        val left = pad + crop.left * canvasWidth
        val top = pad + crop.top * canvasHeight
        val right = pad + crop.right * canvasWidth
        val bottom = pad + crop.bottom * canvasHeight
        val scrim = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(-STAGE_OVERDRAW, -STAGE_OVERDRAW, this@Canvas.size.width + STAGE_OVERDRAW, this@Canvas.size.height + STAGE_OVERDRAW))
            addRect(Rect(left, top, right, bottom))
        }
        drawPath(scrim, Color.Black.copy(alpha = SCRIM_ALPHA))
        drawRect(Color.White, Offset(left, top), Size(right - left, bottom - top), style = Stroke(1.5.dp.toPx()))
        if (gridAlpha > 0f) drawGrid(left, top, right, bottom, gridAlpha)
        drawHandles(left, top, right, bottom)
    }
}

private fun DrawScope.drawGrid(left: Float, top: Float, right: Float, bottom: Float, alpha: Float) {
    val color = Color.White.copy(alpha = 0.7f * alpha)
    val stroke = 1.dp.toPx()
    for (i in 1..2) {
        val x = left + (right - left) * i / 3f
        val y = top + (bottom - top) * i / 3f
        drawLine(color, Offset(x, top), Offset(x, bottom), stroke)
        drawLine(color, Offset(left, y), Offset(right, y), stroke)
    }
}

private fun DrawScope.drawHandles(left: Float, top: Float, right: Float, bottom: Float) {
    val len = min(HANDLE_LENGTH.toPx(), min(right - left, bottom - top) / 3f)
    val thick = HANDLE_THICKNESS.toPx()
    val color = Color.White
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f
    val horizontal = Size(len, thick)
    val vertical = Size(thick, len)
    // Corner brackets.
    drawRect(color, Offset(left - thick, top - thick), Size(len, thick))
    drawRect(color, Offset(left - thick, top - thick), Size(thick, len))
    drawRect(color, Offset(right - len + thick, top - thick), Size(len, thick))
    drawRect(color, Offset(right, top - thick), Size(thick, len))
    drawRect(color, Offset(left - thick, bottom), Size(len, thick))
    drawRect(color, Offset(left - thick, bottom - len + thick), Size(thick, len))
    drawRect(color, Offset(right - len + thick, bottom), Size(len, thick))
    drawRect(color, Offset(right, bottom - len + thick), Size(thick, len))
    // Edge grips.
    drawRect(color, Offset(cx - len / 2f, top - thick), horizontal)
    drawRect(color, Offset(cx - len / 2f, bottom), horizontal)
    drawRect(color, Offset(left - thick, cy - len / 2f), vertical)
    drawRect(color, Offset(right, cy - len / 2f), vertical)
}

private const val TURNS = 4
