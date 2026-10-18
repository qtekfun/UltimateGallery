package com.qtekfun.ultimategallery.feature.photoedit

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.domain.edit.Annotation
import com.qtekfun.ultimategallery.feature.edit.SaveBehaviorDialog

private const val PANEL_MAX_HEIGHT_FRACTION = 0.42f
private val WIDE_PANEL_WIDTH = 380.dp

/** The text being written in the text dialog: a new note, or the note with [id]. */
private data class TextDraft(val id: Long?, val initial: String)

/**
 * The photo editor: light and color, filters, drawing, text, blur and background removal, with undo/redo, a compare button
 * and the save-behavior setting. [onSaved] receives the uri of the result; [onBack] closes the editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(onBack: () -> Unit, onSaved: (Uri?) -> Unit, modifier: Modifier = Modifier, viewModel: PhotoEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val denied = stringResource(R.string.crop_save_denied)
    val failed = stringResource(R.string.crop_save_failed)
    val asCopy = stringResource(R.string.photo_edit_saved_as_copy)
    val currentOnSaved by rememberUpdatedState(onSaved)
    var confirmDiscard by remember { mutableStateOf(false) }
    var comparing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<TextDraft?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PhotoEditorEvent.Saved -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (event.asCopy) Toast.makeText(context, asCopy, Toast.LENGTH_LONG).show()
                    currentOnSaved(event.uri)
                }
                PhotoEditorEvent.Denied -> snackbar.showSnackbar(denied)
                PhotoEditorEvent.Failed -> snackbar.showSnackbar(failed)
            }
        }
    }

    val close = { if (state.hasChanges && !state.saving) confirmDiscard = true else onBack() }
    BackHandler(enabled = state.hasChanges && !state.saving) { confirmDiscard = true }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.photo_edit_title)) },
                navigationIcon = { IconButton(onClick = close) { Icon(Icons.Outlined.Close, stringResource(R.string.photo_edit_close)) } },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = state.canUndo && !state.saving) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.undo))
                    }
                    IconButton(onClick = viewModel::redo, enabled = state.canRedo && !state.saving) {
                        Icon(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.redo))
                    }
                    Button(
                        onClick = viewModel::requestSave,
                        enabled = state.hasChanges && !state.saving,
                        modifier = Modifier.padding(end = 8.dp)
                    ) { Text(stringResource(R.string.crop_save)) }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val panelActions = PanelActions(
            vm = viewModel,
            onAddText = { draft = TextDraft(null, "") },
            onEditText = { id -> (state.edit.annotation(id) as? Annotation.Text)?.let { draft = TextDraft(id, it.text) } }
        )
        EditorContent(state, viewModel, panelActions, comparing, { comparing = it }, onClose = onBack, modifier = Modifier.padding(padding))
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.photo_edit_discard_title)) },
            text = { Text(stringResource(R.string.photo_edit_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onBack()
                }) { Text(stringResource(R.string.photo_edit_discard), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.photo_edit_keep_editing)) } }
        )
    }
    if (state.askingBehavior) SaveBehaviorDialog(onChoose = viewModel::confirmAsk, onDismiss = viewModel::dismissAsk)
    draft?.let { d ->
        TextDialog(d.initial, onDismiss = { draft = null }) { text ->
            if (d.id == null) viewModel.addText(text) else viewModel.updateText(d.id, text)
            draft = null
        }
    }
}

@Composable
private fun EditorContent(
    state: PhotoEditorState,
    viewModel: PhotoEditorViewModel,
    panelActions: PanelActions,
    comparing: Boolean,
    onCompare: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.loadFailed) {
        Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.crop_load_failed), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            TextButton(onClick = onClose) { Text(stringResource(R.string.crop_cancel)) }
        }
        return
    }
    if (state.preview == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val stageActions = StageActions(
        onStroke = viewModel::finishStroke,
        previewStroke = viewModel::previewStroke,
        onShape = viewModel::finishShape,
        previewShape = viewModel::previewShape,
        onSelectAt = viewModel::selectAt,
        onTransform = viewModel::transformSelected,
        onTransformEnd = viewModel::endTransform,
        onTap = viewModel::removeRegionAt,
        onEditText = panelActions.onEditText
    )
    val stage: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            EditorStage(state, viewModel.painter, comparing, stageActions, Modifier.fillMaxSize())
            if (state.saving || state.rendering) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            if (state.hasChanges) CompareButton(onCompare, Modifier.align(Alignment.BottomEnd).padding(12.dp))
        }
    }
    val tabs: @Composable () -> Unit = { ToolTabs(state.tool, enabled = !state.saving, onSelect = viewModel::selectTool) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val panelMaxHeight = maxHeight * PANEL_MAX_HEIGHT_FRACTION
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                stage(Modifier.weight(1f).fillMaxSize())
                Column(Modifier.width(WIDE_PANEL_WIDTH).fillMaxSize()) {
                    ToolPanel(state, panelActions, Modifier.weight(1f).verticalScroll(rememberScrollState()))
                    tabs()
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                stage(Modifier.weight(1f).fillMaxWidth())
                ToolPanel(state, panelActions, Modifier.fillMaxWidth().heightIn(max = panelMaxHeight).verticalScroll(rememberScrollState()))
                tabs()
            }
        }
    }
}

@Composable
private fun ToolTabs(selected: EditorTool, enabled: Boolean, onSelect: (EditorTool) -> Unit) {
    val haptics = LocalHapticFeedback.current
    NavigationBar {
        EditorTool.entries.forEach { tool ->
            val label = stringResource(toolLabel(tool))
            NavigationBarItem(
                selected = tool == selected,
                enabled = enabled,
                onClick = {
                    if (tool != selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(tool)
                },
                icon = { Icon(toolIcon(tool), contentDescription = null) },
                label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

/** Shows the untouched photo while held. */
@Composable
private fun CompareButton(onCompare: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(onCompare)
    SmallFloatingActionButton(
        onClick = {},
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                current(true)
                waitForUpOrCancellation()
                current(false)
            }
        }
    ) {
        Icon(Icons.Outlined.Compare, stringResource(R.string.photo_edit_compare), Modifier.size(20.dp))
    }
}

@Composable
private fun TextDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.isEmpty()) R.string.photo_edit_add_text else R.string.photo_edit_edit_text)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.photo_edit_text_label)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(value.text.trim())
            }, enabled = initial.isNotEmpty() || value.text.isNotBlank()) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
